package za.co.handyflow.platform.crm.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import za.co.handyflow.platform.crm.domain.model.CustomerType;

/**
 * What merging would do. conflicts are the fields where both records have a different value (the kept record's wins unless the person chooses
 * otherwise); moves counts what is moved onto the kept record; blockers are reasons the merge cannot go ahead.
 */
public record MergePreviewResponse(Side keep, Side merge, List<Conflict> conflicts, Moves moves, List<String> willFill, List<String> blockers, boolean canMerge) {
    public record Side(UUID id, String name, String email, String phone, CustomerType customerType, Instant createdAt) {}
    public record Conflict(String field, String label, String keep, String other) {}
    public record Moves(int contacts, int tasks, int communications, int opportunities, int campaigns, int branches, int activities) {}
}
