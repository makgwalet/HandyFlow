package za.co.handyflow.platform.clinic.api;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import za.co.handyflow.platform.clinic.application.internal.ClinicIssuedDocumentService;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Who may see the register of issued documents and withdraw one (CLINIC-DEC-024). Reading documents is not enough. */
@WebMvcTest(ClinicIssuedDocumentController.class)
@Import(za.co.handyflow.platform.WebMvcTestSecuritySupport.class)
class ClinicIssuedDocumentControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean ClinicIssuedDocumentService service;
    final UUID id = UUID.randomUUID();

    @BeforeEach void setTenant() { za.co.handyflow.platform.shared.TenantContext.setTenantId("9ecb3dc7-75d4-4e56-b0a2-c95d3c7c584f"); }
    @AfterEach void clearTenant() { za.co.handyflow.platform.shared.TenantContext.clear(); }

    @Test @WithMockUser(authorities = {"CLINIC_DOCUMENT_READ", "CLINIC_DOCUMENT_CREATE", "CLINIC_DOCUMENT_DOWNLOAD"})
    void readingDocumentsIsNotEnoughToListOrWithdraw() throws Exception {
        mvc.perform(get("/api/v1/clinic/issued-documents")).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/clinic/issued-documents/" + id + "/void").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Wrong patient\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    // the controller reads the user id from the principal, which must be a UUID string
    // (@WithMockUser builds a Spring User object, which UserContext does not recognise)
    private static org.springframework.test.web.servlet.request.RequestPostProcessor asUser(String... authorities) {
        return org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        "22222222-2222-4222-8222-222222222222", "n",
                        java.util.Arrays.stream(authorities)
                                .map(org.springframework.security.core.authority.SimpleGrantedAuthority::new).toList()));
    }

    @Test
    void theVoidPermissionListsAndWithdraws() throws Exception {
        mvc.perform(get("/api/v1/clinic/issued-documents").param("type", "SICK_NOTE").with(asUser("CLINIC_DOCUMENT_VOID"))).andExpect(status().isOk());
        mvc.perform(post("/api/v1/clinic/issued-documents/" + id + "/void").with(csrf()).with(asUser("CLINIC_DOCUMENT_VOID"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Wrong patient\"}"))
                .andExpect(status().isOk());
        verify(service).list(any(), org.mockito.ArgumentMatchers.eq("SICK_NOTE"), any(), any(), anyInt());
        verify(service).voidIt(any(), org.mockito.ArgumentMatchers.eq(id), any(), org.mockito.ArgumentMatchers.eq("Wrong patient"));
    }
}
