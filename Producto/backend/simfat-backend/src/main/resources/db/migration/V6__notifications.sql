CREATE TABLE notifications (
    id          VARCHAR(36)  PRIMARY KEY,
    user_id     VARCHAR(36)  NOT NULL,
    type        VARCHAR(60)  NOT NULL DEFAULT 'RISK_ALERT',
    title       VARCHAR(200) NOT NULL,
    message     VARCHAR(500),
    region_id   VARCHAR(60),
    comuna_id   VARCHAR(60),
    alert_level VARCHAR(20),
    is_read     BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_notifications_user_unread ON notifications(user_id, is_read);
CREATE INDEX idx_notifications_created_at  ON notifications(created_at DESC);
