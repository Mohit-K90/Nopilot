CREATE TABLE IF NOT EXISTS ai_repository_indexes (
    repository_id UUID PRIMARY KEY,
    user_email VARCHAR(320) NOT NULL,
    status VARCHAR(20) NOT NULL,
    file_count INTEGER NOT NULL DEFAULT 0,
    chunk_count INTEGER NOT NULL DEFAULT 0,
    failure_reason VARCHAR(1000),
    indexed_at TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_ai_repository_indexes_user
    ON ai_repository_indexes(user_email);
