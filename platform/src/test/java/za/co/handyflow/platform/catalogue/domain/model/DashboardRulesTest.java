package za.co.handyflow.platform.catalogue.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DashboardRulesTest {
    private static final ZoneId ZONE = ZoneId.of("Africa/Johannesburg");
    private static final Instant NOW = Instant.parse("2026-10-21T10:00:00Z");
    private static Instant at(String iso) { return Instant.parse(iso); }

    @Test
    void theWindowStartsOnTheFirstOfTheOldestMonth() {
        assertThat(DashboardRules.windowStart(NOW, ZONE, 12)).isEqualTo(at("2025-10-31T22:00:00Z"));
        assertThat(DashboardRules.windowStart(NOW, ZONE, 1)).isEqualTo(at("2026-09-30T22:00:00Z"));
    }

    @Test
    void growthIsARunningTotalWithEmptyMonthsCarriedForward() {
        var pts = DashboardRules.monthlyGrowth(List.of(at("2026-08-15T08:00:00Z"), at("2026-08-20T08:00:00Z"), at("2026-10-02T08:00:00Z")), 10, NOW, ZONE, 4);
        assertThat(pts.size()).isEqualTo(4);
        assertThat(pts.get(0).month()).isEqualTo("2026-07");
        assertThat(pts.get(0).total()).isEqualTo(10L);
        assertThat(pts.get(1).total()).isEqualTo(12L);
        assertThat(pts.get(2).total()).isEqualTo(12L);
        assertThat(pts.get(3).month()).isEqualTo("2026-10");
        assertThat(pts.get(3).total()).isEqualTo(13L);
    }

    @Test
    void anItemMadeLateOnTheLastDayOfAMonthBelongsToThatMonthInSouthAfricanTime() {
        // 22:30 UTC on 30 Sep is 00:30 on 1 Oct in Johannesburg
        var pts = DashboardRules.monthlyGrowth(List.of(at("2026-09-30T22:30:00Z")), 0, NOW, ZONE, 2);
        assertThat(pts.get(0).total()).isEqualTo(0L);
        assertThat(pts.get(1).total()).isEqualTo(1L);
    }

    @Test
    void subCategoriesCountUnderTheirRootAndBiggestComesFirst() {
        UUID tools = UUID.randomUUID(), power = UUID.randomUUID(), paint = UUID.randomUUID();
        Map<UUID, UUID> parents = new HashMap<>();
        parents.put(tools, null); parents.put(power, tools); parents.put(paint, null);
        Map<UUID, String> names = Map.of(tools, "Tools", power, "Power tools", paint, "Paint");
        var out = DashboardRules.byRootCategory(parents, names, Map.of(tools, 2L, power, 5L, paint, 4L), 3);
        assertThat(out.size()).isEqualTo(3);
        assertThat(out.get(0).name()).isEqualTo("Tools");
        assertThat(out.get(0).count()).isEqualTo(7L);
        assertThat(out.get(1).name()).isEqualTo("Paint");
        assertThat(out.get(2).name()).isEqualTo("No category");
        assertThat(out.get(2).count()).isEqualTo(3L);
    }

    @Test
    void noUncategorisedItemsMeansNoSuchRow() {
        UUID a = UUID.randomUUID();
        Map<UUID, UUID> parents = new HashMap<>();
        parents.put(a, null);
        var out = DashboardRules.byRootCategory(parents, Map.of(a, "A"), Map.of(a, 1L), 0);
        assertThat(out.size()).isEqualTo(1);
    }

    @Test
    void recentMeansWithinTheLastNDaysIncludingToday() {
        LocalDate today = LocalDate.of(2026, 10, 21);
        assertThat(DashboardRules.withinDays(today, today, 30)).isTrue();
        assertThat(DashboardRules.withinDays(today.minusDays(29), today, 30)).isTrue();
        assertThat(DashboardRules.withinDays(today.minusDays(30), today, 30)).isFalse();
        assertThat(DashboardRules.withinDays(today.plusDays(1), today, 30)).isFalse();
    }
}
