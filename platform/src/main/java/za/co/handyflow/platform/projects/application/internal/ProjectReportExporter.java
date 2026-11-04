package za.co.handyflow.platform.projects.application.internal;

import com.itextpdf.io.font.PdfEncodings;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.font.PdfFontFactory.EmbeddingStrategy;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.Border;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.projects.dto.ReportResponse;
import za.co.handyflow.platform.projects.dto.ReportResponse.GroupRow;
import za.co.handyflow.platform.projects.dto.ReportResponse.ProjectRow;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;

/** The portfolio report as CSV (one row per project) or an A4 landscape PDF (iText 7, embedded Liberation Sans). */
@Component
public class ProjectReportExporter {

    private static final DeviceRgb NAVY = new DeviceRgb(0x1B, 0x3A, 0x6B);
    private static final DeviceRgb WHITE = new DeviceRgb(0xFF, 0xFF, 0xFF);
    private static final DeviceRgb LIGHT_GRAY = new DeviceRgb(0xF8, 0xFA, 0xFC);
    private static final DeviceRgb MID_GRAY = new DeviceRgb(0xE2, 0xE8, 0xF0);
    private static final DeviceRgb TEXT_GRAY = new DeviceRgb(0x64, 0x74, 0x8B);
    private static final DeviceRgb TEXT_DARK = new DeviceRgb(0x0F, 0x17, 0x2A);
    private static final DeviceRgb RED = new DeviceRgb(0xDC, 0x26, 0x26);

    // ── CSV ──────────────────────────────────────────────────────────────────

    public byte[] csv(ReportResponse r) {
        StringBuilder sb = new StringBuilder("﻿");
        sb.append(String.join(",", "Project", "Name", "Client", "Type", "Status", "Health", "Start", "End", "Baseline end", "Completed",
                "On time", "Finish variance (days)", "Contract value", "Budget", "Actual cost", "Forecast at completion", "Margin", "Margin %", "Margin basis")).append("\r\n");
        for (ProjectRow p : r.projects()) {
            sb.append(String.join(",", esc(p.projectNumber()), esc(p.name()), esc(p.clientName()), esc(p.projectType()), p.status(), p.health(),
                    s(p.startDate()), s(p.endDate()), s(p.baselineEnd()), s(p.completedOn()),
                    p.onTime() == null ? "" : p.onTime() ? "yes" : "no", s(p.finishVarianceDays()),
                    num(p.contractValue()), num(p.budget()), num(p.actual()), num(p.forecast()), num(p.margin()), s(p.marginPct()),
                    p.margin() == null ? (p.costVisible() ? "" : "cost not visible") : p.forecastMargin() ? "forecast" : "actual")).append("\r\n");
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    static String esc(String v) {
        if (v == null) return "";
        String s = v.startsWith("=") || v.startsWith("+") || v.startsWith("-") || v.startsWith("@") ? "'" + v : v;   // no formula injection
        return s.contains(",") || s.contains("\"") || s.contains("\n") ? "\"" + s.replace("\"", "\"\"") + "\"" : s;
    }
    private static String s(Object o) { return o == null ? "" : o.toString(); }
    private static String num(BigDecimal v) { return v == null ? "" : v.setScale(2, RoundingMode.HALF_UP).toPlainString(); }

    // ── PDF ──────────────────────────────────────────────────────────────────

    public byte[] pdf(ReportResponse r) {
        try {
            PdfFont regular = font("/fonts/LiberationSans-Regular.ttf"), bold = font("/fonts/LiberationSans-Bold.ttf");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Document doc = new Document(new PdfDocument(new PdfWriter(out)), PageSize.A4.rotate());
            doc.setMargins(28, 28, 32, 28);
            doc.setFont(regular).setFontSize(8.5f).setFontColor(TEXT_DARK);

            Table head = new Table(UnitValue.createPercentArray(new float[]{3, 2})).useAllAvailableWidth().setBackgroundColor(NAVY);
            head.addCell(plain(new Paragraph("Projects portfolio report").setFont(bold).setFontSize(15).setFontColor(WHITE)).setPadding(10));
            head.addCell(plain(new Paragraph("Period " + r.from() + " to " + r.to()).setFontColor(WHITE).setTextAlignment(TextAlignment.RIGHT)).setPadding(10));
            doc.add(head);

            var d = r.delivery(); var m = r.money();
            Table kpi = new Table(UnitValue.createPercentArray(6)).useAllAvailableWidth().setMarginTop(10);
            kpi(kpi, bold, "Completed in period", String.valueOf(d.completed()));
            kpi(kpi, bold, "On time", d.onTimePct() == null ? "—" : d.onTimePct() + "% (" + d.onTime() + ")");
            kpi(kpi, bold, "Avg finish vs baseline", d.avgFinishVarianceDays() == null ? "—" : signed(d.avgFinishVarianceDays()) + " days");
            kpi(kpi, bold, "Revenue (completed)", zar(m.revenue()));
            kpi(kpi, bold, "Margin (completed)", zar(m.margin()) + (m.marginPct() == null ? "" : " · " + m.marginPct() + "%"));
            kpi(kpi, bold, "Forecast margin (live)", zar(m.liveForecastMargin()) + (m.liveForecastMarginPct() == null ? "" : " · " + m.liveForecastMarginPct() + "%"));
            doc.add(kpi);
            doc.add(new Paragraph(d.liveOverdue() + " live project(s) past their end date · " + d.liveForecastLate() + " forecast to finish after baseline · "
                    + m.overBudgetProjects() + " over budget by " + zar(m.costOverrun()) + " in total"
                    + (r.hiddenCostProjects() > 0 ? " · costs of " + r.hiddenCostProjects() + " project(s) not visible to you are left out" : ""))
                    .setFontColor(TEXT_GRAY).setMarginTop(4));

            doc.add(section(bold, "Projects"));
            String[] cols = {"Project", "Client", "Status", "End", "Completed", "On time", "Contract", "Actual", "Forecast", "Margin"};
            Table t = new Table(UnitValue.createPercentArray(new float[]{5, 3, 1.6f, 1.6f, 1.6f, 1.2f, 2, 2, 2, 2.4f})).useAllAvailableWidth();
            for (String c : cols) t.addHeaderCell(th(bold, c));
            boolean shade = false;
            for (ProjectRow p : r.projects()) {
                DeviceRgb bg = shade ? LIGHT_GRAY : WHITE; shade = !shade;
                t.addCell(td(p.projectNumber() + "  " + p.name(), bg));
                t.addCell(td(p.clientName() == null ? "—" : p.clientName(), bg));
                t.addCell(td(p.status().replace('_', ' ').toLowerCase(), bg));
                t.addCell(td(s(p.endDate()), bg));
                t.addCell(td(s(p.completedOn()), bg));
                t.addCell(td(p.onTime() == null ? "" : p.onTime() ? "yes" : "no (" + signed(p.finishVarianceDays()) + " d)", bg).setFontColor(Boolean.FALSE.equals(p.onTime()) ? RED : TEXT_DARK));
                t.addCell(money(p.costVisible() ? zarOrDash(p.contractValue()) : "hidden", bg));
                t.addCell(money(p.costVisible() ? zarOrDash(p.actual()) : "", bg));
                t.addCell(money(p.costVisible() ? zarOrDash(p.forecast()) : "", bg));
                t.addCell(money(p.margin() == null ? "" : zar(p.margin()) + (p.marginPct() == null ? "" : " (" + p.marginPct() + "%)") + (p.forecastMargin() ? " f" : ""), bg)
                        .setFontColor(p.margin() != null && p.margin().signum() < 0 ? RED : TEXT_DARK));
            }
            doc.add(t);
            doc.add(new Paragraph("Margin marked f is a forecast (contract value less forecast at completion); otherwise contract value less actual cost.").setFontColor(TEXT_GRAY).setFontSize(7.5f));

            doc.add(section(bold, "By client"));
            doc.add(groups(bold, r.byClient()));
            doc.add(section(bold, "By project type"));
            doc.add(groups(bold, r.byType()));

            doc.add(new Paragraph("Generated by HandyFlow Projects — " + LocalDateTime.now(ZoneId.of("Africa/Johannesburg")).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")))
                    .setFontColor(TEXT_GRAY).setFontSize(7).setTextAlignment(TextAlignment.RIGHT).setMarginTop(12));
            doc.close();
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Could not build the report PDF", e);
        }
    }

    private Table groups(PdfFont bold, List<GroupRow> rows) {
        Table t = new Table(UnitValue.createPercentArray(new float[]{5, 1.4f, 1.6f, 1.4f, 2.2f, 2.2f, 2.4f})).useAllAvailableWidth();
        for (String c : new String[]{"", "Projects", "Completed", "On time", "Revenue", "Cost", "Margin"}) t.addHeaderCell(th(bold, c));
        for (GroupRow g : rows) {
            t.addCell(td(g.key(), WHITE)); t.addCell(td(String.valueOf(g.projects()), WHITE)); t.addCell(td(String.valueOf(g.completed()), WHITE));
            t.addCell(td(g.completed() == 0 ? "—" : g.onTime() + "/" + g.completed(), WHITE));
            t.addCell(money(zar(g.revenue()), WHITE)); t.addCell(money(zar(g.cost()), WHITE));
            t.addCell(money(zar(g.margin()) + (g.marginPct() == null ? "" : " (" + g.marginPct() + "%)"), WHITE));
        }
        if (rows.isEmpty()) t.addCell(td("No projects", WHITE));
        return t;
    }

    private PdfFont font(String path) throws IOException {
        return PdfFontFactory.createFont(Objects.requireNonNull(getClass().getResourceAsStream(path)).readAllBytes(),
                PdfEncodings.WINANSI, EmbeddingStrategy.FORCE_EMBEDDED);
    }
    private static Cell plain(Paragraph p) { return new Cell().add(p).setBorder(Border.NO_BORDER); }
    private static void kpi(Table t, PdfFont bold, String label, String value) {
        t.addCell(new Cell().add(new Paragraph(label).setFontColor(TEXT_GRAY).setFontSize(7.5f))
                .add(new Paragraph(value).setFont(bold).setFontSize(11)).setBorder(new SolidBorder(MID_GRAY, 0.6f)).setPadding(6).setBackgroundColor(LIGHT_GRAY));
    }
    private static Paragraph section(PdfFont bold, String title) { return new Paragraph(title).setFont(bold).setFontSize(11).setFontColor(NAVY).setMarginTop(12).setMarginBottom(4); }
    private static Cell th(PdfFont bold, String s) {
        return new Cell().add(new Paragraph(s).setFont(bold).setFontSize(7.5f).setFontColor(TEXT_GRAY)).setBorder(Border.NO_BORDER)
                .setBorderBottom(new SolidBorder(MID_GRAY, 0.8f)).setPadding(4);
    }
    private static Cell td(String s, DeviceRgb bg) {
        return new Cell().add(new Paragraph(s == null ? "" : s)).setBorder(Border.NO_BORDER).setBackgroundColor(bg).setPadding(4);
    }
    private static Cell money(String s, DeviceRgb bg) { return td(s, bg).setTextAlignment(TextAlignment.RIGHT); }
    static String zar(BigDecimal v) {
        if (v == null) return "R 0.00";
        BigDecimal x = v.setScale(2, RoundingMode.HALF_UP);
        return (x.signum() < 0 ? "-R " : "R ") + String.format(java.util.Locale.US, "%,.2f", x.abs());
    }
    private static String zarOrDash(BigDecimal v) { return v == null ? "—" : zar(v); }
    private static String signed(Integer n) { return n == null ? "" : (n > 0 ? "+" : "") + n; }
}
