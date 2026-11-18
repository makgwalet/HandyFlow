package za.co.handyflow.platform.contracting.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "contract_templates")
@Getter
@NoArgsConstructor
public class ContractTemplate {

    @Id UUID id;
    @Column(name = "tenant_id")    UUID tenantId;
    String name;
    @Column(name = "contract_type") String contractType;
    String description;
    @Column(name = "body_template", columnDefinition = "TEXT") String bodyTemplate;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    Map<String, String> variables;

    @Column(name = "is_system") boolean isSystem;
    boolean active = true;
    @Column(name = "created_at") Instant createdAt;
    @Column(name = "updated_at") Instant updatedAt;
    @Column(name = "deleted_at") Instant deletedAt;
    @Version long version;

    /** Business version of the wording: starts at 1 and goes up each time an admin edits the template. */
    @Column(name = "template_version") int templateVersion = 1;

    /** Which template this is a version of. The first version's family id is its own id. */
    @Column(name = "family_id") UUID familyId;

    /** DRAFT, INTERNAL_REVIEW, LEGAL_REVIEW, APPROVED, PUBLISHED or SUPERSEDED. See {@link TemplateStatus}. */
    @Column(name = "status") String status = TemplateStatus.DRAFT.name();
    @Column(name = "published_at") Instant publishedAt;
    @Column(name = "superseded_at") Instant supersededAt;
    @Column(name = "legal_area") String legalArea;
    String jurisdiction = "South Africa";

    /** Set by the legal reviewer: the signature standard this document needs. Null means not classified. */
    @Column(name = "required_signing_method") String requiredSigningMethod;

    public static ContractTemplate create(TenantId tenantId, String name,
                                          String contractType, String description,
                                          String bodyTemplate, Map<String, String> variables,
                                          boolean isSystem) {
        ContractTemplate t = new ContractTemplate();
        t.id           = UUID.randomUUID();
        t.familyId     = t.id;
        t.tenantId     = tenantId.getValue();
        t.name         = name;
        t.contractType = contractType;
        t.description  = description;
        t.bodyTemplate = HtmlSanitizer.clean(bodyTemplate);
        t.variables    = variables;
        t.isSystem     = isSystem;
        t.active       = true;
        t.createdAt    = Instant.now();
        t.updatedAt    = Instant.now();
        if (isSystem) {
            // Seeded starter templates are usable straight away but carry no legal review until one is recorded.
            t.status = TemplateStatus.PUBLISHED.name();
            t.publishedAt = t.createdAt;
        }
        return t;
    }

    public TemplateStatus statusEnum() { return TemplateStatus.valueOf(status); }

    private void requireStatus(String action, TemplateStatus... allowed) {
        for (TemplateStatus s : allowed) if (statusEnum() == s) return;
        throw new IllegalStateException("Cannot " + action + " a template that is " + status);
    }

    /** Edits wording while the template is still being drafted or reviewed internally. Published wording is never changed in place: make a new version. */
    public void edit(String name, String contractType, String description, String bodyTemplate,
                     Map<String, String> variables, String legalArea, String requiredSigningMethod) {
        requireStatus("edit", TemplateStatus.DRAFT, TemplateStatus.INTERNAL_REVIEW);
        this.name = name;
        this.contractType = contractType;
        this.description = description;
        this.bodyTemplate = HtmlSanitizer.clean(bodyTemplate);
        this.variables = variables;
        this.legalArea = legalArea;
        this.requiredSigningMethod = requiredSigningMethod;
        this.updatedAt = Instant.now();
    }

    public void submitForInternalReview() { requireStatus("submit for internal review", TemplateStatus.DRAFT); move(TemplateStatus.INTERNAL_REVIEW); }
    public void submitForLegalReview()    { requireStatus("submit for legal review", TemplateStatus.INTERNAL_REVIEW); move(TemplateStatus.LEGAL_REVIEW); }
    public void markApproved()            { requireStatus("approve", TemplateStatus.LEGAL_REVIEW); move(TemplateStatus.APPROVED); }

    /** Back to DRAFT after a rejected or withdrawn review, or when changes are needed. */
    public void returnToDraft() {
        requireStatus("return to draft", TemplateStatus.INTERNAL_REVIEW, TemplateStatus.LEGAL_REVIEW, TemplateStatus.APPROVED);
        move(TemplateStatus.DRAFT);
    }

    public void publish() {
        requireStatus("publish", TemplateStatus.APPROVED);
        this.publishedAt = Instant.now();
        move(TemplateStatus.PUBLISHED);
    }

    public void supersede() {
        requireStatus("supersede", TemplateStatus.PUBLISHED);
        this.supersededAt = Instant.now();
        move(TemplateStatus.SUPERSEDED);
    }

    /** A new DRAFT copy of this template with the next version number. This version stays as it is until the new one is published. */
    public ContractTemplate newVersion() {
        requireStatus("make a new version of", TemplateStatus.PUBLISHED);
        ContractTemplate n = new ContractTemplate();
        n.id = UUID.randomUUID();
        n.familyId = this.familyId != null ? this.familyId : this.id;
        n.tenantId = this.tenantId;
        n.name = this.name;
        n.contractType = this.contractType;
        n.description = this.description;
        n.bodyTemplate = this.bodyTemplate;
        n.variables = this.variables;
        n.isSystem = this.isSystem;
        n.active = true;
        n.legalArea = this.legalArea;
        n.jurisdiction = this.jurisdiction;
        n.requiredSigningMethod = this.requiredSigningMethod;
        n.templateVersion = this.templateVersion + 1;
        n.status = TemplateStatus.DRAFT.name();
        n.createdAt = Instant.now();
        n.updatedAt = n.createdAt;
        return n;
    }

    private void move(TemplateStatus to) {
        this.status = to.name();
        this.updatedAt = Instant.now();
    }

    /** Takes a custom template out of the picker. Contracts already made from it are unaffected. */
    public void archive() {
        if (isSystem) throw new IllegalStateException("System templates cannot be archived.");
        this.active = false;
        this.updatedAt = Instant.now();
    }

    public void softDelete() {
        this.deletedAt = Instant.now();
        this.active    = false;
        this.updatedAt = Instant.now();
    }
}