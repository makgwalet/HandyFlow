package za.co.handyflow.platform.crm.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.crm.application.internal.CompanyHierarchyService;
import za.co.handyflow.platform.crm.dto.HierarchyResponse;
import za.co.handyflow.platform.crm.dto.SetParentRequest;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

@RestController
@RequestMapping("/api/v1/crm/hierarchy")
@RequiredArgsConstructor
@Tag(name = "CRM - Company hierarchy", description = "Head office and branch links between companies")
public class CompanyHierarchyController {

    private final CompanyHierarchyService service;

    @GetMapping("/groups")
    @PreAuthorize("hasAuthority('CUSTOMER_READ')")
    @Operation(summary = "Every company that belongs to a group (has a parent or branches)")
    public ResponseEntity<ApiResponse<List<HierarchyResponse.Node>>> groups() {
        return ResponseEntity.ok(ApiResponse.success(service.groups(TenantContext.getTenantIdAsObject())));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('CUSTOMER_READ')")
    @Operation(summary = "A company with its parents, direct branches and the opportunities of the whole group")
    public ResponseEntity<ApiResponse<HierarchyResponse>> one(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(service.hierarchy(TenantContext.getTenantIdAsObject(), id)));
    }

    @PutMapping("/{id}/parent")
    @PreAuthorize("hasAuthority('CUSTOMER_UPDATE')")
    @Operation(summary = "Put a company under another company, or make it top-level with parentId null")
    public ResponseEntity<ApiResponse<HierarchyResponse>> setParent(@PathVariable UUID id, @RequestBody SetParentRequest request) {
        return ResponseEntity.ok(ApiResponse.success(service.setParent(TenantContext.getTenantIdAsObject(), id, request.parentId())));
    }
}
