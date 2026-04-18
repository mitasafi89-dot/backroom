# M-Pesa Integration Guide

This document explains how to set up and use M-Pesa (Safaricom Daraja API) for Backroom subscriptions.

## Overview

Backroom uses **Lipa Na M-Pesa Online (STK Push)** to collect subscription payments. When a user subscribes:

1. User enters their Safaricom phone number
2. App calls our API to initiate payment
3. User receives STK Push prompt on their phone
4. User enters M-Pesa PIN to authorize
5. Safaricom sends callback to our server
6. We activate the subscription

## Getting Started

### 1. Create Daraja Account

1. Go to [Safaricom Developer Portal](https://developer.safaricom.co.ke/)
2. Create an account and verify your email
3. Create a new app to get Consumer Key and Consumer Secret

### 2. Sandbox Testing

For development, use sandbox credentials:

```env
MPESA_ENVIRONMENT=sandbox
MPESA_CONSUMER_KEY=your_sandbox_consumer_key
MPESA_CONSUMER_SECRET=your_sandbox_consumer_secret
MPESA_SHORTCODE=174379
MPESA_PASSKEY=bfb279f9aa9bdbcf158e97dd71a467cd2e0c893059b10f78e6b72ada1ed2c919
```

Sandbox test credentials:
- **Phone**: Use `254708374149` for testing
- **PIN**: Any 4-digit PIN works in sandbox

### 3. Callback URL Setup

M-Pesa requires a publicly accessible HTTPS URL for callbacks.

**For local development**, use ngrok:

```bash
# Install ngrok
npm install -g ngrok

# Start tunnel
ngrok http 8080

# You'll get a URL like: https://abc123.ngrok.io
# Use this as your callback URL
```

Update `.env`:
```env
MPESA_CALLBACK_URL=https://abc123.ngrok.io/api/v1/mpesa/callback
MPESA_TIMEOUT_URL=https://abc123.ngrok.io/api/v1/mpesa/timeout
```

### 4. Production Setup

For production, you need:

1. **Go Live** on Daraja portal
2. Production Consumer Key and Secret
3. Your actual Paybill/Till number
4. Production passkey from Safaricom
5. Verified callback URLs on your production domain

```env
MPESA_ENVIRONMENT=production
MPESA_CONSUMER_KEY=your_production_key
MPESA_CONSUMER_SECRET=your_production_secret
MPESA_SHORTCODE=your_paybill_number
MPESA_PASSKEY=your_production_passkey
MPESA_CALLBACK_URL=https://api.backroom.co.ke/api/v1/mpesa/callback
```

## API Endpoints

### Initiate Payment

```http
POST /api/v1/mpesa/pay
Authorization: Bearer <jwt_token>
Content-Type: application/json

{
  "phoneNumber": "0712345678",
  "plan": "monthly"
}
```

Response:
```json
{
  "success": true,
  "message": "Please check your phone and enter M-Pesa PIN",
  "checkoutRequestId": "ws_CO_01012026123456789",
  "merchantRequestId": "12345-67890",
  "plan": "monthly",
  "amount": 500
}
```

### Query Payment Status

```http
POST /api/v1/mpesa/query
Authorization: Bearer <jwt_token>
Content-Type: application/json

{
  "checkoutRequestId": "ws_CO_01012026123456789"
}
```

Response:
```json
{
  "success": true,
  "resultCode": "0",
  "resultDesc": "The service request is processed successfully."
}
```

### Callback (Safaricom → Our Server)

Safaricom POSTs to our callback URL:

```json
{
  "Body": {
    "stkCallback": {
      "MerchantRequestID": "12345-67890",
      "CheckoutRequestID": "ws_CO_01012026123456789",
      "ResultCode": 0,
      "ResultDesc": "The service request is processed successfully.",
      "CallbackMetadata": {
        "Item": [
          { "Name": "Amount", "Value": 500 },
          { "Name": "MpesaReceiptNumber", "Value": "ABC123XYZ" },
          { "Name": "TransactionDate", "Value": 20260126123456 },
          { "Name": "PhoneNumber", "Value": 254712345678 }
        ]
      }
    }
  }
}
```

## Phone Number Format

The API accepts these formats:
- `0712345678` → Converted to `254712345678`
- `+254712345678` → Converted to `254712345678`
- `254712345678` → Used as-is

Only Safaricom numbers are supported (starting with 07XX or 01XX).

## Pricing

Default pricing in `.env`:

| Plan | Price (KES) | Duration |
|------|-------------|----------|
| Monthly | 500 | 30 days |
| Yearly | 5,000 | 365 days |

## Result Codes

| Code | Meaning |
|------|---------|
| 0 | Success |
| 1 | Insufficient balance |
| 1032 | Request cancelled by user |
| 1037 | Timeout (user didn't respond) |
| 2001 | Wrong PIN |

## Testing Flow

1. Start your local server:
   ```bash
   cd backend/rest-api
   npm run start:dev
   ```

2. Start ngrok:
   ```bash
   ngrok http 8080
   ```

3. Update `MPESA_CALLBACK_URL` in `.env`

4. Use Swagger UI to test:
   - Go to `http://localhost:8080/docs`
   - Authenticate with a test JWT
   - Call `/api/v1/mpesa/pay`

5. Check your phone for STK Push (use sandbox phone `254708374149`)

## Security Notes

- Never expose Consumer Key/Secret in client code
- Always validate callback requests (IP whitelisting recommended)
- Store transaction records for reconciliation
- Implement idempotency to handle duplicate callbacks

## Troubleshooting

### "Bad Request" on STK Push
- Check phone number format
- Verify Consumer Key/Secret are correct
- Ensure you're using correct environment (sandbox vs production)

### No callback received
- Verify ngrok is running and URL is correct
- Check callback URL is HTTPS
- Look at ngrok web interface (http://localhost:4040) for incoming requests

### "Invalid Access Token"
- Token expired (valid for 1 hour)
- Wrong Consumer Key/Secret
- Mismatched environment (sandbox key on production)

## Resources

- [Daraja API Documentation](https://developer.safaricom.co.ke/Documentation)
- [STK Push Guide](https://developer.safaricom.co.ke/APIs/MpesaExpressSimulate)
- [Safaricom Developer Forum](https://developer.safaricom.co.ke/forums)

