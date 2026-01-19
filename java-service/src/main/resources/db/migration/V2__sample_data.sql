-- Sample data for testing
-- Customers
INSERT INTO customers (name, email, address) VALUES
    ('John Doe', 'john.doe@example.com', '123 Main St, Anytown, USA'),
    ('Jane Smith', 'jane.smith@example.com', '456 Oak Ave, Somewhere, USA')
ON CONFLICT (email) DO NOTHING;

-- Products
INSERT INTO products (name, description, price_cents, inventory_quantity, sku) VALUES
    ('Widget A', 'High-quality widget', 1999, 100, 'WID-A-001'),
    ('Widget B', 'Premium widget', 2999, 50, 'WID-B-001'),
    ('Gadget X', 'Latest gadget', 4999, 25, 'GAD-X-001')
ON CONFLICT (sku) DO NOTHING;
