UPDATE monitoring
SET card_name = REGEXP_REPLACE(card_name, '(?i)\\s+lv\\.?\\s*\\d+(?:[.,]\\d+)?\\s*$', '');

UPDATE collection_card
SET card_name = REGEXP_REPLACE(card_name, '(?i)\\s+lv\\.?\\s*\\d+(?:[.,]\\d+)?\\s*$', '');

UPDATE card_image
SET card_name = REGEXP_REPLACE(card_name, '(?i)\\s+lv\\.?\\s*\\d+(?:[.,]\\d+)?\\s*$', '');
