package za.co.handyflow.platform.crm.dto;

import java.util.List;
import java.util.UUID;

/** Who is in a segment: the total, and the first rows (at most the limit). */
public record SegmentMembersResponse(int total, boolean truncated, List<Member> members) {
    public record Member(UUID id, String name, String email, String phone, String customerType, String status) {}
}
