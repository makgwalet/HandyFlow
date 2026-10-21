package za.co.handyflow.platform.crm.application.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.crm.application.internal.MergeRules.Snap;
import za.co.handyflow.platform.crm.domain.model.CustomerType;
import za.co.handyflow.platform.crm.domain.model.LeadSource;

class MergeRulesTest {
    private static UUID u(int i) { return new UUID(0, i); }
    private static Snap snap(String name, String email, String phone, String tax, String notes, Map<String, String> address, UUID owner, LeadSource src, String deal, LocalDate close, Map<String, String> custom) {
        return new Snap(name, email, phone, tax, notes, address, owner, src, deal == null ? null : new BigDecimal(deal), close, custom);
    }
    private static final Map<String, String> A1 = Map.of("street", "1 Main", "city", "Pretoria"), A2 = Map.of("street", "9 Side", "city", "Pretoria");
    private static final Snap KEEP = snap("Acme Ltd", "a@acme.co", "082 123 4567", null, "Keep note", A1, u(10), LeadSource.WEBSITE, "1000", LocalDate.of(2026, 11, 1), Map.of("tier", "Gold", "size", "5"));
    private static final Snap OTHER = snap("ACME Limited", "A@ACME.CO", "0821234567", "TAX1", "Other note", A2, u(11), LeadSource.REFERRAL, "2000", LocalDate.of(2026, 12, 1), Map.of("tier", "Silver", "region", "North", "size", "5"));
    private static final Snap THIN = snap("Acme Ltd", null, null, null, null, null, null, null, null, null, Map.of());

    @Test
    void refusesTheWrongPairs() {
        assertThatThrownBy(() -> MergeRules.check(null, u(2), CustomerType.LEAD, CustomerType.LEAD)).isInstanceOf(IllegalArgumentException.class).hasMessage("Choose two different customers");
        assertThatThrownBy(() -> MergeRules.check(u(1), u(1), CustomerType.LEAD, CustomerType.LEAD)).hasMessage("Choose two different customers");
        assertThatThrownBy(() -> MergeRules.check(u(1), u(2), CustomerType.LEAD, CustomerType.CUSTOMER)).hasMessage("Keep the customer and merge the lead into it, not the other way round");
        MergeRules.check(u(1), u(2), CustomerType.CUSTOMER, CustomerType.LEAD);
        MergeRules.check(u(1), u(2), CustomerType.LEAD, CustomerType.LEAD);
        MergeRules.check(u(1), u(2), CustomerType.CUSTOMER, CustomerType.CUSTOMER);
    }

    @Test
    void refusesWhenTheRemovedRecordIsUsedElsewhere() {
        MergeRules.checkLinks("Acme", Map.of());
        MergeRules.checkLinks("Acme", Map.of("invoices", 0L));
        assertThatThrownBy(() -> MergeRules.checkLinks("Acme", Map.of("invoices", 3L, "bookings", 1L, "quotes", 0L))).isInstanceOf(IllegalStateException.class)
                .hasMessage("'Acme' has bookings (1), invoices (3) linked to it. Keep that record and merge the other one into it instead");
        assertThat(MergeRules.describeLinks(Map.of("fuel_orders", 2L))).isEqualTo("fuel orders (2)");
    }

    @Test
    void findsTheFieldsThatDiffer() {
        var conflicts = MergeRules.conflicts(KEEP, OTHER, Map.of("tier", "Tier"));
        assertThat(conflicts.stream().map(MergeRules.Conflict::field)).containsExactly("name", "address", "owner", "source", "dealValue", "closeDate", "custom.tier");
        assertThat(conflicts.get(0).keep()).isEqualTo("Acme Ltd");
        assertThat(conflicts.get(0).other()).isEqualTo("ACME Limited");
        assertThat(conflicts.get(1).keep()).isEqualTo("1 Main, Pretoria");
        assertThat(conflicts.get(5).label()).isEqualTo("Expected close date");
        assertThat(conflicts.get(6).label()).isEqualTo("Tier");
        assertThat(MergeRules.conflicts(KEEP, KEEP, Map.of())).isEmpty();
    }

    @Test
    void sameEmailAndPhoneInOtherFormatsAreNotConflicts() {
        var a = snap("A", "x@y.co", "082 123 4567", null, null, null, null, null, null, null, Map.of());
        var b = snap("A", "X@Y.CO", "0821234567", null, null, null, null, null, null, null, Map.of());
        assertThat(MergeRules.conflicts(a, b, Map.of())).isEmpty();
    }

    @Test
    void fillsBlanksAndKeepsTheKeptValues() {
        var m = MergeRules.merge(THIN, OTHER, Set.of(), "ACME Limited");
        assertThat(m.name()).isEqualTo("Acme Ltd");
        assertThat(m.email()).isEqualTo("A@ACME.CO");
        assertThat(m.taxNumber()).isEqualTo("TAX1");
        assertThat(m.ownerId()).isEqualTo(u(11));
        assertThat(m.source()).isEqualTo(LeadSource.REFERRAL);
        assertThat(m.address()).isEqualTo(A2);
        assertThat(m.notes()).isEqualTo("Other note");
        var k = MergeRules.merge(KEEP, OTHER, Set.of(), "ACME Limited");
        assertThat(k.ownerId()).isEqualTo(u(10));
        assertThat(k.source()).isEqualTo(LeadSource.WEBSITE);
        assertThat(k.taxNumber()).isEqualTo("TAX1");
        assertThat(k.custom()).containsEntry("tier", "Gold").containsEntry("region", "North");
        assertThat(k.notes()).isEqualTo("Keep note\n\n— Notes from merged record ACME Limited —\nOther note");
    }

    @Test
    void takesTheOthersValueWhenChosen() {
        var m = MergeRules.merge(KEEP, OTHER, Set.of("owner", "dealValue", "custom.tier", "address"), "ACME Limited");
        assertThat(m.ownerId()).isEqualTo(u(11));
        assertThat(m.dealValue()).isEqualByComparingTo("2000");
        assertThat(m.custom()).containsEntry("tier", "Silver");
        assertThat(m.address()).isEqualTo(A2);
        assertThat(m.source()).isEqualTo(LeadSource.WEBSITE);
    }

    @Test
    void neverLosesNotes() {
        assertThat(MergeRules.mergeNotes("Same", "same", "X")).isEqualTo("Same");
        assertThat(MergeRules.mergeNotes("A", null, "X")).isEqualTo("A");
        assertThat(MergeRules.mergeNotes(null, "B", "X")).isEqualTo("B");
        assertThat(MergeRules.mergeNotes(" ", "B", "X")).isEqualTo("B");
    }

    @Test
    void listsWhatWasFilled() {
        var m = MergeRules.merge(THIN, OTHER, Set.of(), "ACME Limited");
        assertThat(MergeRules.filled(THIN, m)).contains("email", "phone", "taxNumber", "address", "owner", "source", "dealValue", "closeDate", "notes").doesNotContain("name");
        assertThat(MergeRules.filled(KEEP, KEEP)).isEmpty();
        assertThat(MergeRules.filled(KEEP, MergeRules.merge(KEEP, OTHER, Set.of(), "ACME Limited"))).containsExactly("taxNumber", "custom.region", "notes");
    }

    @Test
    void checksWhichFieldsMayBeTaken() {
        assertThat(MergeRules.checkTake(null, KEEP, OTHER)).isEmpty();
        assertThat(MergeRules.checkTake(List.of("owner", "custom.tier"), KEEP, OTHER)).hasSize(2);
        assertThatThrownBy(() -> MergeRules.checkTake(List.of("bogus"), KEEP, OTHER)).hasMessage("Unknown field 'bogus'");
        assertThatThrownBy(() -> MergeRules.checkTake(List.of("taxNumber"), KEEP, THIN)).hasMessage("The other record has nothing to take for Tax number");
        assertThatThrownBy(() -> MergeRules.checkTake(List.of("custom.zzz"), KEEP, OTHER)).hasMessage("The other record has nothing to take for zzz");
    }

    @Test
    void joinsTags() {
        assertThat(MergeRules.mergeTags(Set.of("a"), Set.of("b"))).containsExactlyInAnyOrder("a", "b");
        assertThat(MergeRules.mergeTags(null, Set.of("b"))).containsExactly("b");
    }

    @Test
    void movesBranchesUnderTheKeptRecord() {
        UUID k = u(1), o = u(2), p = u(3), c1 = u(4), c2 = u(5), g = u(6);
        Map<UUID, UUID> l = new HashMap<>();
        l.put(c1, o); l.put(c2, o); l.put(o, p);
        assertThat(MergeRules.linksAfter(l, k, o)).isEqualTo(Map.of(c1, k, c2, k));
        Map<UUID, UUID> childOfOther = new HashMap<>();
        childOfOther.put(k, o); childOfOther.put(c1, o);
        assertThat(MergeRules.linksAfter(childOfOther, k, o)).isEqualTo(Map.of(c1, k));
        Map<UUID, UUID> inherits = new HashMap<>();
        inherits.put(k, o); inherits.put(o, p); inherits.put(c1, o);
        assertThat(MergeRules.linksAfter(inherits, k, o)).isEqualTo(Map.of(k, p, c1, k));
        Map<UUID, UUID> deep = new HashMap<>();
        deep.put(g, k); deep.put(k, o); deep.put(o, p);
        assertThat(MergeRules.linksAfter(deep, k, o)).isEqualTo(Map.of(g, k, k, p));
    }

    @Test
    void refusesGroupsThatWouldLoopOrGetTooDeep() {
        UUID k = u(1), o = u(2), x = u(7);
        Map<UUID, UUID> l = new HashMap<>();
        l.put(x, o); l.put(k, x);   // keep is a grandchild of the removed record
        var after = MergeRules.linksAfter(l, k, o);
        assertThat(after).isEqualTo(Map.of(x, k, k, x));
        assertThatThrownBy(() -> MergeRules.checkGroups(after)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Merging these would put a company group in a loop. Move one of them out of the group first");
        Map<UUID, UUID> chain = new HashMap<>();
        chain.put(u(21), u(20)); chain.put(u(22), u(21)); chain.put(u(23), u(22)); chain.put(u(24), u(23));
        MergeRules.checkGroups(chain);
        chain.put(u(25), u(24));
        assertThatThrownBy(() -> MergeRules.checkGroups(chain)).hasMessage("Merging these would make a company group more than 5 levels deep");
        MergeRules.checkGroups(Map.of());
    }
}
