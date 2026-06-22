package za.co.handyflow.platform.clinic.application.internal;

import org.junit.jupiter.api.*;
import org.mockito.Mockito;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import za.co.handyflow.platform.clinic.domain.model.*;
import za.co.handyflow.platform.clinic.domain.repository.*;
import za.co.handyflow.platform.clinic.dto.billing.*;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClinicBillingServiceTest {

    @Mock ClinicClaimRepository              claimRepo;
    @Mock ClinicConsultationRepository       consultationRepo;
    @Mock ClinicPatientRepository            patientRepo;
    @Mock ClinicPractitionerRepository       practitionerRepo;
    @Mock ClinicPrescriptionRepository       prescriptionRepo;
    @Mock ClinicMedicationCatalogueRepository medicationRepo;

    @InjectMocks
    ClinicBillingService service;

    // TenantId has a private constructor — create via Mockito mock
    // and stub getValue() which is what SpEL :#{#tenantId.value} and service internals call.
    static final UUID TENANT_UUID = UUID.fromString("9ecb3dc7-75d4-4e56-b0a2-c95d3c7c584f");
    static final TenantId TENANT;
    static {
        TENANT = Mockito.mock(TenantId.class);
        Mockito.when(TENANT.getValue()).thenReturn(TENANT_UUID);
    }

    ClinicConsultation consultation(UUID patientId) {
        return ClinicConsultation.create(TENANT, patientId,
                null, null, "Hypertension check");
    }

    // ══════════════════════════════════════════════════════════════════════════
    // createClaim
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("createClaim")
    class CreateClaim {

        @Test
        @DisplayName("creates claim with consultation tariff line")
        void createsClaimWithConsultationLine() {
            var consultId = UUID.randomUUID();
            var patientId = UUID.randomUUID();
            var consult   = consultation(patientId);

            when(claimRepo.findByConsultation(TENANT, consultId)).thenReturn(Optional.empty());
            when(consultationRepo.findActiveById(TENANT, consultId)).thenReturn(Optional.of(consult));
            when(prescriptionRepo.findByConsultation(TENANT, consultId)).thenReturn(List.of());
            when(claimRepo.save(any())).thenAnswer(i -> i.getArgument(0));

            var req = new CreateClaimRequest(
                    "Discovery Health", "DH12345678", "00",
                    "0191", "I10", "Consultation",
                    new BigDecimal("520.00"), List.of());

            var result = service.createClaim(TENANT, consultId, req);

            verify(claimRepo, times(2)).save(any(ClinicClaim.class));
            assertThat(result.schemeName()).isEqualTo("Discovery Health");
            assertThat(result.memberNumber()).isEqualTo("DH12345678");
            assertThat(result.lines()).hasSize(1);
            assertThat(result.lines().get(0).tariffCode()).isEqualTo("0191");
        }

        @Test
        @DisplayName("adds procedure lines from request")
        void addsProcedureLines() {
            var consultId = UUID.randomUUID();
            var consult   = consultation(UUID.randomUUID());

            when(claimRepo.findByConsultation(TENANT, consultId)).thenReturn(Optional.empty());
            when(consultationRepo.findActiveById(TENANT, consultId)).thenReturn(Optional.of(consult));
            when(prescriptionRepo.findByConsultation(TENANT, consultId)).thenReturn(List.of());
            when(claimRepo.save(any())).thenAnswer(i -> i.getArgument(0));

            var procedures = List.of(
                    new ProcedureLineRequest("0115", "I10", "Injection IM",
                            BigDecimal.ONE, new BigDecimal("85.00")));

            var req = new CreateClaimRequest("Discovery", "DH123", "00",
                    "0191", null, "Consultation",
                    new BigDecimal("520"), procedures);

            var result = service.createClaim(TENANT, consultId, req);

            assertThat(result.lines()).hasSize(2); // consultation + injection
            assertThat(result.lines()).anyMatch(l -> "0115".equals(l.tariffCode()));
        }

        @Test
        @DisplayName("throws when claim already exists for consultation")
        void throwsOnDuplicateClaim() {
            var consultId = UUID.randomUUID();
            when(claimRepo.findByConsultation(TENANT, consultId))
                    .thenReturn(Optional.of(ClinicClaim.create(TENANT,
                            consultId, UUID.randomUUID(), null,
                            null, null, null)));

            var req = new CreateClaimRequest("Discovery", "DH123", "00",
                    "0191", null, "Consultation",
                    new BigDecimal("520"), List.of());

            assertThatThrownBy(() -> service.createClaim(TENANT, consultId, req))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("already exists");
        }

        @Test
        @DisplayName("throws when consultation not found")
        void throwsWhenConsultationNotFound() {
            var consultId = UUID.randomUUID();
            when(claimRepo.findByConsultation(TENANT, consultId)).thenReturn(Optional.empty());
            when(consultationRepo.findActiveById(TENANT, consultId)).thenReturn(Optional.empty());

            var req = new CreateClaimRequest("Discovery", "DH123", "00",
                    "0191", null, "Consultation",
                    new BigDecimal("520"), List.of());

            assertThatThrownBy(() -> service.createClaim(TENANT, consultId, req))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // submitClaim / updateClaimStatus
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("claim lifecycle")
    class ClaimLifecycle {

        ClinicClaim draftClaim() {
            return ClinicClaim.create(TENANT, UUID.randomUUID(),
                    UUID.randomUUID(), null, "Discovery", "DH123", "00");
        }

        @Test
        @DisplayName("submit moves claim from DRAFT to SUBMITTED")
        void submitsClaim() {
            var id    = UUID.randomUUID();
            var claim = draftClaim();
            when(claimRepo.findActiveById(TENANT, id)).thenReturn(Optional.of(claim));
            when(patientRepo.findAllByIds(any(), anySet())).thenReturn(List.of());
            when(practitionerRepo.findAllByIds(any(), anySet())).thenReturn(List.of());

            var result = service.submitClaim(TENANT, id, "DH-2026-001");

            assertThat(result.status()).isEqualTo("SUBMITTED");
            assertThat(result.referenceNumber()).isEqualTo("DH-2026-001");
        }

        @Test
        @DisplayName("accept moves claim to ACCEPTED")
        void acceptsClaim() {
            var id    = UUID.randomUUID();
            var claim = draftClaim();
            claim.submit("REF-001");
            when(claimRepo.findActiveById(TENANT, id)).thenReturn(Optional.of(claim));
            when(patientRepo.findAllByIds(any(), anySet())).thenReturn(List.of());
            when(practitionerRepo.findAllByIds(any(), anySet())).thenReturn(List.of());

            var result = service.updateClaimStatus(TENANT, id, "accept", null);

            assertThat(result.status()).isEqualTo("ACCEPTED");
        }

        @Test
        @DisplayName("reject records rejection reason")
        void rejectsClaim() {
            var id    = UUID.randomUUID();
            var claim = draftClaim();
            claim.submit("REF-001");
            when(claimRepo.findActiveById(TENANT, id)).thenReturn(Optional.of(claim));
            when(patientRepo.findAllByIds(any(), anySet())).thenReturn(List.of());
            when(practitionerRepo.findAllByIds(any(), anySet())).thenReturn(List.of());

            var result = service.updateClaimStatus(TENANT, id, "reject",
                    "Invalid member number");

            assertThat(result.status()).isEqualTo("REJECTED");
            assertThat(result.rejectionReason()).isEqualTo("Invalid member number");
        }

        @Test
        @DisplayName("paid moves claim to PAID")
        void marksPaid() {
            var id    = UUID.randomUUID();
            var claim = draftClaim();
            claim.submit("REF-001");
            claim.markAccepted();
            when(claimRepo.findActiveById(TENANT, id)).thenReturn(Optional.of(claim));
            when(patientRepo.findAllByIds(any(), anySet())).thenReturn(List.of());
            when(practitionerRepo.findAllByIds(any(), anySet())).thenReturn(List.of());

            var result = service.updateClaimStatus(TENANT, id, "paid", null);

            assertThat(result.status()).isEqualTo("PAID");
        }

        @Test
        @DisplayName("throws on unknown status action")
        void throwsOnUnknownAction() {
            var id = UUID.randomUUID();
            when(claimRepo.findActiveById(TENANT, id))
                    .thenReturn(Optional.of(draftClaim()));

            assertThatThrownBy(() -> service.updateClaimStatus(TENANT, id, "void", null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("submit throws when claim already submitted")
        void cannotSubmitTwice() {
            var id    = UUID.randomUUID();
            var claim = draftClaim();
            claim.submit("REF-001"); // already submitted
            when(claimRepo.findActiveById(TENANT, id)).thenReturn(Optional.of(claim));

            assertThatThrownBy(() -> service.submitClaim(TENANT, id, "REF-002"))
                    .isInstanceOf(IllegalStateException.class);
        }
    }
}
