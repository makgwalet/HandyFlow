package za.co.handyflow.platform.security.application.internal;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import za.co.handyflow.platform.shared.TenantId;

import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class PersonNamesTest {

    private static final UUID T = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID A = new UUID(0, 1), B = new UUID(0, 2), C = new UUID(0, 3);

    // ---- the rules ----

    @Test
    void nameIsFirstThenLastWithSpacesTidied() {
        assertEquals("Thandi Nkosi", PersonNameRules.display("Thandi", "Nkosi", "t@x.co"));
        assertEquals("Thandi Nkosi", PersonNameRules.display("  Thandi ", " Nkosi  ", null));
        assertEquals("Thandi", PersonNameRules.display("Thandi", null, "t@x.co"));
        assertEquals("Nkosi", PersonNameRules.display("", "Nkosi", "t@x.co"));
        assertEquals("Mary Ann Smith", PersonNameRules.display("Mary  Ann", "Smith", null));
    }

    @Test
    void emptyNameFallsBackToTheEmailAddressThenToNothing() {
        assertEquals("t@x.co", PersonNameRules.display("", "", " t@x.co "));
        assertEquals("t@x.co", PersonNameRules.display(null, null, "t@x.co"));
        assertNull(PersonNameRules.display("", " ", ""));
        assertNull(PersonNameRules.display(null, null, null));
    }

    @Test
    void lookUpListIsDistinctNoNullsInOrderAndCapped() {
        assertEquals(List.of(A, B), PersonNameRules.distinct(Arrays.asList(A, null, B, A)));
        assertEquals(List.of(), PersonNameRules.distinct(null));
        assertEquals(List.of(), PersonNameRules.distinct(Arrays.asList(null, null)));
        List<UUID> many = new ArrayList<>();
        for (int i = 1; i <= 30; i++) many.add(new UUID(0, i));
        List<UUID> cut = PersonNameRules.distinct(many);
        assertEquals(PersonNameRules.MAX_IDS, cut.size());
        assertEquals(new UUID(0, 1), cut.get(0));
    }

    // ---- the service, with a recording double for the database ----

    static class Rec extends JdbcTemplate {
        String sql; Object[] args; int calls; List<String[]> rows = new ArrayList<>();
        @Override public <X> List<X> query(String s, RowMapper<X> m, Object... a) {
            sql = s; args = a; calls++;
            List<X> out = new ArrayList<>();
            try { int i = 0; for (String[] r : rows) out.add(m.mapRow(row(r), i++)); } catch (java.sql.SQLException e) { throw new IllegalStateException(e); }
            return out;
        }
    }

    /** r = id, first, last, email */
    private static java.sql.ResultSet row(String[] r) {
        return (java.sql.ResultSet) Proxy.newProxyInstance(PersonNamesTest.class.getClassLoader(), new Class<?>[]{java.sql.ResultSet.class}, (p, m, a) -> {
            String label = a != null && a.length > 0 ? String.valueOf(a[0]) : "";
            return switch (m.getName()) {
                case "getObject" -> UUID.fromString(r[0]);
                case "getString" -> switch (label) { case "first_name" -> r[1]; case "last_name" -> r[2]; case "email" -> r[3]; default -> null; };
                default -> null;
            };
        });
    }

    private static PersonNameService svc(Rec r) throws Exception {
        var k = PersonNameService.class.getDeclaredConstructors()[0];
        Object[] ctorArgs = new Object[k.getParameterCount()];
        for (int i = 0; i < ctorArgs.length; i++) ctorArgs[i] = k.getParameterTypes()[i].isInstance(r) ? r : null;
        Object o = k.newInstance(ctorArgs);
        var f = PersonNameService.class.getDeclaredField("jdbc"); f.setAccessible(true); f.set(o, r);
        return (PersonNameService) o;
    }

    @Test
    void nothingToLookUpMeansNoQuery() throws Exception {
        Rec r = new Rec();
        assertTrue(svc(r).names(TenantId.of(T), Arrays.asList(null, null)).isEmpty());
        assertTrue(svc(r).names(TenantId.of(T), List.of()).isEmpty());
        assertEquals(0, r.calls);
    }

    @Test
    void oneQueryForTheCompanyWithOnePlaceholderPerPerson() throws Exception {
        Rec r = new Rec();
        r.rows.add(new String[]{A.toString(), "Thandi", "Nkosi", "t@x.co"});
        var out = svc(r).names(TenantId.of(T), Arrays.asList(A, B, A, null));
        assertEquals(1, r.calls);
        assertTrue(r.sql.contains("FROM users WHERE tenant_id = ? AND id IN (?, ?)"), r.sql);
        assertArrayEquals(new Object[]{T, A, B}, r.args);
        assertEquals(Map.of(A, "Thandi Nkosi"), out);
    }

    @Test
    void aPersonWithNoNameOrEmailIsLeftOutAndOneWithOnlyAnEmailShowsIt() throws Exception {
        Rec r = new Rec();
        r.rows.add(new String[]{A.toString(), "", "", "blank@x.co"});
        r.rows.add(new String[]{B.toString(), null, null, null});
        var out = svc(r).names(TenantId.of(T), List.of(A, B));
        assertEquals("blank@x.co", out.get(A));
        assertFalse(out.containsKey(B));
    }

    @Test
    void theLookUpIsAlwaysForTheCallersCompanyOnly() throws Exception {
        Rec r = new Rec();
        svc(r).names(TenantId.of(T), List.of(A));
        assertEquals(T, r.args[0]);
        assertTrue(r.sql.contains("tenant_id = ?"));
    }

    // ---- the controller, read from the source ----

    private static String ctl() throws Exception {
        for (String root : new String[]{"src/main/java", "platform/src/main/java", "work/src/main/java"}) {
            Path p = Path.of(root, "za/co/handyflow/platform/security/api/InvoiceDraftController.java");
            if (Files.exists(p)) return Files.readString(p);
        }
        fail("missing InvoiceDraftController.java"); return "";
    }

    @Test
    void detailCarriesTheNamesOfWhoSavedApprovedAndVoided() throws Exception {
        String c = ctl();
        assertTrue(c.contains("public record NamedDetail(InvoiceDraftService.Row draft, com.fasterxml.jackson.databind.JsonNode snapshot, java.util.Map<UUID, String> people) {}"));
        assertTrue(c.contains("new NamedDetail(d.draft(), d.snapshot(), people(d.draft()))"));
        assertTrue(c.contains("java.util.Arrays.asList(r.createdBy(), r.approvedBy(), r.voidedBy())"));
    }

    @Test
    void aMissingOrFailingNameLookUpNeverStopsTheDraftOpening() throws Exception {
        String c = ctl();
        assertTrue(c.contains("@org.springframework.beans.factory.annotation.Autowired(required = false)\n    private za.co.handyflow.platform.security.application.internal.PersonNameService personNames;"));
        int a = c.indexOf("private java.util.Map<UUID, String> people(");
        String body = c.substring(a);
        assertTrue(body.contains("if (personNames == null) return java.util.Map.of();"));
        assertTrue(body.contains("catch (RuntimeException e)"));
    }

    @Test
    void siteCheckStillRunsBeforeTheDraftIsRead() throws Exception {
        String c = ctl();
        int a = c.indexOf("NamedDetail>> get(");
        String body = c.substring(a, c.indexOf("@PostMapping", a));
        assertTrue(body.indexOf("site(siteId);") < body.indexOf("service.get("));
        assertTrue(body.indexOf("featureGuard.requireModule(\"security\")") < body.indexOf("site(siteId);"));
    }
}
