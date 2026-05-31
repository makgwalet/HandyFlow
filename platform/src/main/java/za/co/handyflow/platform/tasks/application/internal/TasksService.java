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
import java.util.UUID;

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
        long todo       = taskRepo.countByStatus(tenantId, "TODO");
        long inProgress = taskRepo.countByStatus(tenantId, "IN_PROGRESS");
        long inReview   = taskRepo.countByStatus(tenantId, "IN_REVIEW");
        long done       = taskRepo.countByStatus(tenantId, "DONE");
        long overdue    = taskRepo.findOverdue(tenantId, LocalDate.now()).size();
        long mine       = taskRepo.findMyTasks(tenantId, userId).size();
        long total      = todo + inProgress + inReview + done;
        return new TasksSummaryResponse(total, todo, inProgress, inReview, done, overdue, mine);
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

        // Seed industry-standard default columns
        List.of(
                TaskColumn.create(board.getId(), tenantId.getValue(), "To Do",        "#94A3B8", 0, false),
                TaskColumn.create(board.getId(), tenantId.getValue(), "In Progress",  "#3B82F6", 1, false),
                TaskColumn.create(board.getId(), tenantId.getValue(), "In Review",    "#F59E0B", 2, false),
                TaskColumn.create(board.getId(), tenantId.getValue(), "Done",         "#10B981", 3, true)
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
        if (board.isDefault()) {
            throw new HandyFlowException(
                    "The default board cannot be archived", HttpStatus.BAD_REQUEST, "DEFAULT_BOARD");
        }
        board.archive();
        boardRepo.save(board);
        log.info("Archived board={} tenant={}", boardId, tenantId);
    }

    // ── Columns ───────────────────────────────────────────────────────────────

    @Transactional
    public ColumnResponse addColumn(TenantId tenantId, UUID boardId, CreateColumnRequest req) {
        findBoard(tenantId, boardId); // verify ownership
        TaskColumn col = TaskColumn.create(boardId, tenantId.getValue(),
                req.name(), req.color(), req.sortOrder(), req.isDoneColumn());
        columnRepo.save(col);
        log.info("Added column={} to board={}", col.getId(), boardId);
        return toColumnResponse(col, null);
    }

    @Transactional
    public ColumnResponse updateColumn(TenantId tenantId, UUID boardId,
                                       UUID columnId, CreateColumnRequest req) {
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

        // Must not delete the only column
        List<TaskColumn> allCols = columnRepo.findByBoardIdOrderBySortOrderAsc(boardId);
        if (allCols.size() <= 1) {
            throw new HandyFlowException(
                    "Cannot delete the only column on a board", HttpStatus.BAD_REQUEST, "LAST_COLUMN");
        }

        // Move tasks to the first column that isn't the one being deleted
        TaskColumn target = allCols.stream()
                .filter(c -> !c.getId().equals(columnId))
                .findFirst()
                .orElseThrow();

        jdbc.update(
                "UPDATE tasks SET column_id = ? WHERE column_id = ? AND deleted_at IS NULL",
                target.getId(), columnId);

        columnRepo.deleteById(columnId);
        log.info("Deleted column={} from board={} tasks moved to column={}", columnId, boardId, target.getId());
    }

    // ── Tasks ─────────────────────────────────────────────────────────────────

    /**
     * Paginated tasks for a board — replaces the old list-all pattern.
     * Supports standard Spring Pageable (page, size, sort).
     */
    @Transactional(readOnly = true)
    public Page<TaskResponse> getBoardTasks(TenantId tenantId, UUID boardId, Pageable pageable) {
        findBoard(tenantId, boardId); // verify ownership
        // Fetch columns once so we can map columnId → name efficiently
        List<TaskColumn> cols = columnRepo.findByBoardIdOrderBySortOrderAsc(boardId);

        // Build a flat list from all columns and apply pageable manually.
        // Replace with a proper repo method if task volume grows significantly.
        List<Task> all = cols.stream()
                .flatMap(c -> taskRepo
                        .findByColumnIdAndDeletedAtIsNullOrderBySortOrderAsc(c.getId())
                        .stream())
                .toList();

        int start = (int) pageable.getOffset();
        int end   = Math.min(start + pageable.getPageSize(), all.size());
        List<Task> page = start >= all.size() ? List.of() : all.subList(start, end);

        List<TaskResponse> content = page.stream()
                .map(t -> toTaskResponse(t, false))
                .toList();

        return new PageImpl<>(content, pageable, all.size());
    }

    @Transactional(readOnly = true)
    public List<TaskResponse> getMyTasks(TenantId tenantId, UUID userId) {
        return taskRepo.findMyTasks(tenantId, userId)
                .stream().map(t -> toTaskResponse(t, false)).toList();
    }

    @Transactional(readOnly = true)
    public List<TaskResponse> getOverdueTasks(TenantId tenantId) {
        return taskRepo.findOverdue(tenantId, LocalDate.now())
                .stream().map(t -> toTaskResponse(t, false)).toList();
    }

    @Transactional(readOnly = true)
    public List<TaskResponse> getLinkedTasks(TenantId tenantId, String entityType, UUID entityId) {
        return taskRepo.findByLinkedEntity(tenantId, entityType, entityId)
                .stream().map(t -> toTaskResponse(t, false)).toList();
    }

    @Transactional(readOnly = true)
    public TaskResponse getTask(TenantId tenantId, UUID taskId) {
        return toTaskResponse(findTask(tenantId, taskId), true);
    }

    @Transactional
    public TaskResponse createTask(TenantId tenantId, UUID boardId,
                                   UUID createdBy, CreateTaskRequest req) {
        findBoard(tenantId, boardId);

        // Use the requested column, fall back to the first column on the board
        UUID columnId;
        if (req.columnId() != null) {
            columnRepo.findById(req.columnId())
                    .orElseThrow(() -> new ResourceNotFoundException("Column", req.columnId().toString()));
            columnId = req.columnId();
        } else {
            columnId = columnRepo.findFirstByBoardIdOrderBySortOrderAsc(boardId)
                    .map(TaskColumn::getId)
                    .orElseThrow(() -> new HandyFlowException(
                            "Board has no columns", HttpStatus.BAD_REQUEST, "NO_COLUMNS"));
        }

        int sortOrder = taskRepo
                .findByColumnIdAndDeletedAtIsNullOrderBySortOrderAsc(columnId).size();

        Task task = Task.create(tenantId, boardId, columnId,
                req.title(), req.description(), req.priority(),
                req.assigneeId(), req.dueDate(), req.estimatedHours(), sortOrder,
                req.linkedEntityType(), req.linkedEntityId(), createdBy);
        taskRepo.save(task);

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
        log.info("Moved task={} to column={}", taskId, req.columnId());
        return toTaskResponse(task, false);
    }

    @Transactional
    public TaskResponse completeTask(TenantId tenantId, UUID taskId) {
        Task task = findTask(tenantId, taskId);

        // Move to the done column on the board if one exists
        columnRepo.findByBoardIdOrderBySortOrderAsc(task.getBoardId())
                .stream()
                .filter(TaskColumn::isDoneColumn)
                .findFirst()
                .ifPresent(done -> task.moveToColumn(done.getId(), true));

        task.complete();
        taskRepo.save(task);
        log.info("Completed task={}", taskId);
        return toTaskResponse(task, false);
    }

    @Transactional
    public void deleteTask(TenantId tenantId, UUID taskId) {
        findTask(tenantId, taskId); // verify ownership
        jdbc.update("UPDATE tasks SET deleted_at = NOW() WHERE id = ?", taskId);
        log.info("Soft-deleted task={}", taskId);
    }

    // ── Comments ──────────────────────────────────────────────────────────────

    @Transactional
    public TaskResponse addComment(TenantId tenantId, UUID taskId,
                                   AddTaskCommentRequest req, UUID authorId, String authorName) {
        Task task = findTask(tenantId, taskId);
        TaskComment comment = TaskComment.create(taskId, tenantId.getValue(),
                authorId, authorName, req.body());
        commentRepo.save(comment);
        log.info("Added comment to task={} by user={}", taskId, authorId);
        return toTaskResponse(task, true);
    }

    @Transactional(readOnly = true)
    public List<TaskCommentResponse> getComments(TenantId tenantId, UUID taskId) {
        findTask(tenantId, taskId); // verify ownership
        return commentRepo.findByTaskIdOrderByCreatedAtAsc(taskId)
                .stream()
                .map(c -> new TaskCommentResponse(
                        c.getId(), c.getAuthorId(), c.getAuthorName(),
                        c.getBody(), c.getCreatedAt()))
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
        log.info("Logged {}h on task={} by user={}", req.hours(), taskId, userId);
        return toTimeLogResponse(entry);
    }

    @Transactional(readOnly = true)
    public List<TimeLogResponse> getTimeLogs(TenantId tenantId, UUID taskId) {
        findTask(tenantId, taskId);
        return timeLogRepo.findByTaskIdOrderByLoggedDateDesc(taskId)
                .stream().map(this::toTimeLogResponse).toList();
    }

    // ── Public utility ────────────────────────────────────────────────────────

    /**
     * Exposed so the controller can resolve display names without coupling
     * itself to the JDBC layer. Replace the SQL lookup with a proper
     * UserRepository call once available.
     */
    public String resolveUserName(UUID userId) {
        if (userId == null) return "Team Member";
        try {
            String name = jdbc.queryForObject(
                    "SELECT first_name || ' ' || last_name FROM users WHERE id = ?",
                    String.class, userId);
            return name != null ? name : "Team Member";
        } catch (Exception e) {
            return "Team Member";
        }
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
        List<ColumnResponse> columns = columnRepo.findByBoardIdOrderBySortOrderAsc(b.getId())
                .stream()
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
        return new ColumnResponse(c.getId(), c.getName(), c.getColor(),
                c.getSortOrder(), c.isDoneColumn(), tasks);
    }

    private TaskResponse toTaskResponse(Task t, boolean includeComments) {
        String     columnName   = fetchColumnName(t.getColumnId());
        String     assigneeName = resolveUserName(t.getAssigneeId());
        BigDecimal logged       = timeLogRepo.sumHoursByTask(t.getId());
        int        commentCount = commentRepo.findByTaskIdOrderByCreatedAtAsc(t.getId()).size();

        List<TaskCommentResponse> comments = includeComments
                ? commentRepo.findByTaskIdOrderByCreatedAtAsc(t.getId())
                .stream()
                .map(c -> new TaskCommentResponse(
                        c.getId(), c.getAuthorId(), c.getAuthorName(),
                        c.getBody(), c.getCreatedAt()))
                .toList()
                : List.of();

        return new TaskResponse(
                t.getId(), t.getBoardId(), t.getColumnId(), columnName,
                t.getTitle(), t.getDescription(), t.getPriority(), t.getStatus(),
                t.getAssigneeId(), assigneeName,
                t.getDueDate(), t.isOverdue(),
                t.getEstimatedHours(), logged,
                t.getSortOrder(),
                t.getLinkedEntityType(), t.getLinkedEntityId(),
                commentCount, comments,
                t.getCreatedAt(), t.getUpdatedAt(), t.getCompletedAt());
    }

    private String fetchColumnName(UUID columnId) {
        if (columnId == null) return null;
        try {
            return jdbc.queryForObject(
                    "SELECT name FROM task_columns WHERE id = ?", String.class, columnId);
        } catch (Exception e) { return null; }
    }

    private TimeLogResponse toTimeLogResponse(TaskTimeLog l) {
        return new TimeLogResponse(l.getId(), l.getUserId(), l.getUserName(),
                l.getHours(), l.getDescription(), l.getLoggedDate(), l.getCreatedAt());
    }
}
