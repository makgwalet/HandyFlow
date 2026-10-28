package za.co.handyflow.platform.recruiter.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.recruiter.application.internal.RecruiterCvMigrationService;
import za.co.handyflow.platform.recruiter.dto.RecruiterCvMigrationDtos.Result;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/recruiter/cv")
@RequiredArgsConstructor
@Tag(name = "Recruiter", description = "Moving old CVs into file storage")
public class RecruiterCvMigrationController {

    private final RecruiterCvMigrationService migration;

    @PostMapping("/migrate-legacy")
    @PreAuthorize("hasAuthority('RECRUITER_ADMIN')")
    @Operation(summary = "Move a batch of old-style CVs into file storage. Repeat with nextAfter until done")
    public ResponseEntity<ApiResponse<Result>> migrate(@RequestParam(required = false) UUID after, @RequestParam(required = false) Integer limit) {
        return ResponseEntity.ok(ApiResponse.success(migration.migrate(TenantContext.getTenantIdAsObject(), after, limit)));
    }
}
