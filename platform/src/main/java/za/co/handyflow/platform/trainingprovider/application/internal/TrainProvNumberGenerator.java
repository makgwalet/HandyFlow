package za.co.handyflow.platform.trainingprovider.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.identity.TenantNumberingFacade;
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
 * <p>
 * MIGRATED (course + certificate only): a tenant running both this
 * module and {@code training} (internal staff training) independently
 * produced "CRS-00001" and "CERT-00001" from two completely unrelated
 * catalogues/registers — real, verified collision, same shape as the
 * invoicing/bookkeeping/facilitiesmanagement INV- one. {@code training}'s
 * own package-info doesn't allow depending on {@code identity} (same
 * boundary situation as {@code facilities} — see
 * PLATFORM-ENGINES-PROGRESS.md), so it can't be migrated from this pass;
 * instead this side is moved to distinguishable type codes ("TPCRS" /
 * "TPCERT", matching the "TP" family {@code nextInvoiceNumber} already
 * established with "TPI"), which fully resolves the visible ambiguity
 * without needing to touch {@code training}'s boundary at all —
 * {@code training}'s own numbers stay exactly "CRS-00001" /
 * "CERT-00001", now unambiguously distinct from this module's
 * "{tenantCode}-TPCRS-00001" / "{tenantCode}-TPCERT-00001".
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
    private final TenantNumberingFacade numberingFacade;

    public String nextClientCode(TenantId tenantId) {
        long next = sequenceService.nextValue(tenantId, CLIENT_SEQUENCE);
        return "CLI-%05d".formatted(next);
    }

    public String nextCourseCode(TenantId tenantId) {
        return numberingFacade.next(tenantId, COURSE_SEQUENCE, "TPCRS");
    }

    public String nextCertificateNumber(TenantId tenantId) {
        return numberingFacade.next(tenantId, CERTIFICATE_SEQUENCE, "TPCERT");
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
