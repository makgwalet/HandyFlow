package za.co.handyflow.platform.clinic.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.*;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import za.co.handyflow.platform.clinic.application.internal.*;
import za.co.handyflow.platform.clinic.domain.model.ClinicMedicationCatalogue;
import za.co.handyflow.platform.clinic.domain.repository.ClinicMedicationCatalogueRepository;
import za.co.handyflow.platform.clinic.dto.*;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.*;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Spring MVC slice test — loads only the web layer (no DB).
 * Tests that routes exist, security annotations fire, and JSON is mapped correctly.
 */
@WebMvcTest(ClinicController.class)
class ClinicControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    @MockitoBean ClinicService                      clinicService;
    @MockitoBean ClinicPdfService                   clinicPdfService;
    @MockitoBean ClinicMedicationCatalogueRepository medicationRepo;

    static final String BASE = "/api/v1/clinic";

    PatientResponse patientResponse(UUID id, String first, String last) {
        return new PatientResponse(id, first, last, first+" "+last,
                null, null, null, "+27820000001", null, null,
                List.of(), List.of(), null, null, null,
                true, Instant.now(),
                "INDIVIDUAL", null, null, null, null, null);
    }

    // ── GET /patients ─────────────────────────────────────────────────────────

    @Test
    @WithMockUser(authorities = "CLINIC_READ")
    @DisplayName("GET /patients returns 200 with paginated patients")
    void getPatientsReturns200() throws Exception {
        var patient = patientResponse(UUID.randomUUID(), "Jane", "Dlamini");
        var page    = new PageImpl<>(List.of(patient));

        when(clinicService.getPatients(any(), any(), any(), anyBoolean(), any()))
                .thenReturn(page);

        mvc.perform(get(BASE + "/patients"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].fullName").value("Jane Dlamini"));
    }

    @Test
    @WithMockUser(authorities = "CLINIC_READ")
    @DisplayName("GET /patients?search= delegates search parameter")
    void getPatientsWithSearch() throws Exception {
        when(clinicService.getPatients(any(), eq("nkosi"), any(), anyBoolean(), any()))
                .thenReturn(Page.empty());

        mvc.perform(get(BASE + "/patients").param("search", "nkosi"))
                .andExpect(status().isOk());

        verify(clinicService).getPatients(any(), eq("nkosi"), any(), anyBoolean(), any());
    }

    @Test
    @DisplayName("GET /patients without auth returns 403")
    void getPatientsWithoutAuthReturns403() throws Exception {
        mvc.perform(get(BASE + "/patients"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "CLINIC_READ")
    @DisplayName("GET /patients/{id} returns 200 with patient")
    void getPatientByIdReturns200() throws Exception {
        var id      = UUID.randomUUID();
        var patient = patientResponse(id, "Sipho", "Nkosi");

        when(clinicService.getPatient(any(), eq(id))).thenReturn(patient);

        mvc.perform(get(BASE + "/patients/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.firstName").value("Sipho"));
    }

    // ── POST /patients ────────────────────────────────────────────────────────

    @Test
    @WithMockUser(authorities = "CLINIC_WRITE")
    @DisplayName("POST /patients returns 201 with created patient")
    void createPatientReturns201() throws Exception {
        var id = UUID.randomUUID();
        when(clinicService.createPatient(any(), any()))
                .thenReturn(patientResponse(id, "Jane", "Dlamini"));

        var body = Map.of("firstName","Jane","lastName","Dlamini","phone","+27831002000");

        mvc.perform(post(BASE + "/patients").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.fullName").value("Jane Dlamini"));
    }

    @Test
    @WithMockUser(authorities = "CLINIC_READ")  // READ only, not WRITE
    @DisplayName("POST /patients with only CLINIC_READ returns 403")
    void createPatientWithReadOnlyReturns403() throws Exception {
        mvc.perform(post(BASE + "/patients").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Jane\",\"lastName\":\"Dlamini\"}"))
                .andExpect(status().isForbidden());
    }

    // ── PATCH /patients/{id} ──────────────────────────────────────────────────

    @Test
    @WithMockUser(authorities = "CLINIC_WRITE")
    @DisplayName("PATCH /patients/{id} returns 200")
    void patchPatientReturns200() throws Exception {
        var id = UUID.randomUUID();
        when(clinicService.patchPatient(any(), eq(id), any()))
                .thenReturn(patientResponse(id, "Jane", "Dlamini"));

        mvc.perform(patch(BASE + "/patients/" + id).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isOk());
    }

    // ── GET /patients/{id}/family ─────────────────────────────────────────────

    @Test
    @WithMockUser(authorities = "CLINIC_READ")
    @DisplayName("GET /patients/{id}/family returns 200 with list")
    void getFamilyReturns200() throws Exception {
        var id = UUID.randomUUID();
        when(clinicService.getFamilyMembers(any(), eq(id)))
                .thenReturn(List.of(
                        patientResponse(UUID.randomUUID(), "Thomas","Dlamini"),
                        patientResponse(UUID.randomUUID(), "Alex","Dlamini")));

        mvc.perform(get(BASE + "/patients/" + id + "/family"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)));
    }

    // ── Appointments ──────────────────────────────────────────────────────────

    @Test
    @WithMockUser(authorities = "CLINIC_READ")
    @DisplayName("GET /appointments returns 200")
    void getAppointmentsReturns200() throws Exception {
        when(clinicService.getAppointments(any(), any(), any()))
                .thenReturn(Page.empty());

        mvc.perform(get(BASE + "/appointments"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "CLINIC_WRITE")
    @DisplayName("POST /appointments/{id}/confirm returns 200")
    void confirmAppointmentReturns200() throws Exception {
        var id = UUID.randomUUID();
        var response = new AppointmentResponse(id, UUID.randomUUID(), "Jane Dlamini",
                null, null, Instant.now(), 30,
                "CONSULTATION", "CONFIRMED", "Check", null, Instant.now());
        when(clinicService.updateAppointmentStatus(any(), eq(id), eq("confirm")))
                .thenReturn(response);

        mvc.perform(post(BASE + "/appointments/" + id + "/confirm").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"));
    }

    // ── Consultations ─────────────────────────────────────────────────────────

    @Test
    @WithMockUser(authorities = "CLINIC_READ")
    @DisplayName("GET /patients/{id}/consultations returns 200")
    void getPatientConsultationsReturns200() throws Exception {
        var patientId = UUID.randomUUID();
        when(clinicService.getPatientConsultations(any(), eq(patientId)))
                .thenReturn(List.of());

        mvc.perform(get(BASE + "/patients/" + patientId + "/consultations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @WithMockUser(authorities = "CLINIC_WRITE")
    @DisplayName("POST /patients/{id}/consultations returns 201")
    void createConsultationReturns201() throws Exception {
        var patientId = UUID.randomUUID();
        var response  = new ConsultationResponse(
                UUID.randomUUID(), patientId, "Jane Dlamini",
                null, null, null, Instant.now(),
                null, null, null, null, null, null,
                "Annual wellness check", null, null,
                "General wellness check — no acute findings",
                List.of("Z00.0"), "Multi-vitamin supplementation",
                21, false, null, Instant.now());

        when(clinicService.createConsultation(any(), eq(patientId), any()))
                .thenReturn(response);

        var body = Map.of("chiefComplaint","Annual wellness check");

        mvc.perform(post(BASE + "/patients/" + patientId + "/consultations").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.chiefComplaint").value("Annual wellness check"));
    }

    // ── Medications ───────────────────────────────────────────────────────────

    @Test
    @WithMockUser(authorities = "CLINIC_READ")
    @DisplayName("GET /medications?search= returns matching medications")
    void searchMedicationsReturns200() throws Exception {
        var med = new ClinicMedicationCatalogue();
        when(medicationRepo.search(any(), eq("amox")))
                .thenReturn(List.of(med));

        mvc.perform(get(BASE + "/medications").param("search","amox"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }
}
