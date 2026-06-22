package za.co.handyflow.platform.clinic.domain.model;

import org.junit.jupiter.api.*;
import org.mockito.Mockito;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Pure unit tests for the domain model — no Spring, no Mockito, just POJO logic.
 * Covers state machine transitions and domain invariants.
 */
class ClinicDomainModelTest {

    // TenantId constructor is private — mock it for tests
    static final UUID TENANT_UUID = UUID.fromString("9ecb3dc7-75d4-4e56-b0a2-c95d3c7c584f");
    static final TenantId TENANT;
    static {
        TENANT = Mockito.mock(TenantId.class);
        Mockito.when(TENANT.getValue()).thenReturn(TENANT_UUID);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // ClinicPatient
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("ClinicPatient")
    class ClinicPatientTests {

        @Test
        @DisplayName("create sets default INDIVIDUAL account type")
        void createSetsIndividualAccountType() {
            var patient = ClinicPatient.create(TENANT, "Jane", "Dlamini",
                    null, null, null, null, null, null, null);
            assertThat(patient.getAccountType()).isEqualTo("INDIVIDUAL");
            assertThat(patient.isActive()).isTrue();
            assertThat(patient.getCreatedAt()).isNotNull();
        }

        @Test
        @DisplayName("create generates a unique UUID per call")
        void createGeneratesUniqueId() {
            var p1 = ClinicPatient.create(TENANT,"A","A",null,null,null,null,null,null,null);
            var p2 = ClinicPatient.create(TENANT,"B","B",null,null,null,null,null,null,null);
            assertThat(p1.getId()).isNotEqualTo(p2.getId());
        }

        @Test
        @DisplayName("setActive false deactivates patient")
        void setActiveFalseDeactivates() {
            var patient = ClinicPatient.create(TENANT,"Jane","Dlamini",
                    null,null,null,null,null,null,null);
            patient.setActive(false);
            assertThat(patient.isActive()).isFalse();
        }

        @Test
        @DisplayName("softDelete sets deletedAt and deactivates")
        void softDeleteSetsDeletedAt() {
            var patient = ClinicPatient.create(TENANT,"Jane","Dlamini",
                    null,null,null,null,null,null,null);
            var deletedBy = UUID.randomUUID();
            patient.softDelete(deletedBy);
            assertThat(patient.getDeletedAt()).isNotNull();
            assertThat(patient.isActive()).isFalse();
        }

        @Test
        @DisplayName("principalId and relationship set correctly for dependant")
        void dependantFieldsSetCorrectly() {
            var patient = ClinicPatient.create(TENANT,"Alex","Dlamini",
                    null,null,null,null,null,null,null);
            var principalId = UUID.randomUUID();
            patient.setAccountType("DEPENDANT");
            patient.setPrincipalId(principalId);
            patient.setRelationship("CHILD");

            assertThat(patient.getAccountType()).isEqualTo("DEPENDANT");
            assertThat(patient.getPrincipalId()).isEqualTo(principalId);
            assertThat(patient.getRelationship()).isEqualTo("CHILD");
        }

        @Test
        @DisplayName("archiving sets archivedAt and archiveReason")
        void archiveSetsFields() {
            var patient = ClinicPatient.create(TENANT,"Jane","Dlamini",
                    null,null,null,null,null,null,null);
            patient.setArchivedAt(Instant.now());
            patient.setArchiveReason("Patient relocated");

            assertThat(patient.getArchivedAt()).isNotNull();
            assertThat(patient.getArchiveReason()).isEqualTo("Patient relocated");
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // ClinicAppointment — state machine
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("ClinicAppointment state machine")
    class AppointmentStateMachine {

        ClinicAppointment newAppt() {
            return ClinicAppointment.create(TENANT, UUID.randomUUID(), null,
                    Instant.now().plusSeconds(3600), 30, "CONSULTATION", "Annual check");
        }

        @Test
        @DisplayName("initial status is SCHEDULED")
        void initialStatusIsScheduled() {
            assertThat(newAppt().getStatus()).isEqualTo("SCHEDULED");
        }

        @Test
        @DisplayName("SCHEDULED → confirm → CONFIRMED")
        void scheduledToConfirmed() {
            var appt = newAppt();
            appt.confirm();
            assertThat(appt.getStatus()).isEqualTo("CONFIRMED");
        }

        @Test
        @DisplayName("CONFIRMED → start → IN_PROGRESS")
        void confirmedToInProgress() {
            var appt = newAppt();
            appt.confirm();
            appt.start();
            assertThat(appt.getStatus()).isEqualTo("IN_PROGRESS");
        }

        @Test
        @DisplayName("IN_PROGRESS → complete → COMPLETED")
        void inProgressToCompleted() {
            var appt = newAppt();
            appt.confirm(); appt.start(); appt.complete();
            assertThat(appt.getStatus()).isEqualTo("COMPLETED");
        }

        @Test
        @DisplayName("SCHEDULED → cancel → CANCELLED")
        void scheduledToCancelled() {
            var appt = newAppt();
            appt.cancel();
            assertThat(appt.getStatus()).isEqualTo("CANCELLED");
        }

        @Test
        @DisplayName("CONFIRMED → no_show → NO_SHOW")
        void confirmedToNoShow() {
            var appt = newAppt();
            appt.confirm(); appt.noShow();
            assertThat(appt.getStatus()).isEqualTo("NO_SHOW");
        }

        @Test
        @DisplayName("create sets correct fields")
        void createSetsFields() {
            var patientId = UUID.randomUUID();
            var appt = ClinicAppointment.create(TENANT, patientId, null,
                    Instant.now(), 45, "FOLLOW_UP", "Medication review");

            assertThat(appt.getDurationMinutes()).isEqualTo(45);
            assertThat(appt.getAppointmentType()).isEqualTo("FOLLOW_UP");
            assertThat(appt.getReason()).isEqualTo("Medication review");
            assertThat(appt.getPatientId()).isEqualTo(patientId);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // ClinicConsultation
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("ClinicConsultation")
    class ClinicConsultationTests {

        @Test
        @DisplayName("create sets consultedAt and initial fields")
        void createSetsInitialFields() {
            var c = ClinicConsultation.create(TENANT, UUID.randomUUID(),
                    null, null, "Hypertension check");

            assertThat(c.getChiefComplaint()).isEqualTo("Hypertension check");
            assertThat(c.getConsultedAt()).isNotNull();
            assertThat(c.isBilled()).isFalse();
        }

        @Test
        @DisplayName("recordVitals stores all vital signs")
        void recordVitalsStoresValues() {
            var c = ClinicConsultation.create(TENANT, UUID.randomUUID(),
                    null, null, "Check");
            c.recordVitals(new BigDecimal("82"), new BigDecimal("172"),
                    "148/92", 74, new BigDecimal("36.5"), new BigDecimal("98"));

            assertThat(c.getWeightKg()).isEqualByComparingTo("82");
            assertThat(c.getBloodPressure()).isEqualTo("148/92");
            assertThat(c.getPulseBpm()).isEqualTo(74);
        }

        @Test
        @DisplayName("recordClinical stores SOAP fields")
        void recordClinicalStoresSoap() {
            var c = ClinicConsultation.create(TENANT, UUID.randomUUID(),
                    null, null, "Check");
            c.recordClinical("Known T2DM", "BP elevated", "Hypertension",
                    List.of("I10","E11.9"), "Increase Amlodipine", 90);

            assertThat(c.getHistory()).isEqualTo("Known T2DM");
            assertThat(c.getDiagnosis()).isEqualTo("Hypertension");
            assertThat(c.getIcd10Codes()).containsExactly("I10","E11.9");
            assertThat(c.getFollowUpDays()).isEqualTo(90);
        }

        @Test
        @DisplayName("updateChiefComplaint updates complaint mid-session")
        void updateChiefComplaintUpdates() {
            var c = ClinicConsultation.create(TENANT, UUID.randomUUID(),
                    null, null, "Original");
            c.updateChiefComplaint("Corrected complaint");
            assertThat(c.getChiefComplaint()).isEqualTo("Corrected complaint");
        }

        @Test
        @DisplayName("markBilled sets billed=true with code and amount")
        void markBilledSetsFields() {
            var c = ClinicConsultation.create(TENANT, UUID.randomUUID(),
                    null, null, "Check");
            c.markBilled("0191", new BigDecimal("520.00"));

            assertThat(c.isBilled()).isTrue();
            assertThat(c.getBillingCode()).isEqualTo("0191");
            assertThat(c.getBillingAmount()).isEqualByComparingTo("520.00");
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // ClinicClaim — state machine
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("ClinicClaim state machine")
    class ClinicClaimStateMachine {

        ClinicClaim draft() {
            return ClinicClaim.create(TENANT, UUID.randomUUID(), UUID.randomUUID(),
                    null, "Discovery Health", "DH12345", "00");
        }

        @Test
        @DisplayName("initial status is DRAFT")
        void initialStatusIsDraft() {
            assertThat(draft().getStatus()).isEqualTo("DRAFT");
        }

        @Test
        @DisplayName("DRAFT → submit → SUBMITTED with reference")
        void draftToSubmitted() {
            var claim = draft();
            claim.submit("DH-2026-001");
            assertThat(claim.getStatus()).isEqualTo("SUBMITTED");
            assertThat(claim.getReferenceNumber()).isEqualTo("DH-2026-001");
            assertThat(claim.getSubmittedAt()).isNotNull();
        }

        @Test
        @DisplayName("submit on non-DRAFT claim throws")
        void cannotSubmitNonDraft() {
            var claim = draft();
            claim.submit("REF-001");
            assertThatThrownBy(() -> claim.submit("REF-002"))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("SUBMITTED → markAccepted → ACCEPTED")
        void submittedToAccepted() {
            var claim = draft();
            claim.submit("REF"); claim.markAccepted();
            assertThat(claim.getStatus()).isEqualTo("ACCEPTED");
        }

        @Test
        @DisplayName("SUBMITTED → markRejected → REJECTED with reason")
        void submittedToRejected() {
            var claim = draft();
            claim.submit("REF"); claim.markRejected("Invalid member number");
            assertThat(claim.getStatus()).isEqualTo("REJECTED");
            assertThat(claim.getRejectionReason()).isEqualTo("Invalid member number");
        }

        @Test
        @DisplayName("ACCEPTED → markPaid → PAID")
        void acceptedToPaid() {
            var claim = draft();
            claim.submit("REF"); claim.markAccepted(); claim.markPaid(new BigDecimal("500.00"));
            assertThat(claim.getStatus()).isEqualTo("PAID");
        }

        @Test
        @DisplayName("ACCEPTED → markPartial → PARTIAL")
        void acceptedToPartial() {
            var claim = draft();
            claim.submit("REF"); claim.markAccepted(); claim.markPartial(new BigDecimal("400.00"));
            assertThat(claim.getStatus()).isEqualTo("PARTIAL");
        }

        @Test
        @DisplayName("recalculate sums gross from all lines")
        void recalculateSumsLines() {
            var claim = draft();
            claim.addLine(ClinicClaimLine.of(claim.getId(), "CONSULTATION",
                    "0191", null, "I10", "Consultation",
                    BigDecimal.ONE, new BigDecimal("520"), null, 0));
            claim.addLine(ClinicClaimLine.of(claim.getId(), "PROCEDURE",
                    "0115", null, "I10", "Injection IM",
                    BigDecimal.ONE, new BigDecimal("85"), null, 10));

            assertThat(claim.getGrossAmount()).isEqualByComparingTo("605.00");
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // ClinicLabResult
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("ClinicLabResult")
    class ClinicLabResultTests {

        @Test
        @DisplayName("create sets UNREVIEWED status and receivedAt")
        void createSetsDefaults() {
            var r = ClinicLabResult.create(TENANT, "AMPATH",
                    null, "result.pdf", "Nkosi Sipho", "AMP-001");
            assertThat(r.getStatus()).isEqualTo("UNREVIEWED");
            assertThat(r.getReceivedAt()).isNotNull();
            assertThat(r.isNotified()).isFalse();
        }

        @Test
        @DisplayName("markReviewed sets REVIEWED and reviewedAt")
        void markReviewedSetsStatus() {
            var r = ClinicLabResult.create(TENANT,"LANCET",null,"r.pdf","Moosa F","L-001");
            r.markReviewed(UUID.randomUUID());
            assertThat(r.getStatus()).isEqualTo("REVIEWED");
            assertThat(r.getReviewedAt()).isNotNull();
        }

        @Test
        @DisplayName("file sets FILED and consultationId")
        void fileSetsStatus() {
            var consultId = UUID.randomUUID();
            var r = ClinicLabResult.create(TENANT,"AMPATH",null,"r.pdf","Nkosi S","A-001");
            r.file(consultId);
            assertThat(r.getStatus()).isEqualTo("FILED");
            assertThat(r.getConsultationId()).isEqualTo(consultId);
        }

        @Test
        @DisplayName("setInterpretation stores AI interpretation text")
        void setInterpretationStoresText() {
            var r = ClinicLabResult.create(TENANT,"AMPATH",null,"r.pdf","Nkosi S","A-001");
            r.setInterpretation("HbA1c of 8.4% — suboptimal glycaemic control.");
            assertThat(r.getInterpretation()).contains("HbA1c");
        }

        @Test
        @DisplayName("setCollectedAt stores specimen collection date")
        void setCollectedAtStoresDate() {
            var collected = Instant.now().minusSeconds(86400);
            var r = ClinicLabResult.create(TENANT,"LANCET",null,"r.pdf","M F","L-001");
            r.setCollectedAt(collected);
            assertThat(r.getCollectedAt()).isEqualTo(collected);
        }

        @Test
        @DisplayName("matchPatient links result to patient")
        void matchPatientLinksPatient() {
            var patientId = UUID.randomUUID();
            var r = ClinicLabResult.create(TENANT,"AMPATH",null,"r.pdf","Nkosi S","A-001");
            r.matchPatient(patientId);
            assertThat(r.getPatientId()).isEqualTo(patientId);
        }
    }
}
