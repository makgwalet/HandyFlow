-- WHY this table?
-- Spring Modulith stores every published domain event here BEFORE
-- delivering it to listeners. If the app crashes mid-delivery,
-- on restart it retries any events marked as incomplete.
-- This gives us guaranteed at-least-once event delivery — critical
-- for things like "create trial subscription when tenant registers".

CREATE TABLE event_publication (
    id               UUID        NOT NULL,
    listener_id      TEXT        NOT NULL,
    event_type       TEXT        NOT NULL,
    serialized_event TEXT        NOT NULL,
    publication_date TIMESTAMP WITH TIME ZONE NOT NULL,
    completion_date  TIMESTAMP WITH TIME ZONE,

    CONSTRAINT pk_event_publication PRIMARY KEY (id)
);

CREATE INDEX idx_event_publication_completion
    ON event_publication (completion_date);