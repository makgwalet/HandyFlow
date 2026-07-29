package za.co.handyflow.platform.shared.internal.storage;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Backing table for {@link DatabaseFileStorageService}. Deliberately not in
 * a "domain.model" package and not exposed outside this package — callers
 * only ever see {@link za.co.handyflow.platform.shared.FileStorageService}'s
 * opaque storage keys, never this entity. Keeping it package-private in
 * spirit (even though Java doesn't let a JPA entity itself be non-public
 * easily) means swapping the storage backend later touches only this
 * package, nothing that depends on the port.
 */
@Entity
@Table(name = "stored_files")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StoredFileBlob {

    /** The opaque storage key FileStorageService callers already hold — used directly as the PK. */
    @Id
    @Column(name = "storage_key", length = 500)
    private String storageKey;

    @Column(name = "content_type")
    private String contentType;

    /**
     * FIX: deliberately NOT annotated @Lob. On PostgreSQL, @Lob on a byte[]
     * makes Hibernate expect an `oid` column (PostgreSQL's large-object
     * mechanism — a separate table + reference, not a plain column), while
     * the migration creates a plain BYTEA column. That mismatch fails
     * Hibernate's schema validation at startup: "found [bytea], but
     * expecting [oid]". A plain byte[] field with no @Lob maps to BYTEA
     * directly, which is what we actually want here — simpler, and BYTEA is
     * the normal way to store moderate-sized binary data in Postgres.
     */
    @Column(name = "content", nullable = false)
    private byte[] content;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "created_at")
    private Instant createdAt;

    public static StoredFileBlob create(String storageKey, String contentType, byte[] content) {
        StoredFileBlob b = new StoredFileBlob();
        b.storageKey     = storageKey;
        b.contentType    = contentType;
        b.content        = content;
        b.sizeBytes      = content.length;
        b.createdAt      = Instant.now();
        return b;
    }
}