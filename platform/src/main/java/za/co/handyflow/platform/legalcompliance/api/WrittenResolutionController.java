package za.co.handyflow.platform.legalcompliance.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.legalcompliance.application.internal.WrittenResolutionService;
import za.co.handyflow.platform.legalcompliance.domain.model.WrittenStatus;
import za.co.handyflow.platform.legalcompliance.dto.WrittenResolutionDtos.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

/** Written (circular) resolutions. Committee managers circulate and close them; any signed-in member answers their own. */
@RestController
@RequestMapping("/api/v1/legalcompliance/written-resolutions")
@RequiredArgsConstructor
@Tag(name = "Legal/Compliance - Written resolutions", description = "Committee decisions taken without a meeting")
public class WrittenResolutionController {

    private static final String CAN_READ = "hasAnyAuthority('LEGALCOMPLIANCE_READ','LEGALCOMPLIANCE_MANAGE','LEGALCOMPLIANCE_GOVERNANCE_MANAGE','LEGALCOMPLIANCE_ADMIN')";
    private static final String CAN_MANAGE = "hasAnyAuthority('LEGALCOMPLIANCE_GOVERNANCE_MANAGE','LEGALCOMPLIANCE_ADMIN')";

    private final WrittenResolutionService service;
    private final FeatureGuard featureGuard;

    @GetMapping
    @PreAuthorize(CAN_READ)
    public ResponseEntity<ApiResponse<List<WrittenView>>> list(@RequestParam(required = false) UUID committeeId, @RequestParam(required = false) WrittenStatus status) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success(service.list(tenant(), committeeId, status)));
    }

    @GetMapping("/{id}")
    @PreAuthorize(CAN_READ)
    public ResponseEntity<ApiResponse<WrittenDetail>> detail(@PathVariable UUID id) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success(service.detail(tenant(), id)));
    }

    @PostMapping
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<WrittenDetail>> circulate(@Valid @RequestBody CirculateRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Written resolution circulated", service.circulate(tenant(), req, uid(), uname())));
    }

    @PostMapping("/{id}/members/{memberId}/record")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<WrittenDetail>> record(@PathVariable UUID id, @PathVariable UUID memberId, @Valid @RequestBody RecordRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Answer recorded", service.recordOnBehalf(tenant(), id, memberId, req, uid(), uname())));
    }

    @PostMapping("/{id}/close")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<WrittenDetail>> close(@PathVariable UUID id) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Written resolution closed", service.close(tenant(), id, uid(), uname())));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<WrittenDetail>> cancel(@PathVariable UUID id, @Valid @RequestBody CancelRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Written resolution cancelled", service.cancel(tenant(), id, req, uid(), uname())));
    }

    @GetMapping("/mine")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<MyWritten>>> mine() {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success(service.mine(tenant(), uid())));
    }

    @PostMapping("/{id}/respond")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<MyWritten>> respond(@PathVariable UUID id, @Valid @RequestBody RespondRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Answer recorded", service.respond(tenant(), id, req, uid(), uname())));
    }

    private static TenantId tenant() { return TenantContext.getTenantIdAsObject(); }
    private static UUID uid() { return TenantContext.getCurrentUserId(); }
    private static String uname() { return TenantContext.getCurrentUserName(); }
}
