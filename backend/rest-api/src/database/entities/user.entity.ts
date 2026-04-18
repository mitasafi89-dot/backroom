import {
  Entity,
  PrimaryGeneratedColumn,
  Column,
  CreateDateColumn,
  UpdateDateColumn,
  OneToOne,
  OneToMany,
} from 'typeorm';

export enum UserRole {
  SHARER = 'sharer',
  LISTENER = 'listener',
  BOTH = 'both',
}

export enum SubscriptionTier {
  FREE = 'free',
  PLUS = 'plus',
  PREMIUM = 'premium',
}

@Entity('users')
export class User {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  @CreateDateColumn({ name: 'created_at' })
  createdAt: Date;

  @UpdateDateColumn({ name: 'updated_at' })
  updatedAt: Date;

  // Identity
  @Column({ default: true })
  anonymous: boolean;

  @Column({ name: 'device_id', nullable: true })
  deviceId: string;

  @Column({ nullable: true, unique: true })
  email: string;

  // Profile
  @Column({ default: 'en' })
  locale: string;

  @Column({
    type: 'enum',
    enum: UserRole,
    default: UserRole.BOTH,
  })
  role: UserRole;

  // Push notifications
  @Column({ name: 'push_token', nullable: true })
  pushToken: string;

  @Column({ name: 'push_enabled', default: true })
  pushEnabled: boolean;

  // Subscription
  @Column({
    name: 'subscription_tier',
    type: 'enum',
    enum: SubscriptionTier,
    default: SubscriptionTier.FREE,
  })
  subscriptionTier: SubscriptionTier;

  // Status
  @Column({ name: 'last_seen_at', nullable: true })
  lastSeenAt: Date;

  @Column({ name: 'is_suspended', default: false })
  isSuspended: boolean;

  @Column({ name: 'suspended_at', nullable: true })
  suspendedAt: Date;

  @Column({ name: 'suspension_reason', nullable: true })
  suspensionReason: string;
}

