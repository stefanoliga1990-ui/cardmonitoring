-- Trainer Kit products are intentionally outside the application's curated catalogue.
-- Remove their user data and cached images so they cannot remain visible after this release.
DELETE FROM user_collection_card
WHERE collection_card_id IN (
    SELECT id
    FROM collection_card
    WHERE collection_set_id IN (
        SELECT id FROM collection_set WHERE LOWER(name) LIKE '%trainer kit%'
    )
);

DELETE FROM user_collection
WHERE collection_set_id IN (
    SELECT id FROM collection_set WHERE LOWER(name) LIKE '%trainer kit%'
);

DELETE FROM card_image
WHERE expansion_id IN (
    SELECT expansion_id FROM collection_set WHERE LOWER(name) LIKE '%trainer kit%'
    UNION
    SELECT expansion_id FROM monitoring WHERE LOWER(expansion_name) LIKE '%trainer kit%'
);

DELETE FROM pokemon_tcg_set_mapping
WHERE cardtrader_expansion_id IN (
    SELECT expansion_id FROM collection_set WHERE LOWER(name) LIKE '%trainer kit%'
    UNION
    SELECT expansion_id FROM monitoring WHERE LOWER(expansion_name) LIKE '%trainer kit%'
);

DELETE FROM monitoring
WHERE LOWER(expansion_name) LIKE '%trainer kit%';

DELETE FROM collection_set
WHERE LOWER(name) LIKE '%trainer kit%';
