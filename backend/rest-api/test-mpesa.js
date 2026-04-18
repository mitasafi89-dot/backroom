#!/usr/bin/env node
/**
 * M-Pesa Credential Test Script
 * Tests OAuth token generation with Daraja API
 *
 * Usage: node test-mpesa.js
 */

const https = require('https');

// Sandbox credentials
const CONSUMER_KEY = 'JFWmuUAcAsxxjFeKJgMluAHI7n5MCLIgj32AotWZK0DOHvM2';
const CONSUMER_SECRET = 'RgCPAsm6fgF40Q8cMcCyOaANU2v2W9vJqH0bnZOxk88MGmGZqA2fkCneVtbUqqPz';
const BASE_URL = 'sandbox.safaricom.co.ke';

// Generate Basic Auth header
const auth = Buffer.from(`${CONSUMER_KEY}:${CONSUMER_SECRET}`).toString('base64');

console.log('🔐 Testing M-Pesa Daraja API credentials...\n');
console.log('Environment: SANDBOX');
console.log(`Consumer Key: ${CONSUMER_KEY.substring(0, 10)}...`);
console.log('');

const options = {
  hostname: BASE_URL,
  path: '/oauth/v1/generate?grant_type=client_credentials',
  method: 'GET',
  headers: {
    'Authorization': `Basic ${auth}`,
  },
};

const req = https.request(options, (res) => {
  let data = '';

  res.on('data', (chunk) => {
    data += chunk;
  });

  res.on('end', () => {
    console.log(`Status: ${res.statusCode}`);

    if (res.statusCode === 200) {
      const response = JSON.parse(data);
      console.log('✅ SUCCESS! OAuth token generated.\n');
      console.log('Access Token:', response.access_token.substring(0, 30) + '...');
      console.log('Expires In:', response.expires_in, 'seconds');
      console.log('\n🎉 M-Pesa credentials are valid!');
    } else {
      console.log('❌ FAILED!\n');
      console.log('Response:', data);
    }
  });
});

req.on('error', (e) => {
  console.error('❌ Request failed:', e.message);
});

req.end();

