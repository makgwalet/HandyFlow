package za.co.handyflow.platform.tasks.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.shared.*;
import za.co.handyflow.platform.tasks.domain.model.*;
import za.co.handyflow.platform.tasks.domain.repository.*;
import za.co.handyflow.platform.tasks.dto.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
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
    private final JdbcTemplate          jdbc;

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
        List<Task> tasks = taskRepo.findByBoardIdAndDeletedAtIsNullOrderBySortOrderAscCreatedAtAsc(boardId);

        // FIX: pre-load all columns and comment counts in batch — eliminates N+1
        Map<UUID, String> columnNames = columnRepo.findByBoardIdOrderBySortOrderAsc(boardId)
                .stream().collect(Collectors.toMap(TaskColumn::getId, TaskColumn::getName));
        Map<UUID, Integer> commentCounts = commentRepo.countByTaskIds(
                tasks.stream().map(Task::getId).toList());
        Map<UUID, BigDecimal> loggedHours = timeLogRepo.sumHoursByTaskIds(
                tasks.stream().map(Task::getId).toList());

        List<TaskResponse> content = tasks.stream()
                .map(t -> toTaskResponseBatched(t, columnNames, commentCounts, loggedHours))
                .toList();

        int start = (int) Math.min(pageable.getOffset(), content.size());
        int end   = Math.min(start + pageable.getPageSize(), content.size());
        return new PageImpl<>(content.subList(start, end), pageable, content.size());
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
        return toTaskResponse(task, false);
    }

    @Transactional
    public TaskResponse updateTask(TenantId tenantId, UUID taskId, UpdateTaskRequest req) {
        Task task = findTask(tenantId, taskId);
        task.update(req.title(), req.description(), req.priority(),
                req.assigneeId(), req.dueDate(), req.estimatedHours(),
                req.linkedEntityType(), req.linkedEntityId());
        taskRepo.save(task);
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
        return toTaskResponse(task, true);
    }

    @Transactional(readOnly = true)
    public List<TaskCommentResponse> getComments(TenantId tenantId, UUID taskId) {
        findTask(tenantId, taskId);
        return commentRepo.findByTaskIdOrderByCreatedAtAsc(taskId).stream()
                .map(c -> new TaskCommentResponse(c.getId(), c.getAuthorId(), c.getAuthorName(), c.getBody(), c.getCreatedAt()))
                .toList();
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
                                               Map<UUID, BigDecimal> loggedHoursMap) {
        String     columnName   = columnNames.getOrDefault(t.getColumnId(), null);
        String     assigneeName = resolveUserName(t.getAssigneeId());
        BigDecimal logged       = loggedHoursMap.getOrDefault(t.getId(), BigDecimal.ZERO);
        int        commentCount = commentCounts.getOrDefault(t.getId(), 0);
        return new TaskResponse(
                t.getId(), t.getBoardId(), t.getColumnId(), columnName,
                t.getTitle(), t.getDescription(), t.getPriority(), t.getStatus(),
                t.getAssigneeId(), assigneeName, t.getDueDate(), t.isOverdue(),
                t.getEstimatedHours(), logged, t.getSortOrder(),
                t.getLinkedEntityType(), t.getLinkedEntityId(),
                commentCount, List.of(), t.getCreatedAt(), t.getUpdatedAt(), t.getCompletedAt());
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
                commentCount, comments, t.getCreatedAt(), t.getUpdatedAt(), t.getCompletedAt());
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
