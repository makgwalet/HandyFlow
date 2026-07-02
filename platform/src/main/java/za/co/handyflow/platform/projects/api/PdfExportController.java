package za.co.handyflow.platform.projects.api;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.projects.application.internal.PmPdfService;

import java.util.UUID;

/**
 * PDF export endpoints for the PM module.
 * All return application/pdf with Content-Disposition: attachment.
 */
@RestController
@RequestMapping("/api/v1/projects")
@RequiredArgsConstructor
public class PdfExportController {

    private final PmPdfService pdfService;

    // ── Risk Register ─────────────────────────────────────────────────────────

    @GetMapping("/{projectId}/export/risk-register")
    @PreAuthorize("hasAuthority('PM_READ')")
    public ResponseEntity<byte[]> riskRegister(@PathVariable UUID projectId) {
        byte[] pdf = pdfService.generateRiskRegister(projectId);
        return pdf(pdf, "risk-register-" + projectId + ".pdf");
    }

    // ── Site Diary ────────────────────────────────────────────────────────────

    @GetMapping("/{projectId}/site-diaries/{diaryId}/export")
    @PreAuthorize("hasAuthority('PM_READ')")
    public ResponseEntity<byte[]> siteDiary(@PathVariable UUID projectId,
                                            @PathVariable UUID diaryId) {
        byte[] pdf = pdfService.generateSiteDiary(projectId, diaryId);
        return pdf(pdf, "site-diary-" + diaryId + ".pdf");
    }

    // ── Snag List ─────────────────────────────────────────────────────────────

    @GetMapping("/{projectId}/export/snag-list")
    @PreAuthorize("hasAuthority('PM_READ')")
    public ResponseEntity<byte[]> snagList(@PathVariable UUID projectId) {
        byte[] pdf = pdfService.generateSnagList(projectId);
        return pdf(pdf, "snag-list-" + projectId + ".pdf");
    }

    // ── Change Order ──────────────────────────────────────────────────────────

    @GetMapping("/{projectId}/change-orders/{coId}/export")
    @PreAuthorize("hasAuthority('PM_READ')")
    public ResponseEntity<byte[]> changeOrder(@PathVariable UUID projectId,
                                              @PathVariable UUID coId) {
        byte[] pdf = pdfService.generateChangeOrder(projectId, coId);
        return pdf(pdf, "change-order-" + coId + ".pdf");
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private static ResponseEntity<byte[]> pdf(byte[] content, String filename) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate")
                .contentLength(content.length)
                .body(content);
    }
}
