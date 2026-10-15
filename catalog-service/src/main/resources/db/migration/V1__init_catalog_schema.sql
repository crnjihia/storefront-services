CREATE TABLE products (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    price NUMERIC(19,2) NOT NULL,
    sku VARCHAR(100) NOT NULL UNIQUE,
    category VARCHAR(100) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE stocks (
    id UUID PRIMARY KEY,
    product_id UUID NOT NULL UNIQUE,
    available_quantity INT NOT NULL,
    reserved_quantity INT NOT NULL DEFAULT 0,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE stock_reservations (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL,
    product_id UUID NOT NULL,
    quantity INT NOT NULL,
    status VARCHAR(20) NOT NULL,
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

-- Seed Catalog Products and Stock
INSERT INTO products (id, name, description, price, sku, category)
VALUES
    ('c3d4e5f6-a7b8-9c0d-1e2f-3a4b5c6d7e8f', 'Safaricom 4G High-Speed Wi-Fi Router', 'Portable 4G LTE router with bundled 10GB data for Kenyan homes and SMEs', 2500.00, 'SAF-4G-RTR-01', 'Networking'),
    ('d4e5f6a7-b8c9-0d1e-2f3a-4b5c6d7e8f9a', 'Tecno Spark 20 Pro+ (256GB / 8GB RAM)', 'AMOLED curved display with 108MP camera, dual stereo speakers', 28500.00, 'TEC-SPK-20P', 'Smartphones'),
    ('e5f6a7b8-c9d0-1e2f-3a4b-5c6d7e8f9a0b', 'Solar Water Pump Kit 500W', 'Submersible borehole solar water pump ideal for agricultural irrigation', 42000.00, 'SLR-WTR-PMP-500', 'Agriculture')
ON CONFLICT (id) DO NOTHING;

INSERT INTO stocks (id, product_id, available_quantity, reserved_quantity)
VALUES
    ('f6a7b8c9-d0e1-2f3a-4b5c-6d7e8f9a0b1c', 'c3d4e5f6-a7b8-9c0d-1e2f-3a4b5c6d7e8f', 150, 0),
    ('a7b8c9d0-e1f2-3a4b-5c6d-7e8f9a0b1c2d', 'd4e5f6a7-b8c9-0d1e-2f3a-4b5c6d7e8f9a', 45, 0),
    ('b8c9d0e1-f2a3-4b5c-6d7e-8f9a0b1c2d3e', 'e5f6a7b8-c9d0-1e2f-3a4b-5c6d7e8f9a0b', 20, 0)
ON CONFLICT (id) DO NOTHING;
