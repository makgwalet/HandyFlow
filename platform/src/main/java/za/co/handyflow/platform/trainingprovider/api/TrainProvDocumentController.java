package za.co.handyflow.platform.trainingprovider.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.trainingprovider.application.internal.TrainProvDocumentService;
import za.co.handyflow.platform.trainingprovider.dto.DocumentDtos.*;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/training-provider/learner-documents")
@RequiredArgsConstructor
@Tag(name = "Training Provider - Learner documents", description = "Provider-issued completion letters and participation statements")
public class TrainProvDocumentController {

    private static final String READ = "hasAnyAuthority('TRAININGPROVIDER_READ','TRAININGPROVIDER_MANAGE','TRAININGPROVIDER_ADMIN')";
    private static final String MANAGE = "hasAnyAuthority('TRAININGPROVIDER_MANAGE','TRAININGPROVIDER_ADMIN')";
    private static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");

    private final TrainProvDocumentService service;
    private final FeatureGuard featureGuard;

    @GetMapping
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<List<DocumentView>>> list(@RequestParam(required = false) UUID enrolmentId, @RequestParam(required = false) UUID learnerId,
                                                                @RequestParam(required = false) String docType, @RequestParam(required = false) String status) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success(service.list(TenantContext.getTenantIdAsObject(), enrolmentId, learnerId, docType, status)));
    }

    @GetMapping("/{id}")
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<DocumentView>> get(@PathVariable UUID id) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success(service.get(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<DocumentView>> issue(@Valid @RequestBody IssueRequest r) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success("Document issued",
                service.issue(TenantContext.getTenantIdAsObject(), r, TenantContext.getCurrentUserId(), LocalDate.now(SAST))));
    }

    @PostMapping("/{id}/hand-over")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<DocumentView>> handOver(@PathVariable UUID id, @Valid @RequestBody HandOverRequest r) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success("Hand-over recorded",
                service.handOver(TenantContext.getTenantIdAsObject(), id, r, TenantContext.getCurrentUserId(), LocalDate.now(SAST))));
    }

    @PostMapping("/{id}/void")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<DocumentView>> voidDocument(@PathVariable UUID id, @Valid @RequestBody ReasonRequest r) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success("Document voided", service.voidDocument(TenantContext.getTenantIdAsObject(), id, r.reason(), TenantContext.getCurrentUserId())));
    }
}
