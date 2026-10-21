package za.co.handyflow.platform.catalogue.application.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.access.AccessDeniedException;
import za.co.handyflow.platform.catalogue.application.internal.CatalogueService.Caller;
import za.co.handyflow.platform.catalogue.domain.model.*;
import za.co.handyflow.platform.catalogue.domain.repository.*;
import za.co.handyflow.platform.catalogue.dto.*;
import za.co.handyflow.platform.shared.ConflictException;
import za.co.handyflow.platform.shared.TenantId;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CataloguePricingServiceTest {
    private static final TenantId TENANT = TenantId.of(UUID.randomUUID());
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 21);
    private static final UUID MAKER_ID = UUID.randomUUID(), CHECKER_ID = UUID.randomUUID();
    private static final Caller MAKER = new Caller(MAKER_ID, true, true, false, false);
    private static final Caller CHECKER = new Caller(CHECKER_ID, true, false, false, true);
    private static final Caller NO_RIGHTS = new Caller(UUID.randomUUID(), false, false, false, false);

    @Mock CatalogueItemRepository items;
    @Mock CataloguePriceListRepository lists;
    @Mock CataloguePriceChangeRepository changes;
    @Mock CatalogueSettingRepository settings;
    @Mock CatalogueAuditEventRepository audit;
    CataloguePricingService service;
    CatalogueItem item;
    CataloguePriceList retail, trade;

    @BeforeEach
    void setUp() {
        service = new CataloguePricingService(items, lists, changes, settings, audit);
        service.useClock(Clock.fixed(TODAY.atTime(10, 0).atZone(CataloguePricingService.ZONE).toInstant(), CataloguePricingService.ZONE));
        var d = new CatalogueItem.Details("Cement", null, "C-1", null, null, CatalogueItemType.PRODUCT, "each",
                new BigDecimal("100.00"), new BigDecimal("60.00"), new BigDecimal("15.00"), false);
        item = CatalogueItem.create(TENANT, null, d, CatalogueItemStatus.ACTIVE);
        retail = CataloguePriceList.create(TENANT, "RETAIL", "Retail", true, 0);
        trade = CataloguePriceList.create(TENANT, "TRADE", "Trade", false, 10);
        when(items.findActiveById(TENANT, item.getId())).thenReturn(Optional.of(item));
        when(lists.findDefault(TENANT)).thenReturn(Optional.of(retail));
        when(lists.findLiveById(TENANT, retail.getId())).thenReturn(Optional.of(retail));
        when(lists.findLiveById(TENANT, trade.getId())).thenReturn(Optional.of(trade));
        when(lists.findAllLive(TENANT)).thenReturn(List.of(retail, trade));
        when(changes.appliedForItem(TENANT, item.getId())).thenReturn(List.of());
        when(changes.openForItem(TENANT, item.getId())).thenReturn(List.of());
        approvalIs(true);
    }

    private void approvalIs(boolean required) {
        when(settings.findById(TENANT.getValue())).thenReturn(Optional.of(CatalogueSetting.of(TENANT.getValue(), required, null)));
    }

    private static PriceChangeRequest request(UUID list, String price, LocalDate from, String reason) {
        return new PriceChangeRequest(list, new BigDecimal(price), from, reason);
    }

    private CataloguePriceChange saved() {
        var c = ArgumentCaptor.forClass(CataloguePriceChange.class);
        verify(changes, org.mockito.Mockito.atLeastOnce()).save(c.capture());
        return c.getValue();
    }

    private CataloguePriceChange pendingFrom(Caller by, String price, LocalDate from) {
        var c = CataloguePriceChange.propose(TENANT, item.getId(), retail.getId(), new BigDecimal("100.00"), new BigDecimal(price), from, "cost up", by.userId());
        when(changes.findOwned(TENANT, c.getId())).thenReturn(Optional.of(c));
        return c;
    }

    // ── Settings ──────────────────────────────────────────────────────────────

    @Test
    void aTenantWithNoSettingsRowRequiresApproval() {
        when(settings.findById(TENANT.getValue())).thenReturn(Optional.empty());
        assertThat(service.approvalRequired(TENANT)).isTrue();
        assertThat(service.settings(TENANT).priceApprovalRequired()).isTrue();
    }

    @Test
    void switchingApprovalOffWritesAHistoryLine() {
        var out = service.setApprovalRequired(TENANT, false, CHECKER);
        assertThat(out.priceApprovalRequired()).isFalse();
        var ev = ArgumentCaptor.forClass(CatalogueAuditEvent.class);
        verify(audit).save(ev.capture());
        assertThat(ev.getValue().getAction()).isEqualTo(CatalogueAuditAction.SETTING_CHANGED);
        assertThat(ev.getValue().getDetail()).isEqualTo("Price changes no longer need approval");
    }

    @Test
    void savingTheSameSettingWritesNoHistory() {
        service.setApprovalRequired(TENANT, true, CHECKER);
        verify(audit, never()).save(any());
    }

    // ── Proposing ─────────────────────────────────────────────────────────────

    @Test
    void whenApprovalIsRequiredAProposalWaitsAndTheItemPriceDoesNotMove() {
        var out = service.propose(TENANT, item.getId(), request(retail.getId(), "120", TODAY, "supplier increase"), MAKER);
        assertThat(out.status()).isEqualTo("PENDING");
        assertThat(out.changePercent()).isEqualByComparingTo("20.0");
        assertThat(out.proposedByMe()).isTrue();
        assertThat(item.getDefaultPrice()).isEqualByComparingTo("100.00");
        var ev = ArgumentCaptor.forClass(CatalogueAuditEvent.class);
        verify(audit).save(ev.capture());
        assertThat(ev.getValue().getAction()).isEqualTo(CatalogueAuditAction.PRICE_PROPOSED);
    }

    @Test
    void aReasonIsRequiredWhenAnApproverMustReadIt() {
        assertThatThrownBy(() -> service.propose(TENANT, item.getId(), request(retail.getId(), "120", TODAY, " "), MAKER))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Say why the price is changing (at least 3 characters).");
        verify(changes, never()).save(any());
    }

    @Test
    void withoutApprovalAChangeForTodayIsAppliedAtOnce() {
        approvalIs(false);
        var out = service.propose(TENANT, item.getId(), request(retail.getId(), "120", null, null), MAKER);
        assertThat(out.status()).isEqualTo("APPLIED");
        assertThat(item.getDefaultPrice()).isEqualByComparingTo("120.00");
        verify(items).save(item);
    }

    @Test
    void withoutApprovalAFutureChangeIsScheduledAndLeavesThePriceAlone() {
        approvalIs(false);
        var out = service.propose(TENANT, item.getId(), request(retail.getId(), "120", TODAY.plusDays(10), null), MAKER);
        assertThat(out.status()).isEqualTo("SCHEDULED");
        assertThat(item.getDefaultPrice()).isEqualByComparingTo("100.00");
        verify(items, never()).save(any());
    }

    @Test
    void aChangeToAnotherListNeverMovesTheItemsDefaultPrice() {
        approvalIs(false);
        var out = service.propose(TENANT, item.getId(), request(trade.getId(), "90", null, null), MAKER);
        assertThat(out.status()).isEqualTo("APPLIED");
        assertThat(out.oldPrice()).isNull();
        assertThat(item.getDefaultPrice()).isEqualByComparingTo("100.00");
        verify(items, never()).save(any());
    }

    @Test
    void aPriceBelowTheMinimumIsRefused() {
        item.changeMinPrice(new BigDecimal("95.00"));
        assertThatThrownBy(() -> service.propose(TENANT, item.getId(), request(retail.getId(), "90", TODAY, "clearance"), MAKER))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("The price cannot be below the minimum price of R 95.00.");
    }

    @Test
    void thePastAndTheSamePriceAreRefused() {
        assertThatThrownBy(() -> service.propose(TENANT, item.getId(), request(retail.getId(), "120", TODAY.minusDays(1), "x y z"), MAKER))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("The effective date cannot be in the past.");
        assertThatThrownBy(() -> service.propose(TENANT, item.getId(), request(retail.getId(), "100", TODAY, "x y z"), MAKER))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("That is already the price.");
    }

    @Test
    void onlyOneChangeCanWaitPerItemAndList() {
        when(changes.pendingExists(TENANT, item.getId(), retail.getId())).thenReturn(true);
        assertThatThrownBy(() -> service.propose(TENANT, item.getId(), request(retail.getId(), "120", TODAY, "cost up"), MAKER))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void proposingNeedsThePriceWritePermission() {
        assertThatThrownBy(() -> service.propose(TENANT, item.getId(), request(retail.getId(), "120", TODAY, "cost up"), NO_RIGHTS))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void anArchivedItemCannotBeRepriced() {
        item.softDelete(MAKER_ID);
        item.changeStatus(CatalogueItemStatus.ARCHIVED, "old");
        assertThatThrownBy(() -> service.propose(TENANT, item.getId(), request(retail.getId(), "120", TODAY, "cost up"), MAKER))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("archived");
    }

    // ── Deciding ──────────────────────────────────────────────────────────────

    @Test
    void anApproverOtherThanTheProposerPutsTheChangeInForce() {
        var c = pendingFrom(MAKER, "120", TODAY);
        var out = service.approve(TENANT, c.getId(), new PriceDecisionRequest("ok"), CHECKER);
        assertThat(out.status()).isEqualTo("APPLIED");
        assertThat(item.getDefaultPrice()).isEqualByComparingTo("120.00");
        assertThat(c.getDecidedBy()).isEqualTo(CHECKER_ID);
    }

    @Test
    void anApprovedChangeWithAFutureDateIsScheduled() {
        var c = pendingFrom(MAKER, "120", TODAY.plusDays(5));
        assertThat(service.approve(TENANT, c.getId(), null, CHECKER).status()).isEqualTo("SCHEDULED");
        assertThat(item.getDefaultPrice()).isEqualByComparingTo("100.00");
    }

    @Test
    void theProposerCannotApproveOrRejectTheirOwnChange() {
        var c = pendingFrom(MAKER, "120", TODAY);
        var both = new Caller(MAKER_ID, true, true, false, true);
        assertThatThrownBy(() -> service.approve(TENANT, c.getId(), null, both))
                .isInstanceOf(AccessDeniedException.class).hasMessage("You proposed this change, so someone else has to decide it.");
        assertThatThrownBy(() -> service.reject(TENANT, c.getId(), new PriceDecisionRequest("no"), both))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(c.getStatus()).isEqualTo(PriceChangeStatus.PENDING);
        assertThat(item.getDefaultPrice()).isEqualByComparingTo("100.00");
    }

    @Test
    void decidingNeedsTheApprovePermission() {
        var c = pendingFrom(MAKER, "120", TODAY);
        assertThatThrownBy(() -> service.approve(TENANT, c.getId(), null, NO_RIGHTS)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.reject(TENANT, c.getId(), new PriceDecisionRequest("no"), NO_RIGHTS)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void aRejectionNeedsANoteAndLeavesThePriceAlone() {
        var c = pendingFrom(MAKER, "120", TODAY);
        assertThatThrownBy(() -> service.reject(TENANT, c.getId(), new PriceDecisionRequest(" "), CHECKER))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Say why (at least 3 characters).");
        var out = service.reject(TENANT, c.getId(), new PriceDecisionRequest("margin too thin"), CHECKER);
        assertThat(out.status()).isEqualTo("REJECTED");
        assertThat(out.decisionNote()).isEqualTo("margin too thin");
        assertThat(item.getDefaultPrice()).isEqualByComparingTo("100.00");
    }

    @Test
    void aChangeThatIsNoLongerPendingCannotBeDecidedAgain() {
        var c = pendingFrom(MAKER, "120", TODAY);
        c.reject(CHECKER_ID, "no");
        assertThatThrownBy(() -> service.approve(TENANT, c.getId(), null, CHECKER)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void aProposalWhoseDateHasPassedCannotBeApproved() {
        var c = pendingFrom(MAKER, "120", TODAY.minusDays(1));
        assertThatThrownBy(() -> service.approve(TENANT, c.getId(), null, CHECKER))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("has passed");
    }

    @Test
    void approvalIsRefusedWhenTheMinimumWasRaisedMeanwhile() {
        var c = pendingFrom(MAKER, "120", TODAY);
        item.changeMinPrice(new BigDecimal("130.00"));
        assertThatThrownBy(() -> service.approve(TENANT, c.getId(), null, CHECKER)).isInstanceOf(IllegalArgumentException.class);
        assertThat(c.getStatus()).isEqualTo(PriceChangeStatus.PENDING);
    }

    @Test
    void theProposerOrAnApproverMayCancelButNobodyElse() {
        var c = pendingFrom(MAKER, "120", TODAY);
        assertThatThrownBy(() -> service.cancel(TENANT, c.getId(), null, NO_RIGHTS)).isInstanceOf(AccessDeniedException.class);
        assertThat(service.cancel(TENANT, c.getId(), null, MAKER).status()).isEqualTo("CANCELLED");
        var d = pendingFrom(MAKER, "130", TODAY);
        assertThat(service.cancel(TENANT, d.getId(), new PriceDecisionRequest("not needed"), CHECKER).status()).isEqualTo("CANCELLED");
    }

    // ── Minimum price and the scheduler ───────────────────────────────────────

    @Test
    void theMinimumCannotBeSetAboveTheCurrentPrice() {
        assertThatThrownBy(() -> service.setMinPrice(TENANT, item.getId(), new MinPriceRequest(new BigDecimal("150")), MAKER))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("already below that minimum");
        service.setMinPrice(TENANT, item.getId(), new MinPriceRequest(new BigDecimal("80")), MAKER);
        assertThat(item.getMinPrice()).isEqualByComparingTo("80.00");
        service.setMinPrice(TENANT, item.getId(), new MinPriceRequest(null), MAKER);
        assertThat(item.getMinPrice()).isNull();
    }

    @Test
    void theMinimumNeedsThePriceWritePermission() {
        assertThatThrownBy(() -> service.setMinPrice(TENANT, item.getId(), new MinPriceRequest(BigDecimal.ONE), NO_RIGHTS))
                .isInstanceOf(AccessDeniedException.class);
    }

    private CataloguePriceChange scheduled(String price, LocalDate from) {
        var c = CataloguePriceChange.propose(TENANT, item.getId(), retail.getId(), new BigDecimal("100.00"), new BigDecimal(price), from, "cost up", MAKER_ID);
        c.approve(CHECKER_ID, null, TODAY);
        return c;
    }

    @Test
    void theSchedulerAppliesDueChangesAndLeavesFutureOnesAlone() {
        var due = scheduled("120", TODAY.plusDays(1));
        assertThat(due.getStatus()).isEqualTo(PriceChangeStatus.SCHEDULED);
        service.useClock(Clock.fixed(TODAY.plusDays(1).atTime(0, 5).atZone(CataloguePricingService.ZONE).toInstant(), CataloguePricingService.ZONE));
        when(changes.due(TODAY.plusDays(1))).thenReturn(List.of(due));
        when(items.findActiveById(TENANT, item.getId())).thenReturn(Optional.of(item));
        assertThat(service.applyDue()).isEqualTo(1);
        assertThat(due.getStatus()).isEqualTo(PriceChangeStatus.APPLIED);
        assertThat(item.getDefaultPrice()).isEqualByComparingTo("120.00");
    }

    @Test
    void aScheduledChangeBelowAMinimumRaisedLaterIsCancelledNotApplied() {
        var due = scheduled("90", TODAY.plusDays(1));
        item.changeMinPrice(new BigDecimal("95.00"));
        service.useClock(Clock.fixed(TODAY.plusDays(1).atTime(0, 5).atZone(CataloguePricingService.ZONE).toInstant(), CataloguePricingService.ZONE));
        when(changes.due(TODAY.plusDays(1))).thenReturn(List.of(due));
        assertThat(service.applyDue()).isEqualTo(0);
        assertThat(due.getStatus()).isEqualTo(PriceChangeStatus.CANCELLED);
        assertThat(item.getDefaultPrice()).isEqualByComparingTo("100.00");
    }

    @Test
    void theSchedulerCancelsChangesForItemsThatNoLongerExist() {
        var due = scheduled("120", TODAY.plusDays(1));
        when(items.findActiveById(TENANT, item.getId())).thenReturn(Optional.empty());
        when(changes.due(any())).thenReturn(List.of(due));
        assertThat(service.applyDue()).isEqualTo(0);
        assertThat(due.getStatus()).isEqualTo(PriceChangeStatus.CANCELLED);
    }

    // ── Reading and lists ─────────────────────────────────────────────────────

    @Test
    void theItemPricingShowsMarginAndVatPerList() {
        var applied = CataloguePriceChange.propose(TENANT, item.getId(), trade.getId(), null, new BigDecimal("90.00"), TODAY, "x", MAKER_ID);
        applied.approve(CHECKER_ID, null, TODAY);
        when(changes.appliedForItem(TENANT, item.getId())).thenReturn(List.of(applied));
        var out = service.pricing(TENANT, item.getId(), MAKER);
        assertThat(out.approvalRequired()).isTrue();
        assertThat(out.lists()).hasSize(2);
        var r = out.lists().get(0);
        assertThat(r.defaultList()).isTrue();
        assertThat(r.price()).isEqualByComparingTo("100.00");
        assertThat(r.priceInclVat()).isEqualByComparingTo("115.00");
        assertThat(r.marginPercent()).isEqualByComparingTo("40.0");
        assertThat(out.lists().get(1).price()).isEqualByComparingTo("90.00");
    }

    @Test
    void theDefaultListIsCreatedOnFirstUse() {
        when(lists.findDefault(TENANT)).thenReturn(Optional.empty());
        when(lists.save(any(CataloguePriceList.class))).thenAnswer(i -> i.getArgument(0));
        when(lists.findAllLive(TENANT)).thenReturn(List.of());
        service.lists(TENANT);
        var l = ArgumentCaptor.forClass(CataloguePriceList.class);
        verify(lists).save(l.capture());
        assertThat(l.getValue().getCode()).isEqualTo("RETAIL");
        assertThat(l.getValue().isDefaultList()).isTrue();
    }

    @Test
    void listCodesAndNamesAreUnique() {
        when(lists.codeTaken(TENANT, "TRADE")).thenReturn(true);
        assertThatThrownBy(() -> service.createList(TENANT, new CataloguePriceListRequest("trade", "Trade 2", null, null), CHECKER))
                .isInstanceOf(ConflictException.class).hasMessage("A price list with the code 'TRADE' already exists.");
        when(lists.codeTaken(TENANT, "WHOLE")).thenReturn(false);
        when(lists.nameTaken(eq(TENANT), eq("Retail"), any())).thenReturn(true);
        assertThatThrownBy(() -> service.createList(TENANT, new CataloguePriceListRequest("whole", "Retail", null, null), CHECKER))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void theDefaultListCannotBeDeletedOrSwitchedOff() {
        assertThatThrownBy(() -> service.deleteList(TENANT, retail.getId(), CHECKER)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.updateList(TENANT, retail.getId(), new CataloguePriceListRequest("RETAIL", "Retail", 0, false), CHECKER))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aListWithPriceHistoryCannotBeDeleted() {
        when(changes.countForList(TENANT, trade.getId())).thenReturn(3L);
        assertThatThrownBy(() -> service.deleteList(TENANT, trade.getId(), CHECKER)).isInstanceOf(ConflictException.class).hasMessageContaining("Switch it off");
    }

    @Test
    void directEditsAreRefusedOnlyWhenApprovalIsRequired() {
        assertThatThrownBy(() -> service.requireDirectEditAllowed(TENANT)).isInstanceOf(IllegalArgumentException.class);
        approvalIs(false);
        service.requireDirectEditAllowed(TENANT);
    }

    @Test
    void aDirectEditLeavesAnAppliedHistoryRow() {
        approvalIs(false);
        service.recordDirectChange(TENANT, item, new BigDecimal("100.00"), new BigDecimal("110.00"), MAKER);
        var c = saved();
        assertThat(c.getStatus()).isEqualTo(PriceChangeStatus.APPLIED);
        assertThat(c.getPriceListId()).isEqualTo(retail.getId());
        assertThat(c.getNewPrice()).isEqualByComparingTo("110.00");
    }

}
