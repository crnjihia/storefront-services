CREATE TABLE payments (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL UNIQUE,
    method VARCHAR(20) NOT NULL,
    amount NUMERIC(19,2) NOT NULL,
    status VARCHAR(20) NOT NULL,
    mpesa_receipt VARCHAR(255),
    phone VARCHAR(20),
    failure_reason VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE idempotency_keys (
    id UUID PRIMARY KEY,
    idempotency_key VARCHAR(100) NOT NULL UNIQUE,
    order_id UUID NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    response_body TEXT NOT NULL,
    status_code INT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE outbox_events (
    id UUID PRIMARY KEY,
    aggregate_id UUID NOT NULL,
    topic VARCHAR(100) NOT NULL,
    payload TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMP WITH TIME ZONE
);

CREATE TABLE processed_events (
    consumer_name VARCHAR(100) NOT NULL,
    event_id UUID NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (consumer_name, event_id)
);
