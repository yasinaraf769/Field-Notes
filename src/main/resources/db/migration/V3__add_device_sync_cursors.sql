-- V3: Device sync cursor table.
-- Stores the last server-side updated_at timestamp each (user, device) pair
-- successfully received so that incremental delta pull queries are possible.

CREATE TABLE device_sync_cursors (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID        NOT NULL,
    device_id   VARCHAR(255) NOT NULL,
    last_sync_at TIMESTAMPTZ NOT NULL DEFAULT '1970-01-01T00:00:00Z',
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_device_sync_cursors_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT uq_device_sync_cursors_user_device
        UNIQUE (user_id, device_id)
);

CREATE INDEX idx_device_sync_cursors_user_id ON device_sync_cursors(user_id);
