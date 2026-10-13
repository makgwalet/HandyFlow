package za.co.handyflow.platform.clinic.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.clinic.application.internal.ClinicIssuedDocumentService;
import za.co.handyflow.platform.clinic.application.internal.ClinicIssuedDocumentService.IssuedRow;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.UserContext;

import java.util.List;
import java.util.UUID;

/** The register of numbered documents, for the person who may withdraw one (CLINIC-DEC-024). Withdrawing keeps the record and the public check says so. */
@RestController
@RequestMapping("/api/v1/clinic/issued-documents")
@RequiredArgsConstructor
@Tag(name = "Clinic issued documents", description = "Numbered documents issued by the clinic")
public class ClinicIssuedDocumentController {

    public record VoidRequest(String reason) {}

    private final ClinicIssuedDocumentService service;

    private static UUID tenant() { return TenantContext.getTenantIdAsObject().getValue(); }

    @GetMapping
    @PreAuthorize("hasAuthority('CLINIC_DOCUMENT_VOID')")
    @Operation(summary = "List issued documents, newest first")
    public ResponseEntity<ApiResponse<List<IssuedRow>>> list(@RequestParam(required = false) String type, @RequestParam(required = false) String status,
                                                             @RequestParam(required = false) String q, @RequestParam(defaultValue = "100") int limit) {
        return ResponseEntity.ok(ApiResponse.success("Success", service.list(tenant(), type, status, q, limit)));
    }

    @PostMapping("/{id}/void")
    @PreAuthorize("hasAuthority('CLINIC_DOCUMENT_VOID')")
    @Operation(summary = "Withdraw an issued document, with a reason")
    public ResponseEntity<ApiResponse<Void>> voidDocument(@PathVariable UUID id, @RequestBody VoidRequest body) {
        service.voidIt(tenant(), id, UserContext.getCurrentUserId(), body == null ? null : body.reason());
        return ResponseEntity.ok(ApiResponse.success("Document withdrawn", null));
    }
}
