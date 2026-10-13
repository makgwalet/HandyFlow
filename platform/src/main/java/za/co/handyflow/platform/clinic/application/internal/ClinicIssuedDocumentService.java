package za.co.handyflow.platform.clinic.application.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.clinic.dto.DocumentVerificationDtos.VerifyResponse;
import za.co.handyflow.platform.shared.ResourceNotFoundException;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * The permanent record of every document the clinic issues (patch 0193): a number that is never reused, the data it was made from,
 * a hash of the PDF and a public verification code (CLINIC-DEC-024, 025). The number and code are printed on the document, so they are
 * allocated first and the document is rendered inside the same transaction: if rendering fails, no number is used up.
 */
@Service
public class ClinicIssuedDocumentService {

    private static final DateTimeFormatter LONG_DATE = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH);

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final String frontendUrl;
    private final za.co.handyflow.platform.shared.FileStorageService storage;

    public ClinicIssuedDocumentService(JdbcTemplate jdbc, ObjectMapper json, @Value("${app.frontend.url:http://localhost:5173}") String frontendUrl,
                                       za.co.handyflow.platform.shared.FileStorageService storage) {
        this.jdbc = jdbc; this.json = json; this.frontendUrl = frontendUrl; this.storage = storage;
    }

    /** What is printed on the document to identify it. {@code verifyUrl} is the public page; the code is typed there. */
    public record Stamp(String number, String code, String verifyUrl) {}

    /** {@code reused}: the standing copy was handed back and no new number was taken (patch 0203). */
    public record Issued(UUID id, String number, byte[] pdf, boolean reused) {
        public Issued(UUID id, String number, byte[] pdf) { this(id, number, pdf, false); }
    }

    /**
     * @param render makes the PDF once the number and code are known
     * @param snapshot the data the document was made from; kept exactly as it was
     */
    @Transactional
    public Issued issue(UUID tenantId, String type, UUID patientId, UUID consultationId, String templateRef, UUID letterheadId,
                        String issuerName, String rendererName, Map<String, Object> snapshot, UUID by, Function<Stamp, byte[]> render) {
        return issue(tenantId, type, null, patientId, consultationId, templateRef, letterheadId, issuerName, rendererName, snapshot, by, render);
    }

    /** As above, with the record it was made from ({@code sourceRef}: a visit id, a consent event id). The PDF is kept (patch 0203). */
    @Transactional
    public Issued issue(UUID tenantId, String type, String sourceRef, UUID patientId, UUID consultationId, String templateRef, UUID letterheadId,
                        String issuerName, String rendererName, Map<String, Object> snapshot, UUID by, Function<Stamp, byte[]> render) {
        if (!DocumentNumberRules.known(type)) throw new IllegalArgumentException("Unknown document type: " + type);
        int year = LocalDate.now(AppointmentRules.CLINIC_ZONE).getYear();
        Integer next = jdbc.queryForObject("""
                INSERT INTO clinic_document_counters (tenant_id, doc_type, year, last_value) VALUES (?,?,?,1)
                ON CONFLICT (tenant_id, doc_type, year) DO UPDATE SET last_value = clinic_document_counters.last_value + 1
                RETURNING last_value""", Integer.class, tenantId, type, year);
        String number = DocumentNumberRules.format(type, year, next == null ? 1 : next);
        String code = DocumentIntegrityRules.newCode();
        Stamp stamp = new Stamp(number, code, frontendUrl.replaceAll("/+$", "") + "/verify-document");
        byte[] pdf = render.apply(stamp);
        if (pdf == null || pdf.length == 0) throw new IllegalStateException("The document could not be made");
        UUID id = UUID.randomUUID();
        String key = keep(tenantId, patientId, number, pdf);
        jdbc.update("""
                INSERT INTO clinic_issued_documents (id, tenant_id, doc_number, doc_type, patient_id, consultation_id, template_ref, letterhead_id,
                    issuer_name, renderer, snapshot, content_sha256, code_sha256, issued_by, source_ref, pdf_storage_key)
                VALUES (?,?,?,?,?,?,?,?,?,?,?::jsonb,?,?,?,?,?)""",
                id, tenantId, number, type, patientId, consultationId, templateRef, letterheadId, issuerName, rendererName,
                toJson(snapshot), DocumentIntegrityRules.sha256(pdf), DocumentIntegrityRules.codeHash(code), by, sourceRef, key);
        return new Issued(id, number, pdf);
    }

    /** The PDF goes into the shared file storage; a document that cannot be kept is not issued, so a number never points at nothing. */
    private String keep(UUID tenantId, UUID patientId, String number, byte[] pdf) {
        try { return storage.store("clinic-issued-documents/" + tenantId + "/" + patientId, number + ".pdf", "application/pdf", pdf); }
        catch (java.io.IOException e) { throw new IllegalStateException("The document could not be kept", e); }
    }

    /** The copy of a type issued for a source and not withdrawn, newest first. */
    private record Standing(UUID id, String number, String snapshot, String storageKey) {}

    private java.util.Optional<Standing> standing(UUID tenantId, String type, String sourceRef) {
        return jdbc.query("""
                SELECT id, doc_number, snapshot::text, pdf_storage_key FROM clinic_issued_documents
                WHERE tenant_id=? AND doc_type=? AND source_ref=? AND status='ISSUED' ORDER BY issued_at DESC, doc_number DESC LIMIT 1""",
                (rs, i) -> new Standing((UUID) rs.getObject(1), rs.getString(2), rs.getString(3), rs.getString(4)), tenantId, type, sourceRef)
                .stream().findFirst();
    }

    /**
     * One standing document per source (patch 0203, {@link IssueOnceRules}): hands back the copy already issued when nothing changed,
     * replaces it (withdrawing the old one with the reason) when the policy or a reissue reason says so, and otherwise refuses with the
     * number that stands, so a changed prescription is never issued twice without someone deciding to.
     */
    @Transactional
    public Issued issueOnce(UUID tenantId, String type, String sourceRef, UUID patientId, UUID consultationId, UUID letterheadId,
                            String issuerName, String rendererName, Map<String, Object> snapshot, UUID by,
                            IssueOnceRules.WhenChanged policy, String reissueReason, Function<Stamp, byte[]> render) {
        String reason = IssueOnceRules.reason(reissueReason);
        var current = standing(tenantId, type, sourceRef);
        boolean same = current.isPresent() && sameSnapshot(current.get().snapshot(), snapshot);
        boolean kept = current.isPresent() && current.get().storageKey() != null;
        switch (IssueOnceRules.decide(current.isPresent(), same, kept, policy, reason != null)) {
            case REUSE -> {
                Standing st = current.get();
                return new Issued(st.id(), st.number(), read(st.storageKey()), true);
            }
            case REFUSE -> throw new IllegalStateException(DocumentNumberRules.label(type) + " " + current.get().number()
                    + " was issued before this visit changed. Reissue it with a reason; the earlier copy will be withdrawn.");
            case REPLACE -> voidIt(tenantId, current.get().id(), by, IssueOnceRules.replacedBecause(DocumentNumberRules.label(type), reason));
            case ISSUE -> { }
        }
        return issue(tenantId, type, sourceRef, patientId, consultationId, null, letterheadId, issuerName, rendererName, snapshot, by, render);
    }

    private boolean sameSnapshot(String stored, Map<String, Object> now) {
        try { return json.readTree(stored).equals(json.readTree(toJson(now))); }   // both sides parsed the same way
        catch (Exception e) { return false; }
    }

    private byte[] read(String key) {
        try { return storage.retrieve(key); }
        catch (java.io.IOException e) { throw new IllegalStateException("The kept copy of the document could not be read", e); }
    }

    /** The kept PDF of one of this patient's documents. Documents issued before patch 0203 have none. */
    @Transactional(readOnly = true)
    public PatientCopy copy(UUID tenantId, UUID patientId, UUID id) {
        var row = jdbc.query("SELECT doc_number, doc_type, status, pdf_storage_key FROM clinic_issued_documents WHERE tenant_id=? AND patient_id=? AND id=?",
                (rs, i) -> new String[]{rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4)}, tenantId, patientId, id)
                .stream().findFirst().orElseThrow(() -> new ResourceNotFoundException("Issued document", id.toString()));
        if (row[3] == null) throw new IllegalStateException(row[0] + " was issued before copies were kept, so it cannot be downloaded again. Issue a new one instead.");
        return new PatientCopy(id, row[0], row[1], "VOIDED".equals(row[2]), read(row[3]));
    }

    public record PatientCopy(UUID id, String number, String type, boolean withdrawn, byte[] pdf) {}

    /** One of a patient's documents as the patient file shows it, with the last time it was sent. */
    public record PatientDocumentRow(UUID id, String number, String type, String typeLabel, UUID consultationId, java.time.Instant issuedAt,
                                     String status, String voidReason, boolean copyKept, java.time.Instant lastSentAt, String lastSentTo) {}

    /** Newest first; {@code consultationId} and {@code type} narrow the list when given. */
    @Transactional(readOnly = true)
    public java.util.List<PatientDocumentRow> forPatient(UUID tenantId, UUID patientId, UUID consultationId, String type) {
        return jdbc.query("""
                SELECT d.id, d.doc_number, d.doc_type, d.consultation_id, d.issued_at, d.status, d.void_reason, d.pdf_storage_key IS NOT NULL,
                       x.sent_at, x.recipient
                FROM clinic_issued_documents d
                LEFT JOIN LATERAL (SELECT sent_at, recipient FROM clinic_document_deliveries v
                                   WHERE v.tenant_id = d.tenant_id AND v.issued_document_id = d.id AND v.status = 'QUEUED'
                                   ORDER BY sent_at DESC LIMIT 1) x ON true
                WHERE d.tenant_id=? AND d.patient_id=? AND (?::uuid IS NULL OR d.consultation_id=?) AND (?::text IS NULL OR d.doc_type=?)
                ORDER BY d.issued_at DESC, d.doc_number DESC LIMIT 200""",
                (rs, i) -> new PatientDocumentRow((UUID) rs.getObject(1), rs.getString(2), rs.getString(3), DocumentNumberRules.label(rs.getString(3)),
                        (UUID) rs.getObject(4), rs.getTimestamp(5).toInstant(), rs.getString(6), rs.getString(7), rs.getBoolean(8),
                        rs.getTimestamp(9) == null ? null : rs.getTimestamp(9).toInstant(), rs.getString(10)),
                tenantId, patientId, consultationId, consultationId, blank(type), blank(type));
    }

    /** One delivery, kept for ever (CLINIC-DEC-024: delivery is its own, recorded step). */
    @Transactional
    public void recordDelivery(UUID tenantId, UUID documentId, String channel, String recipient, String status, String detail, UUID by) {
        jdbc.update("INSERT INTO clinic_document_deliveries (id, tenant_id, issued_document_id, channel, recipient, status, detail, sent_by) VALUES (?,?,?,?,?,?,?,?)",
                UUID.randomUUID(), tenantId, documentId, channel, recipient, status, detail, by);
    }

    /** Withdraws a document. The record stays, shows as voided, and the public check says so. */
    @Transactional
    public void voidIt(UUID tenantId, UUID id, UUID by, String reason) {
        if (reason == null || reason.trim().length() < 5) throw new IllegalArgumentException("Give a reason of at least 5 characters");
        int n = jdbc.update("UPDATE clinic_issued_documents SET status='VOIDED', voided_at=now(), voided_by=?, void_reason=? WHERE tenant_id=? AND id=? AND status='ISSUED'",
                by, reason.trim(), tenantId, id);
        if (n == 0) throw new ResourceNotFoundException("Issued document", id.toString());
    }

    /** One issued document as the administrator sees it. Never carries the verification code (only its hash is kept) or the PDF. */
    public record IssuedRow(UUID id, String number, String type, String typeLabel, UUID patientId, String patientName, String issuerName,
                            java.time.Instant issuedAt, String status, java.time.Instant voidedAt, String voidReason) {}

    /** Newest first. {@code type} and {@code status} narrow the list when given; a number or patient name fragment in {@code q} narrows it further. */
    @Transactional(readOnly = true)
    public java.util.List<IssuedRow> list(UUID tenantId, String type, String status, String q, int limit) {
        int max = Math.max(1, Math.min(limit <= 0 ? 100 : limit, 500));
        String like = q == null || q.isBlank() ? null : "%" + q.trim().toLowerCase(java.util.Locale.ROOT).replace("%", "").replace("_", "") + "%";
        return jdbc.query("""
                SELECT d.id, d.doc_number, d.doc_type, d.patient_id, p.full_name, d.issuer_name, d.issued_at, d.status, d.voided_at, d.void_reason
                FROM clinic_issued_documents d LEFT JOIN clinic_patients p ON p.id = d.patient_id AND p.tenant_id = d.tenant_id
                WHERE d.tenant_id = ? AND (?::text IS NULL OR d.doc_type = ?) AND (?::text IS NULL OR d.status = ?)
                  AND (?::text IS NULL OR lower(d.doc_number) LIKE ? OR lower(coalesce(p.full_name,'')) LIKE ?)
                ORDER BY d.issued_at DESC, d.doc_number DESC LIMIT ?""",
                (rs, i) -> new IssuedRow((UUID) rs.getObject(1), rs.getString(2), rs.getString(3), DocumentNumberRules.label(rs.getString(3)),
                        (UUID) rs.getObject(4), rs.getString(5), rs.getString(6), rs.getTimestamp(7).toInstant(), rs.getString(8),
                        rs.getTimestamp(9) == null ? null : rs.getTimestamp(9).toInstant(), rs.getString(10)),
                tenantId, blank(type), blank(type), blank(status), blank(status), like, like, like, max);
    }

    private static String blank(String s) { return s == null || s.isBlank() ? null : s.trim(); }

    /** The public check. Looks only at the code; answers only with authenticity data. */
    @Transactional(readOnly = true)
    public VerifyResponse verify(String typedCode) {
        String hash = DocumentIntegrityRules.codeHash(typedCode);
        if (hash == null) return VerifyResponse.notFound();
        return jdbc.query("SELECT doc_type, doc_number, issued_at, issuer_name, status FROM clinic_issued_documents WHERE code_sha256=?",
                (rs, i) -> new VerifyResponse(true, "VOIDED".equals(rs.getString(5)), DocumentNumberRules.label(rs.getString(1)), rs.getString(2),
                        rs.getTimestamp(3).toLocalDateTime().atZone(java.time.ZoneId.systemDefault()).withZoneSameInstant(AppointmentRules.CLINIC_ZONE).toLocalDate().format(LONG_DATE),
                        rs.getString(4)), hash).stream().findFirst().orElse(VerifyResponse.notFound());
    }

    private String toJson(Map<String, Object> m) {
        try { return json.writeValueAsString(m == null ? Map.of() : m); } catch (Exception e) { throw new IllegalStateException("The document data could not be kept", e); }
    }
}
