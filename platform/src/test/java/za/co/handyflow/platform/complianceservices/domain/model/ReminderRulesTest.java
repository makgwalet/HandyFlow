package za.co.handyflow.platform.complianceservices.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReminderRulesTest {

    private static ReminderRules.Line line(String severity, String title) { return new ReminderRules.Line(severity, "Acme", title, "Due tomorrow"); }

    @Test @DisplayName("only urgent items, or urgent and soon, or everything, according to the chosen level")
    void levels() {
        List<ReminderRules.Line> queue = List.of(line("HIGH", "a"), line("MEDIUM", "b"), line("LOW", "c"));
        assertThat(ReminderRules.select(queue, "HIGH", 25).total()).isEqualTo(1);
        assertThat(ReminderRules.select(queue, "MEDIUM", 25).total()).isEqualTo(2);
        assertThat(ReminderRules.select(queue, "LOW", 25).total()).isEqualTo(3);
    }

    @Test @DisplayName("a long queue is cut to the digest's length and says how many more there are")
    void cut() {
        List<ReminderRules.Line> queue = new ArrayList<>();
        for (int i = 0; i < 12; i++) queue.add(line("HIGH", "item " + i));
        ReminderRules.Selection s = ReminderRules.select(queue, "HIGH", 5);
        assertThat(s.lines().size()).isEqualTo(5);
        assertThat(s.total()).isEqualTo(12);
        assertThat(s.more()).isEqualTo(7);
        assertThat(ReminderRules.body(LocalDate.of(2026, 11, 25), s).contains("…and 7 more in the work queue.")).isTrue();
    }

    @Test @DisplayName("the order the queue gave is kept")
    void order() {
        ReminderRules.Selection s = ReminderRules.select(List.of(line("HIGH", "first"), line("HIGH", "second")), "HIGH", 25);
        assertThat(s.lines().get(0).title()).isEqualTo("first");
    }

    @Test @DisplayName("the subject counts items and says how many are urgent")
    void subject() {
        assertThat(ReminderRules.subject(ReminderRules.select(List.of(line("HIGH", "a")), "HIGH", 25))).isEqualTo("Compliance Services: 1 item needs attention (1 urgent)");
        assertThat(ReminderRules.subject(ReminderRules.select(List.of(line("MEDIUM", "a"), line("LOW", "b")), "LOW", 25))).isEqualTo("Compliance Services: 2 items need attention");
    }

    @Test @DisplayName("each line says how urgent it is, for which client, and why")
    void body() {
        String b = ReminderRules.body(LocalDate.of(2026, 11, 25), ReminderRules.select(List.of(line("HIGH", "VAT return")), "HIGH", 25));
        assertThat(b.contains("Work queue for 2026-11-25")).isTrue();
        assertThat(b.contains("- [Urgent] Acme · VAT return — Due tomorrow")).isTrue();
        String noClient = ReminderRules.body(LocalDate.of(2026, 11, 25), ReminderRules.select(List.of(new ReminderRules.Line("LOW", null, "Catalogue review", null)), "LOW", 25));
        assertThat(noClient.contains("- [When you can] Catalogue review")).isTrue();
    }

    @Test @DisplayName("the digest goes out on working days only")
    void sendDays() {
        assertThat(ReminderRules.isSendDay(LocalDate.of(2026, 11, 25))).isTrue();
        assertThat(ReminderRules.isSendDay(LocalDate.of(2026, 11, 28))).isFalse();
        assertThat(ReminderRules.isSendDay(LocalDate.of(2026, 11, 29))).isFalse();
    }

    @Test @DisplayName("levels and lengths are checked")
    void validation() {
        assertThat(ReminderRules.severity(" medium ")).isEqualTo("MEDIUM");
        assertThatThrownBy(() -> ReminderRules.severity("all")).isInstanceOf(IllegalArgumentException.class);
        assertThat(ReminderRules.maxItems(25)).isEqualTo(25);
        assertThatThrownBy(() -> ReminderRules.maxItems(4)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ReminderRules.maxItems(101)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test @DisplayName("a hand-over is never described as delivered or read")
    void wording() {
        assertThat(ReminderRules.outcomeText("HANDED_OVER").contains("delivered")).isFalse();
        assertThat(ReminderRules.outcomeText("HANDED_OVER").contains("read")).isFalse();
        assertThat(ReminderRules.outcomeText("FAILED")).isEqualTo("Could not be handed over");
    }

    @Test @DisplayName("switching the digest on needs someone to send it to, and no more than 25 people")
    void settings() {
        ComplianceReminderSettings s = ComplianceReminderSettings.blank(TenantId.generate());
        assertThat(s.isEnabled()).isFalse();
        assertThatThrownBy(() -> s.apply(true, "HIGH", 25, 0, UUID.randomUUID())).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> s.apply(true, "HIGH", 25, 26, UUID.randomUUID())).isInstanceOf(IllegalArgumentException.class);
        s.apply(true, "medium", 10, 2, UUID.randomUUID());
        assertThat(s.isEnabled()).isTrue();
        assertThat(s.getMinSeverity()).isEqualTo("MEDIUM");
        s.apply(false, "HIGH", 25, 0, UUID.randomUUID());
        assertThat(s.isEnabled()).isFalse();
    }

    @Test @DisplayName("a log entry has a known outcome and a short detail")
    void log() {
        TenantId t = TenantId.generate();
        assertThatThrownBy(() -> ComplianceReminderLog.of(t, UUID.randomUUID(), LocalDate.now(), 1, "DELIVERED", null)).isInstanceOf(IllegalArgumentException.class);
        ComplianceReminderLog l = ComplianceReminderLog.of(t, UUID.randomUUID(), LocalDate.now(), -3, "FAILED", "x".repeat(900));
        assertThat(l.getDetail().length()).isEqualTo(500);
        assertThat(l.getItemCount()).isEqualTo(0);
    }
}
