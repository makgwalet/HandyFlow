package za.co.handyflow.platform.identity.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.shared.HandyFlowException;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class SsoRulesTest {

    private static final UUID A = new UUID(1, 1), B = new UUID(1, 2);
    private static String code(Runnable r) { return assertThrows(HandyFlowException.class, r::run).getErrorCode(); }

    // ---- issuer ----

    @Test void aPublicHttpsIssuerIsKeptExactlyAsGiven() {
        assertEquals("https://login.example.com", SsoRules.cleanIssuer("  https://login.example.com  "));
        assertEquals("https://login.example.com/realms/acme/", SsoRules.cleanIssuer("https://login.example.com/realms/acme/"), "a trailing slash is part of the issuer");
        assertEquals("https://Login.Example.com:8443/x", SsoRules.cleanIssuer("https://Login.Example.com:8443/x"));
    }

    @Test void everythingThatIsNotAPublicHttpsHostIsRefused() {
        for (String bad : new String[]{null, "", "  ", "http://login.example.com", "ftp://login.example.com", "login.example.com", "https://", "https:///x",
                "https://user:pw@login.example.com", "https://login.example.com/?a=1", "https://login.example.com/#x", "https://localhost/", "https://app.localhost/",
                "https://127.0.0.1/", "https://127.1/", "https://10.0.0.5/", "https://2130706433/", "https://[::1]/", "https://corp/", "https://printer.local/",
                "https://db.internal/", "https://169.254.169.254/latest", "https://exa mple.com/", "https://" + "a".repeat(500) + ".com"}) {
            assertEquals("SSO_ISSUER", code(() -> SsoRules.cleanIssuer(bad)), String.valueOf(bad));
        }
    }

    // ---- client id and secret reference ----

    @Test void theClientIdIsTrimmedAndHasNoSpaces() {
        assertEquals("abc-123.apps", SsoRules.cleanClientId(" abc-123.apps "));
        assertEquals(200, SsoRules.cleanClientId("x".repeat(200)).length());
        for (String bad : new String[]{null, "", " ", "a b", "a\tb", "a\nb", "x".repeat(201)}) assertEquals("SSO_CLIENT_ID", code(() -> SsoRules.cleanClientId(bad)), String.valueOf(bad));
    }

    @Test void theSecretReferenceIsTheNameOfAServerSettingNeverASecret() {
        assertNull(SsoRules.cleanSecretRef(null)); assertNull(SsoRules.cleanSecretRef("  "));
        assertEquals("ACME_SSO_SECRET", SsoRules.cleanSecretRef(" ACME_SSO_SECRET "));
        for (String bad : new String[]{"lower", "1ABC", "A-B", "A B", "A.B", "s3cr3t-value!", "A".repeat(101)}) assertEquals("SSO_SECRET_REF", code(() -> SsoRules.cleanSecretRef(bad)), bad);
    }

    // ---- domains ----

    @Test void domainsAreLowerCasedWithoutAtAndWithoutRepeats() {
        assertEquals(List.of("acme.co.za", "sub.acme.com"), SsoRules.cleanDomains(Arrays.asList(" @ACME.co.za ", null, "", "acme.co.za", "Sub.Acme.com")));
        assertEquals(List.of(), SsoRules.cleanDomains(null));
        assertEquals(List.of("xn--bcher-kva.example"), SsoRules.cleanDomains(List.of("xn--bcher-kva.example")));
    }

    @Test void badDomainsAreRefusedByName() {
        for (String bad : new String[]{"acme", "acme..co.za", "-acme.co.za", "acme-.co.za", "ac me.co.za", "acme.c", "acme.co.za/path", "https://acme.co.za", "a@acme.co.za", "*.acme.co.za", "acme.123", "a".repeat(64) + ".co.za"}) {
            assertEquals("SSO_DOMAIN", code(() -> SsoRules.cleanDomains(List.of(bad))), bad);
        }
    }

    @Test void aCompanyCannotClaimAPublicMailProvider() {
        for (String free : new String[]{"gmail.com", "GMAIL.com", "@outlook.com", "hotmail.com", "yahoo.com", "icloud.com", "mweb.co.za", "telkomsa.net", "proton.me"}) {
            assertEquals("SSO_DOMAIN_FREE_MAIL", code(() -> SsoRules.cleanDomains(List.of("acme.co.za", free))), free);
        }
        assertEquals(List.of("gmail.co.za"), SsoRules.cleanDomains(List.of("gmail.co.za")), "only the exact provider domain is blocked");
    }

    @Test void thereIsALimitOnDomains() {
        List<String> ok = new ArrayList<>(), many = new ArrayList<>();
        for (int i = 0; i < 21; i++) { many.add("d" + i + ".co.za"); if (i < 20) ok.add("d" + i + ".co.za"); }
        assertEquals(20, SsoRules.cleanDomains(ok).size());
        assertEquals("SSO_DOMAIN_TOO_MANY", code(() -> SsoRules.cleanDomains(many)));
        List<String> dupes = new ArrayList<>(many); dupes.add("d0.co.za");
        assertEquals("SSO_DOMAIN_TOO_MANY", code(() -> SsoRules.cleanDomains(dupes)), "21 different ones are still too many");
    }

    // ---- break glass ----

    @Test void breakGlassPeopleAreDeduplicatedAndLimited() {
        assertEquals(List.of(A, B), SsoRules.cleanBreakGlass(Arrays.asList(A, null, B, A)));
        assertEquals(List.of(), SsoRules.cleanBreakGlass(null));
        List<UUID> many = new ArrayList<>(); for (int i = 0; i <= SsoRules.MAX_BREAK_GLASS; i++) many.add(new UUID(2, i));
        assertEquals("SSO_BREAK_GLASS_TOO_MANY", code(() -> SsoRules.cleanBreakGlass(many)));
        assertEquals(10, SsoRules.cleanBreakGlass(many.subList(0, 10)).size());
    }

    // ---- the sign-in decision ----

    private static final SsoRules.Policy ENFORCED = new SsoRules.Policy(true, true, List.of("acme.co.za"));

    @Test void aPasswordIsRefusedOnlyWhenEnabledEnforcedAndTheAddressIsAtAnAllowedDomain() {
        assertEquals(SsoRules.Method.SSO_REQUIRED, SsoRules.decide(ENFORCED, "thandi@acme.co.za", false));
        assertEquals(SsoRules.Method.SSO_REQUIRED, SsoRules.decide(ENFORCED, "  THANDI@ACME.CO.ZA", false), "case does not matter");
        assertEquals(SsoRules.Method.PASSWORD_OK, SsoRules.decide(ENFORCED, "contractor@other.co.za", false));
        assertEquals(SsoRules.Method.PASSWORD_OK, SsoRules.decide(ENFORCED, "thandi@mail.acme.co.za", false), "a sub-domain is not the domain");
        assertEquals(SsoRules.Method.PASSWORD_OK, SsoRules.decide(ENFORCED, "thandi@acme.co.za.evil.com", false));
        assertEquals(SsoRules.Method.PASSWORD_OK, SsoRules.decide(ENFORCED, "thandi@acme.co.za", true), "break-glass keeps a password");
    }

    @Test void nothingChangesWhenSsoIsOffOrNotEnforcedOrAbsent() {
        assertEquals(SsoRules.Method.PASSWORD_OK, SsoRules.decide(null, "a@acme.co.za", false));
        assertEquals(SsoRules.Method.PASSWORD_OK, SsoRules.decide(new SsoRules.Policy(false, true, List.of("acme.co.za")), "a@acme.co.za", false));
        assertEquals(SsoRules.Method.PASSWORD_OK, SsoRules.decide(new SsoRules.Policy(true, false, List.of("acme.co.za")), "a@acme.co.za", false));
        assertEquals(SsoRules.Method.PASSWORD_OK, SsoRules.decide(new SsoRules.Policy(true, true, null), "a@acme.co.za", false));
        assertEquals(SsoRules.Method.PASSWORD_OK, SsoRules.decide(new SsoRules.Policy(true, true, List.of()), "a@acme.co.za", false));
    }

    @Test void anAddressWithoutADomainIsNotCaught() {
        for (String e : new String[]{null, "", "nobody", "nobody@", "@"}) assertEquals(SsoRules.Method.PASSWORD_OK, SsoRules.decide(ENFORCED, e, false), String.valueOf(e));
        assertEquals("acme.co.za", SsoRules.domainOf("x@y@Acme.co.za"));
    }

    // ---- lock-out protection ----

    @Test void enforcingNeedsAtLeastOneBreakGlassPerson() {
        assertNotNull(SsoRules.lockoutProblem(List.of("acme.co.za"), List.of(), A, "x@other.co.za"));
        assertNotNull(SsoRules.lockoutProblem(List.of("acme.co.za"), null, A, "x@other.co.za"));
        assertNull(SsoRules.lockoutProblem(List.of("acme.co.za"), List.of(B), A, "x@other.co.za"));
    }

    @Test void theAdministratorMustKeepAPasswordIfTheirOwnAddressWouldBeCaught() {
        assertTrue(SsoRules.lockoutProblem(List.of("acme.co.za"), List.of(B), A, "me@acme.co.za").contains("lock yourself out"));
        assertNull(SsoRules.lockoutProblem(List.of("acme.co.za"), List.of(A, B), A, "me@acme.co.za"));
        assertNull(SsoRules.lockoutProblem(List.of("acme.co.za"), List.of(B), A, "me@elsewhere.com"));
        assertNotNull(SsoRules.lockoutProblem(List.of("acme.co.za"), List.of(B), null, "me@acme.co.za"));
    }
}
