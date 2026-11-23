package za.co.handyflow.platform.security.application.internal;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class RegionServiceTest {

    private static final UUID T = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ACTOR = UUID.fromString("33333333-3333-3333-3333-333333333331");
    private static final UUID R1 = UUID.fromString("55555555-5555-5555-5555-555555555551");
    private static final UUID R2 = UUID.fromString("55555555-5555-5555-5555-555555555552");
    private static final UUID B1 = UUID.fromString("44444444-4444-4444-4444-444444444441");
    private static final String BASE = "src/main/java/za/co/handyflow/platform/security/";

    static class Rec extends JdbcTemplate {
        record Stmt(String kind, String sql, Object[] args) {}
        final List<Stmt> log = new ArrayList<>();
        boolean regionExists = true, branchExists = true;
        int insertRows = 1, holders = 0, clash = 0, touched = 0;
        List<Object[]> branchRows = new ArrayList<>(), regionRows = new ArrayList<>();

        @Override public <X> List<X> queryForList(String s, Class<X> t, Object... a) {
            log.add(new Stmt("list", s, a));
            List<X> out = new ArrayList<>();
            if (s.contains("FROM security_regions") && regionExists) out.add(t.cast(a[0]));
            if (s.contains("FROM security_branches") && branchExists) out.add(t.cast(a[0]));
            return out;
        }
        @Override public <X> X queryForObject(String s, Class<X> t, Object... a) {
            log.add(new Stmt("one", s, a));
            if (s.contains("FROM security_user_regions") && s.contains("DISTINCT")) return t.cast(touched);
            if (s.contains("FROM security_user_regions")) return t.cast(holders);
            if (s.contains("FROM security_regions")) return t.cast(clash);
            return null;
        }
        @Override public int update(String s, Object... a) { log.add(new Stmt("update", s, a)); return s.startsWith("INSERT INTO security_regions") ? insertRows : 1; }
        @Override public <X> List<X> query(String s, RowMapper<X> m, Object... a) {
            log.add(new Stmt("query", s, a));
            List<X> out = new ArrayList<>();
            try {
                if (s.contains("FROM security_branches")) for (int i = 0; i < branchRows.size(); i++) m.mapRow(rs(branchRows.get(i)), i);
                else for (int i = 0; i < regionRows.size(); i++) out.add(m.mapRow(rs(regionRows.get(i)), i));
            } catch (java.sql.SQLException e) { throw new IllegalStateException(e); }
            return out;
        }
        List<Stmt> of(String kind) { return log.stream().filter(x -> x.kind().equals(kind)).toList(); }
        int at(String frag) { for (int i = 0; i < log.size(); i++) if (log.get(i).sql().contains(frag)) return i; return -1; }
        List<Stmt> writes() { return of("update"); }
    }

    private static java.sql.ResultSet rs(Object[] row) {
        return (java.sql.ResultSet) Proxy.newProxyInstance(RegionServiceTest.class.getClassLoader(), new Class<?>[]{java.sql.ResultSet.class}, (p, m, a) -> {
            String label = a != null && a.length > 0 ? String.valueOf(a[0]) : "";
            return switch (m.getName()) {
                case "getObject" -> label.equals("region_id") ? row[0] : label.equals("id") ? row[row.length > 3 ? 0 : 1] : null;
                case "getString" -> label.equals("name") ? row[1] : label.equals("description") ? row[2] : null;
                case "getInt" -> row.length > 3 ? row[3] : 0;
                default -> null;
            };
        });
    }

    private static RegionService svc(Rec r) throws Exception {
        var k = RegionService.class.getDeclaredConstructors()[0];
        RegionService s = (RegionService) (k.getParameterCount() == 0 ? k.newInstance() : k.newInstance(r));
        Field f = RegionService.class.getDeclaredField("jdbc"); f.setAccessible(true); f.set(s, r);
        return s;
    }
    private static String code(Runnable r) { return assertThrows(HandyFlowException.class, r::run).getErrorCode(); }
    private static String read(String p) throws Exception { return Files.readString(Path.of(BASE + p)); }
    private static TenantId tid() { return TenantId.of(T); }

    // ---- create ----

    @Test void createCleansTheNameInsertsThenAudits() throws Exception {
        Rec r = new Rec();
        svc(r).create(tid(), ACTOR, "  Western   Cape ", "  Coast ");
        Rec.Stmt ins = r.writes().get(0);
        assertTrue(ins.sql().startsWith("INSERT INTO security_regions"));
        assertEquals(T, ins.args()[1]);
        assertEquals("Western Cape", ins.args()[2]);
        assertEquals("Coast", ins.args()[3]);
        assertEquals(2, r.writes().size());
        assertTrue(r.writes().get(1).sql().contains("security_audit_log"));
        assertEquals("REGION_CREATED", r.writes().get(1).args()[4]);
        assertEquals(T, r.writes().get(1).args()[0]);
        assertEquals(ACTOR, r.writes().get(1).args()[1]);
    }

    @Test void aSecondRegionWithTheSameNameIsAConflictAndNothingIsAudited() throws Exception {
        Rec r = new Rec(); r.insertRows = 0;
        assertEquals("DUPLICATE_REGION_NAME", code(() -> { try { svc(r).create(tid(), ACTOR, "KZN", null); } catch (RuntimeException e) { throw e; } catch (Exception e) { throw new IllegalStateException(e); } }));
        assertEquals(1, r.writes().size(), "only the refused insert ran");
    }

    @Test void aBadNameStopsBeforeAnyStatement() throws Exception {
        Rec r = new Rec();
        assertEquals("REGION_NAME", code(() -> { try { svc(r).create(tid(), ACTOR, "  ", null); } catch (RuntimeException e) { throw e; } catch (Exception e) { throw new IllegalStateException(e); } }));
        assertTrue(r.log.isEmpty());
    }

    // ---- update ----

    @Test void updateChecksTheRegionThenTheNameThenWritesThenAudits() throws Exception {
        Rec r = new Rec();
        svc(r).update(tid(), ACTOR, R1, " Gauteng ", null);
        int lock = r.at("FOR UPDATE"), clash = r.at("lower(name) = lower(?)"), upd = r.at("UPDATE security_regions SET name"), aud = r.at("security_audit_log");
        assertTrue(lock >= 0 && lock < clash && clash < upd && upd < aud, "lock, clash check, update, audit");
        Rec.Stmt c = r.log.get(clash);
        assertEquals(List.of(T, "Gauteng", R1), Arrays.asList(c.args()), "the name is checked against the other regions only, in this company");
        assertTrue(r.log.get(upd).sql().contains("tenant_id = ?"));
    }

    @Test void updateOfAnUnknownRegionOrToATakenNameWritesNothing() throws Exception {
        Rec none = new Rec(); none.regionExists = false;
        assertEquals("REGION_NOT_FOUND", code(() -> { try { svc(none).update(tid(), ACTOR, R1, "X", null); } catch (RuntimeException e) { throw e; } catch (Exception e) { throw new IllegalStateException(e); } }));
        assertTrue(none.writes().isEmpty());
        Rec taken = new Rec(); taken.clash = 1;
        assertEquals("DUPLICATE_REGION_NAME", code(() -> { try { svc(taken).update(tid(), ACTOR, R1, "X", null); } catch (RuntimeException e) { throw e; } catch (Exception e) { throw new IllegalStateException(e); } }));
        assertTrue(taken.writes().isEmpty());
    }

    // ---- delete ----

    @Test void aRegionThatUsersHoldCannotBeDeletedAndNothingChanges() throws Exception {
        for (int holders : new int[]{1, 3}) {
            Rec r = new Rec(); r.holders = holders;
            HandyFlowException e = assertThrows(HandyFlowException.class, () -> svc(r).delete(tid(), ACTOR, R1));
            assertEquals("REGION_IN_USE", e.getErrorCode());
            assertTrue(e.getMessage().startsWith(holders + (holders == 1 ? " user is" : " users are")), e.getMessage());
            assertTrue(r.writes().isEmpty(), "no branch is freed and nothing is deleted");
        }
    }

    @Test void deleteFreesTheBranchesThenRemovesTheRegionThenAudits() throws Exception {
        Rec r = new Rec();
        svc(r).delete(tid(), ACTOR, R1);
        int lock = r.at("FOR UPDATE"), holders = r.at("FROM security_user_regions"), free = r.at("UPDATE security_branches SET region_id = NULL"), del = r.at("DELETE FROM security_regions"), aud = r.at("security_audit_log");
        assertTrue(lock >= 0 && lock < holders && holders < free && free < del && del < aud);
        assertEquals(List.of(T, R1), Arrays.asList(r.log.get(free).args()));
        assertEquals("REGION_DELETED", r.log.get(aud).args()[4]);
        assertTrue(r.log.get(free).sql().contains("region = NULL"), "the label goes with the region");
    }

    @Test void deleteOfAnUnknownRegionIsNotFound() throws Exception {
        Rec r = new Rec(); r.regionExists = false;
        assertEquals("REGION_NOT_FOUND", code(() -> { try { svc(r).delete(tid(), ACTOR, R1); } catch (RuntimeException e) { throw e; } catch (Exception e) { throw new IllegalStateException(e); } }));
        assertTrue(r.writes().isEmpty());
    }

    // ---- assign a branch ----

    @Test void aBranchIsPutInARegionAfterBothAreChecked() throws Exception {
        Rec r = new Rec(); r.touched = 3;
        svc(r).assignBranch(tid(), ACTOR, B1, R2);
        int b = r.at("FROM security_branches WHERE id = ? AND tenant_id = ? FOR UPDATE"), g = r.at("FROM security_regions WHERE id = ? AND tenant_id = ? FOR UPDATE"),
            cnt = r.at("DISTINCT x.user_id"), upd = r.at("UPDATE security_branches SET region_id = ?"), aud = r.at("BRANCH_REGION_SET");
        assertTrue(b >= 0 && b < g && g < cnt && cnt < upd && upd < aud);
        assertEquals(List.of(R2, B1, T), Arrays.asList(r.log.get(upd).args()));
        assertEquals(3, r.log.get(aud).args()[4], "the audit says how many users this touches");
        assertEquals(R2.toString(), r.log.get(aud).args()[3]);
    }

    @Test void takingABranchOutClearsTheRegionAndItsLabel() throws Exception {
        Rec r = new Rec();
        svc(r).assignBranch(tid(), ACTOR, B1, null);
        assertEquals(-1, r.at("FROM security_regions WHERE id = ? AND tenant_id = ? FOR UPDATE"), "no region to look up");
        Rec.Stmt u = r.log.get(r.at("SET region_id = NULL"));
        assertTrue(u.sql().contains("region = NULL"));
        assertEquals(List.of(B1, T), Arrays.asList(u.args()));
        assertNull(r.log.get(r.at("BRANCH_REGION_SET")).args()[3]);
    }

    @Test void anUnknownBranchOrRegionChangesNothing() throws Exception {
        Rec nb = new Rec(); nb.branchExists = false;
        assertEquals("BRANCH_NOT_FOUND", code(() -> { try { svc(nb).assignBranch(tid(), ACTOR, B1, R1); } catch (RuntimeException e) { throw e; } catch (Exception e) { throw new IllegalStateException(e); } }));
        assertTrue(nb.writes().isEmpty());
        Rec nr = new Rec(); nr.regionExists = false;
        assertEquals("REGION_NOT_FOUND", code(() -> { try { svc(nr).assignBranch(tid(), ACTOR, B1, R1); } catch (RuntimeException e) { throw e; } catch (Exception e) { throw new IllegalStateException(e); } }));
        assertTrue(nr.writes().isEmpty());
        Rec none = new Rec();
        assertEquals("BRANCH_REQUIRED", code(() -> { try { svc(none).assignBranch(tid(), ACTOR, null, R1); } catch (RuntimeException e) { throw e; } catch (Exception e) { throw new IllegalStateException(e); } }));
        assertTrue(none.log.isEmpty());
    }

    // ---- list ----

    @Test void theListGroupsBranchesUnderTheirRegionAndKeepsRegionsWithNone() throws Exception {
        Rec r = new Rec();
        r.branchRows.add(new Object[]{R1, B1, null});
        r.branchRows.add(new Object[]{R1, UUID.fromString("44444444-4444-4444-4444-444444444442"), null});
        r.regionRows.add(new Object[]{R1, "KZN", "Coast", 2});
        r.regionRows.add(new Object[]{R2, "Gauteng", null, 0});
        List<RegionService.RegionView> out = svc(r).list(tid());
        assertEquals(2, out.size());
        assertEquals(2, out.get(0).branchIds().size());
        assertEquals(2, out.get(0).users());
        assertEquals(List.of(), out.get(1).branchIds());
        assertEquals("Gauteng", out.get(1).name());
        for (Rec.Stmt q : r.of("query")) assertEquals(List.of(T), Arrays.asList(q.args()), "both reads are for this company only");
    }

    // ---- every statement stays inside the company ----

    @Test void everyStatementNamesTheCompanyAndNoValueIsGluedIntoSql() throws Exception {
        String s = read("application/internal/RegionService.java");
        int stmts = 0;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("jdbc\\.(update|query|queryForList|queryForObject)\\(").matcher(s);
        while (m.find()) {
            String call = s.substring(m.start(), s.indexOf(");", m.start()));
            assertTrue(call.contains("INSERT INTO") ? call.contains("tenant_id") : call.contains("tenant_id = ?"), "tenant filter missing in: " + call);
            stmts++;
        }
        assertTrue(stmts >= 12, "found " + stmts);
        assertFalse(s.contains("\" + "), "values go through bind parameters");
        assertEquals(5, s.split("@Transactional", -1).length - 1, "four writers and the reader");
    }

    // ---- controller ----

    @Test void everyWriteIsManageOnlyFeatureGuardedAndNeedsTheWholeCompany() throws Exception {
        String c = read("api/RegionController.java");
        assertEquals(5, c.split("@(Get|Post|Put|Delete)Mapping", -1).length - 1);
        assertEquals(1, c.split("hasAuthority\\('SECURITY_READ'\\)", -1).length - 1);
        assertEquals(4, c.split("hasAuthority\\('SECURITY_MANAGE'\\)", -1).length - 1);
        for (String name : new String[]{"create", "update", "delete", "assign"}) {
            int at = c.indexOf(" " + name + "(");
            String body = c.substring(at, c.indexOf("\n    }", at));
            assertTrue(body.indexOf("featureGuard.requireModule(\"security\");") >= 0 && body.indexOf("wholeCompany()") > body.indexOf("featureGuard"), name);
            assertTrue(body.indexOf("wholeCompany()") < body.indexOf("service."), name);
            assertTrue(body.contains("TenantContext.getCurrentUserId()"), name + ": the caller is the actor");
        }
        assertTrue(c.contains("SiteScopeService.checkUnrestricted(scope, t, TenantContext.getCurrentUserId());"));
        assertTrue(c.contains("\"/api/v1/security/branches/{branchId}/region\""));
        assertFalse(c.contains("tenantId\""), "the company never comes from the request");
    }
}
