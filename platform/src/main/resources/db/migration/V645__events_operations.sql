-- Events Chunk 7: event-day operations — sessions / run sheet, staff roster, incidents,
-- emergency contacts, checklists.
-- RENAME to your next Flyway version (after Chunks 1-6), e.g. V217__events_operations.sql
--
-- Event dates elsewhere in this module are stored as LocalDateTime in South African time, so
-- session and shift times are TIMESTAMP (no zone) for the same reason: they mean "wall-clock time
-- at the venue". Audit-style moments (arrived_at, escalated_at ...) are TIMESTAMPTZ.

-- ── Sessions: the programme / run sheet ─────────────────────────────────────
CREATE TABLE IF NOT EXISTS event_sessions (
    id           UUID          PRIMARY KEY,
    tenant_id    UUID          NOT NULL,
    event_id     UUID          NOT NULL REFERENCES events (id) ON DELETE CASCADE,
    title        VARCHAR(200)  NOT NULL,
    description  VARCHAR(1000),
    session_type VARCHAR(20)   NOT NULL DEFAULT 'OTHER',
    room         VARCHAR(100),
    speaker      VARCHAR(200),
    start_at     TIMESTAMP     NOT NULL,
    end_at       TIMESTAMP     NOT NULL,
    status       VARCHAR(12)   NOT NULL DEFAULT 'SCHEDULED',   -- SCHEDULED | IN_PROGRESS | COMPLETED | CANCELLED
    actual_start TIMESTAMP,
    actual_end   TIMESTAMP,
    notes        VARCHAR(1000),
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT chk_event_sessions_times  CHECK (end_at > start_at),
    CONSTRAINT chk_event_sessions_status CHECK (status IN ('SCHEDULED','IN_PROGRESS','COMPLETED','CANCELLED'))
);
CREATE INDEX IF NOT EXISTS idx_event_sessions_event ON event_sessions (event_id, start_at);
-- Room double-booking is prevented in the service under the event row lock. If you want the database
-- to enforce it too, an exclusion constraint over (event_id, lower(room), tsrange(start_at, end_at))
-- does it, but needs the btree_gist extension, which not every managed Postgres lets you install.

-- ── Staff & volunteer roster ────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS event_staff (
    id          UUID          PRIMARY KEY,
    tenant_id   UUID          NOT NULL,
    event_id    UUID          NOT NULL REFERENCES events (id) ON DELETE CASCADE,
    user_id     UUID,                                            -- set when the person is a platform user; enables clash detection across events
    full_name   VARCHAR(200)  NOT NULL,
    kind        VARCHAR(12)   NOT NULL DEFAULT 'STAFF',          -- STAFF | VOLUNTEER | CONTRACTOR
    role        VARCHAR(20)   NOT NULL DEFAULT 'OTHER',
    phone       VARCHAR(50),
    email       VARCHAR(254),
    post        VARCHAR(200),                                    -- where they are stationed
    shift_start TIMESTAMP     NOT NULL,
    shift_end   TIMESTAMP     NOT NULL,
    status      VARCHAR(12)   NOT NULL DEFAULT 'ASSIGNED',       -- ASSIGNED | CONFIRMED | ARRIVED | NO_SHOW | CANCELLED
    arrived_at  TIMESTAMPTZ,
    notes       VARCHAR(500),
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT chk_event_staff_shift  CHECK (shift_end > shift_start),
    CONSTRAINT chk_event_staff_status CHECK (status IN ('ASSIGNED','CONFIRMED','ARRIVED','NO_SHOW','CANCELLED'))
);
CREATE INDEX IF NOT EXISTS idx_event_staff_event ON event_staff (event_id, role);
CREATE INDEX IF NOT EXISTS idx_event_staff_user  ON event_staff (tenant_id, user_id, shift_start) WHERE user_id IS NOT NULL;

-- ── Incidents ───────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS event_incidents (
    id                  UUID          PRIMARY KEY,
    tenant_id           UUID          NOT NULL,
    event_id            UUID          NOT NULL REFERENCES events (id) ON DELETE CASCADE,
    reference           VARCHAR(20)   NOT NULL,                  -- INC-001 ... per event
    title               VARCHAR(200)  NOT NULL,
    description         VARCHAR(2000),
    category            VARCHAR(20)   NOT NULL DEFAULT 'OTHER',
    severity            VARCHAR(10)   NOT NULL DEFAULT 'MEDIUM', -- LOW | MEDIUM | HIGH | CRITICAL
    status              VARCHAR(12)   NOT NULL DEFAULT 'OPEN',   -- OPEN | IN_PROGRESS | ESCALATED | RESOLVED | CLOSED
    location            VARCHAR(200),
    reported_by_name    VARCHAR(200),
    reported_by_user_id UUID,
    assigned_to_name    VARCHAR(200),
    assigned_to_user_id UUID,
    escalation_level    INT           NOT NULL DEFAULT 0,
    escalated_at        TIMESTAMPTZ,
    acknowledged_at     TIMESTAMPTZ,
    resolved_at         TIMESTAMPTZ,
    resolution          VARCHAR(1000),
    version             BIGINT        NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_event_incidents_reference UNIQUE (event_id, reference),
    CONSTRAINT chk_event_incidents_severity CHECK (severity IN ('LOW','MEDIUM','HIGH','CRITICAL')),
    CONSTRAINT chk_event_incidents_status   CHECK (status IN ('OPEN','IN_PROGRESS','ESCALATED','RESOLVED','CLOSED')),
    CONSTRAINT chk_event_incidents_level    CHECK (escalation_level BETWEEN 0 AND 3)
);
CREATE INDEX IF NOT EXISTS idx_event_incidents_event ON event_incidents (event_id, status, created_at DESC);

-- The incident timeline: every note, assignment, escalation and status change, append-only.
CREATE TABLE IF NOT EXISTS event_incident_notes (
    id             UUID          PRIMARY KEY,
    tenant_id      UUID          NOT NULL,
    event_id       UUID          NOT NULL REFERENCES events (id) ON DELETE CASCADE,
    incident_id    UUID          NOT NULL REFERENCES event_incidents (id) ON DELETE CASCADE,
    kind           VARCHAR(12)   NOT NULL DEFAULT 'NOTE',        -- NOTE | ASSIGNMENT | ESCALATION | STATUS
    note           VARCHAR(2000) NOT NULL,
    author_name    VARCHAR(200),
    author_user_id UUID,
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_event_incident_notes ON event_incident_notes (incident_id, created_at);

-- ── Emergency contacts ──────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS event_contacts (
    id              UUID          PRIMARY KEY,
    tenant_id       UUID          NOT NULL,
    event_id        UUID          NOT NULL REFERENCES events (id) ON DELETE CASCADE,
    contact_type    VARCHAR(20)   NOT NULL DEFAULT 'OTHER',
    name            VARCHAR(200)  NOT NULL,
    organisation    VARCHAR(200),
    phone           VARCHAR(50)   NOT NULL,
    alternate_phone VARCHAR(50),
    email           VARCHAR(254),
    notes           VARCHAR(500),
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_event_contacts_event ON event_contacts (event_id, contact_type);

-- ── Checklists (contingency plans are CONTINGENCY-phase items) ──────────────
CREATE TABLE IF NOT EXISTS event_checklist_items (
    id           UUID          PRIMARY KEY,
    tenant_id    UUID          NOT NULL,
    event_id     UUID          NOT NULL REFERENCES events (id) ON DELETE CASCADE,
    phase        VARCHAR(15)   NOT NULL,   -- PRE_EVENT | SETUP | EVENT_DAY | BREAKDOWN | POST_EVENT | CONTINGENCY
    title        VARCHAR(200)  NOT NULL,   -- for CONTINGENCY: the scenario ("What if the power fails?")
    description  VARCHAR(1000),            -- for CONTINGENCY: the response steps
    assigned_to  VARCHAR(200),
    due_at       TIMESTAMP,
    required     BOOLEAN       NOT NULL DEFAULT TRUE,
    done         BOOLEAN       NOT NULL DEFAULT FALSE,   -- for CONTINGENCY: the plan has been reviewed
    done_by_name VARCHAR(200),
    done_at      TIMESTAMPTZ,
    sort_order   INT           NOT NULL DEFAULT 0,
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT chk_event_checklist_phase CHECK (phase IN ('PRE_EVENT','SETUP','EVENT_DAY','BREAKDOWN','POST_EVENT','CONTINGENCY'))
);
CREATE INDEX IF NOT EXISTS idx_event_checklist_event ON event_checklist_items (event_id, phase, sort_order);