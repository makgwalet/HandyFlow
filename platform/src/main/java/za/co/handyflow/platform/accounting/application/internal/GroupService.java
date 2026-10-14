package za.co.handyflow.platform.accounting.application.internal;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.accounting.dto.GroupDtos.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Groups of companies (finance F26). Each company stays its own tenant. Consent is the access grant: a company joins by
 * entering an invitation code from the group's parent, and leaving or being removed ends the parent's access at once.
 * Only the parent manages the group; subsidiaries see the group's name and parent and can leave.
 */
@Service
public class GroupService {

    static final int INVITE_DAYS = 7;
    private static final char[] ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();   // no 0/O/1/I
    private static final SecureRandom RANDOM = new SecureRandom();

    private final JdbcTemplate jdbc;

    public GroupService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static HandyFlowException bad(String m) { return new HandyFlowException(m, HttpStatus.BAD_REQUEST, "GROUP_REFUSED"); }
    private static HandyFlowException forbidden(String m) { return new HandyFlowException(m, HttpStatus.FORBIDDEN, "GROUP_FORBIDDEN"); }

    record GroupRow(UUID id, String name, UUID parentTenantId) {}

    /** The group this company owns, or a refusal. */
    GroupRow requireParent(TenantId tenantId) {
        List<GroupRow> g = jdbc.query("SELECT id, name, parent_tenant_id FROM entity_groups WHERE parent_tenant_id = ?",
                (rs, n) -> new GroupRow(rs.getObject(1, UUID.class), rs.getString(2), rs.getObject(3, UUID.class)), tenantId.getValue());
        if (g.isEmpty()) throw forbidden("Only the company that owns a group can do this.");
        return g.get(0);
    }

    @Transactional(readOnly = true)
    public GroupInfo mine(TenantId tenantId) {
        List<Object[]> m = jdbc.query("""
                SELECT g.id, g.name, mem.role, g.parent_tenant_id, pt.name
                  FROM entity_group_members mem JOIN entity_groups g ON g.id = mem.group_id JOIN tenants pt ON pt.id = g.parent_tenant_id
                 WHERE mem.tenant_id = ?""", (rs, n) -> new Object[]{rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getObject(4, UUID.class), rs.getString(5)},
                tenantId.getValue());
        if (m.isEmpty()) return null;
        Object[] r = m.get(0);
        List<Member> members = "PARENT".equals(r[2]) ? members((UUID) r[0]) : List.of();
        return new GroupInfo((UUID) r[0], (String) r[1], (String) r[2], (UUID) r[3], (String) r[4], members);
    }

    List<Member> members(UUID groupId) {
        return jdbc.query("""
                SELECT mem.tenant_id, t.name, mem.role, mem.ownership_percent, mem.joined_at
                  FROM entity_group_members mem JOIN tenants t ON t.id = mem.tenant_id
                 WHERE mem.group_id = ? ORDER BY mem.role DESC, t.name""",
                (rs, n) -> new Member(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getBigDecimal(4), rs.getTimestamp(5).toInstant()), groupId);
    }

    private boolean inAGroup(TenantId tenantId) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM entity_group_members WHERE tenant_id = ?", Integer.class, tenantId.getValue());
        return n != null && n > 0;
    }

    @Transactional
    public GroupInfo create(TenantId tenantId, String name, UUID by) {
        String problem = ConsolidationRules.groupNameProblem(name);
        if (problem != null) throw bad(problem);
        if (inAGroup(tenantId)) throw bad("This company is already in a group.");
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO entity_groups (id, name, parent_tenant_id, created_by) VALUES (?, ?, ?, ?)", id, name.trim(), tenantId.getValue(), by);
        jdbc.update("INSERT INTO entity_group_members (group_id, tenant_id, role, ownership_percent, joined_by) VALUES (?, ?, 'PARENT', 100, ?)", id, tenantId.getValue(), by);
        return mine(tenantId);
    }

    @Transactional
    public void dissolve(TenantId tenantId) {
        GroupRow g = requireParent(tenantId);
        Integer others = jdbc.queryForObject("SELECT COUNT(*) FROM entity_group_members WHERE group_id = ? AND role = 'SUBSIDIARY'", Integer.class, g.id());
        if (others != null && others > 0) throw bad("Remove the other companies from the group first.");
        jdbc.update("DELETE FROM entity_group_members WHERE group_id = ?", g.id());
        jdbc.update("DELETE FROM entity_groups WHERE id = ?", g.id());
    }

    // ── invitations and joining ──────────────────────────────────────────────

    static String hash(String code) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(code.trim().toUpperCase().getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }

    private static String newCode() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 16; i++) { if (i > 0 && i % 4 == 0) sb.append('-'); sb.append(ALPHABET[RANDOM.nextInt(ALPHABET.length)]); }
        return sb.toString();
    }

    @Transactional
    public InviteCreated invite(TenantId tenantId, CreateInvite req, UUID by) {
        GroupRow g = requireParent(tenantId);
        BigDecimal pct = req.ownershipPercent() == null ? BigDecimal.valueOf(100) : req.ownershipPercent();
        String problem = ConsolidationRules.ownershipProblem(pct);
        if (problem != null) throw bad(problem);
        String code = newCode();
        UUID id = UUID.randomUUID();
        Instant expires = Instant.now().plus(INVITE_DAYS, ChronoUnit.DAYS);
        jdbc.update("INSERT INTO entity_group_invites (id, group_id, code_hash, label, ownership_percent, expires_at, created_by) VALUES (?, ?, ?, ?, ?, ?, ?)",
                id, g.id(), hash(code), req.label() == null || req.label().isBlank() ? null : req.label().trim(), pct, Timestamp.from(expires), by);
        return new InviteCreated(id, code, expires);
    }

    @Transactional(readOnly = true)
    public List<Invite> invites(TenantId tenantId) {
        GroupRow g = requireParent(tenantId);
        return jdbc.query("SELECT id, label, ownership_percent, expires_at, created_at FROM entity_group_invites WHERE group_id = ? AND used_at IS NULL AND expires_at > now() ORDER BY created_at DESC",
                (rs, n) -> new Invite(rs.getObject(1, UUID.class), rs.getString(2), rs.getBigDecimal(3), rs.getTimestamp(4).toInstant(), rs.getTimestamp(5).toInstant()), g.id());
    }

    @Transactional
    public void cancelInvite(TenantId tenantId, UUID inviteId) {
        GroupRow g = requireParent(tenantId);
        jdbc.update("DELETE FROM entity_group_invites WHERE id = ? AND group_id = ? AND used_at IS NULL", inviteId, g.id());
    }

    /** The joining company's own admin enters the code. Joining is the consent for the parent to read this company's account totals. */
    @Transactional
    public GroupInfo join(TenantId tenantId, String code, UUID by) {
        if (code == null || code.isBlank()) throw bad("Enter the invitation code.");
        List<Object[]> found = jdbc.query("""
                SELECT i.id, i.group_id, i.ownership_percent, i.expires_at, i.used_at, g.parent_tenant_id
                  FROM entity_group_invites i JOIN entity_groups g ON g.id = i.group_id WHERE i.code_hash = ?""",
                (rs, n) -> new Object[]{rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getBigDecimal(3), rs.getTimestamp(4).toInstant(),
                        rs.getTimestamp(5) == null ? null : rs.getTimestamp(5).toInstant(), rs.getObject(6, UUID.class)}, hash(code));
        if (found.isEmpty()) throw bad("That invitation code is not valid.");
        Object[] f = found.get(0);
        String problem = ConsolidationRules.inviteProblem((Instant) f[3], (Instant) f[4], Instant.now());
        if (problem == null) problem = ConsolidationRules.joinProblem((UUID) f[5], tenantId.getValue(), inAGroup(tenantId));
        if (problem != null) throw bad(problem);
        int used = jdbc.update("UPDATE entity_group_invites SET used_at = now(), used_by_tenant_id = ? WHERE id = ? AND used_at IS NULL", tenantId.getValue(), f[0]);
        if (used == 0) throw bad("That invitation has already been used.");
        jdbc.update("INSERT INTO entity_group_members (group_id, tenant_id, role, ownership_percent, joined_by) VALUES (?, ?, 'SUBSIDIARY', ?, ?)",
                f[1], tenantId.getValue(), f[2], by);
        return mine(tenantId);
    }

    /** A company leaves its group (its own admin), or the parent removes it. Either way the parent's access ends and the company's rules go. */
    @Transactional
    public void leave(TenantId tenantId) {
        GroupInfo g = mine(tenantId);
        if (g == null) throw bad("This company is not in a group.");
        if ("PARENT".equals(g.myRole())) throw bad("The company that owns the group cannot leave it. Remove the others and dissolve the group.");
        detach(g.id(), tenantId.getValue());
    }

    @Transactional
    public void remove(TenantId tenantId, UUID memberTenantId) {
        GroupRow g = requireParent(tenantId);
        if (memberTenantId.equals(tenantId.getValue())) throw bad("The group's own company cannot be removed.");
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM entity_group_members WHERE group_id = ? AND tenant_id = ?", Integer.class, g.id(), memberTenantId);
        if (n == null || n == 0) throw bad("That company is not in the group.");
        detach(g.id(), memberTenantId);
    }

    private void detach(UUID groupId, UUID tenant) {
        jdbc.update("DELETE FROM entity_group_eliminations WHERE group_id = ? AND (tenant_a_id = ? OR tenant_b_id = ?)", groupId, tenant, tenant);
        jdbc.update("DELETE FROM entity_group_account_map WHERE group_id = ? AND tenant_id = ?", groupId, tenant);
        jdbc.update("DELETE FROM entity_group_members WHERE group_id = ? AND tenant_id = ?", groupId, tenant);
    }

    @Transactional
    public GroupInfo setOwnership(TenantId tenantId, UUID memberTenantId, BigDecimal percent) {
        GroupRow g = requireParent(tenantId);
        String problem = ConsolidationRules.ownershipProblem(percent);
        if (problem != null) throw bad(problem);
        int n = jdbc.update("UPDATE entity_group_members SET ownership_percent = ? WHERE group_id = ? AND tenant_id = ?", percent, g.id(), memberTenantId);
        if (n == 0) throw bad("That company is not in the group.");
        return mine(tenantId);
    }

    // ── the members' charts, mappings and intercompany rules ─────────────────

    /** Account codes and names of a member company (no balances), so the parent can map and pair accounts. */
    @Transactional(readOnly = true)
    public List<AccountRef> accountsOf(TenantId tenantId, UUID memberTenantId) {
        GroupRow g = requireParent(tenantId);
        requireMember(g, memberTenantId);
        return jdbc.query("SELECT account_code, account_name, account_type FROM acc_accounts WHERE tenant_id = ? ORDER BY account_code",
                (rs, n) -> new AccountRef(rs.getString(1), rs.getString(2), rs.getString(3)), memberTenantId);
    }

    private void requireMember(GroupRow g, UUID tenant) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM entity_group_members WHERE group_id = ? AND tenant_id = ?", Integer.class, g.id(), tenant);
        if (n == null || n == 0) throw bad("That company is not in the group.");
    }

    @Transactional(readOnly = true)
    public List<Mapping> mappings(TenantId tenantId) {
        GroupRow g = requireParent(tenantId);
        return jdbc.query("""
                SELECT m.id, m.tenant_id, t.name, m.account_code, m.group_code, m.group_name
                  FROM entity_group_account_map m JOIN tenants t ON t.id = m.tenant_id WHERE m.group_id = ? ORDER BY t.name, m.account_code""",
                (rs, n) -> new Mapping(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6)), g.id());
    }

    @Transactional
    public List<Mapping> map(TenantId tenantId, MapRequest req) {
        GroupRow g = requireParent(tenantId);
        String problem = ConsolidationRules.mappingProblem(req.accountCode(), req.groupCode());
        if (problem != null) throw bad(problem);
        if (req.tenantId() == null) throw bad("Choose the company.");
        requireMember(g, req.tenantId());
        Integer has = jdbc.queryForObject("SELECT COUNT(*) FROM acc_accounts WHERE tenant_id = ? AND account_code = ?", Integer.class, req.tenantId(), req.accountCode().trim());
        if (has == null || has == 0) throw bad("That company has no account with that code.");
        jdbc.update("""
                INSERT INTO entity_group_account_map (group_id, tenant_id, account_code, group_code, group_name) VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (group_id, tenant_id, account_code) DO UPDATE SET group_code = EXCLUDED.group_code, group_name = EXCLUDED.group_name""",
                g.id(), req.tenantId(), req.accountCode().trim(), req.groupCode().trim(), req.groupName() == null || req.groupName().isBlank() ? null : req.groupName().trim());
        return mappings(tenantId);
    }

    @Transactional
    public List<Mapping> unmap(TenantId tenantId, UUID mappingId) {
        GroupRow g = requireParent(tenantId);
        jdbc.update("DELETE FROM entity_group_account_map WHERE id = ? AND group_id = ?", mappingId, g.id());
        return mappings(tenantId);
    }

    @Transactional(readOnly = true)
    public List<Elimination> eliminations(TenantId tenantId) {
        GroupRow g = requireParent(tenantId);
        return jdbc.query("""
                SELECT e.id, e.name, e.tenant_a_id, ta.name, e.account_code_a, e.tenant_b_id, tb.name, e.account_code_b
                  FROM entity_group_eliminations e JOIN tenants ta ON ta.id = e.tenant_a_id JOIN tenants tb ON tb.id = e.tenant_b_id
                 WHERE e.group_id = ? ORDER BY e.created_at""",
                (rs, n) -> new Elimination(rs.getObject(1, UUID.class), rs.getString(2), rs.getObject(3, UUID.class), rs.getString(4), rs.getString(5),
                        rs.getObject(6, UUID.class), rs.getString(7), rs.getString(8)), g.id());
    }

    @Transactional
    public List<Elimination> addElimination(TenantId tenantId, EliminationRequest req, UUID by) {
        GroupRow g = requireParent(tenantId);
        List<UUID> members = members(g.id()).stream().map(Member::tenantId).toList();
        List<ConsolidationRules.Elim> existing = eliminations(tenantId).stream()
                .map(e -> new ConsolidationRules.Elim(e.name(), e.tenantAId(), e.accountCodeA(), e.tenantBId(), e.accountCodeB())).toList();
        String problem = ConsolidationRules.eliminationProblem(req.name(), req.tenantAId(), req.accountCodeA(), req.tenantBId(), req.accountCodeB(), members, existing);
        if (problem != null) throw bad(problem);
        for (Object[] side : new Object[][]{{req.tenantAId(), req.accountCodeA()}, {req.tenantBId(), req.accountCodeB()}}) {
            Integer has = jdbc.queryForObject("SELECT COUNT(*) FROM acc_accounts WHERE tenant_id = ? AND account_code = ?", Integer.class, side[0], side[1]);
            if (has == null || has == 0) throw bad("Account " + side[1] + " does not exist in that company.");
        }
        jdbc.update("INSERT INTO entity_group_eliminations (group_id, name, tenant_a_id, account_code_a, tenant_b_id, account_code_b, created_by) VALUES (?, ?, ?, ?, ?, ?, ?)",
                g.id(), req.name().trim(), req.tenantAId(), req.accountCodeA().trim(), req.tenantBId(), req.accountCodeB().trim(), by);
        return eliminations(tenantId);
    }

    @Transactional
    public List<Elimination> removeElimination(TenantId tenantId, UUID id) {
        GroupRow g = requireParent(tenantId);
        jdbc.update("DELETE FROM entity_group_eliminations WHERE id = ? AND group_id = ?", id, g.id());
        return eliminations(tenantId);
    }
}
