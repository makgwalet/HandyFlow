package za.co.handyflow.platform.crm.application.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.crm.application.internal.SegmentRules.Condition;
import za.co.handyflow.platform.crm.application.internal.SegmentRules.Subject;
import za.co.handyflow.platform.crm.domain.model.CustomFieldType;
import za.co.handyflow.platform.crm.domain.model.CustomerStatus;
import za.co.handyflow.platform.crm.domain.model.CustomerType;
import za.co.handyflow.platform.crm.domain.model.LeadSource;

class SegmentCustomFieldRulesTest {
    private static final Instant NOW = Instant.parse("2026-10-21T08:00:00Z");
    private static final Map<String, CustomFieldType> TYPES = Map.of("size", CustomFieldType.NUMBER, "renew", CustomFieldType.DATE, "tier", CustomFieldType.CHOICE,
            "svc", CustomFieldType.MULTI_CHOICE, "vip", CustomFieldType.YES_NO, "note", CustomFieldType.TEXT, "mail", CustomFieldType.EMAIL, "site", CustomFieldType.URL);
    private static final Map<String, List<String>> OPTIONS = Map.of("tier", List.of("Gold", "Silver"), "svc", List.of("Tyres", "Fuel", "Wash"));
    private static final Map<String, String> VALUES = Map.of("size", "25", "renew", "2026-11-15", "tier", "Gold", "svc", "Tyres|Wash", "vip", "yes",
            "note", "Prefers morning calls", "mail", "a@b.co", "site", "https://acme.example");

    private static Subject subject(Map<String, String> custom) {
        return new Subject(CustomerType.CUSTOMER, CustomerStatus.ACTIVE, LeadSource.REFERRAL, null, null, Set.of(), new BigDecimal("1"), NOW, NOW, true, false, custom);
    }
    private static boolean in(Map<String, String> custom, String field, String op, String value) {
        return SegmentRules.matches(List.of(new Condition("CF:" + field, op, value)), subject(custom), NOW, TYPES);
    }
    private static String problem(String field, String op, String value) {
        return SegmentRules.problem(List.of(new Condition("CF:" + field, op, value)), TYPES, OPTIONS);
    }

    @Test
    void oldSubjectsHaveNoCustomValues() {
        var old = new Subject(CustomerType.LEAD, CustomerStatus.ACTIVE, null, null, null, Set.of(), null, NOW, NOW, false, false);
        assertThat(old.custom()).isEmpty();
        assertThat(SegmentRules.problem(List.of(new Condition("CF:size", "GREATER_THAN", "1")))).isNotNull();
    }

    @Test
    void checksConditionsOnCustomFields() {
        assertThat(problem("size", "GREATER_THAN", "10")).isNull();
        assertThat(problem("size", "GREATER_THAN", "abc")).isEqualTo("The value must be a number.");
        assertThat(problem("gone", "IS", "a")).isEqualTo("That custom field no longer exists.");
        assertThat(problem("tier", "GREATER_THAN", "1")).isEqualTo("That comparison does not fit this field.");
        assertThat(problem("size", null, "1")).isEqualTo("That comparison does not fit this field.");
        assertThat(problem("note", "CONTAINS", " ")).isEqualTo("Give a value to compare with.");
        assertThat(problem("note", "IS_SET", "")).isNull();
        assertThat(problem("note", "CONTAINS", "x".repeat(501))).isEqualTo("The value can be at most 500 characters.");
        assertThat(problem("renew", "BEFORE", "soon")).isEqualTo("The value must be a date like 2026-11-30.");
        assertThat(problem("renew", "BEFORE", "2026-12-01")).isNull();
        assertThat(problem("tier", "IS", "Bronze")).isEqualTo("Choose one of the field's options.");
        assertThat(problem("svc", "HAS", "Fuel")).isNull();
        assertThat(problem("vip", "IS", "maybe")).isEqualTo("Choose yes or no.");
    }

    @Test
    void cleaningDropsTheValueOfFilledInTests() {
        var cleaned = SegmentRules.clean(List.of(new Condition("CF:vip", "IS_SET", " x "), new Condition("CF:note", "CONTAINS", " ab ")));
        assertThat(cleaned.get(0).value()).isEmpty();
        assertThat(cleaned.get(1).value()).isEqualTo("ab");
    }

    @Test
    void matchesEachKindOfField() {
        assertThat(in(VALUES, "size", "GREATER_THAN", "10")).isTrue();
        assertThat(in(VALUES, "size", "GREATER_THAN", "25")).isFalse();
        assertThat(in(VALUES, "size", "EQUALS", "25")).isTrue();
        assertThat(in(VALUES, "size", "LESS_THAN", "25")).isFalse();
        assertThat(in(VALUES, "renew", "BEFORE", "2026-12-01")).isTrue();
        assertThat(in(VALUES, "renew", "BEFORE", "2026-11-15")).isFalse();
        assertThat(in(VALUES, "renew", "AFTER", "2026-11-01")).isTrue();
        assertThat(in(VALUES, "tier", "IS", "Gold")).isTrue();
        assertThat(in(VALUES, "tier", "IS_NOT", "Gold")).isFalse();
        assertThat(in(VALUES, "svc", "HAS", "Wash")).isTrue();
        assertThat(in(VALUES, "svc", "HAS", "Fuel")).isFalse();
        assertThat(in(VALUES, "svc", "NOT_HAS", "Fuel")).isTrue();
        assertThat(in(VALUES, "vip", "IS", "yes")).isTrue();
        assertThat(in(VALUES, "note", "CONTAINS", "MORNING")).isTrue();
        assertThat(in(VALUES, "note", "IS", "prefers")).isFalse();
        assertThat(in(VALUES, "mail", "IS", "A@B.CO")).isTrue();
        assertThat(in(VALUES, "site", "CONTAINS", "acme")).isTrue();
    }

    @Test
    void emptyFieldsAreNotAnything() {
        assertThat(in(VALUES, "size", "IS_SET", "")).isTrue();
        assertThat(in(Map.of(), "size", "IS_SET", "")).isFalse();
        assertThat(in(Map.of(), "size", "IS_NOT_SET", "")).isTrue();
        assertThat(in(Map.of(), "size", "GREATER_THAN", "1")).isFalse();
        assertThat(in(Map.of(), "tier", "IS", "Gold")).isFalse();
        assertThat(in(Map.of(), "tier", "IS_NOT", "Gold")).isTrue();
        assertThat(in(Map.of(), "svc", "NOT_HAS", "Fuel")).isTrue();
        assertThat(in(Map.of(), "svc", "HAS", "Fuel")).isFalse();
    }

    @Test
    void oddDataMatchesNobody() {
        assertThat(in(Map.of("size", "abc"), "size", "GREATER_THAN", "1")).isFalse();
        assertThat(in(VALUES, "gone", "IS_NOT_SET", "")).isFalse();
        var unknownTypes = SegmentRules.matches(List.of(new Condition("CF:size", "GREATER_THAN", "1")), subject(VALUES), NOW);
        assertThat(unknownTypes).isFalse();
    }

    @Test
    void mixesBuiltInAndCustomConditions() {
        var both = List.of(new Condition("TYPE", "IS", "CUSTOMER"), new Condition("CF:vip", "IS", "yes"));
        assertThat(SegmentRules.matches(both, subject(VALUES), NOW, TYPES)).isTrue();
        var map = new HashMap<>(VALUES);
        map.put("vip", "no");
        assertThat(SegmentRules.matches(both, subject(map), NOW, TYPES)).isFalse();
        assertThat(SegmentRules.problem(both, TYPES, OPTIONS)).isNull();
    }
}
