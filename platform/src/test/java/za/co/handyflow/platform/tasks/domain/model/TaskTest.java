package za.co.handyflow.platform.tasks.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Pure domain tests for Task and TaskColumn: no Spring, no mocks. */
class TaskTest {

    private static final TenantId TENANT = TenantId.of(UUID.fromString("9ecb3dc7-75d4-4e56-b0a2-c95d3c7c584f"));
    private static final UUID BOARD  = UUID.randomUUID();
    private static final UUID USER   = UUID.randomUUID();

    private Task newTask() {
        return Task.create(TENANT, BOARD, UUID.randomUUID(), "Title", "Description", "HIGH",
                USER, LocalDate.of(2026, 10, 1), new BigDecimal("4.50"), 0,
                "QUOTE", UUID.randomUUID(), USER);
    }

    // ── status follows the column category ──────────────────────────────────

    @Test
    void applyCategory_done_setsStatusAndCompletedAt_once() {
        Task task = newTask();
        task.applyCategory(TaskCategory.DONE);
        assertThat(task.getStatus()).isEqualTo("DONE");
        var firstCompletion = task.getCompletedAt();
        assertThat(firstCompletion).isNotNull();

        task.applyCategory(TaskCategory.DONE);   // e.g. moved between two done columns
        assertThat(task.getCompletedAt()).isEqualTo(firstCompletion);
    }

    @Test
    void moveToColumn_outOfDone_resetsTheStatusAndClearsCompletedAt() {
        Task task = newTask();
        task.moveToColumn(UUID.randomUUID(), TaskCategory.DONE);
        task.moveToColumn(UUID.randomUUID(), TaskCategory.IN_PROGRESS);
        assertThat(task.getStatus()).isEqualTo("IN_PROGRESS");
        assertThat(task.getCompletedAt()).isNull();
    }

    @Test
    void blocked_isAnOpenStatus() {
        Task task = newTask();
        task.applyCategory(TaskCategory.BLOCKED);
        assertThat(task.getStatus()).isEqualTo("BLOCKED");
        assertThat(task.getCompletedAt()).isNull();
    }

    // ── overdue ─────────────────────────────────────────────────────────────

    @Test
    void isOverdue_followsTheBusinessDay_andStopsOnceDone() {
        Task task = newTask();
        task.update(null, null, null, null, Task.today().minusDays(1), null, null, null,
                false, false, false, false, false);
        assertThat(task.isOverdue()).isTrue();

        task.update(null, null, null, null, Task.today(), null, null, null,
                false, false, false, false, false);
        assertThat(task.isOverdue()).isFalse();   // due today is not overdue yet

        task.update(null, null, null, null, Task.today().minusDays(3), null, null, null,
                false, false, false, false, false);
        task.applyCategory(TaskCategory.DONE);
        assertThat(task.isOverdue()).isFalse();
    }

    // ── update: null means "no change"; clear flags remove ──────────────────

    @Test
    void update_nullLeavesEveryFieldUnchanged() {
        Task task = newTask();
        UUID assignee = task.getAssigneeId();
        task.update(null, null, null, null, null, null, null, null,
                false, false, false, false, false);
        assertThat(task.getTitle()).isEqualTo("Title");
        assertThat(task.getDescription()).isEqualTo("Description");
        assertThat(task.getAssigneeId()).isEqualTo(assignee);
        assertThat(task.getDueDate()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(task.getEstimatedHours()).isEqualByComparingTo("4.50");
        assertThat(task.getLinkedEntityType()).isEqualTo("QUOTE");
    }

    @Test
    void update_clearFlagsRemoveTheirValues() {
        Task task = newTask();
        task.update(null, null, null, null, null, null, null, null,
                true, true, true, true, true);
        assertThat(task.getAssigneeId()).isNull();
        assertThat(task.getDueDate()).isNull();
        assertThat(task.getDescription()).isNull();
        assertThat(task.getEstimatedHours()).isNull();
        assertThat(task.getLinkedEntityType()).isNull();
        assertThat(task.getLinkedEntityId()).isNull();
    }

    @Test
    @DisplayName("clearing the due date re-arms the overdue alert, like changing it does")
    void update_clearingTheDueDate_rearmsTheOverdueAlert() {
        Task task = newTask();
        task.markOverdueAlertSent();
        assertThat(task.getOverdueAlertSentAt()).isNotNull();
        task.update(null, null, null, null, null, null, null, null,
                false, true, false, false, false);
        assertThat(task.getOverdueAlertSentAt()).isNull();
    }

    @Test
    void update_aClearFlagWinsOverAValueSentInTheSameRequest() {
        Task task = newTask();
        task.update(null, "New description", null, null, null, null, null, null,
                false, false, true, false, false);
        assertThat(task.getDescription()).isNull();
    }

    // ── TaskColumn ──────────────────────────────────────────────────────────

    @Test
    void column_createWithoutACategory_derivesItFromTheDoneFlag() {
        assertThat(TaskColumn.create(BOARD, TENANT.getValue(), "Done", null, 3, true).getCategory())
                .isEqualTo(TaskCategory.DONE);
        assertThat(TaskColumn.create(BOARD, TENANT.getValue(), "Todo", null, 0, false).getCategory())
                .isEqualTo(TaskCategory.TODO);
    }

    @Test
    void column_applyCategory_keepsTheDoneFlagInStep() {
        TaskColumn column = TaskColumn.create(BOARD, TENANT.getValue(), "Col", null, 0, false);
        column.applyCategory(TaskCategory.DONE);
        assertThat(column.isDoneColumn()).isTrue();
        column.applyCategory(TaskCategory.BLOCKED);
        assertThat(column.isDoneColumn()).isFalse();
        column.applyCategory(null);               // not supplied: nothing changes
        assertThat(column.getCategory()).isEqualTo(TaskCategory.BLOCKED);
    }

    @Test
    void column_update_keepsTheCategoryConsistentWithTheDoneFlag() {
        TaskColumn column = TaskColumn.create(BOARD, TENANT.getValue(), "Col", null, 0, true);
        column.update("Col", null, 0, false);      // the flag is turned off
        assertThat(column.getCategory()).isEqualTo(TaskCategory.TODO);
        column.update("Col", null, 0, true);       // and on again
        assertThat(column.getCategory()).isEqualTo(TaskCategory.DONE);
    }

    @Test
    void category_parseOrNull() {
        assertThat(TaskCategory.parseOrNull(null)).isNull();
        assertThat(TaskCategory.parseOrNull("  ")).isNull();
        assertThat(TaskCategory.parseOrNull("in_progress")).isEqualTo(TaskCategory.IN_PROGRESS);
    }
}
