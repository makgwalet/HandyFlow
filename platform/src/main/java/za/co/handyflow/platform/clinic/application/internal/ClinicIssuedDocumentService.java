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

    public ClinicIssuedDocumentService(JdbcTemplate jdbc, ObjectMapper json, @Value("${app.frontend.url:http://localhost:5173}") String frontendUrl) {
        this.jdbc = jdbc; this.json = json; this.frontendUrl = frontendUrl;
    }

    /** What is printed on the document to identify it. {@code verifyUrl} is the public page; the code is typed there. */
    public record Stamp(String number, String code, String verifyUrl) {}

    public record Issued(UUID id, String number, byte[] pdf) {}

    /**
     * @param render makes the PDF once the number and code are known
     * @param snapshot the data the document was made from; kept exactly as it was
     */
    @Transactional
    public Issued issue(UUID tenantId, String type, UUID patientId, UUID consultationId, String templateRef, UUID letterheadId,
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
        jdbc.update("""
                INSERT INTO clinic_issued_documents (id, tenant_id, doc_number, doc_type, patient_id, consultation_id, template_ref, letterhead_id,
                    issuer_name, renderer, snapshot, content_sha256, code_sha256, issued_by)
                VALUES (?,?,?,?,?,?,?,?,?,?,?::jsonb,?,?,?)""",
                id, tenantId, number, type, patientId, consultationId, templateRef, letterheadId, issuerName, rendererName,
                toJson(snapshot), DocumentIntegrityRules.sha256(pdf), DocumentIntegrityRules.codeHash(code), by);
        return new Issued(id, number, pdf);
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
