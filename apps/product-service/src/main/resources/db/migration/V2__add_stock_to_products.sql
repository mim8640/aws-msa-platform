ALTER TABLE products
ADD COLUMN stock INT NOT NULL DEFAULT 0;

ALTER TABLE products
ADD CONSTRAINT chk_products_stock CHECK (stock >= 0);
