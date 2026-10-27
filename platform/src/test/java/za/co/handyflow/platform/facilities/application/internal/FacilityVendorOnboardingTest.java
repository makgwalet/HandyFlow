package za.co.handyflow.platform.facilities.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.facilities.domain.model.FacilityVendor;
import za.co.handyflow.platform.facilities.domain.model.FacilityVendorDocument;
import za.co.handyflow.platform.facilities.domain.repository.FacilityVendorDocumentRepository;
import za.co.handyflow.platform.facilities.domain.repository.FacilityVendorRepository;
import za.co.handyflow.platform.facilities.dto.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;

import java.lang.reflect.Proxy;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/** Vendor onboarding: documents, approval with segregation of duties, suspension, audit. */
class FacilityVendorOnboardingTest {

    private final TenantId tenant = TenantId.generate();
    private final UUID creator = UUID.randomUUID();
    private final UUID approver = UUID.randomUUID();
    private final Map<UUID, FacilityVendor> vendors = new HashMap<>();
    private final Map<UUID, FacilityVendorDocument> docs = new LinkedHashMap<>();
    private final List<String> audit = new ArrayList<>();
    private final Set<String> authorities = new HashSet<>();

    private final FacilityVendorRepository repo = (FacilityVendorRepository) Proxy.newProxyInstance(
            getClass().getClassLoader(), new Class<?>[]{FacilityVendorRepository.class}, (p, m, a) -> switch (m.getName()) {
                case "save" -> { FacilityVendor v = (FacilityVendor) a[0]; vendors.put(v.getId(), v); yield v; }
                case "findActiveById" -> Optional.ofNullable(vendors.get(a[1]));
                default -> throw new UnsupportedOperationException(m.getName());
            });
    private final FacilityVendorDocumentRepository docRepo = (FacilityVendorDocumentRepository) Proxy.newProxyInstance(
            getClass().getClassLoader(), new Class<?>[]{FacilityVendorDocumentRepository.class}, (p, m, a) -> switch (m.getName()) {
                case "save" -> { FacilityVendorDocument d = (FacilityVendorDocument) a[0]; docs.put(d.getId(), d); yield d; }
                case "findByIdForTenant" -> Optional.ofNullable(docs.get(a[1]));
                case "findForVendor" -> docs.values().stream().filter(d -> d.getVendorId().equals(a[1])).toList();
                default -> throw new UnsupportedOperationException(m.getName());
            });
    private final FacilityAccessPolicy policy = new FacilityAccessPolicy(null, null, null) {
        @Override public boolean hasAuthority(String a) { return authorities.contains(a); }
    };
    private final FacilityAuditService auditService = new FacilityAuditService(null) {
        @Override public void record(TenantId t, String action, String type, UUID id, String details) { audit.add(action); }
    };
    private final FacilityVendorService service = new FacilityVendorService(repo, docRepo, policy, auditService);
    private final LocalDate today = FacilityTime.today();

    private UUID newVendor() {
        TenantContext.setUserId(creator.toString());
        return service.createVendor(tenant, new UpsertVendorRequest("ACME Plumbing", "plumbing", null, null, null, null)).id();
    }

    private void addRequiredDocs(UUID vendorId, int days) {
        for (String t : FacilityVendorCompliance.REQUIRED)
            service.addDocument(tenant, vendorId, new VendorDocumentRequest(t, "R1", "Insurer", today.minusDays(10), today.plusDays(days), null, null));
    }

    @Test
    void aNewVendorStartsPendingAndCreationIsAudited() {
        UUID id = newVendor();
        assertEquals("PENDING", vendors.get(id).getOnboardingStatus());
        assertEquals(creator, vendors.get(id).getCreatedByUserId());
        assertEquals(List.of("VENDOR_CREATED"), audit);
    }

    @Test
    void updatesAreAudited() {
        UUID id = newVendor();
        service.updateVendor(tenant, id, new UpsertVendorRequest("ACME Plumbing 2", "HVAC", null, null, null, null));
        assertEquals(List.of("VENDOR_CREATED", "VENDOR_UPDATED"), audit);
    }

    @Test
    void cannotApproveWithoutTheRequiredDocuments() {
        UUID id = newVendor();
        TenantContext.setUserId(approver.toString());
        HandyFlowException e = assertThrows(HandyFlowException.class, () -> service.approve(tenant, id));
        assertEquals("VENDOR_NOT_COMPLIANT", e.getErrorCode());
        assertTrue(e.getMessage().contains("COIDA_LETTER"));
        assertEquals("PENDING", vendors.get(id).getOnboardingStatus());
    }

    @Test
    void anotherPersonCanApproveOnceDocumentsAreOnFile() {
        UUID id = newVendor();
        addRequiredDocs(id, 200);
        TenantContext.setUserId(approver.toString());
        VendorResponse r = service.approve(tenant, id);
        assertEquals("APPROVED", r.onboardingStatus());
        assertEquals(approver, vendors.get(id).getApprovedByUserId());
        assertTrue(audit.contains("VENDOR_APPROVED"));
    }

    @Test
    void theCreatorCannotApproveTheirOwnVendor() {
        UUID id = newVendor();
        addRequiredDocs(id, 200);
        TenantContext.setUserId(creator.toString());
        HandyFlowException e = assertThrows(HandyFlowException.class, () -> service.approve(tenant, id));
        assertEquals("VENDOR_SELF_APPROVAL", e.getErrorCode());
        assertEquals("PENDING", vendors.get(id).getOnboardingStatus());
    }

    @Test
    void anAdminMayApproveTheirOwnVendorButItIsAuditedAsSuch() {
        UUID id = newVendor();
        addRequiredDocs(id, 200);
        authorities.add("FACILITIES_ADMIN");
        service.approve(tenant, id);
        assertTrue(audit.contains("VENDOR_SELF_APPROVED"));
        assertFalse(audit.contains("VENDOR_APPROVED"));
    }

    @Test
    void anApprovedVendorCannotBeApprovedAgainButCanBeSuspendedWithAReason() {
        UUID id = newVendor();
        addRequiredDocs(id, 200);
        TenantContext.setUserId(approver.toString());
        service.approve(tenant, id);
        assertThrows(IllegalStateException.class, () -> service.approve(tenant, id));
        assertThrows(IllegalArgumentException.class, () -> service.suspend(tenant, id, " "));
        assertEquals("SUSPENDED", service.suspend(tenant, id, "Failed a safety audit").onboardingStatus());
        assertTrue(audit.contains("VENDOR_SUSPENDED"));
    }

    @Test
    void aSuspendedVendorCanBeReinstatedOnceCompliant() {
        UUID id = newVendor();
        addRequiredDocs(id, 200);
        TenantContext.setUserId(approver.toString());
        service.approve(tenant, id);
        service.suspend(tenant, id, "Audit");
        assertEquals("APPROVED", service.approve(tenant, id).onboardingStatus());
        assertNull(vendors.get(id).getSuspendedReason());
    }

    @Test
    void complianceReportExplainsWhyAVendorIsNotEligible() {
        UUID id = newVendor();
        VendorComplianceResponse c = service.getCompliance(tenant, id);
        assertFalse(c.eligible());
        assertEquals("NON_COMPLIANT", c.complianceStatus());
        assertTrue(c.problems().contains("vendor is PENDING"));
        assertEquals(3, c.missing().size());

        addRequiredDocs(id, 200);
        TenantContext.setUserId(approver.toString());
        service.approve(tenant, id);
        VendorComplianceResponse ok = service.getCompliance(tenant, id);
        assertTrue(ok.eligible());
        assertEquals("COMPLIANT", ok.complianceStatus());
        assertEquals(3, ok.documents().size());
        assertTrue(ok.documents().stream().allMatch(VendorDocumentResponse::inForce));
    }

    @Test
    void revokingADocumentIsAuditedAndTakesAwayEligibility() {
        UUID id = newVendor();
        addRequiredDocs(id, 200);
        UUID insurance = docs.values().stream().filter(d -> d.getDocType().equals("PUBLIC_LIABILITY_INSURANCE")).findFirst().get().getId();
        service.revokeDocument(tenant, id, insurance, "Policy cancelled");
        assertTrue(audit.contains("VENDOR_DOCUMENT_REVOKED"));
        assertEquals(List.of("PUBLIC_LIABILITY_INSURANCE"), service.getCompliance(tenant, id).missing());
    }

    @Test
    void aDocumentOfAnotherVendorCannotBeRevokedThroughThisOne() {
        UUID a = newVendor();
        UUID b = newVendor();
        addRequiredDocs(a, 200);
        UUID doc = docs.keySet().iterator().next();
        assertThrows(ResourceNotFoundException.class, () -> service.revokeDocument(tenant, b, doc, "x"));
    }

    @Test
    void unknownVendorIsNotFoundForEveryOnboardingCall() {
        UUID nope = UUID.randomUUID();
        assertThrows(ResourceNotFoundException.class, () -> service.approve(tenant, nope));
        assertThrows(ResourceNotFoundException.class, () -> service.suspend(tenant, nope, "x"));
        assertThrows(ResourceNotFoundException.class, () -> service.getCompliance(tenant, nope));
        assertThrows(ResourceNotFoundException.class, () -> service.getDocuments(tenant, nope));
        assertThrows(ResourceNotFoundException.class, () -> service.addDocument(tenant, nope,
                new VendorDocumentRequest("COIDA_LETTER", null, null, null, today.plusDays(5), null, null)));
    }
}
