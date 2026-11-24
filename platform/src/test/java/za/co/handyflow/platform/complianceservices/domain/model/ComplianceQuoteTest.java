package za.co.handyflow.platform.complianceservices.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ComplianceQuoteTest {

    private static final UUID AUTHOR = UUID.randomUUID();
    private static final UUID REVIEWER = UUID.randomUUID();
    private static final LocalDate TODAY = LocalDate.of(2027, 3, 1);

    private static ComplianceQuote draft(String approver) {
        ComplianceQuote q = ComplianceQuote.draft(TenantId.generate(), UUID.randomUUID(), "CQ-000001", 1, "ZAR", AUTHOR);
        q.edit("  Annual compliance  ", TODAY.plusDays(30), "note", "terms", TODAY);
        q.applyPricing(QuoteRules.sum(List.of(new QuoteRules.Priced(new BigDecimal("4500"), new BigDecimal("500"), BigDecimal.ZERO, new BigDecimal("675"), new BigDecimal("5175"), new BigDecimal("15")))),
                approver, new BigDecimal("10"));
        return q;
    }

    private static ComplianceQuote pending() {
        ComplianceQuote q = draft("COMPLIANCE_MANAGER");
        q.submit(AUTHOR, 1, TODAY);
        return q;
    }

    private static ComplianceQuote sent() {
        ComplianceQuote q = pending();
        q.approve(REVIEWER, "ok", null);
        q.markSent(AUTHOR, TODAY);
        return q;
    }

    @Test @DisplayName("a draft keeps the cleaned title and the figures the server gave it")
    void draftKeeps() {
        var q = draft("COMPLIANCE_MANAGER");
        assertThat(q.getTitle()).isEqualTo("Annual compliance");
        assertThat(q.getTotal()).isEqualByComparingTo("5175.00");
        assertThat(q.getRequiredApprover()).isEqualTo("COMPLIANCE_MANAGER");
        assertThat(q.getMaxDiscountPct()).isEqualByComparingTo("10");
        assertThat(q.isDraft()).isTrue();
    }

    @Test @DisplayName("a date that has passed is refused")
    void dates() {
        var q = draft("CONSULTANT");
        assertThatThrownBy(() -> q.edit("t", TODAY.minusDays(1), null, null, TODAY)).hasMessageContaining("passed");
    }

    @Test @DisplayName("a discount within the consultant limit needs no approval and is ready to send")
    void noApprovalNeeded() {
        var q = draft("CONSULTANT");
        q.submit(AUTHOR, 1, TODAY);
        assertThat(q.getStatus()).isEqualTo("APPROVED");
        assertThat(q.getApprovedBy()).isNull();
    }

    @Test @DisplayName("a larger discount waits for approval, and submitting needs a line and a date")
    void needsApproval() {
        assertThat(pending().getStatus()).isEqualTo("PENDING_APPROVAL");
        assertThatThrownBy(() -> draft("CONSULTANT").submit(AUTHOR, 0, TODAY)).hasMessageContaining("at least one service");
        var q = ComplianceQuote.draft(TenantId.generate(), UUID.randomUUID(), "CQ-2", 1, "ZAR", AUTHOR);
        assertThatThrownBy(() -> q.submit(AUTHOR, 1, TODAY)).hasMessageContaining("how long");
    }

    @Test @DisplayName("someone else approves; the author needs a recorded reason")
    void approval() {
        var q = pending();
        assertThatThrownBy(() -> q.approve(AUTHOR, null, null)).hasMessageContaining("someone else");
        assertThatThrownBy(() -> q.approve(AUTHOR, null, "short")).hasMessageContaining("at least 15");
        q.approve(AUTHOR, null, "Only partner in the practice");
        assertThat(q.getApprovalExceptionReason()).isEqualTo("Only partner in the practice");
        var other = pending();
        other.approve(REVIEWER, "fine", null);
        assertThat(other.getStatus()).isEqualTo("APPROVED");
        assertThat(other.getApprovedBy()).isEqualTo(REVIEWER);
        assertThat(other.getApprovalExceptionReason()).isNull();
    }

    @Test @DisplayName("sending it back returns it to draft with the reason, and a draft is editable again")
    void reject() {
        var q = pending();
        assertThatThrownBy(() -> q.reject(REVIEWER, "no")).hasMessageContaining("at least 10");
        q.reject(REVIEWER, "Discount too deep for this client");
        assertThat(q.isDraft()).isTrue();
        assertThat(q.getApprovalNote()).isEqualTo("Discount too deep for this client");
        q.edit("t", TODAY.plusDays(10), null, null, TODAY);
    }

    @Test @DisplayName("only an approved quote can be marked sent, and not after its date")
    void sending() {
        assertThatThrownBy(() -> pending().markSent(AUTHOR, TODAY)).isInstanceOf(IllegalStateException.class);
        var q = pending();
        q.approve(REVIEWER, null, null);
        assertThatThrownBy(() -> q.markSent(AUTHOR, TODAY.plusDays(31))).hasMessageContaining("passed its date");
        q.markSent(AUTHOR, TODAY);
        assertThat(q.getStatus()).isEqualTo("SENT");
        assertThat(q.getSentAt()).isNotNull();
    }

    @Test @DisplayName("acceptance needs a note and cannot come after the date")
    void accepting() {
        var q = sent();
        assertThatThrownBy(() -> q.accept(AUTHOR, "ok", TODAY)).hasMessageContaining("at least 10");
        assertThatThrownBy(() -> q.accept(AUTHOR, "Accepted by email 3 March", TODAY.plusDays(31))).hasMessageContaining("expired");
        q.accept(AUTHOR, "Accepted by email 3 March", TODAY.plusDays(5));
        assertThat(q.getStatus()).isEqualTo("ACCEPTED");
        assertThat(q.getDecidedAt()).isNotNull();
        assertThatThrownBy(() -> q.withdraw("changed our mind about it")).hasMessageContaining("answered");
        assertThatThrownBy(() -> q.edit("t", TODAY, null, null, TODAY)).isInstanceOf(IllegalStateException.class);
    }

    @Test @DisplayName("a declined quote keeps the client's reason")
    void declining() {
        var q = sent();
        q.decline(AUTHOR, "Went with another practice", TODAY);
        assertThat(q.getStatus()).isEqualTo("DECLINED");
        assertThat(q.getDecisionNote()).isEqualTo("Went with another practice");
        assertThatThrownBy(() -> draft("CONSULTANT").decline(AUTHOR, "Not sent so nothing to decline", TODAY)).isInstanceOf(IllegalStateException.class);
    }

    @Test @DisplayName("a quote can be withdrawn with a reason until it is answered")
    void withdraw() {
        var q = sent();
        assertThatThrownBy(() -> q.withdraw("x")).hasMessageContaining("at least 10");
        q.withdraw("Scope changed, new quote coming");
        assertThat(q.getStatus()).isEqualTo("WITHDRAWN");
        assertThat(q.getWithdrawnReason()).isEqualTo("Scope changed, new quote coming");
        assertThatThrownBy(() -> q.withdraw("Scope changed again")).isInstanceOf(IllegalStateException.class);
    }

    @Test @DisplayName("a sent quote shows as expired after its date")
    void expiry() {
        var q = sent();
        assertThat(q.effectiveStatus(TODAY)).isEqualTo("SENT");
        assertThat(q.effectiveStatus(TODAY.plusDays(31))).isEqualTo("EXPIRED");
    }

    @Test @DisplayName("a line and its adjustments are cleaned and checked")
    void lines() {
        var t = TenantId.generate();
        var id = UUID.randomUUID();
        var l = ComplianceQuoteLine.of(t, id, new ComplianceQuoteLine.Input("cs ohs", "OHS audit", 2, "PER_SITE", " Three sites ", new BigDecimal("3"), null, null, false, null), 0);
        assertThat(l.getServiceCode()).isEqualTo("CS-OHS");
        assertThat(l.getDescription()).isEqualTo("Three sites");
        assertThat(l.getDiscountPct()).isEqualByComparingTo("0");
        assertThatThrownBy(() -> ComplianceQuoteLine.of(t, id, new ComplianceQuoteLine.Input("CS-OHS", null, null, "FIXED", null, null, null, null, false, new BigDecimal("101")), 0))
                .hasMessageContaining("Discount");
        var e = ComplianceQuoteAdjustment.extra(t, id, "verification", new BigDecimal("2"), new BigDecimal("150"), 0);
        assertThat(e.getKind()).isEqualTo("EXTRA");
        var x = ComplianceQuoteAdjustment.disbursement(t, id, "CIPC fee", new BigDecimal("100"), false, 1);
        assertThat(x.isVatable()).isFalse();
        assertThatThrownBy(() -> ComplianceQuoteAdjustment.extra(t, id, "verification", BigDecimal.ZERO, new BigDecimal("150"), 0)).hasMessageContaining("quantity");
        assertThatThrownBy(() -> ComplianceQuoteAdjustment.disbursement(t, id, " ", new BigDecimal("1"), false, 0)).hasMessageContaining("Describe");
        assertThatThrownBy(() -> ComplianceQuoteAdjustment.disbursement(t, id, "fee", null, false, 0)).hasMessageContaining("amount");
    }
}
