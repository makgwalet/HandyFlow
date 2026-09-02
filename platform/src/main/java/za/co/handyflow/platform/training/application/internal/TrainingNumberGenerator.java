package za.co.handyflow.platform.training.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.shared.TenantSequenceService;

/**
 * Course codes and certificate numbers — atomic, gap-tolerant, via
 * {@code TenantSequenceService}, same pattern as
 * {@code CreditNoteNumberGenerator}. Unlike the provider-module
 * generators (e.g. warehousing's per-client-scoped sequences), this
 * module has no external-client concept, so both sequences are flat,
 * tenant-scoped names — no composite key, no MAX_SEQUENCE_NAME_LENGTH
 * risk.
 */
@Component
@RequiredArgsConstructor
public class TrainingNumberGenerator {

    private static final String COURSE_SEQUENCE = "TRAINING_COURSE";
    private static final String CERTIFICATE_SEQUENCE = "TRAINING_CERT";

    private final TenantSequenceService sequenceService;

    public String nextCourseCode(TenantId tenantId) {
        long next = sequenceService.nextValue(tenantId, COURSE_SEQUENCE);
        return "CRS-%05d".formatted(next);
    }

    public String nextCertificateNumber(TenantId tenantId) {
        long next = sequenceService.nextValue(tenantId, CERTIFICATE_SEQUENCE);
        return "CERT-%05d".formatted(next);
    }
}
