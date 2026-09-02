CREATE TABLE agents (
    id                 VARCHAR(32)  PRIMARY KEY,
    name               VARCHAR(120) NOT NULL,
    active_order_count INTEGER      NOT NULL DEFAULT 0,
    status             VARCHAR(32)  NOT NULL,
    zone               VARCHAR(64),
    max_capacity       INTEGER
);

CREATE TABLE orders (
    id                VARCHAR(32)  PRIMARY KEY,
    description       VARCHAR(255) NOT NULL,
    assigned_agent_id VARCHAR(32)  REFERENCES agents (id),
    status            VARCHAR(32)  NOT NULL,
    created_at        TIMESTAMPTZ  NOT NULL,
    zone              VARCHAR(64),
    weight_class      VARCHAR(32),
    sla_deadline      TIMESTAMPTZ
);

CREATE INDEX idx_orders_status ON orders (status);
CREATE INDEX idx_orders_assigned_agent ON orders (assigned_agent_id);

-- Suggestions reference orders and agents by id without a foreign key: they are advisory
-- records that outlive the rows they point at. See NOTES.md.
CREATE TABLE reassignment_suggestions (
    id                   BIGSERIAL    PRIMARY KEY,
    order_id             VARCHAR(32)  NOT NULL,
    recommended_agent_id VARCHAR(32)  NOT NULL,
    confidence           NUMERIC(3,2),
    reasoning            TEXT,
    status               VARCHAR(32)  NOT NULL,
    trigger_reason       VARCHAR(32),
    strategy_used        VARCHAR(64),
    created_at           TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_suggestions_order ON reassignment_suggestions (order_id);
CREATE INDEX idx_suggestions_status ON reassignment_suggestions (status);
