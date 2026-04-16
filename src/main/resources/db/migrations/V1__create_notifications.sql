CREATE TABLE notifications (
                               id         VARCHAR(36)   PRIMARY KEY DEFAULT (UUID()),
                               user_id    VARCHAR(36)   NOT NULL,
                               event_type VARCHAR(50)   NOT NULL,
                               message    VARCHAR(255)  NOT NULL,
                               is_read    BOOLEAN       NOT NULL DEFAULT FALSE,
                               created_at DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_notifications_user_id
    ON notifications (user_id);

CREATE INDEX idx_notifications_created_at
    ON notifications (created_at DESC);

CREATE INDEX idx_notifications_is_read
    ON notifications (user_id, is_read);

