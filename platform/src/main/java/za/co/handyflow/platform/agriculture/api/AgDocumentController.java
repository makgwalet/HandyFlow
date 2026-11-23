package za.co.handyflow.platform.agriculture.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.agriculture.application.internal.AgMovementDocumentService;
import za.co.handyflow.platform.agriculture.application.internal.AgRegisterService;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.shared.TenantContext;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;

/** Downloads: the herd, treatment and spray registers (CSV) and the document of identification for a sale (PDF). */
@RestController
@RequestMapping("/api/v1/agriculture")
@RequiredArgsConstructor
@Tag(name = "Agriculture - Registers and documents", description = "Registers as CSV, sale document as PDF")
public class AgDocumentController {

    private static final MediaType CSV = new MediaType("text", "csv", StandardCharsets.UTF_8);

    private final AgRegisterService registerService;
    private final AgMovementDocumentService documentService;
    private final FeatureGuard featureGuard;

    @GetMapping("/farms/{farmId}/registers/herd")
    @PreAuthorize("hasAuthority('AGRICULTURE_READ')")
    @Operation(summary = "Herd register (animals and groups) as CSV")
    public ResponseEntity<byte[]> herdRegister(@PathVariable UUID farmId) {
        featureGuard.requireModule("agriculture");
        return csv(registerService.herd(TenantContext.getTenantIdAsObject(), farmId));
    }

    @GetMapping("/farms/{farmId}/registers/treatments")
    @PreAuthorize("hasAuthority('AGRICULTURE_READ')")
    @Operation(summary = "Veterinary treatment register as CSV, with withdrawal end dates")
    public ResponseEntity<byte[]> treatmentRegister(@PathVariable UUID farmId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        featureGuard.requireModule("agriculture");
        return csv(registerService.treatments(TenantContext.getTenantIdAsObject(), farmId, from, to));
    }

    @GetMapping("/farms/{farmId}/registers/sprays")
    @PreAuthorize("hasAuthority('AGRICULTURE_READ')")
    @Operation(summary = "Spray and input register as CSV, with pre-harvest intervals")
    public ResponseEntity<byte[]> sprayRegister(@PathVariable UUID farmId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        featureGuard.requireModule("agriculture");
        return csv(registerService.sprays(TenantContext.getTenantIdAsObject(), farmId, from, to));
    }

    @GetMapping("/movement-records/{movementId}/document")
    @PreAuthorize("hasAuthority('AGRICULTURE_READ')")
    @Operation(summary = "Document of identification (Stock Theft Act s6) for a sale or transfer out, as PDF")
    public ResponseEntity<byte[]> saleDocument(@PathVariable UUID movementId) {
        featureGuard.requireModule("agriculture");
        AgMovementDocumentService.SaleDocument doc = documentService.saleDocument(TenantContext.getTenantIdAsObject(), movementId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(doc.fileName()).build().toString())
                .contentType(MediaType.APPLICATION_PDF)
                .body(doc.bytes());
    }

    private static ResponseEntity<byte[]> csv(AgRegisterService.Register register) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(register.fileName()).build().toString())
                .contentType(CSV)
                .body(register.csv().getBytes(StandardCharsets.UTF_8));
    }
}
