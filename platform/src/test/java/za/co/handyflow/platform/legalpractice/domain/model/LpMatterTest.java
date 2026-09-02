package za.co.handyflow.platform.legalpractice.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Entity-behaviour coverage for {@link LpMatter}'s lifecycle state machine
 * (OPEN -&gt; ON_HOLD -&gt; OPEN (reopen) -&gt; CLOSED -&gt; ARCHIVED) and
 * {@code isBillable()}. Uses real {@link TenantId#of(UUID)} instances per
 * this task's explicit instruction, not a mocked {@code TenantId} — unlike
 * the older {@code ClinicDomainModelTest} workaround, {@code TenantId}
 * exposes a real public {@code of(UUID)} factory so mocking it buys nothing.
 */
class LpMatterTest {

    private LpMatter freshOpenMatter() {
        return LpMatter.create(
                TenantId.of(UUID.randomUUID()),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "MAT-0001",
                "LITIGATION",
                "Smith v Jones",
                "Breach of contract dispute",
                "HOURLY",
                null,
                LocalDate.of(2026, 1, 15),
                "Initial notes");
    }

    @Test
    @DisplayName("create() defaults status to OPEN and openedDate to today when not supplied")
    void createDefaultsStatusAndOpenedDate() {
        LpMatter matter = LpMatter.create(
                TenantId.of(UUID.randomUUID()), UUID.randomUUID(), UUID.randomUUID(),
                "MAT-0002", "CONVEYANCING", "Transfer of Erf 123", null,
                "FIXED_FEE", new BigDecimal("15000.00"), null, null);

        assertThat(matter.getStatus()).isEqualTo("OPEN");
        assertThat(matter.getOpenedDate()).isEqualTo(LocalDate.now());
        assertThat(matter.getFixedFeeAmount()).isEqualByComparingTo("15000.00");
        assertThat(matter.isBillable()).isTrue();
    }

    @Test
    @DisplayName("create() honours a supplied openedDate rather than defaulting")
    void createHonoursSuppliedOpenedDate() {
        LpMatter matter = freshOpenMatter();
        assertThat(matter.getOpenedDate()).isEqualTo(LocalDate.of(2026, 1, 15));
    }

    @Nested
    @DisplayName("putOnHold()")
    class PutOnHold {

        @Test
        @DisplayName("OPEN -> ON_HOLD succeeds")
        void openToOnHoldSucceeds() {
            LpMatter matter = freshOpenMatter();
            matter.putOnHold();
            assertThat(matter.getStatus()).isEqualTo("ON_HOLD");
            assertThat(matter.isBillable()).isFalse();
        }

        @Test
        @DisplayName("ON_HOLD -> ON_HOLD throws IllegalStateException")
        void onHoldToOnHoldThrows() {
            LpMatter matter = freshOpenMatter();
            matter.putOnHold();
            assertThatThrownBy(matter::putOnHold)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Only an OPEN matter can be put on hold");
        }

        @Test
        @DisplayName("CLOSED -> ON_HOLD throws IllegalStateException")
        void closedToOnHoldThrows() {
            LpMatter matter = freshOpenMatter();
            matter.close(null);
            assertThatThrownBy(matter::putOnHold).isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    @DisplayName("reopen()")
    class Reopen {

        @Test
        @DisplayName("ON_HOLD -> OPEN succeeds and clears closedDate")
        void onHoldToOpenSucceeds() {
            LpMatter matter = freshOpenMatter();
            matter.putOnHold();
            matter.reopen();
            assertThat(matter.getStatus()).isEqualTo("OPEN");
            assertThat(matter.getClosedDate()).isNull();
        }

        @Test
        @DisplayName("CLOSED -> OPEN succeeds and clears closedDate")
        void closedToOpenSucceedsAndClearsClosedDate() {
            LpMatter matter = freshOpenMatter();
            matter.close(LocalDate.of(2026, 3, 1));
            assertThat(matter.getClosedDate()).isNotNull();

            matter.reopen();

            assertThat(matter.getStatus()).isEqualTo("OPEN");
            assertThat(matter.getClosedDate()).isNull();
        }

        @Test
        @DisplayName("OPEN -> reopen() throws IllegalStateException")
        void openToReopenThrows() {
            LpMatter matter = freshOpenMatter();
            assertThatThrownBy(matter::reopen).isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("ARCHIVED -> reopen() throws IllegalStateException")
        void archivedToReopenThrows() {
            LpMatter matter = freshOpenMatter();
            matter.close(null);
            matter.archive();
            assertThatThrownBy(matter::reopen).isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    @DisplayName("close()")
    class Close {

        @Test
        @DisplayName("OPEN -> CLOSED succeeds, defaults closedDate to today when null")
        void openToClosedDefaultsClosedDate() {
            LpMatter matter = freshOpenMatter();
            matter.close(null);
            assertThat(matter.getStatus()).isEqualTo("CLOSED");
            assertThat(matter.getClosedDate()).isEqualTo(LocalDate.now());
        }

        @Test
        @DisplayName("ON_HOLD -> CLOSED succeeds with an explicit closedDate")
        void onHoldToClosedHonoursExplicitDate() {
            LpMatter matter = freshOpenMatter();
            matter.putOnHold();
            matter.close(LocalDate.of(2026, 6, 30));
            assertThat(matter.getStatus()).isEqualTo("CLOSED");
            assertThat(matter.getClosedDate()).isEqualTo(LocalDate.of(2026, 6, 30));
        }

        @Test
        @DisplayName("ARCHIVED -> close() throws IllegalStateException")
        void archivedToCloseThrows() {
            LpMatter matter = freshOpenMatter();
            matter.close(null);
            matter.archive();
            assertThatThrownBy(() -> matter.close(null))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("ARCHIVED matter cannot be closed again");
        }

        @Test
        @DisplayName("CLOSED -> close() again succeeds (re-closing is idempotent-ish, only ARCHIVED is guarded)")
        void closedToCloseAgainSucceeds() {
            LpMatter matter = freshOpenMatter();
            matter.close(LocalDate.of(2026, 1, 1));
            matter.close(LocalDate.of(2026, 2, 1));
            assertThat(matter.getClosedDate()).isEqualTo(LocalDate.of(2026, 2, 1));
        }
    }

    @Nested
    @DisplayName("archive()")
    class Archive {

        @Test
        @DisplayName("CLOSED -> ARCHIVED succeeds")
        void closedToArchivedSucceeds() {
            LpMatter matter = freshOpenMatter();
            matter.close(null);
            matter.archive();
            assertThat(matter.getStatus()).isEqualTo("ARCHIVED");
            assertThat(matter.isBillable()).isFalse();
        }

        @Test
        @DisplayName("OPEN -> archive() throws IllegalStateException")
        void openToArchiveThrows() {
            LpMatter matter = freshOpenMatter();
            assertThatThrownBy(matter::archive)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Only a CLOSED matter can be archived");
        }

        @Test
        @DisplayName("ON_HOLD -> archive() throws IllegalStateException")
        void onHoldToArchiveThrows() {
            LpMatter matter = freshOpenMatter();
            matter.putOnHold();
            assertThatThrownBy(matter::archive).isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("ARCHIVED -> archive() again throws IllegalStateException")
        void archivedToArchiveAgainThrows() {
            LpMatter matter = freshOpenMatter();
            matter.close(null);
            matter.archive();
            assertThatThrownBy(matter::archive).isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    @DisplayName("isBillable() is true only in OPEN status — ON_HOLD deliberately does not count")
    void isBillableOnlyTrueWhenOpen() {
        LpMatter matter = freshOpenMatter();
        assertThat(matter.isBillable()).isTrue();

        matter.putOnHold();
        assertThat(matter.isBillable()).isFalse();

        matter.reopen();
        assertThat(matter.isBillable()).isTrue();

        matter.close(null);
        assertThat(matter.isBillable()).isFalse();

        matter.archive();
        assertThat(matter.isBillable()).isFalse();
    }

    @Test
    @DisplayName("update() mutates the editable fields without touching status/openedDate")
    void updateMutatesEditableFieldsOnly() {
        LpMatter matter = freshOpenMatter();
        UUID newAttorney = UUID.randomUUID();

        matter.update(newAttorney, "Smith v Jones (amended)", "Updated description",
                "FIXED_FEE", new BigDecimal("5000.00"), "Updated notes");

        assertThat(matter.getAttorneyId()).isEqualTo(newAttorney);
        assertThat(matter.getMatterName()).isEqualTo("Smith v Jones (amended)");
        assertThat(matter.getBillingType()).isEqualTo("FIXED_FEE");
        assertThat(matter.getFixedFeeAmount()).isEqualByComparingTo("5000.00");
        assertThat(matter.getStatus()).isEqualTo("OPEN");
        assertThat(matter.getOpenedDate()).isEqualTo(LocalDate.of(2026, 1, 15));
    }
}
