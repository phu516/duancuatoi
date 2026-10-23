-- Initial schema for an empty pet_shop_api database. Never baseline an unknown existing schema.
CREATE TABLE users (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 full_name VARCHAR(100) NOT NULL, email VARCHAR(100) NOT NULL,
 password_hash VARCHAR(100) NOT NULL, phone VARCHAR(20), address VARCHAR(255),
 role VARCHAR(20) NOT NULL DEFAULT 'customer', is_active BOOLEAN NOT NULL DEFAULT TRUE,
 session_version BIGINT NOT NULL DEFAULT 0, created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 CONSTRAINT uq_users_email UNIQUE(email),
 CONSTRAINT chk_user_role CHECK(role IN ('customer','staff','admin'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE categories (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, name VARCHAR(100) NOT NULL,
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE products (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, category_id BIGINT NOT NULL,
 product_type VARCHAR(20) NOT NULL, name VARCHAR(150) NOT NULL, description VARCHAR(10000),
 price DECIMAL(12,2) NOT NULL, stock_quantity INT NOT NULL DEFAULT 0, image_url VARCHAR(1000),
 is_active BOOLEAN NOT NULL DEFAULT TRUE, created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 CONSTRAINT fk_product_category FOREIGN KEY(category_id) REFERENCES categories(id),
 CONSTRAINT chk_product_type CHECK(product_type IN ('pet','accessory','food')),
 CONSTRAINT chk_product_price CHECK(price>0),
 CONSTRAINT chk_product_stock CHECK(stock_quantity BETWEEN 0 AND 1000000),
 CONSTRAINT chk_pet_stock CHECK(product_type <> 'pet' OR stock_quantity IN (0,1)),
 INDEX idx_products_catalog(is_active,category_id,product_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
-- Supporting tables enable truthful pet details, review summaries and history deletion checks.
-- They do not expose order/pet/review endpoints in this delivery.
CREATE TABLE suppliers (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, name VARCHAR(150) NOT NULL,
 contact_info VARCHAR(255), address VARCHAR(255),
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE orders (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, user_id BIGINT NOT NULL,
 order_date TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 status VARCHAR(20) NOT NULL DEFAULT 'pending',
 payment_method VARCHAR(20) NOT NULL, payment_status VARCHAR(20) NOT NULL DEFAULT 'unpaid',
 subtotal DECIMAL(12,2) NOT NULL, shipping_fee DECIMAL(12,2) NOT NULL DEFAULT 0,
 discount_amount DECIMAL(12,2) NOT NULL DEFAULT 0, total_amount DECIMAL(12,2) NOT NULL,
 coupon_code VARCHAR(50), reserved_until TIMESTAMP(6) NULL,
 idempotency_key VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin, request_hash CHAR(64),
 cancelled_at TIMESTAMP(6) NULL, cancelled_by BIGINT NULL, cancel_reason VARCHAR(255),
 CONSTRAINT fk_order_user FOREIGN KEY(user_id) REFERENCES users(id),
 CONSTRAINT fk_order_cancel_actor FOREIGN KEY(cancelled_by) REFERENCES users(id),
 CONSTRAINT uq_order_key UNIQUE(user_id,idempotency_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE pets (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, product_id BIGINT NOT NULL, supplier_id BIGINT NULL,
 breed VARCHAR(100), age VARCHAR(50), gender VARCHAR(10), color VARCHAR(50), weight DECIMAL(6,2),
 is_neutered BOOLEAN NOT NULL DEFAULT FALSE, vaccination_status VARCHAR(255),
 status VARCHAR(20) NOT NULL DEFAULT 'available', reserved_order_id BIGINT NULL, reserved_until TIMESTAMP(6) NULL,
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 CONSTRAINT uq_pet_product UNIQUE(product_id),
 CONSTRAINT fk_pet_product FOREIGN KEY(product_id) REFERENCES products(id),
 CONSTRAINT fk_pet_supplier FOREIGN KEY(supplier_id) REFERENCES suppliers(id) ON DELETE SET NULL,
 CONSTRAINT fk_pet_order FOREIGN KEY(reserved_order_id) REFERENCES orders(id),
 CONSTRAINT chk_pet_status CHECK(status IN ('available','reserved','sold'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE order_items (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, order_id BIGINT NOT NULL, product_id BIGINT NOT NULL,
 product_name VARCHAR(150) NOT NULL, quantity INT NOT NULL, unit_price DECIMAL(12,2) NOT NULL,
 CONSTRAINT fk_item_order FOREIGN KEY(order_id) REFERENCES orders(id),
 CONSTRAINT fk_item_product FOREIGN KEY(product_id) REFERENCES products(id),
 CONSTRAINT uq_order_product UNIQUE(order_id,product_id),
 CONSTRAINT chk_item_quantity CHECK(quantity>0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE inventory_movements (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, product_id BIGINT NOT NULL, order_id BIGINT NULL, actor_id BIGINT NULL,
 quantity_change INT NOT NULL, quantity_before INT NOT NULL, quantity_after INT NOT NULL,
 reason VARCHAR(255) NOT NULL, reference VARCHAR(100),
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 CONSTRAINT fk_movement_product FOREIGN KEY(product_id) REFERENCES products(id),
 CONSTRAINT fk_movement_order FOREIGN KEY(order_id) REFERENCES orders(id),
 CONSTRAINT fk_movement_actor FOREIGN KEY(actor_id) REFERENCES users(id),
 CONSTRAINT chk_movement_stock CHECK(quantity_after BETWEEN 0 AND 1000000)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE reviews (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, product_id BIGINT NOT NULL, user_id BIGINT NOT NULL,
 rating INT NOT NULL, comment VARCHAR(10000),
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 CONSTRAINT fk_review_product FOREIGN KEY(product_id) REFERENCES products(id),
 CONSTRAINT fk_review_user FOREIGN KEY(user_id) REFERENCES users(id),
 CONSTRAINT uq_review_owner UNIQUE(user_id,product_id),
 CONSTRAINT chk_rating CHECK(rating BETWEEN 1 AND 5)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

