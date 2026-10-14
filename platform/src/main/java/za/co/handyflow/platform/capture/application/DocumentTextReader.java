package za.co.handyflow.platform.capture.application;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;

/** Reads the text layer of a PDF. Images and scans have none and come back empty (no OCR engine here). */
@Component
public class DocumentTextReader {

    public static final int MAX_BYTES = 10 * 1024 * 1024;
    private static final int MAX_PAGES = 5;

    public enum Kind { PDF, IMAGE, OTHER }

    public static Kind kind(byte[] b) {
        if (b == null || b.length < 4) return Kind.OTHER;
        if (b[0] == '%' && b[1] == 'P' && b[2] == 'D' && b[3] == 'F') return Kind.PDF;
        if ((b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8) return Kind.IMAGE;                   // JPEG
        if ((b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') return Kind.IMAGE; // PNG
        return Kind.OTHER;
    }

    /** Text of the first few pages, or an empty string when there is none or the file cannot be read. */
    public String read(byte[] pdf) {
        if (kind(pdf) != Kind.PDF) return "";
        try (PdfDocument doc = new PdfDocument(new PdfReader(new ByteArrayInputStream(pdf)))) {
            StringBuilder sb = new StringBuilder();
            int pages = Math.min(doc.getNumberOfPages(), MAX_PAGES);
            for (int i = 1; i <= pages; i++) {
                sb.append(PdfTextExtractor.getTextFromPage(doc.getPage(i))).append('\n');
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }
}
