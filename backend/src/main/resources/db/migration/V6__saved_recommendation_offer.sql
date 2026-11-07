ALTER TABLE saved_item ADD COLUMN saved_offer_retailer VARCHAR(120);
ALTER TABLE saved_item ADD COLUMN saved_offer_price NUMERIC(12, 2);
ALTER TABLE saved_item ADD COLUMN saved_offer_condition VARCHAR(40);

ALTER TABLE saved_item ADD CONSTRAINT ck_saved_item_offer
    CHECK ((saved_offer_retailer IS NULL AND saved_offer_price IS NULL AND saved_offer_condition IS NULL)
        OR (saved_offer_retailer IS NOT NULL AND saved_offer_price IS NOT NULL
            AND saved_offer_price >= 0 AND saved_offer_condition IS NOT NULL));
