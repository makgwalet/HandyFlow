package za.co.handyflow.platform.clinic.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import za.co.handyflow.platform.clinic.application.internal.ClinicAccessLogService.Target;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class ClinicAccessLogServiceTest {

    static final String ID = "9ecb3dc7-75d4-4e56-b0a2-c95d3c7c584f";

    @Test
    @DisplayName("patient paths resolve to the patient, whatever follows")
    void classifiesPatientPaths() {
        Target t = ClinicAccessLogService.classify("/api/v1/clinic/patients/" + ID + "/observations/latest");
        assertThat(t.resourceType()).isEqualTo("PATIENT");
        assertThat(t.patientId()).isEqualTo(UUID.fromString(ID));
        assertThat(ClinicAccessLogService.classify("/api/v1/clinic/billing/patients/" + ID + "/statement-pdf").patientId())
                .isEqualTo(UUID.fromString(ID));
    }

    @Test
    @DisplayName("consultation and lab result paths are logged against their own id, without a patient")
    void classifiesOtherRecords() {
        Target c = ClinicAccessLogService.classify("/api/v1/clinic/consultations/" + ID + "/summary-pdf");
        assertThat(c.resourceType()).isEqualTo("CONSULTATION");
        assertThat(c.patientId()).isNull();
        Target l = ClinicAccessLogService.classify("/api/v1/clinic/lab/results/" + ID + "/pdf");
        assertThat(l.resourceType()).isEqualTo("LAB_RESULT");
    }

    @Test
    @DisplayName("lists and non-record paths are not logged")
    void ignoresNonRecordPaths() {
        assertThat(ClinicAccessLogService.classify("/api/v1/clinic/patients")).isNull();
        assertThat(ClinicAccessLogService.classify("/api/v1/clinic/consultations/drafts")).isNull();
        assertThat(ClinicAccessLogService.classify("/api/v1/clinic/medications")).isNull();
    }

    @Test
    @DisplayName("a failing log write never propagates: the record is still served")
    void failOpen() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.update(anyString(), any(Object[].class))).thenThrow(new RuntimeException("db down"));
        ClinicAccessLogService svc = new ClinicAccessLogService(jdbc);

        svc.record(UUID.randomUUID(), null, new Target("PATIENT", UUID.randomUUID(), UUID.randomUUID()),
                "GET", "/x", 200, "127.0.0.1", false);

        verify(jdbc).update(anyString(), any(Object[].class));
    }
}
