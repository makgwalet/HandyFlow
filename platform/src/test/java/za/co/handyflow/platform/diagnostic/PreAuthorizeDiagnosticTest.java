package za.co.handyflow.platform.diagnostic;

import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import za.co.handyflow.platform.WebMvcTestSecuritySupport;
import za.co.handyflow.platform.legalpractice.api.LpClientController;
import za.co.handyflow.platform.legalpractice.application.internal.LpClientService;
import za.co.handyflow.platform.legalpractice.application.internal.LpPortalService;
import za.co.handyflow.platform.legalpractice.application.internal.LpTrustTransactionService;
import za.co.handyflow.platform.evidence.application.EvidenceFacade;
import za.co.handyflow.platform.billing.FeatureGuard;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * DIAGNOSTIC ONLY — not a real test, delete after reading the console
 * output. Exists purely to answer one question empirically instead of
 * through more static-analysis guessing: is the LpClientController bean
 * MockMvc actually invokes the real, method-security-proxied bean, or a
 * raw/unproxied one?
 *
 * Run with: mvn test -Dtest=PreAuthorizeDiagnosticTest
 *
 * HOW TO READ THE OUTPUT:
 * - If it prints "IS a CGLIB/JDK proxy" and the wrong-authority request
 *   still gets 200 -> the proxy exists but @PreAuthorize's SpEL /
 *   authority check itself isn't firing (a deeper AOP-advice-ordering
 *   issue — the interceptor is wired but not actually intercepting).
 * - If it prints "is NOT a proxy (raw bean)" -> confirmed: MockMvc is
 *   bound to an unproxied controller instance, meaning Spring's method-
 *   security BeanPostProcessor ran too late (after this bean was
 *   already instantiated) or was never applied to it at all. This is
 *   the actual smoking gun to chase down in SecurityConfig / the
 *   WebMvcTest slice's bean-creation order.
 */
@WebMvcTest(LpClientController.class)
@org.springframework.context.annotation.Import(WebMvcTestSecuritySupport.class)
class PreAuthorizeDiagnosticTest {

    @Autowired MockMvc mockMvc;
    @Autowired LpClientController controllerBean;

    @MockitoBean LpClientService clientService;
    @MockitoBean LpTrustTransactionService trustService;
    @MockitoBean LpPortalService portalService;
    @MockitoBean EvidenceFacade evidenceFacade;
    @MockitoBean FeatureGuard featureGuard;

    @Test
    @WithMockUser(authorities = "SOME_UNRELATED_AUTHORITY")
    void diagnose() throws Exception {
        boolean isProxy = AopUtils.isAopProxy(controllerBean);
        boolean isCglib = AopUtils.isCglibProxy(controllerBean);
        boolean isJdk   = AopUtils.isJdkDynamicProxy(controllerBean);

        System.out.println("=== PreAuthorize DIAGNOSTIC ===");
        System.out.println("Controller bean class: " + controllerBean.getClass().getName());
        System.out.println("Is AOP proxy at all:   " + isProxy);
        System.out.println("Is CGLIB proxy:        " + isCglib);
        System.out.println("Is JDK dynamic proxy:  " + isJdk);
        if (!isProxy) {
            System.out.println(">>> RAW BEAN — @PreAuthorize's interceptor was never woven in. <<<");
        }

        var result = mockMvc.perform(get("/api/v1/legal-practice/clients"))
                .andReturn();
        System.out.println("Actual response status with wrong authority: " + result.getResponse().getStatus());
        System.out.println("(expected 403 if @PreAuthorize is really enforced)");
    }
}
