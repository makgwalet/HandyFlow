package za.co.handyflow.platform.clinic.application.internal.document;

import za.co.handyflow.platform.clinic.application.internal.document.DocumentModel.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The CLIN-DOC-001A reference pack: the six documents every candidate renderer must produce. SYNTHETIC patients and practices only;
 * nothing here is clinical advice, and the medicines are made-up names so no one mistakes the pack for a prescribing example.
 */
public final class ReferenceDocuments {

    private ReferenceDocuments() {}

    private static final Letterhead PRACTICE = new Letterhead("Sample Family Practice (SYNTHETIC)",
            List.of("12 Example Street, Testville, 0001", "Tel 000 000 0000", "Practice no. 0000000"), null);
    private static final Letterhead BRANCH = new Letterhead("Sample Family Practice – Northside Branch (SYNTHETIC)",
            List.of("1 Branch Road, Testville North, 0002", "Tel 000 000 0001"), null);

    public static Map<String, DocumentModel> all() {
        Map<String, DocumentModel> m = new LinkedHashMap<>();
        m.put("sick-note-practice", sickNote(PRACTICE));
        m.put("sick-note-branch", sickNote(BRANCH));
        m.put("referral-two-pages", referral());
        m.put("prescription-long-instruction", prescription());
        m.put("medical-report-tables", report());
        m.put("branded-footer-numbers", branded());
        m.put("edge-cases", edgeCases());
        return m;
    }

    static DocumentModel sickNote(Letterhead lh) {
        return new DocumentModel("Sick note", lh, List.of(
                new Heading(1, "Sick note"),
                new KeyValues(List.<String[]>of(new String[]{"Patient", "Test Patient One"}, new String[]{"Date of birth", "1 January 1990"},
                        new String[]{"Date issued", "13 October 2026"}, new String[]{"Unfit for work from", "13 October 2026"}, new String[]{"Unfit for work until", "15 October 2026"})),
                TextBlock.of("The patient was seen today and is unfit for work for the period shown."),
                new Signature("Yours faithfully,", "Dr. Sample Doctor", "HPCSA 0000000 (SYNTHETIC)", null)),
                "Synthetic reference document", true);
    }

    static DocumentModel referral() {
        List<Block> b = new ArrayList<>();
        b.add(new Heading(1, "Referral letter"));
        b.add(new KeyValues(List.<String[]>of(new String[]{"Patient", "Test Patient Two"}, new String[]{"Referred to", "Dr. Sample Specialist"})));
        b.add(new Heading(2, "Clinical summary"));
        for (int i = 1; i <= 14; i++) {
            b.add(TextBlock.of("Summary paragraph " + i + ". This is invented text standing in for a long clinical summary so the letter runs onto a second page and the renderer's pagination can be compared. "
                    + "It repeats ordinary sentences and carries no clinical meaning at all, and it is long enough to wrap over several lines in the page body."));
        }
        b.add(new Signature("Kind regards,", "Dr. Sample Doctor", "HPCSA 0000000 (SYNTHETIC)", null));
        return new DocumentModel("Referral letter", PRACTICE, b, "Synthetic reference document", true);
    }

    static DocumentModel prescription() {
        List<List<String>> rows = new ArrayList<>();
        rows.add(List.of("Examplamine 10 mg tablets (SYNTHETIC)", "1 tablet", "Twice a day", "30", "0"));
        rows.add(List.of("Testcillin 250 mg/5 ml suspension (SYNTHETIC)", "5 ml", "Three times a day for 7 days", "1 bottle", "1"));
        rows.add(List.of("Sampleprofen 200 mg tablets (SYNTHETIC)", "1 to 2 tablets", "When needed. " + "Take with food and a full glass of water and do not exceed the number of tablets written here in any one day. ".repeat(4), "20", "2"));
        for (int i = 4; i <= 12; i++) rows.add(List.of("Placeholder medicine " + i + " (SYNTHETIC)", "1", "Once a day", "30", "0"));
        return new DocumentModel("Prescription", PRACTICE, List.of(
                new Heading(1, "Prescription"),
                new KeyValues(List.<String[]>of(new String[]{"Patient", "Test Patient Three"}, new String[]{"Allergies", "None recorded"})),
                new Grid(List.of("Medicine", "Dose", "How to take it", "Quantity", "Repeats"), List.of(30d, 12d, 36d, 11d, 11d), rows),
                new Signature(null, "Dr. Sample Doctor", "HPCSA 0000000 (SYNTHETIC)", null)),
                "Synthetic reference document", true);
    }

    static DocumentModel report() {
        List<List<String>> rows = new ArrayList<>();
        for (int i = 1; i <= 45; i++) rows.add(List.of("Test item " + i, (i * 3 % 17) + " units", "Reference range placeholder", i % 7 == 0 ? "Flag placeholder" : ""));
        return new DocumentModel("Medical report", PRACTICE, List.of(
                new Heading(1, "Medical report"),
                new KeyValues(List.<String[]>of(new String[]{"Patient", "Test Patient Four"}, new String[]{"Purpose", ""})),
                new Heading(2, "Findings"),
                new Grid(List.of("Item", "Result", "Reference", "Flag"), List.of(30d, 20d, 35d, 15d), rows),
                new Heading(2, "Comment"),
                TextBlock.of("Invented comment text. The signature block below must stay with this text and never sit alone on a new page."),
                new Signature("Yours faithfully,", "Dr. Sample Doctor", "HPCSA 0000000 (SYNTHETIC)", null)),
                "Synthetic reference document", true);
    }

    static DocumentModel branded() {
        return new DocumentModel("Branded letter", new Letterhead("Sample Family Practice (SYNTHETIC)", List.of("12 Example Street, Testville"), null), List.of(
                new Heading(1, "Letter with footer and page numbers"),
                TextBlock.of("Body text in the embedded sans-serif font. When the licence review has chosen a library, the Arial-compatible font used here must be embedded in the PDF."),
                new Signature("Kind regards,", "Dr. Sample Doctor", null, null)),
                "Sample Family Practice · Synthetic reference document", true);
    }

    static DocumentModel edgeCases() {
        String longName = "Test-Patient-With-An-Extremely-Long-Hyphenated-Surname-That-Would-Never-Fit-On-One-Line-Without-Wrapping-Or-Clipping-The-Cell";
        return new DocumentModel("Edge cases", PRACTICE, List.of(
                new Heading(1, "Names, blanks and special characters"),
                new KeyValues(List.<String[]>of(new String[]{"Patient", longName}, new String[]{"Preferred name", ""}, new String[]{"Employer", null},
                        new String[]{"Notes", "Ünïcödé: é è ê ñ ç ö ü – “quotes” ‘single’ & <angle> brackets, 50% off, R1 234,56"})),
                new Grid(List.of("Name", "Value"), List.of(50d, 50d), List.of(List.of(longName, ""), List.of("Ordinary", "Value"))),
                new Signature("Regards,", "Dr. Sample Doctor", null, null)),
                null, true);
    }
}
