package za.co.handyflow.platform.tasks.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import za.co.handyflow.platform.notifications.application.UserRecipientResolver;
import za.co.handyflow.platform.notifications.application.internal.NotificationService;
import za.co.handyflow.platform.shared.FileStorageService;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.tasks.domain.model.*;
import za.co.handyflow.platform.tasks.domain.repository.*;
import za.co.handyflow.platform.tasks.dto.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Pure unit tests for the parts of TasksService that were wrong: a client-supplied column id was
 * never checked against the board or tenant (cross-tenant write and delete), a task's status was
 * guessed from a column's name and could be overwritten, and moving a task ignored its position.
 */
@ExtendWith(MockitoExtension.class)
class TasksServiceTest {

    @Mock TaskBoardRepository boardRepo;
    @Mock TaskColumnRepository columnRepo;
    @Mock TaskRepository taskRepo;
    @Mock TaskCommentRepository commentRepo;
    @Mock TaskTimeLogRepository timeLogRepo;
    @Mock TaskAttachmentRepository attachmentRepo;
    @Mock TaskChecklistItemRepository checklistRepo;
    @Mock JdbcTemplate jdbc;
    @Mock NotificationService notificationService;
    @Mock TasksBoardPdfGenerator boardPdfGenerator;
    @Mock FileStorageService fileStorageService;
    @Mock UserRecipientResolver userRecipientResolver;

    private TasksService service;

    private static final UUID   TENANT_UUID = UUID.fromString("9ecb3dc7-75d4-4e56-b0a2-c95d3c7c584f");
    private static final TenantId TENANT    = TenantId.of(TENANT_UUID);
    private static final UUID   USER        = UUID.fromString("5a1b2c3d-4e5f-4a6b-8c7d-9e0f1a2b3c4d");
    private static final UUID   BOARD       = UUID.fromString("0b0a0c0d-1111-4222-8333-444455556666");

    @BeforeEach
    void setUp() {
        service = new TasksService(boardRepo, columnRepo, taskRepo, commentRepo, timeLogRepo,
                attachmentRepo, checklistRepo, jdbc, notificationService, boardPdfGenerator,
                fileStorageService, userRecipientResolver);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private void boardExists() {
        when(boardRepo.findByIdAndTenantId(BOARD, TENANT))
                .thenReturn(Optional.of(TaskBoard.create(TENANT, "Board", null, null, false, USER)));
    }

    private TaskColumn column(String name, TaskCategory category) {
        return TaskColumn.create(BOARD, TENANT_UUID, name, "#94A3B8", 0,
                category == TaskCategory.DONE, category);
    }

    private void columnBelongsToBoard(TaskColumn column) {
        when(columnRepo.findByIdAndBoardIdAndTenantId(column.getId(), BOARD, TENANT_UUID))
                .thenReturn(Optional.of(column));
    }

    private void columnDoesNotBelongToBoard(UUID columnId) {
        when(columnRepo.findByIdAndBoardIdAndTenantId(columnId, BOARD, TENANT_UUID))
                .thenReturn(Optional.empty());
    }

    private Task taskIn(TaskColumn column) {
        return Task.create(TENANT, BOARD, column.getId(), "A task", null, null,
                null, null, null, 0, null, null, USER);
    }

    private CreateTaskRequest createRequest(UUID columnId) {
        return new CreateTaskRequest("Write report", null, null, columnId,
                null, null, null, null, null, null);
    }

    // ── tenant isolation on columns ─────────────────────────────────────────

    @Nested
    @DisplayName("a column id from the client must belong to this board and tenant")
    class ColumnIsolation {

        @Test
        void updateColumn_rejectsAColumnThatIsNotOnThisBoardAndTenant() {
            boardExists();
            UUID foreign = UUID.randomUUID();
            columnDoesNotBelongToBoard(foreign);

            assertThatThrownBy(() -> service.updateColumn(TENANT, BOARD, foreign,
                    new CreateColumnRequest("Hacked", "#000000", 0, false, null)))
                    .isInstanceOf(ResourceNotFoundException.class);

            verify(columnRepo, never()).save(any());
            verify(columnRepo, never()).findById(any());
        }

        @Test
        void deleteColumn_rejectsAForeignColumn_andMovesNoTasks() {
            boardExists();
            UUID foreign = UUID.randomUUID();
            columnDoesNotBelongToBoard(foreign);

            assertThatThrownBy(() -> service.deleteColumn(TENANT, BOARD, foreign))
                    .isInstanceOf(ResourceNotFoundException.class);

            verifyNoInteractions(jdbc);                 // no task was reassigned
            verify(columnRepo, never()).delete(any());  // and the foreign column was not deleted
        }

        @Test
        void deleteColumn_movesTasksToASiblingOnTheSameBoard_thenDeletes() {
            boardExists();
            TaskColumn doomed  = column("Doing", TaskCategory.IN_PROGRESS);
            TaskColumn sibling = column("To Do", TaskCategory.TODO);
            columnBelongsToBoard(doomed);
            when(columnRepo.findByBoardIdOrderBySortOrderAsc(BOARD)).thenReturn(List.of(doomed, sibling));

            service.deleteColumn(TENANT, BOARD, doomed.getId());

            verify(columnRepo).delete(doomed);
        }

        @Test
        void deleteColumn_refusesToDeleteTheOnlyColumn() {
            boardExists();
            TaskColumn only = column("Only", TaskCategory.TODO);
            columnBelongsToBoard(only);
            when(columnRepo.findByBoardIdOrderBySortOrderAsc(BOARD)).thenReturn(List.of(only));

            assertThatThrownBy(() -> service.deleteColumn(TENANT, BOARD, only.getId()))
                    .isInstanceOf(HandyFlowException.class)
                    .hasMessageContaining("only column");

            verify(columnRepo, never()).delete(any());
        }

        @Test
        void createTask_rejectsAColumnFromAnotherBoardOrTenant() {
            boardExists();
            UUID foreign = UUID.randomUUID();
            columnDoesNotBelongToBoard(foreign);

            assertThatThrownBy(() -> service.createTask(TENANT, BOARD, USER, createRequest(foreign)))
                    .isInstanceOf(ResourceNotFoundException.class);

            verify(taskRepo, never()).save(any());
        }

        @Test
        void moveTask_rejectsAColumnFromAnotherBoardOrTenant() {
            TaskColumn todo = column("To Do", TaskCategory.TODO);
            Task task = taskIn(todo);
            UUID foreign = UUID.randomUUID();
            when(taskRepo.findByIdAndTenantId(task.getId(), TENANT)).thenReturn(Optional.of(task));
            columnDoesNotBelongToBoard(foreign);

            assertThatThrownBy(() -> service.moveTask(TENANT, task.getId(), new MoveTaskRequest(foreign, 0)))
                    .isInstanceOf(ResourceNotFoundException.class);

            assertThat(task.getColumnId()).isEqualTo(todo.getId());   // the task did not move
            verify(taskRepo, never()).saveAll(any());
        }
    }

    // ── status follows the column ───────────────────────────────────────────

    @Nested
    @DisplayName("a task's status is the category of its column, not a guess from the column's name")
    class StatusFollowsColumn {

        @Test
        void createTask_inAColumnNamedQA_isInReview_notTodo() {
            boardExists();
            TaskColumn qa = column("QA", TaskCategory.IN_REVIEW);   // the old name-guessing called this TODO
            columnBelongsToBoard(qa);
            when(taskRepo.findByColumnIdAndDeletedAtIsNullOrderBySortOrderAsc(qa.getId())).thenReturn(List.of());
            when(taskRepo.save(any(Task.class))).thenAnswer(inv -> inv.getArgument(0));

            service.createTask(TENANT, BOARD, USER, createRequest(qa.getId()));

            ArgumentCaptor<Task> saved = ArgumentCaptor.forClass(Task.class);
            verify(taskRepo).save(saved.capture());
            assertThat(saved.getValue().getStatus()).isEqualTo("IN_REVIEW");
            assertThat(saved.getValue().getCompletedAt()).isNull();
        }

        @Test
        void createTask_inADoneColumn_isCompleteImmediately() {
            boardExists();
            TaskColumn done = column("Finished", TaskCategory.DONE);
            columnBelongsToBoard(done);
            when(taskRepo.findByColumnIdAndDeletedAtIsNullOrderBySortOrderAsc(done.getId())).thenReturn(List.of());
            when(taskRepo.save(any(Task.class))).thenAnswer(inv -> inv.getArgument(0));

            service.createTask(TENANT, BOARD, USER, createRequest(done.getId()));

            ArgumentCaptor<Task> saved = ArgumentCaptor.forClass(Task.class);
            verify(taskRepo).save(saved.capture());
            assertThat(saved.getValue().getStatus()).isEqualTo("DONE");
            assertThat(saved.getValue().getCompletedAt()).isNotNull();
        }

        @Test
        void moveTask_setsTheStatusInTheEntity_andPlacesTheTaskAtTheRequestedPosition() {
            TaskColumn todo   = column("To Do", TaskCategory.TODO);
            TaskColumn review = column("QA", TaskCategory.IN_REVIEW);
            Task task = taskIn(todo);
            Task s0 = taskIn(review); s0.reposition(0);
            Task s1 = taskIn(review); s1.reposition(1);
            Task s2 = taskIn(review); s2.reposition(2);
            when(taskRepo.findByIdAndTenantId(task.getId(), TENANT)).thenReturn(Optional.of(task));
            columnBelongsToBoard(review);
            when(taskRepo.findByColumnIdAndDeletedAtIsNullOrderBySortOrderAsc(review.getId()))
                    .thenReturn(new ArrayList<>(List.of(s0, s1, s2)));

            service.moveTask(TENANT, task.getId(), new MoveTaskRequest(review.getId(), 1));

            assertThat(task.getStatus()).isEqualTo("IN_REVIEW");
            assertThat(task.getColumnId()).isEqualTo(review.getId());
            // the moved task sits at index 1 and the column is renumbered 0..3 without gaps or clashes
            assertThat(List.of(s0.getSortOrder(), task.getSortOrder(), s1.getSortOrder(), s2.getSortOrder()))
                    .containsExactly(0, 1, 2, 3);
        }

        @Test
        void moveTask_outOfDone_reopensTheTask() {
            TaskColumn done = column("Done", TaskCategory.DONE);
            TaskColumn todo = column("To Do", TaskCategory.TODO);
            Task task = taskIn(done);
            task.applyCategory(TaskCategory.DONE);
            assertThat(task.getCompletedAt()).isNotNull();
            when(taskRepo.findByIdAndTenantId(task.getId(), TENANT)).thenReturn(Optional.of(task));
            columnBelongsToBoard(todo);
            when(taskRepo.findByColumnIdAndDeletedAtIsNullOrderBySortOrderAsc(todo.getId()))
                    .thenReturn(new ArrayList<>());

            service.moveTask(TENANT, task.getId(), new MoveTaskRequest(todo.getId(), 0));

            // the old code left status = DONE here, so the task could never be flagged overdue again
            assertThat(task.getStatus()).isEqualTo("TODO");
            assertThat(task.getCompletedAt()).isNull();
        }
    }

    // ── default board ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("a new tenant gets a default board")
    class DefaultBoard {

        @Test
        void getBoards_createsADefaultBoardForATenantWithNone() {
            when(boardRepo.existsByTenantId(TENANT)).thenReturn(false);
            when(boardRepo.save(any(TaskBoard.class))).thenAnswer(inv -> inv.getArgument(0));
            when(boardRepo.findByTenantIdAndArchivedFalseOrderByIsDefaultDescCreatedAtAsc(TENANT))
                    .thenReturn(List.of());

            service.getBoards(TENANT);

            ArgumentCaptor<TaskBoard> board = ArgumentCaptor.forClass(TaskBoard.class);
            verify(boardRepo).save(board.capture());
            assertThat(board.getValue().isDefault()).isTrue();

            ArgumentCaptor<TaskColumn> columns = ArgumentCaptor.forClass(TaskColumn.class);
            verify(columnRepo, times(4)).save(columns.capture());
            assertThat(columns.getAllValues()).extracting(TaskColumn::getCategory)
                    .containsExactly(TaskCategory.TODO, TaskCategory.IN_PROGRESS,
                            TaskCategory.IN_REVIEW, TaskCategory.DONE);
        }

        @Test
        void getBoards_doesNotCreateABoardWhenOneExists() {
            when(boardRepo.existsByTenantId(TENANT)).thenReturn(true);
            when(boardRepo.findByTenantIdAndArchivedFalseOrderByIsDefaultDescCreatedAtAsc(TENANT))
                    .thenReturn(List.of());

            service.getBoards(TENANT);

            verify(boardRepo, never()).save(any());
        }
    }
}
