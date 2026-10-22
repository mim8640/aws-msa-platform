CREATE TABLE products (
    id BIGINT NOT NULL AUTO_INCREMENT,
    owner_id BIGINT NOT NULL,
    name VARCHAR(255) NOT NULL,
    price DECIMAL(15,2) NOT NULL,
    description VARCHAR(2000),
    image_key VARCHAR(512),
    PRIMARY KEY (id),
    INDEX idx_products_owner_id (owner_id),
    CONSTRAINT chk_products_price CHECK (price >= 0)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci;