# Backroom Production Deployment Guide

## Prerequisites
- A cloud server (Ubuntu 22.04 recommended)
- A domain name (e.g., backroom.app)
- SSH access to your server

---

## 1. Server Setup

### Connect to your server
```bash
ssh root@your-server-ip
```

### Update and install dependencies
```bash
apt update && apt upgrade -y
apt install -y docker.io docker-compose nginx certbot python3-certbot-nginx git
systemctl enable docker
systemctl start docker
```

---

## 2. Clone and Configure

### Clone the repository
```bash
cd /opt
git clone https://github.com/your-repo/backroom.git
cd backroom
```

### Create production environment file
```bash
cat > /opt/backroom/backend/.env.production << 'EOF'
# Database
POSTGRES_HOST=postgres
POSTGRES_PORT=5432
POSTGRES_USER=backroom
POSTGRES_PASSWORD=YOUR_SECURE_PASSWORD_HERE
POSTGRES_DB=backroom

# Redis
REDIS_HOST=redis
REDIS_PORT=6379

# JWT
JWT_SECRET=YOUR_VERY_LONG_RANDOM_SECRET_HERE
JWT_EXPIRES_IN=15m
JWT_REFRESH_EXPIRES_IN=7d

# TURN Server
TURN_URL=turn:turn.yourdomain.com:3478
STUN_URL=stun:turn.yourdomain.com:3478
TURN_LONG_TERM_SECRET=YOUR_COTURN_SECRET_HERE

# M-Pesa (Production)
MPESA_ENVIRONMENT=production
MPESA_CONSUMER_KEY=your_production_consumer_key
MPESA_CONSUMER_SECRET=your_production_consumer_secret
MPESA_SHORTCODE=your_paybill_number
MPESA_PASSKEY=your_production_passkey
MPESA_CALLBACK_URL=https://api.yourdomain.com/api/v1/mpesa/callback

# Firebase
GOOGLE_APPLICATION_CREDENTIALS=/app/firebase-service-account.json
FCM_PROJECT_ID=backroom-b51b1
EOF
```

---

## 3. Docker Compose Production Setup

### Create docker-compose.production.yml
```bash
cat > /opt/backroom/docker-compose.production.yml << 'EOF'
version: '3.8'

services:
  # PostgreSQL Database
  postgres:
    image: postgres:15-alpine
    environment:
      POSTGRES_USER: ${POSTGRES_USER}
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD}
      POSTGRES_DB: ${POSTGRES_DB}
    volumes:
      - postgres_data:/var/lib/postgresql/data
    restart: unless-stopped
    networks:
      - backroom-network

  # Redis
  redis:
    image: redis:7-alpine
    command: redis-server --appendonly yes
    volumes:
      - redis_data:/data
    restart: unless-stopped
    networks:
      - backroom-network

  # REST API
  rest-api:
    build:
      context: ./backend/rest-api
      dockerfile: Dockerfile
    environment:
      - NODE_ENV=production
    env_file:
      - ./backend/.env.production
    ports:
      - "8080:8080"
    depends_on:
      - postgres
      - redis
    restart: unless-stopped
    networks:
      - backroom-network

  # WebSocket Signaling Server
  ws-signaling:
    build:
      context: ./backend/ws-signaling-node
      dockerfile: Dockerfile
    environment:
      - NODE_ENV=production
      - WS_PORT=8443
    ports:
      - "8443:8443"
    depends_on:
      - redis
    restart: unless-stopped
    networks:
      - backroom-network

  # TURN/STUN Server (Coturn)
  coturn:
    image: coturn/coturn:latest
    command: >
      -n
      --log-file=stdout
      --external-ip=$${EXTERNAL_IP}
      --listening-port=3478
      --min-port=49152
      --max-port=65535
      --use-auth-secret
      --static-auth-secret=$${TURN_SECRET}
      --realm=yourdomain.com
      --no-tcp-relay
      --fingerprint
      --lt-cred-mech
    environment:
      - EXTERNAL_IP=${SERVER_PUBLIC_IP}
      - TURN_SECRET=${TURN_LONG_TERM_SECRET}
    ports:
      - "3478:3478/tcp"
      - "3478:3478/udp"
      - "49152-49252:49152-49252/udp"
    restart: unless-stopped
    networks:
      - backroom-network

volumes:
  postgres_data:
  redis_data:

networks:
  backroom-network:
    driver: bridge
EOF
```

---

## 4. Nginx Reverse Proxy with SSL

### Create Nginx configuration
```bash
cat > /etc/nginx/sites-available/backroom << 'EOF'
# REST API
server {
    listen 80;
    server_name api.yourdomain.com;

    location / {
        return 301 https://$server_name$request_uri;
    }
}

server {
    listen 443 ssl http2;
    server_name api.yourdomain.com;

    ssl_certificate /etc/letsencrypt/live/api.yourdomain.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/api.yourdomain.com/privkey.pem;

    location / {
        proxy_pass http://localhost:8080;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}

# WebSocket Signaling
server {
    listen 80;
    server_name ws.yourdomain.com;

    location / {
        return 301 https://$server_name$request_uri;
    }
}

server {
    listen 443 ssl http2;
    server_name ws.yourdomain.com;

    ssl_certificate /etc/letsencrypt/live/ws.yourdomain.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/ws.yourdomain.com/privkey.pem;

    location / {
        proxy_pass http://localhost:8443;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_read_timeout 86400;
    }
}
EOF

ln -s /etc/nginx/sites-available/backroom /etc/nginx/sites-enabled/
```

### Get SSL Certificates
```bash
certbot --nginx -d api.yourdomain.com -d ws.yourdomain.com
```

---

## 5. Start Services

```bash
cd /opt/backroom
docker-compose -f docker-compose.production.yml up -d
```

### Check logs
```bash
docker-compose -f docker-compose.production.yml logs -f
```

---

## 6. Update Android App Configuration

Update `NetworkConfig.kt` for production:

```kotlin
object NetworkConfig {
    // Production URLs
    private const val SERVER_DOMAIN = "yourdomain.com"
    
    const val REST_API_URL = "https://api.$SERVER_DOMAIN/api/v1"
    const val WS_SIGNALING_URL = "wss://ws.$SERVER_DOMAIN"
    const val TURN_URL = "turn:turn.$SERVER_DOMAIN:3478"
    const val STUN_URL = "stun:stun.l.google.com:19302"
}
```

---

## 7. Firewall Configuration

```bash
# Allow required ports
ufw allow 22/tcp      # SSH
ufw allow 80/tcp      # HTTP
ufw allow 443/tcp     # HTTPS
ufw allow 3478/tcp    # TURN TCP
ufw allow 3478/udp    # TURN UDP
ufw allow 49152:49252/udp  # TURN relay ports
ufw enable
```

---

## 8. DNS Configuration

Add these DNS records to your domain:

| Type | Name | Value | TTL |
|------|------|-------|-----|
| A | api | YOUR_SERVER_IP | 300 |
| A | ws | YOUR_SERVER_IP | 300 |
| A | turn | YOUR_SERVER_IP | 300 |

---

## Cost Estimates

| Item | Monthly Cost |
|------|--------------|
| Cloud Server (2GB RAM) | $5-10 |
| Domain Name | ~$1 (yearly $10-15) |
| SSL Certificates | Free (Let's Encrypt) |
| **Total** | **~$6-11/month** |

---

## Scaling Considerations

For more users, consider:
1. **Multiple TURN servers** - TURN is bandwidth-intensive
2. **Redis Cluster** - For signaling state across multiple instances
3. **Load Balancer** - For WebSocket connections
4. **CDN** - For static assets if you add a web version

---

## Security Checklist

- [ ] Change all default passwords
- [ ] Enable UFW firewall
- [ ] Set up fail2ban for SSH
- [ ] Use strong JWT secrets
- [ ] Enable TURN authentication
- [ ] Set up regular backups
- [ ] Enable automatic security updates
- [ ] Use production M-Pesa credentials (not sandbox)

