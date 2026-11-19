package za.co.handyflow.platform.contracting.application.internal;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.contracting.api.ContractNegotiationController;
import za.co.handyflow.platform.contracting.api.PublicNegotiationController;
import za.co.handyflow.platform.contracting.domain.model.ContractNegotiationLink;
import za.co.handyflow.platform.contracting.dto.ContractNegotiationDtos.*;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Structural security checks on the negotiation endpoints. Authorisation, public exposure and the shape of what a counterparty can send
 * or receive are fixed here so a later change cannot loosen them without failing a test.
 */
class ContractNegotiationSecurityTest {

    static boolean isEndpoint(Method m) {
        return m.isAnnotationPresent(GetMapping.class) || m.isAnnotationPresent(PostMapping.class) || m.isAnnotationPresent(PutMapping.class)
                || m.isAnnotationPresent(DeleteMapping.class) || m.isAnnotationPresent(PatchMapping.class) || m.isAnnotationPresent(RequestMapping.class);
    }

    static List<Method> endpoints(Class<?> c) { return Arrays.stream(c.getDeclaredMethods()).filter(ContractNegotiationSecurityTest::isEndpoint).toList(); }

    // ── staff endpoints ──────────────────────────────────────────────────────

    @Test
    void everyStaffEndpointIsGuardedByContractsPermissionsOnly() {
        List<Method> ms = endpoints(ContractNegotiationController.class);
        assertTrue(ms.size() >= 13);
        for (Method m : ms) {
            PreAuthorize pa = m.getAnnotation(PreAuthorize.class);
            assertNotNull(pa, m.getName() + " has no @PreAuthorize");
            assertTrue(pa.value().contains("CONTRACTS_MANAGE"), m.getName());
            assertTrue(pa.value().replace("hasAnyAuthority(", "").matches("(?s)[\\s'A-Z_,)]*"), "only CONTRACTS_* authorities in " + m.getName());
            assertFalse(pa.value().contains("permitAll"), m.getName());
        }
    }

    @Test
    void readOnlyUsersCanOnlyReadAndEveryChangeNeedsManage() {
        for (Method m : endpoints(ContractNegotiationController.class)) {
            String rule = m.getAnnotation(PreAuthorize.class).value();
            boolean read = m.isAnnotationPresent(GetMapping.class);
            assertEquals(read, rule.contains("CONTRACTS_READ"), m.getName() + (read ? " must allow read" : " must not allow read-only users"));
            assertTrue(rule.contains("CONTRACTS_ADMIN"), m.getName());
        }
    }

    @Test
    void noStaffEndpointTakesATenantFromTheRequest() {
        for (Method m : endpoints(ContractNegotiationController.class))
            for (var p : m.getParameters())
                assertFalse(p.getName().toLowerCase().contains("tenant"), m.getName() + " takes " + p.getName());
    }

    // ── public endpoints ─────────────────────────────────────────────────────

    @Test
    void publicEndpointsAreOnlyGetAndPostAndCarryNoPreAuthorize() {
        for (Method m : endpoints(PublicNegotiationController.class)) {
            assertFalse(m.isAnnotationPresent(PutMapping.class) || m.isAnnotationPresent(DeleteMapping.class) || m.isAnnotationPresent(PatchMapping.class),
                    m.getName() + " must not update or delete through a public link");
            assertFalse(m.isAnnotationPresent(PreAuthorize.class), m.getName());
        }
    }

    @Test
    void publicDataEndpointsRequireTheSessionHeader() {
        Set<String> open = Set.of("info", "code", "verify");
        for (Method m : endpoints(PublicNegotiationController.class)) {
            if (open.contains(m.getName())) continue;
            boolean hasHeader = Arrays.stream(m.getParameters()).anyMatch(p -> {
                RequestHeader h = p.getAnnotation(RequestHeader.class);
                return h != null && (h.value().equals("X-Negotiation-Session") || h.name().equals("X-Negotiation-Session") || h.value().isEmpty());
            });
            assertTrue(hasHeader, m.getName() + " does not read the session header");
        }
    }

    @Test
    void publicEndpointsNeverAcceptTenantContractOrPartyIdentifiers() {
        Set<String> forbidden = Set.of("tenant", "tenantid", "contractid", "partyid", "userid", "status");
        for (Method m : endpoints(PublicNegotiationController.class)) {
            for (var p : m.getParameters()) {
                String n = p.getName().toLowerCase();
                assertFalse(forbidden.contains(n), m.getName() + " takes " + p.getName());
                assertFalse(p.isAnnotationPresent(RequestParam.class), m.getName() + " takes a query parameter");
            }
        }
        for (Class<?> in : List.of(ProposalInput.class, CounterpartyCommentInput.class, VerifyRequest.class))
            for (RecordComponent rc : in.getRecordComponents())
                assertFalse(forbidden.contains(rc.getName().toLowerCase()) && !rc.getName().equals("proposalId"), in.getSimpleName() + "." + rc.getName());
    }

    @Test
    void publicProposalInputCannotSetTheStatusOrTheAuthor() {
        List<String> fields = Arrays.stream(ProposalInput.class.getRecordComponents()).map(RecordComponent::getName).toList();
        assertEquals(List.of("kind", "blockIndex", "originalHtml", "proposedHtml", "rationale"), fields);
        List<String> comment = Arrays.stream(CounterpartyCommentInput.class.getRecordComponents()).map(RecordComponent::getName).toList();
        assertEquals(List.of("body", "blockIndex", "blockExcerpt", "proposalId", "parentId"), comment);
        // a counter carries only wording and a reason: the target, round, author and status are all set by the server
        List<String> counter = Arrays.stream(CounterInput.class.getRecordComponents()).map(RecordComponent::getName).toList();
        assertEquals(List.of("proposedHtml", "rationale"), counter);
    }

    @Test
    void thePublicControllerCannotReachTheCanonicalContractBody() throws Exception {
        String src = Files.readString(Path.of("src/main/java/za/co/handyflow/platform/contracting/api/PublicNegotiationController.java"));
        for (String banned : List.of("ContractRepository", "ContractRedlineService", "ContractingService", "applyAccepted", "setBody", "markReady", "staffPropose", "staffCounter"))
            assertFalse(src.contains(banned), "public controller references " + banned);
        String access = Files.readString(Path.of("src/main/java/za/co/handyflow/platform/contracting/application/internal/ContractNegotiationAccessService.java"));
        // a counterparty may answer proposals addressed to them (counterpartyAccept/Reject/Counter), never use the staff decisions or apply anything
        for (String banned : List.of("applyAccepted", "markReady", "negotiation.accept(", "negotiation.reject(", "staffPropose", "staffCounter", "staffWithdraw", "ContractRedlineService", "updateBody"))
            assertFalse(access.contains(banned), "access service references " + banned);
    }

    @Test
    void whatACounterpartySeesCarriesNoInternalsOrSecrets() {
        Set<String> secret = Set.of("events", "links", "tokenhash", "internalonly", "actoruserid", "sessionhash", "sessiontoken");
        for (Class<?> view : List.of(CounterpartyView.class, ProposalView.class, CommentView.class, LinkInfo.class))
            for (RecordComponent rc : view.getRecordComponents())
                assertFalse(secret.contains(rc.getName().toLowerCase()), view.getSimpleName() + "." + rc.getName());
    }

    @Test
    void aLinkStoresOnlyTheHashOfItsToken() {
        List<String> names = Arrays.stream(ContractNegotiationLink.class.getDeclaredFields()).filter(f -> !Modifier.isStatic(f.getModifiers()))
                .map(f -> f.getName().toLowerCase()).toList();
        assertTrue(names.contains("tokenhash"));
        assertFalse(names.contains("token"), "the raw token must never be a column");
        assertFalse(names.stream().anyMatch(n -> n.equals("rawtoken") || n.equals("plaintoken") || n.equals("url")));
    }

    // ── configuration ────────────────────────────────────────────────────────

    @Test
    void theNegotiationAndSigningPathsAreReachableWithoutLogin() throws Exception {
        String cfg = Files.readString(Path.of("src/main/java/za/co/handyflow/platform/config/SecurityConfig.java"));
        assertTrue(cfg.contains("/api/v1/negotiate/**"), "negotiation links would need a login");
        assertTrue(cfg.contains("/api/v1/sign/**"), "signing links would need a login");
    }

    @Test
    void everyPublicEndpointIsRateLimitedBeforeItDoesAnyWork() throws Exception {
        String src = Files.readString(Path.of("src/main/java/za/co/handyflow/platform/contracting/api/PublicNegotiationController.java"));
        String[] chunks = src.split("\\n    @(?=GetMapping|PostMapping)");
        assertTrue(chunks.length >= 8, "expected the seven public endpoints");
        for (int i = 1; i < chunks.length; i++) {
            String c = chunks[i];
            int limit = Math.min(c.contains("rateLimiter.") ? c.indexOf("rateLimiter.") : Integer.MAX_VALUE, c.contains("authenticate(") ? c.indexOf("authenticate(") : Integer.MAX_VALUE);
            int work = Math.min(c.contains("access.") ? c.indexOf("access.") : Integer.MAX_VALUE, c.contains("service.") ? c.indexOf("service.") : Integer.MAX_VALUE);
            assertTrue(limit != Integer.MAX_VALUE && limit < work, "no rate limit ahead of the work in: " + c.substring(0, Math.min(70, c.length())));
        }
    }
}
