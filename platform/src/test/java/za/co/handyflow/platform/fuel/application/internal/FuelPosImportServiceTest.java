package za.co.handyflow.platform.fuel.application.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.web.server.ResponseStatusException;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.fuel.domain.model.FuelImportKey;
import za.co.handyflow.platform.fuel.domain.model.FuelImportPumpAlias;
import za.co.handyflow.platform.fuel.domain.repository.FuelImportPumpAliasRepository;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.fuel.domain.model.FuelPosImport;
import za.co.handyflow.platform.fuel.domain.model.FuelPump;
import za.co.handyflow.platform.fuel.domain.repository.FuelImportKeyRepository;
import za.co.handyflow.platform.fuel.domain.repository.FuelPosImportRepository;
import za.co.handyflow.platform.fuel.domain.repository.FuelPumpRepository;
import za.co.handyflow.platform.fuel.dto.ForecourtDtos.*;
import za.co.handyflow.platform.fuel.dto.PosImportDtos.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Phase 8e (D03): key-authenticated, idempotent, staged import. Accepting runs the normal shift code; nothing posts on receipt. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FuelPosImportServiceTest {

    static final TenantId T = TenantId.of(UUID.randomUUID());
    static final UUID USER = UUID.randomUUID();
    static BigDecimal d(String s) { return new BigDecimal(s); }

    @Mock FuelImportKeyRepository keys;
    @Mock FuelPosImportRepository imports;
    @Mock FuelPumpRepository pumps;
    @Mock FuelImportPumpAliasRepository aliasRepo;
    @Mock FuelForecourtService forecourt;
    @Mock FuelDivisionGuard divisions;
    @Mock FeatureGuard featureGuard;
    FuelPosImportService svc;
    FuelImportKey key;
    String raw;
    FuelPump pump1;

    @BeforeEach void setUp() {
        svc = new FuelPosImportService(keys, imports, pumps, aliasRepo, forecourt, divisions, featureGuard, new ObjectMapper().findAndRegisterModules());
        raw = "hf_fuel_testkey";
        key = FuelImportKey.create(T, "Site POS", FuelPosImportService.hashKey(raw), raw.substring(0, 12), USER);
        when(keys.findByKeyHash(FuelPosImportService.hashKey(raw))).thenReturn(Optional.of(key));
        when(keys.save(any(FuelImportKey.class))).thenAnswer(i -> i.getArgument(0));
        when(featureGuard.hasModuleFor(T, "fuel")).thenReturn(true);
        pump1 = FuelPump.create(T, "Pump 1", UUID.randomUUID(), d("23.50"));
        when(pumps.findAllActive(T)).thenReturn(List.of(pump1));
        when(imports.save(any(FuelPosImport.class))).thenAnswer(i -> i.getArgument(0));
        when(imports.findByRef(eq(T), any(), any())).thenReturn(Optional.empty());
    }

    private IncomingShift shift(String ref, String pump, String closing) {
        return new IncomingShift("ACME-POS", ref, "Thabo", Instant.parse("2026-11-12T06:00:00Z"), Instant.parse("2026-11-12T14:00:00Z"),
                d("2000"), d("350"), d("0"), d("0"), null, null, List.of(new IncomingReading(pump, d("1000"), d(closing), null)));
    }

    // ── keys ─────────────────────────────────────────────────────────────────

    @Test @DisplayName("a new key is returned once in plaintext and only its hash is stored")
    void createKey() {
        CreatedKey c = svc.createKey(T, USER, " Site POS ");
        assertTrue(c.apiKey().startsWith("hf_fuel_"));
        ArgumentCaptor<FuelImportKey> cap = ArgumentCaptor.forClass(FuelImportKey.class);
        verify(keys).save(cap.capture());
        assertEquals(FuelPosImportService.hashKey(c.apiKey()), cap.getValue().getKeyHash());
        assertNotEquals(c.apiKey(), cap.getValue().getKeyHash());
        assertEquals("Site POS", cap.getValue().getName());
    }

    @Test @DisplayName("a missing, unknown or revoked key is a 401 with the same message")
    void badKeys() {
        for (String k : new String[]{null, " ", "hf_fuel_nope"}) {
            ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> svc.ingest(k, shift("A1", "Pump 1", "1100")));
            assertEquals(401, e.getStatusCode().value());
        }
        key.revoke();
        assertEquals(401, assertThrows(ResponseStatusException.class, () -> svc.ingest(raw, shift("A1", "Pump 1", "1100"))).getStatusCode().value());
        verify(imports, never()).save(any());
    }

    // ── ingest ───────────────────────────────────────────────────────────────

    @Test @DisplayName("a valid shift is staged as PENDING and changes no shift or stock")
    void stages() {
        IngestResult r = svc.ingest(raw, shift("A1", "pump 1", "1100"));
        assertEquals("PENDING", r.status());
        assertFalse(r.duplicate());
        assertTrue(r.problems().isEmpty());
        verify(imports).save(any(FuelPosImport.class));
        verifyNoInteractions(forecourt);
        verify(divisions).require(eq(T), eq(Set.of(za.co.handyflow.platform.fuel.domain.model.FuelDivision.RETAIL_FORECOURT)));
    }

    @Test @DisplayName("an unknown pump is staged with a problem listed, not rejected")
    void unknownPumpListed() {
        IngestResult r = svc.ingest(raw, shift("A2", "Pump 9", "1100"));
        assertEquals(1, r.problems().size());
        assertTrue(r.problems().get(0).contains("Pump 9"));
    }

    @Test @DisplayName("sending the same shift again returns the original and stages nothing new")
    void idempotent() {
        ArgumentCaptor<FuelPosImport> cap = ArgumentCaptor.forClass(FuelPosImport.class);
        svc.ingest(raw, shift("A1", "Pump 1", "1100"));
        verify(imports).save(cap.capture());
        when(imports.findByRef(T, "ACME-POS", "A1")).thenReturn(Optional.of(cap.getValue()));
        IngestResult again = svc.ingest(raw, shift("A1", "Pump 1", "1100.00"));   // 1100 and 1100.00 are the same content
        assertTrue(again.duplicate());
        assertEquals(cap.getValue().getId(), again.importId());
        verify(imports, times(1)).save(any(FuelPosImport.class));
    }

    @Test @DisplayName("the same reference with different content is a 409 and is never overwritten")
    void changedContentRefused() {
        ArgumentCaptor<FuelPosImport> cap = ArgumentCaptor.forClass(FuelPosImport.class);
        svc.ingest(raw, shift("A1", "Pump 1", "1100"));
        verify(imports).save(cap.capture());
        when(imports.findByRef(T, "ACME-POS", "A1")).thenReturn(Optional.of(cap.getValue()));
        HandyFlowException e = assertThrows(HandyFlowException.class, () -> svc.ingest(raw, shift("A1", "Pump 1", "1200")));
        assertEquals("FUEL_IMPORT_CONTENT_CHANGED", e.getErrorCode());
        verify(imports, times(1)).save(any(FuelPosImport.class));
    }

    @Test @DisplayName("shape errors are 400s and stage nothing: closing below opening, test litres above movement, duplicate pump, closes before it opens")
    void shapeErrors() {
        assertThrows(IllegalArgumentException.class, () -> svc.ingest(raw, shift("B1", "Pump 1", "900")));
        IncomingShift bigTest = new IncomingShift("S", "B2", "T", null, null, d("1"), d("0"), d("0"), d("0"), null, null, List.of(new IncomingReading("Pump 1", d("0"), d("10"), d("11"))));
        assertThrows(IllegalArgumentException.class, () -> svc.ingest(raw, bigTest));
        IncomingShift twice = new IncomingShift("S", "B3", "T", null, null, d("1"), d("0"), d("0"), d("0"), null, null,
                List.of(new IncomingReading("Pump 1", d("0"), d("10"), null), new IncomingReading("PUMP 1", d("0"), d("10"), null)));
        assertThrows(IllegalArgumentException.class, () -> svc.ingest(raw, twice));
        IncomingShift backwards = new IncomingShift("S", "B4", "T", Instant.parse("2026-11-12T10:00:00Z"), Instant.parse("2026-11-12T09:00:00Z"), d("1"), d("0"), d("0"), d("0"), null, null,
                List.of(new IncomingReading("Pump 1", d("0"), d("10"), null)));
        assertThrows(IllegalArgumentException.class, () -> svc.ingest(raw, backwards));
        verify(imports, never()).save(any());
    }

    @Test @DisplayName("a company without the Fuel module, or with Retail off, is refused before anything is staged")
    void moduleAndDivision() {
        when(featureGuard.hasModuleFor(T, "fuel")).thenReturn(false);
        assertEquals("FUEL_MODULE_UNAVAILABLE", assertThrows(HandyFlowException.class, () -> svc.ingest(raw, shift("C1", "Pump 1", "1100"))).getErrorCode());
        when(featureGuard.hasModuleFor(T, "fuel")).thenReturn(true);
        doThrow(new HandyFlowException("off", org.springframework.http.HttpStatus.FORBIDDEN, "FUEL_DIVISION_NOT_ACTIVE")).when(divisions).require(eq(T), any());
        assertThrows(HandyFlowException.class, () -> svc.ingest(raw, shift("C2", "Pump 1", "1100")));
        verify(imports, never()).save(any());
    }

    @Test @DisplayName("the content check ignores reading order and decimal padding")
    void canonicalStable() {
        IncomingShift a = new IncomingShift("S", "R", "T", null, null, d("10.0"), d("0"), d("0"), d("0"), null, null,
                List.of(new IncomingReading("Pump 1", d("0"), d("10"), null), new IncomingReading("Pump 2", d("5"), d("9.50"), null)));
        IncomingShift b = new IncomingShift("S", "R", "T", null, null, d("10.00"), d("0.00"), d("0"), d("0"), null, null,
                List.of(new IncomingReading("pump 2", d("5.0"), d("9.5"), null), new IncomingReading("PUMP 1", d("0.00"), d("10.0"), null)));
        assertEquals(FuelPosImportService.canonical(a), FuelPosImportService.canonical(b));
    }

    // ── review ───────────────────────────────────────────────────────────────

    private FuelPosImport staged(String ref, String pump, String closing) {
        svc.ingest(raw, shift(ref, pump, closing));
        ArgumentCaptor<FuelPosImport> cap = ArgumentCaptor.forClass(FuelPosImport.class);
        verify(imports, atLeastOnce()).save(cap.capture());
        FuelPosImport i = cap.getValue();
        when(imports.findByTenantAndId(T, i.getId())).thenReturn(Optional.of(i));
        return i;
    }

    private ShiftResponse opened(UUID id) {
        return new ShiftResponse(id, "SH-1", "Thabo", "OPEN", Instant.now(), null, null, null, null, null, null, null, null, null, null, null, List.of(), null, null, null, null, null, null);
    }

    @Test @DisplayName("accept opens then closes a shift with the normal code, as the supervisor, with no opener, and marks the import accepted")
    void accepts() {
        FuelPosImport i = staged("A1", "Pump 1", "1100");
        UUID shiftId = UUID.randomUUID();
        when(forecourt.openShift(eq(T), any(OpenShiftRequest.class), any())).thenReturn(opened(shiftId));
        ImportView v = svc.accept(T, i.getId(), new AcceptRequest("short by 5", "checked"), USER);
        ArgumentCaptor<OpenShiftRequest> open = ArgumentCaptor.forClass(OpenShiftRequest.class);
        verify(forecourt).openShift(eq(T), open.capture(), isNull());
        assertEquals("Thabo", open.getValue().attendantName());
        assertEquals(0, d("1000").compareTo(open.getValue().readings().get(0).openingMeter()));
        assertTrue(open.getValue().notes().contains("ACME-POS ref A1"));
        ArgumentCaptor<CloseShiftRequest> close = ArgumentCaptor.forClass(CloseShiftRequest.class);
        verify(forecourt).closeShift(eq(T), eq(shiftId), close.capture(), eq(USER));
        assertEquals(0, d("2000").compareTo(close.getValue().cash()));
        assertEquals("short by 5", close.getValue().varianceReason());
        assertEquals("ACCEPTED", v.status());
        assertEquals(shiftId, v.shiftId());
    }

    @Test @DisplayName("accept with a pump that is not set up is refused and posts nothing")
    void acceptUnknownPump() {
        FuelPosImport i = staged("A3", "Pump 9", "1100");
        assertThrows(IllegalArgumentException.class, () -> svc.accept(T, i.getId(), null, USER));
        verify(forecourt, never()).openShift(any(), any(), any());
        assertTrue(i.isPending());
    }

    @Test @DisplayName("when the normal close refuses (for example the variance rule), the refusal reaches the reviewer and the import stays pending")
    void acceptRefusedByCloseRules() {
        FuelPosImport i = staged("A4", "Pump 1", "1100");
        UUID shiftId = UUID.randomUUID();
        when(forecourt.openShift(eq(T), any(OpenShiftRequest.class), any())).thenReturn(opened(shiftId));
        when(forecourt.closeShift(eq(T), eq(shiftId), any(CloseShiftRequest.class), any())).thenThrow(new IllegalStateException("Enter a reason for the variance."));
        assertThrows(IllegalStateException.class, () -> svc.accept(T, i.getId(), null, USER));
        assertTrue(i.isPending());
    }

    @Test @DisplayName("an import that is already decided cannot be accepted or rejected again")
    void decidedOnce() {
        FuelPosImport i = staged("A5", "Pump 1", "1100");
        svc.reject(T, i.getId(), "Duplicate of shift typed by hand", USER);
        assertEquals("REJECTED", i.getStatus());
        assertThrows(IllegalStateException.class, () -> svc.accept(T, i.getId(), null, USER));
        assertThrows(IllegalStateException.class, () -> svc.reject(T, i.getId(), "again please", USER));
    }

    @Test @DisplayName("reject needs a real reason")
    void rejectNeedsReason() {
        FuelPosImport i = staged("A6", "Pump 1", "1100");
        assertThrows(IllegalArgumentException.class, () -> svc.reject(T, i.getId(), " no", USER));
        assertTrue(i.isPending());
    }

    @Test @DisplayName("list rejects an unknown status and caps the page size")
    void listChecks() {
        assertThrows(IllegalArgumentException.class, () -> svc.list(T, "DONE", 10));
        when(imports.findByStatus(eq(T), eq("PENDING"), any())).thenReturn(List.of());
        assertTrue(svc.list(T, "pending", 100000).isEmpty());
        ArgumentCaptor<org.springframework.data.domain.Pageable> p = ArgumentCaptor.forClass(org.springframework.data.domain.Pageable.class);
        verify(imports).findByStatus(eq(T), eq("PENDING"), p.capture());
        assertEquals(200, p.getValue().getPageSize());
    }

    @Test @DisplayName("revoking a key stops it working")
    void revoke() {
        when(keys.findByTenantAndId(T, key.getId())).thenReturn(Optional.of(key));
        assertNotNull(svc.revokeKey(T, key.getId()).revokedAt());
        assertThrows(ResponseStatusException.class, () -> svc.ingest(raw, shift("D1", "Pump 1", "1100")));
    }

    // ── pump labels ──────────────────────────────────────────────────────────

    @Test @DisplayName("a label mapped for the sender's source finds the pump, so the shift has no problem listed")
    void aliasResolves() {
        var a = FuelImportPumpAlias.of(T, "acme-pos", "P-01", pump1.getId(), USER);
        when(aliasRepo.findBySource(T, "acme-pos")).thenReturn(List.of(a));
        IngestResult r = svc.ingest(raw, shift("B1", "p-01", "1100"));
        assertTrue(r.problems().isEmpty());
    }

    @Test @DisplayName("a label mapped for another source does not apply")
    void aliasIsPerSource() {
        when(aliasRepo.findBySource(T, "other-pos")).thenReturn(List.of(FuelImportPumpAlias.of(T, "other-pos", "P-01", pump1.getId(), USER)));
        assertEquals(1, svc.ingest(raw, shift("B2", "P-01", "1100")).problems().size());
    }

    @Test @DisplayName("two labels that point at the same pump are listed as a problem")
    void twoLabelsSamePump() {
        when(aliasRepo.findBySource(T, "acme-pos")).thenReturn(List.of(FuelImportPumpAlias.of(T, "acme-pos", "P-01", pump1.getId(), USER)));
        IncomingShift in = new IncomingShift("ACME-POS", "B3", "Thabo", null, null, d("0"), d("0"), d("0"), d("0"), null, null,
                List.of(new IncomingReading("Pump 1", d("1000"), d("1100"), null), new IncomingReading("P-01", d("500"), d("600"), null)));
        assertTrue(svc.ingest(raw, in).problems().stream().anyMatch(p -> p.contains("same pump")));
    }

    @Test @DisplayName("adding a label needs an active pump and refuses a label already mapped for that source")
    void addAlias() {
        var v = svc.addAlias(T, USER, new AliasRequest(" ACME-POS ", " P-01 ", pump1.getId()));
        assertEquals("acme-pos", v.source()); assertEquals("p-01", v.alias()); assertEquals("Pump 1", v.pumpName());
        assertThrows(ResourceNotFoundException.class, () -> svc.addAlias(T, USER, new AliasRequest("acme-pos", "x", UUID.randomUUID())));
        when(aliasRepo.findOne(T, "acme-pos", "p-01")).thenReturn(Optional.of(mock(FuelImportPumpAlias.class)));
        assertThrows(IllegalStateException.class, () -> svc.addAlias(T, USER, new AliasRequest("acme-pos", "P-01", pump1.getId())));
    }

    @Test @DisplayName("removing a label that does not exist is a not-found")
    void removeMissing() {
        when(aliasRepo.findById(eq(T), any(UUID.class))).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> svc.removeAlias(T, UUID.randomUUID()));
    }
}
