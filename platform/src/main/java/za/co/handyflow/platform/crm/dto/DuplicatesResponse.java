package za.co.handyflow.platform.crm.dto;

import za.co.handyflow.platform.crm.domain.model.CustomerType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Groups of customers that look like the same business entered more than once. scanned is how many customers were compared;
 * truncated is true when the tenant has more than the finder looks at.
 */
public record DuplicatesResponse(List<Group> groups, int scanned, boolean truncated) {

    public record Group(List<Member> customers, List<String> reasons) {}

    public record Member(UUID id, String name, String email, String phone, String taxNumber, CustomerType customerType, Instant createdAt) {}
}
