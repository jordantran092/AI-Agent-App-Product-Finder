UPDATE product SET model = 'SM-S931W-128GB' WHERE model = 'OR-15-128';
UPDATE product SET model = 'A3240-16GB-256GB' WHERE model = 'PB-A14-1T';
UPDATE product SET model = 'A3266-128GB-WIFI' WHERE model = 'NT-11-256';
UPDATE product SET model = 'QN55S90DAFXZC' WHERE model = 'VO-55-4K';
UPDATE product SET model = 'WH1000XM5-B' WHERE model = 'SW-PRO-WL';
UPDATE product SET model = 'AR4997-010' WHERE model = 'FW-TEE-M';
UPDATE product SET model = '00501-0165' WHERE model = 'FW-CHINO-32-30';
UPDATE product SET model = 'IF9390' WHERE model = 'TW-RUN-10';

INSERT INTO profile_excluded_brand (profile_id, brand)
SELECT DISTINCT old.profile_id,
       CASE LOWER(old.brand)
           WHEN 'orion' THEN 'Samsung'
           WHEN 'vista' THEN 'Samsung'
           WHEN 'pine' THEN 'Apple'
           WHEN 'north' THEN 'Apple'
           WHEN 'soundwave' THEN 'Sony'
           WHEN 'tideway' THEN 'Adidas'
           WHEN 'fieldwork' THEN 'Nike'
       END
FROM profile_excluded_brand old
WHERE LOWER(old.brand) IN ('orion', 'vista', 'pine', 'north', 'soundwave', 'tideway', 'fieldwork')
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
            WHEN 'fieldwork' THEN 'Nike'
        END)
  );

INSERT INTO profile_excluded_brand (profile_id, brand)
SELECT old.profile_id, 'Levi''s'
FROM profile_excluded_brand old
WHERE LOWER(old.brand) = 'fieldwork'
  AND NOT EXISTS (
      SELECT 1
      FROM profile_excluded_brand current
      WHERE current.profile_id = old.profile_id AND LOWER(current.brand) = 'levi''s'
  );

DELETE FROM profile_excluded_brand
WHERE LOWER(brand) IN ('orion', 'vista', 'pine', 'north', 'soundwave', 'fieldwork', 'tideway');
