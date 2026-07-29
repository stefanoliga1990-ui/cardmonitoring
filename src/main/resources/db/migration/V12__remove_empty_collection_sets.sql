-- Keep the stored card count aligned with the actual cards before identifying empty sets.
UPDATE collection_set
SET card_count = (
    SELECT COUNT(*)
    FROM collection_card
    WHERE collection_card.collection_set_id = collection_set.id
);

-- Cached image URLs belonging only to an empty set are no longer useful.
DELETE FROM card_image
WHERE expansion_id IN (
    SELECT expansion_id
    FROM collection_set
    WHERE card_count = 0
);

-- The dependent ownership rows are removed through the ON DELETE CASCADE
-- relationship from user_collection.
DELETE FROM user_collection
WHERE collection_set_id IN (
    SELECT id
    FROM collection_set
    WHERE card_count = 0
);

-- collection_card rows are removed through the ON DELETE CASCADE relationship.
DELETE FROM collection_set
WHERE card_count = 0;
