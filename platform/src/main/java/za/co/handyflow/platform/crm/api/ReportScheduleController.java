package za.co.handyflow.platform.crm.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.crm.application.internal.ReportScheduleService;
import za.co.handyflow.platform.crm.domain.model.ReportType;
import za.co.handyflow.platform.crm.dto.ReportPreviewResponse;
import za.co.handyflow.platform.crm.dto.ReportScheduleRequest;
import za.co.handyflow.platform.crm.dto.ReportScheduleResponse;
import za.co.handyflow.platform.crm.dto.ReportSendResponse;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

@RestController
@RequestMapping("/api/v1/crm/report-schedules")
@RequiredArgsConstructor
@Tag(name = "CRM - Scheduled reports", description = "Reports sent to people on a schedule")
public class ReportScheduleController {
    private final ReportScheduleService service;

    @GetMapping
    @PreAuthorize("hasAuthority('CUSTOMER_READ')")
    @Operation(summary = "The scheduled reports")
    public ResponseEntity<ApiResponse<List<ReportScheduleResponse>>> list() {
        return ResponseEntity.ok(ApiResponse.success(service.list(TenantContext.getTenantIdAsObject())));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('CUSTOMER_UPDATE')")
    @Operation(summary = "Schedule a report")
    public ResponseEntity<ApiResponse<ReportScheduleResponse>> create(@RequestBody ReportScheduleRequest request) {
        return ResponseEntity.ok(ApiResponse.success(service.create(TenantContext.getTenantIdAsObject(), request)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('CUSTOMER_UPDATE')")
    @Operation(summary = "Change a scheduled report")
    public ResponseEntity<ApiResponse<ReportScheduleResponse>> update(@PathVariable UUID id, @RequestBody ReportScheduleRequest request) {
        return ResponseEntity.ok(ApiResponse.success(service.update(TenantContext.getTenantIdAsObject(), id, request)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('CUSTOMER_UPDATE')")
    @Operation(summary = "Delete a scheduled report")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        service.delete(TenantContext.getTenantIdAsObject(), id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @GetMapping("/preview")
    @PreAuthorize("hasAuthority('CUSTOMER_READ')")
    @Operation(summary = "What a report says right now")
    public ResponseEntity<ApiResponse<ReportPreviewResponse>> preview(@RequestParam ReportType type) {
        return ResponseEntity.ok(ApiResponse.success(service.preview(TenantContext.getTenantIdAsObject(), type)));
    }

    @PostMapping("/{id}/send")
    @PreAuthorize("hasAuthority('CUSTOMER_UPDATE')")
    @Operation(summary = "Send the report now, as well as on its schedule")
    public ResponseEntity<ApiResponse<ReportSendResponse>> sendNow(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(service.sendNow(TenantContext.getTenantIdAsObject(), id)));
    }
}
