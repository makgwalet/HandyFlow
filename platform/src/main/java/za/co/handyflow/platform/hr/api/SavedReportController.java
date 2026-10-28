package za.co.handyflow.platform.hr.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.hr.application.internal.HrAccess;
import za.co.handyflow.platform.hr.application.internal.SavedReportService;
import za.co.handyflow.platform.hr.dto.SavedReportDtos.SaveRequest;
import za.co.handyflow.platform.hr.dto.SavedReportDtos.View;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.UUID;

/** A person's own saved report views. Opening the report itself still goes through the report endpoints and their rights. */
@RestController
@RequestMapping("/api/v1/hr/saved-reports")
@RequiredArgsConstructor
@PreAuthorize("hasAnyAuthority('HR_REPORT_READ')")
@Tag(name = "Saved HR reports", description = "Keep a report and period, and be reminded weekly or monthly")
public class SavedReportController {

    private final SavedReportService service;

    @GetMapping
    @Operation(summary = "My saved reports, with the dates each period means today")
    public ResponseEntity<ApiResponse<List<View>>> mine(Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(service.mine(TenantContext.getTenantIdAsObject(), user(auth), service.todaySast())));
    }

    @PostMapping
    @Operation(summary = "Save a report and period, with an optional weekly or monthly reminder")
    public ResponseEntity<ApiResponse<View>> save(@RequestBody SaveRequest req, Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Report saved",
                service.save(TenantContext.getTenantIdAsObject(), user(auth), HrAccess.held(auth), req, service.todaySast())));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete one of my saved reports")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id, Authentication auth) {
        service.delete(TenantContext.getTenantIdAsObject(), user(auth), id);
        return ResponseEntity.ok(ApiResponse.success("Saved report deleted", null));
    }

    private static UUID user(Authentication auth) {
        try { return UUID.fromString(auth.getName()); } catch (RuntimeException e) { return null; }
    }
}
