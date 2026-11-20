package za.co.handyflow.platform.legalpractice.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.legalpractice.application.internal.LpTaskService;
import za.co.handyflow.platform.legalpractice.dto.TaskDtos.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

/** Matter tasks and workflow templates. */
@RestController("legalPracticeTaskController")
@RequestMapping("/api/v1/legal-practice/tasks")
@RequiredArgsConstructor
@Tag(name = "Legal Practice - Tasks", description = "Matter tasks and workflow templates")
public class LpTaskController {

    private static final String READ = "hasAnyAuthority('LEGALPRACTICE_READ','LEGALPRACTICE_MANAGE','LEGALPRACTICE_TASK_WRITE','LEGALPRACTICE_WORKFLOW_MANAGE','LEGALPRACTICE_ADMIN')";
    private static final String WRITE = "hasAnyAuthority('LEGALPRACTICE_TASK_WRITE','LEGALPRACTICE_ADMIN')";
    private static final String WORKFLOW = "hasAnyAuthority('LEGALPRACTICE_WORKFLOW_MANAGE','LEGALPRACTICE_ADMIN')";

    private final LpTaskService service;
    private final FeatureGuard featureGuard;

    private static <T> ResponseEntity<ApiResponse<T>> ok(T body) { return ResponseEntity.ok(ApiResponse.success(body)); }
    private static TenantId tenant() { return TenantContext.getTenantIdAsObject(); }
    private static UUID me() { return TenantContext.getCurrentUserId(); }
    private static String name() { return TenantContext.getCurrentUserName(); }

    // ---- tasks ---------------------------------------------------------------------------------------------------------

    @GetMapping
    @PreAuthorize(READ)
    @Operation(summary = "Tasks across the firm, or only the caller's (leaves out matters the caller is walled from)")
    public ResponseEntity<ApiResponse<Page<TaskSummary>>> firm(@RequestParam(defaultValue = "false") boolean mine, @RequestParam(required = false) String search,
                                                               @RequestParam(required = false) String status, @RequestParam(defaultValue = "false") boolean includeClosed,
                                                               @RequestParam(defaultValue = "false") boolean overdueOnly, @PageableDefault(size = 20) Pageable pageable) {
        featureGuard.requireModule("legalpractice");
        return ok(service.firmList(tenant(), me(), mine, search, status, includeClosed, overdueOnly, pageable));
    }

    @GetMapping("/mine/overdue")
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<MyTasks>> myOverdue() {
        featureGuard.requireModule("legalpractice");
        return ok(service.myOverdue(tenant(), me()));
    }

    @GetMapping("/matters/{matterId}")
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<List<TaskSummary>>> forMatter(@PathVariable UUID matterId) {
        featureGuard.requireModule("legalpractice");
        return ok(service.forMatter(tenant(), matterId));
    }

    @GetMapping("/{id}")
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<TaskDetail>> get(@PathVariable UUID id) {
        featureGuard.requireModule("legalpractice");
        return ok(service.get(tenant(), id));
    }

    @PostMapping
    @PreAuthorize(WRITE)
    public ResponseEntity<ApiResponse<TaskDetail>> create(@RequestBody CreateTaskRequest req) {
        featureGuard.requireModule("legalpractice");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Task added", service.create(tenant(), req, me(), name())));
    }

    @PutMapping("/{id}")
    @PreAuthorize(WRITE)
    public ResponseEntity<ApiResponse<TaskDetail>> edit(@PathVariable UUID id, @RequestBody EditTaskRequest req) {
        featureGuard.requireModule("legalpractice");
        return ok(service.edit(tenant(), id, req, me(), name()));
    }

    @PostMapping("/{id}/assign")
    @PreAuthorize(WRITE)
    public ResponseEntity<ApiResponse<TaskDetail>> assign(@PathVariable UUID id, @RequestBody AssignRequest req) {
        featureGuard.requireModule("legalpractice");
        return ok(service.assign(tenant(), id, req, me(), name()));
    }

    @PostMapping("/{id}/start")
    @PreAuthorize(WRITE)
    public ResponseEntity<ApiResponse<TaskDetail>> start(@PathVariable UUID id) {
        featureGuard.requireModule("legalpractice");
        return ok(service.start(tenant(), id, me(), name()));
    }

    @PostMapping("/{id}/stop")
    @PreAuthorize(WRITE)
    public ResponseEntity<ApiResponse<TaskDetail>> stop(@PathVariable UUID id) {
        featureGuard.requireModule("legalpractice");
        return ok(service.stop(tenant(), id, me(), name()));
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize(WRITE)
    public ResponseEntity<ApiResponse<TaskDetail>> complete(@PathVariable UUID id) {
        featureGuard.requireModule("legalpractice");
        return ok(service.complete(tenant(), id, me(), name()));
    }

    @PostMapping("/{id}/reopen")
    @PreAuthorize(WRITE)
    public ResponseEntity<ApiResponse<TaskDetail>> reopen(@PathVariable UUID id, @RequestBody ReasonRequest req) {
        featureGuard.requireModule("legalpractice");
        return ok(service.reopen(tenant(), id, req, me(), name()));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize(WRITE)
    public ResponseEntity<ApiResponse<TaskDetail>> cancel(@PathVariable UUID id, @RequestBody ReasonRequest req) {
        featureGuard.requireModule("legalpractice");
        return ok(service.cancel(tenant(), id, req, me(), name()));
    }

    // ---- workflows -----------------------------------------------------------------------------------------------------

    @GetMapping("/workflows")
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<List<TemplateSummary>>> templates(@RequestParam(defaultValue = "false") boolean includeOff) {
        featureGuard.requireModule("legalpractice");
        return ok(service.templates(tenant(), includeOff));
    }

    @GetMapping("/workflows/{id}")
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<TemplateDetail>> template(@PathVariable UUID id) {
        featureGuard.requireModule("legalpractice");
        return ok(service.template(tenant(), id));
    }

    @PostMapping("/workflows")
    @PreAuthorize(WORKFLOW)
    public ResponseEntity<ApiResponse<TemplateDetail>> createTemplate(@RequestBody TemplateRequest req) {
        featureGuard.requireModule("legalpractice");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Workflow added", service.createTemplate(tenant(), req, me(), name())));
    }

    @PutMapping("/workflows/{id}")
    @PreAuthorize(WORKFLOW)
    public ResponseEntity<ApiResponse<TemplateDetail>> updateTemplate(@PathVariable UUID id, @RequestBody TemplateRequest req) {
        featureGuard.requireModule("legalpractice");
        return ok(service.updateTemplate(tenant(), id, req, me(), name()));
    }

    @PostMapping("/workflows/{id}/turn-off")
    @PreAuthorize(WORKFLOW)
    public ResponseEntity<ApiResponse<TemplateDetail>> off(@PathVariable UUID id) {
        featureGuard.requireModule("legalpractice");
        return ok(service.setTemplateActive(tenant(), id, false));
    }

    @PostMapping("/workflows/{id}/turn-on")
    @PreAuthorize(WORKFLOW)
    public ResponseEntity<ApiResponse<TemplateDetail>> on(@PathVariable UUID id) {
        featureGuard.requireModule("legalpractice");
        return ok(service.setTemplateActive(tenant(), id, true));
    }

    @PostMapping("/workflows/{id}/apply")
    @PreAuthorize("hasAnyAuthority('LEGALPRACTICE_WORKFLOW_MANAGE','LEGALPRACTICE_TASK_WRITE','LEGALPRACTICE_ADMIN')")
    @Operation(summary = "Apply a workflow to a matter: creates one task per step")
    public ResponseEntity<ApiResponse<ApplyResult>> apply(@PathVariable UUID id, @RequestBody ApplyRequest req) {
        featureGuard.requireModule("legalpractice");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Workflow applied", service.apply(tenant(), id, req, me(), name())));
    }

    @GetMapping("/workflows/matters/{matterId}")
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<List<RunResponse>>> runs(@PathVariable UUID matterId) {
        featureGuard.requireModule("legalpractice");
        return ok(service.runsForMatter(tenant(), matterId));
    }
}
