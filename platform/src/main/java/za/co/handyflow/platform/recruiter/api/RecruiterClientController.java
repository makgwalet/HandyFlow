package za.co.handyflow.platform.recruiter.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.recruiter.application.internal.RecruiterClientService;
import za.co.handyflow.platform.recruiter.domain.model.RecruiterDashboardRules;
import za.co.handyflow.platform.recruiter.dto.RecruiterClientDtos.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantContext;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/recruiter")
@RequiredArgsConstructor
@Tag(name = "Recruiter", description = "Clients and placements")
public class RecruiterClientController {

    private final RecruiterClientService clients;

    @GetMapping("/clients")
    @PreAuthorize("hasAuthority('RECRUITER_READ')")
    @Operation(summary = "Clients, optionally by search text or status")
    public ResponseEntity<ApiResponse<List<ClientRow>>> list(@RequestParam(required = false) String q, @RequestParam(required = false) String status) {
        return ResponseEntity.ok(ApiResponse.success(clients.clients(TenantContext.getTenantIdAsObject(), q, status)));
    }

    @GetMapping("/clients/{id}")
    @PreAuthorize("hasAuthority('RECRUITER_READ')")
    @Operation(summary = "One client with its contacts and jobs")
    public ResponseEntity<ApiResponse<ClientDetail>> get(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(clients.detail(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/clients")
    @PreAuthorize("hasAuthority('RECRUITER_MANAGE')")
    @Operation(summary = "Add a client")
    public ResponseEntity<ApiResponse<ClientDetail>> create(@RequestBody ClientRequest req) {
        return ResponseEntity.status(201).body(ApiResponse.success("Created", clients.create(TenantContext.getTenantIdAsObject(), req, TenantContext.getCurrentUserId())));
    }

    @PutMapping("/clients/{id}")
    @PreAuthorize("hasAuthority('RECRUITER_MANAGE')")
    @Operation(summary = "Change a client's details or make it active or inactive")
    public ResponseEntity<ApiResponse<ClientDetail>> update(@PathVariable UUID id, @RequestBody ClientRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Saved", clients.update(TenantContext.getTenantIdAsObject(), id, req)));
    }

    @DeleteMapping("/clients/{id}")
    @PreAuthorize("hasAuthority('RECRUITER_ADMIN')")
    @Operation(summary = "Remove a client. Its jobs stay, no longer tied to a client")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        clients.delete(TenantContext.getTenantIdAsObject(), id);
        return ResponseEntity.ok(ApiResponse.success("Removed", null));
    }

    @PostMapping("/clients/{id}/contacts")
    @PreAuthorize("hasAuthority('RECRUITER_MANAGE')")
    @Operation(summary = "Add a contact at a client")
    public ResponseEntity<ApiResponse<ClientDetail>> addContact(@PathVariable UUID id, @RequestBody ContactRequest req) {
        return ResponseEntity.status(201).body(ApiResponse.success("Added", clients.addContact(TenantContext.getTenantIdAsObject(), id, req)));
    }

    @DeleteMapping("/clients/{id}/contacts/{contactId}")
    @PreAuthorize("hasAuthority('RECRUITER_MANAGE')")
    @Operation(summary = "Remove a contact")
    public ResponseEntity<ApiResponse<ClientDetail>> removeContact(@PathVariable UUID id, @PathVariable UUID contactId) {
        return ResponseEntity.ok(ApiResponse.success("Removed", clients.removeContact(TenantContext.getTenantIdAsObject(), id, contactId)));
    }

    @GetMapping("/job-clients")
    @PreAuthorize("hasAuthority('RECRUITER_READ')")
    @Operation(summary = "Which client each job is for")
    public ResponseEntity<ApiResponse<List<JobClient>>> jobClients() {
        return ResponseEntity.ok(ApiResponse.success(clients.jobClients(TenantContext.getTenantIdAsObject())));
    }

    @PutMapping("/jobs/{jobId}/client")
    @PreAuthorize("hasAuthority('RECRUITER_MANAGE')")
    @Operation(summary = "Tie a job to a client, or free it with a null clientId")
    public ResponseEntity<ApiResponse<Void>> setJobClient(@PathVariable UUID jobId, @RequestBody JobClientRequest req) {
        clients.setJobClient(TenantContext.getTenantIdAsObject(), jobId, req.clientId());
        return ResponseEntity.ok(ApiResponse.success("Saved", null));
    }

    @GetMapping("/placements")
    @PreAuthorize("hasAuthority('RECRUITER_READ')")
    @Operation(summary = "Candidates hired onto a client's jobs in the last 7, 30 or 90 days")
    public ResponseEntity<ApiResponse<Placements>> placements(@RequestParam(required = false) Integer days) {
        String problem = RecruiterDashboardRules.daysProblem(days);
        if (problem != null) throw new HandyFlowException(problem, HttpStatus.BAD_REQUEST, "INVALID_PERIOD");
        return ResponseEntity.ok(ApiResponse.success(clients.placements(TenantContext.getTenantIdAsObject(), RecruiterDashboardRules.daysOrDefault(days), Instant.now())));
    }

    @PutMapping("/placements/{applicationId}/fee")
    @PreAuthorize("hasAuthority('RECRUITER_MANAGE')")
    @Operation(summary = "Record or clear what a placement earned")
    public ResponseEntity<ApiResponse<Void>> setFee(@PathVariable UUID applicationId, @RequestBody FeeRequest req) {
        clients.setFee(TenantContext.getTenantIdAsObject(), applicationId, req.fee());
        return ResponseEntity.ok(ApiResponse.success("Saved", null));
    }
}
