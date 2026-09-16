package za.co.handyflow.platform.security.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.security.domain.model.Guard;
import za.co.handyflow.platform.security.domain.model.Post;
import za.co.handyflow.platform.security.domain.model.PostOrder;
import za.co.handyflow.platform.security.domain.model.PostOrderAcknowledgement;
import za.co.handyflow.platform.security.domain.model.PostOrderAttachment;
import za.co.handyflow.platform.security.domain.model.SecurityContact;
import za.co.handyflow.platform.security.domain.repository.GuardRepository;
import za.co.handyflow.platform.security.domain.repository.PostOrderAcknowledgementRepository;
import za.co.handyflow.platform.security.domain.repository.PostOrderAttachmentRepository;
import za.co.handyflow.platform.security.domain.repository.PostOrderContactRepository;
import za.co.handyflow.platform.security.domain.repository.PostOrderRepository;
import za.co.handyflow.platform.security.domain.repository.PostRepository;
import za.co.handyflow.platform.security.domain.repository.SecurityContactRepository;
import za.co.handyflow.platform.security.dto.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Post Orders / My Post, per the product owner's own explicit design —
 * "I think this is the one I'd prioritize for the actual guard
 * experience." See PostOrder's own class comment for the versioning
 * model, and MyPostResponse's own comment for why the guard-facing read
 * lets the guard pick their own post rather than the backend guessing
 * one (no formal post-assignment concept exists yet).
 */
@Service
@RequiredArgsConstructor
public class PostOrderService {

    private final SecurityContactRepository contactRepo;
    private final PostRepository postRepo;
    private final PostOrderRepository orderRepo;
    private final PostOrderAttachmentRepository attachmentRepo;
    private final PostOrderAcknowledgementRepository ackRepo;
    private final PostOrderContactRepository orderContactRepo;
    private final GuardRepository guardRepo;

    // ── Contacts ─────────────────────────────────────────────────────────────

    @Transactional
    public SecurityContactResponse createContact(TenantId tenantId, CreateContactRequest req) {
        SecurityContact c = SecurityContact.create(tenantId, req.siteId(), req.name(), req.role(),
                req.phone(), req.email());
        contactRepo.save(c);
        return toContactResponse(c);
    }

    @Transactional
    public SecurityContactResponse updateContact(TenantId tenantId, UUID id, CreateContactRequest req) {
        SecurityContact c = contactRepo.findByTenantAndId(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("SecurityContact", id.toString()));
        c.update(req.name(), req.role(), req.phone(), req.email());
        contactRepo.save(c);
        return toContactResponse(c);
    }

    @Transactional
    public void deactivateContact(TenantId tenantId, UUID id) {
        SecurityContact c = contactRepo.findByTenantAndId(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("SecurityContact", id.toString()));
        c.deactivate();
        contactRepo.save(c);
    }

    @Transactional(readOnly = true)
    public List<SecurityContactResponse> getContactsForSite(TenantId tenantId, UUID siteId) {
        return contactRepo.findAvailableForSite(tenantId, siteId).stream().map(this::toContactResponse).toList();
    }

    // ── Posts ────────────────────────────────────────────────────────────────

    @Transactional
    public PostResponse createPost(TenantId tenantId, UUID siteId, CreatePostRequest req) {
        Post p = Post.create(tenantId, siteId, req.name(), req.description());
        postRepo.save(p);
        return toPostResponse(p);
    }

    @Transactional
    public PostResponse updatePost(TenantId tenantId, UUID id, CreatePostRequest req) {
        Post p = postRepo.findByTenantAndId(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Post", id.toString()));
        p.update(req.name(), req.description());
        postRepo.save(p);
        return toPostResponse(p);
    }

    @Transactional
    public void deactivatePost(TenantId tenantId, UUID id) {
        Post p = postRepo.findByTenantAndId(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Post", id.toString()));
        p.deactivate();
        postRepo.save(p);
    }

    @Transactional(readOnly = true)
    public List<PostResponse> getPostsForSite(TenantId tenantId, UUID siteId) {
        return postRepo.findActiveForSite(tenantId, siteId).stream().map(this::toPostResponse).toList();
    }

    // ── Post Order lifecycle ────────────────────────────────────────────────

    @Transactional
    public PostOrderResponse createDraft(TenantId tenantId, UUID siteId, CreatePostOrderDraftRequest req, UUID createdBy) {
        int version = orderRepo.nextVersion(tenantId, siteId, req.postId());
        PostOrder o = PostOrder.createDraft(tenantId, siteId, req.postId(), version,
                req.instructions(), req.duties(), req.emergencyProcedures(), req.restrictedAreas(),
                req.accessRules(), createdBy);
        orderRepo.save(o);
        if (req.contactIds() != null && !req.contactIds().isEmpty()) {
            orderContactRepo.replaceContactsForOrder(o.getId(), req.contactIds());
        }
        return toOrderResponse(o);
    }

    @Transactional
    public PostOrderResponse updateDraft(TenantId tenantId, UUID id, CreatePostOrderDraftRequest req) {
        PostOrder o = orderRepo.findByTenantAndId(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("PostOrder", id.toString()));
        try {
            o.updateDraft(req.instructions(), req.duties(), req.emergencyProcedures(),
                    req.restrictedAreas(), req.accessRules());
        } catch (IllegalStateException e) {
            throw new HandyFlowException(e.getMessage(), HttpStatus.CONFLICT, "INVALID_STATUS");
        }
        orderRepo.save(o);
        if (req.contactIds() != null) {
            orderContactRepo.replaceContactsForOrder(o.getId(), req.contactIds());
        }
        return toOrderResponse(o);
    }

    /**
     * Publishing is the one operation that touches two PostOrder rows —
     * the new DRAFT becoming ACTIVE, and whatever was previously ACTIVE
     * for this same site/post becoming SUPERSEDED — in the same
     * transaction, so a guard can never observe a moment with zero or
     * two ACTIVE versions for the same site/post. Existing
     * acknowledgments are NOT carried forward to the new version — a
     * guard who already acknowledged v1 still needs to acknowledge v2,
     * per the product owner's own "Important changes" design (a new
     * version is a genuine update requiring fresh acknowledgment, not
     * silently inherited).
     */
    @Transactional
    public PostOrderResponse publish(TenantId tenantId, UUID id, UUID publishedBy) {
        PostOrder draft = orderRepo.findByTenantAndId(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("PostOrder", id.toString()));

        orderRepo.findCurrentActive(tenantId, draft.getSiteId(), draft.getPostId())
                .ifPresent(PostOrder::supersede);

        try {
            draft.publish(publishedBy);
        } catch (IllegalStateException e) {
            throw new HandyFlowException(e.getMessage(), HttpStatus.CONFLICT, "INVALID_STATUS");
        }
        orderRepo.save(draft);
        return toOrderResponse(draft);
    }

    @Transactional
    public void addAttachment(TenantId tenantId, UUID postOrderId, AddPostOrderAttachmentRequest req) {
        orderRepo.findByTenantAndId(tenantId, postOrderId)
                .orElseThrow(() -> new ResourceNotFoundException("PostOrder", postOrderId.toString()));
        attachmentRepo.save(PostOrderAttachment.create(tenantId, postOrderId, req.fileUrl(), req.fileName()));
    }

    @Transactional(readOnly = true)
    public List<PostOrderResponse> getHistory(TenantId tenantId, UUID siteId, UUID postId) {
        return orderRepo.findHistory(tenantId, siteId, postId).stream().map(this::toOrderResponse).toList();
    }

    // ── Guard-facing ─────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public MyPostResponse getMyPost(TenantId tenantId, UUID siteId, UUID guardId) {
        PostOrderResponse siteLevel = orderRepo.findActiveSiteLevel(tenantId, siteId)
                .map(this::toOrderResponse).orElse(null);
        boolean siteNeedsAck = siteLevel != null && needsAcknowledgment(tenantId, siteLevel.id(), siteLevel.version(), guardId);

        List<MyPostResponse.PostSummary> posts = postRepo.findActiveForSite(tenantId, siteId).stream()
                .map(p -> new MyPostResponse.PostSummary(p.getId(), p.getName(),
                        orderRepo.findActiveForPost(tenantId, p.getId()).isPresent()))
                .toList();

        return new MyPostResponse(siteId, siteLevel, siteNeedsAck, posts);
    }

    @Transactional(readOnly = true)
    public PostOrderResponse getMyPostOrderForPost(TenantId tenantId, UUID postId) {
        PostOrder o = orderRepo.findActiveForPost(tenantId, postId)
                .orElseThrow(() -> new ResourceNotFoundException("PostOrder", "no active order for post " + postId));
        return toOrderResponse(o);
    }

    private boolean needsAcknowledgment(TenantId tenantId, UUID postOrderId, int version, UUID guardId) {
        return ackRepo.findByOrderVersionAndGuard(tenantId, postOrderId, version, guardId).isEmpty();
    }

    @Transactional
    public PostOrderAcknowledgementResponse acknowledge(TenantId tenantId, UUID postOrderId, UUID guardId,
                                                        AcknowledgePostOrderRequest req) {
        PostOrder o = orderRepo.findByTenantAndId(tenantId, postOrderId)
                .orElseThrow(() -> new ResourceNotFoundException("PostOrder", postOrderId.toString()));
        Instant when = Boolean.TRUE.equals(req.acknowledgedOffline()) && req.acknowledgedAt() != null
                ? req.acknowledgedAt() : Instant.now();
        PostOrderAcknowledgement ack = PostOrderAcknowledgement.create(tenantId, postOrderId, o.getVersion(),
                guardId, when, req.deviceHardwareId(), Boolean.TRUE.equals(req.acknowledgedOffline()));
        ackRepo.save(ack);
        return toAckResponse(ack);
    }

    @Transactional(readOnly = true)
    public List<PostOrderAcknowledgementResponse> getAcknowledgementsForOrder(TenantId tenantId, UUID postOrderId) {
        return ackRepo.findByPostOrder(tenantId, postOrderId).stream().map(this::toAckResponse).toList();
    }

    // ── Mappers ──────────────────────────────────────────────────────────────

    private SecurityContactResponse toContactResponse(SecurityContact c) {
        return new SecurityContactResponse(c.getId(), c.getSiteId(), c.getName(), c.getRole(),
                c.getPhone(), c.getEmail(), c.isActive());
    }

    private PostResponse toPostResponse(Post p) {
        return new PostResponse(p.getId(), p.getSiteId(), p.getName(), p.getDescription(), p.isActive());
    }

    private PostOrderResponse toOrderResponse(PostOrder o) {
        String postName = o.getPostId() != null
                ? postRepo.findByTenantAndId(o.getTenantId(), o.getPostId()).map(Post::getName).orElse(null)
                : null;
        List<SecurityContactResponse> contacts = orderContactRepo.findContactIdsForOrder(o.getId()).stream()
                .map(id -> contactRepo.findByTenantAndId(o.getTenantId(), id).map(this::toContactResponse).orElse(null))
                .filter(java.util.Objects::nonNull)
                .toList();
        List<PostOrderAttachmentResponse> attachments = attachmentRepo.findByPostOrder(o.getTenantId(), o.getId()).stream()
                .map(a -> new PostOrderAttachmentResponse(a.getId(), a.getFileUrl(), a.getFileName()))
                .toList();
        return new PostOrderResponse(o.getId(), o.getSiteId(), o.getPostId(), postName, o.getVersion(), o.getStatus(),
                o.getEffectiveFrom(), o.getEffectiveTo(), o.getInstructions(), o.getDuties(),
                o.getEmergencyProcedures(), o.getRestrictedAreas(), o.getAccessRules(),
                o.getCreatedBy(), o.getPublishedBy(), o.getPublishedAt(), o.getCreatedAt(),
                contacts, attachments);
    }

    private PostOrderAcknowledgementResponse toAckResponse(PostOrderAcknowledgement a) {
        String guardName = guardRepo.findActiveById(a.getTenantId(), a.getGuardId())
                .map(Guard::getFullName).orElse("Unknown");
        return new PostOrderAcknowledgementResponse(a.getId(), a.getPostOrderId(), a.getVersion(),
                a.getGuardId(), guardName, a.getAcknowledgedAt(), a.getDeviceHardwareId(),
                a.isAcknowledgedOffline(), a.getSyncedAt());
    }
}
