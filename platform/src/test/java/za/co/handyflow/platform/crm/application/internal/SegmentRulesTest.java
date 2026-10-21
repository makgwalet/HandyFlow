package za.co.handyflow.platform.crm.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.crm.application.internal.SegmentRules.Condition;
import za.co.handyflow.platform.crm.application.internal.SegmentRules.Subject;
import za.co.handyflow.platform.crm.domain.model.CustomerStatus;
import za.co.handyflow.platform.crm.domain.model.CustomerType;
import za.co.handyflow.platform.crm.domain.model.LeadSource;
import za.co.handyflow.platform.crm.domain.model.LeadStage;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SegmentRulesTest {

    private static final Instant NOW = Instant.parse("2026-10-21T08:00:00Z");
    private static final UUID OWNER = UUID.randomUUID();

    private static Subject subject() {
        return new Subject(CustomerType.CUSTOMER, CustomerStatus.ACTIVE, LeadSource.REFERRAL, null, OWNER, Set.of("VIP", "Fleet"),
                new BigDecimal("150000"), NOW.minus(Duration.ofDays(10)), NOW.minus(Duration.ofDays(100)), true, false);
    }

    private static Condition c(String field, String op, String value) { return new Condition(field, op, value); }

    private static boolean in(Condition... cs) { return SegmentRules.matches(List.of(cs), subject(), NOW); }

    @Test
    void everyConditionMustMatch() {
        assertThat(in(c("TYPE", "IS", "CUSTOMER"), c("STATUS", "IS", "ACTIVE"))).isTrue();
        assertThat(in(c("TYPE", "IS", "CUSTOMER"), c("STATUS", "IS", "BLOCKED"))).isFalse();
    }

    @Test
    void isAndIsNotTreatMissingValuesSensibly() {
        assertThat(in(c("STAGE", "IS", "NEW"))).isFalse();
        assertThat(in(c("STAGE", "IS_NOT", "NEW"))).isTrue();
        assertThat(in(c("SOURCE", "IS", "REFERRAL"))).isTrue();
        assertThat(in(c("SOURCE", "IS_NOT", "REFERRAL"))).isFalse();
    }

    @Test
    void ownerCanBeAPersonOrNobody() {
        assertThat(in(c("OWNER", "IS", OWNER.toString()))).isTrue();
        assertThat(in(c("OWNER", "IS", "UNASSIGNED"))).isFalse();
        assertThat(in(c("OWNER", "IS_NOT", "UNASSIGNED"))).isTrue();
    }

    @Test
    void tagsIgnoreCase() {
        assertThat(in(c("TAG", "HAS", "vip"))).isTrue();
        assertThat(in(c("TAG", "NOT_HAS", "vip"))).isFalse();
        assertThat(in(c("TAG", "NOT_HAS", "other"))).isTrue();
    }

    @Test
    void dealValueNeedsAValue() {
        assertThat(in(c("DEAL_VALUE", "GREATER_THAN", "100000"))).isTrue();
        assertThat(in(c("DEAL_VALUE", "LESS_THAN", "100000"))).isFalse();
        var none = new Subject(CustomerType.LEAD, CustomerStatus.ACTIVE, null, LeadStage.NEW, null, Set.of(), null, NOW, NOW, false, false);
        assertThat(SegmentRules.matches(List.of(c("DEAL_VALUE", "LESS_THAN", "100000")), none, NOW)).isFalse();
    }

    @Test
    void agesAreCountedInDays() {
        assertThat(in(c("CREATED", "WITHIN_DAYS", "30"))).isTrue();
        assertThat(in(c("CREATED", "OLDER_THAN_DAYS", "30"))).isFalse();
        assertThat(in(c("UPDATED", "OLDER_THAN_DAYS", "90"))).isTrue();
        assertThat(in(c("UPDATED", "WITHIN_DAYS", "90"))).isFalse();
    }

    @Test
    void contactDetailsCanBeRequiredOrMissing() {
        assertThat(in(c("EMAIL", "IS_SET", ""))).isTrue();
        assertThat(in(c("PHONE", "IS_SET", ""))).isFalse();
        assertThat(in(c("PHONE", "IS_NOT_SET", ""))).isTrue();
    }

    @Test
    void badConditionsAreRejected() {
        assertThat(SegmentRules.problem(List.of())).isNotNull();
        assertThat(SegmentRules.problem(List.of(c("NOPE", "IS", "x")))).isNotNull();
        assertThat(SegmentRules.problem(List.of(c("TYPE", "HAS", "LEAD")))).isNotNull();
        assertThat(SegmentRules.problem(List.of(c("TYPE", "IS", "SUPPLIER")))).isNotNull();
        assertThat(SegmentRules.problem(List.of(c("OWNER", "IS", "not-a-uuid")))).isNotNull();
        assertThat(SegmentRules.problem(List.of(c("DEAL_VALUE", "GREATER_THAN", "abc")))).isNotNull();
        assertThat(SegmentRules.problem(List.of(c("DEAL_VALUE", "GREATER_THAN", "-5")))).isNotNull();
        assertThat(SegmentRules.problem(List.of(c("CREATED", "WITHIN_DAYS", "0")))).isNotNull();
        assertThat(SegmentRules.problem(List.of(c("CREATED", "WITHIN_DAYS", "4000")))).isNotNull();
        assertThat(SegmentRules.problem(java.util.Collections.nCopies(11, c("EMAIL", "IS_SET", ""))) ).isNotNull();
        assertThat(SegmentRules.problem(List.of(c("TYPE", "IS", "LEAD"), c("EMAIL", "IS_SET", ""), c("PHONE", "IS_NOT_SET", "")))).isNull();
    }

    @Test
    void cleaningTrimsValuesAndDropsValuesWhereNoneBelong() {
        var out = SegmentRules.clean(List.of(c("TAG", "HAS", "  VIP "), c("EMAIL", "IS_SET", "junk")));
        assertThat(out).containsExactly(c("TAG", "HAS", "VIP"), c("EMAIL", "IS_SET", ""));
    }
}
