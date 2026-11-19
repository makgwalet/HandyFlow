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
 * Until then the public endpoints rely on limits that do not depend on the client address: per link token, per link for codes, and per
 * code attempt. The address is still recorded and limited per IP from the connection itself, which is the only address that cannot be
 * chosen by the caller.
 */
class ClientIpNotTrustedTest {

    static final Path CONTRACTING = Path.of("src/main/java/za/co/handyflow/platform/contracting");

    private static List<Path> sources() throws IOException {
        try (Stream<Path> s = Files.walk(CONTRACTING)) { return s.filter(p -> p.toString().endsWith(".java")).toList(); }
    }

    @Test
    void noContractingCodeReadsAForwardedAddressHeader() throws IOException {
        for (Path p : sources()) {
            String src = Files.readString(p).toLowerCase();
            for (String header : List.of("x-forwarded-for", "x-real-ip", "forwarded\"", "cf-connecting-ip", "true-client-ip"))
                assertFalse(src.contains(header), p.getFileName() + " reads " + header);
        }
    }

    @Test
    void publicControllersTakeTheAddressFromTheConnectionOnly() throws IOException {
        for (String name : new String[]{"PublicNegotiationController.java", "PublicSigningController.java"}) {
            String src = Files.readString(CONTRACTING.resolve("api").resolve(name));
            assertTrue(src.contains("getRemoteAddr()"), name);
            assertFalse(src.contains("getHeader(\"X-"), name + " reads a custom header for the address");
        }
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
