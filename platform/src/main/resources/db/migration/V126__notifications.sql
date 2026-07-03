-- Shared in-app notifications, usable by any module (earthmoving, billing, etc.)
CREATE TABLE notifications (
    id                UUID        NOT NULL DEFAULT gen_random_uuid(),
    tenant_id         UUID        NOT NULL,
    recipient_user_id UUID        NOT NULL,
    type              VARCHAR(100) NOT NULL,   -- see NotificationType enum in code; not DB-constrained on purpose
    severity          VARCHAR(20)  NOT NULL,
    title             VARCHAR(255) NOT NULL,
    message           TEXT         NOT NULL,
    action_url        VARCHAR(500),
    source_module     VARCHAR(50)  NOT NULL,   -- e.g. 'earthmoving'
    source_entity_id  VARCHAR(100),
    read_at           TIMESTAMP,
    created_at        TIMESTAMP    NOT NULL DEFAULT now(),

    CONSTRAINT pk_notifications PRIMARY KEY (id),
    CONSTRAINT chk_notification_severity CHECK (severity IN ('INFO', 'WARNING', 'CRITICAL'))
);

-- The notification bell's main query: "give me this user's notifications,
-- newest first" and "how many are unread". Both are covered by this one
-- composite index (Postgres can use a leading-columns prefix for the count
-- query and the full index, ordered, for the paged list).
CREATE INDEX idx_notifications_recipient
    ON notifications (tenant_id, recipient_user_id, created_at DESC);

-- Speeds up the unread-count query specifically once a user has thousands
-- of historical (mostly-read) notifications — a partial index only over
-- unread rows stays small regardless of total notification volume.
CREATE INDEX idx_notifications_unread
    ON notifications (tenant_id, recipient_user_id)
    WHERE read_at IS NULL;

-- Per-user, per-channel opt-out. Row presence = user has made a choice;
-- absence = default (enabled). See NotificationPreference.java for why.
CREATE TABLE notification_preferences (
    id         UUID        NOT NULL DEFAULT gen_random_uuid(),
    tenant_id  UUID        NOT NULL,
    user_id    UUID        NOT NULL,
    channel    VARCHAR(20) NOT NULL,
    enabled    BOOLEAN     NOT NULL DEFAULT true,
    updated_at TIMESTAMP   NOT NULL DEFAULT now(),

    CONSTRAINT pk_notification_preferences PRIMARY KEY (id),
    CONSTRAINT chk_notification_pref_channel CHECK (channel IN ('EMAIL', 'SMS')),
    CONSTRAINT uq_notification_pref_user_channel UNIQUE (tenant_id, user_id, channel)
);