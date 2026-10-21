package za.co.handyflow.platform.catalogue.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.catalogue.application.internal.CatalogueService.Caller;
import za.co.handyflow.platform.catalogue.domain.model.*;
import za.co.handyflow.platform.catalogue.domain.repository.CatalogueAuditEventRepository;
import za.co.handyflow.platform.catalogue.domain.repository.CatalogueItemApprovalRepository;
import za.co.handyflow.platform.catalogue.domain.repository.CatalogueItemRepository;
import za.co.handyflow.platform.catalogue.dto.ItemApprovalResponse;
import za.co.handyflow.platform.catalogue.dto.ItemStatusRequest;
import za.co.handyflow.platform.shared.ConflictException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.*;

/**
 * Approval before a draft item goes live, for tenants that switched it on. The creator submits a draft; someone else with
 * CATALOGUE_APPROVE approves it (the item becomes active, through the ordinary status change and its checks) or rejects it
 * with a reason. Nobody decides on their own request: the service refuses it and the database refuses it too.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CatalogueApprovalService {

    private final CatalogueItemApprovalRepository approvalRepository;
    private final CatalogueItemRepository itemRepository;
    private final CatalogueAuditEventRepository auditRepository;
    private final CatalogueService catalogueService;
    private final CataloguePricingService pricing;

    @Transactional
    public ItemApprovalResponse submit(TenantId tenantId, UUID itemId, String note, Caller caller) {
        if (caller.userId() == null) throw new AccessDeniedException("Sign in as a user to submit an item.");
        var item = item(tenantId, itemId);
        if (!pricing.itemApprovalRequired(tenantId)) throw new IllegalArgumentException("Items do not need approval here. Activate the item directly.");
        if (item.getStatus() != CatalogueItemStatus.DRAFT) throw new IllegalArgumentException("Only a draft can be submitted for approval.");
        if (item.isVariant()) throw new IllegalArgumentException("Submit the main item; its variants are approved with it.");
        if (approvalRepository.pendingFor(tenantId, itemId).isPresent()) throw new ConflictException("This item is already waiting for approval.");
        var a = approvalRepository.save(CatalogueItemApproval.request(tenantId, itemId, note, caller.userId()));
        audit(tenantId, item, CatalogueAuditAction.APPROVAL_REQUESTED, a.getNote() == null ? "Submitted for approval" : "Submitted for approval: " + a.getNote(), caller);
        return toResponse(a, item);
    }

    @Transactional
    public ItemApprovalResponse approve(TenantId tenantId, UUID approvalId, String note, Caller caller) {
        requireApprover(caller);
        var a = load(tenantId, approvalId);
        var item = item(tenantId, a.getItemId());
        a.approve(caller.userId(), note);   // refuses the requester and a request that is no longer open
        if (item.getStatus() != CatalogueItemStatus.DRAFT)
            throw new ConflictException("The item is no longer a draft, so there is nothing to approve. Withdraw or reject the request.");
        // The ordinary activation, with all its checks; if one fails nothing is saved and the request stays open.
        catalogueService.changeStatus(tenantId, item.getId(), new ItemStatusRequest(CatalogueItemStatus.ACTIVE, null, null), caller);
        approvalRepository.save(a);
        audit(tenantId, item, CatalogueAuditAction.ITEM_APPROVED, a.getDecisionNote() == null ? "Approved and activated" : "Approved and activated: " + a.getDecisionNote(), caller);
        log.info("Catalogue item approved item={} by={}", item.getId(), caller.userId());
        return toResponse(a, item);
    }

    @Transactional
    public ItemApprovalResponse reject(TenantId tenantId, UUID approvalId, String note, Caller caller) {
        requireApprover(caller);
        var a = load(tenantId, approvalId);
        var item = item(tenantId, a.getItemId());
        a.reject(caller.userId(), note);
        approvalRepository.save(a);
        audit(tenantId, item, CatalogueAuditAction.ITEM_REJECTED, "Rejected: " + a.getDecisionNote(), caller);
        return toResponse(a, item);
    }

    @Transactional
    public ItemApprovalResponse withdraw(TenantId tenantId, UUID approvalId, Caller caller) {
        var a = load(tenantId, approvalId);
        var item = item(tenantId, a.getItemId());
        a.withdraw(caller.userId());
        approvalRepository.save(a);
        audit(tenantId, item, CatalogueAuditAction.APPROVAL_WITHDRAWN, "Request withdrawn", caller);
        return toResponse(a, item);
    }

    @Transactional(readOnly = true)
    public List<ItemApprovalResponse> queue(TenantId tenantId, ItemApprovalStatus status) {
        var rows = approvalRepository.byStatus(tenantId, status == null ? ItemApprovalStatus.PENDING : status);
        Map<UUID, CatalogueItem> items = new HashMap<>();
        itemRepository.findLiveByIds(tenantId, rows.stream().map(CatalogueItemApproval::getItemId).distinct().toList()).forEach(i -> items.put(i.getId(), i));
        return rows.stream().map(a -> toResponse(a, items.get(a.getItemId()))).toList();
    }

    /** The latest request for an item, if any. */
    @Transactional(readOnly = true)
    public Optional<ItemApprovalResponse> latest(TenantId tenantId, UUID itemId) {
        var item = item(tenantId, itemId);
        return approvalRepository.forItem(tenantId, itemId).stream().findFirst().map(a -> toResponse(a, item));
    }

    private static void requireApprover(Caller caller) {
        if (!caller.canApproveItems()) throw new AccessDeniedException("You are not allowed to approve or reject items.");
    }

    private CatalogueItemApproval load(TenantId tenantId, UUID id) {
        return approvalRepository.findInTenant(tenantId, id).orElseThrow(() -> new ResourceNotFoundException("ItemApproval", id.toString()));
    }

    private CatalogueItem item(TenantId tenantId, UUID itemId) {
        return itemRepository.findActiveById(tenantId, itemId).orElseThrow(() -> new ResourceNotFoundException("CatalogueItem", itemId.toString()));
    }

    private void audit(TenantId tenantId, CatalogueItem item, CatalogueAuditAction action, String detail, Caller caller) {
        auditRepository.save(CatalogueAuditEvent.of(tenantId, "ITEM", item.getId(), item.getName(), action, detail, caller.userId()));
    }

    static ItemApprovalResponse toResponse(CatalogueItemApproval a, CatalogueItem item) {
        return new ItemApprovalResponse(a.getId(), a.getItemId(), item == null ? null : item.getName(), item == null ? null : item.getSku(),
                item == null ? null : item.getStatus().name(), a.getStatus().name(), a.getRequestedBy(), a.getRequestedAt(), a.getNote(),
                a.getDecidedBy(), a.getDecidedAt(), a.getDecisionNote());
    }
}
