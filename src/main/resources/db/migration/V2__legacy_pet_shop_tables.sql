-- Add legacy tables absent from the Spring target schema; compatible BIGINT keys and FKs.
CREATE TABLE carts (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 user_id BIGINT NOT NULL,
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 CONSTRAINT uq_cart_user UNIQUE(user_id),
 CONSTRAINT fk_cart_user FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE cart_items (
 cart_id BIGINT NOT NULL,
 product_id BIGINT NOT NULL,
 quantity INT NOT NULL,
 PRIMARY KEY(cart_id,product_id),
 CONSTRAINT fk_cart_item_cart FOREIGN KEY(cart_id) REFERENCES carts(id) ON DELETE CASCADE,
 CONSTRAINT fk_cart_item_product FOREIGN KEY(product_id) REFERENCES products(id) ON DELETE CASCADE,
 CONSTRAINT chk_cart_item_quantity CHECK(quantity>0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE coupons (
 code VARCHAR(50) NOT NULL PRIMARY KEY,
 discount_type ENUM('fixed','percent') NOT NULL,
 discount_value DECIMAL(12,2) NOT NULL,
 min_subtotal DECIMAL(12,2) NOT NULL DEFAULT 0,
 starts_at DATETIME NOT NULL,
 ends_at DATETIME NOT NULL,
 usage_limit INT NULL,
 used_count INT NOT NULL DEFAULT 0,
 is_active BOOLEAN NOT NULL DEFAULT TRUE,
 CONSTRAINT chk_coupon_discount CHECK(discount_value>0),
 CONSTRAINT chk_coupon_percent CHECK(discount_type<>'percent' OR discount_value<=100)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE pet_health_records (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 pet_id BIGINT NOT NULL,
 record_date DATE NOT NULL,
 record_type VARCHAR(100) NOT NULL,
 description TEXT NULL,
 vet_name VARCHAR(100) NULL,
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 CONSTRAINT fk_health_record_pet FOREIGN KEY(pet_id) REFERENCES pets(id) ON DELETE CASCADE,
 INDEX idx_health_pet_date(pet_id,record_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE order_events (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 order_id BIGINT NOT NULL,
 actor_id BIGINT NULL,
 event_type VARCHAR(40) NOT NULL,
 old_value VARCHAR(40) NULL,
 new_value VARCHAR(40) NOT NULL,
 note VARCHAR(255) NULL,
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 CONSTRAINT fk_order_event_order FOREIGN KEY(order_id) REFERENCES orders(id) ON DELETE RESTRICT,
 CONSTRAINT fk_order_event_actor FOREIGN KEY(actor_id) REFERENCES users(id) ON DELETE SET NULL,
 INDEX idx_event_order_created(order_id,created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE shipping_info (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 order_id BIGINT NOT NULL,
 recipient_name VARCHAR(100) NULL,
 recipient_phone VARCHAR(20) NULL,
 delivery_method ENUM('pickup','pet_taxi','shipping') NOT NULL,
 address VARCHAR(255) NULL,
 note VARCHAR(255) NULL,
 CONSTRAINT uq_shipping_order UNIQUE(order_id),
 CONSTRAINT fk_shipping_order FOREIGN KEY(order_id) REFERENCES orders(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

