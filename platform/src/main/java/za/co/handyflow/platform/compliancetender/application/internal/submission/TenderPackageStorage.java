package za.co.handyflow.platform.compliancetender.application.internal.submission;

import za.co.handyflow.platform.shared.TenantId;

import java.util.UUID;

/**
 * Where a built package is kept. The package service depends on this, not on EvidenceFacade, so the
 * storage can change (a dedicated document store, large-file storage) without touching the builder
 * (ADR-005 decision 4). A stored package is never edited: a rebuild is a new version.
 */
public interface TenderPackageStorage {

    /** What was stored. {@code sha256} is of the stored bytes. */
    record Stored(UUID storageId, String fileName, long sizeBytes, String sha256) {}

    record Loaded(byte[] content, String fileName, String contentType) {}

    Stored store(TenantId tenantId, UUID packageId, String fileName, String contentType, byte[] content,
                 UUID storedBy, String storedByName);

    Loaded load(TenantId tenantId, UUID storageId);
}
