//package za.co.handyflow.platform.clinic.api;
//
//import com.fasterxml.jackson.databind.ObjectMapper;
//import org.junit.jupiter.api.*;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
//import org.springframework.test.context.bean.override.mockito.MockitoBean;
//import org.springframework.http.MediaType;
//import org.springframework.security.test.context.support.WithMockUser;
//import org.springframework.test.web.servlet.MockMvc;
//import za.co.handyflow.platform.clinic.application.internal.ClinicLabService;
//import za.co.handyflow.platform.clinic.dto.lab.*;
//
//import java.time.Instant;
//import java.util.*;
//
//import static org.mockito.ArgumentMatchers.*;
//import static org.mockito.Mockito.*;
//import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
//import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
//import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
//
//@WebMvcTest(ClinicLabController.class)
//class ClinicLabControllerTest {
//
//    @Autowired MockMvc mvc;
//    @Autowired ObjectMapper mapper;
//    @MockitoBean  ClinicLabService labService;
//
//    static final String BASE = "/api/v1/clinic/lab";
//
//    LabResultResponse response(UUID id, String status) {
//        return new LabResultResponse(id, UUID.randomUUID(), null,
//                "AMPATH", "AMP-001", null, Instant.now(),
//                null, "result.pdf", status,
//                "Nkosi S", null, null, false, Instant.now());
//    }
//
//    @Test
//    @WithMockUser(authorities = "CLINIC_READ")
//    @DisplayName("GET /lab/results returns 200")
//    void getResultsReturns200() throws Exception {
//        when(labService.getResults(any(), any())).thenReturn(List.of());
//        mvc.perform(get(BASE + "/results"))
//                .andExpect(status().isOk())
//                .andExpect(jsonPath("$.data").isArray());
//    }
//
//    @Test
//    @WithMockUser(authorities = "CLINIC_READ")
//    @DisplayName("GET /lab/patients/{id}/results returns patient results")
//    void getPatientResultsReturns200() throws Exception {
//        var patientId = UUID.randomUUID();
//        var res = response(UUID.randomUUID(), "UNREVIEWED");
//        when(labService.getPatientResults(any(), eq(patientId))).thenReturn(List.of(res));
//
//        mvc.perform(get(BASE + "/patients/" + patientId + "/results"))
//                .andExpect(status().isOk())
//                .andExpect(jsonPath("$.data[0].status").value("UNREVIEWED"));
//    }
//
//    @Test
//    @WithMockUser(authorities = "CLINIC_WRITE")
//    @DisplayName("POST /lab/results returns 201")
//    void uploadResultReturns201() throws Exception {
//        var id  = UUID.randomUUID();
//        var res = response(id, "UNREVIEWED");
//        when(labService.uploadResult(any(), any())).thenReturn(res);
//
//        var body = Map.of("source","AMPATH","pdfFilename","result.pdf",
//                "patientNameRaw","Nkosi S","labReference","AMP-001");
//
//        mvc.perform(post(BASE + "/results").with(csrf())
//                        .contentType(MediaType.APPLICATION_JSON)
//                        .content(mapper.writeValueAsString(body)))
//                .andExpect(status().isCreated())
//                .andExpect(jsonPath("$.data.source").value("AMPATH"));
//    }
//
//    @Test
//    @WithMockUser(authorities = "CLINIC_WRITE")
//    @DisplayName("POST /lab/results/{id}/interpret saves interpretation")
//    void interpretResultReturns200() throws Exception {
//        var id  = UUID.randomUUID();
//        var res = response(id, "REVIEWED");
//        when(labService.setInterpretation(any(), eq(id), anyString())).thenReturn(res);
//
//        mvc.perform(post(BASE + "/results/" + id + "/interpret").with(csrf())
//                        .param("interpretation", "HbA1c elevated — suboptimal control"))
//                .andExpect(status().isOk());
//
//        verify(labService).setInterpretation(any(), eq(id),
//                eq("HbA1c elevated — suboptimal control"));
//    }
//
//    @Test
//    @WithMockUser(authorities = "CLINIC_WRITE")
//    @DisplayName("POST /lab/results/{id}/review accepts 'current' as reviewedBy string")
//    void reviewAcceptsCurrentString() throws Exception {
//        var id  = UUID.randomUUID();
//        var res = response(id, "REVIEWED");
//        // reviewedBy="current" should resolve to null UUID (not crash)
//        when(labService.markReviewed(any(), eq(id), isNull())).thenReturn(res);
//
//        mvc.perform(post(BASE + "/results/" + id + "/review").with(csrf())
//                        .param("reviewedBy", "current"))
//                .andExpect(status().isOk());
//
//        verify(labService).markReviewed(any(), eq(id), isNull());
//    }
//
//    @Test
//    @WithMockUser(authorities = "CLINIC_WRITE")
//    @DisplayName("POST /lab/results/{id}/file links to consultation")
//    void fileResultReturns200() throws Exception {
//        var id          = UUID.randomUUID();
//        var consultId   = UUID.randomUUID();
//        var res         = response(id, "FILED");
//        when(labService.fileResult(any(), eq(id), eq(consultId))).thenReturn(res);
//
//        mvc.perform(post(BASE + "/results/" + id + "/file").with(csrf())
//                        .param("consultationId", consultId.toString()))
//                .andExpect(status().isOk())
//                .andExpect(jsonPath("$.data.status").value("FILED"));
//    }
//
//    @Test
//    @DisplayName("POST /lab/results without auth returns 403")
//    void uploadWithoutAuthReturns403() throws Exception {
//        mvc.perform(post(BASE + "/results").with(csrf())
//                        .contentType(MediaType.APPLICATION_JSON)
//                        .content("{}"))
//                .andExpect(status().isForbidden());
//    }
//}
