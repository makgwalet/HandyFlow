package za.co.handyflow.platform.legalcompliance.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import za.co.handyflow.platform.legalcompliance.application.internal.CustomReportRules.Filter;
import za.co.handyflow.platform.shared.AggregateRoot;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

/** A saved view over a standard report. Holds no data: the columns, filters and sort are applied to the standard report's own output each time it runs. */
@Entity
@Table(name = "legalcompliance_custom_reports")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CustomReport extends AggregateRoot<CustomReport> {

    @Column(name = "owner_user_id", nullable = false, updatable = false) private UUID ownerUserId;
    @Column(name = "owner_name", length = 255) private String ownerName;
    @Column(nullable = false, length = 120) private String name;
    @Column(length = 500) private String description;
    @Column(name = "base_key", nullable = false, length = 60) private String baseKey;
    @JdbcTypeCode(SqlTypes.JSON) @Column(nullable = false, columnDefinition = "jsonb") private List<String> columns;
    @JdbcTypeCode(SqlTypes.JSON) @Column(nullable = false, columnDefinition = "jsonb") private List<Filter> filters;
    @Column(name = "sort_column", length = 120) private String sortColumn;
    @Column(name = "sort_desc", nullable = false) private boolean sortDesc;
    @Column(nullable = false) private boolean shared;

    public static CustomReport create(TenantId t, UUID ownerId, String ownerName, String name, String description, String baseKey, List<String> columns, List<Filter> filters,
                                      String sortColumn, boolean sortDesc, boolean shared) {
        if (ownerId == null) throw new IllegalArgumentException("A report needs an owner");
        CustomReport r = new CustomReport();
        r.initTenantId(t);
        r.ownerUserId = ownerId;
        r.ownerName = ownerName;
        r.change(name, description, baseKey, columns, filters, sortColumn, sortDesc, shared);
        return r;
    }

    public void change(String name, String description, String baseKey, List<String> columns, List<Filter> filters, String sortColumn, boolean sortDesc, boolean shared) {
        if (name == null || name.trim().length() < 3) throw new IllegalArgumentException("Give the report a name of at least 3 characters");
        if (name.trim().length() > 120) throw new IllegalArgumentException("The name is too long (120 characters at most)");
        if (description != null && description.length() > 500) throw new IllegalArgumentException("The description is too long (500 characters at most)");
        if (baseKey == null || baseKey.isBlank()) throw new IllegalArgumentException("Choose the standard report this is built on");
        if (columns == null || columns.isEmpty()) throw new IllegalArgumentException("Choose at least one column");
        this.name = name.trim();
        this.description = description == null || description.isBlank() ? null : description.trim();
        this.baseKey = baseKey.trim().toUpperCase(java.util.Locale.ROOT);
        this.columns = List.copyOf(columns);
        this.filters = filters == null ? List.of() : List.copyOf(filters);
        this.sortColumn = sortColumn == null || sortColumn.isBlank() ? null : sortColumn;
        this.sortDesc = sortDesc;
        this.shared = shared;
    }

    public boolean isOwnedBy(UUID userId) { return ownerUserId.equals(userId); }
}
