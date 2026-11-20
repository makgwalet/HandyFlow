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
import za.co.handyflow.platform.legalpractice.application.internal.LpEthicalWallService;
import za.co.handyflow.platform.legalpractice.dto.CreateWallRequest;
import za.co.handyflow.platform.legalpractice.dto.LiftWallRequest;
import za.co.handyflow.platform.legalpractice.dto.WallDenialResponse;
import za.co.handyflow.platform.legalpractice.dto.WallResponse;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.UUID;

/** Ethical walls: bar a member of staff from a matter or a client. */
@RestController
@RequestMapping("/api/v1/legal-practice/ethical-walls")
@RequiredArgsConstructor
@Tag(name = "Legal Practice - Ethical walls", description = "Bar staff from a matter or client")
public class LpEthicalWallController {

    private final LpEthicalWallService service;
    private final FeatureGuard featureGuard;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('LEGALPRACTICE_WALL_MANAGE','LEGALPRACTICE_WALL_AUDIT','LEGALPRACTICE_ADMIN')")
    public ResponseEntity<ApiResponse<Page<WallResponse>>> list(@RequestParam(defaultValue = "true") boolean activeOnly,
                                                                @PageableDefault(size = 25) Pageable pageable) {
        featureGuard.requireModule("legalpractice");
        return ResponseEntity.ok(ApiResponse.success(service.list(TenantContext.getTenantIdAsObject(), activeOnly, pageable)));
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('LEGALPRACTICE_WALL_MANAGE','LEGALPRACTICE_ADMIN')")
    @Operation(summary = "Bar a member of staff from a matter or from all of a client's matters")
    public ResponseEntity<ApiResponse<WallResponse>> create(@Valid @RequestBody CreateWallRequest req) {
        featureGuard.requireModule("legalpractice");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Wall put up",
                service.create(TenantContext.getTenantIdAsObject(), req, TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName())));
    }

    @PostMapping("/{id}/lift")
    @PreAuthorize("hasAnyAuthority('LEGALPRACTICE_WALL_MANAGE','LEGALPRACTICE_ADMIN')")
    @Operation(summary = "Lift a wall. Not the person it bars")
    public ResponseEntity<ApiResponse<WallResponse>> lift(@PathVariable UUID id, @Valid @RequestBody LiftWallRequest req) {
        featureGuard.requireModule("legalpractice");
        return ResponseEntity.ok(ApiResponse.success("Wall lifted",
                service.lift(TenantContext.getTenantIdAsObject(), id, req.reason(), TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName())));
    }

    @GetMapping("/denials")
    @PreAuthorize("hasAnyAuthority('LEGALPRACTICE_WALL_AUDIT','LEGALPRACTICE_ADMIN')")
    @Operation(summary = "Who was turned away by a wall, and when")
    public ResponseEntity<ApiResponse<Page<WallDenialResponse>>> denials(@PageableDefault(size = 25) Pageable pageable) {
        featureGuard.requireModule("legalpractice");
        return ResponseEntity.ok(ApiResponse.success(service.denials(TenantContext.getTenantIdAsObject(), pageable)));
    }
}
