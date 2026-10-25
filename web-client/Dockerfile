FROM node:20-alpine

WORKDIR /app

COPY package*.json ./
RUN npm install --omit=dev

COPY . .

EXPOSE 3000

ENV PORT=3000
ENV BACKEND_URL=http://orden-service:8080

CMD ["node", "server.js"]
