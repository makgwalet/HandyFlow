package za.co.handyflow.platform.accounting.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Groups of companies and consolidation (finance F26). */
public final class GroupDtos {
    private GroupDtos() {}

    public record CreateGroup(String name) {}
    public record CreateInvite(String label, BigDecimal ownershipPercent) {}
    public record Invite(UUID id, String label, BigDecimal ownershipPercent, Instant expiresAt, Instant createdAt) {}
    public record InviteCreated(UUID id, String code, Instant expiresAt) {}
    public record JoinRequest(String code) {}
    public record SetOwnership(BigDecimal ownershipPercent) {}

    public record Member(UUID tenantId, String name, String role, BigDecimal ownershipPercent, Instant joinedAt) {}
    /** Subsidiaries see only the group's name and its parent, not the other members. members is empty for them. */
    public record GroupInfo(UUID id, String name, String myRole, UUID parentTenantId, String parentName, List<Member> members) {}

    public record AccountRef(String code, String name, String type) {}

    public record MapRequest(UUID tenantId, String accountCode, String groupCode, String groupName) {}
    public record Mapping(UUID id, UUID tenantId, String companyName, String accountCode, String groupCode, String groupName) {}

    public record EliminationRequest(String name, UUID tenantAId, String accountCodeA, UUID tenantBId, String accountCodeB) {}
    public record Elimination(UUID id, String name, UUID tenantAId, String companyA, String accountCodeA, UUID tenantBId, String companyB, String accountCodeB) {}

    public record Entity(UUID tenantId, String name, BigDecimal ownershipPercent) {}
    public record Line(String code, String name, String type, List<BigDecimal> amounts, BigDecimal elimination, BigDecimal consolidated) {}
    public record Check(String name, BigDecimal sideA, BigDecimal sideB, BigDecimal difference) {}
    public record Report(LocalDate from, LocalDate to, List<Entity> entities, List<Line> lines, List<Check> checks,
                         BigDecimal income, BigDecimal expenses, BigDecimal profit, BigDecimal assets, BigDecimal liabilities, BigDecimal equity,
                         BigDecimal balanceDifference, List<String> warnings) {}
}
