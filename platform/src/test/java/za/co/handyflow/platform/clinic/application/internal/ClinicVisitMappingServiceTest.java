package za.co.handyflow.platform.clinic.application.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import za.co.handyflow.platform.clinic.dto.VisitMappingDtos.Entry;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClinicVisitMappingServiceTest {

    @Mock JdbcTemplate jdbc;

    ClinicVisitMappingService service;
    TenantId tenant;

    @BeforeEach
    void setUp() {
        service = new ClinicVisitMappingService(jdbc, new ObjectMapper());
        tenant = TenantId.of(UUID.randomUUID());
    }

    @Test
    @DisplayName("saving replaces the practice's list and records the change")
    void setReplacesAndAudits() {
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(1);

        service.set(tenant, "consultation", List.of(new Entry("A", true), new Entry("B", false)));

        var sql = org.mockito.ArgumentCaptor.forClass(String.class);
        var args = org.mockito.ArgumentCaptor.forClass(Object[].class);
        verify(jdbc, times(4)).update(sql.capture(), args.capture());   // delete, two inserts, audit
        assertThat(sql.getAllValues().get(0)).startsWith("DELETE FROM clinic_visit_type_group");
        assertThat(sql.getAllValues().get(1)).contains("INSERT INTO clinic_visit_type_group");
        assertThat(sql.getAllValues().get(3)).contains("clinic_visit_mapping_audit");
        assertThat(args.getAllValues().get(1)[1]).isEqualTo("CONSULTATION");
        assertThat(String.valueOf(args.getAllValues().get(3)[3])).contains("\"groupCode\":\"A\"");
    }

    @Test
    @DisplayName("a group code that does not exist changes nothing and records nothing")
    void unknownGroupIsRefusedBeforeAnyWrite() {
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(0);

        assertThatThrownBy(() -> service.set(tenant, "CONSULTATION", List.of(new Entry("NOPE", false))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("NOPE");

        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test
    @DisplayName("clearing goes back to the default and is recorded")
    void clearAudits() {
        service.clear(tenant, "FOLLOW_UP");

        var sql = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(jdbc, times(2)).update(sql.capture(), any(Object[].class));
        assertThat(sql.getAllValues().get(0)).startsWith("DELETE FROM clinic_visit_type_group");
        assertThat(sql.getAllValues().get(1)).contains("clinic_visit_mapping_audit");
    }
}
