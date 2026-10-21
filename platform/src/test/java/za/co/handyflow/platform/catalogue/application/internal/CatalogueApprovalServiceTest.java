package za.co.handyflow.platform.catalogue.application.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
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
import za.co.handyflow.platform.catalogue.dto.ItemStatusRequest;
import za.co.handyflow.platform.shared.ConflictException;
import za.co.handyflow.platform.shared.TenantId;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CatalogueApprovalServiceTest {
    private static final TenantId TENANT = TenantId.of(UUID.randomUUID());
    private static final Caller MAKER = new Caller(UUID.randomUUID(), false, false, false, false, false);
    private static final Caller CHECKER = new Caller(UUID.randomUUID(), false, false, false, false, true);

    @Mock CatalogueItemApprovalRepository approvals;
    @Mock CatalogueItemRepository items;
    @Mock CatalogueAuditEventRepository audit;
    @Mock CatalogueService catalogue;
    @Mock CataloguePricingService pricing;
    CatalogueApprovalService service;
    CatalogueItem draft;

    @BeforeEach
    void setUp() {
        service = new CatalogueApprovalService(approvals, items, audit, catalogue, pricing);
        draft = CatalogueItem.create(TENANT, null, new CatalogueItem.Details("Brick", null, "B-1", null, null, CatalogueItemType.PRODUCT, "each",
                new BigDecimal("5.00"), null, new BigDecimal("15.00"), false), CatalogueItemStatus.DRAFT);
        when(items.findActiveById(TENANT, draft.getId())).thenReturn(Optional.of(draft));
        when(pricing.itemApprovalRequired(TENANT)).thenReturn(true);
        when(approvals.pendingFor(TENANT, draft.getId())).thenReturn(Optional.empty());
        when(approvals.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    private CatalogueItemApproval pending() {
        var a = CatalogueItemApproval.request(TENANT, draft.getId(), "please", MAKER.userId());
        when(approvals.findInTenant(TENANT, a.getId())).thenReturn(Optional.of(a));
        return a;
    }

    @Test
    void submitsADraftAndWritesHistory() {
        var r = service.submit(TENANT, draft.getId(), " ready ", MAKER);
        assertThat(r.status()).isEqualTo("PENDING");
        assertThat(r.note()).isEqualTo("ready");
        assertThat(r.requestedBy()).isEqualTo(MAKER.userId());
        var ev = ArgumentCaptor.forClass(CatalogueAuditEvent.class);
        verify(audit).save(ev.capture());
        assertThat(ev.getValue().getAction()).isEqualTo(CatalogueAuditAction.APPROVAL_REQUESTED);
    }

    @Test
    void refusesToSubmitWhenApprovalIsOffTheItemIsNotADraftOrIsAlreadyWaiting() {
        when(pricing.itemApprovalRequired(TENANT)).thenReturn(false);
        assertThatThrownBy(() -> service.submit(TENANT, draft.getId(), null, MAKER)).isInstanceOf(IllegalArgumentException.class);
        when(pricing.itemApprovalRequired(TENANT)).thenReturn(true);
        when(approvals.pendingFor(TENANT, draft.getId())).thenReturn(Optional.of(CatalogueItemApproval.request(TENANT, draft.getId(), null, MAKER.userId())));
        assertThatThrownBy(() -> service.submit(TENANT, draft.getId(), null, MAKER)).isInstanceOf(ConflictException.class);
        when(approvals.pendingFor(TENANT, draft.getId())).thenReturn(Optional.empty());
        draft.changeStatus(CatalogueItemStatus.ACTIVE, null);
        assertThatThrownBy(() -> service.submit(TENANT, draft.getId(), null, MAKER)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("draft");
    }

    @Test
    void anApproverActivatesTheItemAndTheRequestIsClosed() {
        var a = pending();
        var r = service.approve(TENANT, a.getId(), "ok", CHECKER);
        assertThat(r.status()).isEqualTo("APPROVED");
        assertThat(r.decidedBy()).isEqualTo(CHECKER.userId());
        var req = ArgumentCaptor.forClass(ItemStatusRequest.class);
        verify(catalogue).changeStatus(eq(TENANT), eq(draft.getId()), req.capture(), eq(CHECKER));
        assertThat(req.getValue().status()).isEqualTo(CatalogueItemStatus.ACTIVE);
    }

    @Test
    void nobodyApprovesTheirOwnRequestEvenWithThePermission() {
        var a = pending();
        var self = new Caller(MAKER.userId(), false, false, false, false, true);
        assertThatThrownBy(() -> service.approve(TENANT, a.getId(), null, self)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.reject(TENANT, a.getId(), "no good", self)).isInstanceOf(AccessDeniedException.class);
        verify(catalogue, never()).changeStatus(any(), any(), any(), any());
    }

    @Test
    void approvingNeedsThePermission() {
        var a = pending();
        assertThatThrownBy(() -> service.approve(TENANT, a.getId(), null, MAKER)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void ifTheActivationChecksFailTheRequestStaysOpen() {
        var a = pending();
        when(catalogue.changeStatus(eq(TENANT), eq(draft.getId()), any(), eq(CHECKER))).thenThrow(new IllegalArgumentException("Required attribute 'Colour' is missing."));
        assertThatThrownBy(() -> service.approve(TENANT, a.getId(), null, CHECKER)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Colour");
        verify(approvals, never()).save(any());
    }

    @Test
    void rejectingNeedsAReasonAndLeavesTheItemADraft() {
        var a = pending();
        assertThatThrownBy(() -> service.reject(TENANT, a.getId(), " ", CHECKER)).isInstanceOf(IllegalArgumentException.class);
        var r = service.reject(TENANT, a.getId(), "Price looks wrong", CHECKER);
        assertThat(r.status()).isEqualTo("REJECTED");
        assertThat(r.decisionNote()).isEqualTo("Price looks wrong");
        verify(catalogue, never()).changeStatus(any(), any(), any(), any());
    }

    @Test
    void aDecidedRequestCannotBeDecidedAgain() {
        var a = pending();
        service.reject(TENANT, a.getId(), "No", CHECKER);
        assertThatThrownBy(() -> service.approve(TENANT, a.getId(), null, CHECKER)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void onlyTheRequesterCanWithdraw() {
        var a = pending();
        assertThatThrownBy(() -> service.withdraw(TENANT, a.getId(), CHECKER)).isInstanceOf(AccessDeniedException.class);
        assertThat(service.withdraw(TENANT, a.getId(), MAKER).status()).isEqualTo("WITHDRAWN");
    }

    @Test
    void theQueueShowsItemNames() {
        var a = CatalogueItemApproval.request(TENANT, draft.getId(), null, MAKER.userId());
        when(approvals.byStatus(TENANT, ItemApprovalStatus.PENDING)).thenReturn(List.of(a));
        when(items.findLiveByIds(eq(TENANT), any())).thenReturn(List.of(draft));
        var q = service.queue(TENANT, null);
        assertThat(q.size()).isEqualTo(1);
        assertThat(q.get(0).itemName()).isEqualTo("Brick");
        assertThat(q.get(0).itemStatus()).isEqualTo("DRAFT");
    }
}
