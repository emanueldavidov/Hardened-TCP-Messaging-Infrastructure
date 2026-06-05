import express from 'express';
import dotenv from 'dotenv/config';
import mongoDBConnect from './mongoDB/connection.js';
import mongoose from 'mongoose';
import bodyParser from 'body-parser';
import cors from 'cors';
import userRoutes from './routes/user.js';
import chatRoutes from './routes/chat.js';
import messageRoutes from './routes/message.js';

const app = express();
const corsConfig = {
  origin: 'http://localhost:3000',
  credentials: true,
};
const PORT = process.env.PORT || 5000;

app.use(bodyParser.json());
app.use(bodyParser.urlencoded({ extended: true }));
app.use(cors(corsConfig));

// REST API Endpoints לניהול לוגיסטיקה
app.use('/', userRoutes);
app.use('/api/chat', chatRoutes);
app.use('/api/message', messageRoutes);

mongoose.set('strictQuery', false);

// התחברות למונגו
mongoDBConnect();

app.listen(PORT, () => {
  console.log(`Node.js HTTP API Control Plane running on port ${PORT}`);
});