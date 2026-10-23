-- Preserve imported data. Existing FKs and uniqueness constraints already cover writes.
CREATE INDEX idx_orders_owner_date ON orders(user_id,order_date);
CREATE INDEX idx_orders_expiry ON orders(status,payment_status,reserved_until);
CREATE INDEX idx_order_items_product ON order_items(product_id,order_id);
CREATE INDEX idx_movements_product_id ON inventory_movements(product_id,id);
CREATE INDEX idx_reviews_product_id ON reviews(product_id,id);
CREATE INDEX idx_pets_status_supplier ON pets(status,supplier_id);
