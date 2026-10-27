-- Praan-Setu schema, version 1.
-- All timestamps are epoch milliseconds (UTC). All ids are UUID strings except node ids (short labels).
-- Statements are separated by semicolons at end of line; do not put semicolons inside literals.

CREATE TABLE nodes (
    id                TEXT PRIMARY KEY,
    label             TEXT NOT NULL,
    responder_station INTEGER NOT NULL DEFAULT 0 CHECK (responder_station IN (0, 1)),
    online            INTEGER NOT NULL DEFAULT 1 CHECK (online IN (0, 1)),
    compromised       INTEGER NOT NULL DEFAULT 0 CHECK (compromised IN (0, 1)),
    pos_x             REAL NOT NULL DEFAULT 100,
    pos_y             REAL NOT NULL DEFAULT 100,
    public_key        BLOB NOT NULL,
    private_key       BLOB NOT NULL,
    created_at        INTEGER NOT NULL,
    updated_at        INTEGER NOT NULL
);

CREATE TABLE links (
    node_a     TEXT NOT NULL REFERENCES nodes(id) ON DELETE CASCADE,
    node_b     TEXT NOT NULL REFERENCES nodes(id) ON DELETE CASCADE,
    up         INTEGER NOT NULL DEFAULT 1 CHECK (up IN (0, 1)),
    updated_at INTEGER NOT NULL,
    PRIMARY KEY (node_a, node_b),
    CHECK (node_a < node_b)
);

CREATE TABLE users (
    id              TEXT PRIMARY KEY,
    username        TEXT NOT NULL UNIQUE COLLATE NOCASE,
    full_name       TEXT NOT NULL,
    role            TEXT NOT NULL CHECK (role IN ('VICTIM', 'RESPONDER')),
    password_hash   TEXT NOT NULL,
    phone           TEXT,
    organization    TEXT,
    node_id         TEXT REFERENCES nodes(id) ON DELETE SET NULL,
    failed_attempts INTEGER NOT NULL DEFAULT 0,
    locked_until    INTEGER,
    last_login_at   INTEGER,
    created_at      INTEGER NOT NULL,
    updated_at      INTEGER NOT NULL
);

CREATE TABLE emergency_alerts (
    id                    TEXT PRIMARY KEY,
    sender_user_id        TEXT NOT NULL REFERENCES users(id),
    source_node           TEXT NOT NULL,
    category              TEXT NOT NULL CHECK (category IN ('MEDICAL', 'TRAPPED', 'FIRE', 'FLOOD', 'EARTHQUAKE', 'OTHER')),
    severity              TEXT NOT NULL CHECK (severity IN ('LOW', 'MODERATE', 'HIGH', 'CRITICAL')),
    description           TEXT NOT NULL,
    location              TEXT,
    status                TEXT NOT NULL CHECK (status IN ('PENDING', 'QUEUED', 'SENT', 'DELIVERED', 'ACKNOWLEDGED', 'RESOLVED', 'FAILED')),
    message_id            TEXT UNIQUE,
    received_node_id      TEXT,
    received_at           INTEGER,
    assigned_responder_id TEXT REFERENCES users(id),
    acknowledged_by       TEXT REFERENCES users(id),
    acknowledged_at       INTEGER,
    resolved_by           TEXT REFERENCES users(id),
    resolved_at           INTEGER,
    resolution_note       TEXT,
    created_at            INTEGER NOT NULL,
    updated_at            INTEGER NOT NULL
);

CREATE TABLE messages (
    id                TEXT PRIMARY KEY,
    kind              TEXT NOT NULL CHECK (kind IN ('TEXT', 'SOS')),
    sender_user_id    TEXT REFERENCES users(id),
    recipient_user_id TEXT REFERENCES users(id),
    source_node       TEXT NOT NULL,
    dest_node         TEXT,
    alert_id          TEXT REFERENCES emergency_alerts(id) ON DELETE CASCADE,
    priority          INTEGER NOT NULL,
    status            TEXT NOT NULL CHECK (status IN ('QUEUED', 'SENT', 'DELIVERED', 'ACKNOWLEDGED', 'FAILED')),
    attempts          INTEGER NOT NULL DEFAULT 0,
    max_attempts      INTEGER NOT NULL,
    hop_limit         INTEGER NOT NULL,
    cipher_salt       BLOB,
    cipher_nonce      BLOB,
    ciphertext        BLOB,
    last_route        TEXT,
    hop_count         INTEGER,
    note              TEXT,
    created_at        INTEGER NOT NULL,
    updated_at        INTEGER NOT NULL,
    expires_at        INTEGER NOT NULL,
    sent_at           INTEGER,
    received_at       INTEGER,
    delivered_at      INTEGER,
    read_at           INTEGER,
    acknowledged_at   INTEGER
);

CREATE TABLE delivery_receipts (
    id           TEXT PRIMARY KEY,
    message_id   TEXT NOT NULL REFERENCES messages(id) ON DELETE CASCADE,
    receipt_type TEXT NOT NULL CHECK (receipt_type IN ('DELIVERED', 'READ')),
    from_node    TEXT NOT NULL,
    route        TEXT,
    hop_count    INTEGER NOT NULL DEFAULT 0,
    created_at   INTEGER NOT NULL,
    UNIQUE (message_id, receipt_type)
);

CREATE TABLE audit_events (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    created_at   INTEGER NOT NULL,
    category     TEXT NOT NULL,
    event_type   TEXT NOT NULL,
    level        TEXT NOT NULL,
    actor        TEXT,
    node_id      TEXT,
    peer_node_id TEXT,
    reference_id TEXT,
    details      TEXT
);

CREATE INDEX idx_messages_status ON messages(status);
CREATE INDEX idx_messages_sender ON messages(sender_user_id);
CREATE INDEX idx_messages_recipient ON messages(recipient_user_id);
CREATE INDEX idx_alerts_status ON emergency_alerts(status);
CREATE INDEX idx_alerts_received ON emergency_alerts(received_at);
CREATE INDEX idx_audit_created ON audit_events(created_at);
CREATE INDEX idx_audit_reference ON audit_events(reference_id);
