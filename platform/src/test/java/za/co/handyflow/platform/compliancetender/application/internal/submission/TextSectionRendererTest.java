package za.co.handyflow.platform.compliancetender.application.internal.submission;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TextSectionRendererTest {

    private final TextSectionRenderer renderer = new TextSectionRenderer();
    private final Letterhead zeta = new Letterhead("Zeta Civils (Pty) Ltd", List.of("12 Main Road, Polokwane", "Tel: 015 000 0000", "VAT: 4123456789"), null,
            "ZETA-TND-00001", "Routine Road Maintenance of National Route R555", "Elias Motsoaledi Municipality", "X.002-246-2026/1", "21 December 2026");

    private static String textOf(byte[] pdf) throws Exception {
        try (PdfDocument doc = new PdfDocument(new PdfReader(new ByteArrayInputStream(pdf)))) {
            StringBuilder sb = new StringBuilder();
            for (int i = 1; i <= doc.getNumberOfPages(); i++) sb.append(PdfTextExtractor.getTextFromPage(doc.getPage(i))).append('\n');
            return sb.toString();
        }
    }

    @Test
    void coverLetterIsALetterOnTheLetterhead() throws Exception {
        byte[] pdf = renderer.render(zeta, SectionCatalogue.COVER_LETTER, "Dear Sir/Madam,\n\nWe submit our tender.\n\nYours faithfully");
        assertThat(PdfPackageMerger.inspect(pdf).health()).isEqualTo(PdfHealth.OK);
        String text = textOf(pdf);
        assertThat(text).contains("Zeta Civils (Pty) Ltd", "Tel: 015 000 0000", "Elias Motsoaledi Municipality", "Re: Tender X.002-246-2026/1",
                "Closing date: 21 December 2026", "We submit our tender.", "Yours faithfully");
    }

    @Test
    void theTextIsNeverChangedOrExtended() throws Exception {
        String text = textOf(renderer.render(zeta, SectionCatalogue.COVER_LETTER, "Just this."));
        assertThat(text).contains("Just this.").doesNotContain("Dear").doesNotContain("Yours");
    }

    @Test
    void companyProfileLinesBecomeAFactsTableWithHeadings() throws Exception {
        String text = textOf(renderer.render(zeta, SectionCatalogue.COMPANY_PROFILE,
                "Company: Zeta Civils (Pty) Ltd\nVAT number: 4123456789\n\n## Registrations and standing\nCIDB Grading: 6CE  (valid until 1 Aug 2027)"));
        assertThat(text).contains("Company profile", "VAT number", "4123456789", "Registrations and standing", "CIDB Grading", "6CE");
    }

    @Test
    void aMissingOrBrokenLogoStillGivesAPage() {
        Letterhead broken = new Letterhead("Zeta", List.of(), new byte[]{1, 2, 3, 4}, null, "Road", null, null, null);
        byte[] pdf = renderer.render(broken, SectionCatalogue.COMPLIANCE, "Line");
        assertThat(PdfPackageMerger.inspect(pdf).health()).isEqualTo(PdfHealth.OK);
        assertThat(PdfPackageMerger.inspect(renderer.render(null, SectionCatalogue.COMPLIANCE, "Line")).pages()).isEqualTo(1);
    }

    @Test
    void longTextRunsOntoMorePagesWithTheCompanyOnEach() throws Exception {
        StringBuilder body = new StringBuilder();
        for (int i = 0; i < 120; i++) body.append("Paragraph number ").append(i).append(" of a long compliance response.\n");
        byte[] pdf = renderer.render(zeta, SectionCatalogue.COMPLIANCE, body.toString());
        assertThat(PdfPackageMerger.inspect(pdf).pages()).isGreaterThan(1);
        String text = textOf(pdf);
        assertThat(text).contains("Page 1", "Page 2");
        assertThat(text.split("Zeta Civils \\(Pty\\) Ltd", -1).length - 1).isGreaterThanOrEqualTo(3); // letterhead plus each footer
    }

    @Test
    void logoDataUriIsDecodedAndBadOnesIgnored() {
        assertThat(TenantLetterheadProvider.logoBytes("data:image/png;base64,AAEC")).containsExactly(0, 1, 2);
        assertThat(TenantLetterheadProvider.logoBytes("https://x/logo.png")).isNull();
        assertThat(TenantLetterheadProvider.logoBytes("data:image/png;base64,@@@")).isNull();
        assertThat(TenantLetterheadProvider.logoBytes(null)).isNull();
    }
}
