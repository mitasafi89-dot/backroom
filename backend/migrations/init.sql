-- Backroom — Initial Database Schema
-- This file runs automatically on first Postgres container start
-- For production, use a migration tool (Flyway, node-pg-migrate, etc.)

-- ============================================
-- EXTENSIONS
-- ============================================
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ============================================
-- ENUMS
-- ============================================
CREATE TYPE user_role AS ENUM ('sharer', 'listener', 'both');
CREATE TYPE intensity_level AS ENUM ('light', 'heavy', 'very_heavy');
CREATE TYPE share_status AS ENUM ('waiting', 'matched', 'cancelled', 'expired');
CREATE TYPE call_status AS ENUM ('pending', 'ongoing', 'ended', 'failed');
CREATE TYPE call_ended_by AS ENUM ('sharer', 'listener', 'system');
CREATE TYPE feedback_rating AS ENUM ('helped', 'neutral', 'uncomfortable');
CREATE TYPE subscription_tier AS ENUM ('free', 'plus', 'premium');
CREATE TYPE subscription_provider AS ENUM ('stripe', 'google_play', 'apple');

-- ============================================
-- USERS
-- ============================================
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    -- Identity
    anonymous BOOLEAN NOT NULL DEFAULT TRUE,
    device_id TEXT,
    email TEXT UNIQUE,

    -- Profile
    locale TEXT DEFAULT 'en',
    role user_role DEFAULT 'both',

    -- Push notifications
    push_token TEXT,
    push_enabled BOOLEAN DEFAULT TRUE,

    -- Subscription
    subscription_tier subscription_tier DEFAULT 'free',

    -- Status
    last_seen_at TIMESTAMPTZ,
    is_suspended BOOLEAN NOT NULL DEFAULT FALSE,
    suspended_at TIMESTAMPTZ,
    suspension_reason TEXT
);

CREATE INDEX idx_users_device_id ON users(device_id);
CREATE INDEX idx_users_email ON users(email) WHERE email IS NOT NULL;
CREATE INDEX idx_users_last_seen ON users(last_seen_at);
CREATE INDEX idx_users_subscription ON users(subscription_tier);

-- ============================================
-- PROFILES (extended user settings)
-- ============================================
CREATE TABLE profiles (
    user_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    -- Display
    display_name TEXT,

    -- Voice anonymization settings (client-side)
    anonymization_settings JSONB DEFAULT '{"pitch_shift": 0, "formant_shift": 0, "enabled": true}'::jsonb,

    -- Preferences
    preferences JSONB DEFAULT '{}'::jsonb
);

-- ============================================
-- BOUNDARIES (listener capacity/limits)
-- ============================================
CREATE TABLE boundaries (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    -- What topics the listener can handle
    topics TEXT[] DEFAULT ARRAY['General'],

    -- Maximum emotional intensity
    max_intensity intensity_level DEFAULT 'heavy',

    -- Maximum call duration (minutes)
    max_duration_minutes INTEGER DEFAULT 15,

    -- Active boundaries flag
    is_active BOOLEAN DEFAULT TRUE
);

CREATE INDEX idx_boundaries_user ON boundaries(user_id);
CREATE INDEX idx_boundaries_topics ON boundaries USING GIN(topics);

-- ============================================
-- SHARES (share requests from sharers)
-- ============================================
CREATE TABLE shares (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    -- Sharer
    sharer_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,

    -- Request details
    topic TEXT NOT NULL,
    tone TEXT NOT NULL,
    preview_text TEXT NOT NULL CHECK (char_length(preview_text) <= 240),
    duration_minutes INTEGER NOT NULL CHECK (duration_minutes IN (5, 10, 15, 30)),

    -- Matching preferences
    allow_local_matches BOOLEAN DEFAULT FALSE,
    priority_matching BOOLEAN DEFAULT FALSE,

    -- Status
    status share_status NOT NULL DEFAULT 'waiting',
    expires_at TIMESTAMPTZ,

    -- Match result
    matched_at TIMESTAMPTZ,
    matched_listener_id UUID REFERENCES users(id),

    -- Metadata (for analytics, debugging)
    metadata JSONB DEFAULT '{}'::jsonb
);

CREATE INDEX idx_shares_sharer ON shares(sharer_id);
CREATE INDEX idx_shares_status ON shares(status);
CREATE INDEX idx_shares_created ON shares(created_at);
CREATE INDEX idx_shares_topic ON shares(topic);
CREATE INDEX idx_shares_waiting ON shares(status, created_at) WHERE status = 'waiting';

-- ============================================
-- LISTENER AVAILABILITY
-- ============================================
CREATE TABLE listener_availability (
    user_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    -- Availability
    is_available BOOLEAN NOT NULL DEFAULT FALSE,

    -- Capacity management
    capacity INTEGER DEFAULT 1,
    current_load INTEGER DEFAULT 0,

    -- Quick match filter (hash of boundaries for fast lookup)
    boundaries_hash TEXT,

    -- Last toggle timestamp
    last_toggled_at TIMESTAMPTZ
);

CREATE INDEX idx_listener_available ON listener_availability(is_available) WHERE is_available = TRUE;

-- ============================================
-- CALLS
-- ============================================
CREATE TABLE calls (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    -- Related share
    share_id UUID REFERENCES shares(id),

    -- Participants
    sharer_id UUID NOT NULL REFERENCES users(id),
    listener_id UUID NOT NULL REFERENCES users(id),

    -- Timing
    start_time TIMESTAMPTZ,
    end_time TIMESTAMPTZ,
    duration_seconds INTEGER,

    -- Status
    status call_status NOT NULL DEFAULT 'pending',
    ended_by call_ended_by,
    reason_code TEXT,

    -- WebRTC metadata (no audio content!)
    webrtc_metadata JSONB DEFAULT '{}'::jsonb
);

CREATE INDEX idx_calls_sharer ON calls(sharer_id);
CREATE INDEX idx_calls_listener ON calls(listener_id);
CREATE INDEX idx_calls_status ON calls(status);
CREATE INDEX idx_calls_created ON calls(created_at);

-- ============================================
-- FEEDBACKS
-- ============================================
CREATE TABLE feedbacks (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    -- Related call
    call_id UUID NOT NULL REFERENCES calls(id) ON DELETE CASCADE,

    -- Who submitted
    by_user_id UUID NOT NULL REFERENCES users(id),

    -- Rating
    rating feedback_rating NOT NULL,

    -- Optional text
    feedback_text TEXT CHECK (char_length(feedback_text) <= 500)
);

CREATE INDEX idx_feedbacks_call ON feedbacks(call_id);
CREATE INDEX idx_feedbacks_user ON feedbacks(by_user_id);
CREATE INDEX idx_feedbacks_rating ON feedbacks(rating);

-- ============================================
-- REPORTS
-- ============================================
CREATE TABLE reports (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    -- Related call (optional - can report without call)
    call_id UUID REFERENCES calls(id),

    -- Reporter and reported
    reporter_user_id UUID NOT NULL REFERENCES users(id),
    reported_user_id UUID NOT NULL REFERENCES users(id),

    -- Report details
    reason TEXT NOT NULL,
    details TEXT CHECK (char_length(details) <= 1000),

    -- Actions
    block_requested BOOLEAN DEFAULT FALSE,

    -- Moderation
    reviewed_at TIMESTAMPTZ,
    reviewed_by UUID REFERENCES users(id),
    action_taken TEXT,
    resolution TEXT
);

CREATE INDEX idx_reports_reporter ON reports(reporter_user_id);
CREATE INDEX idx_reports_reported ON reports(reported_user_id);
CREATE INDEX idx_reports_pending ON reports(created_at) WHERE reviewed_at IS NULL;

-- ============================================
-- BLOCKS (user blocks)
-- ============================================
CREATE TABLE blocks (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    blocker_user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    blocked_user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,

    UNIQUE(blocker_user_id, blocked_user_id)
);

CREATE INDEX idx_blocks_blocker ON blocks(blocker_user_id);
CREATE INDEX idx_blocks_blocked ON blocks(blocked_user_id);

-- ============================================
-- SUBSCRIPTIONS
-- ============================================
CREATE TABLE subscriptions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    -- Provider info
    provider subscription_provider NOT NULL,
    provider_subscription_id TEXT NOT NULL,
    provider_customer_id TEXT,

    -- Subscription details
    tier subscription_tier NOT NULL,

    -- Validity
    started_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ,
    cancelled_at TIMESTAMPTZ,

    -- Status
    is_active BOOLEAN NOT NULL DEFAULT TRUE,

    -- Raw provider data
    provider_data JSONB DEFAULT '{}'::jsonb
);

CREATE INDEX idx_subscriptions_user ON subscriptions(user_id);
CREATE INDEX idx_subscriptions_active ON subscriptions(user_id, is_active) WHERE is_active = TRUE;
CREATE INDEX idx_subscriptions_provider ON subscriptions(provider, provider_subscription_id);

-- ============================================
-- AUDIT LOGS
-- ============================================
CREATE TABLE audit_logs (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    -- Event info
    event_type TEXT NOT NULL,

    -- Actor (optional - system events may not have one)
    user_id UUID REFERENCES users(id),

    -- Metadata (never log PII or audio content!)
    metadata JSONB DEFAULT '{}'::jsonb,

    -- Request context
    ip_address INET,
    user_agent TEXT
);

CREATE INDEX idx_audit_logs_type ON audit_logs(event_type);
CREATE INDEX idx_audit_logs_user ON audit_logs(user_id);
CREATE INDEX idx_audit_logs_created ON audit_logs(created_at);

-- Partition audit_logs by month for easier retention management
-- (For production, consider TimescaleDB or native partitioning)

-- ============================================
-- CRISIS RESOURCES (admin-editable)
-- ============================================
CREATE TABLE crisis_resources (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    -- Targeting
    locale TEXT NOT NULL DEFAULT 'en',
    country_code TEXT,

    -- Resource info
    name TEXT NOT NULL,
    description TEXT,
    phone TEXT,
    url TEXT,

    -- Display
    display_order INTEGER DEFAULT 0,
    is_active BOOLEAN DEFAULT TRUE
);

CREATE INDEX idx_crisis_resources_locale ON crisis_resources(locale, is_active);

-- ============================================
-- FUNCTIONS & TRIGGERS
-- ============================================

-- Auto-update updated_at timestamp
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ language 'plpgsql';

-- Apply trigger to all tables with updated_at
CREATE TRIGGER update_users_updated_at BEFORE UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_profiles_updated_at BEFORE UPDATE ON profiles
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_boundaries_updated_at BEFORE UPDATE ON boundaries
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_shares_updated_at BEFORE UPDATE ON shares
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_listener_availability_updated_at BEFORE UPDATE ON listener_availability
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_calls_updated_at BEFORE UPDATE ON calls
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_reports_updated_at BEFORE UPDATE ON reports
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_subscriptions_updated_at BEFORE UPDATE ON subscriptions
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_crisis_resources_updated_at BEFORE UPDATE ON crisis_resources
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- ============================================
-- SEED DATA (Crisis Resources)
-- ============================================
INSERT INTO crisis_resources (locale, country_code, name, description, phone, url, display_order) VALUES
('en', 'KE', 'Kenya Red Cross', '24/7 emergency and crisis support', '1199', 'https://www.redcross.or.ke/', 1),
('en', 'KE', 'Befrienders Kenya', 'Emotional support and suicide prevention', '+254 722 178 177', 'https://www.befrienderskenya.org/', 2),
('en', 'US', 'National Suicide Prevention Lifeline', '24/7 crisis support', '988', 'https://988lifeline.org/', 1),
('en', 'US', 'Crisis Text Line', 'Text HOME to 741741', NULL, 'https://www.crisistextline.org/', 2),
('en', 'GB', 'Samaritans', '24/7 emotional support', '116 123', 'https://www.samaritans.org/', 1),
('en', NULL, 'International Association for Suicide Prevention', 'Find a crisis center', NULL, 'https://www.iasp.info/resources/Crisis_Centres/', 99);

-- ============================================
-- GRANTS (for app user - adjust as needed)
-- ============================================
-- In production, create a separate app user with limited privileges
-- GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO backroom_app;
-- GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO backroom_app;

COMMENT ON DATABASE backroom IS 'Backroom - Anonymous voice support platform';

