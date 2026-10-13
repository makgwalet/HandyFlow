package za.co.handyflow.platform.clinic.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import za.co.handyflow.platform.clinic.application.internal.ClinicPermission;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The API authorization matrix is enforced, not just documented: every Clinic endpoint has a @PreAuthorize, every
 * permission it names is in the catalogue, and no endpoint still relies on one of the old coarse permissions
 * (CLINIC_READ, CLINIC_WRITE, ...) except the access catalogue itself, which is for CLINIC_ADMIN.
 * No Spring context: it reads the controller classes only.
 */
class ClinicEndpointAuthorizationTest {

    private static final Pattern LITERAL = Pattern.compile("'([A-Z][A-Z0-9_]*)'");

    private static List<Method> endpoints() throws Exception {
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        List<Method> out = new ArrayList<>();
        for (BeanDefinition bd : scanner.findCandidateComponents("za.co.handyflow.platform.clinic.api")) {
            Class<?> c = Class.forName(bd.getBeanClassName());
            for (Method m : c.getDeclaredMethods()) {
                if (AnnotatedElementUtils.hasAnnotation(m, RequestMapping.class)) out.add(m);
            }
        }
        return out;
    }

    /** Public on purpose (CLINIC-DEC-025): the check that a document is genuine. Exact path in SecurityConfig, rate limited, authenticity data only. */
    private static final java.util.Set<String> PUBLIC = java.util.Set.of("ClinicDocumentVerificationController.verify");

    private static String label(Method m) { return m.getDeclaringClass().getSimpleName() + "." + m.getName(); }

    @Test void thereAreClinicEndpoints() throws Exception {
        assertTrue(endpoints().size() > 100, "found only " + endpoints().size());
    }

    @Test void everyEndpointHasAPreAuthorize() throws Exception {
        List<String> bare = new ArrayList<>();
        for (Method m : endpoints()) {
            if (!m.isAnnotationPresent(PreAuthorize.class) && !m.getDeclaringClass().isAnnotationPresent(PreAuthorize.class) && !PUBLIC.contains(label(m))) bare.add(label(m));
        }
        assertTrue(bare.isEmpty(), "Endpoints without @PreAuthorize: " + bare);
    }

    @Test void everyPermissionNamedIsInTheCatalogue() throws Exception {
        List<String> unknown = new ArrayList<>();
        for (Method m : endpoints()) {
            PreAuthorize pa = m.getAnnotation(PreAuthorize.class);
            if (pa == null) continue;
            Matcher mt = LITERAL.matcher(pa.value());
            while (mt.find()) if (!ClinicPermission.isKnown(mt.group(1))) unknown.add(label(m) + " -> " + mt.group(1));
        }
        assertTrue(unknown.isEmpty(), "Permissions not in the catalogue: " + unknown);
    }

    @Test void noEndpointStillUsesACoarsePermissionExceptTheAccessCatalogue() throws Exception {
        List<String> coarse = new ArrayList<>();
        for (Method m : endpoints()) {
            PreAuthorize pa = m.getAnnotation(PreAuthorize.class);
            if (pa == null || m.getDeclaringClass() == ClinicAccessCatalogueController.class) continue;
            Matcher mt = LITERAL.matcher(pa.value());
            while (mt.find()) if (ClinicPermission.LEGACY.contains(mt.group(1))) coarse.add(label(m) + " -> " + mt.group(1));
        }
        assertTrue(coarse.isEmpty(), "Endpoints still on a coarse permission: " + coarse);
    }

    @Test void theOnlyPublicEndpointsAreTheDocumentedOnesAndLiveUnderPublic() throws Exception {
        for (Method m : endpoints()) {
            if (!PUBLIC.contains(label(m))) continue;
            RequestMapping base = m.getDeclaringClass().getAnnotation(RequestMapping.class);
            assertTrue(base != null && base.value()[0].startsWith("/api/v1/public/clinic/"), label(m) + " must live under /api/v1/public/clinic/");
            assertTrue(AnnotatedElementUtils.hasAnnotation(m, org.springframework.web.bind.annotation.PostMapping.class), label(m) + " must be a POST");
        }
        assertTrue(endpoints().stream().anyMatch(m -> PUBLIC.contains(label(m))), "the public endpoint was not found");
    }
}
