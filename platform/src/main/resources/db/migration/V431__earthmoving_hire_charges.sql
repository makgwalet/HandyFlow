-- E7: hire charges. A charge is one billing period of one deployment (days on hire, or hours worked) at the machine's
-- rate on the day it was raised. Charges are turned into a DRAFT invoice in Invoicing; nothing here issues an invoice.
-- A period of a deployment can be charged once: live (not voided) charges of one deployment may not overlap.
DO $$ BEGIN
    CREATE EXTENSION IF NOT EXISTS btree_gist;
EXCEPTION WHEN OTHERS THEN RAISE NOTICE 'btree_gist could not be installed (%): overlapping hire periods are only refused by the application', SQLERRM;
END $$;

CREATE TABLE IF NOT EXISTS earthmoving_hire_charges (
    id              UUID PRIMARY KEY,
    tenant_id       UUID NOT NULL,
    deployment_id   UUID NOT NULL REFERENCES earthmoving_deployments(id),
    asset_id        UUID NOT NULL REFERENCES earthmoving_assets(id),
    customer_id     UUID NOT NULL,
    period_from     DATE NOT NULL,
    period_to       DATE NOT NULL,
    basis           VARCHAR(8)  NOT NULL CHECK (basis IN ('DAILY', 'HOURLY')),
    quantity        NUMERIC(10,2) NOT NULL CHECK (quantity > 0),
    unit_rate       NUMERIC(12,2) NOT NULL CHECK (unit_rate > 0),
    vat_rate        NUMERIC(5,2)  NOT NULL CHECK (vat_rate >= 0 AND vat_rate <= 100),
    amount          NUMERIC(15,2) NOT NULL CHECK (amount > 0),
    description     TEXT NOT NULL,
    status          VARCHAR(10) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'INVOICED', 'VOIDED')),
    invoice_id      UUID,
    invoice_number  VARCHAR(64),
    note            TEXT,
    created_by      UUID,
    created_at      TIMESTAMP NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP NOT NULL DEFAULT now(),
    version         BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_em_hire_period CHECK (period_to >= period_from),
    CONSTRAINT chk_em_hire_invoiced CHECK ((status = 'INVOICED') = (invoice_id IS NOT NULL)),
    CONSTRAINT chk_em_hire_voided_note CHECK (status <> 'VOIDED' OR length(btrim(coalesce(note, ''))) >= 5)
);
CREATE INDEX IF NOT EXISTS idx_em_hire_charges_tenant ON earthmoving_hire_charges (tenant_id, status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_em_hire_charges_deployment ON earthmoving_hire_charges (tenant_id, deployment_id);
DO $$ BEGIN
    ALTER TABLE earthmoving_hire_charges ADD CONSTRAINT ex_em_hire_no_overlap
        EXCLUDE USING gist (deployment_id WITH =, daterange(period_from, period_to, '[]') WITH &&) WHERE (status <> 'VOIDED');
EXCEPTION WHEN duplicate_object OR duplicate_table THEN NULL;
          WHEN undefined_object THEN RAISE NOTICE 'btree_gist is not installed: overlapping hire periods are only refused by the application';
          WHEN feature_not_supported THEN RAISE NOTICE 'exclusion constraint not supported: overlapping hire periods are only refused by the application';
END $$;
