import { Injectable, Logger } from '@nestjs/common';
import { JwtService } from '@nestjs/jwt';
import { ConfigService } from '@nestjs/config';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository } from 'typeorm';
import * as crypto from 'crypto';

import { User, SubscriptionTier } from '../../database/entities/user.entity';
import { AnonymousAuthDto } from './dto/auth.dto';

export interface JwtPayload {
  sub: string; // user id
  anonymous: boolean;
  subscriptionTier: string;
  iat?: number;
  exp?: number;
}

export interface AuthResponse {
  accessToken: string;
  expiresIn: string;
  user: {
    id: string;
    anonymous: boolean;
    subscriptionTier: string;
    createdAt: Date;
  };
}

export interface TurnCredentials {
  username: string;
  credential: string;
  ttl: number;
  urls: string[];
}

@Injectable()
export class AuthService {
  private readonly logger = new Logger(AuthService.name);

  constructor(
    @InjectRepository(User)
    private readonly userRepository: Repository<User>,
    private readonly jwtService: JwtService,
    private readonly configService: ConfigService,
  ) {}

  /**
   * Authenticate or create anonymous user
   */
  async authenticateAnonymous(dto: AnonymousAuthDto): Promise<AuthResponse> {
    let user: User | null = null;

    // Check if user exists with this device ID
    if (dto.deviceId) {
      user = await this.userRepository.findOne({
        where: { deviceId: dto.deviceId },
      });
    }

    // Create new anonymous user if not found
    if (!user) {
      user = this.userRepository.create({
        anonymous: true,
        deviceId: dto.deviceId,
        locale: dto.locale || 'en',
        subscriptionTier: SubscriptionTier.FREE,
      });
      await this.userRepository.save(user);
      this.logger.log(`New anonymous user created: ${user.id}`);
    } else {
      // Update last seen
      user.lastSeenAt = new Date();
      await this.userRepository.save(user);
    }

    // Generate JWT
    const payload: JwtPayload = {
      sub: user.id,
      anonymous: user.anonymous,
      subscriptionTier: user.subscriptionTier,
    };

    const accessToken = this.jwtService.sign(payload);

    return {
      accessToken,
      expiresIn: this.configService.get<string>('jwt.expiresIn') ?? '15m',
      user: {
        id: user.id,
        anonymous: user.anonymous,
        subscriptionTier: user.subscriptionTier,
        createdAt: user.createdAt,
      },
    };
  }

  /**
   * Validate JWT payload and return user
   */
  async validateUser(payload: JwtPayload): Promise<User | null> {
    return this.userRepository.findOne({
      where: { id: payload.sub },
    });
  }

  /**
   * Generate time-limited TURN credentials
   */
  generateTurnCredentials(userId: string): TurnCredentials {
    const ttl = this.configService.get<number>('turn.ttl') ?? 3600;
    const secret = this.configService.get<string>('turn.secret') ?? '';
    const turnUrl = this.configService.get<string>('turn.url') ?? 'turn:localhost:3478';
    const stunUrl = this.configService.get<string>('turn.stunUrl') ?? 'stun:localhost:3478';

    // Timestamp for credential expiry (Unix time + TTL)
    const timestamp = Math.floor(Date.now() / 1000) + ttl;
    const username = `${timestamp}:${userId}`;

    // Generate HMAC-SHA1 credential
    const hmac = crypto.createHmac('sha1', secret);
    hmac.update(username);
    const credential = hmac.digest('base64');

    return {
      username,
      credential,
      ttl,
      urls: [stunUrl, turnUrl],
    };
  }

  /**
   * Refresh access token
   */
  async refreshToken(userId: string): Promise<{ accessToken: string } | null> {
    const user = await this.userRepository.findOne({
      where: { id: userId },
    });

    if (!user) {
      return null;
    }

    const payload: JwtPayload = {
      sub: user.id,
      anonymous: user.anonymous,
      subscriptionTier: user.subscriptionTier,
    };

    return {
      accessToken: this.jwtService.sign(payload),
    };
  }
}

