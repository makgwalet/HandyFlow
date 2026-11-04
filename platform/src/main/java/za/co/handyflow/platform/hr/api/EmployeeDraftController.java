package za.co.handyflow.platform.hr.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.hr.application.internal.EmployeeDraftService;
import za.co.handyflow.platform.hr.dto.EmployeeDraftDtos.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/hr/employee-drafts")
@RequiredArgsConstructor
@Tag(name = "Employee drafts", description = "Half-finished employee registrations, private to the person who started them")
@PreAuthorize("hasAuthority('HR_MANAGE')")
public class EmployeeDraftController {

    private final EmployeeDraftService service;

    @GetMapping
    @Operation(summary = "My drafts, newest first, without their contents")
    public ResponseEntity<ApiResponse<List<Summary>>> mine(Principal p) {
        return ResponseEntity.ok(ApiResponse.success(service.list(TenantContext.getTenantIdAsObject(), user(p))));
    }

    @GetMapping("/{id}")
    @Operation(summary = "One of my drafts, with its contents")
    public ResponseEntity<ApiResponse<Detail>> one(@PathVariable UUID id, Principal p) {
        return ResponseEntity.ok(ApiResponse.success(service.get(TenantContext.getTenantIdAsObject(), user(p), id)));
    }

    @PostMapping
    @Operation(summary = "Start a draft")
    public ResponseEntity<ApiResponse<Detail>> create(@Valid @RequestBody Save req, Principal p) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Draft saved",
                service.save(TenantContext.getTenantIdAsObject(), user(p), null, req)));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Replace one of my drafts")
    public ResponseEntity<ApiResponse<Detail>> replace(@PathVariable UUID id, @Valid @RequestBody Save req, Principal p) {
        return ResponseEntity.ok(ApiResponse.success("Draft saved", service.save(TenantContext.getTenantIdAsObject(), user(p), id, req)));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete one of my drafts")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id, Principal p) {
        service.delete(TenantContext.getTenantIdAsObject(), user(p), id);
        return ResponseEntity.ok(ApiResponse.success("Draft deleted", null));
    }

    private static UUID user(Principal p) {
        try { return p == null ? null : UUID.fromString(p.getName()); } catch (Exception e) { return null; }
    }
}
