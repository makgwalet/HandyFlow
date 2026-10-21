package za.co.handyflow.platform.crm.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.crm.application.internal.TargetProgress.Target;
import za.co.handyflow.platform.crm.application.internal.TargetProgress.Win;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TargetProgressTest {

    private static final ZoneId Z = ZoneId.of("Africa/Johannesburg");
    private final UUID u1 = new UUID(0, 1), u2 = new UUID(0, 2), u3 = new UUID(0, 3);

    @Test
    void countsOnlyWinsMarkedInTheMonthAndComparesToTarget() {
        Instant inOct = LocalDate.of(2026, 10, 15).atStartOfDay(Z).toInstant();
        Instant lateOct = LocalDateTime.of(2026, 10, 31, 23, 30).atZone(Z).toInstant();
        Instant nov = LocalDate.of(2026, 11, 1).atStartOfDay(Z).toInstant();
        var rows = TargetProgress.build(YearMonth.of(2026, 10), Z,
                List.of(new Win(u1, new BigDecimal("60000"), inOct), new Win(u1, new BigDecimal("15000"), lateOct), new Win(u1, new BigDecimal("999"), nov),
                        new Win(u2, null, inOct), new Win(null, new BigDecimal("5"), inOct), new Win(u3, BigDecimal.ONE, null)),
                List.of(new Target(u1, new BigDecimal("100000")), new Target(u3, new BigDecimal("50000"))));
        assertThat(rows).hasSize(3);
        assertThat(rows.get(0).ownerId()).isEqualTo(u1);
        assertThat(rows.get(0).achieved()).isEqualByComparingTo("75000");
        assertThat(rows.get(0).wonDeals()).isEqualTo(2);
        assertThat(rows.get(0).percent()).isEqualByComparingTo("75.0");
        assertThat(rows.get(1).target()).isNull();
        assertThat(rows.get(1).percent()).isNull();
        assertThat(rows.get(2).achieved()).isEqualByComparingTo("0");
        assertThat(rows.get(2).percent()).isEqualByComparingTo("0.0");
    }

    @Test
    void emptyInputsGiveNoRows() {
        assertThat(TargetProgress.build(YearMonth.of(2026, 10), Z, List.of(), List.of())).isEmpty();
    }

    @Test
    void periodMustBeYearDashMonth() {
        assertThat(SalesTargetService.parse("2026-11")).isEqualTo(YearMonth.of(2026, 11));
        assertThatThrownBy(() -> SalesTargetService.parse("2026-13")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SalesTargetService.parse("Nov 2026")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SalesTargetService.parse(null)).isInstanceOf(IllegalArgumentException.class);
    }
}
