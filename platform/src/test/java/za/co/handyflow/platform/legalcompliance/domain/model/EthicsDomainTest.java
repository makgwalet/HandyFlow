package za.co.handyflow.platform.legalcompliance.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class EthicsDomainTest {

    private final TenantId tenant = TenantId.generate();
    private final LocalDate today = LocalDate.of(2026, 6, 1);
    private final UUID alice = UUID.randomUUID(), bob = UUID.randomUUID();
    private static final String TXT = "A sufficiently long text for the rule";
    private static final BigDecimal THRESHOLD = new BigDecimal("500.00");

    private CoiDeclaration decl() { return CoiDeclaration.declare(tenant, "COI-1", alice, "Alice", DeclarationType.DIRECTORSHIP, "Acme Ltd", TXT, null, today, 12); }

    @Test @DisplayName("Declaration: validates its fields")
    void declare() {
        assertThrows(IllegalArgumentException.class, () -> CoiDeclaration.declare(tenant, "C", null, "A", DeclarationType.OTHER, "X", TXT, null, today, 12));
        assertThrows(IllegalArgumentException.class, () -> CoiDeclaration.declare(tenant, "C", alice, "A", null, "X", TXT, null, today, 12));
        assertThrows(IllegalArgumentException.class, () -> CoiDeclaration.declare(tenant, "C", alice, "A", DeclarationType.OTHER, " ", TXT, null, today, 12));
        assertThrows(IllegalArgumentException.class, () -> CoiDeclaration.declare(tenant, "C", alice, "A", DeclarationType.OTHER, "X", "short", null, today, 12));
        assertThrows(IllegalArgumentException.class, () -> CoiDeclaration.declare(tenant, "C", alice, "A", DeclarationType.OTHER, "X", TXT, new BigDecimal("-1"), today, 12));
        assertThrows(IllegalArgumentException.class, () -> CoiDeclaration.declare(tenant, "C", alice, "A", DeclarationType.OTHER, "X", TXT, null, today, 37));
        assertEquals(DeclarationStatus.DECLARED, decl().getStatus());
    }

    @Test @DisplayName("Declaration: can be amended only before review starts")
    void amend() {
        CoiDeclaration d = decl();
        d.amend(DeclarationType.FINANCIAL_INTEREST, "Other Ltd", TXT, null, 6);
        assertEquals("Other Ltd", d.getCounterparty());
        d.startReview(bob, "Bob");
        assertThrows(IllegalStateException.class, () -> d.amend(DeclarationType.OTHER, "X", TXT, null, 6));
    }

    @Test @DisplayName("Declaration: nobody reviews or decides their own")
    void sod() {
        CoiDeclaration d = decl();
        assertThrows(IllegalStateException.class, () -> d.startReview(alice, "Alice"));
        assertThrows(IllegalStateException.class, () -> d.decide(DeclarationStatus.CLEARED, TXT, null, alice, "Alice", today));
        d.decide(DeclarationStatus.CLEARED, TXT, null, bob, "Bob", today);
        assertEquals(DeclarationStatus.CLEARED, d.getStatus());
    }

    @Test @DisplayName("Declaration: decision needs reasoning, mitigation needs a description, and the outcome must be a decision")
    void decide() {
        CoiDeclaration d = decl();
        assertThrows(IllegalArgumentException.class, () -> d.decide(DeclarationStatus.CLEARED, "no", null, bob, "Bob", today));
        assertThrows(IllegalArgumentException.class, () -> d.decide(DeclarationStatus.MITIGATED, TXT, "short", bob, "Bob", today));
        assertThrows(IllegalArgumentException.class, () -> d.decide(DeclarationStatus.WITHDRAWN, TXT, null, bob, "Bob", today));
        d.decide(DeclarationStatus.MITIGATED, TXT, "Alice recuses herself from any decision involving Acme", bob, "Bob", today);
        assertEquals(today.plusMonths(12), d.getNextReviewDue());
        assertNotNull(d.getMitigation());
        assertThrows(IllegalStateException.class, () -> d.decide(DeclarationStatus.CLEARED, TXT, null, bob, "Bob", today), "already decided");
    }

    @Test @DisplayName("Declaration: not permitted has no next review; confirm and withdraw belong to the declarant")
    void lifecycle() {
        CoiDeclaration np = decl();
        np.decide(DeclarationStatus.NOT_PERMITTED, TXT, null, bob, "Bob", today);
        assertNull(np.getNextReviewDue());
        assertThrows(IllegalStateException.class, () -> np.withdraw(TXT, alice));

        CoiDeclaration d = decl();
        assertThrows(IllegalStateException.class, () -> d.confirmUnchanged(alice, today), "not decided yet");
        d.decide(DeclarationStatus.CLEARED, TXT, null, bob, "Bob", today);
        assertThrows(IllegalStateException.class, () -> d.confirmUnchanged(bob, today));
        d.confirmUnchanged(alice, today.plusMonths(11));
        assertEquals(today.plusMonths(23), d.getNextReviewDue());
        assertThrows(IllegalStateException.class, () -> d.withdraw(TXT, bob));
        assertThrows(IllegalArgumentException.class, () -> d.withdraw("no", alice));
        d.withdraw("No longer a director of Acme", alice);
        assertEquals(DeclarationStatus.WITHDRAWN, d.getStatus());
        assertNull(d.getNextReviewDue());
    }

    private GiftEntry gift(String value) { return GiftEntry.record(tenant, "GFT-1", GiftDirection.RECEIVED, alice, "Alice", "Supplier", "Bottle of wine", "Year end", today, new BigDecimal(value), THRESHOLD, today); }

    @Test @DisplayName("Gift: at or below the threshold is simply recorded; above it waits for approval; the threshold is kept on the row")
    void giftStatus() {
        assertEquals(GiftStatus.RECORDED, gift("500.00").getStatus());
        assertEquals(GiftStatus.PENDING, gift("500.01").getStatus());
        assertEquals(THRESHOLD, gift("10").getThresholdZar());
    }

    @Test @DisplayName("Gift: validates its fields")
    void giftValidation() {
        assertThrows(IllegalArgumentException.class, () -> GiftEntry.record(tenant, "G", null, alice, "A", "X", "A long enough description", null, today, BigDecimal.ONE, THRESHOLD, today));
        assertThrows(IllegalArgumentException.class, () -> GiftEntry.record(tenant, "G", GiftDirection.GIVEN, alice, "A", " ", "A long enough description", null, today, BigDecimal.ONE, THRESHOLD, today));
        assertThrows(IllegalArgumentException.class, () -> GiftEntry.record(tenant, "G", GiftDirection.GIVEN, alice, "A", "X", "short", null, today, BigDecimal.ONE, THRESHOLD, today));
        assertThrows(IllegalArgumentException.class, () -> GiftEntry.record(tenant, "G", GiftDirection.GIVEN, alice, "A", "X", "A long enough description", null, today.plusDays(1), BigDecimal.ONE, THRESHOLD, today));
        assertThrows(IllegalArgumentException.class, () -> GiftEntry.record(tenant, "G", GiftDirection.GIVEN, alice, "A", "X", "A long enough description", null, today, new BigDecimal("-1"), THRESHOLD, today));
    }

    @Test @DisplayName("Gift: only a pending entry can be decided, never by the person who recorded it; declining needs a note")
    void giftDecision() {
        GiftEntry g = gift("900");
        assertThrows(IllegalStateException.class, () -> g.approve(alice, "Alice", null, today));
        assertThrows(IllegalArgumentException.class, () -> g.decline(bob, "Bob", "no", today));
        g.decline(bob, "Bob", "Returned to the supplier with a thank-you note", today);
        assertEquals(GiftStatus.DECLINED, g.getStatus());
        assertThrows(IllegalStateException.class, () -> g.approve(bob, "Bob", null, today));
        GiftEntry ok = gift("900");
        ok.approve(bob, "Bob", null, today);
        assertEquals(GiftStatus.APPROVED, ok.getStatus());
        assertThrows(IllegalStateException.class, () -> gift("10").approve(bob, "Bob", null, today), "recorded entries need no approval");
    }
}
