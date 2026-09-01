CREATE TABLE tasks (
    id         BIGSERIAL PRIMARY KEY,
    title      VARCHAR(200) NOT NULL,
    status     VARCHAR(32)  NOT NULL,
    created_at TIMESTAMP    NOT NULL
);

CREATE INDEX idx_tasks_status ON tasks (status);
