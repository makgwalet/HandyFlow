package za.co.handyflow.platform.contracting.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.contracting.application.internal.AuthorityRules.Context;
import za.co.handyflow.platform.contracting.domain.model.AuthorityAction;
import za.co.handyflow.platform.contracting.domain.model.ContractAuthorityGrant;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static za.co.handyflow.platform.contracting.domain.model.AuthorityAction.*;

class AuthorityRulesTest {
    static final LocalDate TODAY = LocalDate.of(2026, 11, 19);
    UUID tenant = UUID.randomUUID(), alice = UUID.randomUUID(), bob = UUID.randomUUID();

    private ContractAuthorityGrant grant(UUID user, String role, Set<String> types, Set<AuthorityAction> actions, boolean unlimited, String max, LocalDate from, LocalDate to) {
        return ContractAuthorityGrant.create(tenant, user, role, null, types, actions, unlimited, max == null ? null : new BigDecimal(max), from, to, null, null);
    }

    private ContractAuthorityGrant plain(UUID user, Set<AuthorityAction> actions, String max) {
        return grant(user, null, Set.of(), actions, false, max, TODAY.minusDays(10), null);
    }

    private static Context ctx(String type, String amount) { return new Context(type, amount == null ? null : new BigDecimal(amount), "ZAR"); }

    // ── evaluate ──

    @Test
    void noGrantMeansNoAuthority() {
        var d = AuthorityRules.evaluate(List.of(), alice, Set.of(), APPROVE, ctx("NDA", "10"), TODAY);
        assertFalse(d.allowed());
        assertTrue(d.reason().contains("no delegated contract authority"));
    }

    @Test
    void aGrantCoversTheActionTheTypeAndTheAmountUpToItsLimit() {
        var g = grant(alice, null, Set.of("SUPPLY"), Set.of(APPROVE), false, "100000", TODAY, null);
        assertTrue(AuthorityRules.evaluate(List.of(g), alice, Set.of(), APPROVE, ctx("SUPPLY", "100000"), TODAY).allowed(), "the limit itself is allowed");
        assertFalse(AuthorityRules.evaluate(List.of(g), alice, Set.of(), APPROVE, ctx("SUPPLY", "100000.01"), TODAY).allowed());
        assertFalse(AuthorityRules.evaluate(List.of(g), alice, Set.of(), APPROVE, ctx("NDA", "10"), TODAY).allowed());
        assertFalse(AuthorityRules.evaluate(List.of(g), alice, Set.of(), SIGN, ctx("SUPPLY", "10"), TODAY).allowed());
    }

    @Test
    void approvingAndSigningAreSeparate() {
        var g = plain(alice, Set.of(APPROVE), "500000");
        var d = AuthorityRules.evaluate(List.of(g), alice, Set.of(), SIGN, ctx("NDA", "1"), TODAY);
        assertFalse(d.allowed());
        assertTrue(d.reason().contains("does not include sign"));
    }

    @Test
    void aMoreSpecificGrantDoesNotLetAnotherGrantExceedItsOwnLimits() {
        var small = grant(alice, null, Set.of("NDA"), Set.of(APPROVE), false, "1000", TODAY, null);
        var big = grant(alice, null, Set.of("SUPPLY"), Set.of(APPROVE), false, "900000", TODAY, null);
        assertFalse(AuthorityRules.evaluate(List.of(small, big), alice, Set.of(), APPROVE, ctx("NDA", "5000"), TODAY).allowed(), "the big limit belongs to SUPPLY only");
        assertTrue(AuthorityRules.evaluate(List.of(small, big), alice, Set.of(), APPROVE, ctx("SUPPLY", "5000"), TODAY).allowed());
    }

    @Test
    void emptyTypesCoverEveryTypeAndNoLimitCoversAnyAmount() {
        var g = grant(alice, null, Set.of(), Set.of(SIGN), true, null, TODAY, null);
        assertTrue(AuthorityRules.evaluate(List.of(g), alice, Set.of(), SIGN, ctx("ANYTHING", "99999999"), TODAY).allowed());
    }

    @Test
    void aGrantToARoleAppliesToEveryoneWhoHoldsIt() {
        var g = grant(null, "CONTRACTS_MANAGE", Set.of(), Set.of(APPROVE), false, "50000", TODAY, null);
        assertTrue(AuthorityRules.evaluate(List.of(g), alice, Set.of("CONTRACTS_MANAGE"), APPROVE, ctx("NDA", "1"), TODAY).allowed());
        assertFalse(AuthorityRules.evaluate(List.of(g), alice, Set.of("CONTRACTS_READ"), APPROVE, ctx("NDA", "1"), TODAY).allowed());
    }

    @Test
    void aGrantIsNotInForceBeforeItStartsOrAfterItExpires() {
        var future = grant(alice, null, Set.of(), Set.of(APPROVE), true, null, TODAY.plusDays(1), null);
        var expired = grant(alice, null, Set.of(), Set.of(APPROVE), true, null, TODAY.minusDays(30), TODAY.minusDays(1));
        var lastDay = grant(alice, null, Set.of(), Set.of(APPROVE), true, null, TODAY.minusDays(30), TODAY);
        assertFalse(AuthorityRules.evaluate(List.of(future), alice, Set.of(), APPROVE, ctx("NDA", "1"), TODAY).allowed());
        assertTrue(AuthorityRules.evaluate(List.of(expired), alice, Set.of(), APPROVE, ctx("NDA", "1"), TODAY).reason().contains("expired"));
        assertTrue(AuthorityRules.evaluate(List.of(lastDay), alice, Set.of(), APPROVE, ctx("NDA", "1"), TODAY).allowed(), "valid through its end date");
        assertFalse(AuthorityRules.evaluate(List.of(lastDay), alice, Set.of(), APPROVE, ctx("NDA", "1"), TODAY.plusDays(1)).allowed(), "invalid the day after, with no clean-up job");
    }

    @Test
    void aRevokedGrantNoLongerCounts() {
        var g = plain(alice, Set.of(APPROVE), "1000");
        g.revoke(bob, "left the company");
        assertFalse(AuthorityRules.evaluate(List.of(g), alice, Set.of(), APPROVE, ctx("NDA", "1"), TODAY).allowed());
    }

    @Test
    void limitsAreInRandSoAForeignContractNeedsNoLimitAuthority() {
        var limited = plain(alice, Set.of(APPROVE), "1000000");
        var foreign = new Context("NDA", new BigDecimal("10"), "USD");
        var d = AuthorityRules.evaluate(List.of(limited), alice, Set.of(), APPROVE, foreign, TODAY);
        assertFalse(d.allowed());
        assertTrue(d.reason().contains("USD"));
        var open = grant(alice, null, Set.of(), Set.of(APPROVE), true, null, TODAY, null);
        assertTrue(AuthorityRules.evaluate(List.of(open), alice, Set.of(), APPROVE, foreign, TODAY).allowed());
    }

    @Test
    void aContractWithNoValueIsWithinAnyLimit() {
        assertTrue(AuthorityRules.evaluate(List.of(plain(alice, Set.of(APPROVE), "0")), alice, Set.of(), APPROVE, ctx("NDA", null), TODAY).allowed());
    }

    @Test
    void theReasonNamesTheLimitWhenTheAmountIsTooHigh() {
        var d = AuthorityRules.evaluate(List.of(plain(alice, Set.of(APPROVE), "100000")), alice, Set.of(), APPROVE, ctx("NDA", "250000"), TODAY);
        assertTrue(d.reason().contains("R100000"));
    }

    // ── delegation ──

    private Map<UUID, ContractAuthorityGrant> map(ContractAuthorityGrant... g) {
        Map<UUID, ContractAuthorityGrant> m = new HashMap<>();
        for (ContractAuthorityGrant x : g) m.put(x.getId(), x);
        return m;
    }

    private String check(ContractAuthorityGrant src, UUID delegator, Set<AuthorityAction> actions, Set<String> types, boolean unlimited, String max, LocalDate from, LocalDate to) {
        return AuthorityRules.checkDelegation(src, map(src), delegator, Set.of(), bob, actions, types, unlimited, max == null ? null : new BigDecimal(max), from, to, TODAY);
    }

    @Test
    void aHolderCanDelegateLessForAStatedPeriod() {
        var src = grant(alice, null, Set.of("SUPPLY", "NDA"), Set.of(APPROVE, SIGN), false, "500000", TODAY.minusDays(5), null);
        assertNull(check(src, alice, Set.of(APPROVE), Set.of("SUPPLY"), false, "100000", TODAY, TODAY.plusDays(14)));
    }

    @Test
    void aDelegationCannotCarryMoreThanTheSource() {
        var src = grant(alice, null, Set.of("SUPPLY"), Set.of(APPROVE), false, "100000", TODAY.minusDays(5), TODAY.plusDays(30));
        assertTrue(check(src, alice, Set.of(APPROVE, SIGN), Set.of("SUPPLY"), false, "1", TODAY, TODAY.plusDays(1)).contains("not authorised to do"));
        assertTrue(check(src, alice, Set.of(APPROVE), Set.of("SUPPLY"), false, "100000.01", TODAY, TODAY.plusDays(1)).contains("own limit"));
        assertTrue(check(src, alice, Set.of(APPROVE), Set.of("SUPPLY"), true, null, TODAY, TODAY.plusDays(1)).contains("own financial limit"));
        assertTrue(check(src, alice, Set.of(APPROVE), Set.of("NDA"), false, "1", TODAY, TODAY.plusDays(1)).contains("contract type"));
        assertTrue(check(src, alice, Set.of(APPROVE), Set.of(), false, "1", TODAY, TODAY.plusDays(1)).contains("name the types"));
        assertTrue(check(src, alice, Set.of(APPROVE), Set.of("SUPPLY"), false, "1", TODAY, TODAY.plusDays(31)).contains("outlast"));
        assertTrue(check(src, alice, Set.of(APPROVE), Set.of("SUPPLY"), false, "1", TODAY.minusDays(6), TODAY.plusDays(1)).contains("before your own"));
    }

    @Test
    void onlyTheHolderCanDelegateAndOnlyToSomeoneElse() {
        var src = plain(alice, Set.of(APPROVE), "1000");
        assertTrue(check(src, bob, Set.of(APPROVE), Set.of(), false, "1", TODAY, TODAY.plusDays(1)).contains("hold yourself"));
        assertTrue(AuthorityRules.checkDelegation(src, map(src), alice, Set.of(), alice, Set.of(APPROVE), Set.of(), false, BigDecimal.ONE, TODAY, TODAY.plusDays(1), TODAY).contains("yourself"));
    }

    @Test
    void aDelegationNeedsAnEndDateAndCannotBeDelegatedAgain() {
        var src = plain(alice, Set.of(APPROVE), "1000");
        assertTrue(check(src, alice, Set.of(APPROVE), Set.of(), false, "1", TODAY, null).contains("end date"));
        assertTrue(check(src, alice, Set.of(APPROVE), Set.of(), false, "1", TODAY, TODAY.minusDays(1)).contains("end date"));
        var del = ContractAuthorityGrant.delegate(src, bob, Set.of(), Set.of(APPROVE), false, BigDecimal.ONE, TODAY, TODAY.plusDays(5), null, alice);
        assertTrue(AuthorityRules.checkDelegation(del, map(src, del), bob, Set.of(), UUID.randomUUID(), Set.of(APPROVE), Set.of(), false, BigDecimal.ONE, TODAY, TODAY.plusDays(1), TODAY).contains("cannot be delegated again"));
    }

    @Test
    void aDelegationIsOnlyAsGoodAsItsSource() {
        var src = plain(alice, Set.of(APPROVE), "1000");
        var del = ContractAuthorityGrant.delegate(src, bob, Set.of(), Set.of(APPROVE), false, new BigDecimal("500"), TODAY, TODAY.plusDays(5), null, alice);
        var all = List.of(src, del);
        assertTrue(AuthorityRules.evaluate(all, bob, Set.of(), APPROVE, ctx("NDA", "500"), TODAY).allowed());
        assertFalse(AuthorityRules.evaluate(all, bob, Set.of(), APPROVE, ctx("NDA", "501"), TODAY).allowed());
        assertFalse(AuthorityRules.evaluate(all, bob, Set.of(), APPROVE, ctx("NDA", "1"), TODAY.plusDays(6)).allowed(), "expired delegation");
        src.revoke(alice, "no longer holds the role");
        assertFalse(AuthorityRules.evaluate(all, bob, Set.of(), APPROVE, ctx("NDA", "1"), TODAY).allowed(), "revoking the source ends the delegation");
    }
}
