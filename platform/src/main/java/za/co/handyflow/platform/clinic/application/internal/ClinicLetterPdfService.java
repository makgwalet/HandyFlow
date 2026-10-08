package za.co.handyflow.platform.clinic.application.internal;

import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.canvas.draw.SolidLine;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.LineSeparator;
import com.itextpdf.layout.element.Paragraph;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import za.co.handyflow.platform.clinic.domain.model.*;
import za.co.handyflow.platform.clinic.domain.repository.*;
import za.co.handyflow.platform.shared.TenantId;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

/** A general letter (prescription letter, fitness letter, appointment letter and so on) written for one visit (patch 0164). */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClinicLetterPdfService {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH);
    private static final DeviceRgb TEAL = new DeviceRgb(13, 148, 136);
    private static final DeviceRgb MUTED = new DeviceRgb(120, 130, 140);

    private final ClinicLetterValues letterValues;

    public byte[] generate(TenantId t, UUID consultationId, String title, String body) {
        return generate(t, null, consultationId, title, body, null, null);
    }

    /** A letter for a patient, with or without a visit, optionally addressed to a person or company. Merge fields typed in the text are filled here too. */
    public byte[] generate(TenantId t, UUID patientId, UUID consultationId, String title, String body, String recipientName, String recipientCompany) {
        if (title == null || title.isBlank()) throw new IllegalArgumentException("Give the letter a title");
        if (body == null || body.isBlank()) throw new IllegalArgumentException("The letter has no text");
        if (body.length() > 8000) throw new IllegalArgumentException("The letter is longer than 8000 characters");
        if (!LetterMerge.unknown(body + " " + title).isEmpty()) throw new IllegalArgumentException("The letter has merge fields this system does not know: " + String.join(", ", LetterMerge.unknown(body + " " + title)));
        var loaded = letterValues.load(t, patientId, consultationId, recipientName, recipientCompany);
        ClinicPatient p = loaded.patient();
        ClinicPractitioner dr = loaded.doctor();
        title = LetterMerge.render(title, loaded.values());
        body = LetterMerge.render(body, loaded.values());
        String company = loaded.values().get("practice.name") == null ? "" : loaded.values().get("practice.name");
        String toName = recipientName == null || recipientName.isBlank() ? null : recipientName.trim();
        String toCompany = recipientCompany == null || recipientCompany.isBlank() ? null : recipientCompany.trim();

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfDocument pdf = new PdfDocument(new PdfWriter(out));
            Document doc = new Document(pdf, PageSize.A4);
            doc.setMargins(36, 40, 36, 40);
            PdfFont regular = PdfFontFactory.createFont(StandardFonts.HELVETICA);
            PdfFont bold = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);

            doc.add(new Paragraph(company).setFont(bold).setFontSize(14));
            if (dr != null) doc.add(new Paragraph(drName(dr.getFullName()) + (dr.getHpcsaNumber() != null ? " · HPCSA " + dr.getHpcsaNumber() : ""))
                    .setFont(regular).setFontSize(9).setFontColor(MUTED));
            doc.add(new LineSeparator(new SolidLine(1f)).setMarginTop(8).setMarginBottom(16).setFontColor(TEAL));
            doc.add(new Paragraph(LocalDate.now(AppointmentRules.CLINIC_ZONE).format(DATE)).setFont(regular).setFontSize(10).setMarginBottom(12));
            if (toName != null) doc.add(new Paragraph(toName).setFont(bold).setFontSize(10).setMarginBottom(0));
            if (toCompany != null) doc.add(new Paragraph(toCompany).setFont(regular).setFontSize(10).setMarginBottom(0));
            if (toName != null || toCompany != null) doc.add(new Paragraph(" ").setFontSize(6));
            String dob = p.getDateOfBirth() == null ? "" : " (DOB " + p.getDateOfBirth().format(DATE) + ")";
            doc.add(new Paragraph("Re: " + p.getFirstName() + " " + p.getLastName() + dob).setFont(bold).setFontSize(11).setMarginBottom(12));
            doc.add(new Paragraph(title.trim()).setFont(bold).setFontSize(14).setFontColor(TEAL).setMarginBottom(12));
            for (String para : body.trim().split("\\R\\s*\\R")) {
                doc.add(new Paragraph(para.trim()).setFont(regular).setFontSize(10).setMarginBottom(10));
            }
            doc.add(new Paragraph("Kind regards,").setFont(regular).setFontSize(10).setMarginTop(14).setMarginBottom(30));
            if (dr != null) doc.add(new Paragraph(drName(dr.getFullName())).setFont(bold).setFontSize(11));
            else if (!company.isEmpty()) doc.add(new Paragraph(company).setFont(bold).setFontSize(11));
            doc.close();
            return out.toByteArray();
        } catch (Exception e) {
            log.error("Failed to generate letter for patient={} consultation={}: {}", p.getId(), consultationId, e.getMessage(), e);
            throw new RuntimeException("Letter generation failed", e);
        }
    }

    private static String drName(String fullName) {
        if (fullName == null) return "";
        String s = fullName.trim();
        return s.toLowerCase(Locale.ROOT).startsWith("dr") ? s : "Dr. " + s;
    }
}
