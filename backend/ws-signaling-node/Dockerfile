FROM node:20-alpine

WORKDIR /app

# Install dependencies
COPY package*.json ./
RUN npm ci --only=production

# Copy source
COPY . .

# Expose WebSocket port
EXPOSE 8443

# Health check (basic TCP check)
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
  CMD nc -z localhost 8443 || exit 1

# Start
CMD ["node", "server.js"]

