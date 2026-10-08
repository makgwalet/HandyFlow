package za.co.handyflow.platform.clinic.application.internal;

import za.co.handyflow.platform.clinic.domain.model.ClinicPatientDocument;
import za.co.handyflow.platform.clinic.dto.DocumentDtos.RegisterItem;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Builds the lines of the documents register and orders them. Pure. */
final class DocumentRegister {
    private DocumentRegister() {}

    private static final String BASE = "/api/v1/clinic";

    static boolean hasRecordDocuments(String visitStatus) { return "SIGNED".equals(visitStatus) || "LOCKED".equals(visitStatus); }

    static RegisterItem stored(ClinicPatientDocument d, String addedBy, UUID patientId) {
        return new RegisterItem("STORED", d.getId(), d.getDocType(), d.getSource(), d.getTitle(), d.getDocumentDate(), d.getNotes(),
                d.getFileName(), d.getSizeBytes(), addedBy, d.getConsultationId(),
                BASE + "/patients/" + patientId + "/documents/" + d.getId() + "/file", d.getContentType(), true);
    }

    static List<RegisterItem> forVisit(UUID consultationId, Instant at, String complaint, boolean hasMedicines) {
        LocalDate day = day(at);
        String why = complaint == null || complaint.isBlank() ? "" : ": " + complaint.trim();
        RegisterItem summary = new RegisterItem("RECORD", consultationId, "VISIT_SUMMARY", "RECORD", "Visit summary" + why, day, null,
                "visit-summary-" + consultationId + ".pdf", null, null, consultationId,
                BASE + "/consultations/" + consultationId + "/summary-pdf", "application/pdf", false);
        if (!hasMedicines) return List.of(summary);
        RegisterItem rx = new RegisterItem("RECORD", consultationId, "PRESCRIPTION", "RECORD", "Prescription" + why, day, null,
                "prescription-" + consultationId + ".pdf", null, null, consultationId,
                BASE + "/consultations/" + consultationId + "/prescription-pdf", "application/pdf", false);
        return List.of(summary, rx);
    }

    static RegisterItem labReport(UUID resultId, Instant at, String reference, String fileName) {
        String ref = reference == null || reference.isBlank() ? "" : ": " + reference.trim();
        return new RegisterItem("RECORD", resultId, "LAB_REPORT", "RECORD", "Lab report" + ref, day(at), null,
                fileName == null || fileName.isBlank() ? "lab-result.pdf" : fileName, null, null, null,
                BASE + "/lab/results/" + resultId + "/pdf", "application/pdf", false);
    }

    static List<RegisterItem> newestFirst(List<RegisterItem> items) {
        return items.stream()
                .sorted(Comparator.comparing(RegisterItem::date, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(RegisterItem::title, Comparator.nullsLast(String::compareToIgnoreCase)))
                .toList();
    }

    private static LocalDate day(Instant at) { return at == null ? null : at.atZone(AppointmentRules.CLINIC_ZONE).toLocalDate(); }
}
