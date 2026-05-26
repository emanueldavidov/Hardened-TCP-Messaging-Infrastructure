# Java Multi-Threaded Chat System (Hardened & Monitored)

![Java](https://img.shields.io/badge/Java-21-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)
![Docker](https://img.shields.io/badge/Docker-Compose-2496ED?style=for-the-badge&logo=docker&logoColor=white)
![GitHub Actions](https://img.shields.io/badge/GitHub_Actions-CI%2FCD-2088FF?style=for-the-badge&logo=githubactions&logoColor=white)
![Prometheus](https://img.shields.io/badge/Prometheus-Observability-E6522C?style=for-the-badge&logo=prometheus&logoColor=white)
![Grafana](https://img.shields.io/badge/Grafana-Metrics-F46800?style=for-the-badge&logo=grafana&logoColor=white)

An enterprise-grade, high-concurrency TCP chat system built with Java 21, backed by MongoDB, monitored via Prometheus & Grafana, and secured using strict DevSecOps lifecycle principles.

---

## 🏗️ Architectural Core Pillars

### 1. Concurrency & Performance
* **Custom Thread Pool:** Utilizes a thread-caching `ExecutorService` pool to efficiently handle sudden traffic bursts while protecting system memory from thread-exhaustion.
* **Thread-Safe Routing:** Manages active user routing tables and messaging rooms safely across concurrent execution blocks using `ConcurrentHashMap`.
* **Persistent Audit Trail:** Connects synchronously with MongoDB via the official Java driver to manage real-time user authentication (`jbcrypt`) and complete message logging.

### 2. Infrastructure & Application Hardening (DevSecOps)
* **Transport Encryption (TLS/SSL):** Enforces encrypted peer-to-peer communication using Java `SSLServerSocketFactory` and `SSLSocketFactory` to block eavesdropping.
* **Least Privilege Containment:** Features a multi-stage Docker build utilizing a minimal production image (`eclipse-temurin:21-jre-alpine`). Runs strictly under a restricted system profile (`USER appuser`) to negate container breakout risks.
* **Feature-Flag Environment Control:** Includes a dynamic runtime switch (`DISABLE_SSL`) configured inside the code, allowing quick testing runs in sandboxed integration steps without keystore certificate overhead.

### 3. Real-Time Telemetry & Observability
* **Custom Metric Instrumentation:** Captured using SLF4J, Logback, and Micrometer core blocks to expose active thread pools, transaction load rates, and network performance indicators.
* **Telemetry Matrix Setup:** Integrates an autonomous **Prometheus** collector scraping metric hooks directly on port `8080`, visualizing the entire system health through interactive dashboards via **Grafana** on port `3000`.

---

## 🚀 Local Installation & Configuration

### Prerequisites
* Java Development Kit (JDK) 21.
* Apache Maven 3.9+.
* Docker Engine & Docker Compose CLI.

### 1. Generating the Keystore Certificate
To run the server in its native secure mode, generate a self-signed PKCS12 cryptography certificate inside your root workspace directory:
keytool -genkeypair -alias chatserver -keyalg RSA -keysize 2048 -storetype PKCS12 -keystore keystore.p12 -validity 365

### 2. Environment & IDE Template Setup
The repository uses strict secret-management configurations. Before booting the application, you must prepare your local credentials using the provided example templates:
* **Local Environment (.env)**: Copy the .env.example file, rename it to .env, and populate it with your actual backend secrets and database passwords:
* cp .env.example .env
* **VS Code Debugger (launch.json)**: Create a .vscode/ directory in the root folder. Copy launch.json.example into it, rename it to launch.json, and update the SSL_KEYSTORE_PASSWORD fields:
* mkdir .vscode && cp launch.json.example .vscode/launch.json

#### (Note: These files are explicitly guarded by .gitignore to prevent any credential leaks to GitHub).

## 🐳 Automated Deployment Orchestration
To run the complete production-ready stack in a sandboxed, secure local network, use the provided compose orchestrator layout:
docker compose up --build
After that in another Shell run:
docker compose run --rm client
You can repeat this command in another shell to create more clients.
* Server Port Mapping: 9999 (Encrypted communication)
* Metrics Ingestion Port: 8080 (Prometheus Scraping endpoint)
* Prometheus Management Gateway: http://localhost:9090
* Grafana Visual Boards: http://localhost:3000

## 🛡️ GitHub Actions DevSecOps Validation Gates
The platform governs software reliability and compliance testing dynamically through distinct pipeline automated structures:

### 1. Verification & Automated Testing (tests.yml)
Fires automatically upon code updates to compile binaries, setup a localized isolated Mongo infrastructure container block, and execute clean JUnit regressions (ChatIntegrationTest) over decoupled DISABLE_SSL logic frameworks.

### 2. Shift-Left Security Assurance (security.yml)
Enforces zero-tolerance blocking rules to break deployment code builds if any vulnerability metrics fail criteria thresholds:

* **Secret Scanning (Gitleaks)**: Analyzes version history branches to capture exposed database strings, AWS access vectors, or plain-text access tokens.

* **SAST Scanning (Semgrep)**: Validates abstract codebase syntax profiles to block raw query injections and catch data sanitization design flaws.

* **SCA Dependencies Scan (Snyk)**: Screens the target dependency ledger framework (pom.xml) against global defect indexes to stop supply-chain compromises.

* **Container Security (Trivy)**: Audits compiled base target layers and container binary configurations thoroughly before any release triggers.

## 📈 Stress Testing & Concurrency Telemetry Verification
To stress test the server and generate metric tracking vectors across visual graph modules:

### 1. Fire up the docker infrastructure dependencies stack:
docker compose up -d

### 2. Fire the highly parallel Bot stress tester tool:
mvn exec:java -Dexec.mainClass="LoadTester"

### 3. Open Grafana (http://localhost:3000)
to evaluate user load charts, operational memory footprints, and socket processing behaviors under heavy connection pressures.

### 4. Agentic DevOps & Autonomous Self-Healing (AIOps)
The infrastructure is supercharged with autonomous Python AI agents powered by the **Google Gemini API** to completely remove manual friction during runtime failures and deployment security vulnerabilities:

* **The Self-Healing CI/CD Agent (Deployment Phase):** Built directly into the `security.yml` pipeline framework. If a security gate fails (e.g., Semgrep detects a Kubernetes misconfiguration), a specialized Python step intercepts the raw error log, evaluates the root cause via structured Pydantic schemas, modifies the broken code locally, pushes a branch, and opens an automated **GitHub Pull Request** with the exact security remediation blueprint.
* **The Autonomous Cluster AI SRE Agent (Runtime Phase):** A multi-turn event analyzer designed as a virtual Site Reliability Engineer. The agent hosts a localized Flask listener endpoint. When system alerts occur in the cluster, it leverages advanced **AI Function Calling** to dynamically run localized diagnostics (`kubectl logs` and `kubectl describe`), safely ingesting and parsing live cluster states to deliver precise engineering incident reports.
