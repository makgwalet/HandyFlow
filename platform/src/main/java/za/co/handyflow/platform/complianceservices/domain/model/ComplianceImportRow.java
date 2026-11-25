package za.co.handyflow.platform.complianceservices.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.util.UUID;

/** One line of an imported file and what became of it. The fields are kept in the fixed column order of the import kind. */
@Entity(name = "ComplianceImportRow")
@Table(name = "compliance_import_rows")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ComplianceImportRow {
    @Id
    private UUID id = UUID.randomUUID();
    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;
    @Column(name = "batch_id", nullable = false)
    private UUID batchId;
    @Column(name = "row_number", nullable = false)
    private int rowNumber;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String fields;
    @Column(nullable = false)
    private String status;
    @Column(length = 500)
    private String message;
    @Column(name = "target_id")
    private UUID targetId;
    @Version
    private Long version;

    public static ComplianceImportRow of(TenantId tenantId, UUID batchId, int rowNumber, java.util.List<String> fields, String status, String message) {
        ComplianceImportRow r = new ComplianceImportRow();
        r.tenantId = tenantId; r.batchId = batchId; r.rowNumber = rowNumber;
        r.fields = ImportRules.join(fields);
        r.status = status;
        r.message = message == null ? null : message.length() > 500 ? message.substring(0, 500) : message;
        return r;
    }

    public java.util.List<String> fieldList() { return ImportRules.split(fields); }

    public void imported(UUID targetId) {
        if (!ImportRules.OK.equals(status)) throw new IllegalStateException("Only a row that passed the preview can be imported");
        this.status = ImportRules.IMPORTED; this.targetId = targetId; this.message = "Created";
    }

    public void setAside(String status, String message) {
        if (!ImportRules.OK.equals(this.status)) throw new IllegalStateException("Only a row that passed the preview can change");
        this.status = status; this.message = message.length() > 500 ? message.substring(0, 500) : message;
    }
}
