package za.co.handyflow.platform.secureaccess.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * What was issued, frozen at issue time: the generic document reference (module, source type, source id),
 * the revision, the display fields and the file hash as they were then. Never updated.
 */
@Entity
@Table(name = "sda_share_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SecureShareItem {

    @Id private UUID id;
    @Column(name = "tenant_id", nullable = false)         private UUID tenantId;
    @Column(name = "share_id", nullable = false)          private UUID shareId;
    @Column(name = "doc_module_code", nullable = false)   private String docModuleCode;
    @Column(name = "doc_source_type", nullable = false)   private String docSourceType;
    @Column(name = "doc_source_id", nullable = false)     private UUID docSourceId;
    @Column(name = "revision")                            private String revision;
    @Column(name = "display_number")                      private String displayNumber;
    @Column(name = "title", nullable = false)             private String title;
    @Column(name = "status_at_issue")                     private String statusAtIssue;
    @Column(name = "file_name")                           private String fileName;
    @Column(name = "file_sha256")                         private String fileSha256;
    @Column(name = "sort_order", nullable = false)        private int sortOrder;

    public static SecureShareItem of(UUID tenantId, UUID shareId, String docModuleCode, String docSourceType,
                                     UUID docSourceId, String revision, String displayNumber, String title,
                                     String statusAtIssue, String fileName, String fileSha256, int sortOrder) {
        SecureShareItem i = new SecureShareItem();
        i.id             = UUID.randomUUID();
        i.tenantId       = tenantId;
        i.shareId        = shareId;
        i.docModuleCode  = docModuleCode;
        i.docSourceType  = docSourceType;
        i.docSourceId    = docSourceId;
        i.revision       = revision;
        i.displayNumber  = displayNumber;
        i.title          = title;
        i.statusAtIssue  = statusAtIssue;
        i.fileName       = fileName;
        i.fileSha256     = fileSha256;
        i.sortOrder      = sortOrder;
        return i;
    }
}
