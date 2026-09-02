package za.co.handyflow.platform.facilities.application.internal;

import com.lowagie.text.*;
import com.lowagie.text.Font;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.facilities.domain.model.FacilityAsset;
import za.co.handyflow.platform.facilities.domain.model.FacilitySite;
import za.co.handyflow.platform.facilities.domain.model.FacilityWorkOrder;
import za.co.handyflow.platform.facilities.domain.repository.FacilityAssetRepository;
import za.co.handyflow.platform.facilities.domain.repository.FacilitySiteRepository;
import za.co.handyflow.platform.facilities.domain.repository.FacilityWorkOrderRepository;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;
import org.springframework.http.HttpStatus;

import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.util.UUID;

/**
 * Printable job card for the technician/vendor assigned to a work order —
 * OpenPDF, same brand colour and table/footer conventions used by every
 * other module's PDF service in this codebase.
 */
@Service
@RequiredArgsConstructor
public class FacilityPdfService {

    private static final Color BRAND_COLOR = new Color(30, 41, 59);

    private final FacilityWorkOrderRepository workOrderRepository;
    private final FacilitySiteRepository siteRepository;
    private final FacilityAssetRepository assetRepository;

    @Transactional(readOnly = true)
    public byte[] generateJobCard(TenantId tenantId, UUID workOrderId) {
        FacilityWorkOrder wo = workOrderRepository.findActiveById(tenantId, workOrderId)
                .orElseThrow(() -> new ResourceNotFoundException("FacilityWorkOrder", workOrderId.toString()));
        FacilitySite site = siteRepository.findActiveById(tenantId, wo.getSiteId()).orElse(null);
        FacilityAsset asset = wo.getAssetId() != null
                ? assetRepository.findActiveById(tenantId, wo.getAssetId()).orElse(null) : null;

        try {
            Document document = new Document(PageSize.A4, 40, 40, 50, 50);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            PdfWriter.getInstance(document, out);
            document.open();

            Font titleFont = new Font(Font.HELVETICA, 18, Font.BOLD, BRAND_COLOR);
            Font labelFont = new Font(Font.HELVETICA, 10, Font.BOLD, Color.DARK_GRAY);
            Font valueFont = new Font(Font.HELVETICA, 11, Font.NORMAL, Color.BLACK);

            document.add(new Paragraph("Work Order Job Card", titleFont));
            document.add(new Paragraph(wo.getWorkOrderNumber(), new Font(Font.HELVETICA, 13, Font.NORMAL, BRAND_COLOR)));
            document.add(Chunk.NEWLINE);

            PdfPTable table = new PdfPTable(2);
            table.setWidthPercentage(100);
            addRow(table, "Site", site != null ? site.getName() : "-", labelFont, valueFont);
            addRow(table, "Asset", asset != null ? asset.getName() + (asset.getAssetTag() != null ? " (" + asset.getAssetTag() + ")" : "") : "-", labelFont, valueFont);
            addRow(table, "Category", wo.getCategory(), labelFont, valueFont);
            addRow(table, "Priority", wo.getPriority(), labelFont, valueFont);
            addRow(table, "Status", wo.getStatus(), labelFont, valueFont);
            addRow(table, "Scheduled date", wo.getScheduledDate() != null ? wo.getScheduledDate().toString() : "-", labelFont, valueFont);
            addRow(table, "Assigned to", wo.getTechnicianName() != null ? wo.getTechnicianName()
                    : (wo.getVendorName() != null ? wo.getVendorName() + " (vendor)" : "Unassigned"), labelFont, valueFont);
            addRow(table, "Reported by", wo.getReportedBy() != null ? wo.getReportedBy() : "-", labelFont, valueFont);
            document.add(table);

            document.add(Chunk.NEWLINE);
            document.add(new Paragraph("Description", labelFont));
            document.add(new Paragraph(wo.getDescription(), valueFont));

            if (wo.getCompletionNotes() != null) {
                document.add(Chunk.NEWLINE);
                document.add(new Paragraph("Completion notes", labelFont));
                document.add(new Paragraph(wo.getCompletionNotes(), valueFont));
            }

            document.add(Chunk.NEWLINE);
            document.add(Chunk.NEWLINE);
            document.add(new Paragraph("Technician signature: ______________________________     Date: ______________", valueFont));

            document.close();
            return out.toByteArray();
        } catch (DocumentException e) {
            throw new HandyFlowException("Failed to generate job card PDF", HttpStatus.INTERNAL_SERVER_ERROR, "PDF_GENERATION_FAILED");
        }
    }

    private void addRow(PdfPTable table, String label, String value, Font labelFont, Font valueFont) {
        PdfPCell labelCell = new PdfPCell(new Phrase(label, labelFont));
        labelCell.setBorder(Rectangle.BOTTOM);
        labelCell.setPadding(6);
        PdfPCell valueCell = new PdfPCell(new Phrase(value, valueFont));
        valueCell.setBorder(Rectangle.BOTTOM);
        valueCell.setPadding(6);
        table.addCell(labelCell);
        table.addCell(valueCell);
    }
}
