CREATE TABLE orders (
    id VARCHAR(100) PRIMARY KEY,
    status VARCHAR(50) NOT NULL
);

CREATE TABLE kitchen_tickets (
    order_id VARCHAR(100) PRIMARY KEY,
    status VARCHAR(50) NOT NULL
);

CREATE TABLE dishes (
    id SERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    recipe VARCHAR(255) NOT NULL,
    preparation_time INTEGER NOT NULL
);

CREATE TABLE invoices (
    id BIGSERIAL PRIMARY KEY,
    order_id VARCHAR(100) NOT NULL,
    amount DECIMAL(10, 2) NOT NULL,
    status VARCHAR(50) NOT NULL
);
