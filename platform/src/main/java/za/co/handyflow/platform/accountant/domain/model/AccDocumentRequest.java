package za.co.handyflow.platform.accountant.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

/**
 * Closes the accountant module audit's "document requests" gap —
 * deliberately scoped out of the workpaper system pass as a genuinely
 * separate feature with its own lifecycle (asking a client for
 * specific items, not managing files staff already have).
 * <p>
 * FIX: items was originally mapped as List&lt;String&gt; via
 * @JdbcTypeCode(SqlTypes.JSON) — Hibernate 6's native JSON support.
 * Confirmed via real testing this broke schema validation on an
 * entirely unrelated entity (ClinicConsultation.icd10Codes, a
 * text[]-mapped List&lt;String&gt; with its own explicit
 * @JdbcTypeCode(SqlTypes.ARRAY)) — Hibernate's SessionFactory bootstrap
 * processes the whole application's entity metadata in one pass, and
 * introducing the first @JdbcTypeCode(SqlTypes.JSON) usage anywhere in
 * this application is the most plausible trigger for that other,
 * already-explicitly-annotated collection mapping resolving
 * differently at boot.
 * <p>
 * Now stored as a plain String (raw JSON text) instead — Hibernate
 * treats this exactly like any other TEXT-ish column (matching
 * file_content_base64 elsewhere this session), no special JDBC type
 * handling introduced into the application at all. Serialization
 * to/from List&lt;String&gt; happens in AccDocumentRequestService via a
 * standard Jackson ObjectMapper, not in this entity.
 * <p>
 * FIX: confirmed via real testing that columnDefinition = "jsonb" only
 * affects DDL/schema validation, not how the runtime bind parameter is
 * typed — Postgres doesn't implicitly cast a varchar bind parameter to
 * jsonb on insert, so the very first real write failed with "column
 * items is of type jsonb but expression is of type character varying".
 * @ColumnTransformer(write = "?::jsonb") is a purely SQL-level fix —
 * it just wraps the insert/update parameter with an explicit cast in
 * the generated SQL, not a JDBC-type-system change, so it shouldn't
 * reintroduce the cross-entity interference @JdbcTypeCode(SqlTypes.JSON)
 * caused on the Clinic module's icd10Codes mapping.
 */
@Entity(name = "AccountantDocumentRequest")
@Table(name = "acc_document_requests")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AccDocumentRequest {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "client_id", nullable = false) private UUID clientId;
    @Column(name = "folder_id") private UUID folderId;
    @Column(name = "requested_by") private UUID requestedBy;
    @Column(name = "description", nullable = false, columnDefinition = "TEXT") private String description;

    @org.hibernate.annotations.ColumnTransformer(write = "?::jsonb")
    @Column(name = "items", nullable = false, columnDefinition = "jsonb")
    private String itemsJson;

    @Column(name = "status", nullable = false) private String status = "PENDING";
    @Column(name = "due_date") private LocalDate dueDate;
    @Column(name = "completed_at") private Instant completedAt;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    public static AccDocumentRequest create(UUID tenantId, UUID clientId, UUID folderId, UUID requestedBy,
                                            String description, String itemsJson, LocalDate dueDate) {
        AccDocumentRequest r = new AccDocumentRequest();
        r.tenantId    = tenantId;
        r.clientId    = clientId;
        r.folderId    = folderId;
        r.requestedBy = requestedBy;
        r.description = description;
        r.itemsJson   = itemsJson;
        r.dueDate     = dueDate;
        r.createdAt   = Instant.now();
        r.updatedAt   = Instant.now();
        return r;
    }

    private static final Set<String> VALID_STATUSES = Set.of("PENDING", "PARTIAL", "COMPLETE", "CANCELLED");

    public void updateStatus(String newStatus) {
        if (!VALID_STATUSES.contains(newStatus)) {
            throw new IllegalArgumentException("Unknown status: " + newStatus);
        }
        this.status = newStatus;
        if ("COMPLETE".equals(newStatus)) {
            this.completedAt = Instant.now();
        } else if (this.completedAt != null) {
            this.completedAt = null;
        }
        this.updatedAt = Instant.now();
    }
}