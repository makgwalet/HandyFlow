package za.co.handyflow.platform.projects.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.contracting.application.ContractSummary;
import za.co.handyflow.platform.projects.application.internal.ProjectActivityService;
import za.co.handyflow.platform.projects.application.internal.ProjectContractService;
import za.co.handyflow.platform.projects.dto.ActivityItem;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/projects/{projectId}")
@RequiredArgsConstructor
@Tag(name = "Project activity and contracts", description = "Recent activity on a project, and the contracts linked to it")
public class ProjectActivityContractsController {

    private final ProjectActivityService activity;
    private final ProjectContractService contracts;

    public record LinkContractRequest(@NotNull(message = "Choose a contract") UUID contractId) {}
    public record DraftContractRequest(@Size(max = 300) String title, @NotBlank(message = "Choose a contract type") String contractType,
                                       BigDecimal valueAmount, LocalDate startDate, LocalDate endDate) {}

    @GetMapping("/activity")
    @PreAuthorize("hasAuthority('PM_READ')")
    @Operation(summary = "Recent activity, newest first. Page with before=<at of the last item>.")
    public ResponseEntity<ApiResponse<List<ActivityItem>>> activity(@PathVariable UUID projectId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant before,
            @RequestParam(defaultValue = "50") int limit) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                activity.feed(TenantContext.getTenantIdAsObject().getValue(), projectId, before, limit)));
    }

    @GetMapping("/contracts")
    @PreAuthorize("hasAuthority('PM_READ')")
    @Operation(summary = "Contracts linked to this project")
    public ResponseEntity<ApiResponse<List<ContractSummary>>> list(@PathVariable UUID projectId) {
        return ResponseEntity.ok(ApiResponse.success("Success", contracts.forProject(TenantContext.getTenantIdAsObject(), projectId)));
    }

    @GetMapping("/contracts/linkable")
    @PreAuthorize("hasAuthority('PM_WRITE') and hasAnyAuthority('USER_READ','USER_UPDATE','CONTRACTS_MANAGE','CONTRACTS_ADMIN')")
    @Operation(summary = "Contracts not linked to anything yet, to pick from")
    public ResponseEntity<ApiResponse<List<ContractSummary>>> linkable(@PathVariable UUID projectId) {
        return ResponseEntity.ok(ApiResponse.success("Success", contracts.linkable(TenantContext.getTenantIdAsObject())));
    }

    @PostMapping("/contracts/link")
    @PreAuthorize("hasAuthority('PM_WRITE')")
    @Operation(summary = "Link an existing contract to this project")
    public ResponseEntity<ApiResponse<ContractSummary>> link(@PathVariable UUID projectId, @Valid @RequestBody LinkContractRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Contract linked", contracts.link(TenantContext.getTenantIdAsObject(), projectId, req.contractId())));
    }

    @PostMapping("/contracts")
    @PreAuthorize("hasAuthority('PM_WRITE') and hasAnyAuthority('USER_UPDATE','CONTRACTS_MANAGE')")
    @Operation(summary = "Start a draft contract for this project from a Contracting template; finish and send it in Contracting")
    public ResponseEntity<ApiResponse<ContractSummary>> draft(@PathVariable UUID projectId, @Valid @RequestBody DraftContractRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Draft contract started",
                contracts.draft(TenantContext.getTenantIdAsObject(), projectId, req.title(), req.contractType(), req.valueAmount(), req.startDate(), req.endDate())));
    }

    @DeleteMapping("/contracts/{contractId}")
    @PreAuthorize("hasAuthority('PM_WRITE')")
    @Operation(summary = "Unlink a contract from this project (the contract itself is not changed)")
    public ResponseEntity<ApiResponse<Void>> unlink(@PathVariable UUID projectId, @PathVariable UUID contractId) {
        contracts.unlink(TenantContext.getTenantIdAsObject(), projectId, contractId);
        return ResponseEntity.ok(ApiResponse.success("Contract unlinked", null));
    }
}
