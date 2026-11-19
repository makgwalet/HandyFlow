package za.co.handyflow.platform.contracting.application.internal;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Decision: forwarded IP headers are not trusted until the hosting proxy topology is confirmed and trusted-proxy configuration exists.
 * The configuration now exists ({@link ClientIpResolver}) but is empty by default, so nothing is trusted until someone sets
 * handyflow.contracting.trusted-proxies. These checks keep every forwarded-header read inside that one class, and keep the public
 * endpoints limited by things that do not depend on the client address: per link token, per link for codes, and per code attempt.
 */
class ClientIpNotTrustedTest {

    static final Path CONTRACTING = Path.of("src/main/java/za/co/handyflow/platform/contracting");

    private static List<Path> sources() throws IOException {
        try (Stream<Path> s = Files.walk(CONTRACTING)) { return s.filter(p -> p.toString().endsWith(".java")).toList(); }
    }

    @Test
    void noContractingCodeOutsideTheResolverReadsAForwardedAddressHeader() throws IOException {
        for (Path p : sources()) {
            if (p.getFileName().toString().equals("ClientIpResolver.java")) continue;   // the only place allowed to read them
            String src = Files.readString(p).toLowerCase();
            for (String header : List.of("x-forwarded-for", "x-real-ip", "forwarded\"", "cf-connecting-ip", "true-client-ip"))
                assertFalse(src.contains(header), p.getFileName() + " reads " + header);
        }
    }

    @Test
    void controllersTakeTheAddressFromTheResolverOnly() throws IOException {
        for (String name : new String[]{"PublicNegotiationController.java", "PublicSigningController.java", "ContractingController.java"}) {
            String src = Files.readString(CONTRACTING.resolve("api").resolve(name));
            assertTrue(src.contains("clientIps.address(http)"), name);
            assertFalse(src.contains("getRemoteAddr()"), name + " bypasses the resolver");
            assertFalse(src.contains("getHeader(\"X-"), name + " reads a custom header for the address");
        }
    }

    @Test
    void noProxyAddressIsConfiguredByDefault() throws IOException {
        String yaml = Files.readString(Path.of("src/main/resources/application.yaml"));
        assertFalse(yaml.contains("trusted-proxies:") && !yaml.contains("trusted-proxies: ${CONTRACTING_TRUSTED_PROXIES:}"),
                "trusted-proxies must default to empty");
    }

    @Test
    void thereAreLimitsThatDoNotDependOnTheClientAddress() {
        List<String> names = Arrays.stream(PublicSigningRateLimiter.class.getDeclaredMethods()).map(m -> m.getName()).toList();
        for (String needed : new String[]{"checkNegotiateView", "checkNegotiateAct", "checkNegotiateOtpRequest", "checkOtpVerify", "checkView", "checkSubmit"})
            assertTrue(names.contains(needed), "missing " + needed);
        // the per-link checks are keyed by the token or the link id, not the address
        assertTrue(Arrays.stream(PublicSigningRateLimiter.class.getDeclaredMethods())
                .filter(m -> m.getName().equals("checkNegotiateOtpRequest") || m.getName().equals("checkOtpVerify"))
                .allMatch(m -> m.getParameterTypes().length == 1 && m.getParameterTypes()[0] == java.util.UUID.class));
    }
}
