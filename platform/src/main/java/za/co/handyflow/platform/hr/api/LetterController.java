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
import za.co.handyflow.platform.hr.application.internal.LetterService;
import za.co.handyflow.platform.hr.dto.LetterDtos.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.UUID;

/**
 * The Letter & Document Generation Centre. HR prepares, edits, issues and voids letters; an employee sees the letters issued to them,
 * downloads the PDF and confirms receipt. The service checks the permission again for each action.
 */
@RestController
@RequestMapping("/api/v1/hr/letters")
@RequiredArgsConstructor
@Tag(name = "HR letters", description = "Letter templates, letters, PDFs and acknowledgements")
public class LetterController {

    private static final String SIGNED_IN = "isAuthenticated()";
    private static final String READ = "hasAnyAuthority('HR_LETTER_READ','HR_LETTER_MANAGE','HR_LETTER_ISSUE')";
    private static final String MANAGE = "hasAnyAuthority('HR_LETTER_MANAGE')";
    private static final String ISSUE = "hasAnyAuthority('HR_LETTER_ISSUE')";

    private final LetterService letters;

    // ── Templates ─────────────────────────────────────────────────────────────────────────────────

    @GetMapping("/templates")
    @PreAuthorize(READ)
    @Operation(summary = "The company's templates and the merge fields they may use. A new company gets the standard set")
    public ResponseEntity<ApiResponse<Templates>> templates(Authentication auth) {
        return ok(letters.templates(tenant(), HrAccess.held(auth)));
    }

    @PostMapping("/templates")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<TemplateView>> createTemplate(@RequestBody TemplateRequest req, Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Template created", letters.createTemplate(tenant(), req, user(auth), HrAccess.held(auth))));
    }

    @PutMapping("/templates/{id}")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<TemplateView>> saveTemplate(@PathVariable UUID id, @RequestBody TemplateRequest req, Authentication auth) {
        return ok(letters.saveTemplate(tenant(), id, req, user(auth), HrAccess.held(auth)));
    }

    @PostMapping("/templates/{id}/restore")
    @PreAuthorize(MANAGE)
    @Operation(summary = "Put a standard template back to its standard wording")
    public ResponseEntity<ApiResponse<TemplateView>> restoreTemplate(@PathVariable UUID id, Authentication auth) {
        return ok(letters.restoreTemplate(tenant(), id, user(auth), HrAccess.held(auth)));
    }

    // ── Letters, for HR ───────────────────────────────────────────────────────────────────────────

    @GetMapping
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<List<Row>>> list(@RequestParam(required = false) UUID employeeId, @RequestParam(required = false) UUID caseId,
                                                       @RequestParam(required = false) String status, Authentication auth) {
        return ok(letters.list(tenant(), employeeId, caseId, status, HrAccess.held(auth), user(auth)));
    }

    @GetMapping("/{id}")
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<Detail>> get(@PathVariable UUID id, Authentication auth) {
        return ok(letters.get(tenant(), id, HrAccess.held(auth), user(auth)));
    }

    @PostMapping("/prepare")
    @PreAuthorize(ISSUE)
    @Operation(summary = "Fill a template in for an employee (and a case) and keep it as a draft")
    public ResponseEntity<ApiResponse<Detail>> prepare(@RequestBody PrepareRequest req, Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Draft prepared", letters.prepare(tenant(), req, user(auth), HrAccess.held(auth))));
    }

    @PostMapping("/bulk/prepare")
    @PreAuthorize(ISSUE)
    @Operation(summary = "Make one general or employment template for many employees, each as its own draft")
    public ResponseEntity<ApiResponse<BulkPrepared>> prepareBulk(@RequestBody BulkPrepareRequest req, Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Drafts prepared", letters.prepareBulk(tenant(), req, user(auth), HrAccess.held(auth))));
    }

    @PostMapping("/bulk/issue")
    @PreAuthorize(ISSUE)
    @Operation(summary = "Issue many drafts. Drafts with gaps are reported and left as drafts")
    public ResponseEntity<ApiResponse<BulkIssued>> issueBulk(@RequestBody BulkIssueRequest req, Authentication auth) {
        return ok(letters.issueBulk(tenant(), req, user(auth), HrAccess.held(auth)));
    }

    @PutMapping("/{id}/draft")
    @PreAuthorize(ISSUE)
    public ResponseEntity<ApiResponse<Detail>> updateDraft(@PathVariable UUID id, @RequestBody DraftRequest req, Authentication auth) {
        return ok(letters.updateDraft(tenant(), id, req, user(auth), HrAccess.held(auth)));
    }

    @PostMapping("/{id}/issue")
    @PreAuthorize(ISSUE)
    @Operation(summary = "Make the PDF, keep it on the employee's file and give the letter to the employee")
    public ResponseEntity<ApiResponse<Detail>> issue(@PathVariable UUID id, Authentication auth) {
        return ok(letters.issue(tenant(), id, user(auth), HrAccess.held(auth)));
    }

    @PostMapping("/{id}/void")
    @PreAuthorize(ISSUE)
    public ResponseEntity<ApiResponse<Detail>> voidLetter(@PathVariable UUID id, @RequestBody VoidRequest req, Authentication auth) {
        return ok(letters.voidLetter(tenant(), id, req, user(auth), HrAccess.held(auth)));
    }

    @GetMapping("/{id}/pdf")
    @PreAuthorize(READ)
    public ResponseEntity<byte[]> pdf(@PathVariable UUID id, Authentication auth) {
        return file(letters.download(tenant(), id, HrAccess.held(auth), user(auth)));
    }

    // ── For the employee ──────────────────────────────────────────────────────────────────────────

    @GetMapping("/mine")
    @PreAuthorize(SIGNED_IN)
    public ResponseEntity<ApiResponse<List<Mine>>> mine(Authentication auth) {
        return ok(letters.mine(tenant(), user(auth)));
    }

    @GetMapping("/mine/{id}")
    @PreAuthorize(SIGNED_IN)
    public ResponseEntity<ApiResponse<Mine>> mineOne(@PathVariable UUID id, Authentication auth) {
        return ok(letters.mineOne(tenant(), id, user(auth)));
    }

    @GetMapping("/mine/{id}/pdf")
    @PreAuthorize(SIGNED_IN)
    public ResponseEntity<byte[]> minePdf(@PathVariable UUID id, Authentication auth) {
        return file(letters.minePdf(tenant(), id, user(auth)));
    }

    @PostMapping("/mine/{id}/acknowledge")
    @PreAuthorize(SIGNED_IN)
    @Operation(summary = "Confirm the letter was received. Receiving is not agreeing")
    public ResponseEntity<ApiResponse<Mine>> acknowledge(@PathVariable UUID id, Authentication auth) {
        return ok(letters.acknowledge(tenant(), id, user(auth)));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────────────────────────

    private static ResponseEntity<byte[]> file(LetterService.Pdf pdf) {
        return ResponseEntity.ok()
                .header("Content-Type", "application/pdf")
                .header("Content-Disposition", "attachment; filename=\"" + pdf.fileName() + "\"")
                .body(pdf.bytes());
    }

    private static <T> ResponseEntity<ApiResponse<T>> ok(T body) { return ResponseEntity.ok(ApiResponse.success(body)); }

    private static za.co.handyflow.platform.shared.TenantId tenant() { return TenantContext.getTenantIdAsObject(); }

    private static UUID user(Authentication auth) {
        if (auth == null) return null;
        try { return UUID.fromString(auth.getName()); } catch (Exception e) { return null; }
    }
}
