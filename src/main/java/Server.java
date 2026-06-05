import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// MongoDB Imports
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Metrics and Monitoring Imports (Prometheus/Micrometer)
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import com.sun.net.httpserver.HttpServer;

// Socket.IO / Netty Engine Imports
import com.corundumstudio.socketio.AckRequest;
import com.corundumstudio.socketio.Configuration;
import com.corundumstudio.socketio.SocketIOClient;
import com.corundumstudio.socketio.SocketIOServer;
import com.corundumstudio.socketio.listener.DataListener;
import com.fasterxml.jackson.databind.JsonNode;

public class Server implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(Server.class);
    private boolean done;
    private ExecutorService pool;
    
    // MongoDB Components
    private MongoClient mongoClient;
    private MongoDatabase database;
    private MongoCollection<Document> messageCollection;

    // Real-time Engine Component (Netty-backed)
    private SocketIOServer socketIOServer;

    // Telemetry & Prometheus metrics
    private static final PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
    private static final Counter messagesCounter = Counter.builder("chat_messages_total")
            .description("Total messages processed by Java Netty Core")
            .register(registry);

    public Server() {
        done = false;
    }

    @Override
    public void run() {
        try {
            // Retrieve DB Host from environment variables (useful for Docker/Production)
            String dbHost = System.getenv("DB_HOST");
            if (dbHost == null) dbHost = "localhost";

            // 1. Initialize MongoDB connection
            mongoClient = MongoClients.create("mongodb://" + dbHost + ":27017");
            database = mongoClient.getDatabase("chatDB");
            messageCollection = database.getCollection("messages");
            logger.info("Connected to MongoDB successfully!");

            // 2. Initialize your custom Thread Pool for Async DB tasks
            pool = Executors.newCachedThreadPool();

            // 3. Prometheus Telemetry: Monitor online connections from Socket.IO in real-time
            Gauge.builder("chat_users_online", () -> socketIOServer != null ? socketIOServer.getAllClients().size() : 0)
                 .description("Current live WebSocket clients online")
                 .register(registry);
            
            // 4. Start the Metrics Infrastructure on port 8080
            setupMetricsServer();
            
            // 5. Fire up the High-Performance Socket.IO Netty Server on port 9999
            setupSocketIOServer(9999);

            // Keep main thread alive while system is active
            while (!done) {
                Thread.sleep(1000);
            }

        } catch (Exception e) {
            logger.error("Server crashed due to an exception: ", e);
            shutdown();
        }
    }

    private void setupMetricsServer() {
        try {
            HttpServer metricsServer = HttpServer.create(new InetSocketAddress(8080), 0);
            metricsServer.createContext("/metrics", httpExchange -> {
                String response = registry.scrape();
                httpExchange.getResponseHeaders().set("Content-Type", "text/plain; version=0.0.4; charset=utf-8");
                httpExchange.sendResponseHeaders(200, response.getBytes().length);
                try (OutputStream os = httpExchange.getResponseBody()) {
                    os.write(response.getBytes());
                }
            });
            new Thread(metricsServer::start).start();
            logger.info("Prometheus Telemetry endpoints exposed on port 8080");
        } catch (IOException e) {
            logger.error("Failed to start telemetry endpoint: " + e.getMessage());
        }
    }

    private void setupSocketIOServer(int port) {
        Configuration config = new Configuration();
        config.setPort(port);
        config.setOrigin("http://localhost:3000"); // CORS Gate Configuration

        this.socketIOServer = new SocketIOServer(config);
        
        // ---- REGISTER REAL-TIME EVENT LISTENERS ----

        // 1. Event: 'setup' -> Client connection initialized
        socketIOServer.addEventListener("setup", JsonNode.class, new DataListener<JsonNode>() {
            @Override
            public void onData(SocketIOClient client, JsonNode activeUser, AckRequest ackSender) {
                String userName = activeUser.has("name") ? activeUser.get("name").asText() : "Unknown";
                logger.info("User '{}' connected over WebSocket. [Session: {}]", userName, client.getSessionId());
                client.sendEvent("connected");
            }
        });

        // 2. Event: 'join room' -> Subscribing to a group chat or 1-on-1 private room
        socketIOServer.addEventListener("join room", String.class, new DataListener<String>() {
            @Override
            public void onData(SocketIOClient client, String chatId, AckRequest ackSender) {
                client.joinRoom(chatId);
                logger.info("Socket Session {} joined virtual room ID: {}", client.getSessionId(), chatId);
            }
        });

        // 3. Event: 'typing' -> Forward to other room members
        socketIOServer.addEventListener("typing", String.class, new DataListener<String>() {
            @Override
            public void onData(SocketIOClient client, String chatId, AckRequest ackSender) {
                socketIOServer.getRoomOperations(chatId).sendEvent("typing");
            }
        });

        // 4. Event: 'stop typing' -> Stop animation
        socketIOServer.addEventListener("stop typing", String.class, new DataListener<String>() {
            @Override
            public void onData(SocketIOClient client, String chatId, AckRequest ackSender) {
                socketIOServer.getRoomOperations(chatId).sendEvent("stop typing");
            }
        });

        // 5. Event: 'new message' -> Core Messaging Delivery
        socketIOServer.addEventListener("new message", JsonNode.class, new DataListener<JsonNode>() {
            @Override
            public void onData(SocketIOClient client, JsonNode messageData, AckRequest ackSender) {
                try {
                    String chatId = messageData.get("chatId").asText();
                    String senderName = messageData.get("sender").get("name").asText();
                    String rawContent = messageData.get("content").asText();
                    
                    // 🔒 Security Layer: Apply Input Sanitization against XSS injections
                    String cleanContent = sanitize(rawContent);
                    ((com.fasterxml.jackson.databind.node.ObjectNode) messageData).put("content", cleanContent);
                    
                    // Telemetry increment
                    messagesCounter.increment();

                    // High speed broadcasting back to the frontend event
                    socketIOServer.getRoomOperations(chatId).sendEvent("message recieved", messageData);
                    
                    // Asynchronous archive persistence to avoid I/O bottlenecks on Event Loop
                    saveMessageToDB(senderName, cleanContent, chatId);
                    
                } catch (Exception e) {
                    logger.error("Fail to route dynamic broadcast packet: ", e);
                }
            }
        });

        socketIOServer.start();
        logger.info("Java Netty real-time Core engine actively running on port {}", port);
    }

    private void saveMessageToDB(String user, String message, String room) {
        if (pool == null || done) return;
        pool.execute(() -> {
            try {
                if (messageCollection != null) {
                    Document doc = new Document("user", user)
                            .append("message", message)
                            .append("room", room)
                            .append("timestamp", new Date());
                    messageCollection.insertOne(doc);
                }
            } catch (Exception e) {
                logger.error("Async Database archival thread error: " + e.getMessage());
            }
        });
    }

    private String sanitize(String input) {
        if (input == null) return null;
        return input.replace("&", "&amp;")   
                    .replace("<", "&lt;")    
                    .replace(">", "&gt;")    
                    .replace("\"", "&quot;") 
                    .replace("'", "&#x27;"); 
    }
    
    public void shutdown() {
        try {
            done = true;
            if (socketIOServer != null) socketIOServer.stop();
            if (mongoClient != null) mongoClient.close();
            if (pool != null) pool.shutdown();
        } catch (Exception e) {
            // Cleanup bypass
        }
    }

    public static void main(String[] args) {
        Server server = new Server();
        server.run();
    }
}