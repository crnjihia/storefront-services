CREATE TABLE users (
    id UUID PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    email VARCHAR(100) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(100) NOT NULL,
    phone_number VARCHAR(20) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE user_addresses (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    street VARCHAR(255) NOT NULL,
    city VARCHAR(100) NOT NULL,
    county VARCHAR(100) NOT NULL,
    postal_code VARCHAR(20),
    is_default BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Seed initial test user
INSERT INTO users (id, username, email, password_hash, full_name, phone_number)
VALUES ('a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d', 'crnjihia', 'crnjihia@example.com', '$2a$12$e8pA7O7FwU6Z...', 'Christopher Njihia', '+254712345678')
ON CONFLICT (id) DO NOTHING;

INSERT INTO user_addresses (id, user_id, street, city, county, postal_code, is_default)
VALUES ('b2c3d4e5-f6a7-8b9c-0d1e-2f3a4b5c6d7e', 'a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d', 'Kenyatta Avenue, CBD', 'Nairobi', 'Nairobi County', '00100', true)
ON CONFLICT (id) DO NOTHING;
