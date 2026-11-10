package za.co.handyflow.platform.warehousing.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class WhseTariffBookTest {

    private final UUID client = UUID.randomUUID();
    private final UUID otherClient = UUID.randomUUID();
    private final UUID item = UUID.randomUUID();
    private final LocalDate d1 = LocalDate.of(2026, 11, 1);
    private final LocalDate d15 = LocalDate.of(2026, 11, 15);
    private final Instant t0 = Instant.parse("2026-10-01T00:00:00Z");

    private WhseTariffBook.TariffVersion tariff(UUID c, UUID i, String rate, LocalDate from, Instant created) {
        return new WhseTariffBook.TariffVersion(UUID.randomUUID(), c, i, new BigDecimal(rate), from, created);
    }

    private WhseTariffBook book(WhseTariffBook.TariffVersion... t) { return new WhseTariffBook(List.of(t), List.of()); }

    @Test
    @DisplayName("a new tariff version only applies from its start date; earlier days keep the old rate")
    void versionsAreEffectiveDated() {
        WhseTariffBook b = book(tariff(client, null, "3", d1, t0), tariff(client, null, "4", d15, t0));
        assertEquals(0, new BigDecimal("3").compareTo(b.resolve(client, item, LocalDate.of(2026, 11, 14), null, null, null).rate()));
        assertEquals(0, new BigDecimal("4").compareTo(b.resolve(client, item, d15, null, null, null).rate()));
        assertNull(b.resolve(client, item, LocalDate.of(2026, 10, 31), null, null, null), "nothing before the first version");
    }

    @Test
    @DisplayName("for the same start date, the most recently created version wins")
    void sameDayCorrection() {
        WhseTariffBook b = book(tariff(client, null, "3", d1, t0), tariff(client, null, "3.5", d1, t0.plusSeconds(60)));
        assertEquals(0, new BigDecimal("3.5").compareTo(b.resolve(client, item, d1, null, null, null).rate()));
    }

    @Test
    @DisplayName("order: item tariff, item field, client tariff, client field, default tariff, profile field")
    void precedence() {
        UUID d = null;
        WhseTariffBook all = book(tariff(client, item, "1", d1, t0), tariff(client, null, "2", d1, t0), tariff(d, d, "3", d1, t0));
        assertEquals("ITEM_TARIFF", all.resolve(client, item, d1, new BigDecimal("9"), new BigDecimal("9"), new BigDecimal("9")).source());

        WhseTariffBook clientAndDefault = book(tariff(client, null, "2", d1, t0), tariff(d, d, "3", d1, t0));
        assertEquals("ITEM", clientAndDefault.resolve(client, item, d1, new BigDecimal("9"), null, null).source());
        assertEquals("CLIENT_TARIFF", clientAndDefault.resolve(client, item, d1, null, new BigDecimal("9"), null).source());
        assertEquals("DEFAULT_TARIFF", clientAndDefault.resolve(otherClient, item, d1, null, null, new BigDecimal("9")).source());
        assertEquals("PROFILE", book().resolve(client, item, d1, null, null, new BigDecimal("9")).source());
        assertEquals("CLIENT", book().resolve(client, item, d1, null, new BigDecimal("9"), new BigDecimal("8")).source());
        assertNull(book().resolve(client, item, d1, null, null, null));
    }

    @Test
    @DisplayName("one client's tariff never prices another client")
    void clientsAreSeparate() {
        WhseTariffBook b = book(tariff(client, null, "2", d1, t0));
        assertNull(b.resolve(otherClient, item, d1, null, null, null));
    }

    @Test
    @DisplayName("the tariff version that priced a day is reported")
    void reportsVersionId() {
        WhseTariffBook.TariffVersion v = tariff(client, null, "2", d1, t0);
        assertEquals(v.id(), book(v).resolve(client, item, d1, null, null, null).tariffId());
    }

    @Test
    @DisplayName("minimum charge: the client's own beats the warehouse default, and starts on its date")
    void minimumCharge() {
        UUID none = null;
        WhseTariffBook b = new WhseTariffBook(List.of(), List.of(
                new WhseTariffBook.TermsVersion(UUID.randomUUID(), none, new BigDecimal("300"), d1, t0),
                new WhseTariffBook.TermsVersion(UUID.randomUUID(), client, new BigDecimal("500"), d15, t0)));
        assertEquals(0, new BigDecimal("300").compareTo(b.minimumStorageCharge(client, d1)));
        assertEquals(0, new BigDecimal("500").compareTo(b.minimumStorageCharge(client, d15)));
        assertEquals(0, new BigDecimal("300").compareTo(b.minimumStorageCharge(otherClient, d15)));
        assertEquals(0, BigDecimal.ZERO.compareTo(b.minimumStorageCharge(client, LocalDate.of(2026, 10, 1))));
    }

    @Test
    @DisplayName("a minimum is per 30 days and pro-rated to the days an invoice covers")
    void minimumIsProrated() {
        assertEquals(0, new BigDecimal("500.00").compareTo(WhseTariffBook.proratedMinimum(new BigDecimal("500"), 30)));
        assertEquals(0, new BigDecimal("250.00").compareTo(WhseTariffBook.proratedMinimum(new BigDecimal("500"), 15)));
        assertEquals(0, new BigDecimal("516.67").compareTo(WhseTariffBook.proratedMinimum(new BigDecimal("500"), 31)));
        assertEquals(0, BigDecimal.ZERO.compareTo(WhseTariffBook.proratedMinimum(null, 30)));
        assertEquals(0, BigDecimal.ZERO.compareTo(WhseTariffBook.proratedMinimum(new BigDecimal("500"), 0)));
    }

    @Test
    @DisplayName("terms are a full snapshot: the client's own version supplies all three values, else the warehouse default's")
    void termsSnapshot() {
        UUID none = null;
        WhseTariffBook b = new WhseTariffBook(List.of(), List.of(
                new WhseTariffBook.TermsVersion(UUID.randomUUID(), none, new BigDecimal("300"), 14, new BigDecimal("20"), d1, t0),
                new WhseTariffBook.TermsVersion(UUID.randomUUID(), client, new BigDecimal("500"), 0, BigDecimal.ZERO, d15, t0)));
        WhseTariffBook.Terms def = b.termsFor(otherClient, d15);
        assertEquals(14, def.freeDays());
        assertEquals(0, new BigDecimal("20").compareTo(def.locationFeePerMonth()));
        WhseTariffBook.Terms own = b.termsFor(client, d15);
        assertEquals(0, own.freeDays(), "the client's own version replaces the default's free days");
        assertEquals(0, BigDecimal.ZERO.compareTo(own.locationFeePerMonth()));
        assertEquals(WhseTariffBook.Terms.NONE, b.termsFor(client, LocalDate.of(2026, 10, 1)));
    }

    @Test
    @DisplayName("free quantity is the smaller of what is held and what arrived inside the window; nothing when either is zero")
    void freeQuantity() {
        assertEquals(0, new BigDecimal("40").compareTo(WhseTariffBook.freeQuantity(new BigDecimal("100"), new BigDecimal("40"))));
        assertEquals(0, new BigDecimal("30").compareTo(WhseTariffBook.freeQuantity(new BigDecimal("30"), new BigDecimal("40"))));
        assertEquals(0, BigDecimal.ZERO.compareTo(WhseTariffBook.freeQuantity(new BigDecimal("30"), null)));
        assertEquals(0, BigDecimal.ZERO.compareTo(WhseTariffBook.freeQuantity(BigDecimal.ZERO, new BigDecimal("5"))));
    }
}
