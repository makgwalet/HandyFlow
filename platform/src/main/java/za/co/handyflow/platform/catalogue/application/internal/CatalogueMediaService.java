package za.co.handyflow.platform.catalogue.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import za.co.handyflow.platform.catalogue.application.internal.CatalogueService.Caller;
import za.co.handyflow.platform.catalogue.domain.model.*;
import za.co.handyflow.platform.catalogue.domain.repository.CatalogueAuditEventRepository;
import za.co.handyflow.platform.catalogue.domain.repository.CatalogueItemMediaRepository;
import za.co.handyflow.platform.catalogue.domain.repository.CatalogueItemRepository;
import za.co.handyflow.platform.catalogue.dto.MediaResponse;
import za.co.handyflow.platform.catalogue.dto.MediaUpdateRequest;
import za.co.handyflow.platform.shared.FileStorageService;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * Pictures and documents on an item. Files go through the shared {@link FileStorageService}; the catalogue keeps only the
 * opaque key. A file is checked for size, type and its first bytes before it is stored. Removing a file hides it and keeps
 * the stored bytes, the same as other modules do, so a mistaken removal can be recovered by support.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CatalogueMediaService {

    private final CatalogueItemMediaRepository mediaRepository;
    private final CatalogueItemRepository itemRepository;
    private final CatalogueAuditEventRepository auditRepository;
    private final FileStorageService storage;

    /** A file ready to send: always as a download, never shown inline in our own origin. */
    public record Download(String fileName, String contentType, long sizeBytes, byte[] bytes) {}

    @Transactional(readOnly = true)
    public List<MediaResponse> list(TenantId tenantId, UUID itemId) {
        item(tenantId, itemId);
        return mediaRepository.forItem(tenantId, itemId).stream().map(CatalogueMediaService::toResponse).toList();
    }

    @Transactional
    public MediaResponse upload(TenantId tenantId, UUID itemId, String originalName, String declaredType, byte[] bytes,
                                String title, String altText, Caller caller) {
        var item = item(tenantId, itemId);
        if (bytes == null || bytes.length == 0) throw new IllegalArgumentException("A non-empty file is required.");
        MediaRules.Kind kind = MediaRules.kindOf(declaredType);
        String type = declaredType.toLowerCase().trim();
        if (bytes.length > MediaRules.maxBytes(kind)) {
            throw new IllegalArgumentException("That file is too large. " + (kind == MediaRules.Kind.IMAGE ? "Pictures" : "Documents")
                    + " can be up to " + (MediaRules.maxBytes(kind) / 1024 / 1024) + " MB.");
        }
        if (!MediaRules.signatureMatches(type, bytes, Math.min(bytes.length, 12))) {
            throw new IllegalArgumentException("The file's content does not match its type (" + type + ").");
        }
        if (mediaRepository.count(tenantId, itemId, kind) >= MediaRules.maxCount(kind)) {
            throw new IllegalArgumentException("An item can have at most " + MediaRules.maxCount(kind) + " " + MediaRules.LABEL.get(kind) + ".");
        }
        String name = MediaRules.safeFileName(originalName);
        String cleanTitle = MediaRules.text(title, 120, "The title");
        String cleanAlt = MediaRules.text(altText, 200, "The picture description");

        String key = store(tenantId, itemId, name, type, bytes);
        boolean primary = MediaRules.becomesPrimary(kind, mediaRepository.primaryOf(tenantId, itemId).isPresent());
        int next = (int) mediaRepository.count(tenantId, itemId, kind);
        var media = mediaRepository.save(CatalogueItemMedia.create(tenantId, itemId, kind, cleanTitle, cleanAlt, name, type,
                bytes.length, key, primary, next, caller.userId()));
        audit(tenantId, item, CatalogueAuditAction.MEDIA_ADDED, "Added " + (kind == MediaRules.Kind.IMAGE ? "picture " : "document ") + name, caller);
        return toResponse(media);
    }

    @Transactional
    public MediaResponse update(TenantId tenantId, UUID itemId, UUID mediaId, MediaUpdateRequest r, Caller caller) {
        var item = item(tenantId, itemId);
        var m = load(tenantId, itemId, mediaId);
        if (r == null) throw new IllegalArgumentException("Nothing to change.");
        if (r.title() != null || r.altText() != null) {
            m.describe(r.title() != null ? MediaRules.text(r.title(), 120, "The title") : m.getTitle(),
                    r.altText() != null ? MediaRules.text(r.altText(), 200, "The picture description") : m.getAltText());
        }
        if (Boolean.TRUE.equals(r.primary()) && !m.isPrimary()) {
            if (m.getKind() != MediaRules.Kind.IMAGE) throw new IllegalArgumentException("Only a picture can be the main picture.");
            mediaRepository.primaryOf(tenantId, itemId).ifPresent(old -> {
                old.setPrimary(false);
                mediaRepository.saveAndFlush(old);   // free the one-main-picture slot before taking it
            });
            m.setPrimary(true);
        } else if (Boolean.FALSE.equals(r.primary()) && m.isPrimary()) {
            m.setPrimary(false);
        }
        mediaRepository.save(m);
        audit(tenantId, item, CatalogueAuditAction.MEDIA_CHANGED, "Changed " + m.getFileName(), caller);
        return toResponse(m);
    }

    @Transactional
    public void remove(TenantId tenantId, UUID itemId, UUID mediaId, Caller caller) {
        var item = item(tenantId, itemId);
        var m = load(tenantId, itemId, mediaId);
        boolean wasPrimary = m.isPrimary();
        m.softDelete();
        mediaRepository.saveAndFlush(m);
        if (wasPrimary) {   // the next picture takes over, so the item is never left without a main picture by accident
            mediaRepository.forItem(tenantId, itemId).stream().filter(x -> x.getKind() == MediaRules.Kind.IMAGE).findFirst()
                    .ifPresent(next -> { next.setPrimary(true); mediaRepository.save(next); });
        }
        audit(tenantId, item, CatalogueAuditAction.MEDIA_REMOVED, "Removed " + m.getFileName(), caller);
    }

    @Transactional(readOnly = true)
    public Download download(TenantId tenantId, UUID itemId, UUID mediaId) {
        item(tenantId, itemId);
        var m = load(tenantId, itemId, mediaId);
        try {
            return new Download(m.getFileName(), m.getContentType(), m.getSizeBytes(), storage.retrieve(m.getStorageKey()));
        } catch (IOException e) {
            throw new IllegalStateException("The stored file could not be read.", e);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Stores the bytes and removes them again if the surrounding transaction does not commit. */
    private String store(TenantId tenantId, UUID itemId, String name, String type, byte[] bytes) {
        final String key;
        try {
            key = storage.store("catalogue/" + tenantId.getValue() + "/" + itemId, name, type, bytes);
        } catch (IOException e) {
            throw new IllegalStateException("The file could not be stored.", e);
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCompletion(int status) {
                    if (status != STATUS_COMMITTED) {
                        try { storage.delete(key); } catch (IOException | RuntimeException e) { log.warn("Could not clean up orphaned catalogue file key={}", key, e); }
                    }
                }
            });
        }
        return key;
    }

    private CatalogueItem item(TenantId tenantId, UUID itemId) {
        return itemRepository.findActiveById(tenantId, itemId).orElseThrow(() -> new ResourceNotFoundException("CatalogueItem", itemId.toString()));
    }

    private CatalogueItemMedia load(TenantId tenantId, UUID itemId, UUID mediaId) {
        return mediaRepository.findInItem(tenantId, itemId, mediaId).orElseThrow(() -> new ResourceNotFoundException("CatalogueMedia", mediaId.toString()));
    }

    private void audit(TenantId tenantId, CatalogueItem item, CatalogueAuditAction action, String detail, Caller caller) {
        auditRepository.save(CatalogueAuditEvent.of(tenantId, "ITEM", item.getId(), item.getName(), action, detail, caller.userId()));
    }

    static MediaResponse toResponse(CatalogueItemMedia m) {
        return new MediaResponse(m.getId(), m.getItemId(), m.getKind().name(), m.getTitle(), m.getAltText(), m.getFileName(), m.getContentType(),
                m.getSizeBytes(), m.isPrimary(), m.getSortOrder(), m.getUploadedAt(),
                "/api/v1/catalogue/items/" + m.getItemId() + "/media/" + m.getId() + "/file");
    }
}
