package za.co.handyflow.platform.trainingprovider.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.shared.TenantSequenceService;

import java.util.UUID;

/**
 * Atomic, gap-tolerant numbering via TenantSequenceService.
 * <p>
 * Course codes, certificate numbers and invoice numbers are FLAT
 * tenant-scoped sequences — these need to read as globally sequential
 * across the whole practice (a course catalogue shared by every
 * client, a certificate register, an invoice run), not restart per
 * client. Delegate numbers ARE per-client-scoped (key pattern
 * {@code "TRAINPROV_DELEGATE:" + clientId}, mirroring the confirmed
 * real convention warehousing/payroll-bureau/booking-agency use for
 * client-scoped numbering) — each client's own delegate roster reads
 * as its own numbered list. TenantSequenceService's own
 * safeSequenceName() guards against the composed-name-exceeds-
 * VARCHAR(50) crash that payroll bureau and booking agency both hit
 * historically, so no extra care is needed here for that.
 */
@Component
@RequiredArgsConstructor
public class TrainProvNumberGenerator {

    private static final String CLIENT_SEQUENCE = "TRAINPROV_CLIENT";
    private static final String COURSE_SEQUENCE = "TRAINPROV_COURSE";
    private static final String CERTIFICATE_SEQUENCE = "TRAINPROV_CERT";
    private static final String INVOICE_SEQUENCE = "TRAINPROV_INVOICE";
    private static final String DELEGATE_SEQUENCE_PREFIX = "TRAINPROV_DELEGATE:";

    private final TenantSequenceService sequenceService;

    public String nextClientCode(TenantId tenantId) {
        long next = sequenceService.nextValue(tenantId, CLIENT_SEQUENCE);
        return "CLI-%05d".formatted(next);
    }

    public String nextCourseCode(TenantId tenantId) {
        long next = sequenceService.nextValue(tenantId, COURSE_SEQUENCE);
        return "CRS-%05d".formatted(next);
    }

    public String nextCertificateNumber(TenantId tenantId) {
        long next = sequenceService.nextValue(tenantId, CERTIFICATE_SEQUENCE);
        return "CERT-%05d".formatted(next);
    }

    public String nextInvoiceNumber(TenantId tenantId) {
        long next = sequenceService.nextValue(tenantId, INVOICE_SEQUENCE);
        return "TPI-%05d".formatted(next);
    }

    public String nextDelegateNumber(TenantId tenantId, UUID clientId) {
        long next = sequenceService.nextValue(tenantId, DELEGATE_SEQUENCE_PREFIX + clientId);
        return "DEL-%05d".formatted(next);
    }
}
