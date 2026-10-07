package za.co.handyflow.platform.compliancetender.application.internal.submission;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Picks, for each requirement that is proved by a document, the document to put in the package. Pure: no repositories, so it is tested without a database.
 * <p>
 * The rules mirror readiness (ADR-003) so the package never offers a document the readiness check would reject: the type matches ignoring case and
 * surrounding spaces, the document must be verified, and it must still be valid on the tender's closing date (a certificate that lapses the week before
 * closing is no use). When more than one qualifies, the one valid the longest wins, then the most recently issued.
 */
public final class DocumentSuggester {

    private DocumentSuggester() {}

    public record Need(String requirement, String documentType) {}
    public record Candidate(UUID id, String documentType, LocalDate issueDate, LocalDate expiryDate, boolean verified) {}

    /** CHOSEN: a document qualifies. NOT_VERIFIED / EXPIRED: documents of the type exist but none qualifies. MISSING: none of that type at all. */
    public enum Outcome { CHOSEN, NOT_VERIFIED, EXPIRED, MISSING }

    public record Suggestion(String requirement, String documentType, Outcome outcome, UUID documentId, String message) {}

    public static List<Suggestion> suggest(List<Need> needs, List<Candidate> documents, LocalDate asOf) {
        List<Suggestion> out = new ArrayList<>();
        for (Need need : needs) {
            if (need.documentType() == null || need.documentType().isBlank()) continue;
            String wanted = need.documentType().trim();
            List<Candidate> ofType = documents.stream().filter(d -> d.documentType() != null && d.documentType().trim().equalsIgnoreCase(wanted)).toList();
            if (ofType.isEmpty()) {
                out.add(new Suggestion(need.requirement(), wanted, Outcome.MISSING, null, "No " + wanted + " has been uploaded."));
                continue;
            }
            List<Candidate> inDate = ofType.stream().filter(d -> d.expiryDate() == null || !d.expiryDate().isBefore(asOf)).toList();
            if (inDate.isEmpty()) {
                out.add(new Suggestion(need.requirement(), wanted, Outcome.EXPIRED, null,
                        "Every " + wanted + " on file expires before " + asOf + ". Upload a current one."));
                continue;
            }
            List<Candidate> verified = inDate.stream().filter(Candidate::verified).toList();
            if (verified.isEmpty()) {
                out.add(new Suggestion(need.requirement(), wanted, Outcome.NOT_VERIFIED, null,
                        "The " + wanted + " on file has not been verified yet. Verify it in the Documents tab."));
                continue;
            }
            Candidate best = verified.stream().max(Comparator
                    .comparing((Candidate d) -> d.expiryDate() == null ? LocalDate.MAX : d.expiryDate())
                    .thenComparing(d -> d.issueDate() == null ? LocalDate.MIN : d.issueDate())).orElseThrow();
            out.add(new Suggestion(need.requirement(), wanted, Outcome.CHOSEN, best.id(),
                    best.expiryDate() == null ? "Verified, no expiry." : "Verified, valid until " + best.expiryDate() + "."));
        }
        return out;
    }
}
