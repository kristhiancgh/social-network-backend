-- =============================================================================
--  SEED 02 - profiledb
--  Run with:  psql -h localhost -U profile_service -d profiledb -f 02-seed-profiledb.sql
-- =============================================================================
--  user_id values MUST match 01-seed-authdb.sql exactly.
--  Covers the four fields required by the spec: first name, last name,
--  birth date and alias.
-- =============================================================================

INSERT INTO profiles (id, user_id, first_name, last_name, birth_date, alias, bio) VALUES
    ('33333333-3333-3333-3333-333333330101',
     '11111111-1111-1111-1111-111111110101',
     'John', 'Doe', DATE '1992-03-14', 'johnny',
     'Backend engineer. Coffee first, deploy later.'),

    ('33333333-3333-3333-3333-333333330102',
     '11111111-1111-1111-1111-111111110102',
     'Maria', 'Garcia', DATE '1988-11-02', 'mary_g',
     'Product designer. I draw boxes and arrows for a living.'),

    ('33333333-3333-3333-3333-333333330103',
     '11111111-1111-1111-1111-111111110103',
     'Li', 'Chen', DATE '1995-07-21', 'li_chen',
     'Data engineer. Pipelines, parquet and patience.'),

    ('33333333-3333-3333-3333-333333330104',
     '11111111-1111-1111-1111-111111110104',
     'Aisha', 'Rossi', DATE '1999-01-09', 'aisha_r',
     'Frontend developer. Angular, accessibility and dark mode.'),

    ('33333333-3333-3333-3333-333333330105',
     '11111111-1111-1111-1111-111111110105',
     'Kristhian', 'Camilo', DATE '1994-05-30', 'kris',
     'Full stack developer. Building this network.')
ON CONFLICT (id) DO NOTHING;

-- Sanity check
SELECT alias, first_name, last_name, birth_date,
       date_part('year', age(birth_date))::INT AS age
  FROM profiles
 ORDER BY alias;
