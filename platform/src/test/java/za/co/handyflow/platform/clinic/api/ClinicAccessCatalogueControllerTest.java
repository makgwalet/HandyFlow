package za.co.handyflow.platform.clinic.api;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ClinicAccessCatalogueController.class)
@Import(za.co.handyflow.platform.WebMvcTestSecuritySupport.class)
class ClinicAccessCatalogueControllerTest {

    @Autowired MockMvc mvc;

    @BeforeEach
    void setTenant() { za.co.handyflow.platform.shared.TenantContext.setTenantId("9ecb3dc7-75d4-4e56-b0a2-c95d3c7c584f"); }

    @AfterEach
    void clearTenant() { za.co.handyflow.platform.shared.TenantContext.clear(); }

    @Test
    @WithMockUser(authorities = "CLINIC_ADMIN")
    @DisplayName("GET /access/catalogue returns permissions, the coarse legacy ones and the role templates")
    void adminSeesTheCatalogue() throws Exception {
        mvc.perform(get("/api/v1/clinic/access/catalogue"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.permissions.length()", greaterThan(100)))
                .andExpect(jsonPath("$.data.legacyPermissions", hasItem("CLINIC_READ")))
                .andExpect(jsonPath("$.data.roleTemplates[?(@.key=='DOCTOR')].permissions[*]", hasItem("CLINIC_CONSULTATION_SIGN")))
                .andExpect(jsonPath("$.data.roleTemplates[?(@.key=='RECEPTION')].permissions[*]", not(hasItem("CLINIC_ALLERGY_READ"))));
    }

    @Test
    @WithMockUser(authorities = "CLINIC_PATIENT_READ")
    @DisplayName("GET /access/catalogue is for CLINIC_ADMIN only")
    void othersAreRefused() throws Exception {
        mvc.perform(get("/api/v1/clinic/access/catalogue")).andExpect(status().isForbidden());
    }
}
