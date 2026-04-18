import { Injectable, Logger, OnModuleInit } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import * as admin from 'firebase-admin';
import * as path from 'path';

export interface PushNotificationPayload {
  title: string;
  body: string;
  data?: Record<string, string>;
  imageUrl?: string;
}

export interface SendToTokenOptions {
  token: string;
  notification: PushNotificationPayload;
  android?: {
    priority?: 'high' | 'normal';
    ttl?: number; // seconds
  };
}

export interface SendToTopicOptions {
  topic: string;
  notification: PushNotificationPayload;
}

@Injectable()
export class NotificationsService implements OnModuleInit {
  private readonly logger = new Logger(NotificationsService.name);
  private initialized = false;

  constructor(private readonly configService: ConfigService) {}

  async onModuleInit() {
    await this.initializeFirebase();
  }

  /**
   * Initialize Firebase Admin SDK
   */
  private async initializeFirebase() {
    try {
      const serviceAccountPath = this.configService.get<string>(
        'fcm.serviceAccountPath',
        './firebase-service-account.json',
      );

      // Check if already initialized
      if (admin.apps.length > 0) {
        this.initialized = true;
        return;
      }

      // Initialize with service account
      const fs = require('fs');
      const resolvedPath = path.resolve(serviceAccountPath);

      if (!fs.existsSync(resolvedPath)) {
        this.logger.warn(`Firebase service account file not found at ${resolvedPath}, skipping initialization`);
        return;
      }

      const serviceAccount = require(resolvedPath);

      admin.initializeApp({
        credential: admin.credential.cert(serviceAccount),
        projectId: serviceAccount.project_id,
      });

      this.initialized = true;
      this.logger.log(`✅ Firebase Admin SDK initialized (project: ${serviceAccount.project_id})`);
    } catch (error) {
      this.logger.error('❌ Failed to initialize Firebase Admin SDK', error);
      this.logger.warn('Push notifications will not work until Firebase is configured');
    }
  }

  /**
   * Send push notification to a specific device token
   */
  async sendToToken(options: SendToTokenOptions): Promise<string | null> {
    if (!this.initialized) {
      this.logger.warn('Firebase not initialized, skipping notification');
      return null;
    }

    try {
      const message: admin.messaging.Message = {
        token: options.token,
        notification: {
          title: options.notification.title,
          body: options.notification.body,
          imageUrl: options.notification.imageUrl,
        },
        data: options.notification.data,
        android: {
          priority: options.android?.priority || 'high',
          ttl: (options.android?.ttl || 3600) * 1000, // Convert to ms
          notification: {
            sound: 'default',
            clickAction: 'FLUTTER_NOTIFICATION_CLICK',
          },
        },
      };

      const response = await admin.messaging().send(message);
      this.logger.log(`📤 Notification sent: ${response}`);
      return response;
    } catch (error) {
      this.logger.error(`❌ Failed to send notification: ${error.message}`);

      // Handle invalid token
      if (error.code === 'messaging/invalid-registration-token' ||
          error.code === 'messaging/registration-token-not-registered') {
        this.logger.warn(`Invalid token, should be removed from database`);
        // TODO: Emit event to remove invalid token
      }

      return null;
    }
  }

  /**
   * Send push notification to a topic
   */
  async sendToTopic(options: SendToTopicOptions): Promise<string | null> {
    if (!this.initialized) {
      this.logger.warn('Firebase not initialized, skipping notification');
      return null;
    }

    try {
      const message: admin.messaging.Message = {
        topic: options.topic,
        notification: {
          title: options.notification.title,
          body: options.notification.body,
        },
        data: options.notification.data,
        android: {
          priority: 'high',
        },
      };

      const response = await admin.messaging().send(message);
      this.logger.log(`📤 Topic notification sent to ${options.topic}: ${response}`);
      return response;
    } catch (error) {
      this.logger.error(`❌ Failed to send topic notification: ${error.message}`);
      return null;
    }
  }

  /**
   * Send notification to multiple tokens
   */
  async sendToMultipleTokens(
    tokens: string[],
    notification: PushNotificationPayload,
  ): Promise<{ successCount: number; failureCount: number }> {
    if (!this.initialized || tokens.length === 0) {
      return { successCount: 0, failureCount: tokens.length };
    }

    try {
      const message: admin.messaging.MulticastMessage = {
        tokens,
        notification: {
          title: notification.title,
          body: notification.body,
        },
        data: notification.data,
        android: {
          priority: 'high',
        },
      };

      const response = await admin.messaging().sendEachForMulticast(message);

      this.logger.log(
        `📤 Multicast sent: ${response.successCount} success, ${response.failureCount} failed`,
      );

      return {
        successCount: response.successCount,
        failureCount: response.failureCount,
      };
    } catch (error) {
      this.logger.error(`❌ Failed to send multicast: ${error.message}`);
      return { successCount: 0, failureCount: tokens.length };
    }
  }

  /**
   * Subscribe a token to a topic
   */
  async subscribeToTopic(token: string, topic: string): Promise<boolean> {
    if (!this.initialized) return false;

    try {
      await admin.messaging().subscribeToTopic([token], topic);
      this.logger.log(`📌 Token subscribed to topic: ${topic}`);
      return true;
    } catch (error) {
      this.logger.error(`❌ Failed to subscribe to topic: ${error.message}`);
      return false;
    }
  }

  /**
   * Unsubscribe a token from a topic
   */
  async unsubscribeFromTopic(token: string, topic: string): Promise<boolean> {
    if (!this.initialized) return false;

    try {
      await admin.messaging().unsubscribeFromTopic([token], topic);
      this.logger.log(`📌 Token unsubscribed from topic: ${topic}`);
      return true;
    } catch (error) {
      this.logger.error(`❌ Failed to unsubscribe from topic: ${error.message}`);
      return false;
    }
  }

  // ============================================
  // Backroom-specific notification methods
  // ============================================

  /**
   * Notify sharer that a listener accepted their share
   */
  async notifyShareAccepted(sharerToken: string, callId: string): Promise<string | null> {
    return this.sendToToken({
      token: sharerToken,
      notification: {
        title: 'Someone is here for you',
        body: 'A listener has accepted your share. Connecting now...',
        data: {
          type: 'share_accepted',
          call_id: callId,
        },
      },
      android: {
        priority: 'high',
      },
    });
  }

  /**
   * Notify listener of incoming share preview
   */
  async notifyIncomingPreview(
    listenerToken: string,
    shareId: string,
    topic: string,
    previewText: string,
  ): Promise<string | null> {
    return this.sendToToken({
      token: listenerToken,
      notification: {
        title: 'Someone needs to talk',
        body: `${topic}: "${previewText.substring(0, 50)}..."`,
        data: {
          type: 'incoming_preview',
          share_id: shareId,
          topic: topic,
        },
      },
      android: {
        priority: 'high',
        ttl: 30, // Preview expires in 30 seconds
      },
    });
  }

  /**
   * Notify user that their subscription is active
   */
  async notifySubscriptionActive(userToken: string, plan: string): Promise<string | null> {
    return this.sendToToken({
      token: userToken,
      notification: {
        title: 'Welcome to Backroom Plus! 🎉',
        body: `Your ${plan} subscription is now active. Enjoy extended calls and priority matching.`,
        data: {
          type: 'subscription_active',
          plan: plan,
        },
      },
    });
  }

  /**
   * Notify user that their subscription is expiring soon
   */
  async notifySubscriptionExpiring(userToken: string, daysLeft: number): Promise<string | null> {
    return this.sendToToken({
      token: userToken,
      notification: {
        title: 'Subscription expiring soon',
        body: `Your Backroom Plus subscription expires in ${daysLeft} days. Renew to keep your benefits.`,
        data: {
          type: 'subscription_expiring',
          days_left: daysLeft.toString(),
        },
      },
    });
  }
}

