package za.co.handyflow.platform.clinic.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** The documents register of a patient (patch 0163). */
public final class DocumentDtos {
    private DocumentDtos() {}

    /**
     * One line in the register. {@code origin} is STORED (a file the clinic holds: uploaded or issued) or RECORD (a document
     * the system produces from the record on request: visit summary, prescription, lab report). {@code downloadPath} is
     * where the file comes from; {@code removable} is true only for stored entries.
     */
    public record RegisterItem(String origin, UUID id, String docType, String source, String title, LocalDate date, String notes,
                               String fileName, Long sizeBytes, String addedBy, UUID consultationId, String downloadPath,
                               String contentType, boolean removable) {}

    public record RegisterResponse(UUID patientId, List<RegisterItem> items) {}
}
