package za.co.handyflow.platform.clinic.application.internal;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import za.co.handyflow.platform.clinic.application.internal.ClaimLedgerRules.Totals;
import za.co.handyflow.platform.clinic.dto.billing.ClaimMoneyDtos.LedgerEntry;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** SQL for the append-only claim money ledger (V365). Rules live in {@link ClaimLedgerRules}; orchestration in ClinicClaimMoneyService. */
@Service
public class ClinicClaimLedgerService {

    private final JdbcTemplate jdbc;

    public ClinicClaimLedgerService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    private static final String TOTALS_SELECT =
            "SELECT c.id, COALESCE(p.s,0), COALESCE(w.s,0), COALESCE(k.s,0) FROM clinic_claims c "
          + "LEFT JOIN (SELECT claim_id, SUM(amount) s FROM clinic_claim_scheme_payments WHERE tenant_id = ? GROUP BY claim_id) p ON p.claim_id = c.id "
          + "LEFT JOIN (SELECT claim_id, SUM(amount) s FROM clinic_claim_adjustments WHERE tenant_id = ? AND kind = 'WRITE_OFF' GROUP BY claim_id) w ON w.claim_id = c.id "
          + "LEFT JOIN (SELECT claim_id, SUM(amount) s FROM clinic_claim_adjustments WHERE tenant_id = ? AND kind = 'CREDIT_NOTE' GROUP BY claim_id) k ON k.claim_id = c.id "
          + "WHERE c.tenant_id = ? ";

    private static Totals totals(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new Totals(rs.getBigDecimal(2), rs.getBigDecimal(3), rs.getBigDecimal(4));
    }

    /** Totals for every claim of the tenant that has any ledger row (claims with none are absent: treat as {@link Totals#NONE}). */
    public Map<UUID, Totals> totalsByClaim(UUID tenantId) {
        Map<UUID, Totals> out = new HashMap<>();
        jdbc.query(TOTALS_SELECT + "AND (p.s IS NOT NULL OR w.s IS NOT NULL OR k.s IS NOT NULL)",
                rs -> { out.put((UUID) rs.getObject(1), totals(rs)); }, tenantId, tenantId, tenantId, tenantId);
        return out;
    }

    public Totals totals(UUID tenantId, UUID claimId) {
        List<Totals> r = jdbc.query(TOTALS_SELECT + "AND c.id = ?", (rs, i) -> totals(rs), tenantId, tenantId, tenantId, tenantId, claimId);
        return r.isEmpty() ? Totals.NONE : r.get(0);
    }

    /** Row-lock the claims so two people cannot both spend the same outstanding balance. Locks in id order to avoid deadlock. */
    public void lock(UUID tenantId, List<UUID> claimIds) {
        List<UUID> ordered = new ArrayList<>(claimIds);
        ordered.sort(Comparator.comparing(UUID::toString));
        for (UUID id : ordered) {
            jdbc.query("SELECT 1 FROM clinic_claims WHERE tenant_id = ? AND id = ? FOR UPDATE", rs -> { }, tenantId, id);
        }
    }

    public void addPayment(UUID tenantId, UUID claimId, BigDecimal amount, LocalDate receivedOn, String reference,
                           UUID batchId, String allocation, String overrideReason, UUID userId) {
        jdbc.update("INSERT INTO clinic_claim_scheme_payments (id, tenant_id, claim_id, amount, received_on, reference, batch_id, allocation, override_reason, recorded_by, recorded_at) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                UUID.randomUUID(), tenantId, claimId, amount, java.sql.Date.valueOf(receivedOn), trunc(reference, 100), batchId, allocation,
                overrideReason, userId, Timestamp.from(Instant.now()));
    }

    /** @return the credit note number for a CREDIT_NOTE, otherwise null */
    public String addAdjustment(UUID tenantId, UUID claimId, String kind, BigDecimal amount, String reason, UUID userId, LocalDate on) {
        String number = "CREDIT_NOTE".equals(kind)
                ? jdbc.queryForObject("SELECT 'CN' || lpad(nextval('clinic_credit_note_seq')::text, 6, '0')", String.class) : null;
        jdbc.update("INSERT INTO clinic_claim_adjustments (id, tenant_id, claim_id, kind, amount, reason, credit_note_no, authorised_by, adjusted_on, recorded_at) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?)",
                UUID.randomUUID(), tenantId, claimId, kind, amount, reason, number, userId, java.sql.Date.valueOf(on), Timestamp.from(Instant.now()));
        return number;
    }

    public List<LedgerEntry> entries(UUID tenantId, UUID claimId) {
        List<LedgerEntry> out = new ArrayList<>(jdbc.query(
                "SELECT amount, received_on, reference, allocation, override_reason, recorded_by, recorded_at FROM clinic_claim_scheme_payments WHERE tenant_id = ? AND claim_id = ?",
                (rs, i) -> new LedgerEntry("SCHEME_PAYMENT", rs.getBigDecimal(1), rs.getDate(2).toLocalDate(), rs.getString(3),
                        rs.getString(5) != null ? rs.getString(4) + ": " + rs.getString(5) : rs.getString(4),
                        (UUID) rs.getObject(6), rs.getTimestamp(7).toInstant()), tenantId, claimId));
        out.addAll(jdbc.query(
                "SELECT kind, amount, adjusted_on, credit_note_no, reason, authorised_by, recorded_at FROM clinic_claim_adjustments WHERE tenant_id = ? AND claim_id = ?",
                (rs, i) -> new LedgerEntry(rs.getString(1), rs.getBigDecimal(2), rs.getDate(3).toLocalDate(), rs.getString(4), rs.getString(5),
                        (UUID) rs.getObject(6), rs.getTimestamp(7).toInstant()), tenantId, claimId));
        out.sort(Comparator.comparing(LedgerEntry::recordedAt).reversed());
        return out;
    }

    /** Scheme payments with their time, for the revenue chart. */
    public record PaymentAt(Instant at, BigDecimal amount) {}

    public List<PaymentAt> paymentsSince(UUID tenantId, Instant since) {
        return jdbc.query("SELECT recorded_at, amount FROM clinic_claim_scheme_payments WHERE tenant_id = ? AND recorded_at >= ?",
                (rs, i) -> new PaymentAt(rs.getTimestamp(1).toInstant(), rs.getBigDecimal(2)), tenantId, Timestamp.from(since));
    }

    /** A claim open to a scheme payment: accepted or part-paid, with a scheme balance, for one scheme. */
    public record OpenClaim(UUID id, String status, String patientName, String reference, Instant billedAt, BigDecimal schemePortion, Totals totals) {}

    public List<OpenClaim> openForScheme(UUID tenantId, String schemeName) {
        String sql = "SELECT c.id, c.status, pt.full_name, c.reference_number, COALESCE(c.submitted_at, c.created_at), c.scheme_portion, "
                + "COALESCE(p.s,0), COALESCE(w.s,0), COALESCE(k.s,0) FROM clinic_claims c "
                + "LEFT JOIN clinic_patients pt ON pt.id = c.patient_id "
                + "LEFT JOIN (SELECT claim_id, SUM(amount) s FROM clinic_claim_scheme_payments WHERE tenant_id = ? GROUP BY claim_id) p ON p.claim_id = c.id "
                + "LEFT JOIN (SELECT claim_id, SUM(amount) s FROM clinic_claim_adjustments WHERE tenant_id = ? AND kind = 'WRITE_OFF' GROUP BY claim_id) w ON w.claim_id = c.id "
                + "LEFT JOIN (SELECT claim_id, SUM(amount) s FROM clinic_claim_adjustments WHERE tenant_id = ? AND kind = 'CREDIT_NOTE' GROUP BY claim_id) k ON k.claim_id = c.id "
                + "WHERE c.tenant_id = ? AND c.status IN ('ACCEPTED','PARTIAL') AND c.scheme_portion > 0 AND lower(c.scheme_name) = lower(?)";
        return jdbc.query(sql, (rs, i) -> new OpenClaim((UUID) rs.getObject(1), rs.getString(2), rs.getString(3), rs.getString(4),
                        rs.getTimestamp(5).toInstant(), rs.getBigDecimal(6), new Totals(rs.getBigDecimal(7), rs.getBigDecimal(8), rs.getBigDecimal(9))),
                tenantId, tenantId, tenantId, tenantId, schemeName == null ? "" : schemeName.trim());
    }

    private static String trunc(String s, int max) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t.length() <= max ? t : t.substring(0, max);
    }
}
