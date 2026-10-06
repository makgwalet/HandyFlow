package za.co.handyflow.platform.complianceservices.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import za.co.handyflow.platform.businessreadiness.ReadinessAssessment;
import za.co.handyflow.platform.businessreadiness.ReadinessItem;
import za.co.handyflow.platform.businessreadiness.ReadinessResult;
import za.co.handyflow.platform.complianceservices.domain.model.ClientComplianceDocument;
import za.co.handyflow.platform.complianceservices.domain.model.ClientComplianceRegistration;
import za.co.handyflow.platform.complianceservices.domain.model.ClientComplianceRequirement;
import za.co.handyflow.platform.complianceservices.domain.model.ClientTender;
import za.co.handyflow.platform.complianceservices.domain.model.ClientTenderRequirement;
import za.co.handyflow.platform.complianceservices.domain.repository.ClientComplianceDocumentRepository;
import za.co.handyflow.platform.complianceservices.domain.repository.ClientComplianceRegistrationRepository;
import za.co.handyflow.platform.complianceservices.domain.repository.ClientComplianceRequirementRepository;
import za.co.handyflow.platform.complianceservices.domain.repository.ClientTenderRepository;
import za.co.handyflow.platform.complianceservices.domain.repository.ClientTenderRequirementRepository;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * The CLIENT side of business readiness: it gathers the tender's requirements, the tracked requirements they link to, and the CLIENT's own registrations and documents, and
 * hands them to the shared evaluator. The judging rules themselves are tested in RequirementReadinessEvaluatorTest; these tests pin what THIS service feeds it.
 */
@ExtendWith(MockitoExtension.class)
class ClientClientTenderReadinessServiceTest {

    @Mock private ClientTenderRepository tenderRepository;
    @Mock private ClientTenderRequirementRepository requirementRepository;
    @Mock private ClientComplianceRequirementRepository catalogueRepository;
    @Mock private ClientComplianceRegistrationRepository registrationRepository;
    @Mock private ClientComplianceDocumentRepository documentRepository;

    private ClientTenderReadinessService service() {
        return new ClientTenderReadinessService(tenderRepository, requirementRepository, catalogueRepository, registrationRepository, documentRepository);
    }

    private static final TenantId TENANT = TenantId.generate();
    private static final UUID USER = UUID.randomUUID();
    private static final UUID CLIENT = UUID.randomUUID();
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 4);
    private static final LocalDate CLOSING = LocalDate.of(2026, 11, 15);

    private UUID tender(LocalDate closing) {
        UUID id = UUID.randomUUID();
        when(tenderRepository.findByIdForTenant(TENANT, id)).thenReturn(Optional.of(ClientTender.create(TENANT, CLIENT, "CTND-00001", "Road Upgrade", null, null, closing, null, null, null, null, null, USER)));
        return id;
    }

    /** A tender requirement linked to a tracked requirement (found for this tenant) that has the given rule. */
    private ClientTenderRequirement linked(UUID tenderId, String code, String authority, String registrationType, String evidenceType, String manualStatus) {
        UUID linkId = UUID.randomUUID();
        ClientComplianceRequirement tracked = ClientComplianceRequirement.create(TENANT, CLIENT, code, code, null, evidenceType, true, authority, registrationType, USER);
        when(catalogueRepository.findByIdForTenant(TENANT, linkId)).thenReturn(Optional.of(tracked));
        when(catalogueRepository.findLatestByCode(TENANT, CLIENT, code)).thenReturn(Optional.of(tracked));
        ClientTenderRequirement r = ClientTenderRequirement.create(TENANT, tenderId, linkId, "Requirement " + code, "COMPLIANCE", USER);
        if (manualStatus != null) r.setStatus(manualStatus, USER);
        return r;
    }

    private static ClientComplianceRegistration registration(String authority, String type, String status, LocalDate expiry) {
        ClientComplianceRegistration g = ClientComplianceRegistration.create(TENANT, CLIENT, authority, type, "REG-1", null, expiry, null, USER);
        if (!"ACTIVE".equals(status)) g.update("REG-1", status, null, expiry, null, USER);
        return g;
    }

    private static ClientComplianceDocument document(String type, LocalDate expiry, boolean verified) {
        ClientComplianceDocument d = ClientComplianceDocument.create(UUID.randomUUID(), TENANT, CLIENT, null, type, UUID.randomUUID(), null, expiry, USER);
        if (verified) d.verify(USER);
        return d;
    }

    private static ReadinessItem only(ReadinessAssessment a) { return a.items().get(0); }

    @Test
    @DisplayName("a tender with a closing date is judged as of that date, and says so")
    void judgedAsOfTheClosingDate() {
        UUID tenderId = tender(CLOSING);
        ClientTenderRequirement r = linked(tenderId, "BBBEE", "BBBEE", null, null, null);
        when(requirementRepository.findByTender(TENANT, tenderId)).thenReturn(List.of(r));
        // valid today, but it expires the day before the tender closes
        when(registrationRepository.findByClient(TENANT, CLIENT)).thenReturn(List.of(registration("BBBEE", "Certificate", "ACTIVE", CLOSING.minusDays(1))));

        ReadinessAssessment a = service().assess(TENANT, tenderId, TODAY);

        assertThat(a.asOf()).isEqualTo(CLOSING);
        assertThat(a.asOfBasis()).isEqualTo("CLOSING_DATE");
        assertThat(only(a).result()).isEqualTo(ReadinessResult.EXPIRED);
        assertThat(only(a).detail()).contains("before the closing date");
    }

    @Test
    @DisplayName("a tender with no closing date is judged as of today")
    void noClosingDate_judgedToday() {
        UUID tenderId = tender(null);
        when(requirementRepository.findByTender(TENANT, tenderId)).thenReturn(List.of());

        ReadinessAssessment a = service().assess(TENANT, tenderId, TODAY);

        assertThat(a.asOf()).isEqualTo(TODAY);
        assertThat(a.asOfBasis()).isEqualTo("TODAY");
    }

    @Test
    @DisplayName("a registration rule is met by a valid registration of that authority")
    void registrationRule_met() {
        UUID tenderId = tender(CLOSING);
        var stub1 = List.of(linked(tenderId, "CSD_ACTIVE", "CSD", "Supplier", null, null));
        when(requirementRepository.findByTender(TENANT, tenderId)).thenReturn(stub1);
        when(registrationRepository.findByClient(TENANT, CLIENT)).thenReturn(List.of(registration("csd", "supplier", "ACTIVE", null)));

        assertThat(only(service().assess(TENANT, tenderId, TODAY)).result()).isEqualTo(ReadinessResult.MET);
    }

    @Test
    @DisplayName("the document that satisfies a requirement is its evidenceType: verified is MET, unverified is PENDING")
    void documentRule_usesEvidenceType() {
        UUID tenderId = tender(CLOSING);
        var stub2 = List.of(linked(tenderId, "BBBEE_CERT", null, null, "BBBEE Certificate", null));
        when(requirementRepository.findByTender(TENANT, tenderId)).thenReturn(stub2);
        when(documentRepository.findByClient(TENANT, CLIENT)).thenReturn(List.of(document("BBBEE Certificate", LocalDate.of(2027, 6, 1), true)));

        assertThat(only(service().assess(TENANT, tenderId, TODAY)).result()).isEqualTo(ReadinessResult.MET);
    }

    @Test
    @DisplayName("an uploaded but unverified document is PENDING")
    void documentRule_unverifiedIsPending() {
        UUID tenderId = tender(CLOSING);
        var stub3 = List.of(linked(tenderId, "BBBEE_CERT", null, null, "BBBEE Certificate", null));
        when(requirementRepository.findByTender(TENANT, tenderId)).thenReturn(stub3);
        when(documentRepository.findByClient(TENANT, CLIENT)).thenReturn(List.of(document("BBBEE Certificate", LocalDate.of(2027, 6, 1), false)));

        assertThat(only(service().assess(TENANT, tenderId, TODAY)).result()).isEqualTo(ReadinessResult.PENDING);
    }

    @Test
    @DisplayName("a requirement typed by hand (no link) is not evaluated, and the catalogue is not asked")
    void unlinked_notEvaluated() {
        UUID tenderId = tender(CLOSING);
        ClientTenderRequirement typed = ClientTenderRequirement.create(TENANT, tenderId, null, "Typed by hand", "MANUAL", USER);
        when(requirementRepository.findByTender(TENANT, tenderId)).thenReturn(List.of(typed));

        ReadinessItem item = only(service().assess(TENANT, tenderId, TODAY));

        assertThat(item.result()).isEqualTo(ReadinessResult.NOT_EVALUATED);
        assertThat(item.detail()).contains("Not linked");
        verifyNoInteractions(catalogueRepository);
    }

    @Test
    @DisplayName("a link that does not resolve for this tenant is treated as not linked: nothing is judged against someone else's rule")
    void unresolvedLink_notJudged() {
        UUID tenderId = tender(CLOSING), foreignId = UUID.randomUUID();
        when(catalogueRepository.findByIdForTenant(TENANT, foreignId)).thenReturn(Optional.empty());
        when(requirementRepository.findByTender(TENANT, tenderId)).thenReturn(List.of(ClientTenderRequirement.create(TENANT, tenderId, foreignId, "Points elsewhere", "COMPLIANCE", USER)));

        ReadinessItem item = only(service().assess(TENANT, tenderId, TODAY));

        assertThat(item.result()).isEqualTo(ReadinessResult.NOT_EVALUATED);
        assertThat(item.requirementId()).isNull();
    }

    @Test
    @DisplayName("a tracked requirement with no rule is not evaluated and says no rule is set")
    void noRule_notEvaluated() {
        UUID tenderId = tender(CLOSING);
        var stub4 = List.of(linked(tenderId, "BEE_PLAN", null, null, null, null));
        when(requirementRepository.findByTender(TENANT, tenderId)).thenReturn(stub4);

        ReadinessItem item = only(service().assess(TENANT, tenderId, TODAY));

        assertThat(item.result()).isEqualTo(ReadinessResult.NOT_EVALUATED);
        assertThat(item.detail()).contains("No evidence rule");
    }

    @Test
    @DisplayName("it reports when a newer version of the tracked requirement exists than the one the tender was linked to")
    void newerVersionAvailable() {
        UUID tenderId = tender(CLOSING), linkId = UUID.randomUUID();
        ClientComplianceRequirement v1 = ClientComplianceRequirement.create(TENANT, CLIENT, "CSD_ACTIVE", "CSD", null, null, true, "CSD", null, USER);
        ClientComplianceRequirement v2 = v1.newVersion("CSD (2026 rules)", null, null, true, USER);
        when(catalogueRepository.findByIdForTenant(TENANT, linkId)).thenReturn(Optional.of(v1));
        when(catalogueRepository.findLatestByCode(TENANT, CLIENT, "CSD_ACTIVE")).thenReturn(Optional.of(v2));
        when(requirementRepository.findByTender(TENANT, tenderId)).thenReturn(List.of(ClientTenderRequirement.create(TENANT, tenderId, linkId, "CSD", "COMPLIANCE", USER)));

        assertThat(only(service().assess(TENANT, tenderId, TODAY)).newerVersionAvailable()).isTrue();
    }

    @Test
    @DisplayName("when the linked version is the latest there is no newer-version flag")
    void noNewerVersion() {
        UUID tenderId = tender(CLOSING);
        var stub5 = List.of(linked(tenderId, "CSD_ACTIVE", "CSD", null, null, null));
        when(requirementRepository.findByTender(TENANT, tenderId)).thenReturn(stub5);

        assertThat(only(service().assess(TENANT, tenderId, TODAY)).newerVersionAvailable()).isFalse();
    }

    @Test
    @DisplayName("requirements sharing one tracked requirement look it up once, not once each")
    void sharedTrackedRequirement_lookedUpOnce() {
        UUID tenderId = tender(CLOSING), linkId = UUID.randomUUID();
        ClientComplianceRequirement tracked = ClientComplianceRequirement.create(TENANT, CLIENT, "CSD_ACTIVE", "CSD", null, null, true, "CSD", null, USER);
        when(catalogueRepository.findByIdForTenant(TENANT, linkId)).thenReturn(Optional.of(tracked));
        when(catalogueRepository.findLatestByCode(TENANT, CLIENT, "CSD_ACTIVE")).thenReturn(Optional.of(tracked));
        when(requirementRepository.findByTender(TENANT, tenderId)).thenReturn(List.of(
                ClientTenderRequirement.create(TENANT, tenderId, linkId, "First", "COMPLIANCE", USER), ClientTenderRequirement.create(TENANT, tenderId, linkId, "Second", "COMPLIANCE", USER)));

        ReadinessAssessment a = service().assess(TENANT, tenderId, TODAY);

        assertThat(a.items()).hasSize(2);
        verify(catalogueRepository, times(1)).findByIdForTenant(TENANT, linkId);
        verify(catalogueRepository, times(1)).findLatestByCode(TENANT, CLIENT, "CSD_ACTIVE");
    }

    @Test
    @DisplayName("ticked MET with no evidence is flagged as differing, and the tick itself is left alone")
    void tickedMetButMissing_flagged() {
        UUID tenderId = tender(CLOSING);
        var stub6 = List.of(linked(tenderId, "CSD_ACTIVE", "CSD", null, null, "MET"));
        when(requirementRepository.findByTender(TENANT, tenderId)).thenReturn(stub6);

        ReadinessAssessment a = service().assess(TENANT, tenderId, TODAY);

        assertThat(only(a).result()).isEqualTo(ReadinessResult.MISSING);
        assertThat(only(a).differsFromManualStatus()).isTrue();
        assertThat(only(a).manualStatus()).isEqualTo("MET");
        assertThat(a.summary().differFromManualStatus()).isEqualTo(1);
    }

    @Test
    @DisplayName("a requirement ticked NOT_APPLICABLE is not evaluated")
    void notApplicableTick() {
        UUID tenderId = tender(CLOSING);
        var stub7 = List.of(linked(tenderId, "NHBRC", "NHBRC", null, null, "NOT_APPLICABLE"));
        when(requirementRepository.findByTender(TENANT, tenderId)).thenReturn(stub7);

        assertThat(only(service().assess(TENANT, tenderId, TODAY)).result()).isEqualTo(ReadinessResult.NOT_APPLICABLE);
    }

    @Test
    @DisplayName("an unknown client tender is a 404 and nothing else is read")
    void unknownTender() {
        UUID tenderId = UUID.randomUUID();
        when(tenderRepository.findByIdForTenant(TENANT, tenderId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().assess(TENANT, tenderId, TODAY)).isInstanceOf(ResourceNotFoundException.class);

        verifyNoInteractions(requirementRepository, catalogueRepository, registrationRepository, documentRepository);
    }

    @Test
    @DisplayName("only the tender's own client's registrations and documents are read")
    void onlyTheTendersClientsRecords() {
        UUID tenderId = tender(CLOSING);
        when(requirementRepository.findByTender(TENANT, tenderId)).thenReturn(List.of());

        service().assess(TENANT, tenderId, TODAY);

        verify(registrationRepository).findByClient(TENANT, CLIENT);
        verify(documentRepository).findByClient(TENANT, CLIENT);
        verifyNoMoreInteractions(registrationRepository, documentRepository);
    }

    @Test
    @DisplayName("a tracked requirement that belongs to a DIFFERENT client is not used to judge this client: treated as not linked")
    void anotherClientsRequirement_isNotJudged() {
        UUID tenderId = tender(CLOSING), linkId = UUID.randomUUID();
        ClientComplianceRequirement theirs = ClientComplianceRequirement.create(TENANT, UUID.randomUUID(), "CSD_ACTIVE", "CSD", null, null, true, "CSD", null, USER);
        when(catalogueRepository.findByIdForTenant(TENANT, linkId)).thenReturn(Optional.of(theirs));
        when(requirementRepository.findByTender(TENANT, tenderId)).thenReturn(List.of(ClientTenderRequirement.create(TENANT, tenderId, linkId, "Wrong client's rule", "COMPLIANCE", USER)));
        when(registrationRepository.findByClient(TENANT, CLIENT)).thenReturn(List.of(registration("CSD", "Supplier", "ACTIVE", null)));

        ReadinessItem item = only(service().assess(TENANT, tenderId, TODAY));

        assertThat(item.result()).isEqualTo(ReadinessResult.NOT_EVALUATED);     // not MET, even though a CSD registration exists: the rule is not this client's
        assertThat(item.requirementId()).isNull();
    }

    @Test
    @DisplayName("the tender's client is the one whose records are read, never another client's")
    void factsAreTheTendersClients() {
        UUID tenderId = tender(CLOSING);
        when(requirementRepository.findByTender(TENANT, tenderId)).thenReturn(List.of());

        service().assess(TENANT, tenderId, TODAY);

        verify(registrationRepository).findByClient(TENANT, CLIENT);
        verify(documentRepository).findByClient(TENANT, CLIENT);
    }

    @Test
    @DisplayName("a registration's own status is what is judged: a PENDING registration is PENDING")
    void registrationStatusIsPassedThrough() {
        UUID tenderId = tender(CLOSING);
        var stub8 = List.of(linked(tenderId, "CIDB", "CIDB", null, null, null));
        when(requirementRepository.findByTender(TENANT, tenderId)).thenReturn(stub8);
        when(registrationRepository.findByClient(TENANT, CLIENT)).thenReturn(List.of(registration("CIDB", "Contractor", "PENDING", null)));

        assertThat(only(service().assess(TENANT, tenderId, TODAY)).result()).isEqualTo(ReadinessResult.PENDING);
    }

    @Test
    @DisplayName("a document's expiry date is what is judged: an expired verified document is EXPIRED")
    void documentExpiryIsPassedThrough() {
        UUID tenderId = tender(CLOSING);
        var stub9 = List.of(linked(tenderId, "INSURANCE", null, null, "Insurance", null));
        when(requirementRepository.findByTender(TENANT, tenderId)).thenReturn(stub9);
        when(documentRepository.findByClient(TENANT, CLIENT)).thenReturn(List.of(document("Insurance", TODAY.minusDays(3), true)));

        assertThat(only(service().assess(TENANT, tenderId, TODAY)).result()).isEqualTo(ReadinessResult.EXPIRED);
    }
}
