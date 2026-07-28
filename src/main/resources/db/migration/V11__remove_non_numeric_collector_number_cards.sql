DELETE FROM user_collection_card
WHERE collection_card_id IN (
    SELECT id
    FROM collection_card
    WHERE collector_number IS NULL OR NOT REGEXP_LIKE(collector_number, '^[0-9]+$')
);

DELETE FROM collection_card
WHERE collector_number IS NULL OR NOT REGEXP_LIKE(collector_number, '^[0-9]+$');

UPDATE collection_set
SET card_count = (
    SELECT COUNT(*)
    FROM collection_card
    WHERE collection_card.collection_set_id = collection_set.id
);

DELETE FROM card_image
WHERE NOT REGEXP_LIKE(collector_number, '^[0-9]+$');

DELETE FROM monitoring
WHERE NOT REGEXP_LIKE(card_version, '(?i)^.*\\|\\s*[0-9]+\\s*(?:/\\s*[0-9]+)?\\s*$');
