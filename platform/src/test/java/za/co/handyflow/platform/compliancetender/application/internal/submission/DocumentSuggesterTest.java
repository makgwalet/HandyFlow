package za.co.handyflow.platform.compliancetender.application.internal.submission;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.compliancetender.application.internal.submission.DocumentSuggester.Candidate;
import za.co.handyflow.platform.compliancetender.application.internal.submission.DocumentSuggester.Need;
import za.co.handyflow.platform.compliancetender.application.internal.submission.DocumentSuggester.Outcome;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentSuggesterTest {

    private static final LocalDate CLOSING = LocalDate.of(2026, 12, 21);
    private static final Need TAX = new Need("Tax compliant", "Tax Clearance Certificate");

    private static Candidate doc(UUID id, String type, LocalDate issued, LocalDate expiry, boolean verified) { return new Candidate(id, type, issued, expiry, verified); }

    @Test
    @DisplayName("picks the verified document that is valid on the closing date, matching the type ignoring case and spaces")
    void picksQualifying() {
        UUID good = UUID.randomUUID();
        var out = DocumentSuggester.suggest(List.of(TAX), List.of(doc(good, "  tax clearance certificate ", null, LocalDate.of(2027, 3, 1), true)), CLOSING);
        assertThat(out).hasSize(1);
        assertThat(out.get(0).outcome()).isEqualTo(Outcome.CHOSEN);
        assertThat(out.get(0).documentId()).isEqualTo(good);
        assertThat(out.get(0).requirement()).isEqualTo("Tax compliant");
    }

    @Test
    @DisplayName("a certificate that lapses before the closing date does not qualify even though it is valid today")
    void expiresBeforeClosing() {
        var out = DocumentSuggester.suggest(List.of(TAX), List.of(doc(UUID.randomUUID(), TAX.documentType(), null, LocalDate.of(2026, 10, 20), true)), CLOSING);
        assertThat(out.get(0).outcome()).isEqualTo(Outcome.EXPIRED);
        assertThat(out.get(0).documentId()).isNull();
        assertThat(out.get(0).message()).contains("2026-12-21");
    }

    @Test
    @DisplayName("one that expires on the closing date itself still qualifies")
    void expiresOnClosing() {
        UUID id = UUID.randomUUID();
        assertThat(DocumentSuggester.suggest(List.of(TAX), List.of(doc(id, TAX.documentType(), null, CLOSING, true)), CLOSING).get(0).documentId()).isEqualTo(id);
    }

    @Test
    @DisplayName("an unverified document is reported as not verified, not chosen; a missing type is reported as missing")
    void unverifiedAndMissing() {
        var unverified = DocumentSuggester.suggest(List.of(TAX), List.of(doc(UUID.randomUUID(), TAX.documentType(), null, null, false)), CLOSING);
        assertThat(unverified.get(0).outcome()).isEqualTo(Outcome.NOT_VERIFIED);
        var missing = DocumentSuggester.suggest(List.of(TAX), List.of(doc(UUID.randomUUID(), "Something else", null, null, true)), CLOSING);
        assertThat(missing.get(0).outcome()).isEqualTo(Outcome.MISSING);
    }

    @Test
    @DisplayName("of several qualifying documents the one valid longest wins, no expiry beats any expiry, then the latest issued")
    void bestOfSeveral() {
        UUID short1 = UUID.randomUUID(), long1 = UUID.randomUUID(), none = UUID.randomUUID(), olderNone = UUID.randomUUID();
        var docs = List.of(doc(short1, TAX.documentType(), null, LocalDate.of(2027, 1, 1), true), doc(long1, TAX.documentType(), null, LocalDate.of(2028, 1, 1), true));
        assertThat(DocumentSuggester.suggest(List.of(TAX), docs, CLOSING).get(0).documentId()).isEqualTo(long1);
        var withNone = List.of(doc(long1, TAX.documentType(), null, LocalDate.of(2028, 1, 1), true), doc(olderNone, TAX.documentType(), LocalDate.of(2020, 1, 1), null, true), doc(none, TAX.documentType(), LocalDate.of(2025, 1, 1), null, true));
        assertThat(DocumentSuggester.suggest(List.of(TAX), withNone, CLOSING).get(0).documentId()).isEqualTo(none);
    }

    @Test
    @DisplayName("an unverified document never beats a verified one, and needs without a document type are skipped")
    void unverifiedNeverWins() {
        UUID verified = UUID.randomUUID();
        var docs = List.of(doc(UUID.randomUUID(), TAX.documentType(), null, LocalDate.of(2030, 1, 1), false), doc(verified, TAX.documentType(), null, LocalDate.of(2027, 1, 1), true));
        assertThat(DocumentSuggester.suggest(List.of(TAX, new Need("Attended briefing", null), new Need("x", " ")), docs, CLOSING)).hasSize(1)
                .first().extracting(DocumentSuggester.Suggestion::documentId).isEqualTo(verified);
    }
}
