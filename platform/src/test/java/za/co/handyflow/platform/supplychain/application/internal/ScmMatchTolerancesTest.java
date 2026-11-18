package za.co.handyflow.platform.supplychain.application.internal;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.supplychain.application.internal.ScmMatchTolerances.Tolerances;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ScmMatchTolerancesTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final ScmMatchTolerances service = new ScmMatchTolerances(jdbc, bd("2.0"), bd("0.0"), bd("2.0"), bd("0.10"), true, false);
    private final UUID tenant = UUID.randomUUID();

    private static BigDecimal bd(String s) { return new BigDecimal(s); }
    private static Tolerances t(String price, String qty, String amount, String rounding) {
        return new Tolerances(bd(price), bd(qty), bd(amount), bd(rounding), true, false);
    }

    @SuppressWarnings("unchecked")
    private void stored(List<Tolerances> rows) {
        when(jdbc.query(anyString(), any(org.springframework.jdbc.core.RowMapper.class), eq(tenant))).thenReturn((List) rows);
    }

    @Test
    void aCompanyWithNoRowGetsTheDefaults() {
        stored(List.of());
        var v = service.view(tenant);
        assertThat(v.customised()).isFalse();
        assertThat(v.effective()).isEqualTo(v.defaults());
        assertThat(service.forTenant(tenant).pricePct()).isEqualByComparingTo("2.0");
    }

    @Test
    void aCompanysOwnRowWinsOverTheDefaults() {
        stored(List.of(t("5", "1", "3", "0.50")));
        assertThat(service.forTenant(tenant).pricePct()).isEqualByComparingTo("5");
        assertThat(service.view(tenant).customised()).isTrue();
    }

    @Test
    void validation() {
        assertThat(ScmMatchTolerances.problem(t("2", "0", "2", "0.10"))).isNull();
        assertThat(ScmMatchTolerances.problem(t("-1", "0", "2", "0.10"))).contains("between 0 and 25");
        assertThat(ScmMatchTolerances.problem(t("2", "26", "2", "0.10"))).contains("between 0 and 25");
        assertThat(ScmMatchTolerances.problem(t("2", "0", "2", "101"))).contains("rounding");
        assertThat(ScmMatchTolerances.problem(new Tolerances(null, bd("0"), bd("2"), bd("0.1"), true, false))).contains("required");
        assertThat(ScmMatchTolerances.problem(null)).contains("required");
    }

    @Test
    void savingWritesTheRowAndAnAuditEvent_butRefusesBadValuesBeforeTouchingTheDatabase() {
        stored(List.of());
        assertThatThrownBy(() -> service.save(tenant, t("50", "0", "2", "0.1"), UUID.randomUUID(), "Ann"))
                .isInstanceOf(HandyFlowException.class);
        verify(jdbc, never()).update(anyString(), any(Object[].class));

        service.save(tenant, t("3", "0", "3", "0.20"), UUID.randomUUID(), "Ann");
        verify(jdbc, times(2)).update(anyString(), any(Object[].class));   // the row, then the audit event
    }
}
