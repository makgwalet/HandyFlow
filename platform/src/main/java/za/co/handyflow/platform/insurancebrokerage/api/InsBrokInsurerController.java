package za.co.handyflow.platform.insurancebrokerage.api;

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
import za.co.handyflow.platform.insurancebrokerage.application.internal.InsBrokInsurerService;
import za.co.handyflow.platform.insurancebrokerage.domain.model.InsBrokInsurer;
import za.co.handyflow.platform.insurancebrokerage.dto.CreateInsBrokInsurerRequest;
import za.co.handyflow.platform.insurancebrokerage.dto.InsBrokInsurerResponse;
import za.co.handyflow.platform.insurancebrokerage.dto.UpdateInsBrokInsurerRequest;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.UUID;

/** Permission triplet INSURANCEBROKERAGE_READ/MANAGE/ADMIN, same gating shape InsPolicyController already uses. */
@RestController
@RequestMapping("/api/v1/insurance-brokerage/insurers")
@RequiredArgsConstructor
@Tag(name = "Insurance Brokerage - Insurers", description = "Insurer/underwriter master data this brokerage places business with")
public class InsBrokInsurerController {

    private final InsBrokInsurerService insurerService;
    private final FeatureGuard featureGuard;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('INSURANCEBROKERAGE_READ','INSURANCEBROKERAGE_MANAGE','INSURANCEBROKERAGE_ADMIN')")
    public ResponseEntity<ApiResponse<Page<InsBrokInsurerResponse>>> list(@PageableDefault(size = 24) Pageable pageable) {
        featureGuard.requireModule("insurancebrokerage");
        return ResponseEntity.ok(ApiResponse.success(
                insurerService.list(TenantContext.getTenantIdAsObject(), pageable).map(this::toResponse)));
    }

    @GetMapping("/all")
    @PreAuthorize("hasAnyAuthority('INSURANCEBROKERAGE_READ','INSURANCEBROKERAGE_MANAGE','INSURANCEBROKERAGE_ADMIN')")
    @Operation(summary = "Flat list for dropdowns — same /all shape other master-data controllers in this codebase already use")
    public ResponseEntity<ApiResponse<List<InsBrokInsurerResponse>>> listAll() {
        featureGuard.requireModule("insurancebrokerage");
        return ResponseEntity.ok(ApiResponse.success(
                insurerService.listAll(TenantContext.getTenantIdAsObject()).stream().map(this::toResponse).toList()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('INSURANCEBROKERAGE_READ','INSURANCEBROKERAGE_MANAGE','INSURANCEBROKERAGE_ADMIN')")
    public ResponseEntity<ApiResponse<InsBrokInsurerResponse>> get(@PathVariable UUID id) {
        featureGuard.requireModule("insurancebrokerage");
        return ResponseEntity.ok(ApiResponse.success(toResponse(insurerService.get(TenantContext.getTenantIdAsObject(), id))));
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('INSURANCEBROKERAGE_MANAGE','INSURANCEBROKERAGE_ADMIN')")
    public ResponseEntity<ApiResponse<InsBrokInsurerResponse>> create(@Valid @RequestBody CreateInsBrokInsurerRequest req) {
        featureGuard.requireModule("insurancebrokerage");
        InsBrokInsurer insurer = insurerService.create(TenantContext.getTenantIdAsObject(), req.name(), req.contactName(),
                req.contactEmail(), req.contactPhone(), req.notes());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Insurer created", toResponse(insurer)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('INSURANCEBROKERAGE_MANAGE','INSURANCEBROKERAGE_ADMIN')")
    public ResponseEntity<ApiResponse<InsBrokInsurerResponse>> update(@PathVariable UUID id,
            @Valid @RequestBody UpdateInsBrokInsurerRequest req) {
        featureGuard.requireModule("insurancebrokerage");
        InsBrokInsurer insurer = insurerService.update(TenantContext.getTenantIdAsObject(), id, req.name(),
                req.contactName(), req.contactEmail(), req.contactPhone(), req.notes());
        return ResponseEntity.ok(ApiResponse.success("Insurer updated", toResponse(insurer)));
    }

    @PostMapping("/{id}/deactivate")
    @PreAuthorize("hasAnyAuthority('INSURANCEBROKERAGE_MANAGE','INSURANCEBROKERAGE_ADMIN')")
    public ResponseEntity<ApiResponse<InsBrokInsurerResponse>> deactivate(@PathVariable UUID id) {
        featureGuard.requireModule("insurancebrokerage");
        return ResponseEntity.ok(ApiResponse.success("Insurer deactivated",
                toResponse(insurerService.deactivate(TenantContext.getTenantIdAsObject(), id))));
    }

    @PostMapping("/{id}/reactivate")
    @PreAuthorize("hasAnyAuthority('INSURANCEBROKERAGE_MANAGE','INSURANCEBROKERAGE_ADMIN')")
    public ResponseEntity<ApiResponse<InsBrokInsurerResponse>> reactivate(@PathVariable UUID id) {
        featureGuard.requireModule("insurancebrokerage");
        return ResponseEntity.ok(ApiResponse.success("Insurer reactivated",
                toResponse(insurerService.reactivate(TenantContext.getTenantIdAsObject(), id))));
    }

    private InsBrokInsurerResponse toResponse(InsBrokInsurer i) {
        return new InsBrokInsurerResponse(i.getId(), i.getName(), i.getContactName(), i.getContactEmail(),
                i.getContactPhone(), i.getNotes(), i.isActive(), i.getCreatedAt(), i.getUpdatedAt());
    }
}
