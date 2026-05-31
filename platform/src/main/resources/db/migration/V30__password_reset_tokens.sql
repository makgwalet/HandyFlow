-- V30__password_reset_tokens.sql
-- Stores short-lived tokens for the forgot-password flow.
-- WHY a separate table instead of storing on users?
-- A user may request multiple resets. A separate table lets us
-- invalidate old tokens when a new one is issued, and clean up
-- expired ones without touching the users table.

CREATE TABLE password_reset_tokens (
    id          UUID        NOT NULL DEFAULT gen_random_uuid(),
    user_id     UUID        NOT NULL,
    tenant_id   UUID        NOT NULL,
    token       VARCHAR(255) NOT NULL,
    expires_at  TIMESTAMP   NOT NULL,
    used        BOOLEAN     NOT NULL DEFAULT false,
    created_at  TIMESTAMP   NOT NULL DEFAULT now(),

    CONSTRAINT pk_prt          PRIMARY KEY (id),
    CONSTRAINT fk_prt_user     FOREIGN KEY (user_id)   REFERENCES users(id)   ON DELETE CASCADE,
    CONSTRAINT fk_prt_tenant   FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE,
    CONSTRAINT uq_prt_token    UNIQUE (token)
);

CREATE INDEX idx_prt_token     ON password_reset_tokens(token);
CREATE INDEX idx_prt_user_id   ON password_reset_tokens(user_id);
