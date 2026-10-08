package za.co.handyflow.platform.clinic.config;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import za.co.handyflow.platform.clinic.application.internal.ClinicRestrictedRecordService;
import za.co.handyflow.platform.clinic.application.internal.ClinicRestrictedRecordService.Restriction;
import za.co.handyflow.platform.clinic.application.internal.RestrictedRecordRules.Guard;
import za.co.handyflow.platform.shared.TenantContext;

import java.time.Instant;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClinicRestrictedRecordInterceptorTest {

    static final String TENANT = "9ecb3dc7-75d4-4e56-b0a2-c95d3c7c584f";
    final UUID patient = UUID.randomUUID(), user = UUID.randomUUID(), session = UUID.randomUUID();
    @Mock ClinicRestrictedRecordService svc;
    @Mock ObjectProvider<ClinicRestrictedRecordService> provider;
    ClinicRestrictedRecordInterceptor interceptor;
    MockHttpServletResponse response = new MockHttpServletResponse();

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(TENANT);
        lenient().when(provider.getIfAvailable()).thenReturn(svc);
        interceptor = new ClinicRestrictedRecordInterceptor(provider);
    }

    @AfterEach
    void tearDown() { TenantContext.clear(); SecurityContextHolder.clearContext(); }

    private void signIn(String... authorities) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user.toString(), "n/a",
                Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList()));
    }

    private boolean call(String method, String path) throws Exception {
        return interceptor.preHandle(new MockHttpServletRequest(method, path), response, new Object());
    }

    private String record() { return "/api/v1/clinic/patients/" + patient + "/medical-history"; }

    private void restricted() {
        when(svc.patientOf(any(), any(Guard.class))).thenReturn(Optional.of(patient));
        when(svc.restriction(any(), eq(patient))).thenReturn(Optional.of(new Restriction("MENTAL_HEALTH", Instant.now())));
    }

    @Test void options_requests_pass_untouched() throws Exception {
        assertTrue(call("OPTIONS", record()));
        verifyNoInteractions(svc);
    }

    @Test void paths_outside_the_clinical_record_pass_without_a_lookup() throws Exception {
        signIn("CLINIC_PATIENT_READ");
        assertTrue(call("GET", "/api/v1/clinic/queue"));
        verifyNoInteractions(svc);
    }

    @Test void no_service_bean_means_the_interceptor_does_nothing() throws Exception {
        when(provider.getIfAvailable()).thenReturn(null);
        assertTrue(call("GET", record()));
    }

    @Test void unrestricted_patient_passes() throws Exception {
        signIn("CLINIC_PATIENT_READ");
        when(svc.patientOf(any(), any(Guard.class))).thenReturn(Optional.of(patient));
        when(svc.restriction(any(), eq(patient))).thenReturn(Optional.empty());
        assertTrue(call("GET", record()));
        verify(svc, never()).audit(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test void restricted_without_session_is_refused_with_a_code() throws Exception {
        signIn("CLINIC_PATIENT_READ");
        restricted();
        when(svc.activeSession(any(), eq(patient), eq(user))).thenReturn(Optional.empty());
        assertFalse(call("GET", record()));
        assertEquals(403, response.getStatus());
        assertTrue(response.getContentAsString().contains("\"code\":\"RESTRICTED_RECORD\""));
        assertTrue(response.getContentAsString().contains("MENTAL_HEALTH"));
    }

    @Test void standing_access_passes_without_an_audit_row() throws Exception {
        signIn("CLINIC_PATIENT_READ", "CLINIC_RESTRICTED_RECORD_ACCESS");
        restricted();
        when(svc.activeSession(any(), eq(patient), eq(user))).thenReturn(Optional.empty());
        assertTrue(call("GET", record()));
        verify(svc, never()).audit(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test void active_break_glass_passes_and_every_request_is_audited() throws Exception {
        signIn("CLINIC_PATIENT_READ", "CLINIC_BREAK_GLASS_VIEW");
        restricted();
        when(svc.activeSession(any(), eq(patient), eq(user))).thenReturn(Optional.of(session));
        assertTrue(call("GET", record()));
        verify(svc).audit(any(), eq(session), eq(patient), eq(user), eq("BREAK_GLASS_VIEWED"), eq("PATIENT"), eq(patient), eq(record()));
    }

    @Test void a_document_under_break_glass_needs_the_print_or_export_permission() throws Exception {
        signIn("CLINIC_PATIENT_READ", "CLINIC_BREAK_GLASS_VIEW");
        restricted();
        when(svc.activeSession(any(), eq(patient), eq(user))).thenReturn(Optional.of(session));
        assertFalse(call("GET", "/api/v1/clinic/patients/" + patient + "/medical-certificate"));
        assertEquals(403, response.getStatus());
        assertTrue(response.getContentAsString().contains("RESTRICTED_DOCUMENT"));
    }

    @Test void a_document_with_print_permission_is_audited_as_exported() throws Exception {
        signIn("CLINIC_PATIENT_READ", "CLINIC_BREAK_GLASS_VIEW", "CLINIC_BREAK_GLASS_PRINT");
        restricted();
        when(svc.activeSession(any(), eq(patient), eq(user))).thenReturn(Optional.of(session));
        assertTrue(call("GET", "/api/v1/clinic/patients/" + patient + "/medical-certificate"));
        verify(svc).audit(any(), eq(session), eq(patient), eq(user), eq("BREAK_GLASS_DOCUMENT_EXPORTED"), any(), any(), any());
    }

    @Test void the_restriction_endpoints_themselves_stay_open() throws Exception {
        signIn("CLINIC_PATIENT_READ");
        assertTrue(call("GET", "/api/v1/clinic/patients/" + patient + "/restriction"));
        assertTrue(call("POST", "/api/v1/clinic/patients/" + patient + "/break-glass"));
        verifyNoInteractions(svc);
    }

    @Test void fails_closed_when_the_check_cannot_be_made() throws Exception {
        signIn("CLINIC_PATIENT_READ");
        when(svc.patientOf(any(), any(Guard.class))).thenThrow(new IllegalStateException("db down"));
        assertFalse(call("GET", record()));
        assertEquals(503, response.getStatus());
    }
}
