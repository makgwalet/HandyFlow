package za.co.handyflow.platform.crm.application.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.crm.application.internal.CampaignRules.Step;
import za.co.handyflow.platform.crm.domain.model.*;

class CampaignRulesTest {
    private static final LocalDate D1 = LocalDate.of(2026, 10, 1), D2 = LocalDate.of(2026, 10, 31);

    private static Step st(int day, WorkflowAction a, String t) { return new Step(day, a, t, null); }
    private static void refuses(Runnable r, String msg) { assertThatThrownBy(r::run).isInstanceOf(IllegalArgumentException.class).hasMessage(msg); }

    @Test
    void checksNameGoalAndDates() {
        CampaignRules.check("Spring push", "Win back", D1, D2);
        CampaignRules.check("x", null, null, null);
        refuses(() -> CampaignRules.check(" ", null, null, null), "Name the campaign");
        refuses(() -> CampaignRules.check("x".repeat(121), null, null, null), "The name can be at most 120 characters");
        refuses(() -> CampaignRules.check("x", "g".repeat(501), null, null), "The goal can be at most 500 characters");
        refuses(() -> CampaignRules.check("x", null, D2, D1), "The end date cannot be before the start date");
    }

    @Test
    void statusMovesOnlyForward() {
        CampaignRules.checkMove(CampaignStatus.DRAFT, CampaignStatus.ACTIVE);
        CampaignRules.checkMove(CampaignStatus.ACTIVE, CampaignStatus.PAUSED);
        CampaignRules.checkMove(CampaignStatus.PAUSED, CampaignStatus.ACTIVE);
        CampaignRules.checkMove(CampaignStatus.PAUSED, CampaignStatus.COMPLETED);
        CampaignRules.checkMove(CampaignStatus.ACTIVE, CampaignStatus.ACTIVE);
        refuses(() -> CampaignRules.checkMove(CampaignStatus.DRAFT, CampaignStatus.PAUSED), "Cannot move a campaign from draft to paused");
        refuses(() -> CampaignRules.checkMove(CampaignStatus.ACTIVE, CampaignStatus.DRAFT), "Cannot move a campaign from active to draft");
        refuses(() -> CampaignRules.checkMove(CampaignStatus.COMPLETED, CampaignStatus.ACTIVE), "A finished campaign cannot be started again");
        refuses(() -> CampaignRules.checkMove(CampaignStatus.DRAFT, null), "Choose a status");
    }

    @Test
    void stepsCanOnlyBeChangedWhileNotRunning() {
        CampaignRules.checkEditable(CampaignStatus.DRAFT);
        CampaignRules.checkEditable(CampaignStatus.PAUSED);
        refuses(() -> CampaignRules.checkEditable(CampaignStatus.ACTIVE), "Pause the campaign before changing its steps");
        refuses(() -> CampaignRules.checkEditable(CampaignStatus.COMPLETED), "A finished campaign cannot be changed");
    }

    @Test
    void checksSteps() {
        CampaignRules.checkSteps(List.of(st(0, WorkflowAction.CREATE_TASK, "Call"), st(3, WorkflowAction.ADD_TAG, "warm"), st(3, WorkflowAction.NOTIFY_OWNER, "Check in"),
                new Step(7, WorkflowAction.ASSIGN_OWNER, null, UUID.randomUUID())));
        refuses(() -> CampaignRules.checkSteps(null), "Send the list of steps");
        refuses(() -> CampaignRules.checkSteps(Collections.nCopies(11, st(0, WorkflowAction.ADD_TAG, "a"))), "A journey can have at most 10 steps");
        refuses(() -> CampaignRules.checkSteps(List.of(st(366, WorkflowAction.ADD_TAG, "a"))), "Step 1: the day must be between 0 and 365");
        refuses(() -> CampaignRules.checkSteps(List.of(st(-1, WorkflowAction.ADD_TAG, "a"))), "Step 1: the day must be between 0 and 365");
        refuses(() -> CampaignRules.checkSteps(List.of(st(5, WorkflowAction.ADD_TAG, "a"), st(2, WorkflowAction.ADD_TAG, "b"))), "Step 2: it cannot come before the step above it");
        refuses(() -> CampaignRules.checkSteps(List.of(st(0, null, "a"))), "Step 1: choose what it does");
        refuses(() -> CampaignRules.checkSteps(List.of(st(0, WorkflowAction.CREATE_TASK, " "))), "Step 1: say what the task is");
        refuses(() -> CampaignRules.checkSteps(List.of(st(0, WorkflowAction.ADD_TAG, "x".repeat(51)))), "Step 1: the tag can be at most 50 characters");
        refuses(() -> CampaignRules.checkSteps(List.of(st(0, WorkflowAction.ASSIGN_OWNER, null))), "Step 1: choose who becomes the owner");
        refuses(() -> CampaignRules.checkSteps(List.of(st(0, WorkflowAction.NOTIFY_OWNER, ""))), "Step 1: write the message");
        refuses(() -> CampaignRules.checkSteps(List.of(st(0, WorkflowAction.ADD_TAG, "ok"), st(1, WorkflowAction.NOTIFY_OWNER, "m".repeat(501)))), "Step 2: the message can be at most 500 characters");
    }

    @Test
    void worksOutWhichStepsAreDue() {
        List<Step> j = List.of(st(0, WorkflowAction.CREATE_TASK, "a"), st(3, WorkflowAction.ADD_TAG, "b"), st(7, WorkflowAction.NOTIFY_OWNER, "c"));
        LocalDate added = LocalDate.of(2026, 10, 1);
        assertThat(CampaignRules.dueSteps(j, 0, added, added)).containsExactly(0);
        assertThat(CampaignRules.dueSteps(j, 1, added, added.plusDays(2))).isEmpty();
        assertThat(CampaignRules.dueSteps(j, 1, added, added.plusDays(3))).containsExactly(1);
        assertThat(CampaignRules.dueSteps(j, 0, added, added.plusDays(10))).containsExactly(0, 1, 2);
        assertThat(CampaignRules.dueSteps(j, 3, added, added.plusDays(10))).isEmpty();
        assertThat(CampaignRules.dueSteps(List.of(), 0, added, added)).isEmpty();
    }

    @Test
    void journeyStopsForRepliesConversionsAndClosedLeads() {
        assertThat(CampaignRules.exits(MemberStatus.RESPONDED, LeadStage.NEW)).isTrue();
        assertThat(CampaignRules.exits(MemberStatus.CONVERTED, null)).isTrue();
        assertThat(CampaignRules.exits(MemberStatus.ADDED, LeadStage.WON)).isTrue();
        assertThat(CampaignRules.exits(MemberStatus.CONTACTED, LeadStage.LOST)).isTrue();
        assertThat(CampaignRules.exits(MemberStatus.CONTACTED, LeadStage.QUALIFIED)).isFalse();
        assertThat(CampaignRules.exits(MemberStatus.ADDED, null)).isFalse();
    }

    @Test
    void campaignDatesAreOpenEnded() {
        assertThat(CampaignRules.inWindow(null, null, D1)).isTrue();
        assertThat(CampaignRules.inWindow(D1, D2, D1)).isTrue();
        assertThat(CampaignRules.inWindow(D1, D2, D2)).isTrue();
        assertThat(CampaignRules.inWindow(D1, D2, D1.minusDays(1))).isFalse();
        assertThat(CampaignRules.inWindow(D1, D2, D2.plusDays(1))).isFalse();
        assertThat(CampaignRules.inWindow(D1, null, D2)).isTrue();
        assertThat(CampaignRules.inWindow(null, D2, D1)).isTrue();
    }

    @Test
    void summarises() {
        var s = CampaignRules.summarise(List.of(MemberStatus.ADDED, MemberStatus.CONTACTED, MemberStatus.RESPONDED, MemberStatus.CONVERTED));
        assertThat(s.total()).isEqualTo(4);
        assertThat(s.contacted()).isEqualTo(3);
        assertThat(s.responded()).isEqualTo(2);
        assertThat(s.converted()).isEqualTo(1);
        assertThat(s.responseRate()).isEqualTo(50);
        assertThat(s.conversionRate()).isEqualTo(25);
        var empty = CampaignRules.summarise(List.<MemberStatus>of());
        assertThat(empty.total()).isZero();
        assertThat(empty.responseRate()).isNull();
        var counts = new EnumMap<MemberStatus, Integer>(MemberStatus.class);
        counts.put(MemberStatus.CONVERTED, 1);
        counts.put(MemberStatus.ADDED, 3);
        assertThat(CampaignRules.summarise(counts).conversionRate()).isEqualTo(25);
    }

    @Test
    void addsOnlyNewPeopleWithinLimits() {
        UUID a = new UUID(0, 1), b = new UUID(0, 2), c = new UUID(0, 3);
        assertThat(CampaignRules.toAdd(Set.of(a), List.of(a, b, b, c))).containsExactly(b, c);
        refuses(() -> CampaignRules.toAdd(Set.of(), List.of()), "Choose who to add");
        refuses(() -> CampaignRules.toAdd(Set.of(), null), "Choose who to add");
        List<UUID> many = new ArrayList<>();
        for (int i = 0; i < 501; i++) many.add(new UUID(1, i));
        refuses(() -> CampaignRules.toAdd(Set.of(), many), "Add at most 500 people at a time");
        Set<UUID> full = new HashSet<>();
        for (int i = 0; i < 1999; i++) full.add(new UUID(2, i));
        refuses(() -> CampaignRules.toAdd(full, List.of(a, b)), "A campaign can have at most 2000 people");
        assertThat(CampaignRules.toAdd(full, List.of(a))).hasSize(1);
    }

    @Test
    void describesSteps() {
        assertThat(CampaignRules.describeStep(st(0, WorkflowAction.CREATE_TASK, " Call "), null)).isEqualTo("Straight away: add the task \"Call\"");
        assertThat(CampaignRules.describeStep(st(1, WorkflowAction.ADD_TAG, " WARM "), null)).isEqualTo("After 1 day: tag it \"warm\"");
        assertThat(CampaignRules.describeStep(new Step(7, WorkflowAction.ASSIGN_OWNER, null, UUID.randomUUID()), "Sam")).isEqualTo("After 7 days: make Sam the owner");
        assertThat(CampaignRules.describeStep(st(2, WorkflowAction.NOTIFY_OWNER, "Check in"), null)).isEqualTo("After 2 days: tell the owner: Check in");
    }
}
