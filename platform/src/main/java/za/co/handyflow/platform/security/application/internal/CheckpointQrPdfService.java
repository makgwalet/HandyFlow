// security/application/internal/CheckpointQrPdfService.java

package za.co.handyflow.platform.security.application.internal;

import com.itextpdf.barcodes.BarcodeQRCode;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.xobject.PdfFormXObject;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Image;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import za.co.handyflow.platform.identity.TenantDetails;
import za.co.handyflow.platform.security.domain.model.Checkpoint;
import za.co.handyflow.platform.security.domain.model.Site;
import za.co.handyflow.platform.shared.TenantId;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.List;

/**
 * CheckpointQrPdfService — printable QR codes for checkpoints, using
 * iText's built-in BarcodeQRCode (no new dependency -- same library every
 * other PDF this session already uses).
 *
 * Every QR printed here uses the fully signed payload
 * (CheckpointScanService.generateQrPayload()) regardless of whether the
 * site currently enforces signature verification -- see that method's
 * javadoc for why this future-proofs the print, avoiding a reprint later
 * when a site enables enforcement.
 *
 * Two entry points: single checkpoint (e.g. right after regenerating one
 * compromised code) and a whole-site sheet (the realistic "supervisor
 * visits the site once, prints everything, cuts out and mounts each one"
 * workflow).
 *
 * NOTE ON iText's BarcodeQRCode API: createFormXObject(Color, PdfDocument)
 * is the signature I'm confident in for this iText7 version family, but I
 * have not test-compiled this against your exact iText dependency version
 * -- if the signature differs slightly (e.g. an extra background-color
 * param), that's a one-line fix, not a design problem.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CheckpointQrPdfService {

    private static final DeviceRgb BRAND_NAVY  = new DeviceRgb(27, 58, 107);
    private static final DeviceRgb MID_GREY    = new DeviceRgb(200, 200, 200);

    private static final float QR_SIZE_PT = 140f;

    private final SecurityPdfBrandingHelper brandingHelper;
    private final CheckpointScanService     checkpointScanService;

    /** Single-checkpoint QR — e.g. immediately after regenerating a compromised code. */
    public byte[] singleCheckpointPdf(Checkpoint checkpoint, TenantId tenantId) {
        TenantDetails tenant = brandingHelper.resolveTenant(tenantId);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (PdfDocument pdf = new PdfDocument(new PdfWriter(baos));
             Document doc  = new Document(pdf, PageSize.A4)) {

            doc.setMargins(36, 36, 36, 36);

            brandingHelper.addBrandedHeader(doc, "Checkpoint QR Code",
                    checkpoint.getSite().getName(), checkpoint.getName(), tenant, BRAND_NAVY);

            String payload = checkpointScanService.generateQrPayload(checkpoint);
            doc.add(buildQrCell(checkpoint, payload, pdf).setWidth(UnitValue.createPointValue(240)));

            brandingHelper.addBrandedFooter(doc, tenant);
        } catch (Exception e) {
            log.error("[Reporting] Checkpoint QR PDF generation failed checkpointId={}: {}",
                    checkpoint.getId(), e.getMessage(), e);
            throw new RuntimeException("PDF generation failed", e);
        }
        return baos.toByteArray();
    }

    /** Whole-site QR sheet — every active checkpoint, laid out in a 2-column grid. */
    public byte[] siteQrSheetPdf(Site site, TenantId tenantId) {
        TenantDetails tenant = brandingHelper.resolveTenant(tenantId);
        List<Checkpoint> checkpoints = site.getCheckpoints().stream()
                .filter(Checkpoint::isActive)
                .toList();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (PdfDocument pdf = new PdfDocument(new PdfWriter(baos));
             Document doc  = new Document(pdf, PageSize.A4)) {

            doc.setMargins(36, 36, 36, 36);

            brandingHelper.addBrandedHeader(doc, "Checkpoint QR Codes",
                    site.getName(), checkpoints.size() + " checkpoint"
                            + (checkpoints.size() != 1 ? "s" : ""), tenant, BRAND_NAVY);

            if (checkpoints.isEmpty()) {
                doc.add(new Paragraph("No active checkpoints at this site yet.").setFontSize(11));
            } else {
                Table grid = new Table(UnitValue.createPercentArray(new float[]{1, 1}))
                        .useAllAvailableWidth();
                for (Checkpoint cp : checkpoints) {
                    String payload = checkpointScanService.generateQrPayload(cp);
                    grid.addCell(buildQrCell(cp, payload, pdf));
                }
                doc.add(grid);
            }

            brandingHelper.addBrandedFooter(doc, tenant);
        } catch (Exception e) {
            log.error("[Reporting] Site QR sheet PDF generation failed siteId={}: {}",
                    site.getId(), e.getMessage(), e);
            throw new RuntimeException("PDF generation failed", e);
        }
        return baos.toByteArray();
    }

    /**
     * Raw PNG of just the QR code (no branding/header/footer) — for inline
     * display in the admin UI (SitesTab's checkpoint tiles), not printing.
     * Kept server-side deliberately: the payload is a security-sensitive
     * HMAC-signed access token, and generating the image here means it
     * never needs to be sent to a third-party QR-rendering library/API in
     * the browser.
     *
     * NOTE ON API: BarcodeQRCode.createAwtImage(Color, Color) is my best
     * recollection of iText7's API for this, not test-compiled against
     * your exact dependency version -- if the signature differs slightly,
     * that's a one-line fix.
     *
     * Upscaled with nearest-neighbor interpolation (not bilinear/bicubic) --
     * QR modules are hard-edged squares; smoothing them blurs the edges and
     * can make small modules unscannable at low resolution.
     */
    public byte[] qrImagePng(Checkpoint checkpoint) {
        try {
            String payload = checkpointScanService.generateQrPayload(checkpoint);
            BarcodeQRCode qr = new BarcodeQRCode(payload);
            java.awt.Image raw = qr.createAwtImage(Color.BLACK, Color.WHITE);

            int rawSize = Math.max(raw.getWidth(null), raw.getHeight(null));
            int targetSize = 300;

            BufferedImage scaled = new BufferedImage(targetSize, targetSize, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = scaled.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g.drawImage(raw, 0, 0, targetSize, targetSize, null);
            g.dispose();

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(scaled, "png", baos);
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("[Reporting] QR image generation failed checkpointId={}: {}",
                    checkpoint.getId(), e.getMessage(), e);
            throw new RuntimeException("QR image generation failed", e);
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private Cell buildQrCell(Checkpoint checkpoint, String payload, PdfDocument pdf) {
        BarcodeQRCode qr = new BarcodeQRCode(payload);
        PdfFormXObject qrObject = qr.createFormXObject(ColorConstants.BLACK, pdf);
        Image qrImage = new Image(qrObject).setWidth(QR_SIZE_PT).setHeight(QR_SIZE_PT);

        Cell cell = new Cell()
                .setBorder(new SolidBorder(MID_GREY, 0.5f))
                .setPadding(16)
                .setTextAlignment(TextAlignment.CENTER);
        cell.add(qrImage.setHorizontalAlignment(com.itextpdf.layout.properties.HorizontalAlignment.CENTER));
        cell.add(new Paragraph(checkpoint.getName())
                .setBold().setFontSize(12).setMarginTop(8).setTextAlignment(TextAlignment.CENTER));
        if (checkpoint.getDescription() != null && !checkpoint.getDescription().isBlank()) {
            cell.add(new Paragraph(checkpoint.getDescription())
                    .setFontSize(9).setFontColor(MID_GREY).setTextAlignment(TextAlignment.CENTER));
        }
        return cell;
    }
}