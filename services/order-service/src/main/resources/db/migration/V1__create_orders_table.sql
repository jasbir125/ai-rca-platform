CREATE TABLE orders (
    id               UUID PRIMARY KEY,
    item_description VARCHAR(255)   NOT NULL,
    amount           NUMERIC(12, 2) NOT NULL,
    status           VARCHAR(20)    NOT NULL,
    payment_id       VARCHAR(64),
    failure_reason   VARCHAR(64),
    created_at       TIMESTAMPTZ    NOT NULL,
    updated_at       TIMESTAMPTZ    NOT NULL
);

CREATE INDEX idx_orders_status ON orders (status);
CREATE INDEX idx_orders_created_at ON orders (created_at);
