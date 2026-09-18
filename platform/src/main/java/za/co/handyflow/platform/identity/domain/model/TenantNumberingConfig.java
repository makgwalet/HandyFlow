package za.co.handyflow.platform.identity.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Per-tenant override of how one document type's number is built. A missing
 * row for (tenantId, documentType) is the normal case, not an error — see
 * TenantNumberingEngine, which falls back to tenant.documentCode plus the
 * built-in default type code for that documentType.
 * <p>
 * {@code documentType} is deliberately the same string every existing
 * NumberGenerator already passes as {@code sequenceName} into
 * {@code TenantSequenceService.nextValue(...)} (e.g. "INVOICE", "QUOTE",
 * "CREDIT_NOTE") — this table adds tenant-facing formatting on top of an
 * existing counter, it does not replace or reset it.
 */
@Entity
@Table(name = "tenant_numbering_config")
@IdClass(TenantNumberingConfig.Key.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TenantNumberingConfig {

    @Id
    @Column(name = "tenant_id")
    private java.util.UUID tenantId;

    @Id
    @Column(name = "document_type", length = 50)
    private String documentType;

    @Column(name = "type_code", length = 10)
    private String typeCode;

    @Column(name = "prefix_override", length = 20)
    private String prefixOverride;

    @Column(name = "padding")
    private short padding = 6;

    @Column(name = "include_year")
    private boolean includeYear = true;

    @Column(name = "reset_policy", length = 10)
    private String resetPolicy = "NEVER";

    @Column(name = "enabled")
    private boolean enabled = true;

    public static class Key implements java.io.Serializable {
        private java.util.UUID tenantId;
        private String documentType;

        public Key() {}

        public Key(java.util.UUID tenantId, String documentType) {
            this.tenantId = tenantId;
            this.documentType = documentType;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Key key)) return false;
            return java.util.Objects.equals(tenantId, key.tenantId)
                    && java.util.Objects.equals(documentType, key.documentType);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(tenantId, documentType);
        }
    }
}
