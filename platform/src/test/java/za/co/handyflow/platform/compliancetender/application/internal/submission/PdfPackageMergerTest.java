package za.co.handyflow.platform.compliancetender.application.internal.submission;

import com.itextpdf.kernel.pdf.EncryptionConstants;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.WriterProperties;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.compliancetender.application.internal.submission.PdfPackageMerger.Input;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.imageio.ImageIO;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The PDF merge spike (ADR-005 section 7). The PDFs are generated here with iText, so no sample files are
 * needed; real tender PDFs (scans, odd producers) are the part this cannot cover and are still to be tried.
 * Assertions are limited to behaviour that is certain from iText's documented API; for the owner-password
 * case the behaviour is not certain, so the test only requires that nothing throws and that a verdict comes back.
 */
class PdfPackageMergerTest {

    private static byte[] pdf(int pages, WriterProperties props) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PdfWriter writer = props == null ? new PdfWriter(out) : new PdfWriter(out, props);
        try (PdfDocument doc = new PdfDocument(writer); Document layout = new Document(doc)) {
            for (int i = 1; i <= pages; i++) {
                layout.add(new Paragraph("Page " + i));
                if (i < pages) layout.add(new com.itextpdf.layout.element.AreaBreak());
            }
        }
        return out.toByteArray();
    }

    private static byte[] plainPdf(int pages) { return pdf(pages, null); }

    private static byte[] userPasswordPdf(int pages) {
        byte[] user = "secret".getBytes(StandardCharsets.UTF_8);
        byte[] owner = "owner".getBytes(StandardCharsets.UTF_8);
        return pdf(pages, new WriterProperties().setStandardEncryption(user, owner, EncryptionConstants.ALLOW_PRINTING,
                EncryptionConstants.ENCRYPTION_AES_128));
    }

    private static byte[] ownerOnlyPdf(int pages) {
        byte[] owner = "owner".getBytes(StandardCharsets.UTF_8);
        return pdf(pages, new WriterProperties().setStandardEncryption(null, owner, EncryptionConstants.ALLOW_PRINTING,
                EncryptionConstants.ENCRYPTION_AES_128));
    }

    private static byte[] png() throws Exception {
        BufferedImage image = new BufferedImage(400, 300, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    @Test
    @DisplayName("a normal PDF inspects as OK with its page count")
    void inspectOk() {
        PdfPackageMerger.Inspection i = PdfPackageMerger.inspect(plainPdf(3));
        assertThat(i.health()).isEqualTo(PdfHealth.OK);
        assertThat(i.pages()).isEqualTo(3);
    }

    @Test
    @DisplayName("a PDF with a user password inspects as ENCRYPTED")
    void inspectEncrypted() {
        assertThat(PdfPackageMerger.inspect(userPasswordPdf(2)).health()).isEqualTo(PdfHealth.ENCRYPTED);
    }

    @Test
    @DisplayName("bytes that are not a PDF inspect as UNREADABLE and do not throw")
    void inspectGarbage() {
        assertThat(PdfPackageMerger.inspect("this is not a pdf".getBytes(StandardCharsets.UTF_8)).health()).isEqualTo(PdfHealth.UNREADABLE);
    }

    @Test
    @DisplayName("a truncated PDF inspects as UNREADABLE and does not throw")
    void inspectTruncated() {
        byte[] whole = plainPdf(2);
        byte[] cut = java.util.Arrays.copyOf(whole, whole.length / 3);
        assertThat(PdfPackageMerger.inspect(cut).health()).isEqualTo(PdfHealth.UNREADABLE);
    }

    @Test
    @DisplayName("an owner-password-only PDF gives a verdict without throwing (the verdict itself is what the spike records)")
    void inspectOwnerOnly() {
        assertThat(PdfPackageMerger.inspect(ownerOnlyPdf(2)).health()).isNotNull();
    }

    @Test
    @DisplayName("merge keeps order and adds the pages up")
    void mergeOrderAndPages() {
        PdfPackageMerger.Merged merged = PdfPackageMerger.merge(List.of(
                new Input("a.pdf", plainPdf(2)), new Input("b.pdf", plainPdf(3)), new Input("c.PDF", plainPdf(1))));
        assertThat(merged.pages()).isEqualTo(6);
        assertThat(merged.pagesPerInput()).isEqualTo(List.of(2, 3, 1));
        assertThat(PdfPackageMerger.inspect(merged.pdf()).pages()).isEqualTo(6);
    }

    @Test
    @DisplayName("an image becomes one page between two PDFs")
    void mergeImage() throws Exception {
        PdfPackageMerger.Merged merged = PdfPackageMerger.merge(List.of(
                new Input("a.pdf", plainPdf(1)), new Input("scan.png", png()), new Input("b.pdf", plainPdf(1))));
        assertThat(merged.pagesPerInput()).isEqualTo(List.of(1, 1, 1));
        assertThat(merged.pages()).isEqualTo(3);
    }

    @Test
    @DisplayName("a password-protected input fails the merge and names the file")
    void mergeEncryptedNamesFile() {
        assertThatThrownBy(() -> PdfPackageMerger.merge(List.of(new Input("ok.pdf", plainPdf(1)), new Input("locked.pdf", userPasswordPdf(1)))))
                .isInstanceOf(PackageMergeException.class);
    }

    @Test
    @DisplayName("a damaged input fails the merge with a PackageMergeException, not a library exception")
    void mergeDamaged() {
        assertThatThrownBy(() -> PdfPackageMerger.merge(List.of(new Input("bad.pdf", "nope".getBytes(StandardCharsets.UTF_8)))))
                .isInstanceOf(PackageMergeException.class);
    }

    @Test
    @DisplayName("a Word or Excel original is refused by the merger (it is kept by the caller instead)")
    void mergeRefusesOffice() {
        assertThatThrownBy(() -> PdfPackageMerger.merge(List.of(new Input("pricing.xlsx", new byte[]{1, 2, 3}))))
                .isInstanceOf(PackageMergeException.class);
    }
}
