package za.co.handyflow.platform.crm.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import za.co.handyflow.platform.crm.domain.model.CustomerStatus;
import za.co.handyflow.platform.crm.domain.model.CustomerType;

/** A company in context: who is above it, who is directly under it, and the opportunities of the whole group. */
public record HierarchyResponse(
        Node self, List<Node> ancestors, List<Node> children, int branchCount, Rollup rollup
) {
    /** parentId is null for a top-level company. */
    public record Node(UUID id, String name, UUID parentId, CustomerType customerType, CustomerStatus status) {}

    /** Opportunities of this company plus every company below it. */
    public record Rollup(int companies, int openCount, BigDecimal openValue, BigDecimal weightedValue, int wonCount, BigDecimal wonValue) {}
}
