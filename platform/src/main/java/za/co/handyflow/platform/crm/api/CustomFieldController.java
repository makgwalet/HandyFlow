package za.co.handyflow.platform.crm.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.crm.application.internal.CustomFieldService;
import za.co.handyflow.platform.crm.dto.CustomFieldRequest;
import za.co.handyflow.platform.crm.dto.CustomFieldResponse;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/crm/custom-fields")
@RequiredArgsConstructor
@Tag(name = "CRM - Custom fields", description = "Fields an account defines for its own customers")
public class CustomFieldController {

    private final CustomFieldService service;

    @GetMapping
    @PreAuthorize("hasAuthority('CUSTOMER_READ')")
    @Operation(summary = "The account's custom fields (active ones, or all with includeInactive=true)")
    public ResponseEntity<ApiResponse<List<CustomFieldResponse>>> list(@RequestParam(defaultValue = "false") boolean includeInactive) {
        return ResponseEntity.ok(ApiResponse.success(service.list(TenantContext.getTenantIdAsObject(), includeInactive)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('CUSTOMER_UPDATE')")
    @Operation(summary = "Add a custom field")
    public ResponseEntity<ApiResponse<CustomFieldResponse>> create(@RequestBody CustomFieldRequest request) {
        return ResponseEntity.ok(ApiResponse.success(service.create(TenantContext.getTenantIdAsObject(), request)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('CUSTOMER_UPDATE')")
    @Operation(summary = "Rename, reorder, make required, change the options of, or switch off a custom field")
    public ResponseEntity<ApiResponse<CustomFieldResponse>> update(@PathVariable UUID id, @RequestBody CustomFieldRequest request) {
        return ResponseEntity.ok(ApiResponse.success(service.update(TenantContext.getTenantIdAsObject(), id, request)));
    }
}
