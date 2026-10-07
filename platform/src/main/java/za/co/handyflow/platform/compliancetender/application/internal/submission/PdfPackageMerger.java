package za.co.handyflow.platform.compliancetender.application.internal.submission;

import com.itextpdf.io.image.ImageData;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.exceptions.BadPasswordException;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.WriterProperties;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.canvas.PdfCanvas;
import com.itextpdf.kernel.utils.PdfMerger;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Image;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Opens and merges PDFs (and places JPEG/PNG images onto A4 pages) for a combined submission PDF.
 * <p>
 * UNVERIFIED against a real iText run when written (ADR-005 section 7): the library could not be
 * downloaded in the authoring session, so the behaviour here comes from iText 7.2.5's documented API and
 * is pinned by {@code PdfPackageMergerTest}, whose first Maven run is the actual spike. Every failure
 * path ends in an {@link PdfHealth} or a {@link PackageMergeException} naming the file, never a bare
 * library exception and never a silently skipped file.
 */
public final class PdfPackageMerger {

    private PdfPackageMerger() {}

    /** One input to a merge; {@code kind} decides whether it is merged as a PDF or placed as an image. */
    public record Input(String fileName, byte[] content) {}

    public record Inspection(PdfHealth health, Integer pages) {}

    /** What to do to the combined PDF: stamp "Page X of N" on every page, and/or write it with full object compression. */
    public record Options(boolean pageNumbers, boolean compress) {
        public static final Options NONE = new Options(false, false);
    }

    public record Merged(byte[] pdf, int pages, List<Integer> pagesPerInput) {}

    /** Opens the PDF just far enough to say whether it can be merged. Never throws. */
    public static Inspection inspect(byte[] pdf) {
        try (PdfDocument doc = new PdfDocument(new PdfReader(new ByteArrayInputStream(pdf)))) {
            int pages = doc.getNumberOfPages();
            return pages < 1 ? new Inspection(PdfHealth.UNREADABLE, 0) : new Inspection(PdfHealth.OK, pages);
        } catch (BadPasswordException e) {
            return new Inspection(PdfHealth.ENCRYPTED, null);
        } catch (Exception e) {
            return new Inspection(PdfHealth.UNREADABLE, null);
        }
    }

    /**
     * Merges in the order given. PDFs are copied page by page, images become one A4 page each. Any other
     * kind is refused with the file's name: originals that are not merged (Word, Excel) must be handled
     * by the caller as separate files, never passed in here.
     */
    public static Merged merge(List<Input> inputs) { return merge(inputs, Options.NONE); }

    public static Merged merge(List<Input> inputs, Options options) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        WriterProperties props = new WriterProperties();
        if (options.compress()) props.setFullCompressionMode(true).setCompressionLevel(9);
        List<Integer> pagesPerInput = new ArrayList<>();
        int total = 0;
        try (PdfDocument target = new PdfDocument(new PdfWriter(out, props))) {
            PdfMerger merger = new PdfMerger(target);
            for (Input input : inputs) {
                byte[] pdf = switch (FileKind.of(input.fileName())) {
                    case PDF -> input.content();
                    case IMAGE -> imagePage(input);
                    default -> throw new PackageMergeException(input.fileName(), "this kind of file is not merged into a PDF; it is kept as an original", null);
                };
                int pages = mergeOne(merger, input.fileName(), pdf);
                pagesPerInput.add(pages);
                total += pages;
            }
            merger.close();
            if (options.pageNumbers()) stampPageNumbers(target);
        } catch (PackageMergeException e) {
            throw e;
        } catch (Exception e) {
            throw new PackageMergeException("package", "the combined PDF could not be written (" + e.getMessage() + ")", e);
        }
        return new Merged(out.toByteArray(), total, List.copyOf(pagesPerInput));
    }

    /** "Page X of N" centred near the bottom of every page, small and grey so it never hides content. */
    static void stampPageNumbers(PdfDocument target) throws java.io.IOException {
        PdfFont font = PdfFontFactory.createFont(StandardFonts.HELVETICA);
        int total = target.getNumberOfPages();
        for (int i = 1; i <= total; i++) {
            PdfPage page = target.getPage(i);
            Rectangle box = page.getPageSize();
            String text = "Page " + i + " of " + total;
            float size = 8f;
            float width = font.getWidth(text, size);
            PdfCanvas canvas = new PdfCanvas(page.newContentStreamAfter(), page.getResources(), target);
            canvas.beginText().setFontAndSize(font, size).setFillColorGray(0.35f)
                    .moveText(box.getLeft() + (box.getWidth() - width) / 2f, box.getBottom() + 14f).showText(text).endText();
            canvas.release();
        }
    }

    private static int mergeOne(PdfMerger merger, String fileName, byte[] pdf) {
        try (PdfDocument source = new PdfDocument(new PdfReader(new ByteArrayInputStream(pdf)))) {
            int pages = source.getNumberOfPages();
            if (pages < 1) throw new PackageMergeException(fileName, "the PDF has no pages", null);
            merger.merge(source, 1, pages);
            return pages;
        } catch (PackageMergeException e) {
            throw e;
        } catch (BadPasswordException e) {
            throw new PackageMergeException(fileName, "the PDF is password protected", e);
        } catch (Exception e) {
            throw new PackageMergeException(fileName, "the PDF could not be read (" + e.getMessage() + ")", e);
        }
    }

    private static byte[] imagePage(Input input) {
        try {
            ImageData data = ImageDataFactory.create(input.content());
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (PdfDocument pdf = new PdfDocument(new PdfWriter(bytes)); Document doc = new Document(pdf, PageSize.A4)) {
                doc.setMargins(36, 36, 36, 36);
                Image image = new Image(data);
                image.scaleToFit(PageSize.A4.getWidth() - 72, PageSize.A4.getHeight() - 72);
                doc.add(image);
            }
            return bytes.toByteArray();
        } catch (Exception e) {
            throw new PackageMergeException(input.fileName(), "the image could not be read (" + e.getMessage() + ")", e);
        }
    }
}
