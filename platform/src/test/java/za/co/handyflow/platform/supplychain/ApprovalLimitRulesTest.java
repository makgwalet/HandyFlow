package za.co.handyflow.platform.supplychain;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.supplychain.application.internal.ApprovalLimitRules;
import za.co.handyflow.platform.supplychain.application.internal.ApprovalLimitRules.Authority;
import za.co.handyflow.platform.supplychain.application.internal.ApprovalLimitRules.Limit;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ApprovalLimitRulesTest {

    private static final String PO = ApprovalLimitRules.PURCHASE_ORDER, INV = ApprovalLimitRules.SUPPLIER_INVOICE, ANY = ApprovalLimitRules.ANY;
    private final UUID me = UUID.randomUUID(), buyers = UUID.randomUUID(), managers = UUID.randomUUID();

    private static BigDecimal bd(String v) { return v == null ? null : new BigDecimal(v); }
    private static Limit user(UUID id, String doc, String max) { return new Limit("USER", id, doc, bd(max)); }
    private static Limit role(UUID id, String doc, String max) { return new Limit("ROLE", id, doc, bd(max)); }

    @Test
    void noLimitAtAllMeansNoAuthority() {
        Authority a = ApprovalLimitRules.resolve(me, Set.of(buyers), List.of(), PO);
        assertThat(a.granted()).isFalse();
        assertThat(ApprovalLimitRules.within(a, bd("1"))).isFalse();
    }

    @Test
    void anAmountEqualToTheLimitIsAllowedAndOneCentOverIsNot() {
        Authority a = ApprovalLimitRules.resolve(me, Set.of(), List.of(user(me, PO, "50000")), PO);
        assertThat(ApprovalLimitRules.within(a, bd("50000.00"))).isTrue();
        assertThat(ApprovalLimitRules.within(a, bd("50000.01"))).isFalse();
    }

    @Test
    void aTypeSpecificLimitBeatsTheAllDocumentsLimitEvenWhenLower() {
        List<Limit> l = List.of(user(me, ANY, "1000000"), user(me, INV, "10000"));
        assertThat(ApprovalLimitRules.resolve(me, Set.of(), l, INV).max()).isEqualByComparingTo("10000");
        assertThat(ApprovalLimitRules.resolve(me, Set.of(), l, PO).max()).isEqualByComparingTo("1000000");
    }

    @Test
    void aPersonalLimitBeatsTheirRolesInBothDirections() {
        List<Limit> lower = List.of(role(managers, ANY, "500000"), user(me, ANY, "20000"));
        assertThat(ApprovalLimitRules.resolve(me, Set.of(managers), lower, PO).max()).isEqualByComparingTo("20000");
        List<Limit> higher = List.of(role(buyers, ANY, "5000"), user(me, ANY, "75000"));
        assertThat(ApprovalLimitRules.resolve(me, Set.of(buyers), higher, PO).max()).isEqualByComparingTo("75000");
    }

    @Test
    void theMostGenerousRoleWinsAndUnlimitedBeatsEveryFigure() {
        List<Limit> l = List.of(role(buyers, ANY, "5000"), role(managers, ANY, "250000"));
        assertThat(ApprovalLimitRules.resolve(me, Set.of(buyers, managers), l, PO).max()).isEqualByComparingTo("250000");
        List<Limit> unlimited = List.of(role(buyers, ANY, "5000"), role(managers, ANY, null));
        Authority a = ApprovalLimitRules.resolve(me, Set.of(buyers, managers), unlimited, PO);
        assertThat(a.unlimited()).isTrue();
        assertThat(ApprovalLimitRules.within(a, bd("999999999"))).isTrue();
    }

    @Test
    void withinOneRoleItsTypeSpecificLimitBeatsItsAllDocumentsLimit() {
        List<Limit> l = List.of(role(buyers, ANY, "100000"), role(buyers, PO, "20000"));
        assertThat(ApprovalLimitRules.resolve(me, Set.of(buyers), l, PO).max()).isEqualByComparingTo("20000");
        assertThat(ApprovalLimitRules.resolve(me, Set.of(buyers), l, INV).max()).isEqualByComparingTo("100000");
    }

    @Test
    void aZeroLimitIsAuthorityToApproveNothingNotMissingAuthority() {
        Authority a = ApprovalLimitRules.resolve(me, Set.of(), List.of(user(me, PO, "0")), PO);
        assertThat(a.granted()).isTrue();
        assertThat(ApprovalLimitRules.within(a, bd("0.01"))).isFalse();
    }

    @Test
    void otherPeoplesAndOtherRolesLimitsAreIgnored() {
        List<Limit> l = List.of(user(UUID.randomUUID(), ANY, null), role(UUID.randomUUID(), ANY, null));
        assertThat(ApprovalLimitRules.resolve(me, Set.of(buyers), l, PO).granted()).isFalse();
    }

    @Test
    void theRefusalSaysWhatWasTooBigAndWhoCouldApproveIt() {
        Authority a = ApprovalLimitRules.resolve(me, Set.of(), List.of(user(me, PO, "50000")), PO);
        String m = ApprovalLimitRules.problem(a, bd("250000"), PO, List.of("Thandi Nkosi", "Pieter Botha"));
        assertThat(m).contains("This purchase order is R 250,000.00").contains("R 50,000.00").contains("Thandi Nkosi, Pieter Botha");
        assertThat(ApprovalLimitRules.problem(a, bd("100"), PO, List.of())).isNull();
        assertThat(ApprovalLimitRules.problem(Authority_none(), bd("1"), INV, List.of())).contains("no approval limit").contains("supplier invoices")
                .contains("administrator");
    }

    private static Authority Authority_none() { return new Authority(false, null, "no limit set"); }

    @Test
    void enforcementIsOnlySafeWhenSomeoneIsUnlimitedForEverything() {
        assertThat(ApprovalLimitRules.hasUnlimitedForEverything(List.of())).isFalse();
        assertThat(ApprovalLimitRules.hasUnlimitedForEverything(List.of(user(me, PO, null)))).isFalse();
        assertThat(ApprovalLimitRules.hasUnlimitedForEverything(List.of(user(me, ANY, "1000000")))).isFalse();
        assertThat(ApprovalLimitRules.hasUnlimitedForEverything(List.of(user(me, ANY, null)))).isTrue();
    }

    @Test
    void documentTypesAreValidated() {
        assertThat(ApprovalLimitRules.validDocumentType("*")).isTrue();
        assertThat(ApprovalLimitRules.validDocumentType("PO_AMENDMENT")).isTrue();
        assertThat(ApprovalLimitRules.validDocumentType("INVOICE")).isFalse();
    }
}
