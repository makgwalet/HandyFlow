package za.co.handyflow.platform.complianceservices.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class PortalRulesTest {

    @Test
    @DisplayName("A client sees only quotes that were sent to them, and what they answered")
    void quotes() {
        assertThat(PortalRules.quoteVisible("SENT")).isTrue();
        assertThat(PortalRules.quoteVisible("ACCEPTED")).isTrue();
        assertThat(PortalRules.quoteVisible("DECLINED")).isTrue();
        assertThat(PortalRules.quoteVisible("DRAFT")).isFalse();
        assertThat(PortalRules.quoteVisible("PENDING_APPROVAL")).isFalse();
        assertThat(PortalRules.quoteVisible("APPROVED")).isFalse();
        assertThat(PortalRules.quoteVisible("WITHDRAWN")).isFalse();
        assertThat(PortalRules.quoteVisible(null)).isFalse();
    }

    @Test
    @DisplayName("A client sees agreements that have started, not drafts or cancelled ones")
    void agreements() {
        assertThat(PortalRules.agreementVisible("ACTIVE")).isTrue();
        assertThat(PortalRules.agreementVisible("SUSPENDED")).isTrue();
        assertThat(PortalRules.agreementVisible("ENDED")).isTrue();
        assertThat(PortalRules.agreementVisible("DRAFT")).isFalse();
        assertThat(PortalRules.agreementVisible("CANCELLED")).isFalse();
    }

    @Test
    @DisplayName("Draft and cancelled invoices stay out; issued, part paid and paid ones show")
    void invoices() {
        assertThat(PortalRules.invoiceVisible("DRAFT")).isFalse();
        assertThat(PortalRules.invoiceVisible("CANCELLED")).isFalse();
        assertThat(PortalRules.invoiceVisible(null)).isFalse();
        assertThat(PortalRules.invoiceVisible("ISSUED")).isTrue();
        assertThat(PortalRules.invoiceVisible("PARTIALLY_PAID")).isTrue();
        assertThat(PortalRules.invoiceVisible("PAID")).isTrue();
        assertThat(PortalRules.invoiceVisible("OVERDUE")).isTrue();
    }

    @Test
    @DisplayName("Email checks")
    void email() {
        assertThat(PortalRules.emailProblem("  Jo@Acme.co.za ")).isNull();
        assertThat(PortalRules.normaliseEmail("  Jo@Acme.co.za ")).isEqualTo("jo@acme.co.za");
        assertThat(PortalRules.emailProblem("")).contains("required");
        assertThat(PortalRules.emailProblem(null)).contains("required");
        assertThat(PortalRules.emailProblem("not an email")).contains("does not look");
        assertThat(PortalRules.emailProblem("a@b")).contains("does not look");
    }

    @Test
    @DisplayName("Outstanding is the total less paid and credited, never below zero")
    void outstanding() {
        assertThat(PortalRules.outstanding(new BigDecimal("1725"), new BigDecimal("725"), null)).isEqualByComparingTo("1000");
        assertThat(PortalRules.outstanding(new BigDecimal("1725"), null, new BigDecimal("1725"))).isEqualByComparingTo("0");
        assertThat(PortalRules.outstanding(new BigDecimal("100"), new BigDecimal("150"), null)).isEqualByComparingTo("0");
        assertThat(PortalRules.outstanding(null, null, null)).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("Upcoming includes today")
    void upcoming() {
        LocalDate today = LocalDate.of(2027, 3, 1);
        assertThat(PortalRules.upcoming(today, today)).isTrue();
        assertThat(PortalRules.upcoming(today.minusDays(1), today)).isFalse();
        assertThat(PortalRules.upcoming(null, today)).isFalse();
    }
}
