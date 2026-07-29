package za.co.handyflow.platform.tasks.application.internal;

import com.lowagie.text.*;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfWriter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.tasks.domain.model.TaskBoard;
import za.co.handyflow.platform.tasks.domain.model.TaskColumn;
import za.co.handyflow.platform.tasks.dto.TaskResponse;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Generates a board status-report PDF the "board/task export" gap flagged
 * in the Tasks module audit ("useful for a status report to a client or
 * manager who doesn't have platform access"). Groups tasks by column, in
 * the board's own column order, so the PDF reads the same left-to-right
 * shape as the Kanban view it's exported from.
 * <p>
 * Same OpenPDF (com.lowagie.text.*) library, brand colors, and
 * header/divider/footer structure as this codebase's other PDF generators
 * (ScPoPdfGenerator, AccFeeNotePdfGenerator) — deliberately NOT iText7, see
 * ScPoPdfGenerator's own Javadoc for why (AGPL licensing without a
 * purchased commercial license for this project; OpenPDF is LGPL).
 * <p>
 * ASSUMPTION: TaskResponse is a record with plain (non-"get"-prefixed)
 * accessors — columnId(), title(), priority(), etc. — matching every other
 * DTO seen in this codebase (NotificationRequest, Recipient,
 * UserOptionResponse). If TaskResponse is actually a plain class with
 * getters instead, swap the accessor calls below accordingly; nothing
 * else in this file depends on which.
 */
@Slf4j
@Component
public class TasksBoardPdfGenerator {

    private static final Color BRAND_DARK  = new Color(27, 58, 107);
    private static final Color BRAND_BLUE  = new Color(59, 130, 246);   // Tasks accent — matches the frontend's "In Progress" color
    private static final Color LIGHT_GRAY  = new Color(248, 250, 252);
    private static final Color MID_GRAY    = new Color(100, 116, 139);
    private static final Color BORDER_GRAY = new Color(226, 232, 240);
    private static final Color OVERDUE_RED = new Color(220, 38, 38);
    private static final Color OVERDUE_BG  = new Color(254, 242, 242);

    private static final Color PRIORITY_URGENT = new Color(185, 28, 28);
    private static final Color PRIORITY_HIGH   = new Color(180, 83, 9);
    private static final Color PRIORITY_NORMAL = new Color(29, 78, 216);
    private static final Color PRIORITY_LOW    = new Color(100, 116, 139);

    private static final Font BRAND_FONT         = new Font(Font.HELVETICA, 20, Font.BOLD, BRAND_DARK);
    private static final Font TENANT_FONT        = new Font(Font.HELVETICA, 10, Font.NORMAL, MID_GRAY);
    private static final Font DOC_TYPE_FONT      = new Font(Font.HELVETICA, 16, Font.BOLD, BRAND_BLUE);
    private static final Font BOARD_NAME_FONT    = new Font(Font.HELVETICA, 11, Font.NORMAL, MID_GRAY);
    private static final Font GENERATED_FONT     = new Font(Font.HELVETICA, 9, Font.NORMAL, MID_GRAY);
    private static final Font SUMMARY_LABEL_FONT = new Font(Font.HELVETICA, 8, Font.BOLD, MID_GRAY);
    private static final Font COLUMN_HEAD_FONT   = new Font(Font.HELVETICA, 11, Font.BOLD, Color.WHITE);
    private static final Font TABLE_HEADER_FONT  = new Font(Font.HELVETICA, 8, Font.BOLD, Color.WHITE);
    private static final Font TABLE_CELL_FONT    = new Font(Font.HELVETICA, 9, Font.NORMAL, BRAND_DARK);
    private static final Font TABLE_CELL_MUTED   = new Font(Font.HELVETICA, 9, Font.NORMAL, MID_GRAY);
    private static final Font FOOTER_FONT        = new Font(Font.HELVETICA, 8, Font.NORMAL, MID_GRAY);
    private static final Font EMPTY_FONT         = new Font(Font.HELVETICA, 9, Font.ITALIC, MID_GRAY);

    private static final DateTimeFormatter D = DateTimeFormatter
            .ofPattern("dd MMM yyyy")
            .withZone(ZoneId.of("Africa/Johannesburg"));
    private static final DateTimeFormatter DT = DateTimeFormatter
            .ofPattern("dd MMM yyyy, HH:mm")
            .withZone(ZoneId.of("Africa/Johannesburg"));

    // Same thread-safety rationale as ScPoPdfGenerator.ZA_SYMBOLS: this bean is a
    // Spring singleton that can serve concurrent export requests, and
    // DecimalFormat itself is not thread-safe — build a fresh instance per call
    // from these shared (effectively immutable) symbols rather than one static one.
    private static final DecimalFormatSymbols HOURS_SYMBOLS;
    static {
        HOURS_SYMBOLS = new DecimalFormatSymbols();
        HOURS_SYMBOLS.setGroupingSeparator(' ');
        HOURS_SYMBOLS.setDecimalSeparator('.');
    }

    public byte[] generate(TaskBoard board, List<TaskColumn> columns, List<TaskResponse> tasks, String tenantName) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4, 50, 50, 60, 60);
            PdfWriter writer = PdfWriter.getInstance(doc, baos);
            writer.setPageEvent(new FooterHandler(board.getName()));

            doc.open();
            addHeader(doc, board, tenantName);
            addDivider(doc);
            addSummary(doc, tasks);

            Map<UUID, List<TaskResponse>> byColumn = tasks.stream()
                    .collect(Collectors.groupingBy(TaskResponse::columnId, LinkedHashMap::new, Collectors.toList()));

            for (TaskColumn col : columns) {
                addColumnSection(doc, col, byColumn.getOrDefault(col.getId(), List.of()));
            }

            doc.close();
            log.info("[TASKS] Generated board status report PDF for board={}", board.getId());
            return baos.toByteArray();

        } catch (Exception e) {
            log.error("[TASKS] Board PDF generation failed for board={}: {}", board.getId(), e.getMessage());
            throw new RuntimeException("Failed to generate board status report PDF", e);
        }
    }

    // Header

    private void addHeader(Document doc, TaskBoard board, String tenantName) throws DocumentException {
        PdfPTable header = new PdfPTable(2);
        header.setWidthPercentage(100);
        header.setWidths(new float[]{1, 1});

        PdfPCell left = new PdfPCell();
        left.setBorder(Rectangle.NO_BORDER);
        left.setPadding(0);
        left.addElement(new Paragraph("HandyFlow", BRAND_FONT));
        if (tenantName != null && !tenantName.isBlank()) {
            Paragraph tenantP = new Paragraph(tenantName, TENANT_FONT);
            tenantP.setSpacingBefore(2);
            left.addElement(tenantP);
        }
        header.addCell(left);

        PdfPCell right = new PdfPCell();
        right.setBorder(Rectangle.NO_BORDER);
        right.setPadding(0);
        right.setHorizontalAlignment(Element.ALIGN_RIGHT);

        Paragraph typeP = new Paragraph("TASK STATUS REPORT", DOC_TYPE_FONT);
        typeP.setAlignment(Element.ALIGN_RIGHT);
        right.addElement(typeP);

        Paragraph boardP = new Paragraph(board.getName(), BOARD_NAME_FONT);
        boardP.setAlignment(Element.ALIGN_RIGHT);
        boardP.setSpacingBefore(3);
        right.addElement(boardP);

        Paragraph genP = new Paragraph("Generated " + DT.format(java.time.Instant.now()), GENERATED_FONT);
        genP.setAlignment(Element.ALIGN_RIGHT);
        genP.setSpacingBefore(3);
        right.addElement(genP);

        header.addCell(right);
        doc.add(header);
    }

    // Summary strip

    private void addSummary(Document doc, List<TaskResponse> tasks) throws DocumentException {
        long total     = tasks.size();
        long overdue   = tasks.stream().filter(TaskResponse::overdue).count();
        long completed = tasks.stream().filter(t -> "DONE".equals(t.status())).count();
        long open      = total - completed;

        PdfPTable summary = new PdfPTable(4);
        summary.setWidthPercentage(100);
        summary.setSpacingBefore(16);
        summary.setSpacingAfter(18);

        addSummaryCell(summary, "TOTAL TASKS", String.valueOf(total), BRAND_DARK);
        addSummaryCell(summary, "OPEN", String.valueOf(open), BRAND_DARK);
        addSummaryCell(summary, "COMPLETED", String.valueOf(completed), BRAND_DARK);
        addSummaryCell(summary, "OVERDUE", String.valueOf(overdue), overdue > 0 ? OVERDUE_RED : BRAND_DARK);

        doc.add(summary);
    }

    private void addSummaryCell(PdfPTable table, String label, String value, Color valueColor) {
        PdfPCell cell = new PdfPCell();
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setBackgroundColor(LIGHT_GRAY);
        cell.setPadding(12);
        cell.addElement(new Paragraph(label, SUMMARY_LABEL_FONT));
        Paragraph valP = new Paragraph(value, new Font(Font.HELVETICA, 17, Font.BOLD, valueColor));
        valP.setSpacingBefore(4);
        cell.addElement(valP);
        table.addCell(cell);
    }

    // Per-column task table

    private void addColumnSection(Document doc, TaskColumn column, List<TaskResponse> tasks) throws DocumentException {
        PdfPTable head = new PdfPTable(1);
        head.setWidthPercentage(100);
        head.setSpacingBefore(6);
        PdfPCell headCell = new PdfPCell(new Phrase(column.getName() + "  (" + tasks.size() + ")", COLUMN_HEAD_FONT));
        headCell.setBackgroundColor(column.getColor() != null ? hexToColor(column.getColor()) : BRAND_BLUE);
        headCell.setBorder(Rectangle.NO_BORDER);
        headCell.setPadding(8);
        head.addCell(headCell);
        doc.add(head);

        if (tasks.isEmpty()) {
            Paragraph empty = new Paragraph("No tasks in this column.", EMPTY_FONT);
            empty.setSpacingBefore(6);
            empty.setSpacingAfter(14);
            doc.add(empty);
            return;
        }

        PdfPTable table = new PdfPTable(5);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{2.6f, 1.3f, 1f, 1f, 1.1f});
        table.setSpacingAfter(16);

        for (String h : new String[]{"Task", "Assignee", "Priority", "Due Date", "Logged / Est."}) {
            PdfPCell headerCell = new PdfPCell(new Phrase(h, TABLE_HEADER_FONT));
            headerCell.setBackgroundColor(BRAND_DARK);
            headerCell.setBorder(Rectangle.NO_BORDER);
            headerCell.setPadding(6);
            table.addCell(headerCell);
        }

        int i = 0;
        for (TaskResponse t : tasks) {
            boolean overdue = Boolean.TRUE.equals(t.overdue());
            Color rowBg = overdue ? OVERDUE_BG : (i++ % 2 == 0 ? Color.WHITE : LIGHT_GRAY);

            addCell(table, t.title(), TABLE_CELL_FONT, Element.ALIGN_LEFT, rowBg);
            addCell(table, t.assigneeName() != null ? t.assigneeName() : "Unassigned", TABLE_CELL_MUTED, Element.ALIGN_LEFT, rowBg);

            PdfPCell priorityCell = new PdfPCell(new Phrase(prettyPriority(t.priority()),
                    new Font(Font.HELVETICA, 9, Font.BOLD, priorityColor(t.priority()))));
            priorityCell.setBorder(Rectangle.BOTTOM);
            priorityCell.setBorderColor(BORDER_GRAY);
            priorityCell.setBackgroundColor(rowBg);
            priorityCell.setPadding(6);
            table.addCell(priorityCell);

            String dueText = t.dueDate() != null ? D.format(t.dueDate()) : "-";
            Font dueFont = overdue ? new Font(Font.HELVETICA, 9, Font.BOLD, OVERDUE_RED) : TABLE_CELL_FONT;
            addCell(table, dueText + (overdue ? " (overdue)" : ""), dueFont, Element.ALIGN_LEFT, rowBg);

            String hoursText = formatHours(t.loggedHours()) + " / " +
                    (t.estimatedHours() != null ? formatHours(t.estimatedHours()) : "-");
            addCell(table, hoursText, TABLE_CELL_MUTED, Element.ALIGN_RIGHT, rowBg);
        }

        doc.add(table);
    }

    private void addCell(PdfPTable table, String text, Font font, int align, Color bg) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setBorder(Rectangle.BOTTOM);
        cell.setBorderColor(BORDER_GRAY);
        cell.setBackgroundColor(bg);
        cell.setPadding(6);
        cell.setHorizontalAlignment(align);
        table.addCell(cell);
    }

    // Helpers

    private String prettyPriority(String priority) {
        if (priority == null) return "-";
        return priority.charAt(0) + priority.substring(1).toLowerCase();
    }

    private Color priorityColor(String priority) {
        if (priority == null) return PRIORITY_LOW;
        return switch (priority) {
            case "URGENT" -> PRIORITY_URGENT;
            case "HIGH"   -> PRIORITY_HIGH;
            case "NORMAL" -> PRIORITY_NORMAL;
            default       -> PRIORITY_LOW;
        };
    }

    private String formatHours(BigDecimal hours) {
        DecimalFormat fmt = new DecimalFormat("0.#", HOURS_SYMBOLS);
        return fmt.format(hours != null ? hours : BigDecimal.ZERO) + "h";
    }

    private Color hexToColor(String hex) {
        try {
            String h = hex.startsWith("#") ? hex.substring(1) : hex;
            return new Color(Integer.parseInt(h.substring(0, 2), 16),
                    Integer.parseInt(h.substring(2, 4), 16),
                    Integer.parseInt(h.substring(4, 6), 16));
        } catch (Exception e) {
            return BRAND_BLUE; // malformed/unexpected column color format - fall back rather than fail the whole export
        }
    }

    private void addDivider(Document doc) throws DocumentException {
        PdfPTable line = new PdfPTable(1);
        line.setWidthPercentage(100);
        line.setSpacingBefore(8);
        line.setSpacingAfter(8);
        PdfPCell cell = new PdfPCell();
        cell.setFixedHeight(1);
        cell.setBackgroundColor(BRAND_DARK);
        cell.setBorder(Rectangle.NO_BORDER);
        line.addCell(cell);
        doc.add(line);
    }

    // Page footer handler — same ColumnText.showTextAligned(...) pattern as
    // ScPoPdfGenerator's FooterHandler; see that class's Javadoc for why the
    // naive beginText()/showText()/endText() pairing is unsafe across pages.

    private static class FooterHandler extends PdfPageEventHelper {
        private final String boardName;
        FooterHandler(String boardName) { this.boardName = boardName; }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            try {
                com.lowagie.text.pdf.PdfContentByte cb = writer.getDirectContent();
                Phrase footer = new Phrase(
                        "HandyFlow \u00b7 " + boardName + " \u00b7 Page " + writer.getPageNumber(),
                        FOOTER_FONT);
                ColumnText.showTextAligned(cb, Element.ALIGN_LEFT, footer,
                        document.leftMargin(), document.bottomMargin() - 20, 0);
            } catch (Exception ignored) {
                // A broken footer must never take down PDF generation for a
                // document that otherwise generated correctly.
            }
        }
    }
}