package za.co.handyflow.platform.complianceservices.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.complianceservices.application.internal.ComplianceImportService;
import za.co.handyflow.platform.complianceservices.dto.ImportDtos.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantContext;

import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/compliance-services/imports")
@RequiredArgsConstructor
@Tag(name = "Compliance Services - Import", description = "Bring clients and registrations in from a CSV file")
public class ComplianceImportController {

    private static final String READ = "hasAnyAuthority('COMPLIANCE_SERVICES_READ','COMPLIANCE_SERVICES_MANAGE','COMPLIANCE_SERVICES_ADMIN')";
    private static final String MANAGE = "hasAnyAuthority('COMPLIANCE_SERVICES_MANAGE','COMPLIANCE_SERVICES_IMPORT_MANAGE','COMPLIANCE_SERVICES_ADMIN')";
    private static final String ADMIN = "hasAuthority('COMPLIANCE_SERVICES_ADMIN')";
    private static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");

    private final ComplianceImportService service;
    private final FeatureGuard featureGuard;

    @GetMapping
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<List<BatchView>>> list() {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success(service.list(TenantContext.getTenantIdAsObject())));
    }

    @GetMapping("/{id}")
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<BatchDetail>> get(@PathVariable UUID id) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success(service.get(TenantContext.getTenantIdAsObject(), id)));
    }

    /** Reads and checks the file. Changes no client or registration. */
    @PostMapping(value = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<BatchDetail>> preview(@RequestParam String kind, @RequestPart("file") MultipartFile file) {
        featureGuard.requireModule("complianceservices");
        try {
            return ResponseEntity.ok(ApiResponse.success(service.preview(TenantContext.getTenantIdAsObject(), kind == null ? "" : kind.toUpperCase(), file.getOriginalFilename(), file.getBytes(), TenantContext.getCurrentUserId())));
        } catch (IOException e) {
            throw new HandyFlowException("The file could not be read", org.springframework.http.HttpStatus.BAD_REQUEST, "IMPORT_INVALID");
        }
    }

    @PostMapping("/{id}/commit")
    @PreAuthorize(ADMIN)
    public ResponseEntity<ApiResponse<BatchDetail>> commit(@PathVariable UUID id) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success(service.commit(TenantContext.getTenantIdAsObject(), id, TenantContext.getCurrentUserId(), LocalDate.now(SAST))));
    }

    @PostMapping("/{id}/discard")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<BatchDetail>> discard(@PathVariable UUID id, @Valid @RequestBody DiscardRequest body) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success(service.discard(TenantContext.getTenantIdAsObject(), id, body.note(), TenantContext.getCurrentUserId())));
    }
}
