package za.co.handyflow.platform.clinic.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import za.co.handyflow.platform.clinic.application.internal.ClinicIssuedDocumentService.Issued;
import za.co.handyflow.platform.clinic.application.internal.ClinicIssuedDocumentService.Stamp;
import za.co.handyflow.platform.clinic.application.internal.document.DocumentModel;
import za.co.handyflow.platform.clinic.application.internal.document.DocumentModel.*;
import za.co.handyflow.platform.clinic.application.internal.document.DocumentRenderer;
import za.co.handyflow.platform.clinic.domain.model.ClinicPatient;
import za.co.handyflow.platform.clinic.domain.model.ClinicPractitioner;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * A general letter (prescription letter, fitness letter, appointment letter and so on). Since patch 0193 a letter is described as a
 * {@link DocumentModel}, printed on the practice's letterhead (with logo) by whichever {@link DocumentRenderer} is configured, and issued
 * with a number, a hash and a public verification code (CLINIC-DEC-023 to 026).
 */
@Service
@RequiredArgsConstructor
public class ClinicLetterPdfService {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH);

    private final ClinicLetterValues letterValues;
    private final ClinicLetterheadService letterheads;
    private final ClinicIssuedDocumentService issued;
    private final DocumentRenderer renderer;

    /** What a letter is made from, checked and loaded, before any number is used. */
    record Prepared(DocumentModel model, UUID patientId, UUID letterheadId, String issuerName, Map<String, Object> snapshot) {}

    /** Preview-style output with no number: kept for callers that only need the bytes. */
    public byte[] generate(TenantId t, UUID consultationId, String title, String body) {
        return generate(t, null, consultationId, title, body, null, null);
    }

    public byte[] generate(TenantId t, UUID patientId, UUID consultationId, String title, String body, String recipientName, String recipientCompany) {
        return generate(t, patientId, consultationId, title, body, recipientName, recipientCompany, null);
    }

    public byte[] generate(TenantId t, UUID patientId, UUID consultationId, String title, String body, String recipientName, String recipientCompany, UUID signedBy) {
        return renderer.render(prepare(t, patientId, consultationId, title, body, recipientName, recipientCompany, signedBy, null, null).model());
    }

    /** Makes the letter, numbers it and records it, in one transaction: if the letter cannot be made, no number is used. {@code signedBy}: the practitioner who signs (wins over the visit's doctor). */
    public Issued issue(TenantId t, UUID patientId, UUID consultationId, String title, String body, String recipientName, String recipientCompany,
                        UUID signedBy, UUID letterheadId, UUID by) {
        Prepared p = prepare(t, patientId, consultationId, title, body, recipientName, recipientCompany, signedBy, letterheadId, null);
        return issued.issue(t.getValue(), "LETTER", p.patientId(), consultationId, null, p.letterheadId(), p.issuerName(), renderer.name(), p.snapshot(), by,
                stamp -> renderer.render(withStamp(p.model(), stamp)));
    }

    static DocumentModel withStamp(DocumentModel m, Stamp stamp) {
        return new DocumentModel(m.title(), m.letterhead(), m.blocks(), DocumentNumberRules.footer(stamp.number(), stamp.code(), stamp.verifyUrl()), true);
    }

    Prepared prepare(TenantId t, UUID patientId, UUID consultationId, String title, String body, String recipientName, String recipientCompany,
                     UUID signedBy, UUID letterheadId, String branchName) {
        if (title == null || title.isBlank()) throw new IllegalArgumentException("Give the letter a title");
        if (body == null || body.isBlank()) throw new IllegalArgumentException("The letter has no text");
        if (body.length() > 8000) throw new IllegalArgumentException("The letter is longer than 8000 characters");
        if (!LetterMerge.unknown(body + " " + title).isEmpty()) throw new IllegalArgumentException("The letter has merge fields this system does not know: " + String.join(", ", LetterMerge.unknown(body + " " + title)));
        var loaded = letterValues.load(t, patientId, consultationId, recipientName, recipientCompany, signedBy);
        ClinicPatient p = loaded.patient();
        ClinicPractitioner dr = loaded.doctor();
        String mergedTitle = LetterMerge.render(title, loaded.values()).trim();
        String mergedBody = LetterMerge.render(body, loaded.values()).trim();
        String company = loaded.values().get("practice.name") == null ? "" : loaded.values().get("practice.name");
        String toName = blankToNull(recipientName);
        String toCompany = blankToNull(recipientCompany);
        String signer = dr == null ? null : drName(dr.getFullName());
        String signerLine = dr == null || dr.getHpcsaNumber() == null ? null : "HPCSA " + dr.getHpcsaNumber();

        var resolved = letterheads.resolve(t.getValue(), letterheadId, branchName);
        Letterhead head;
        if (resolved.isPresent()) {
            head = resolved.get().letterhead();
        } else {
            // No letterhead set up yet: the practice name and the signer, as the letter always had.
            List<String> lines = new ArrayList<>();
            if (signer != null) lines.add(signer + (signerLine == null ? "" : " · " + signerLine));
            head = new Letterhead(company, lines, null);
        }

        List<Block> blocks = new ArrayList<>();
        blocks.add(new TextBlock(LocalDate.now(AppointmentRules.CLINIC_ZONE).format(DATE), false, false));
        if (toName != null) blocks.add(new TextBlock(toName, true, false));
        if (toCompany != null) blocks.add(new TextBlock(toCompany, false, false));
        if (toName != null || toCompany != null) blocks.add(new Spacer(6));
        String dob = p.getDateOfBirth() == null ? "" : " (DOB " + p.getDateOfBirth().format(DATE) + ")";
        blocks.add(new TextBlock("Re: " + p.getFirstName() + " " + p.getLastName() + dob, true, false));
        blocks.add(new Heading(1, mergedTitle));
        for (String para : mergedBody.split("\\R\\s*\\R")) blocks.add(new TextBlock(para.trim(), false, false));
        String signerName = signer != null ? signer : company;
        blocks.add(new Signature("Kind regards,", signerName == null ? "" : signerName, signer != null ? signerLine : null, null));
        DocumentModel model = new DocumentModel(mergedTitle, head, blocks, null, true);

        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("title", mergedTitle);
        snapshot.put("body", mergedBody);
        snapshot.put("recipientName", toName);
        snapshot.put("recipientCompany", toCompany);
        snapshot.put("patientId", p.getId().toString());
        snapshot.put("patientName", p.getFirstName() + " " + p.getLastName());
        snapshot.put("patientDateOfBirth", p.getDateOfBirth() == null ? null : p.getDateOfBirth().toString());
        snapshot.put("signerName", signer);
        snapshot.put("signerLine", signerLine);
        snapshot.put("letterhead", Map.of("name", head.name() == null ? "" : head.name(), "lines", head.lines(), "hasLogo", head.logo() != null));
        return new Prepared(model, p.getId(), resolved.map(ClinicLetterheadService.Resolved::id).orElse(null), head.name() == null ? "" : head.name(), snapshot);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String drName(String fullName) {
        if (fullName == null) return "";
        String s = fullName.trim();
        return s.toLowerCase(Locale.ROOT).startsWith("dr") ? s : "Dr. " + s;
    }
}
