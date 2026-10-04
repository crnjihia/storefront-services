CREATE TABLE orders (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL,
    total NUMERIC(19,2) NOT NULL,
    created_at TIMESTAMP NOT NULL
);

CREATE TABLE order_items (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES orders(id),
    product_id UUID NOT NULL,
    quantity INT NOT NULL,
    price NUMERIC(19,2) NOT NULL,
    created_at TIMESTAMP NOT NULL
);

CREATE TABLE saga_state (
    order_id UUID PRIMARY KEY,
    step VARCHAR(30) NOT NULL,
    status VARCHAR(20) NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    failure_reason VARCHAR(255)
);
