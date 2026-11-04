package za.co.handyflow.platform.crm.domain.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CustomerSearchTermsTest {

    @Test void oneWordBecomesAPrefix() { assertEquals("ka:*", CustomerSearchTerms.prefixQuery("Ka")); }

    @Test void severalWordsMustAllMatch() { assertEquals("karoo:* & fresh:*", CustomerSearchTerms.prefixQuery("  Karoo   Fresh ")); }

    @Test void symbolsAreDropped() { assertEquals("o:* & brien:*", CustomerSearchTerms.prefixQuery("O'Brien & (")); }

    @Test void nothingSearchableGivesNull() {
        assertNull(CustomerSearchTerms.prefixQuery("   "));
        assertNull(CustomerSearchTerms.prefixQuery("!!!"));
        assertNull(CustomerSearchTerms.prefixQuery(null));
    }

    @Test void accentsAndDigitsAreKept() { assertEquals("café:* & 4120:*", CustomerSearchTerms.prefixQuery("Café 4120")); }

    @Test void tooManyWordsAreCut() {
        String q = CustomerSearchTerms.prefixQuery("a b c d e f g h i j");
        assertEquals(CustomerSearchTerms.MAX_WORDS, q.split(" & ").length);
    }
}
