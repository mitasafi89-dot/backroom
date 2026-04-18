#!/usr/bin/env node
/**
 * Firebase Admin SDK Test Script
 * Tests that Firebase is properly configured
 *
 * Usage: node test-firebase.js
 */

const admin = require('firebase-admin');
const path = require('path');

// Path to service account
const serviceAccountPath = path.join(__dirname, 'firebase-service-account.json');

console.log('🔥 Testing Firebase Admin SDK...\n');

try {
  // Load service account
  const serviceAccount = require(serviceAccountPath);

  console.log('✅ Service account file found');
  console.log(`   Project ID: ${serviceAccount.project_id}`);
  console.log(`   Client Email: ${serviceAccount.client_email}`);
  console.log('');

  // Initialize Firebase
  admin.initializeApp({
    credential: admin.credential.cert(serviceAccount),
    projectId: serviceAccount.project_id,
  });

  console.log('✅ Firebase Admin SDK initialized successfully!\n');

  // Test messaging (dry run)
  console.log('📤 Testing FCM (dry run)...');

  const message = {
    notification: {
      title: 'Test Notification',
      body: 'This is a test from Backroom server',
    },
    token: 'fake-token-for-validation', // This will fail, but validates SDK setup
  };

  admin.messaging().send(message, true) // true = dry run
    .then((response) => {
      console.log('✅ FCM dry run successful!');
      console.log(`   Response: ${response}\n`);
      console.log('🎉 Firebase is fully configured and ready!\n');
      process.exit(0);
    })
    .catch((error) => {
      if (error.code === 'messaging/invalid-argument' ||
          error.code === 'messaging/invalid-registration-token') {
        // This is expected with a fake token
        console.log('✅ FCM API is working (fake token rejected as expected)');
        console.log(`   Error code: ${error.code}\n`);
        console.log('🎉 Firebase is fully configured and ready!\n');
        process.exit(0);
      } else {
        console.error('❌ FCM test failed:', error.message);
        process.exit(1);
      }
    });

} catch (error) {
  console.error('❌ Firebase initialization failed:', error.message);
  console.log('\nMake sure firebase-service-account.json exists in this directory.');
  process.exit(1);
}

