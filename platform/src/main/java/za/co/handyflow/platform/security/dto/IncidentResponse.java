package za.co.handyflow.platform.security.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * IncidentResponse — CHANGE: added `type`.
 *
 * BUGFIX (completes bug #16): the original fix for bug #16 made
 * IncidentService.createIncident() actually PERSIST the incident type
 * (raw JDBC UPDATE after insert) -- but nothing ever read it back out.
 * Neither this record nor either SELECT query in IncidentService included
 * the `type` column at all. The frontend (IncidentsTab.tsx) was fully
 * built around displaying it -- a type selector on the create form, a
 * type badge on every incident card -- but `inc.type` was always
 * `undefined` client-side, so the badge silently never rendered. No error
 * anywhere; the data was correctly in the database the whole time, just
 * never fetched back.
 *
 * Inserted here (not appended) since IncidentService.mapRow() is the only
 * construction site for this record -- no risk of an unknown positional
 * caller elsewhere breaking.
 */
public record IncidentResponse(
        UUID       id,
        UUID       siteId,
        String     siteName,
        UUID       shiftId,
        UUID       guardId,
        String     guardName,
        String     title,
        String     description,
        String     severity,
        String     status,
        String     type,
        BigDecimal latitude,
        BigDecimal longitude,
        Instant    acknowledgedAt,
        Instant    resolvedAt,
        Instant    reportedAt,
        Instant    updatedAt
) {}