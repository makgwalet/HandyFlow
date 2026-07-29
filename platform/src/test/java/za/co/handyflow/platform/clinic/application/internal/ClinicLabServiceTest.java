//package za.co.handyflow.platform.clinic.application.internal;
//
//import org.junit.jupiter.api.*;
//import org.mockito.Mockito;
//import org.junit.jupiter.api.extension.ExtendWith;
//import org.mockito.*;
//import org.mockito.junit.jupiter.MockitoExtension;
//import org.springframework.data.domain.*;
//import za.co.handyflow.platform.clinic.domain.model.*;
//import za.co.handyflow.platform.clinic.domain.repository.*;
//import za.co.handyflow.platform.clinic.dto.lab.*;
//import za.co.handyflow.platform.shared.ResourceNotFoundException;
//import za.co.handyflow.platform.shared.TenantId;
//
//import java.time.Instant;
//import java.util.*;
//
//import static org.assertj.core.api.Assertions.*;
//import static org.mockito.ArgumentMatchers.*;
//import static org.mockito.Mockito.*;
//
//@ExtendWith(MockitoExtension.class)
//class ClinicLabServiceTest {
//
//    @Mock ClinicLabResultRepository labRepo;
//    @Mock ClinicPatientRepository   patientRepo;
//
//    @InjectMocks ClinicLabService service;
//
//    // TenantId has a private constructor — create via Mockito mock
//    // and stub getValue() which is what SpEL :#{#tenantId.value} and service internals call.
//    static final UUID TENANT_UUID = UUID.fromString("9ecb3dc7-75d4-4e56-b0a2-c95d3c7c584f");
//    static final TenantId TENANT;
//    static {
//        TENANT = Mockito.mock(TenantId.class);
//        Mockito.when(TENANT.getValue()).thenReturn(TENANT_UUID);
//    }
//
//    ClinicLabResult labResult() {
//        return ClinicLabResult.create(TENANT, "AMPATH",
//                null, "Nkosi_HbA1c.pdf", "Nkosi S", "AMP-2026-001");
//    }
//
//    @Nested
//    @DisplayName("uploadResult")
//    class UploadResult {
//
//        @Test
//        @DisplayName("saves lab result and returns response")
//        void savesLabResult() {
//            var req = new UploadLabResultRequest(
//                    "AMPATH", null, "result.pdf",
//                    "Nkosi Sipho", "AMP-2026-001", null);
//
//            var result = service.uploadResult(TENANT, req);
//
//            verify(labRepo).save(any(ClinicLabResult.class));
//            assertThat(result.source()).isEqualTo("AMPATH");
//            assertThat(result.status()).isEqualTo("UNREVIEWED");
//            assertThat(result.pdfFilename()).isEqualTo("result.pdf");
//        }
//
//        @Test
//        @DisplayName("auto-matches patient by last name from raw name")
//        void autoMatchesPatient() {
//            var patientId = UUID.randomUUID();
//            var patient   = ClinicPatient.create(TENANT,
//                    "Sipho", "Nkosi", null, null, "MALE",
//                    null, null, null, null);
//            var matchPage = new PageImpl<>(List.of(patient));
//
//            when(patientRepo.searchActive(eq(TENANT), eq("Nkosi"), any()))
//                    .thenReturn(matchPage);
//
//            var req = new UploadLabResultRequest(
//                    "AMPATH", null, "result.pdf", "Nkosi Sipho", "AMP-001", null);
//
//            service.uploadResult(TENANT, req);
//
//            // Verify save called with matched patient
//            verify(labRepo).save(argThat(r -> r.getPatientId() != null));
//        }
//
//        @Test
//        @DisplayName("saves collectedAt when provided")
//        void savesCollectedAt() {
//            var collected = Instant.now().minusSeconds(86400);
//            var req = new UploadLabResultRequest(
//                    "LANCET", null, "blood.pdf",
//                    "Moosa Fatima", "LAN-001", collected);
//
//            service.uploadResult(TENANT, req);
//
//            verify(labRepo).save(argThat(r -> r.getCollectedAt() != null));
//        }
//    }
//
//    @Nested
//    @DisplayName("setInterpretation")
//    class SetInterpretation {
//
//        @Test
//        @DisplayName("saves AI interpretation text")
//        void savesInterpretation() {
//            var id     = UUID.randomUUID();
//            var result = labResult();
//            when(labRepo.findByIdAndTenant(TENANT, id)).thenReturn(Optional.of(result));
//
//            var response = service.setInterpretation(TENANT, id,
//                    "HbA1c of 8.4% indicates suboptimal glycaemic control.");
//
//            verify(labRepo).save(argThat(r ->
//                    r.getInterpretation().contains("HbA1c")));
//            assertThat(response.interpretation()).contains("HbA1c");
//        }
//
//        @Test
//        @DisplayName("throws when lab result not found")
//        void throwsWhenNotFound() {
//            var id = UUID.randomUUID();
//            when(labRepo.findByIdAndTenant(TENANT, id)).thenReturn(Optional.empty());
//
//            assertThatThrownBy(() -> service.setInterpretation(TENANT, id, "text"))
//                    .isInstanceOf(ResourceNotFoundException.class);
//        }
//    }
//
//    @Nested
//    @DisplayName("markReviewed")
//    class MarkReviewed {
//
//        @Test
//        @DisplayName("sets status to REVIEWED with timestamp")
//        void setsReviewedStatus() {
//            var id     = UUID.randomUUID();
//            var result = labResult();
//            when(labRepo.findByIdAndTenant(TENANT, id)).thenReturn(Optional.of(result));
//
//            var response = service.markReviewed(TENANT, id, null);
//
//            verify(labRepo).save(argThat(r ->
//                    "REVIEWED".equals(r.getStatus()) && r.getReviewedAt() != null));
//            assertThat(response.status()).isEqualTo("REVIEWED");
//        }
//
//        @Test
//        @DisplayName("accepts null reviewedBy without error")
//        void acceptsNullReviewedBy() {
//            var id = UUID.randomUUID();
//            when(labRepo.findByIdAndTenant(TENANT, id)).thenReturn(Optional.of(labResult()));
//
//            assertThatCode(() -> service.markReviewed(TENANT, id, null))
//                    .doesNotThrowAnyException();
//        }
//    }
//
//    @Nested
//    @DisplayName("fileResult")
//    class FileResult {
//
//        @Test
//        @DisplayName("links result to consultation and sets FILED status")
//        void filesResult() {
//            var id            = UUID.randomUUID();
//            var consultId     = UUID.randomUUID();
//            var result        = labResult();
//            when(labRepo.findByIdAndTenant(TENANT, id)).thenReturn(Optional.of(result));
//
//            var response = service.fileResult(TENANT, id, consultId);
//
//            verify(labRepo).save(argThat(r ->
//                    "FILED".equals(r.getStatus()) &&
//                            consultId.equals(r.getConsultationId())));
//            assertThat(response.status()).isEqualTo("FILED");
//        }
//    }
//
//    @Nested
//    @DisplayName("matchPatient")
//    class MatchPatient {
//
//        @Test
//        @DisplayName("links result to patient")
//        void linksToPatient() {
//            var id        = UUID.randomUUID();
//            var patientId = UUID.randomUUID();
//            var result    = labResult();
//            when(labRepo.findByIdAndTenant(TENANT, id)).thenReturn(Optional.of(result));
//
//            var response = service.matchPatient(TENANT, id, patientId);
//
//            verify(labRepo).save(argThat(r -> patientId.equals(r.getPatientId())));
//            assertThat(response.patientId()).isEqualTo(patientId);
//        }
//    }
//}
