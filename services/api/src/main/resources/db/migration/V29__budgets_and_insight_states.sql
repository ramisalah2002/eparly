-- ============================================================================
-- Migration V29 : budgets mensuels par categorie et suivi utilisateur des recommandations.
-- Additive. Les recommandations restent calculees a la demande : seul le choix de l'utilisateur est stocke.
-- ============================================================================

CREATE TABLE category_budgets (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    category_id UUID NOT NULL REFERENCES categories(id) ON DELETE CASCADE,
    monthly_limit_minor BIGINT NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'EUR',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_category_budgets_user_category UNIQUE (user_id, category_id),
    CONSTRAINT chk_category_budgets_limit CHECK (monthly_limit_minor > 0),
    CONSTRAINT chk_category_budgets_currency CHECK (currency = 'EUR')
);

CREATE TABLE insight_recommendation_states (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    recommendation_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL,
    hidden_until TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, recommendation_id),
    CONSTRAINT chk_insight_state_status CHECK (status IN ('NEW', 'VIEWED', 'IGNORED', 'DONE'))
);
