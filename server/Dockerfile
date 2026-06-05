FROM node:18-alpine
WORKDIR /app
# מעתיקים רק את ה-package.json כדי להתקין תלויות פעם אחת בבנייה
COPY package*.json ./
RUN npm install --legacy-peer-deps
COPY . .
EXPOSE 5000
CMD ["npm", "start"]