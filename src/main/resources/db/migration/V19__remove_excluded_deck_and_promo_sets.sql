-- These sets are intentionally outside the curated catalogue. Existing user
-- data is removed as the excluded expansions can no longer be refreshed.
DELETE FROM user_collection_card
WHERE collection_card_id IN (
    SELECT id
    FROM collection_card
    WHERE collection_set_id IN (
        SELECT id FROM collection_set WHERE
            code IN ('ba-20', 'nbsp', 'playprizep', 'clb', 'clc', 'clv', 'swshbs',
                'wcd2004', 'wcd2005', 'wcd2006', 'wcd2007', 'wcd2008', 'wcd2009',
                'wcd2010', 'wcd2011', 'wcd2012', 'wcd2013', 'wcd2014', 'wcd2015',
                'wcd2016', 'wcd2017', 'wcd2018', 'wcd2019', 'wcd2022', 'wcd2023')
            OR LOWER(name) = 'battle academy 2020'
            OR LOWER(name) LIKE '%mcdonald%collection%2018%french%'
            OR LOWER(name) LIKE '%nintendo%promos%'
            OR LOWER(name) LIKE '%prize pack series%'
            OR LOWER(name) LIKE '%tcg classic%blastoise%'
            OR LOWER(name) LIKE '%tcg classic%charizard%'
            OR LOWER(name) LIKE '%tcg classic%venusaur%'
            OR LOWER(name) LIKE '%swsh%black star promos%'
            OR LOWER(name) IN ('world championship decks 2004', 'world championship decks 2005',
                'world championship decks 2006', 'world championship decks 2007',
                'world championship decks 2008', 'world championship decks 2009',
                'world championship decks 2010', 'world championship decks 2011',
                'world championship decks 2012', 'world championship decks 2013',
                'world championship decks 2014', 'world championship decks 2015',
                'world championship decks 2016', 'world championship decks 2017',
                'world championship decks 2018', 'world championship decks 2019',
                'world championship decks 2022', 'world championship decks 2023')
    )
);

DELETE FROM user_collection
WHERE collection_set_id IN (
    SELECT id FROM collection_set WHERE
        code IN ('ba-20', 'nbsp', 'playprizep', 'clb', 'clc', 'clv', 'swshbs',
            'wcd2004', 'wcd2005', 'wcd2006', 'wcd2007', 'wcd2008', 'wcd2009',
            'wcd2010', 'wcd2011', 'wcd2012', 'wcd2013', 'wcd2014', 'wcd2015',
            'wcd2016', 'wcd2017', 'wcd2018', 'wcd2019', 'wcd2022', 'wcd2023')
        OR LOWER(name) = 'battle academy 2020'
        OR LOWER(name) LIKE '%mcdonald%collection%2018%french%'
        OR LOWER(name) LIKE '%nintendo%promos%'
        OR LOWER(name) LIKE '%prize pack series%'
        OR LOWER(name) LIKE '%tcg classic%blastoise%'
        OR LOWER(name) LIKE '%tcg classic%charizard%'
        OR LOWER(name) LIKE '%tcg classic%venusaur%'
        OR LOWER(name) LIKE '%swsh%black star promos%'
        OR LOWER(name) IN ('world championship decks 2004', 'world championship decks 2005',
            'world championship decks 2006', 'world championship decks 2007',
            'world championship decks 2008', 'world championship decks 2009',
            'world championship decks 2010', 'world championship decks 2011',
            'world championship decks 2012', 'world championship decks 2013',
            'world championship decks 2014', 'world championship decks 2015',
            'world championship decks 2016', 'world championship decks 2017',
            'world championship decks 2018', 'world championship decks 2019',
            'world championship decks 2022', 'world championship decks 2023')
);

DELETE FROM card_image
WHERE expansion_id IN (
    SELECT expansion_id FROM collection_set WHERE
        code IN ('ba-20', 'nbsp', 'playprizep', 'clb', 'clc', 'clv', 'swshbs',
            'wcd2004', 'wcd2005', 'wcd2006', 'wcd2007', 'wcd2008', 'wcd2009',
            'wcd2010', 'wcd2011', 'wcd2012', 'wcd2013', 'wcd2014', 'wcd2015',
            'wcd2016', 'wcd2017', 'wcd2018', 'wcd2019', 'wcd2022', 'wcd2023')
        OR LOWER(name) = 'battle academy 2020'
        OR LOWER(name) LIKE '%mcdonald%collection%2018%french%'
        OR LOWER(name) LIKE '%nintendo%promos%'
        OR LOWER(name) LIKE '%prize pack series%'
        OR LOWER(name) LIKE '%tcg classic%blastoise%'
        OR LOWER(name) LIKE '%tcg classic%charizard%'
        OR LOWER(name) LIKE '%tcg classic%venusaur%'
        OR LOWER(name) LIKE '%swsh%black star promos%'
        OR LOWER(name) IN ('world championship decks 2004', 'world championship decks 2005',
            'world championship decks 2006', 'world championship decks 2007',
            'world championship decks 2008', 'world championship decks 2009',
            'world championship decks 2010', 'world championship decks 2011',
            'world championship decks 2012', 'world championship decks 2013',
            'world championship decks 2014', 'world championship decks 2015',
            'world championship decks 2016', 'world championship decks 2017',
            'world championship decks 2018', 'world championship decks 2019',
            'world championship decks 2022', 'world championship decks 2023')
    UNION
    SELECT expansion_id FROM monitoring WHERE
        expansion_code IN ('ba-20', 'nbsp', 'playprizep', 'clb', 'clc', 'clv', 'swshbs',
            'wcd2004', 'wcd2005', 'wcd2006', 'wcd2007', 'wcd2008', 'wcd2009',
            'wcd2010', 'wcd2011', 'wcd2012', 'wcd2013', 'wcd2014', 'wcd2015',
            'wcd2016', 'wcd2017', 'wcd2018', 'wcd2019', 'wcd2022', 'wcd2023')
        OR LOWER(expansion_name) = 'battle academy 2020'
        OR LOWER(expansion_name) LIKE '%mcdonald%collection%2018%french%'
        OR LOWER(expansion_name) LIKE '%nintendo%promos%'
        OR LOWER(expansion_name) LIKE '%prize pack series%'
        OR LOWER(expansion_name) LIKE '%tcg classic%blastoise%'
        OR LOWER(expansion_name) LIKE '%tcg classic%charizard%'
        OR LOWER(expansion_name) LIKE '%tcg classic%venusaur%'
        OR LOWER(expansion_name) LIKE '%swsh%black star promos%'
        OR LOWER(expansion_name) IN ('world championship decks 2004', 'world championship decks 2005',
            'world championship decks 2006', 'world championship decks 2007',
            'world championship decks 2008', 'world championship decks 2009',
            'world championship decks 2010', 'world championship decks 2011',
            'world championship decks 2012', 'world championship decks 2013',
            'world championship decks 2014', 'world championship decks 2015',
            'world championship decks 2016', 'world championship decks 2017',
            'world championship decks 2018', 'world championship decks 2019',
            'world championship decks 2022', 'world championship decks 2023')
);

DELETE FROM pokemon_tcg_set_mapping
WHERE cardtrader_expansion_id IN (
    SELECT expansion_id FROM collection_set WHERE
        code IN ('ba-20', 'nbsp', 'playprizep', 'clb', 'clc', 'clv', 'swshbs',
            'wcd2004', 'wcd2005', 'wcd2006', 'wcd2007', 'wcd2008', 'wcd2009',
            'wcd2010', 'wcd2011', 'wcd2012', 'wcd2013', 'wcd2014', 'wcd2015',
            'wcd2016', 'wcd2017', 'wcd2018', 'wcd2019', 'wcd2022', 'wcd2023')
        OR LOWER(name) = 'battle academy 2020'
        OR LOWER(name) LIKE '%mcdonald%collection%2018%french%'
        OR LOWER(name) LIKE '%nintendo%promos%'
        OR LOWER(name) LIKE '%prize pack series%'
        OR LOWER(name) LIKE '%tcg classic%blastoise%'
        OR LOWER(name) LIKE '%tcg classic%charizard%'
        OR LOWER(name) LIKE '%tcg classic%venusaur%'
        OR LOWER(name) LIKE '%swsh%black star promos%'
        OR LOWER(name) IN ('world championship decks 2004', 'world championship decks 2005',
            'world championship decks 2006', 'world championship decks 2007',
            'world championship decks 2008', 'world championship decks 2009',
            'world championship decks 2010', 'world championship decks 2011',
            'world championship decks 2012', 'world championship decks 2013',
            'world championship decks 2014', 'world championship decks 2015',
            'world championship decks 2016', 'world championship decks 2017',
            'world championship decks 2018', 'world championship decks 2019',
            'world championship decks 2022', 'world championship decks 2023')
    UNION
    SELECT expansion_id FROM monitoring WHERE expansion_code IN ('ba-20', 'nbsp', 'playprizep', 'clb', 'clc', 'clv', 'swshbs',
        'wcd2004', 'wcd2005', 'wcd2006', 'wcd2007', 'wcd2008', 'wcd2009',
        'wcd2010', 'wcd2011', 'wcd2012', 'wcd2013', 'wcd2014', 'wcd2015',
        'wcd2016', 'wcd2017', 'wcd2018', 'wcd2019', 'wcd2022', 'wcd2023')
);

DELETE FROM tcg_reference_pokemon_set_mapping
WHERE cardtrader_expansion_id IN (
    SELECT expansion_id FROM collection_set WHERE code IN ('ba-20', 'nbsp', 'playprizep', 'clb', 'clc', 'clv', 'swshbs',
        'wcd2004', 'wcd2005', 'wcd2006', 'wcd2007', 'wcd2008', 'wcd2009',
        'wcd2010', 'wcd2011', 'wcd2012', 'wcd2013', 'wcd2014', 'wcd2015',
        'wcd2016', 'wcd2017', 'wcd2018', 'wcd2019', 'wcd2022', 'wcd2023')
    UNION
    SELECT expansion_id FROM monitoring WHERE expansion_code IN ('ba-20', 'nbsp', 'playprizep', 'clb', 'clc', 'clv', 'swshbs',
        'wcd2004', 'wcd2005', 'wcd2006', 'wcd2007', 'wcd2008', 'wcd2009',
        'wcd2010', 'wcd2011', 'wcd2012', 'wcd2013', 'wcd2014', 'wcd2015',
        'wcd2016', 'wcd2017', 'wcd2018', 'wcd2019', 'wcd2022', 'wcd2023')
);

DELETE FROM monitoring
WHERE expansion_code IN ('ba-20', 'nbsp', 'playprizep', 'clb', 'clc', 'clv', 'swshbs',
    'wcd2004', 'wcd2005', 'wcd2006', 'wcd2007', 'wcd2008', 'wcd2009',
    'wcd2010', 'wcd2011', 'wcd2012', 'wcd2013', 'wcd2014', 'wcd2015',
    'wcd2016', 'wcd2017', 'wcd2018', 'wcd2019', 'wcd2022', 'wcd2023')
OR LOWER(expansion_name) = 'battle academy 2020'
OR LOWER(expansion_name) LIKE '%mcdonald%collection%2018%french%'
OR LOWER(expansion_name) LIKE '%nintendo%promos%'
OR LOWER(expansion_name) LIKE '%prize pack series%'
OR LOWER(expansion_name) LIKE '%tcg classic%blastoise%'
OR LOWER(expansion_name) LIKE '%tcg classic%charizard%'
OR LOWER(expansion_name) LIKE '%tcg classic%venusaur%'
OR LOWER(expansion_name) LIKE '%swsh%black star promos%'
OR LOWER(expansion_name) IN ('world championship decks 2004', 'world championship decks 2005',
    'world championship decks 2006', 'world championship decks 2007',
    'world championship decks 2008', 'world championship decks 2009',
    'world championship decks 2010', 'world championship decks 2011',
    'world championship decks 2012', 'world championship decks 2013',
    'world championship decks 2014', 'world championship decks 2015',
    'world championship decks 2016', 'world championship decks 2017',
    'world championship decks 2018', 'world championship decks 2019',
    'world championship decks 2022', 'world championship decks 2023');

DELETE FROM collection_set
WHERE code IN ('ba-20', 'nbsp', 'playprizep', 'clb', 'clc', 'clv', 'swshbs',
    'wcd2004', 'wcd2005', 'wcd2006', 'wcd2007', 'wcd2008', 'wcd2009',
    'wcd2010', 'wcd2011', 'wcd2012', 'wcd2013', 'wcd2014', 'wcd2015',
    'wcd2016', 'wcd2017', 'wcd2018', 'wcd2019', 'wcd2022', 'wcd2023')
OR LOWER(name) = 'battle academy 2020'
OR LOWER(name) LIKE '%mcdonald%collection%2018%french%'
OR LOWER(name) LIKE '%nintendo%promos%'
OR LOWER(name) LIKE '%prize pack series%'
OR LOWER(name) LIKE '%tcg classic%blastoise%'
OR LOWER(name) LIKE '%tcg classic%charizard%'
OR LOWER(name) LIKE '%tcg classic%venusaur%'
OR LOWER(name) LIKE '%swsh%black star promos%'
OR LOWER(name) IN ('world championship decks 2004', 'world championship decks 2005',
    'world championship decks 2006', 'world championship decks 2007',
    'world championship decks 2008', 'world championship decks 2009',
    'world championship decks 2010', 'world championship decks 2011',
    'world championship decks 2012', 'world championship decks 2013',
    'world championship decks 2014', 'world championship decks 2015',
    'world championship decks 2016', 'world championship decks 2017',
    'world championship decks 2018', 'world championship decks 2019',
    'world championship decks 2022', 'world championship decks 2023');
