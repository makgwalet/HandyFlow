package za.co.handyflow.platform.clinic.application.internal;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.clinic.application.internal.PatientReceivableRules.Open;
import za.co.handyflow.platform.clinic.application.internal.PatientReceivableRules.Part;
import za.co.handyflow.platform.clinic.application.internal.PatientReceivableRules.Split;
import za.co.handyflow.platform.clinic.dto.PaymentLinkDtos.LinkResult;
import za.co.handyflow.platform.shared.ResourceNotFoundException;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

/**
 * The patient's side of what is owed (patch 0190, CLINIC-DEC-005). The scheme's money is tracked per claim by the claim ledger; this
 * links each patient payment to the visits it pays for (oldest visit first, or one the person chose), so every visit shows what the
 * patient has paid on it. Money that matches no visit stays on account. Links are never changed afterwards.
 */
@Service
public class ClinicPatientReceivableService {

    private final JdbcTemplate jdbc;

    public ClinicPatientReceivableService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    /** Visits (claims) of the patient that still owe something from the patient. Rejected and voided claims owe nothing. */
    @Transactional(readOnly = true)
    public List<Open> openClaims(UUID tenantId, UUID patientId) {
        return jdbc.query("""
                SELECT c.id, c.created_at,
                       c.patient_portion
                         - COALESCE((SELECT SUM(a.amount) FROM clinic_payment_allocations a WHERE a.tenant_id = c.tenant_id AND a.claim_id = c.id), 0)
                         - COALESCE((SELECT SUM(p.amount) FROM clinic_payments p WHERE p.tenant_id = c.tenant_id AND p.claim_id = c.id), 0) AS owed
                FROM clinic_claims c
                WHERE c.tenant_id = ? AND c.patient_id = ? AND c.status NOT IN ('REJECTED','VOIDED')""",
                (rs, i) -> new Open((UUID) rs.getObject(1), rs.getTimestamp(2) == null ? null : rs.getTimestamp(2).toInstant(), rs.getBigDecimal(3).max(BigDecimal.ZERO)),
                tenantId, patientId);
    }

    /** Refuses a visit that is not the patient's or owes nothing, before a payment is taken against it. */
    @Transactional(readOnly = true)
    public void requireOpenVisit(UUID tenantId, UUID patientId, UUID claimId) {
        boolean ok = openClaims(tenantId, patientId).stream().anyMatch(o -> o.claimId().equals(claimId) && o.owed().signum() > 0);
        if (!ok) throw new IllegalArgumentException("That visit does not owe anything from this patient.");
    }

    /** Links a payment just recorded: to the chosen visit when one is given, otherwise oldest visit first. */
    @Transactional
    public LinkResult linkNew(UUID tenantId, UUID paymentId, UUID by, UUID chosenClaim) {
        Object[] pay = payment(tenantId, paymentId);
        UUID patientId = (UUID) pay[0];
        lockPatient(tenantId, patientId);
        BigDecimal unlinked = PatientReceivableRules.unlinked((BigDecimal) pay[1], linkedTo(tenantId, paymentId));
        List<Open> open = openClaims(tenantId, patientId);
        Split split = chosenClaim == null ? PatientReceivableRules.oldestFirst(unlinked, open) : PatientReceivableRules.toVisit(chosenClaim, unlinked, open);
        return save(tenantId, paymentId, patientId, by, split, open);
    }

    /** Links the payments that were recorded before this existed (or came in with no visit to match), oldest payment first. */
    @Transactional
    public LinkResult linkOnAccount(UUID tenantId, UUID patientId, UUID by) {
        lockPatient(tenantId, patientId);
        List<Object[]> payments = jdbc.query("SELECT id, amount FROM clinic_payments WHERE tenant_id = ? AND patient_id = ? AND claim_id IS NULL ORDER BY recorded_at, id",
                (rs, i) -> new Object[]{rs.getObject(1), rs.getBigDecimal(2)}, tenantId, patientId);
        BigDecimal linked = BigDecimal.ZERO, onAccount = BigDecimal.ZERO;
        int paidOff = 0;
        List<Open> open = openClaims(tenantId, patientId);
        for (Object[] p : payments) {
            UUID id = (UUID) p[0];
            BigDecimal unlinked = PatientReceivableRules.unlinked((BigDecimal) p[1], linkedTo(tenantId, id));
            if (unlinked.signum() == 0) continue;
            Split split = PatientReceivableRules.oldestFirst(unlinked, open);
            LinkResult r = save(tenantId, id, patientId, by, split, open);
            linked = linked.add(r.linked()); onAccount = onAccount.add(r.onAccount()); paidOff += r.visitsPaidOff();
            open = openClaims(tenantId, patientId);
        }
        return new LinkResult(linked, onAccount, paidOff);
    }

    /** Linked amounts per claim for the account screen. */
    @Transactional(readOnly = true)
    public Map<UUID, BigDecimal> linkedByClaim(UUID tenantId, UUID patientId) {
        Map<UUID, BigDecimal> out = new HashMap<>();
        jdbc.query("SELECT claim_id, SUM(amount) FROM clinic_payment_allocations WHERE tenant_id = ? AND patient_id = ? GROUP BY claim_id",
                rs -> { out.put((UUID) rs.getObject(1), rs.getBigDecimal(2)); }, tenantId, patientId);
        return out;
    }

    private LinkResult save(UUID tenantId, UUID paymentId, UUID patientId, UUID by, Split split, List<Open> open) {
        int paidOff = 0;
        Map<UUID, BigDecimal> owed = new HashMap<>();
        open.forEach(o -> owed.put(o.claimId(), o.owed()));
        for (Part part : split.parts()) {
            jdbc.update("INSERT INTO clinic_payment_allocations (id, tenant_id, payment_id, claim_id, patient_id, amount, allocated_at, allocated_by) VALUES (?,?,?,?,?,?,?,?)",
                    UUID.randomUUID(), tenantId, paymentId, part.claimId(), patientId, part.amount(), Timestamp.from(Instant.now()), by);
            if (owed.getOrDefault(part.claimId(), BigDecimal.ZERO).compareTo(part.amount()) <= 0) paidOff++;
        }
        return new LinkResult(split.allocated(), split.onAccount(), paidOff);
    }

    private Object[] payment(UUID tenantId, UUID paymentId) {
        List<Object[]> rows = jdbc.query("SELECT patient_id, amount FROM clinic_payments WHERE tenant_id = ? AND id = ? AND patient_id IS NOT NULL",
                (rs, i) -> new Object[]{rs.getObject(1), rs.getBigDecimal(2)}, tenantId, paymentId);
        if (rows.isEmpty()) throw new ResourceNotFoundException("Payment", paymentId.toString());
        return rows.get(0);
    }

    private BigDecimal linkedTo(UUID tenantId, UUID paymentId) {
        BigDecimal b = jdbc.queryForObject("SELECT COALESCE(SUM(amount), 0) FROM clinic_payment_allocations WHERE tenant_id = ? AND payment_id = ?", BigDecimal.class, tenantId, paymentId);
        return b == null ? BigDecimal.ZERO : b;
    }

    /** Two people linking the same patient's payments at once would otherwise both see the same money as free. */
    private void lockPatient(UUID tenantId, UUID patientId) {
        List<UUID> rows = jdbc.query("SELECT id FROM clinic_patients WHERE tenant_id = ? AND id = ? FOR UPDATE", (rs, i) -> (UUID) rs.getObject(1), tenantId, patientId);
        if (rows.isEmpty()) throw new ResourceNotFoundException("Patient", patientId.toString());
    }
}
