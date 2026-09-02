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
import za.co.handyflow.platform.insurancebrokerage.application.internal.InsBrokClientService;
import za.co.handyflow.platform.insurancebrokerage.domain.model.InsBrokClient;
import za.co.handyflow.platform.insurancebrokerage.dto.CreateInsBrokClientRequest;
import za.co.handyflow.platform.insurancebrokerage.dto.InsBrokClientResponse;
import za.co.handyflow.platform.insurancebrokerage.dto.UpdateInsBrokClientRequest;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/insurance-brokerage/clients")
@RequiredArgsConstructor
@Tag(name = "Insurance Brokerage - Clients", description = "Businesses/individuals this brokerage places and manages cover for")
public class InsBrokClientController {

    private final InsBrokClientService clientService;
    private final FeatureGuard featureGuard;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('INSURANCEBROKERAGE_READ','INSURANCEBROKERAGE_MANAGE','INSURANCEBROKERAGE_ADMIN')")
    public ResponseEntity<ApiResponse<Page<InsBrokClientResponse>>> list(@PageableDefault(size = 24) Pageable pageable) {
        featureGuard.requireModule("insurancebrokerage");
        return ResponseEntity.ok(ApiResponse.success(
                clientService.list(TenantContext.getTenantIdAsObject(), pageable).map(this::toResponse)));
    }

    @GetMapping("/all")
    @PreAuthorize("hasAnyAuthority('INSURANCEBROKERAGE_READ','INSURANCEBROKERAGE_MANAGE','INSURANCEBROKERAGE_ADMIN')")
    @Operation(summary = "Flat list for dropdowns")
    public ResponseEntity<ApiResponse<List<InsBrokClientResponse>>> listAll() {
        featureGuard.requireModule("insurancebrokerage");
        return ResponseEntity.ok(ApiResponse.success(
                clientService.listAll(TenantContext.getTenantIdAsObject()).stream().map(this::toResponse).toList()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('INSURANCEBROKERAGE_READ','INSURANCEBROKERAGE_MANAGE','INSURANCEBROKERAGE_ADMIN')")
    public ResponseEntity<ApiResponse<InsBrokClientResponse>> get(@PathVariable UUID id) {
        featureGuard.requireModule("insurancebrokerage");
        return ResponseEntity.ok(ApiResponse.success(toResponse(clientService.get(TenantContext.getTenantIdAsObject(), id))));
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('INSURANCEBROKERAGE_MANAGE','INSURANCEBROKERAGE_ADMIN')")
    public ResponseEntity<ApiResponse<InsBrokClientResponse>> create(@Valid @RequestBody CreateInsBrokClientRequest req) {
        featureGuard.requireModule("insurancebrokerage");
        InsBrokClient client = clientService.create(TenantContext.getTenantIdAsObject(), req.clientName(), req.clientType(),
                req.registrationOrIdNumber(), req.contactName(), req.contactEmail(), req.contactPhone(), req.address(),
                req.defaultCommissionRatePct(), req.notes());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Client created", toResponse(client)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('INSURANCEBROKERAGE_MANAGE','INSURANCEBROKERAGE_ADMIN')")
    public ResponseEntity<ApiResponse<InsBrokClientResponse>> update(@PathVariable UUID id,
            @Valid @RequestBody UpdateInsBrokClientRequest req) {
        featureGuard.requireModule("insurancebrokerage");
        InsBrokClient client = clientService.update(TenantContext.getTenantIdAsObject(), id, req.clientName(),
                req.clientType(), req.registrationOrIdNumber(), req.contactName(), req.contactEmail(), req.contactPhone(),
                req.address(), req.defaultCommissionRatePct(), req.notes());
        return ResponseEntity.ok(ApiResponse.success("Client updated", toResponse(client)));
    }

    @PostMapping("/{id}/deactivate")
    @PreAuthorize("hasAnyAuthority('INSURANCEBROKERAGE_MANAGE','INSURANCEBROKERAGE_ADMIN')")
    public ResponseEntity<ApiResponse<InsBrokClientResponse>> deactivate(@PathVariable UUID id) {
        featureGuard.requireModule("insurancebrokerage");
        return ResponseEntity.ok(ApiResponse.success("Client deactivated",
                toResponse(clientService.deactivate(TenantContext.getTenantIdAsObject(), id))));
    }

    @PostMapping("/{id}/reactivate")
    @PreAuthorize("hasAnyAuthority('INSURANCEBROKERAGE_MANAGE','INSURANCEBROKERAGE_ADMIN')")
    public ResponseEntity<ApiResponse<InsBrokClientResponse>> reactivate(@PathVariable UUID id) {
        featureGuard.requireModule("insurancebrokerage");
        return ResponseEntity.ok(ApiResponse.success("Client reactivated",
                toResponse(clientService.reactivate(TenantContext.getTenantIdAsObject(), id))));
    }

    private InsBrokClientResponse toResponse(InsBrokClient c) {
        return new InsBrokClientResponse(c.getId(), c.getClientName(), c.getClientType(), c.getRegistrationOrIdNumber(),
                c.getContactName(), c.getContactEmail(), c.getContactPhone(), c.getAddress(),
                c.getDefaultCommissionRatePct(), c.getNotes(), c.isActive(), c.getCreatedAt(), c.getUpdatedAt());
    }
}
