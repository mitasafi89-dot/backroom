export default () => ({
  // App
  port: parseInt(process.env.PORT ?? '8080', 10),
  nodeEnv: process.env.NODE_ENV || 'development',

  // Database
  database: {
    host: process.env.POSTGRES_HOST || 'localhost',
    port: parseInt(process.env.POSTGRES_PORT ?? '5432', 10),
    username: process.env.POSTGRES_USER || 'backroom',
    password: process.env.POSTGRES_PASSWORD || 'backroom_dev_password',
    database: process.env.POSTGRES_DB || 'backroom',
  },

  // Redis
  redis: {
    host: process.env.REDIS_HOST || 'localhost',
    port: parseInt(process.env.REDIS_PORT ?? '6379', 10),
    url: process.env.REDIS_URL || 'redis://localhost:6379',
  },

  // JWT
  jwt: {
    secret: process.env.JWT_SECRET || 'CHANGE_ME_IN_PRODUCTION',
    expiresIn: process.env.JWT_EXPIRES_IN || '15m',
    refreshExpiresIn: process.env.JWT_REFRESH_EXPIRES_IN || '7d',
  },

  // TURN/STUN
  turn: {
    url: process.env.TURN_URL || 'turn:localhost:3478',
    stunUrl: process.env.STUN_URL || 'stun:localhost:3478',
    secret: process.env.TURN_LONG_TERM_SECRET || 'CHANGE_ME_coturn_static_auth_secret',
    ttl: parseInt(process.env.TURN_CREDENTIAL_TTL ?? '3600', 10),
  },

  // M-Pesa (Safaricom Daraja API)
  mpesa: {
    environment: process.env.MPESA_ENVIRONMENT || 'sandbox', // 'sandbox' or 'production'
    consumerKey: process.env.MPESA_CONSUMER_KEY || '',
    consumerSecret: process.env.MPESA_CONSUMER_SECRET || '',
    shortcode: process.env.MPESA_SHORTCODE || '', // Paybill or Till number
    passkey: process.env.MPESA_PASSKEY || '', // Lipa Na M-Pesa passkey
    callbackUrl: process.env.MPESA_CALLBACK_URL || 'https://your-domain.com/api/v1/mpesa/callback',
    timeoutUrl: process.env.MPESA_TIMEOUT_URL || 'https://your-domain.com/api/v1/mpesa/timeout',
    resultUrl: process.env.MPESA_RESULT_URL || 'https://your-domain.com/api/v1/mpesa/result',
  },

  // FCM (Firebase Cloud Messaging)
  fcm: {
    serviceAccountPath: process.env.GOOGLE_APPLICATION_CREDENTIALS || './firebase-service-account.json',
    projectId: process.env.FCM_PROJECT_ID || 'backroom-b51b1',
  },

  // Subscription pricing (in KES)
  subscription: {
    monthlyPrice: parseInt(process.env.SUBSCRIPTION_MONTHLY_PRICE ?? '500', 10),
    yearlyPrice: parseInt(process.env.SUBSCRIPTION_YEARLY_PRICE ?? '5000', 10),
    trialDays: parseInt(process.env.SUBSCRIPTION_TRIAL_DAYS ?? '7', 10),
  },
});

