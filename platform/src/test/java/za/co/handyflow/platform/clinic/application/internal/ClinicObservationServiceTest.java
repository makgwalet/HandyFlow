package za.co.handyflow.platform.clinic.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import za.co.handyflow.platform.clinic.domain.model.*;
import za.co.handyflow.platform.clinic.domain.repository.*;
import za.co.handyflow.platform.clinic.dto.ObservationDtos.ObservationRequest;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClinicObservationServiceTest {

    @Mock ClinicObservationRepository  observationRepo;
    @Mock ClinicPatientRepository      patientRepo;
    @Mock ClinicConsultationRepository consultationRepo;
    @Mock ClinicQuestionLibraryService questionLibrary;

    @InjectMocks ClinicObservationService service;

    static final UUID TENANT_UUID = UUID.fromString("9ecb3dc7-75d4-4e56-b0a2-c95d3c7c584f");
    static final TenantId TENANT;
    static {
        TENANT = TenantId.of(TENANT_UUID);
    }

    private ClinicPatient patient() {
        var p = ClinicPatient.create(TENANT, "Jane", "Dlamini", null, null, null,
                "+27820000000", null, null, null);
        when(patientRepo.findActiveById(TENANT, p.getId())).thenReturn(Optional.of(p));
        return p;
    }

    private static ObservationRequest req(String code, String value, String low, String high) {
        return new ObservationRequest(code, new BigDecimal(value),
                low == null ? null : new BigDecimal(low), high == null ? null : new BigDecimal(high),
                null, null, null);
    }

    @Test
    @DisplayName("record uses the canonical unit and flags only against a supplied range")
    void recordFlagsAgainstSuppliedRangeOnly() {
        var p = patient();

        var out = service.record(TENANT, p.getId(), List.of(
                req("pulse", "120", "50", "100"),
                req("WEIGHT", "70", null, null),
                req("glucose", "5.0", "4.0", "7.8")));

        assertThat(out).hasSize(3);
        assertThat(out.get(0).code()).isEqualTo("PULSE");
        assertThat(out.get(0).unit()).isEqualTo("bpm");
        assertThat(out.get(0).abnormalFlag()).isEqualTo("HIGH");
        assertThat(out.get(1).abnormalFlag()).isNull();          // no range, no judgement
        assertThat(out.get(2).abnormalFlag()).isEqualTo("NORMAL");
        verify(observationRepo).saveAll(anyList());
    }

    @Test
    @DisplayName("record rejects unknown codes, missing values, inverted ranges and empty batches")
    void recordValidates() {
        var p = patient();

        assertThatThrownBy(() -> service.record(TENANT, p.getId(), List.of(req("MAGIC", "1", null, null))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.record(TENANT, p.getId(),
                List.of(new ObservationRequest("PULSE", null, null, null, null, null, null))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.record(TENANT, p.getId(), List.of(req("PULSE", "80", "100", "50"))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.record(TENANT, p.getId(), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        verify(observationRepo, never()).saveAll(any());
    }

    @Test
    @DisplayName("record rejects a consultation that belongs to another patient")
    void recordRejectsForeignConsultation() {
        var p = patient();
        var other = ClinicConsultation.create(TENANT, UUID.randomUUID(), null, null, "x");
        when(consultationRepo.findActiveById(TENANT, other.getId())).thenReturn(Optional.of(other));

        var r = new ObservationRequest("PULSE", new BigDecimal("80"), null, null, null, other.getId(), null);

        assertThatThrownBy(() -> service.record(TENANT, p.getId(), List.of(r)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("latest returns the newest final value per code and skips voided rows")
    void latestPerCode() {
        var p = patient();
        var newest = ClinicObservation.of(TENANT, p.getId(), null, ObservationCode.WEIGHT,
                new BigDecimal("72"), null, null, Instant.parse("2026-10-02T08:00:00Z"), null, "MANUAL", null);
        var voided = ClinicObservation.of(TENANT, p.getId(), null, ObservationCode.WEIGHT,
                new BigDecimal("99"), null, null, Instant.parse("2026-10-03T08:00:00Z"), null, "MANUAL", null);
        voided.markEnteredInError();
        var older = ClinicObservation.of(TENANT, p.getId(), null, ObservationCode.WEIGHT,
                new BigDecimal("75"), null, null, Instant.parse("2026-09-01T08:00:00Z"), null, "MANUAL", null);
        var pulse = ClinicObservation.of(TENANT, p.getId(), null, ObservationCode.PULSE,
                new BigDecimal("80"), null, null, Instant.parse("2026-10-01T08:00:00Z"), null, "MANUAL", null);
        when(observationRepo.findByPatient(TENANT, p.getId()))
                .thenReturn(List.of(voided, newest, pulse, older));      // newest first

        var out = service.latest(TENANT, p.getId());

        assertThat(out).hasSize(2);
        assertThat(out.get(0).code()).isEqualTo("WEIGHT");
        assertThat(out.get(0).value()).isEqualByComparingTo("72");
    }

    @Test
    @DisplayName("voiding marks the row entered in error")
    void voidObservation() {
        var p = patient();
        var o = ClinicObservation.of(TENANT, p.getId(), null, ObservationCode.PULSE,
                new BigDecimal("80"), null, null, null, null, "MANUAL", null);
        when(observationRepo.findOne(TENANT, p.getId(), o.getId())).thenReturn(Optional.of(o));

        var r = service.voidObservation(TENANT, p.getId(), o.getId());

        assertThat(r.status()).isEqualTo("ENTERED_IN_ERROR");
    }

    @Test
    @DisplayName("voiding an unknown observation is a not-found")
    void voidMissing() {
        var p = patient();
        var id = UUID.randomUUID();
        when(observationRepo.findOne(TENANT, p.getId(), id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.voidObservation(TENANT, p.getId(), id))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("syncConsultationVitals rebuilds derived rows: BP split, BMI calculated, bad BP ignored")
    @SuppressWarnings("unchecked")
    void syncConsultationVitals() {
        var c = ClinicConsultation.create(TENANT, UUID.randomUUID(), null, null, "Check");
        c.recordVitals(new BigDecimal("80"), new BigDecimal("180"), "132/84", 96,
                new BigDecimal("38.1"), new BigDecimal("96"));

        service.syncConsultationVitals(TENANT, c);

        verify(observationRepo).deleteConsultationDerived(TENANT, c.getId());
        ArgumentCaptor<List<ClinicObservation>> cap = ArgumentCaptor.forClass(List.class);
        verify(observationRepo).saveAll(cap.capture());
        var rows = new ArrayList<>(cap.getValue());
        assertThat(rows).extracting(ClinicObservation::getCode).containsExactlyInAnyOrder(
                "BP_SYSTOLIC", "BP_DIASTOLIC", "PULSE", "TEMPERATURE", "SPO2", "WEIGHT", "HEIGHT", "BMI");
        assertThat(rows).allMatch(o -> "CONSULTATION".equals(o.getSource()));
        var sys = rows.stream().filter(o -> o.getCode().equals("BP_SYSTOLIC")).findFirst().orElseThrow();
        assertThat(sys.getValueNumeric()).isEqualByComparingTo("132");
        var bmi = rows.stream().filter(o -> o.getCode().equals("BMI")).findFirst().orElseThrow();
        assertThat(bmi.getValueNumeric()).isEqualByComparingTo("24.7");   // 80 / 1.8^2
    }

    @Test
    @DisplayName("syncConsultationVitals adds questionnaire measurements; a vitals field wins over the form")
    @SuppressWarnings("unchecked")
    void syncMirrorsQuestionnaireMeasurements() {
        var c = ClinicConsultation.create(TENANT, UUID.randomUUID(), null, null, "Check");
        c.recordVitals(new BigDecimal("80"), null, null, null, null, null);
        when(questionLibrary.measuredAnswers(eq(TENANT), eq(c.getId()), any())).thenReturn(List.of(
                new ClinicQuestionLibraryService.MeasuredAnswer("WEIGHT", new BigDecimal("99")),
                new ClinicQuestionLibraryService.MeasuredAnswer("GLUCOSE", new BigDecimal("6.2")),
                new ClinicQuestionLibraryService.MeasuredAnswer("NOT_A_CODE", new BigDecimal("1"))));

        service.syncConsultationVitals(TENANT, c);

        ArgumentCaptor<List<ClinicObservation>> cap = ArgumentCaptor.forClass(List.class);
        verify(observationRepo).saveAll(cap.capture());
        var rows = cap.getValue();
        assertThat(rows).extracting(ClinicObservation::getCode).containsExactlyInAnyOrder("WEIGHT", "GLUCOSE");
        var w = rows.stream().filter(o -> o.getCode().equals("WEIGHT")).findFirst().orElseThrow();
        assertThat(w.getValueNumeric()).isEqualByComparingTo("80");
    }

    @Test
    @DisplayName("syncConsultationVitals with no vitals only clears derived rows")
    void syncWithNoVitals() {
        var c = ClinicConsultation.create(TENANT, UUID.randomUUID(), null, null, "Check");

        service.syncConsultationVitals(TENANT, c);

        verify(observationRepo).deleteConsultationDerived(TENANT, c.getId());
        verify(observationRepo, never()).saveAll(any());
    }

    @Test
    @DisplayName("an unparseable blood pressure string produces no BP rows")
    @SuppressWarnings("unchecked")
    void badBloodPressureIgnored() {
        var c = ClinicConsultation.create(TENANT, UUID.randomUUID(), null, null, "Check");
        c.recordVitals(null, null, "high", 70, null, null);

        service.syncConsultationVitals(TENANT, c);

        ArgumentCaptor<List<ClinicObservation>> cap = ArgumentCaptor.forClass(List.class);
        verify(observationRepo).saveAll(cap.capture());
        assertThat(cap.getValue()).extracting(ClinicObservation::getCode).containsExactly("PULSE");
    }
}
