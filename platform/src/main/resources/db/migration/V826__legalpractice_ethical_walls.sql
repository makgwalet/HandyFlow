-- V826: Legal Practice — ethical walls. Confirm the next free Flyway version (V825 is LP chunk 5).
--
-- A wall bars one member of staff from one matter, or from every matter of one client. The services refuse the barred person
-- (they cannot read, change or add to the matter) and keep a record of each refusal. Walls are never deleted: lifting one
-- records who lifted it and why, and the person a wall bars cannot be the one who lifts it.

CREATE TABLE IF NOT EXISTS lp_ethical_walls (
    id              UUID PRIMARY KEY,
    tenant_id       UUID         NOT NULL,
    scope           VARCHAR(8)   NOT NULL,
    matter_id       UUID,
    client_id       UUID,
    user_id         UUID         NOT NULL,
    user_name       VARCHAR(255),
    reason          TEXT         NOT NULL,
    created_by      UUID         NOT NULL,
    created_by_name VARCHAR(255),
    created_at      TIMESTAMPTZ  NOT NULL,
    lifted_by       UUID,
    lifted_by_name  VARCHAR(255),
    lifted_at       TIMESTAMPTZ,
    lift_reason     TEXT,
    CONSTRAINT ck_lp_wall_scope  CHECK ((scope = 'MATTER' AND matter_id IS NOT NULL AND client_id IS NULL)
                                     OR (scope = 'CLIENT' AND client_id IS NOT NULL AND matter_id IS NULL)),
    CONSTRAINT ck_lp_wall_reason CHECK (length(btrim(reason)) >= 10),
    CONSTRAINT ck_lp_wall_lift   CHECK ((lifted_at IS NULL AND lifted_by IS NULL AND lift_reason IS NULL)
                                     OR (lifted_at IS NOT NULL AND lifted_by IS NOT NULL AND length(btrim(coalesce(lift_reason,''))) >= 10)),
    -- The person a wall bars cannot be the one who lifts it.
    CONSTRAINT ck_lp_wall_lifter CHECK (lifted_by IS NULL OR lifted_by <> user_id)
);

CREATE INDEX IF NOT EXISTS idx_lp_wall_user ON lp_ethical_walls (tenant_id, user_id) WHERE lifted_at IS NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_lp_wall_matter ON lp_ethical_walls (tenant_id, user_id, matter_id) WHERE lifted_at IS NULL AND matter_id IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_lp_wall_client ON lp_ethical_walls (tenant_id, user_id, client_id) WHERE lifted_at IS NULL AND client_id IS NOT NULL;

-- Each time the services turned a barred person away.
CREATE TABLE IF NOT EXISTS lp_wall_denials (
    id         UUID PRIMARY KEY,
    tenant_id  UUID         NOT NULL,
    user_id    UUID         NOT NULL,
    user_name  VARCHAR(255),
    matter_id  UUID         NOT NULL,
    client_id  UUID,
    wall_id    UUID         NOT NULL REFERENCES lp_ethical_walls(id),
    denied_at  TIMESTAMPTZ  NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_lp_wd_tenant ON lp_wall_denials (tenant_id, denied_at DESC);

CREATE OR REPLACE FUNCTION lp_wall_guard() RETURNS trigger AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'ethical walls are never deleted' USING ERRCODE = 'integrity_constraint_violation';
    END IF;
    -- Only an active wall can change, and only to be lifted; nothing else about it can be rewritten.
    IF OLD.lifted_at IS NOT NULL THEN
        RAISE EXCEPTION 'a lifted wall cannot be changed' USING ERRCODE = 'integrity_constraint_violation';
    END IF;
    IF NEW.id IS DISTINCT FROM OLD.id OR NEW.tenant_id IS DISTINCT FROM OLD.tenant_id OR NEW.scope IS DISTINCT FROM OLD.scope
       OR NEW.matter_id IS DISTINCT FROM OLD.matter_id OR NEW.client_id IS DISTINCT FROM OLD.client_id OR NEW.user_id IS DISTINCT FROM OLD.user_id
       OR NEW.reason IS DISTINCT FROM OLD.reason OR NEW.created_by IS DISTINCT FROM OLD.created_by OR NEW.created_at IS DISTINCT FROM OLD.created_at THEN
        RAISE EXCEPTION 'only the lifting of a wall can be recorded; the wall itself cannot be rewritten' USING ERRCODE = 'integrity_constraint_violation';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;
DROP TRIGGER IF EXISTS trg_lp_wall_guard ON lp_ethical_walls;
CREATE TRIGGER trg_lp_wall_guard BEFORE UPDATE OR DELETE ON lp_ethical_walls FOR EACH ROW EXECUTE FUNCTION lp_wall_guard();

CREATE OR REPLACE FUNCTION lp_wall_denial_append_only() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'lp_wall_denials is append-only: % is not allowed', TG_OP USING ERRCODE = 'integrity_constraint_violation';
END;
$$ LANGUAGE plpgsql;
DROP TRIGGER IF EXISTS trg_lp_wd_append_only ON lp_wall_denials;
CREATE TRIGGER trg_lp_wd_append_only BEFORE UPDATE OR DELETE ON lp_wall_denials FOR EACH ROW EXECUTE FUNCTION lp_wall_denial_append_only();

-- Permissions -------------------------------------------------------------------------------------------------------
INSERT INTO permissions (id, name, description) VALUES
    (gen_random_uuid(), 'LEGALPRACTICE_WALL_MANAGE', 'Put up and lift ethical walls that bar staff from a matter or client'),
    (gen_random_uuid(), 'LEGALPRACTICE_WALL_AUDIT',  'See the record of staff turned away by ethical walls')
ON CONFLICT (name) DO NOTHING;

UPDATE permissions SET module_key = 'legalpractice', resource = 'wall',
       verb = CASE name WHEN 'LEGALPRACTICE_WALL_MANAGE' THEN 'MANAGE' ELSE 'AUDIT' END,
       sensitivity = 'SENSITIVE', requires_second_person = FALSE, grantable_by = 'ADMIN'
 WHERE name IN ('LEGALPRACTICE_WALL_MANAGE', 'LEGALPRACTICE_WALL_AUDIT');

INSERT INTO role_permissions (role_id, permission_id)
SELECT rp.role_id, np.id
  FROM role_permissions rp
  JOIN permissions src ON src.id = rp.permission_id AND src.name = 'LEGALPRACTICE_ADMIN'
  JOIN permissions np ON np.name IN ('LEGALPRACTICE_WALL_MANAGE', 'LEGALPRACTICE_WALL_AUDIT')
 WHERE NOT EXISTS (SELECT 1 FROM role_permissions x WHERE x.role_id = rp.role_id AND x.permission_id = np.id);
