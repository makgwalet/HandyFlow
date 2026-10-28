package za.co.handyflow.platform.recruiter.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.recruiter.application.internal.RecruiterImportService;
import za.co.handyflow.platform.recruiter.dto.RecruiterImportDtos.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/recruiter/jobs/{jobId}/import")
@RequiredArgsConstructor
@Tag(name = "Recruiter", description = "Add candidates to a job from a CSV file")
public class RecruiterImportController {

    private final RecruiterImportService importer;

    @PostMapping("/check")
    @PreAuthorize("hasAuthority('RECRUITER_MANAGE')")
    @Operation(summary = "Check a CSV and say what an import would do; saves nothing")
    public ResponseEntity<ApiResponse<ImportResult>> check(@PathVariable UUID jobId, @RequestBody ImportRequest req) {
        return ResponseEntity.ok(ApiResponse.success(importer.importCandidates(TenantContext.getTenantIdAsObject(), jobId, req, true, TenantContext.getCurrentUserName())));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('RECRUITER_MANAGE')")
    @Operation(summary = "Add the candidates in a CSV to the job")
    public ResponseEntity<ApiResponse<ImportResult>> importCsv(@PathVariable UUID jobId, @RequestBody ImportRequest req) {
        return ResponseEntity.ok(ApiResponse.success(importer.importCandidates(TenantContext.getTenantIdAsObject(), jobId, req, false, TenantContext.getCurrentUserName())));
    }
}
