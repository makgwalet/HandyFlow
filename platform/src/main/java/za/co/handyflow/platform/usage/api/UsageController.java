package za.co.handyflow.platform.usage.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.usage.application.internal.UsageService;

@RestController
@RequestMapping("/api/v1/usage")
@RequiredArgsConstructor
@Tag(name = "Usage", description = "Basic usage visibility: what this business has used against its tier (report mode, nothing is blocked)")
public class UsageController {

    private final UsageService service;

    @GetMapping
    @Operation(summary = "Invoices issued this month and seats in use against the tier limits. Free for every business; limits are provisional")
    public ResponseEntity<ApiResponse<UsageService.Snapshot>> mine() {
        return ResponseEntity.ok(ApiResponse.success(service.snapshot(TenantContext.getTenantIdAsObject())));
    }
}
