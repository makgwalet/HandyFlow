package za.co.handyflow.platform.legalpractice.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.legalpractice.application.internal.LpLitigationService;
import za.co.handyflow.platform.legalpractice.dto.LitigationDtos.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Court cases, hearings and court orders on matters. Concluding or reopening a case needs its own permission. */
@RestController
@RequestMapping("/api/v1/legal-practice/litigation")
@RequiredArgsConstructor
@Tag(name = "Legal Practice - Litigation", description = "Court cases, hearings, court orders")
public class LpLitigationController {

    private static final String READ = "hasAnyAuthority('LEGALPRACTICE_READ','LEGALPRACTICE_MANAGE','LEGALPRACTICE_LITIGATION_MANAGE','LEGALPRACTICE_LITIGATION_CONCLUDE','LEGALPRACTICE_ADMIN')";
    private static final String WRITE = "hasAnyAuthority('LEGALPRACTICE_LITIGATION_MANAGE','LEGALPRACTICE_ADMIN')";
    private static final String CONCLUDE = "hasAnyAuthority('LEGALPRACTICE_LITIGATION_CONCLUDE','LEGALPRACTICE_ADMIN')";

    private final LpLitigationService service;
    private final FeatureGuard featureGuard;

    private static <T> ResponseEntity<ApiResponse<T>> ok(T body) { return ResponseEntity.ok(ApiResponse.success(body)); }

    private static java.util.UUID me() { return TenantContext.getCurrentUserId(); }

    private static String myName() { return TenantContext.getCurrentUserName(); }

    @GetMapping
    @PreAuthorize(READ)
    @Operation(summary = "Court cases across the firm (leaves out matters the caller is walled from)")
    public ResponseEntity<ApiResponse<Page<CaseSummary>>> firm(@RequestParam(required = false) String search, @RequestParam(required = false) String stage,
                                                               @RequestParam(defaultValue = "false") boolean includeConcluded, @PageableDefault(size = 20) Pageable pageable) {
        featureGuard.requireModule("legalpractice");
        return ok(service.firmList(TenantContext.getTenantIdAsObject(), search, stage, includeConcluded, pageable));
    }

    @GetMapping("/roll")
    @PreAuthorize(READ)
    @Operation(summary = "Scheduled hearings in a date range across the firm")
    public ResponseEntity<ApiResponse<List<RollRow>>> roll(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                           @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        featureGuard.requireModule("legalpractice");
        return ok(service.courtRoll(TenantContext.getTenantIdAsObject(), from, to));
    }

    @GetMapping("/matters/{matterId}")
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<MatterLitigation>> forMatter(@PathVariable UUID matterId) {
        featureGuard.requireModule("legalpractice");
        return ok(service.forMatter(TenantContext.getTenantIdAsObject(), matterId));
    }

    @GetMapping("/{id}")
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<CaseDetail>> get(@PathVariable UUID id) {
        featureGuard.requireModule("legalpractice");
        return ok(service.get(TenantContext.getTenantIdAsObject(), id));
    }

    @PostMapping
    @PreAuthorize(WRITE)
    public ResponseEntity<ApiResponse<CaseDetail>> open(@RequestBody OpenCaseRequest req) {
        featureGuard.requireModule("legalpractice");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Case opened", service.open(TenantContext.getTenantIdAsObject(), req, me(), myName())));
    }

    @PutMapping("/{id}")
    @PreAuthorize(WRITE)
    public ResponseEntity<ApiResponse<CaseDetail>> edit(@PathVariable UUID id, @RequestBody EditCaseRequest req) {
        featureGuard.requireModule("legalpractice");
        return ok(service.edit(TenantContext.getTenantIdAsObject(), id, req, me(), myName()));
    }

    @PostMapping("/{id}/stage")
    @PreAuthorize(WRITE)
    public ResponseEntity<ApiResponse<CaseDetail>> stage(@PathVariable UUID id, @RequestBody StageRequest req) {
        featureGuard.requireModule("legalpractice");
        return ok(service.changeStage(TenantContext.getTenantIdAsObject(), id, req, me(), myName()));
    }

    @PostMapping("/{id}/conclude")
    @PreAuthorize(CONCLUDE)
    public ResponseEntity<ApiResponse<CaseDetail>> conclude(@PathVariable UUID id, @RequestBody ConcludeRequest req) {
        featureGuard.requireModule("legalpractice");
        return ok(service.conclude(TenantContext.getTenantIdAsObject(), id, req, me(), myName()));
    }

    @PostMapping("/{id}/reopen")
    @PreAuthorize(CONCLUDE)
    public ResponseEntity<ApiResponse<CaseDetail>> reopen(@PathVariable UUID id, @RequestBody ReopenRequest req) {
        featureGuard.requireModule("legalpractice");
        return ok(service.reopen(TenantContext.getTenantIdAsObject(), id, req, me(), myName()));
    }

    @PostMapping("/{id}/hearings")
    @PreAuthorize(WRITE)
    public ResponseEntity<ApiResponse<CaseDetail>> schedule(@PathVariable UUID id, @RequestBody HearingRequest req) {
        featureGuard.requireModule("legalpractice");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Hearing set", service.scheduleHearing(TenantContext.getTenantIdAsObject(), id, req, me(), myName())));
    }

    @PostMapping("/hearings/{hearingId}/postpone")
    @PreAuthorize(WRITE)
    public ResponseEntity<ApiResponse<CaseDetail>> postpone(@PathVariable UUID hearingId, @RequestBody PostponeRequest req) {
        featureGuard.requireModule("legalpractice");
        return ok(service.postponeHearing(TenantContext.getTenantIdAsObject(), hearingId, req, me(), myName()));
    }

    @PostMapping("/hearings/{hearingId}/held")
    @PreAuthorize(WRITE)
    public ResponseEntity<ApiResponse<CaseDetail>> held(@PathVariable UUID hearingId, @RequestBody HeldRequest req) {
        featureGuard.requireModule("legalpractice");
        return ok(service.holdHearing(TenantContext.getTenantIdAsObject(), hearingId, req, me(), myName()));
    }

    @PostMapping("/hearings/{hearingId}/cancel")
    @PreAuthorize(WRITE)
    public ResponseEntity<ApiResponse<CaseDetail>> cancel(@PathVariable UUID hearingId, @RequestBody CancelRequest req) {
        featureGuard.requireModule("legalpractice");
        return ok(service.cancelHearing(TenantContext.getTenantIdAsObject(), hearingId, req, me(), myName()));
    }

    @PostMapping("/{id}/orders")
    @PreAuthorize(WRITE)
    public ResponseEntity<ApiResponse<CaseDetail>> order(@PathVariable UUID id, @RequestBody OrderRequest req) {
        featureGuard.requireModule("legalpractice");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Order recorded", service.recordOrder(TenantContext.getTenantIdAsObject(), id, req, me(), myName())));
    }

    @PostMapping("/orders/{orderId}/complied")
    @PreAuthorize(WRITE)
    public ResponseEntity<ApiResponse<CaseDetail>> complied(@PathVariable UUID orderId, @RequestBody ComplyRequest req) {
        featureGuard.requireModule("legalpractice");
        return ok(service.markComplied(TenantContext.getTenantIdAsObject(), orderId, req, me(), myName()));
    }
}
