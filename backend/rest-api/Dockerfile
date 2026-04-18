FROM node:20-alpine AS builder

WORKDIR /app

# Install ALL dependencies (including dev for building)
COPY backend/rest-api/package*.json ./
RUN npm ci

# Copy source and build
COPY backend/rest-api/ .
RUN npm run build

# --- Production stage ---
FROM node:20-alpine

WORKDIR /app

COPY --from=builder /app/package*.json ./
RUN npm ci --omit=dev

COPY --from=builder /app/dist ./dist

# Expose port
EXPOSE 8080

# Start
CMD ["node", "dist/main"]

