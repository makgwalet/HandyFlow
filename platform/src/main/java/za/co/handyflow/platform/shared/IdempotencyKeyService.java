package za.co.handyflow.platform.shared;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

/**
 * Core idempotency logic: given a tenant, a request path, and a
 * client-supplied Idempotency-Key, either (a) claim the key atomically if
 * this is the first time it's been seen, (b) return the previously-cached
 * response if the original request already completed, or (c) signal that
 * another request with the same key is currently in flight.
 * <p>
 * ATOMICITY: uses INSERT ... ON CONFLICT DO NOTHING to atomically claim a
 * key — same "let Postgres serialize concurrent callers" pattern already
 * established for sequence generation (TenantSequenceService,
 * ScSupplierInvoice numbering) rather than a SELECT-then-INSERT race.
 * Whoever's INSERT actually creates the row owns the request; every other
 * concurrent caller with the same key sees 0 rows affected and knows to
 * back off.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotencyKeyService {

    private final JdbcTemplate jdbc;

    public sealed interface ClaimResult permits Claimed, AlreadyCompleted, InProgress {}

    /** This caller now owns executing the request; call complete() when done. */
    public record Claimed(UUID recordId) implements ClaimResult {}

    /** A previous request with this key already finished — replay its response, don't re-execute. */
    public record AlreadyCompleted(int responseStatus, String responseBody, String contentType) implements ClaimResult {}

    /** Another request with this key is currently executing — reject, don't queue or re-execute. */
    public record InProgress() implements ClaimResult {}

    public ClaimResult claim(TenantId tenantId, String requestPath, String idempotencyKey) {
        UUID recordId = UUID.randomUUID();

        int inserted = jdbc.update(
                """
                INSERT INTO idempotency_keys (id, tenant_id, request_path, idempotency_key, status, created_at)
                VALUES (?, ?, ?, ?, 'IN_PROGRESS', ?)
                ON CONFLICT (tenant_id, request_path, idempotency_key) DO NOTHING
                """,
                recordId, tenantId.getValue(), requestPath, idempotencyKey, Timestamp.from(Instant.now()));

        if (inserted == 1) {
            return new Claimed(recordId);
        }

        // Someone else already has (or had) this key — find out which.
        return jdbc.query(
                """
                SELECT status, response_status, response_body, response_content_type
                FROM idempotency_keys
                WHERE tenant_id = ? AND request_path = ? AND idempotency_key = ?
                """,
                rs -> {
                    if (!rs.next()) {
                        // Vanishingly unlikely (row existed a moment ago, gone now) —
                        // treat as safe-to-retry rather than throwing.
                        return new InProgress();
                    }
                    if ("COMPLETED".equals(rs.getString("status"))) {
                        return new AlreadyCompleted(
                                rs.getInt("response_status"),
                                rs.getString("response_body"),
                                rs.getString("response_content_type"));
                    }
                    return new InProgress();
                },
                tenantId.getValue(), requestPath, idempotencyKey);
    }

    public void complete(UUID recordId, int responseStatus, String responseBody, String contentType) {
        jdbc.update(
                """
                UPDATE idempotency_keys
                SET status = 'COMPLETED', response_status = ?, response_body = ?,
                    response_content_type = ?, completed_at = ?
                WHERE id = ?
                """,
                responseStatus, responseBody, contentType, Timestamp.from(Instant.now()), recordId);
    }

    /**
     * Called if the wrapped request throws or the response indicates a
     * server error — deletes the claim rather than marking it COMPLETED,
     * so a genuinely failed request (not just "already handled") can be
     * retried with the same key instead of getting a cached failure
     * replayed forever. Client errors (4xx) are NOT released here — a
     * 400 for bad input is still a "completed" response worth caching,
     * since retrying with the same bad input would just fail the same way.
     */
    public void releaseOnServerError(UUID recordId) {
        jdbc.update("DELETE FROM idempotency_keys WHERE id = ?", recordId);
    }
}