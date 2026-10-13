package za.co.handyflow.platform.clinic.application.internal.document;

import java.util.List;

/**
 * A document described without reference to any PDF library (CLINIC-DEC-023). Templates and the studio build this;
 * a {@link DocumentRenderer} turns it into a PDF. Nothing here may import iText, PDFBox or any other renderer.
 */
public record DocumentModel(String title, Letterhead letterhead, List<Block> blocks, String footerText, boolean pageNumbers) {

    public DocumentModel {
        blocks = blocks == null ? List.of() : List.copyOf(blocks);
    }

    /** Practice or branch heading. {@code logo} is PNG or JPEG bytes, or null. */
    public record Letterhead(String name, List<String> lines, byte[] logo) {
        public Letterhead {
            lines = lines == null ? List.of() : List.copyOf(lines);
        }
    }

    public sealed interface Block permits Heading, TextBlock, KeyValues, Grid, Rule, Spacer, Signature {}

    /** level 1 is the document title, 2 a section, 3 a sub-section. */
    public record Heading(int level, String text) implements Block {}

    public record TextBlock(String text, boolean bold, boolean muted) implements Block {
        public static TextBlock of(String text) { return new TextBlock(text, false, false); }
    }

    /** Label and value pairs (patient details, dates). A blank value is printed as a dash, never dropped. */
    public record KeyValues(List<String[]> pairs) implements Block {
        public KeyValues { pairs = pairs == null ? List.of() : List.copyOf(pairs); }
    }

    /** {@code weights} are relative column widths. The header row repeats on every page. */
    public record Grid(List<String> header, List<Double> weights, List<List<String>> rows) implements Block {
        public Grid {
            header = header == null ? List.of() : List.copyOf(header);
            weights = weights == null ? List.of() : List.copyOf(weights);
            rows = rows == null ? List.of() : List.copyOf(rows);
        }
    }

    public record Rule() implements Block {}

    public record Spacer(int points) implements Block {}

    /** Kept together on one page and never left alone at the top of a page by a renderer. A signature image is a picture, not a cryptographic signature (CLINIC-DEC-024). */
    public record Signature(String closing, String signerName, String signerLine, byte[] image) implements Block {}
}
