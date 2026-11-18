package za.co.handyflow.platform.fuel.api;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.fuel.application.internal.FuelCashControlService;
import za.co.handyflow.platform.fuel.application.internal.FuelForecourtService;
import za.co.handyflow.platform.fuel.application.internal.FuelOfflineService;
import za.co.handyflow.platform.fuel.application.internal.FuelScopeService;
import za.co.handyflow.platform.fuel.application.internal.FuelStayInvoicingService;
import za.co.handyflow.platform.fuel.application.internal.FuelTankReconciliationService;
import za.co.handyflow.platform.fuel.application.internal.FuelTruckStopFleetLinks;
import za.co.handyflow.platform.fuel.application.internal.FuelTruckStopStayService;
import za.co.handyflow.platform.fuel.dto.ForecourtDtos.CloseShiftRequest;
import za.co.handyflow.platform.fuel.dto.ForecourtDtos.OpenShiftRequest;
import za.co.handyflow.platform.fuel.dto.StayDtos.CheckInRequest;
import za.co.handyflow.platform.fuel.dto.StayDtos.CheckOutRequest;
import za.co.handyflow.platform.fuel.dto.StayDtos.StayView;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;

/**
 * The HTTP door of the offline writes, with Spring method security switched on: the request id and captured time must reach the service,
 * a bad body is a 400 before any service runs, and the permission is checked on every call, so a write that arrives late from a phone whose
 * user has lost the permission is refused and never reaches the service.
 * Needs no database: the services are mocks. Not a test of the services (see FuelOfflineServiceTest).
 */
@SpringJUnitConfig(FuelOfflineControllersTest.Cfg.class)
class FuelOfflineControllersTest {

    @Configuration
    @EnableMethodSecurity
    static class Cfg {
        @Bean FuelOfflineService offline() { return mock(FuelOfflineService.class); }
        @Bean FuelForecourtService forecourt() { return mock(FuelForecourtService.class); }
        @Bean FuelTruckStopStayService stayService() { return mock(FuelTruckStopStayService.class); }
        @Bean FeatureGuard featureGuard() { return mock(FeatureGuard.class); }
        @Bean FuelForecourtController forecourtController(FuelForecourtService f, FuelOfflineService o, FeatureGuard g) {
            return new FuelForecourtController(f, o, mock(FuelScopeService.class), mock(FuelCashControlService.class), mock(FuelTankReconciliationService.class), g);
        }
        @Bean FuelTruckStopStayController stayController(FuelTruckStopStayService s, FuelOfflineService o, FeatureGuard g) {
            return new FuelTruckStopStayController(s, o, mock(FuelStayInvoicingService.class), mock(FuelTruckStopFleetLinks.class), g);
        }
    }

    @Autowired FuelOfflineService offline;
    @Autowired FuelForecourtController forecourtController;
    @Autowired FuelTruckStopStayController stayController;

    static final UUID TENANT = UUID.randomUUID(), USER = UUID.randomUUID(), PUMP = UUID.randomUUID(), SHIFT = UUID.randomUUID(), STAY = UUID.randomUUID();
    MockMvc forecourt, stays;

    @BeforeEach void setUp() {
        reset(offline);
        TenantContext.setTenantId(TENANT.toString());
        TenantContext.setUserId(USER.toString());
        as("FUEL_FORECOURT");
        forecourt = MockMvcBuilders.standaloneSetup(forecourtController).build();
        stays = MockMvcBuilders.standaloneSetup(stayController).build();
    }

    @AfterEach void tearDown() { SecurityContextHolder.clearContext(); TenantContext.clear(); }

    private static void as(String... authorities) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("user", "n/a", AuthorityUtils.createAuthorityList(authorities)));
    }

    static final String RID = "7b3f6a52-0c1e-4c64-9a52-2d0d1f0b6a11";
    static final String OPEN_BODY = "{\"attendantName\":\"Thandi\",\"readings\":[{\"pumpId\":\"" + PUMP + "\",\"openingMeter\":1000,\"expectedPrice\":22.5}],"
            + "\"clientRequestId\":\"" + RID + "\",\"capturedAt\":\"2026-11-18T06:00:00Z\"}";
    static final String CLOSE_BODY = "{\"cash\":100,\"card\":0,\"account\":0,\"other\":0,\"readings\":[{\"pumpId\":\"" + PUMP + "\",\"closingMeter\":1100}],"
            + "\"clientRequestId\":\"" + RID + "\",\"capturedAt\":\"2026-11-18T14:00:00Z\"}";

    // ── Forecourt ─────────────────────────────────────────────────────────────

    @Test @DisplayName("open: the request id, captured time and the price the phone showed reach the service, and the answer is 201")
    void openPassesTheOfflineFieldsOn() throws Exception {
        forecourt.perform(post("/api/v1/fuel/forecourt/shifts").contentType(MediaType.APPLICATION_JSON).content(OPEN_BODY)).andExpect(status().isCreated());
        ArgumentCaptor<OpenShiftRequest> req = ArgumentCaptor.forClass(OpenShiftRequest.class);
        verify(offline).openShift(eq(TenantId.of(TENANT)), req.capture(), eq(USER), any(Instant.class));
        assertEquals(UUID.fromString(RID), req.getValue().clientRequestId());
        assertEquals(Instant.parse("2026-11-18T06:00:00Z"), req.getValue().capturedAt());
        assertEquals(0, new java.math.BigDecimal("22.5").compareTo(req.getValue().readings().get(0).expectedPrice()));
    }

    @Test @DisplayName("open: an older app that sends no request id and no price still works, as an ordinary open")
    void openWithoutOfflineFieldsStillWorks() throws Exception {
        String body = "{\"attendantName\":\"Thandi\",\"readings\":[{\"pumpId\":\"" + PUMP + "\",\"openingMeter\":1000}]}";
        forecourt.perform(post("/api/v1/fuel/forecourt/shifts").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated());
        ArgumentCaptor<OpenShiftRequest> req = ArgumentCaptor.forClass(OpenShiftRequest.class);
        verify(offline).openShift(any(), req.capture(), any(), any());
        assertNull(req.getValue().clientRequestId());
        assertNull(req.getValue().readings().get(0).expectedPrice());
    }

    @Test @DisplayName("open: a body with no pumps, or a negative meter or price, is a 400 and no service runs")
    void badOpenBodiesAreRefusedBeforeTheService() throws Exception {
        for (String body : List.of(
                "{\"attendantName\":\"Thandi\",\"readings\":[]}",
                "{\"attendantName\":\"\",\"readings\":[{\"pumpId\":\"" + PUMP + "\",\"openingMeter\":1}]}",
                "{\"attendantName\":\"T\",\"readings\":[{\"pumpId\":\"" + PUMP + "\",\"openingMeter\":-1}]}",
                "{\"attendantName\":\"T\",\"readings\":[{\"pumpId\":\"" + PUMP + "\",\"openingMeter\":1,\"expectedPrice\":-5}]}")) {
            forecourt.perform(post("/api/v1/fuel/forecourt/shifts").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        }
        verifyNoInteractions(offline);
    }

    @Test @DisplayName("close: the request id and captured time reach the service for the shift in the path")
    void closePassesTheOfflineFieldsOn() throws Exception {
        forecourt.perform(post("/api/v1/fuel/forecourt/shifts/" + SHIFT + "/close").contentType(MediaType.APPLICATION_JSON).content(CLOSE_BODY)).andExpect(status().isOk());
        ArgumentCaptor<CloseShiftRequest> req = ArgumentCaptor.forClass(CloseShiftRequest.class);
        verify(offline).closeShift(eq(TenantId.of(TENANT)), eq(SHIFT), req.capture(), eq(USER), any(Instant.class));
        assertEquals(UUID.fromString(RID), req.getValue().clientRequestId());
        assertEquals(Instant.parse("2026-11-18T14:00:00Z"), req.getValue().capturedAt());
    }

    @Test @DisplayName("close by the open's request id: reaches closeByOpenRequest with that id")
    void closeByOpenRequest() throws Exception {
        UUID openRid = UUID.randomUUID();
        forecourt.perform(post("/api/v1/fuel/forecourt/shifts/by-request/" + openRid + "/close").contentType(MediaType.APPLICATION_JSON).content(CLOSE_BODY)).andExpect(status().isOk());
        verify(offline).closeByOpenRequest(eq(TenantId.of(TENANT)), eq(openRid), any(CloseShiftRequest.class), eq(USER), any(Instant.class));
        verify(offline, never()).closeShift(any(), any(), any(), any(), any());
    }

    @Test @DisplayName("close: a negative takings figure or no readings is a 400 and no service runs")
    void badCloseBodiesAreRefused() throws Exception {
        forecourt.perform(post("/api/v1/fuel/forecourt/shifts/" + SHIFT + "/close").contentType(MediaType.APPLICATION_JSON)
                .content("{\"cash\":-1,\"card\":0,\"account\":0,\"other\":0,\"readings\":[{\"pumpId\":\"" + PUMP + "\",\"closingMeter\":1}]}")).andExpect(status().isBadRequest());
        forecourt.perform(post("/api/v1/fuel/forecourt/shifts/" + SHIFT + "/close").contentType(MediaType.APPLICATION_JSON)
                .content("{\"cash\":0,\"card\":0,\"account\":0,\"other\":0,\"readings\":[]}")).andExpect(status().isBadRequest());
        verifyNoInteractions(offline);
    }

    // ── Truck stop ────────────────────────────────────────────────────────────

    @Test @DisplayName("stay check-in: the request id and captured time reach the service; an online check-in with no body works")
    void checkInPassesTheOfflineFieldsOn() throws Exception {
        as("FUEL_TRUCKSTOP_MANAGE");
        stays.perform(post("/api/v1/fuel/truck-stop/stays/" + STAY + "/check-in").contentType(MediaType.APPLICATION_JSON)
                .content("{\"clientRequestId\":\"" + RID + "\",\"capturedAt\":\"2026-11-18T05:00:00Z\"}")).andExpect(status().isOk());
        ArgumentCaptor<CheckInRequest> req = ArgumentCaptor.forClass(CheckInRequest.class);
        verify(offline).checkIn(eq(TenantId.of(TENANT)), eq(STAY), req.capture(), eq(USER), any(), any(Instant.class));
        assertEquals(UUID.fromString(RID), req.getValue().clientRequestId());

        reset(offline);
        stays.perform(post("/api/v1/fuel/truck-stop/stays/" + STAY + "/check-in")).andExpect(status().isOk());
        verify(offline).checkIn(eq(TenantId.of(TENANT)), eq(STAY), isNull(), eq(USER), any(), any(Instant.class));
    }

    @Test @DisplayName("stay check-out: the request id reaches the service, and a stay with no account raises no invoice")
    void checkOutPassesTheOfflineFieldsOn() {
        as("FUEL_TRUCKSTOP_MANAGE");
        StayView noAccount = mock(StayView.class);
        when(offline.checkOut(any(), eq(STAY), any(), any(), any(), any())).thenReturn(noAccount);
        CheckOutRequest req = new CheckOutRequest(null, UUID.fromString(RID), Instant.parse("2026-11-18T09:00:00Z"));
        assertNotNull(stayController.checkOut(STAY, req));
        verify(offline).checkOut(eq(TenantId.of(TENANT)), eq(STAY), eq(req), eq(USER), any(), any(Instant.class));
    }

    // ── permissions are checked on every call ─────────────────────────────────

    @Test @DisplayName("revoked permission: a shift open that arrives after the user lost FUEL_FORECOURT is refused and never reaches the service")
    void aLateOpenAfterARoleChangeIsRefused() throws Exception {
        forecourt.perform(post("/api/v1/fuel/forecourt/shifts").contentType(MediaType.APPLICATION_JSON).content(OPEN_BODY)).andExpect(status().isCreated());
        verify(offline, times(1)).openShift(any(), any(), any(), any());

        as("FUEL_READ"); // the role changed while the phone was offline; the queued write now arrives
        Exception e = assertThrows(Exception.class, () -> forecourt.perform(post("/api/v1/fuel/forecourt/shifts").contentType(MediaType.APPLICATION_JSON).content(OPEN_BODY)));
        assertTrue(isDenied(e), "expected access denied, got " + e);
        verify(offline, times(1)).openShift(any(), any(), any(), any()); // still only the first
    }

    @Test @DisplayName("revoked permission: close, close-by-request, check-in and check-out are all refused without the permission")
    void everyOfflineWriteNeedsItsPermission() {
        as("FUEL_READ");
        assertThrows(AccessDeniedException.class, () -> forecourtController.close(SHIFT, new CloseShiftRequest(java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO, null,
                List.of(new za.co.handyflow.platform.fuel.dto.ForecourtDtos.CloseReading(PUMP, java.math.BigDecimal.ONE, null)))));
        assertThrows(AccessDeniedException.class, () -> forecourtController.closeByRequest(UUID.randomUUID(), new CloseShiftRequest(java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO, null,
                List.of(new za.co.handyflow.platform.fuel.dto.ForecourtDtos.CloseReading(PUMP, java.math.BigDecimal.ONE, null)))));
        assertThrows(AccessDeniedException.class, () -> stayController.checkIn(STAY, new CheckInRequest(UUID.randomUUID(), Instant.now())));
        assertThrows(AccessDeniedException.class, () -> stayController.checkOut(STAY, new CheckOutRequest(null, UUID.randomUUID(), Instant.now())));
        verifyNoInteractions(offline);
    }

    @Test @DisplayName("an attendant (FUEL_FORECOURT) can run shifts but cannot touch the truck stop, and the reverse")
    void permissionsDoNotLeakAcrossDivisions() {
        as("FUEL_FORECOURT");
        assertThrows(AccessDeniedException.class, () -> stayController.checkIn(STAY, null));
        as("FUEL_TRUCKSTOP_MANAGE");
        assertThrows(AccessDeniedException.class, () -> forecourtController.closeByRequest(UUID.randomUUID(), new CloseShiftRequest(java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO, null,
                List.of(new za.co.handyflow.platform.fuel.dto.ForecourtDtos.CloseReading(PUMP, java.math.BigDecimal.ONE, null)))));
        verifyNoInteractions(offline);
    }

    @Test @DisplayName("no sign-in at all is refused")
    void anonymousIsRefused() {
        SecurityContextHolder.clearContext();
        assertThrows(Exception.class, () -> stayController.checkIn(STAY, null));
        verifyNoInteractions(offline);
    }

    private static boolean isDenied(Throwable t) {
        for (Throwable c = t; c != null; c = c.getCause()) if (c instanceof AccessDeniedException) return true;
        return false;
    }
}
