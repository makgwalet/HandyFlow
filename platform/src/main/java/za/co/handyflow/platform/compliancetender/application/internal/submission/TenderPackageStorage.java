package za.co.handyflow.platform.compliancetender.application.internal.submission;

import za.co.handyflow.platform.shared.TenantId;

import java.util.UUID;

/**
 * Where a built package's bytes are kept (ADR-005 decision 4). The package service and the rest of the
 * tender code depend on this, never on a file system, a database or an object store. A package is never
 * edited: a rebuild is a new version with a new key.
 */
public interface TenderPackageStorage {

    /** @param storageKey opaque; persist it and pass it back unchanged, never build or parse one */
    record Stored(String storageKey, long sizeBytes, String sha256) {}

    Stored store(TenantId tenantId, UUID tenderId, UUID packageId, String fileName, String contentType, byte[] content);

    byte[] load(String storageKey);

    /** Idempotent: a key that is already gone is not an error. */
    void delete(String storageKey);
}
