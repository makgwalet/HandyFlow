package za.co.handyflow.platform.tasks.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import za.co.handyflow.platform.notifications.application.NotificationRequest;
import za.co.handyflow.platform.notifications.application.Recipient;
import za.co.handyflow.platform.notifications.application.UserRecipientResolver;
import za.co.handyflow.platform.notifications.application.internal.NotificationService;
import za.co.handyflow.platform.notifications.domain.model.NotificationType;
import za.co.handyflow.platform.shared.*;
import za.co.handyflow.platform.tasks.domain.model.*;
import za.co.handyflow.platform.tasks.domain.repository.*;
import za.co.handyflow.platform.tasks.dto.*;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TasksService {

    private final TaskBoardRepository   boardRepo;
    private final TaskColumnRepository  columnRepo;
    private final TaskRepository        taskRepo;
    private final TaskCommentRepository commentRepo;
    private final TaskTimeLogRepository timeLogRepo;
    private final TaskAttachmentRepository attachmentRepo;
    private final TaskChecklistItemRepository checklistRepo;
    private final JdbcTemplate          jdbc;
    private final NotificationService   notificationService;
    private final TasksBoardPdfGenerator boardPdfGenerator;
    private final FileStorageService    fileStorageService;
    private final UserRecipientResolver userRecipientResolver;

    @Value("${tasks.attachments.max-size-mb:20}")
    private long maxAttachmentSizeMb = 20;

    // Basic guard against accidentally serving directly-executable content back out of
    // what's currently local disk (and will eventually be a shared object store) — not a
    // substitute for real malware scanning, which is a separate infra concern (e.g. ClamAV)
    // out of scope for this dev-stage implementation.
    private static final Set<String> BLOCKED_EXTENSIONS = Set.of(
            "exe", "sh", "bat", "cmd", "msi", "jar", "com", "scr", "ps1");

    // ── Summary ───────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public TasksSummaryResponse getSummary(TenantId tenantId, UUID userId) {
        // Use isDoneColumn flag as the source of truth for completed count.
        // The status field may lag when tasks are created directly into non-default columns.
        Long doneByColumn = jdbc.queryForObject(
                "SELECT COUNT(t.id) FROM tasks t " +
                        "JOIN task_columns tc ON tc.id = t.column_id " +
                        "WHERE t.tenant_id = ? AND t.deleted_at IS NULL AND tc.is_done_column = true",
                Long.class, tenantId.getValue());
        long doneCount  = doneByColumn != null ? doneByColumn : 0;

        long todo       = taskRepo.countByStatus(tenantId, "TODO");
        long inProgress = taskRepo.countByStatus(tenantId, "IN_PROGRESS");
        long inReview   = taskRepo.countByStatus(tenantId, "IN_REVIEW");
        long overdue    = taskRepo.countOverdue(tenantId, LocalDate.now());
        long mine       = userId != null ? taskRepo.countMyTasks(tenantId, userId) : 0;
        long total      = todo + inProgress + inReview + doneCount;
        return new TasksSummaryResponse(total, todo, inProgress, inReview, doneCount, overdue, mine);
    }

    // ── Boards ────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<BoardResponse> getBoards(TenantId tenantId) {
        return boardRepo
                .findByTenantIdAndArchivedFalseOrderByIsDefaultDescCreatedAtAsc(tenantId)
                .stream()
                .map(b -> toBoardResponse(b, false))
                .toList();
    }

    @Transactional(readOnly = true)
    public BoardResponse getBoard(TenantId tenantId, UUID boardId) {
        return toBoardResponse(findBoard(tenantId, boardId), false);
    }

    @Transactional
    public BoardResponse createBoard(TenantId tenantId, UUID createdBy, CreateBoardRequest req) {
        TaskBoard board = TaskBoard.create(tenantId, req.name(), req.description(),
                req.color(), false, createdBy);
        boardRepo.save(board);

        List.of(
                TaskColumn.create(board.getId(), tenantId.getValue(), "To Do",       "#94A3B8", 0, false),
                TaskColumn.create(board.getId(), tenantId.getValue(), "In Progress", "#3B82F6", 1, false),
                TaskColumn.create(board.getId(), tenantId.getValue(), "In Review",   "#F59E0B", 2, false),
                TaskColumn.create(board.getId(), tenantId.getValue(), "Done",        "#10B981", 3, true)
        ).forEach(columnRepo::save);

        log.info("Created board={} tenant={}", board.getId(), tenantId);
        return toBoardResponse(board, false);
    }

    @Transactional
    public BoardResponse updateBoard(TenantId tenantId, UUID boardId, CreateBoardRequest req) {
        TaskBoard board = findBoard(tenantId, boardId);
        board.update(req.name(), req.description(), req.color());
        boardRepo.save(board);
        return toBoardResponse(board, false);
    }

    @Transactional
    public void archiveBoard(TenantId tenantId, UUID boardId) {
        TaskBoard board = findBoard(tenantId, boardId);
        if (board.isDefault()) throw new HandyFlowException(
                "The default board cannot be archived", HttpStatus.BAD_REQUEST, "DEFAULT_BOARD");
        board.archive();
        boardRepo.save(board);
    }

    // ── Columns ───────────────────────────────────────────────────────────────

    @Transactional
    public ColumnResponse addColumn(TenantId tenantId, UUID boardId, CreateColumnRequest req) {
        findBoard(tenantId, boardId);
        TaskColumn col = TaskColumn.create(boardId, tenantId.getValue(),
                req.name(), req.color(), req.sortOrder(), req.isDoneColumn());
        columnRepo.save(col);
        return toColumnResponse(col, null);
    }

    @Transactional
    public ColumnResponse updateColumn(TenantId tenantId, UUID boardId, UUID columnId, CreateColumnRequest req) {
        findBoard(tenantId, boardId);
        TaskColumn col = columnRepo.findById(columnId)
                .orElseThrow(() -> new ResourceNotFoundException("Column", columnId.toString()));
        col.update(req.name(), req.color(), req.sortOrder(), req.isDoneColumn());
        columnRepo.save(col);
        return toColumnResponse(col, null);
    }

    @Transactional
    public void deleteColumn(TenantId tenantId, UUID boardId, UUID columnId) {
        findBoard(tenantId, boardId);
        List<TaskColumn> allCols = columnRepo.findByBoardIdOrderBySortOrderAsc(boardId);
        if (allCols.size() <= 1) throw new HandyFlowException(
                "Cannot delete the only column on a board", HttpStatus.BAD_REQUEST, "LAST_COLUMN");
        TaskColumn target = allCols.stream().filter(c -> !c.getId().equals(columnId)).findFirst().orElseThrow();
        jdbc.update("UPDATE tasks SET column_id = ? WHERE column_id = ? AND deleted_at IS NULL",
                target.getId(), columnId);
        columnRepo.deleteById(columnId);
    }

    // ── Tasks ─────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<TaskResponse> getBoardTasks(TenantId tenantId, UUID boardId, Pageable pageable) {
        findBoard(tenantId, boardId);
        List<TaskResponse> content = loadAllTasksForBoard(boardId);

        int start = (int) Math.min(pageable.getOffset(), content.size());
        int end   = Math.min(start + pageable.getPageSize(), content.size());
        return new PageImpl<>(content.subList(start, end), pageable, content.size());
    }

    /**
     * Batch-efficient, unpaginated load of every task on a board — shared by
     * getBoardTasks() (which then pages the result) and exportBoardPdf()
     * (which needs the full set, grouped by column, with no page cutoff).
     */
    private List<TaskResponse> loadAllTasksForBoard(UUID boardId) {
        List<Task> tasks = taskRepo.findByBoardIdAndDeletedAtIsNullOrderBySortOrderAscCreatedAtAsc(boardId);

        // FIX: pre-load all columns and comment counts in batch — eliminates N+1
        Map<UUID, String> columnNames = columnRepo.findByBoardIdOrderBySortOrderAsc(boardId)
                .stream().collect(Collectors.toMap(TaskColumn::getId, TaskColumn::getName));
        Map<UUID, Integer> commentCounts = commentRepo.countByTaskIds(
                tasks.stream().map(Task::getId).toList());
        Map<UUID, BigDecimal> loggedHours = timeLogRepo.sumHoursByTaskIds(
                tasks.stream().map(Task::getId).toList());
        Map<UUID, TaskChecklistItemRepository.ChecklistProgress> checklistProgress = checklistRepo.countProgressByTaskIds(
                tasks.stream().map(Task::getId).toList());

        return tasks.stream()
                .map(t -> toTaskResponseBatched(t, columnNames, commentCounts, loggedHours, checklistProgress))
                .toList();
    }

    // ── Export ────────────────────────────────────────────────────────────────

    /**
     * FIX: "board/task export" gap — no CSV or PDF export of a board's tasks
     * existed at all, despite the audit calling it out as useful for a
     * status report to a client or manager without platform access.
     */
    @Transactional(readOnly = true)
    public byte[] exportBoardPdf(TenantId tenantId, UUID boardId) {
        TaskBoard board = findBoard(tenantId, boardId);
        List<TaskColumn> columns = columnRepo.findByBoardIdOrderBySortOrderAsc(boardId);
        List<TaskResponse> tasks = loadAllTasksForBoard(boardId);
        return boardPdfGenerator.generate(board, columns, tasks, resolveTenantName(tenantId));
    }

    /**
     * FIX: "time-log report" gap — TaskTimeLog captures exactly the
     * billing/timesheet data the audit flagged as missing an export path
     * for, despite the Accountant module having a similar time-tracking →
     * billing pipeline this could plug into. CSV rather than PDF: it's the
     * format that actually imports into a billing/payroll tool, where a
     * PDF status report is meant to be read, not re-parsed.
     */
    @Transactional(readOnly = true)
    public String exportBoardTimesheetCsv(TenantId tenantId, UUID boardId) {
        findBoard(tenantId, boardId);
        List<Task> tasks = taskRepo.findByBoardIdAndDeletedAtIsNullOrderBySortOrderAscCreatedAtAsc(boardId);
        List<UUID> taskIds = tasks.stream().map(Task::getId).toList();
        Map<UUID, String> taskTitles = tasks.stream()
                .collect(Collectors.toMap(Task::getId, Task::getTitle));

        // Batch-loaded (see TaskTimeLogRepository.findByTaskIdInOrderByLoggedDateDesc) —
        // same N+1 avoidance as everywhere else time logs get pulled for a whole board.
        List<TaskTimeLog> logs = taskIds.isEmpty()
                ? List.of()
                : timeLogRepo.findByTaskIdInOrderByLoggedDateDesc(taskIds);

        StringBuilder csv = new StringBuilder();
        csv.append("Date,User,Task,Hours,Description\n");
        for (TaskTimeLog log : logs) {
            csv.append(csvCell(log.getLoggedDate() != null ? log.getLoggedDate().toString() : "")).append(',')
                    .append(csvCell(log.getUserName())).append(',')
                    .append(csvCell(taskTitles.getOrDefault(log.getTaskId(), "Deleted task"))).append(',')
                    .append(csvCell(log.getHours() != null ? log.getHours().toPlainString() : "0")).append(',')
                    .append(csvCell(log.getDescription() != null ? log.getDescription() : ""))
                    .append('\n');
        }
        return csv.toString();
    }

    /** Minimal RFC 4180 quoting — wraps in quotes only when the value needs it. */
    private String csvCell(String value) {
        if (value == null) return "";
        boolean needsQuoting = value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r");
        String escaped = value.replace("\"", "\"\"");
        return needsQuoting ? "\"" + escaped + "\"" : escaped;
    }

    /**
     * ASSUMPTION: guesses a `tenants` table with a `name` column, same
     * defensive try/catch-and-fall-back-to-null style as resolveUserName()
     * below. Verify against the real schema — if it's wrong, the PDF header
     * just omits the tenant line rather than failing the export.
     */
    private String resolveTenantName(TenantId tenantId) {
        try {
            return jdbc.queryForObject("SELECT name FROM tenants WHERE id = ?", String.class, tenantId.getValue());
        } catch (Exception e) {
            return null;
        }
    }

    @Transactional(readOnly = true)
    public List<TaskResponse> getMyTasks(TenantId tenantId, UUID userId) {
        return taskRepo.findMyTasks(tenantId, userId).stream()
                .map(t -> toTaskResponse(t, false)).toList();
    }

    @Transactional(readOnly = true)
    public List<TaskResponse> getOverdueTasks(TenantId tenantId) {
        return taskRepo.findOverdue(tenantId, LocalDate.now()).stream()
                .map(t -> toTaskResponse(t, false)).toList();
    }

    @Transactional(readOnly = true)
    public List<TaskResponse> getLinkedTasks(TenantId tenantId, String entityType, UUID entityId) {
        return taskRepo.findByLinkedEntity(tenantId, entityType, entityId).stream()
                .map(t -> toTaskResponse(t, false)).toList();
    }

    @Transactional(readOnly = true)
    public TaskResponse getTask(TenantId tenantId, UUID taskId) {
        return toTaskResponse(findTask(tenantId, taskId), true);
    }

    @Transactional
    public TaskResponse createTask(TenantId tenantId, UUID boardId, UUID createdBy, CreateTaskRequest req) {
        findBoard(tenantId, boardId);

        UUID columnId;
        if (req.columnId() != null) {
            columnId = req.columnId();
        } else {
            columnId = columnRepo.findFirstByBoardIdOrderBySortOrderAsc(boardId)
                    .map(TaskColumn::getId)
                    .orElseThrow(() -> new HandyFlowException(
                            "Board has no columns", HttpStatus.BAD_REQUEST, "NO_COLUMNS"));
        }

        int sortOrder = taskRepo.findByColumnIdAndDeletedAtIsNullOrderBySortOrderAsc(columnId).size();
        Task task = Task.create(tenantId, boardId, columnId,
                req.title(), req.description(), req.priority(),
                req.assigneeId(), req.dueDate(), req.estimatedHours(), sortOrder,
                req.linkedEntityType(), req.linkedEntityId(), createdBy);
        taskRepo.save(task);
        // Sync status field based on which column the task is created in
        final UUID finalColumnId = columnId;
        columnRepo.findById(finalColumnId).ifPresent(col ->
                jdbc.update("UPDATE tasks SET status = ? WHERE id = ?", deriveStatus(col), task.getId()));
        log.info("Created task={} board={} column={}", task.getId(), boardId, columnId);
        notifyAssignment(tenantId, task, createdBy);
        return toTaskResponse(task, false);
    }

    @Transactional
    public TaskResponse updateTask(TenantId tenantId, UUID taskId, UpdateTaskRequest req, UUID updatedBy) {
        Task task = findTask(tenantId, taskId);
        UUID previousAssignee = task.getAssigneeId();
        task.update(req.title(), req.description(), req.priority(),
                req.assigneeId(), req.dueDate(), req.estimatedHours(),
                req.linkedEntityType(), req.linkedEntityId());
        taskRepo.save(task);
        boolean reassigned = req.assigneeId() != null && !req.assigneeId().equals(previousAssignee);
        if (reassigned) {
            notifyAssignment(tenantId, task, updatedBy);
        }
        return toTaskResponse(task, false);
    }

    @Transactional
    public TaskResponse moveTask(TenantId tenantId, UUID taskId, MoveTaskRequest req) {
        Task task = findTask(tenantId, taskId);
        TaskColumn col = columnRepo.findById(req.columnId())
                .orElseThrow(() -> new ResourceNotFoundException("Column", req.columnId().toString()));
        task.moveToColumn(req.columnId(), col.isDoneColumn());
        taskRepo.save(task);
        // Sync status field with column name so summary counts stay accurate
        jdbc.update("UPDATE tasks SET status = ? WHERE id = ?", deriveStatus(col), taskId);
        return toTaskResponse(task, false);
    }

    /** Derive a status string from the column name for backward compat with status-based queries. */
    private String deriveStatus(TaskColumn col) {
        if (col.isDoneColumn()) return "DONE";
        String upper = col.getName().toUpperCase();
        if (upper.contains("PROGRESS") || upper.contains("DOING"))    return "IN_PROGRESS";
        if (upper.contains("REVIEW")   || upper.contains("TESTING"))  return "IN_REVIEW";
        return "TODO";
    }

    @Transactional
    public TaskResponse completeTask(TenantId tenantId, UUID taskId) {
        Task task = findTask(tenantId, taskId);
        columnRepo.findByBoardIdOrderBySortOrderAsc(task.getBoardId()).stream()
                .filter(TaskColumn::isDoneColumn).findFirst()
                .ifPresent(done -> task.moveToColumn(done.getId(), true));
        task.complete();
        taskRepo.save(task);
        return toTaskResponse(task, false);
    }

    @Transactional
    public void deleteTask(TenantId tenantId, UUID taskId) {
        findTask(tenantId, taskId);
        jdbc.update("UPDATE tasks SET deleted_at = NOW() WHERE id = ?", taskId);
    }

    // ── Comments ──────────────────────────────────────────────────────────────

    @Transactional
    public TaskResponse addComment(TenantId tenantId, UUID taskId,
                                   AddTaskCommentRequest req, UUID authorId, String authorName) {
        Task task = findTask(tenantId, taskId);
        commentRepo.save(TaskComment.create(taskId, tenantId.getValue(), authorId, authorName, req.body()));
        notifyComment(tenantId, task, authorId, authorName);
        return toTaskResponse(task, true);
    }

    @Transactional(readOnly = true)
    public List<TaskCommentResponse> getComments(TenantId tenantId, UUID taskId) {
        findTask(tenantId, taskId);
        return commentRepo.findByTaskIdOrderByCreatedAtAsc(taskId).stream()
                .map(c -> new TaskCommentResponse(c.getId(), c.getAuthorId(), c.getAuthorName(), c.getBody(), c.getCreatedAt()))
                .toList();
    }

    // ── Attachments ───────────────────────────────────────────────────────────
    // FIX: "no file attachments" gap — comments were text-only, with no way to
    // attach a document/screenshot to a task. Files go through FileStorageService
    // (currently LocalFileStorageService — see its Javadoc: this is a dev-stage
    // stand-in until a real object store is wired up, not production-durable yet).

    @Transactional
    public TaskAttachmentResponse addAttachment(TenantId tenantId, UUID taskId, MultipartFile file,
                                                UUID uploaderId, String uploaderName) {
        findTask(tenantId, taskId);
        if (file == null || file.isEmpty()) {
            throw new HandyFlowException("File is required", HttpStatus.BAD_REQUEST, "FILE_REQUIRED");
        }
        long maxBytes = maxAttachmentSizeMb * 1024 * 1024;
        if (file.getSize() > maxBytes) {
            throw new HandyFlowException("File exceeds the " + maxAttachmentSizeMb + "MB limit",
                    HttpStatus.BAD_REQUEST, "FILE_TOO_LARGE");
        }
        String originalName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "file";
        String ext = originalName.contains(".")
                ? originalName.substring(originalName.lastIndexOf('.') + 1).toLowerCase()
                : "";
        if (BLOCKED_EXTENSIONS.contains(ext)) {
            throw new HandyFlowException("File type ." + ext + " is not allowed",
                    HttpStatus.BAD_REQUEST, "FILE_TYPE_BLOCKED");
        }

        try {
            String pathPrefix = "tasks/" + tenantId.getValue() + "/" + taskId;
            String storageKey = fileStorageService.store(pathPrefix, originalName, file.getContentType(), file.getBytes());
            TaskAttachment attachment = TaskAttachment.create(taskId, tenantId.getValue(), originalName,
                    file.getContentType(), file.getSize(), storageKey, uploaderId, uploaderName);
            attachmentRepo.save(attachment);
            return toAttachmentResponse(attachment);
        } catch (IOException e) {
            log.error("Failed to store attachment for task={}: {}", taskId, e.getMessage(), e);
            throw new HandyFlowException("Failed to store attachment", HttpStatus.INTERNAL_SERVER_ERROR, "STORAGE_FAILED");
        }
    }

    @Transactional(readOnly = true)
    public List<TaskAttachmentResponse> getAttachments(TenantId tenantId, UUID taskId) {
        findTask(tenantId, taskId);
        return attachmentRepo.findByTaskIdOrderByCreatedAtDesc(taskId).stream()
                .map(this::toAttachmentResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public DownloadedFile downloadAttachment(TenantId tenantId, UUID taskId, UUID attachmentId) {
        findTask(tenantId, taskId);
        TaskAttachment attachment = attachmentRepo.findByIdAndTaskId(attachmentId, taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Attachment", attachmentId.toString()));
        try {
            byte[] content = fileStorageService.retrieve(attachment.getStorageKey());
            return new DownloadedFile(content, attachment.getFileName(), attachment.getContentType());
        } catch (IOException e) {
            log.error("Failed to retrieve attachment={} for task={}: {}", attachmentId, taskId, e.getMessage(), e);
            throw new HandyFlowException("Failed to retrieve attachment", HttpStatus.INTERNAL_SERVER_ERROR, "STORAGE_FAILED");
        }
    }

    @Transactional
    public void deleteAttachment(TenantId tenantId, UUID taskId, UUID attachmentId) {
        findTask(tenantId, taskId);
        TaskAttachment attachment = attachmentRepo.findByIdAndTaskId(attachmentId, taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Attachment", attachmentId.toString()));
        try {
            fileStorageService.delete(attachment.getStorageKey());
        } catch (IOException e) {
            // Don't let a storage-layer hiccup block removing the DB row — an orphaned
            // blob on disk is a much smaller problem than an attachment the user can't
            // remove from the task at all.
            log.warn("Failed to delete stored file for attachment={} (storageKey={}): {} — deleting DB row anyway",
                    attachmentId, attachment.getStorageKey(), e.getMessage());
        }
        attachmentRepo.deleteById(attachmentId);
    }

    private TaskAttachmentResponse toAttachmentResponse(TaskAttachment a) {
        return new TaskAttachmentResponse(a.getId(), a.getFileName(), a.getContentType(), a.getSizeBytes(),
                a.getUploadedBy(), a.getUploadedByName(), a.getCreatedAt());
    }

    /** Carries downloaded bytes back to the controller alongside the metadata needed for response headers. */
    public record DownloadedFile(byte[] content, String fileName, String contentType) {
    }

    // ── Checklist items ───────────────────────────────────────────────────────
    // FIX: "no subtasks/checklists" gap — flat checkable line items under a task,
    // not a second tier of Kanban-tracked sub-tasks (see TaskChecklistItem's
    // class Javadoc for why that scope line was drawn there).

    @Transactional
    public TaskChecklistItemResponse addChecklistItem(TenantId tenantId, UUID taskId, String text) {
        findTask(tenantId, taskId);
        if (text == null || text.isBlank()) {
            throw new HandyFlowException("Checklist item text is required", HttpStatus.BAD_REQUEST, "TEXT_REQUIRED");
        }
        int sortOrder = checklistRepo.findByTaskIdOrderBySortOrderAsc(taskId).size();
        TaskChecklistItem item = TaskChecklistItem.create(taskId, tenantId.getValue(), text, sortOrder);
        checklistRepo.save(item);
        return toChecklistItemResponse(item);
    }

    @Transactional(readOnly = true)
    public List<TaskChecklistItemResponse> getChecklistItems(TenantId tenantId, UUID taskId) {
        findTask(tenantId, taskId);
        return checklistRepo.findByTaskIdOrderBySortOrderAsc(taskId).stream()
                .map(this::toChecklistItemResponse)
                .toList();
    }

    @Transactional
    public TaskChecklistItemResponse toggleChecklistItem(TenantId tenantId, UUID taskId, UUID itemId, boolean completed) {
        findTask(tenantId, taskId);
        TaskChecklistItem item = checklistRepo.findByIdAndTaskId(itemId, taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Checklist item", itemId.toString()));
        item.setCompleted(completed);
        checklistRepo.save(item);
        return toChecklistItemResponse(item);
    }

    @Transactional
    public TaskChecklistItemResponse updateChecklistItemText(TenantId tenantId, UUID taskId, UUID itemId, String text) {
        findTask(tenantId, taskId);
        TaskChecklistItem item = checklistRepo.findByIdAndTaskId(itemId, taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Checklist item", itemId.toString()));
        item.updateText(text);
        checklistRepo.save(item);
        return toChecklistItemResponse(item);
    }

    @Transactional
    public void deleteChecklistItem(TenantId tenantId, UUID taskId, UUID itemId) {
        findTask(tenantId, taskId);
        TaskChecklistItem item = checklistRepo.findByIdAndTaskId(itemId, taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Checklist item", itemId.toString()));
        checklistRepo.delete(item);
    }

    private TaskChecklistItemResponse toChecklistItemResponse(TaskChecklistItem i) {
        return new TaskChecklistItemResponse(i.getId(), i.getText(), i.isCompleted(), i.getSortOrder(),
                i.getCreatedAt(), i.getCompletedAt());
    }

    // ── Time logging ──────────────────────────────────────────────────────────

    @Transactional
    public TimeLogResponse logTime(TenantId tenantId, UUID taskId,
                                   LogTimeRequest req, UUID userId, String userName) {
        findTask(tenantId, taskId);
        LocalDate logDate = req.loggedDate() != null ? req.loggedDate() : LocalDate.now();
        TaskTimeLog entry = TaskTimeLog.create(taskId, tenantId.getValue(),
                userId, userName, req.hours(), req.description(), logDate);
        timeLogRepo.save(entry);
        return toTimeLogResponse(entry);
    }

    @Transactional(readOnly = true)
    public List<TimeLogResponse> getTimeLogs(TenantId tenantId, UUID taskId) {
        findTask(tenantId, taskId);
        return timeLogRepo.findByTaskIdOrderByLoggedDateDesc(taskId)
                .stream().map(this::toTimeLogResponse).toList();
    }

    // ── User resolution ───────────────────────────────────────────────────────

    public String resolveUserName(UUID userId) {
        if (userId == null) return "Team Member";
        try {
            String name = jdbc.queryForObject(
                    "SELECT first_name || ' ' || last_name FROM users WHERE id = ?", String.class, userId);
            return name != null ? name : "Team Member";
        } catch (Exception e) { return "Team Member"; }
    }

    /**
     * FIX: backs the assignee picker. Tasks were previously created with a free-text
     * assigneeName that never resolved to a real assigneeId, silently breaking
     * getMyTasks()/findMyTasks() for anyone whose typed name didn't match a real account.
     * This gives the frontend a real list of {id, name} to bind the picker to instead.
     */
    @Transactional(readOnly = true)
    public List<UserOptionResponse> getAssignableUsers(TenantId tenantId) {
        return jdbc.query(
                "SELECT id, first_name || ' ' || last_name AS name FROM users " +
                        "WHERE tenant_id = ? ORDER BY first_name, last_name",
                (rs, rowNum) -> new UserOptionResponse(
                        UUID.fromString(rs.getString("id")), rs.getString("name")),
                tenantId.getValue());
    }

    // ── Notifications ────────────────────────────────────────────────────────

    /**
     +     * FIX: previously ran its own jdbc query directly, annotated
     +     * @Transactional(readOnly = true) — but called only via self-invocation
     +     * from notifyAssignment()/notifyComment() below, where that annotation
     +     * is silently ignored (Spring's proxy never sees a call through `this`).
     +     * A failed lookup there would poison whatever transaction was already
     +     * open on the calling create/update method — the exact failure mode
     +     * that surfaced as a Postgres 25P02 in Expenses' approveClaim(). Also
     +     * had no tenant_id scoping at all. Now delegates to a genuinely
     +     * separate bean (UserRecipientResolver, REQUIRES_NEW, tenant-scoped)
     +     * so a failure here is isolated and this class no longer runs the
     +     * query itself.
     +     */
    public Recipient resolveRecipient(TenantId tenantId, UUID userId) {
                return userRecipientResolver.resolveUser(tenantId, userId).orElse(null);
            }

    /** Fires TASK_ASSIGNED — skipped if unassigned, or if the actor assigned it to themselves. */
    private void notifyAssignment(TenantId tenantId, Task task, UUID actorId) {
        if (task.getAssigneeId() == null || task.getAssigneeId().equals(actorId)) return;
        Recipient recipient = resolveRecipient(tenantId, task.getAssigneeId());
        if (recipient == null) return;

        notificationService.send(NotificationRequest.builder()
                .tenantId(tenantId)
                .type(NotificationType.TASK_ASSIGNED)
                .title("Task assigned to you: " + task.getTitle())
                .message((resolveUserName(actorId)) + " assigned you a task: \"" + task.getTitle() + "\""
                        + (task.getDueDate() != null ? " — due " + task.getDueDate() : "") + ".")
                .actionUrl("/tasks")
                .sourceModule("tasks")
                .sourceEntityId(task.getId().toString())
                .recipient(recipient)
                .build());
    }

    /** Fires TASK_COMMENT_ADDED to the assignee — skipped if unassigned, or if they're the commenter. */
    private void notifyComment(TenantId tenantId, Task task, UUID authorId, String authorName) {
        if (task.getAssigneeId() == null || task.getAssigneeId().equals(authorId)) return;
        Recipient recipient = resolveRecipient(tenantId, task.getAssigneeId());
        if (recipient == null) return;

        notificationService.send(NotificationRequest.builder()
                .tenantId(tenantId)
                .type(NotificationType.TASK_COMMENT_ADDED)
                .title(authorName + " commented on: " + task.getTitle())
                .message(authorName + " commented on \"" + task.getTitle() + "\".")
                .actionUrl("/tasks")
                .sourceModule("tasks")
                .sourceEntityId(task.getId().toString())
                .recipient(recipient)
                .build());
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private TaskBoard findBoard(TenantId tenantId, UUID boardId) {
        return boardRepo.findByIdAndTenantId(boardId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Board", boardId.toString()));
    }

    private Task findTask(TenantId tenantId, UUID taskId) {
        return taskRepo.findByIdAndTenantId(taskId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Task", taskId.toString()));
    }

    private BoardResponse toBoardResponse(TaskBoard b, boolean includeTasks) {
        List<ColumnResponse> columns = columnRepo.findByBoardIdOrderBySortOrderAsc(b.getId()).stream()
                .map(col -> {
                    List<TaskResponse> tasks = includeTasks
                            ? taskRepo.findByColumnIdAndDeletedAtIsNullOrderBySortOrderAsc(col.getId())
                            .stream().map(t -> toTaskResponse(t, false)).toList()
                            : null;
                    return toColumnResponse(col, tasks);
                }).toList();
        return new BoardResponse(b.getId(), b.getName(), b.getDescription(),
                b.getColor(), b.isDefault(), b.isArchived(), columns, b.getCreatedAt());
    }

    private ColumnResponse toColumnResponse(TaskColumn c, List<TaskResponse> tasks) {
        return new ColumnResponse(c.getId(), c.getName(), c.getColor(), c.getSortOrder(), c.isDoneColumn(), tasks);
    }

    /**
     * Batch-efficient mapper — resolves column names and counts from pre-loaded maps.
     * Used in getBoardTasks() to avoid N+1 queries per card.
     */
    private TaskResponse toTaskResponseBatched(Task t,
                                               Map<UUID, String> columnNames,
                                               Map<UUID, Integer> commentCounts,
                                               Map<UUID, BigDecimal> loggedHoursMap,
                                               Map<UUID, TaskChecklistItemRepository.ChecklistProgress> checklistProgressMap) {
        String     columnName   = columnNames.getOrDefault(t.getColumnId(), null);
        String     assigneeName = resolveUserName(t.getAssigneeId());
        BigDecimal logged       = loggedHoursMap.getOrDefault(t.getId(), BigDecimal.ZERO);
        int        commentCount = commentCounts.getOrDefault(t.getId(), 0);
        TaskChecklistItemRepository.ChecklistProgress checklist = checklistProgressMap
                .getOrDefault(t.getId(), new TaskChecklistItemRepository.ChecklistProgress(0, 0));
        return new TaskResponse(
                t.getId(), t.getBoardId(), t.getColumnId(), columnName,
                t.getTitle(), t.getDescription(), t.getPriority(), t.getStatus(),
                t.getAssigneeId(), assigneeName, t.getDueDate(), t.isOverdue(),
                t.getEstimatedHours(), logged, t.getSortOrder(),
                t.getLinkedEntityType(), t.getLinkedEntityId(),
                commentCount, checklist.total(), checklist.completed(),
                List.of(), t.getCreatedAt(), t.getUpdatedAt(), t.getCompletedAt());
    }

    /**
     * Single-task mapper with optional comments inclusion.
     * Used for task detail, create, update, move — not for board list loads.
     */
    private TaskResponse toTaskResponse(Task t, boolean includeComments) {
        String     columnName   = fetchColumnName(t.getColumnId());
        String     assigneeName = resolveUserName(t.getAssigneeId());
        BigDecimal logged       = timeLogRepo.sumHoursByTask(t.getId());
        int        commentCount = commentRepo.countByTask(t.getId());
        TaskChecklistItemRepository.ChecklistProgress checklist = checklistRepo
                .countProgressByTaskIds(List.of(t.getId()))
                .getOrDefault(t.getId(), new TaskChecklistItemRepository.ChecklistProgress(0, 0));

        List<TaskCommentResponse> comments = includeComments
                ? commentRepo.findByTaskIdOrderByCreatedAtAsc(t.getId()).stream()
                .map(c -> new TaskCommentResponse(c.getId(), c.getAuthorId(), c.getAuthorName(), c.getBody(), c.getCreatedAt()))
                .toList()
                : List.of();

        return new TaskResponse(
                t.getId(), t.getBoardId(), t.getColumnId(), columnName,
                t.getTitle(), t.getDescription(), t.getPriority(), t.getStatus(),
                t.getAssigneeId(), assigneeName, t.getDueDate(), t.isOverdue(),
                t.getEstimatedHours(), logged, t.getSortOrder(),
                t.getLinkedEntityType(), t.getLinkedEntityId(),
                commentCount, checklist.total(), checklist.completed(),
                comments, t.getCreatedAt(), t.getUpdatedAt(), t.getCompletedAt());
    }

    private String fetchColumnName(UUID columnId) {
        if (columnId == null) return null;
        try { return jdbc.queryForObject("SELECT name FROM task_columns WHERE id = ?", String.class, columnId); }
        catch (Exception e) { return null; }
    }

    private TimeLogResponse toTimeLogResponse(TaskTimeLog l) {
        return new TimeLogResponse(l.getId(), l.getUserId(), l.getUserName(),
                l.getHours(), l.getDescription(), l.getLoggedDate(), l.getCreatedAt());
    }
}