-- Recruiter: an optional, voluntary equal-opportunity declaration made with an application. It is kept apart from the applicant's details, is never
-- shown on an application or used to rank or filter anyone, and is only ever counted, in groups of five or more, in the diversity report.
CREATE TABLE rec_diversity (
    application_id UUID        NOT NULL REFERENCES rec_applications (id) ON DELETE CASCADE,
    tenant_id      UUID        NOT NULL,
    gender         VARCHAR(20) CHECK (gender IN ('FEMALE', 'MALE', 'OTHER', 'PREFER_NOT_TO_SAY')),
    race           VARCHAR(20) CHECK (race IN ('AFRICAN', 'COLOURED', 'INDIAN', 'WHITE', 'OTHER', 'PREFER_NOT_TO_SAY')),
    disability     VARCHAR(20) CHECK (disability IN ('YES', 'NO', 'PREFER_NOT_TO_SAY')),
    declared_at    TIMESTAMP   NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_rec_diversity PRIMARY KEY (application_id)
);
CREATE INDEX idx_rec_diversity_tenant ON rec_diversity (tenant_id);
