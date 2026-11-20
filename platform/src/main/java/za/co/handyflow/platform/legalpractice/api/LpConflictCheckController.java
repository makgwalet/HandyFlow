package za.co.handyflow.platform.legalpractice.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.legalpractice.application.internal.LpConflictCheckService;
import za.co.handyflow.platform.legalpractice.dto.DecideConflictCheckRequest;
import za.co.handyflow.platform.legalpractice.dto.LpConflictCheckResponse;
import za.co.handyflow.platform.legalpractice.dto.RunConflictCheckRequest;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.UUID;

/** Conflict-of-interest checks: run one before opening a matter; a second person reviews any that found something. */
@RestController
@RequestMapping("/api/v1/legal-practice/conflict-checks")
@RequiredArgsConstructor
@Tag(name = "Legal Practice - Conflict checks", description = "Conflict-of-interest search before a matter is opened")
public class LpConflictCheckController {

    private final LpConflictCheckService service;
    private final FeatureGuard featureGuard;

    @PostMapping
    @PreAuthorize("hasAnyAuthority('LEGALPRACTICE_CONFLICT_RUN','LEGALPRACTICE_MANAGE','LEGALPRACTICE_ADMIN')")
    @Operation(summary = "Search for conflicts for a client and the other parties to a prospective matter")
    public ResponseEntity<ApiResponse<LpConflictCheckResponse>> run(@Valid @RequestBody RunConflictCheckRequest req) {
        featureGuard.requireModule("legalpractice");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Conflict check run",
                service.run(TenantContext.getTenantIdAsObject(), req, TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName())));
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('LEGALPRACTICE_CONFLICT_RUN','LEGALPRACTICE_CONFLICT_REVIEW','LEGALPRACTICE_MANAGE','LEGALPRACTICE_ADMIN')")
    public ResponseEntity<ApiResponse<Page<LpConflictCheckResponse>>> list(
            @RequestParam(required = false) String status, @RequestParam(required = false) UUID clientId,
            @PageableDefault(size = 25) Pageable pageable) {
        featureGuard.requireModule("legalpractice");
        return ResponseEntity.ok(ApiResponse.success(service.list(TenantContext.getTenantIdAsObject(), status, clientId, pageable)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('LEGALPRACTICE_CONFLICT_RUN','LEGALPRACTICE_CONFLICT_REVIEW','LEGALPRACTICE_MANAGE','LEGALPRACTICE_ADMIN')")
    public ResponseEntity<ApiResponse<LpConflictCheckResponse>> get(@PathVariable UUID id) {
        featureGuard.requireModule("legalpractice");
        return ResponseEntity.ok(ApiResponse.success(service.get(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/{id}/decide")
    @PreAuthorize("hasAnyAuthority('LEGALPRACTICE_CONFLICT_REVIEW','LEGALPRACTICE_ADMIN')")
    @Operation(summary = "Decide a check that found something: cleared, proceed with consent, or declined. Not the person who ran it, unless the firm allows it")
    public ResponseEntity<ApiResponse<LpConflictCheckResponse>> decide(@PathVariable UUID id, @Valid @RequestBody DecideConflictCheckRequest req) {
        featureGuard.requireModule("legalpractice");
        return ResponseEntity.ok(ApiResponse.success("Decision recorded",
                service.decide(TenantContext.getTenantIdAsObject(), id, req, TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName())));
    }
}
