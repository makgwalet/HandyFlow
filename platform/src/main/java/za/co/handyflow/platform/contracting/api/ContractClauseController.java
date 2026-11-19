package za.co.handyflow.platform.contracting.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.contracting.application.internal.ContractClauseService;
import za.co.handyflow.platform.contracting.dto.ContractClauseDtos.*;
import za.co.handyflow.platform.contracting.dto.ContractRedlineDtos.ProposalView;
import za.co.handyflow.platform.documents.application.DocumentsFacade;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/** The clause library. The module entitlement check covers /api/v1/contracts (ModuleEntitlementPaths). */
@RestController
@RequestMapping("/api/v1/contracts")
@RequiredArgsConstructor
@Tag(name = "Contract clause library")
public class ContractClauseController {

    private static final String READ = "hasAnyAuthority('CONTRACTS_READ','CONTRACTS_TEMPLATES','CONTRACTS_MANAGE','CONTRACTS_ADMIN')";
    private static final String AUTHOR = "hasAnyAuthority('CONTRACTS_TEMPLATES','CONTRACTS_MANAGE')";
    private static final String INSERT = "hasAnyAuthority('CONTRACTS_MANAGE','CONTRACTS_ADMIN')";

    private final ContractClauseService service;

    @GetMapping("/clauses")
    @PreAuthorize(READ)
    @Operation(summary = "Clauses in the library, optionally by category or status")
    public ResponseEntity<ApiResponse<List<ClauseResponse>>> list(@RequestParam(required = false) String category, @RequestParam(required = false) String status) {
        return ResponseEntity.ok(ApiResponse.success("Clauses", service.list(TenantContext.getTenantIdAsObject(), category, status)));
    }

    @GetMapping("/clauses/{id}")
    @PreAuthorize(READ)
    @Operation(summary = "One clause")
    public ResponseEntity<ApiResponse<ClauseResponse>> get(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Clause", service.get(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/clauses")
    @PreAuthorize(AUTHOR)
    @Operation(summary = "Add a draft clause")
    public ResponseEntity<ApiResponse<ClauseResponse>> create(@Valid @RequestBody ClauseRequest req) {
        return ResponseEntity.status(201).body(ApiResponse.success("Clause created", service.create(TenantContext.getTenantIdAsObject(), req, TenantContext.getCurrentUserId())));
    }

    @PutMapping("/clauses/{id}")
    @PreAuthorize(AUTHOR)
    @Operation(summary = "Edit a draft clause")
    public ResponseEntity<ApiResponse<ClauseResponse>> edit(@PathVariable UUID id, @Valid @RequestBody ClauseRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Clause updated", service.edit(TenantContext.getTenantIdAsObject(), id, req)));
    }

    @PostMapping("/clauses/{id}/approve")
    @PreAuthorize(AUTHOR)
    @Operation(summary = "Record the legal reviewer's approval of this exact wording")
    public ResponseEntity<ApiResponse<ClauseResponse>> approve(@PathVariable UUID id, @Valid @RequestBody ApproveClauseRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Clause approved", service.approve(TenantContext.getTenantIdAsObject(), id, req)));
    }

    @PostMapping(value = "/clauses/{id}/evidence", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(AUTHOR)
    @Operation(summary = "Attach the reviewer's signed-off review to an approved or published clause. A second file is stored as a new version")
    public ResponseEntity<ApiResponse<ClauseResponse>> uploadEvidence(@PathVariable UUID id, @RequestParam("file") MultipartFile file) throws IOException {
        return ResponseEntity.ok(ApiResponse.success("Evidence stored", service.uploadEvidence(TenantContext.getTenantIdAsObject(), id,
                new DocumentsFacade.FileUpload(file.getOriginalFilename(), file.getContentType(), file.getBytes()), TenantContext.getCurrentUserId())));
    }

    @GetMapping("/clauses/{id}/evidence")
    @PreAuthorize(READ)
    @Operation(summary = "Download the evidence file of a clause review (the latest version, or the numbered one). The download is audited")
    public ResponseEntity<byte[]> downloadEvidence(@PathVariable UUID id, @RequestParam(required = false) Integer version) {
        DocumentsFacade.Download file = service.downloadEvidence(TenantContext.getTenantIdAsObject(), id, version, TenantContext.getCurrentUserId());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.fileName().replace("\"", "") + "\"")
                .header("X-Content-Type-Options", "nosniff")
                .contentType(MediaType.parseMediaType(file.contentType()))
                .body(file.content());
    }

    @PostMapping("/clauses/{id}/return-to-draft")
    @PreAuthorize(AUTHOR)
    @Operation(summary = "Send an approved clause back to draft (the review details are cleared)")
    public ResponseEntity<ApiResponse<ClauseResponse>> returnToDraft(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Clause returned to draft", service.returnToDraft(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/clauses/{id}/publish")
    @PreAuthorize(AUTHOR)
    @Operation(summary = "Publish an approved clause. The previous published version of it is superseded")
    public ResponseEntity<ApiResponse<ClauseResponse>> publish(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Clause published", service.publish(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/clauses/{id}/new-version")
    @PreAuthorize(AUTHOR)
    @Operation(summary = "Start a new draft version of a published clause")
    public ResponseEntity<ApiResponse<ClauseResponse>> newVersion(@PathVariable UUID id) {
        return ResponseEntity.status(201).body(ApiResponse.success("New version created", service.newVersion(TenantContext.getTenantIdAsObject(), id, TenantContext.getCurrentUserId())));
    }

    @PostMapping("/clauses/{id}/withdraw")
    @PreAuthorize(AUTHOR)
    @Operation(summary = "Withdraw an approved or published clause, with the reason")
    public ResponseEntity<ApiResponse<ClauseResponse>> withdraw(@PathVariable UUID id, @Valid @RequestBody WithdrawClauseRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Clause withdrawn", service.withdraw(TenantContext.getTenantIdAsObject(), id, req.reason())));
    }

    @PostMapping("/{contractId}/clauses/insert")
    @PreAuthorize(INSERT)
    @Operation(summary = "Propose inserting a published clause into a draft contract. The proposal is reviewed like any other change")
    public ResponseEntity<ApiResponse<ProposalView>> insert(@PathVariable UUID contractId, @Valid @RequestBody InsertClauseRequest req) {
        return ResponseEntity.status(201).body(ApiResponse.success("Clause proposed",
                service.insert(TenantContext.getTenantIdAsObject(), contractId, req, TenantContext.getCurrentUserId())));
    }

    @GetMapping("/{contractId}/clauses")
    @PreAuthorize(READ)
    @Operation(summary = "Library clauses that went into this contract, and whether each is still current")
    public ResponseEntity<ApiResponse<List<ClauseUsageResponse>>> usage(@PathVariable UUID contractId) {
        return ResponseEntity.ok(ApiResponse.success("Clauses in contract", service.usage(TenantContext.getTenantIdAsObject(), contractId)));
    }
}
