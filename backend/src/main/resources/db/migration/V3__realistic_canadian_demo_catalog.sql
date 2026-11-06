UPDATE retailer SET name = 'Best Buy Canada' WHERE name = 'Northstar Electronics';
UPDATE retailer SET name = 'Amazon.ca' WHERE name = 'Maple Market';
UPDATE retailer SET name = 'Sport Chek' WHERE name = 'Harbour Outfitters';

INSERT INTO retailer (name) VALUES
    ('Apple Store'),
    ('Nike Canada'),
    ('Levi''s Canada');

UPDATE product
SET name = 'Galaxy S25 128 GB',
    brand = 'Samsung',
    description = 'Samsung Galaxy S25 smartphone with a 6.2-inch Dynamic AMOLED 2X display, 128 GB storage, and 5G connectivity.',
    attributes = JSON_OBJECT(
        'displaySizeInches' VALUE 6.2, 'resolution' VALUE '2340x1080',
        'operatingSystem' VALUE 'Android 15', 'memoryGb' VALUE 12, 'storageGb' VALUE 128,
        'batteryMah' VALUE 4000, 'connectivity' VALUE JSON_ARRAY('5G', 'Wi-Fi 7', 'Bluetooth 5.4'))
WHERE model = 'OR-15-128';

UPDATE product
SET name = 'MacBook Air 13-inch (M4)',
    brand = 'Apple',
    description = 'Apple MacBook Air with M4 chip, a 13.6-inch Liquid Retina display, 16 GB unified memory, and 256 GB SSD storage.',
    attributes = JSON_OBJECT(
        'displaySizeInches' VALUE 13.6, 'resolution' VALUE '2560x1664',
        'processor' VALUE 'Apple M4', 'memoryGb' VALUE 16, 'storageType' VALUE 'SSD',
        'storageGb' VALUE 256, 'graphics' VALUE 'Apple M4 GPU',
        'operatingSystem' VALUE 'macOS Sequoia')
WHERE model = 'PB-A14-1T';

UPDATE product
SET name = 'iPad Air 11-inch (M3)',
    brand = 'Apple',
    description = 'Apple iPad Air with M3 chip, an 11-inch Liquid Retina display, 128 GB storage, and Apple Pencil support.',
    attributes = JSON_OBJECT(
        'displaySizeInches' VALUE 11.0, 'resolution' VALUE '2360x1640',
        'operatingSystem' VALUE 'iPadOS 18', 'memoryGb' VALUE 8, 'storageGb' VALUE 128,
        'connectivity' VALUE JSON_ARRAY('Wi-Fi 6E', 'Bluetooth 5.3'), 'stylusSupport' VALUE TRUE)
WHERE model = 'NT-11-256';

UPDATE product
SET name = 'Samsung 55-inch Class S90D OLED 4K Smart TV',
    brand = 'Samsung',
    description = 'Samsung 55-inch OLED 4K smart television with a 120 Hz refresh rate and HDR support.',
    attributes = JSON_OBJECT(
        'screenSizeInches' VALUE 55.0, 'resolution' VALUE '3840x2160',
        'panelType' VALUE 'OLED', 'refreshRateHz' VALUE 120,
        'hdrSupport' VALUE JSON_ARRAY('HDR10+', 'HDR10'), 'smartTvPlatform' VALUE 'Tizen')
WHERE model = 'VO-55-4K';

UPDATE product
SET name = 'Sony WH-1000XM5 Wireless Noise-Cancelling Headphones',
    brand = 'Sony',
    description = 'Sony over-ear wireless headphones with active noise cancellation and up to 30 hours of battery life.',
    attributes = JSON_OBJECT(
        'formFactor' VALUE 'over-ear', 'connectivity' VALUE 'wireless',
        'noiseCancellation' VALUE TRUE, 'batteryLifeHours' VALUE 30,
        'supportedConnectionTypes' VALUE JSON_ARRAY('Bluetooth 5.2', 'USB-C'))
WHERE model = 'SW-PRO-WL';

UPDATE product
SET name = 'Nike Sportswear Club T-Shirt',
    brand = 'Nike',
    description = 'Nike cotton-blend crew-neck short-sleeve T-shirt in a regular fit.',
    attributes = JSON_OBJECT(
        'garmentType' VALUE 't-shirt', 'size' VALUE 'M', 'sizeSystem' VALUE 'US',
        'fit' VALUE 'regular', 'material' VALUE 'cotton blend', 'color' VALUE 'black',
        'sleeveStyle' VALUE 'short')
WHERE model = 'FW-TEE-M';

UPDATE product
SET name = 'Levi''s 501 Original Fit Jeans',
    brand = 'Levi''s',
    description = 'Levi''s 501 Original Fit straight-leg jeans in a cotton denim blend.',
    attributes = JSON_OBJECT(
        'garmentType' VALUE 'jeans', 'size' VALUE '32x30', 'sizeSystem' VALUE 'US',
        'waistInches' VALUE 32, 'inseamInches' VALUE 30, 'fit' VALUE 'straight',
        'material' VALUE 'cotton denim', 'color' VALUE 'dark blue')
WHERE model = 'FW-CHINO-32-30';

UPDATE product
SET name = 'Adidas Duramo SL 2.0 Running Shoes',
    brand = 'Adidas',
    description = 'Adidas lightweight road-running shoes with a breathable mesh upper and lace closure.',
    attributes = JSON_OBJECT(
        'shoeType' VALUE 'running shoe', 'size' VALUE 10, 'sizeSystem' VALUE 'US',
        'width' VALUE 'medium', 'material' VALUE 'mesh', 'closure' VALUE 'lace',
        'intendedUse' VALUE 'road running')
WHERE model = 'TW-RUN-10';

INSERT INTO profile_excluded_brand (profile_id, brand)
SELECT DISTINCT old.profile_id,
       CASE LOWER(old.brand)
           WHEN 'orion' THEN 'Samsung'
           WHEN 'vista' THEN 'Samsung'
           WHEN 'pine' THEN 'Apple'
           WHEN 'north' THEN 'Apple'
           WHEN 'soundwave' THEN 'Sony'
           WHEN 'tideway' THEN 'Adidas'
       END
FROM profile_excluded_brand old
WHERE LOWER(old.brand) IN ('orion', 'vista', 'pine', 'north', 'soundwave', 'tideway')
  AND NOT EXISTS (
      SELECT 1
      FROM profile_excluded_brand current
      WHERE current.profile_id = old.profile_id
        AND LOWER(current.brand) = LOWER(CASE LOWER(old.brand)
            WHEN 'orion' THEN 'Samsung'
            WHEN 'vista' THEN 'Samsung'
            WHEN 'pine' THEN 'Apple'
            WHEN 'north' THEN 'Apple'
            WHEN 'soundwave' THEN 'Sony'
            WHEN 'tideway' THEN 'Adidas'
        END)
  );

INSERT INTO profile_excluded_brand (profile_id, brand)
SELECT old.profile_id, 'Nike'
FROM profile_excluded_brand old
WHERE LOWER(old.brand) = 'fieldwork'
  AND NOT EXISTS (
      SELECT 1 FROM profile_excluded_brand current
      WHERE current.profile_id = old.profile_id AND LOWER(current.brand) = 'nike'
  );

INSERT INTO profile_excluded_brand (profile_id, brand)
SELECT old.profile_id, 'Levi''s'
FROM profile_excluded_brand old
WHERE LOWER(old.brand) = 'fieldwork'
  AND NOT EXISTS (
      SELECT 1 FROM profile_excluded_brand current
      WHERE current.profile_id = old.profile_id AND LOWER(current.brand) = 'levi''s'
  );

DELETE FROM profile_excluded_brand
WHERE LOWER(brand) IN ('orion', 'vista', 'pine', 'north', 'soundwave', 'fieldwork', 'tideway');

UPDATE listing
SET retailer_id = (SELECT id FROM retailer WHERE name = 'Nike Canada')
WHERE product_id = (SELECT id FROM product WHERE model = 'FW-TEE-M')
  AND retailer_id = (SELECT id FROM retailer WHERE name = 'Best Buy Canada');

UPDATE review
SET retailer_id = (SELECT id FROM retailer WHERE name = 'Nike Canada')
WHERE product_id = (SELECT id FROM product WHERE model = 'FW-TEE-M')
  AND retailer_id = (SELECT id FROM retailer WHERE name = 'Best Buy Canada');

UPDATE listing
SET retailer_id = (SELECT id FROM retailer WHERE name = 'Levi''s Canada')
WHERE product_id = (SELECT id FROM product WHERE model = 'FW-CHINO-32-30')
  AND retailer_id = (SELECT id FROM retailer WHERE name = 'Sport Chek');

UPDATE review
SET retailer_id = (SELECT id FROM retailer WHERE name = 'Levi''s Canada')
WHERE product_id = (SELECT id FROM product WHERE model = 'FW-CHINO-32-30')
  AND retailer_id = (SELECT id FROM retailer WHERE name = 'Sport Chek');

INSERT INTO listing (product_id, retailer_id, price, condition, in_stock)
SELECT p.id, r.id, 1399.00, 'NEW', TRUE
FROM product p CROSS JOIN retailer r
WHERE p.model = 'PB-A14-1T'
  AND r.name = 'Apple Store'
  AND NOT EXISTS (
      SELECT 1 FROM listing l WHERE l.product_id = p.id AND l.retailer_id = r.id
  );

UPDATE listing SET price = 1199.99
WHERE product_id = (SELECT id FROM product WHERE model = 'OR-15-128')
  AND retailer_id = (SELECT id FROM retailer WHERE name = 'Best Buy Canada');
UPDATE listing SET price = 1149.99
WHERE product_id = (SELECT id FROM product WHERE model = 'OR-15-128')
  AND retailer_id = (SELECT id FROM retailer WHERE name = 'Amazon.ca');
UPDATE listing SET price = 1399.00
WHERE product_id = (SELECT id FROM product WHERE model = 'PB-A14-1T')
  AND retailer_id IN (SELECT id FROM retailer WHERE name IN ('Best Buy Canada', 'Apple Store'));
UPDATE listing SET price = 1349.00
WHERE product_id = (SELECT id FROM product WHERE model = 'PB-A14-1T')
  AND retailer_id = (SELECT id FROM retailer WHERE name = 'Amazon.ca');
UPDATE listing SET price = 799.00
WHERE product_id = (SELECT id FROM product WHERE model = 'NT-11-256')
  AND retailer_id = (SELECT id FROM retailer WHERE name = 'Best Buy Canada');
UPDATE listing SET price = 769.00
WHERE product_id = (SELECT id FROM product WHERE model = 'NT-11-256')
  AND retailer_id = (SELECT id FROM retailer WHERE name = 'Amazon.ca');
UPDATE listing SET price = 1898.00
WHERE product_id = (SELECT id FROM product WHERE model = 'VO-55-4K')
  AND retailer_id = (SELECT id FROM retailer WHERE name = 'Best Buy Canada');
UPDATE listing SET price = 1798.00
WHERE product_id = (SELECT id FROM product WHERE model = 'VO-55-4K')
  AND retailer_id = (SELECT id FROM retailer WHERE name = 'Amazon.ca');
UPDATE listing SET price = 449.99
WHERE product_id = (SELECT id FROM product WHERE model = 'SW-PRO-WL')
  AND retailer_id = (SELECT id FROM retailer WHERE name = 'Best Buy Canada');
UPDATE listing SET price = 399.99
WHERE product_id = (SELECT id FROM product WHERE model = 'SW-PRO-WL')
  AND retailer_id = (SELECT id FROM retailer WHERE name = 'Amazon.ca');
UPDATE listing SET price = 44.99
WHERE product_id = (SELECT id FROM product WHERE model = 'FW-TEE-M')
  AND retailer_id = (SELECT id FROM retailer WHERE name = 'Nike Canada');
UPDATE listing SET price = 39.99
WHERE product_id = (SELECT id FROM product WHERE model = 'FW-TEE-M')
  AND retailer_id = (SELECT id FROM retailer WHERE name = 'Amazon.ca');
UPDATE listing SET price = 99.95
WHERE product_id = (SELECT id FROM product WHERE model = 'FW-CHINO-32-30')
  AND retailer_id = (SELECT id FROM retailer WHERE name = 'Levi''s Canada');
UPDATE listing SET price = 89.95
WHERE product_id = (SELECT id FROM product WHERE model = 'FW-CHINO-32-30')
  AND retailer_id = (SELECT id FROM retailer WHERE name = 'Amazon.ca');
UPDATE listing SET price = 139.99
WHERE product_id = (SELECT id FROM product WHERE model = 'TW-RUN-10')
  AND retailer_id = (SELECT id FROM retailer WHERE name = 'Sport Chek');
UPDATE listing SET price = 129.99
WHERE product_id = (SELECT id FROM product WHERE model = 'TW-RUN-10')
  AND retailer_id = (SELECT id FROM retailer WHERE name = 'Amazon.ca');

UPDATE price_point
SET price = (SELECT l.price FROM listing l WHERE l.id = price_point.listing_id)
    + CASE WHEN recorded_at < TIMESTAMP WITH TIME ZONE '2025-02-01 00:00:00+00' THEN 20 ELSE 0 END;

UPDATE review SET text = 'Synthetic review: bright AMOLED display and smooth everyday performance.'
WHERE product_id = (SELECT id FROM product WHERE model = 'OR-15-128')
  AND retailer_id = (SELECT id FROM retailer WHERE name = 'Best Buy Canada');
UPDATE review SET text = 'Synthetic review: compact design, capable cameras, and dependable battery life.'
WHERE product_id = (SELECT id FROM product WHERE model = 'OR-15-128')
  AND retailer_id = (SELECT id FROM retailer WHERE name = 'Amazon.ca');
UPDATE review SET text = 'Synthetic review: quiet operation, fast M4 performance, and a sharp display.'
WHERE product_id = (SELECT id FROM product WHERE model = 'PB-A14-1T')
  AND retailer_id = (SELECT id FROM retailer WHERE name = 'Best Buy Canada');
UPDATE review SET text = 'Synthetic review: lightweight build and long battery life for school and work.'
WHERE product_id = (SELECT id FROM product WHERE model = 'PB-A14-1T')
  AND retailer_id = (SELECT id FROM retailer WHERE name = 'Amazon.ca');
UPDATE review SET text = 'Synthetic review: responsive performance and a light, portable design.'
WHERE product_id = (SELECT id FROM product WHERE model = 'NT-11-256')
  AND retailer_id = (SELECT id FROM retailer WHERE name = 'Best Buy Canada');
UPDATE review SET text = 'Synthetic review: useful size for reading, drawing, and video.'
WHERE product_id = (SELECT id FROM product WHERE model = 'NT-11-256')
  AND retailer_id = (SELECT id FROM retailer WHERE name = 'Amazon.ca');
UPDATE review SET text = 'Synthetic review: deep OLED contrast and smooth motion in games.'
WHERE product_id = (SELECT id FROM product WHERE model = 'VO-55-4K')
  AND retailer_id = (SELECT id FROM retailer WHERE name = 'Best Buy Canada');
UPDATE review SET text = 'Synthetic review: vivid picture quality and straightforward setup.'
WHERE product_id = (SELECT id FROM product WHERE model = 'VO-55-4K')
  AND retailer_id = (SELECT id FROM retailer WHERE name = 'Amazon.ca');
UPDATE review SET text = 'Synthetic review: comfortable fit and effective active noise cancellation.'
WHERE product_id = (SELECT id FROM product WHERE model = 'SW-PRO-WL')
  AND retailer_id = (SELECT id FROM retailer WHERE name = 'Best Buy Canada');
UPDATE review SET text = 'Synthetic review: clear sound and practical battery life for travel.'
WHERE product_id = (SELECT id FROM product WHERE model = 'SW-PRO-WL')
  AND retailer_id = (SELECT id FROM retailer WHERE name = 'Amazon.ca');
UPDATE review SET text = 'Synthetic review: soft fabric and a comfortable everyday fit.'
WHERE product_id = (SELECT id FROM product WHERE model = 'FW-TEE-M')
  AND retailer_id = (SELECT id FROM retailer WHERE name = 'Nike Canada');
UPDATE review SET text = 'Synthetic review: breathable material and a simple, versatile style.'
WHERE product_id = (SELECT id FROM product WHERE model = 'FW-TEE-M')
  AND retailer_id = (SELECT id FROM retailer WHERE name = 'Amazon.ca');
UPDATE review SET text = 'Synthetic review: classic straight fit and sturdy denim feel.'
WHERE product_id = (SELECT id FROM product WHERE model = 'FW-CHINO-32-30')
  AND retailer_id = (SELECT id FROM retailer WHERE name = 'Levi''s Canada');
UPDATE review SET text = 'Synthetic review: familiar 501 fit and durable construction.'
WHERE product_id = (SELECT id FROM product WHERE model = 'FW-CHINO-32-30')
  AND retailer_id = (SELECT id FROM retailer WHERE name = 'Amazon.ca');
UPDATE review SET text = 'Synthetic review: lightweight feel and a comfortable ride on paved routes.'
WHERE product_id = (SELECT id FROM product WHERE model = 'TW-RUN-10')
  AND retailer_id = (SELECT id FROM retailer WHERE name = 'Sport Chek');
UPDATE review SET text = 'Synthetic review: breathable upper and reliable everyday road-running comfort.'
WHERE product_id = (SELECT id FROM product WHERE model = 'TW-RUN-10')
  AND retailer_id = (SELECT id FROM retailer WHERE name = 'Amazon.ca');

INSERT INTO price_point (listing_id, recorded_at, price)
SELECT l.id, TIMESTAMP WITH TIME ZONE '2025-01-01 12:00:00+00', l.price + 20
FROM listing l
JOIN product p ON p.id = l.product_id
JOIN retailer r ON r.id = l.retailer_id
WHERE p.model = 'PB-A14-1T' AND r.name = 'Apple Store';

INSERT INTO price_point (listing_id, recorded_at, price)
SELECT l.id, TIMESTAMP WITH TIME ZONE '2025-02-01 12:00:00+00', l.price
FROM listing l
JOIN product p ON p.id = l.product_id
JOIN retailer r ON r.id = l.retailer_id
WHERE p.model = 'PB-A14-1T' AND r.name = 'Apple Store';
