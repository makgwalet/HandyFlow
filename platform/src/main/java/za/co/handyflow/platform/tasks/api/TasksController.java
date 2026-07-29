package za.co.handyflow.platform.tasks.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.tasks.application.internal.TasksService;
import za.co.handyflow.platform.tasks.dto.*;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tasks")
@RequiredArgsConstructor
@Tag(name = "Tasks", description = "Kanban boards, task management and time tracking")
public class TasksController {

    private final TasksService tasksService;

    // ── Summary ───────────────────────────────────────────────────────────────

    @GetMapping("/summary")
    @PreAuthorize("hasAuthority('TASKS_READ')")
    @Operation(summary = "Tasks dashboard — counts by status, overdue, my tasks")
    public ResponseEntity<ApiResponse<TasksSummaryResponse>> getSummary() {
        return ResponseEntity.ok(ApiResponse.success(
                tasksService.getSummary(
                        TenantContext.getTenantIdAsObject(),
                        TenantContext.getCurrentUserId())));
    }

    // ── Users (for assignee picker) ─────────────────────────────────────────────

    @GetMapping("/assignable-users")
    @PreAuthorize("hasAuthority('TASKS_READ')")
    @Operation(summary = "Users that can be assigned tasks — backs the assignee picker")
    public ResponseEntity<ApiResponse<List<UserOptionResponse>>> getAssignableUsers() {
        return ResponseEntity.ok(ApiResponse.success(
                tasksService.getAssignableUsers(TenantContext.getTenantIdAsObject())));
    }

    // ── Boards ────────────────────────────────────────────────────────────────

    @GetMapping("/boards")
    @PreAuthorize("hasAuthority('TASKS_READ')")
    @Operation(summary = "List all active boards (columns embedded, tasks excluded)")
    public ResponseEntity<ApiResponse<List<BoardResponse>>> getBoards() {
        return ResponseEntity.ok(ApiResponse.success(
                tasksService.getBoards(TenantContext.getTenantIdAsObject())));
    }

    @GetMapping("/boards/{id}")
    @PreAuthorize("hasAuthority('TASKS_READ')")
    @Operation(summary = "Get a board with its columns (tasks fetched separately)")
    public ResponseEntity<ApiResponse<BoardResponse>> getBoard(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(
                tasksService.getBoard(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/boards")
    @PreAuthorize("hasAuthority('TASKS_ADMIN')")
    @Operation(summary = "Create a new board — seeds To Do, In Progress, In Review, Done columns automatically")
    public ResponseEntity<ApiResponse<BoardResponse>> createBoard(
            @Valid @RequestBody CreateBoardRequest req) {
        return ResponseEntity.status(201).body(ApiResponse.success("Board created",
                tasksService.createBoard(
                        TenantContext.getTenantIdAsObject(),
                        TenantContext.getCurrentUserId(), req)));
    }

    @PutMapping("/boards/{id}")
    @PreAuthorize("hasAuthority('TASKS_ADMIN')")
    @Operation(summary = "Update board name, description or color")
    public ResponseEntity<ApiResponse<BoardResponse>> updateBoard(
            @PathVariable UUID id,
            @Valid @RequestBody CreateBoardRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Board updated",
                tasksService.updateBoard(TenantContext.getTenantIdAsObject(), id, req)));
    }

    @PostMapping("/boards/{id}/archive")
    @PreAuthorize("hasAuthority('TASKS_ADMIN')")
    @Operation(summary = "Archive a board (soft delete)")
    public ResponseEntity<ApiResponse<Void>> archiveBoard(@PathVariable UUID id) {
        tasksService.archiveBoard(TenantContext.getTenantIdAsObject(), id);
        return ResponseEntity.ok(ApiResponse.success("Board archived", null));
    }

    // ── Export ────────────────────────────────────────────────────────────────

    @GetMapping("/boards/{boardId}/export/pdf")
    @PreAuthorize("hasAuthority('TASKS_READ')")
    @Operation(summary = "Export a board's tasks as a PDF status report — grouped by column, with a summary strip")
    public ResponseEntity<byte[]> exportBoardPdf(@PathVariable UUID boardId) {
        byte[] pdf = tasksService.exportBoardPdf(TenantContext.getTenantIdAsObject(), boardId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"board-" + boardId + "-status-report.pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    @GetMapping("/boards/{boardId}/export/timesheet")
    @PreAuthorize("hasAuthority('TASKS_READ')")
    @Operation(summary = "Export a board's logged time as a CSV timesheet — one row per time-log entry")
    public ResponseEntity<byte[]> exportBoardTimesheet(@PathVariable UUID boardId) {
        String csv = tasksService.exportBoardTimesheetCsv(TenantContext.getTenantIdAsObject(), boardId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"board-" + boardId + "-timesheet.csv\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(csv.getBytes(StandardCharsets.UTF_8));
    }

    // ── Columns ───────────────────────────────────────────────────────────────

    @PostMapping("/boards/{boardId}/columns")
    @PreAuthorize("hasAuthority('TASKS_ADMIN')")
    @Operation(summary = "Add a column to a board")
    public ResponseEntity<ApiResponse<ColumnResponse>> addColumn(
            @PathVariable UUID boardId,
            @Valid @RequestBody CreateColumnRequest req) {
        return ResponseEntity.status(201).body(ApiResponse.success("Column added",
                tasksService.addColumn(TenantContext.getTenantIdAsObject(), boardId, req)));
    }

    @PutMapping("/boards/{boardId}/columns/{columnId}")
    @PreAuthorize("hasAuthority('TASKS_ADMIN')")
    @Operation(summary = "Update a column's name, color, sort order or done-flag")
    public ResponseEntity<ApiResponse<ColumnResponse>> updateColumn(
            @PathVariable UUID boardId,
            @PathVariable UUID columnId,
            @Valid @RequestBody CreateColumnRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Column updated",
                tasksService.updateColumn(
                        TenantContext.getTenantIdAsObject(), boardId, columnId, req)));
    }

    @DeleteMapping("/boards/{boardId}/columns/{columnId}")
    @PreAuthorize("hasAuthority('TASKS_ADMIN')")
    @Operation(summary = "Delete a column — tasks are moved to the first column automatically")
    public ResponseEntity<ApiResponse<Void>> deleteColumn(
            @PathVariable UUID boardId,
            @PathVariable UUID columnId) {
        tasksService.deleteColumn(TenantContext.getTenantIdAsObject(), boardId, columnId);
        return ResponseEntity.ok(ApiResponse.success("Column deleted", null));
    }

    // ── Tasks ─────────────────────────────────────────────────────────────────

    /**
     * Paginated task list for a board — columns are already embedded in the board
     * response so the frontend only needs one extra call per board.
     *
     * Query params: page (0-based), size, sort (field,asc|desc)
     */
    @GetMapping("/boards/{boardId}/tasks")
    @PreAuthorize("hasAuthority('TASKS_READ')")
    @Operation(summary = "Paginated task list for a board")
    public ResponseEntity<ApiResponse<Page<TaskResponse>>> getBoardTasks(
            @PathVariable UUID boardId,
            @RequestParam(defaultValue = "0")   int page,
            @RequestParam(defaultValue = "200")  int size,
            @RequestParam(defaultValue = "sortOrder,asc") String sort) {

        String[] parts     = sort.split(",");
        String   field     = parts[0];
        Sort.Direction dir = parts.length > 1 && parts[1].equalsIgnoreCase("desc")
                ? Sort.Direction.DESC : Sort.Direction.ASC;
        Pageable pageable  = PageRequest.of(page, Math.min(size, 500), Sort.by(dir, field));

        return ResponseEntity.ok(ApiResponse.success(
                tasksService.getBoardTasks(
                        TenantContext.getTenantIdAsObject(), boardId, pageable)));
    }

    @GetMapping("/my")
    @PreAuthorize("hasAuthority('TASKS_READ')")
    @Operation(summary = "Tasks assigned to the currently authenticated user")
    public ResponseEntity<ApiResponse<List<TaskResponse>>> getMyTasks() {
        return ResponseEntity.ok(ApiResponse.success(
                tasksService.getMyTasks(
                        TenantContext.getTenantIdAsObject(),
                        TenantContext.getCurrentUserId())));
    }

    @GetMapping("/overdue")
    @PreAuthorize("hasAuthority('TASKS_READ')")
    @Operation(summary = "All overdue tasks across all boards")
    public ResponseEntity<ApiResponse<List<TaskResponse>>> getOverdueTasks() {
        return ResponseEntity.ok(ApiResponse.success(
                tasksService.getOverdueTasks(TenantContext.getTenantIdAsObject())));
    }

    @GetMapping("/linked/{entityType}/{entityId}")
    @PreAuthorize("hasAuthority('TASKS_READ')")
    @Operation(summary = "Tasks linked to a specific entity — e.g. QUOTE, INVOICE, CUSTOMER")
    public ResponseEntity<ApiResponse<List<TaskResponse>>> getLinkedTasks(
            @PathVariable String entityType,
            @PathVariable UUID   entityId) {
        return ResponseEntity.ok(ApiResponse.success(
                tasksService.getLinkedTasks(
                        TenantContext.getTenantIdAsObject(), entityType, entityId)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('TASKS_READ')")
    @Operation(summary = "Task detail — includes comments and time-log summary")
    public ResponseEntity<ApiResponse<TaskResponse>> getTask(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(
                tasksService.getTask(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/boards/{boardId}/tasks")
    @PreAuthorize("hasAuthority('TASKS_MANAGE')")
    @Operation(summary = "Create a task on a board")
    public ResponseEntity<ApiResponse<TaskResponse>> createTask(
            @PathVariable UUID boardId,
            @Valid @RequestBody CreateTaskRequest req) {
        return ResponseEntity.status(201).body(ApiResponse.success("Task created",
                tasksService.createTask(
                        TenantContext.getTenantIdAsObject(), boardId,
                        TenantContext.getCurrentUserId(), req)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('TASKS_MANAGE')")
    @Operation(summary = "Update task details (title, description, priority, assignee, dates, hours)")
    public ResponseEntity<ApiResponse<TaskResponse>> updateTask(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateTaskRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Task updated",
                tasksService.updateTask(
                        TenantContext.getTenantIdAsObject(), id, req,
                        TenantContext.getCurrentUserId())));
    }

    /**
     * Canonical move endpoint — POST /{id}/move with { columnId, sortOrder }.
     * The legacy PATCH /{id} with just { columnId } is also supported below
     * for backward compatibility with older clients.
     */
    @PostMapping("/{id}/move")
    @PreAuthorize("hasAuthority('TASKS_MANAGE')")
    @Operation(summary = "Move task to a different column (Kanban drag-and-drop)")
    public ResponseEntity<ApiResponse<TaskResponse>> moveTask(
            @PathVariable UUID id,
            @Valid @RequestBody MoveTaskRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Task moved",
                tasksService.moveTask(TenantContext.getTenantIdAsObject(), id, req)));
    }

    /** Backward-compat shim: PATCH /{id} with { columnId } → delegates to moveTask */
    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('TASKS_MANAGE')")
    @Operation(summary = "Partial update — currently supports { columnId } for quick column moves")
    public ResponseEntity<ApiResponse<TaskResponse>> patchTask(
            @PathVariable UUID id,
            @RequestBody MoveTaskRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Task moved",
                tasksService.moveTask(TenantContext.getTenantIdAsObject(), id, req)));
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("hasAuthority('TASKS_MANAGE')")
    @Operation(summary = "Mark task as complete and move it to the done column")
    public ResponseEntity<ApiResponse<TaskResponse>> completeTask(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Task completed",
                tasksService.completeTask(TenantContext.getTenantIdAsObject(), id)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('TASKS_MANAGE')")
    @Operation(summary = "Soft-delete a task")
    public ResponseEntity<ApiResponse<Void>> deleteTask(@PathVariable UUID id) {
        tasksService.deleteTask(TenantContext.getTenantIdAsObject(), id);
        return ResponseEntity.ok(ApiResponse.success("Task deleted", null));
    }

    // ── Comments ──────────────────────────────────────────────────────────────

    @PostMapping("/{id}/comments")
    @PreAuthorize("hasAuthority('TASKS_MANAGE')")
    @Operation(summary = "Add a comment to a task")
    public ResponseEntity<ApiResponse<TaskResponse>> addComment(
            @PathVariable UUID id,
            @Valid @RequestBody AddTaskCommentRequest req) {
        UUID   userId = TenantContext.getCurrentUserId();
        String name   = tasksService.resolveUserName(userId);
        return ResponseEntity.status(201).body(ApiResponse.success("Comment added",
                tasksService.addComment(
                        TenantContext.getTenantIdAsObject(), id, req, userId, name)));
    }

    @GetMapping("/{id}/comments")
    @PreAuthorize("hasAuthority('TASKS_READ')")
    @Operation(summary = "List all comments on a task")
    public ResponseEntity<ApiResponse<List<TaskCommentResponse>>> getComments(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(
                tasksService.getComments(TenantContext.getTenantIdAsObject(), id)));
    }

    // ── Attachments ───────────────────────────────────────────────────────────

    @PostMapping(value = "/{id}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('TASKS_MANAGE')")
    @Operation(summary = "Upload a file attachment to a task")
    public ResponseEntity<ApiResponse<TaskAttachmentResponse>> uploadAttachment(
            @PathVariable UUID id,
            @RequestParam("file") MultipartFile file) {
        UUID   userId = TenantContext.getCurrentUserId();
        String name   = tasksService.resolveUserName(userId);
        return ResponseEntity.status(201).body(ApiResponse.success("Attachment uploaded",
                tasksService.addAttachment(
                        TenantContext.getTenantIdAsObject(), id, file, userId, name)));
    }

    @GetMapping("/{id}/attachments")
    @PreAuthorize("hasAuthority('TASKS_READ')")
    @Operation(summary = "List all attachments on a task")
    public ResponseEntity<ApiResponse<List<TaskAttachmentResponse>>> getAttachments(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(
                tasksService.getAttachments(TenantContext.getTenantIdAsObject(), id)));
    }

    @GetMapping("/{id}/attachments/{attachmentId}/download")
    @PreAuthorize("hasAuthority('TASKS_READ')")
    @Operation(summary = "Download a task attachment")
    public ResponseEntity<byte[]> downloadAttachment(
            @PathVariable UUID id,
            @PathVariable UUID attachmentId) {
        TasksService.DownloadedFile file = tasksService.downloadAttachment(
                TenantContext.getTenantIdAsObject(), id, attachmentId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.fileName() + "\"")
                .contentType(file.contentType() != null
                        ? MediaType.parseMediaType(file.contentType())
                        : MediaType.APPLICATION_OCTET_STREAM)
                .body(file.content());
    }

    @DeleteMapping("/{id}/attachments/{attachmentId}")
    @PreAuthorize("hasAuthority('TASKS_MANAGE')")
    @Operation(summary = "Delete a task attachment")
    public ResponseEntity<ApiResponse<Void>> deleteAttachment(
            @PathVariable UUID id,
            @PathVariable UUID attachmentId) {
        tasksService.deleteAttachment(TenantContext.getTenantIdAsObject(), id, attachmentId);
        return ResponseEntity.ok(ApiResponse.success("Attachment deleted", null));
    }

    // ── Checklist items ───────────────────────────────────────────────────────

    @PostMapping("/{id}/checklist-items")
    @PreAuthorize("hasAuthority('TASKS_MANAGE')")
    @Operation(summary = "Add a checklist item to a task")
    public ResponseEntity<ApiResponse<TaskChecklistItemResponse>> addChecklistItem(
            @PathVariable UUID id,
            @Valid @RequestBody AddChecklistItemRequest req) {
        return ResponseEntity.status(201).body(ApiResponse.success("Checklist item added",
                tasksService.addChecklistItem(TenantContext.getTenantIdAsObject(), id, req.text())));
    }

    @GetMapping("/{id}/checklist-items")
    @PreAuthorize("hasAuthority('TASKS_READ')")
    @Operation(summary = "List checklist items on a task")
    public ResponseEntity<ApiResponse<List<TaskChecklistItemResponse>>> getChecklistItems(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(
                tasksService.getChecklistItems(TenantContext.getTenantIdAsObject(), id)));
    }

    @PatchMapping("/{id}/checklist-items/{itemId}/toggle")
    @PreAuthorize("hasAuthority('TASKS_MANAGE')")
    @Operation(summary = "Mark a checklist item complete or incomplete")
    public ResponseEntity<ApiResponse<TaskChecklistItemResponse>> toggleChecklistItem(
            @PathVariable UUID id,
            @PathVariable UUID itemId,
            @Valid @RequestBody ToggleChecklistItemRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Checklist item updated",
                tasksService.toggleChecklistItem(TenantContext.getTenantIdAsObject(), id, itemId, req.completed())));
    }

    @PutMapping("/{id}/checklist-items/{itemId}")
    @PreAuthorize("hasAuthority('TASKS_MANAGE')")
    @Operation(summary = "Rename a checklist item")
    public ResponseEntity<ApiResponse<TaskChecklistItemResponse>> updateChecklistItem(
            @PathVariable UUID id,
            @PathVariable UUID itemId,
            @Valid @RequestBody UpdateChecklistItemRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Checklist item updated",
                tasksService.updateChecklistItemText(TenantContext.getTenantIdAsObject(), id, itemId, req.text())));
    }

    @DeleteMapping("/{id}/checklist-items/{itemId}")
    @PreAuthorize("hasAuthority('TASKS_MANAGE')")
    @Operation(summary = "Delete a checklist item")
    public ResponseEntity<ApiResponse<Void>> deleteChecklistItem(
            @PathVariable UUID id,
            @PathVariable UUID itemId) {
        tasksService.deleteChecklistItem(TenantContext.getTenantIdAsObject(), id, itemId);
        return ResponseEntity.ok(ApiResponse.success("Checklist item deleted", null));
    }

    // ── Time logging ──────────────────────────────────────────────────────────

    @PostMapping("/{id}/time")
    @PreAuthorize("hasAuthority('TASKS_MANAGE')")
    @Operation(summary = "Log time spent on a task")
    public ResponseEntity<ApiResponse<TimeLogResponse>> logTime(
            @PathVariable UUID id,
            @Valid @RequestBody LogTimeRequest req) {
        UUID   userId = TenantContext.getCurrentUserId();
        String name   = tasksService.resolveUserName(userId);
        return ResponseEntity.status(201).body(ApiResponse.success("Time logged",
                tasksService.logTime(
                        TenantContext.getTenantIdAsObject(), id, req, userId, name)));
    }

    @GetMapping("/{id}/time")
    @PreAuthorize("hasAuthority('TASKS_READ')")
    @Operation(summary = "Get all time logs for a task (most recent first)")
    public ResponseEntity<ApiResponse<List<TimeLogResponse>>> getTimeLogs(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(
                tasksService.getTimeLogs(TenantContext.getTenantIdAsObject(), id)));
    }
}