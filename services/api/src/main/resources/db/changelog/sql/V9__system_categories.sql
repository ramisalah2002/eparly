-- ============================================================================
-- Migration V9 : Initialisation des catégories système pour le marché français
-- ============================================================================
-- Catégories accessibles à tous les utilisateurs (system_flag = TRUE, user_id IS NULL).
-- Respecte les valeurs existantes de CategoryType : EXPENSE, INCOME, BOTH.
-- Ne modifie aucune migration V1 à V8.
-- ============================================================================

INSERT INTO categories (id, name, system_flag, category_type) VALUES
    ('550e8400-e001-0000-0000-000000000001', 'Logement',            TRUE,  'EXPENSE'),
    ('550e8400-e001-0000-0000-000000000002', 'Courses',            TRUE,  'EXPENSE'),
    ('550e8400-e001-0000-0000-000000000003', 'Restaurants',        TRUE,  'EXPENSE'),
    ('550e8400-e001-0000-0000-000000000004', 'Transport',          TRUE,  'EXPENSE'),
    ('550e8400-e001-0000-0000-000000000005', 'Abonnements',        TRUE,  'EXPENSE'),
    ('550e8400-e001-0000-0000-000000000006', 'Shopping',           TRUE,  'EXPENSE'),
    ('550e8400-e001-0000-0000-000000000007', 'Santé',              TRUE,  'EXPENSE'),
    ('550e8400-e001-0000-0000-000000000008', 'Loisirs',            TRUE,  'EXPENSE'),
    ('550e8400-e001-0000-0000-000000000009', 'Frais bancaires',    TRUE,  'EXPENSE'),
    ('550e8400-e001-0000-0000-00000000000a', 'Revenus',            TRUE,  'INCOME'),
    ('550e8400-e001-0000-0000-00000000000b', 'Virements',          TRUE,  'BOTH'),
    ('550e8400-e001-0000-0000-00000000000c', 'Autres dépenses',    TRUE,  'EXPENSE')
;
