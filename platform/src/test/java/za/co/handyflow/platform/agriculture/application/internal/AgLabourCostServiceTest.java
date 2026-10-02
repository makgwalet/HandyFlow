package za.co.handyflow.platform.agriculture.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import za.co.handyflow.platform.agriculture.application.internal.AgFinanceSettingsService.Effective;
import za.co.handyflow.platform.agriculture.domain.model.AgCropCycle;
import za.co.handyflow.platform.agriculture.domain.model.AgFarm;
import za.co.handyflow.platform.agriculture.domain.model.AgHarvestRecord;
import za.co.handyflow.platform.agriculture.domain.model.AgInputApplication;
import za.co.handyflow.platform.agriculture.domain.repository.*;
import za.co.handyflow.platform.agriculture.dto.CostEntryDtos.CostEntryResponse;
import za.co.handyflow.platform.agriculture.dto.LabourDtos.CostLabourRequest;
import za.co.handyflow.platform.agriculture.dto.LabourDtos.LabourCandidate;
import za.co.handyflow.platform.agriculture.dto.LabourDtos.LabourItem;
import za.co.handyflow.platform.agriculture.dto.LabourDtos.LabourOverview;
import za.co.handyflow.platform.hr.application.HrFacade;
import za.co.handyflow.platform.hr.dto.EmployeeResponse;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AgLabourCostServiceTest {

    @Mock AgInputApplicationRepository inputRepository;
    @Mock AgHarvestRecordRepository harvestRepository;
    @Mock AgCropCycleRepository cropCycleRepository;
    @Mock AgCostEntryRepository costEntryRepository;
    @Mock AgFarmRepository farmRepository;
    @Mock AgCostEntryService costEntryService;
    @Mock AgFinanceSettingsService settingsService;
    @Mock HrFacade hrFacade;

    static final TenantId TENANT = TenantId.of(UUID.randomUUID());
    final UUID farmId = UUID.randomUUID(), otherFarm = UUID.randomUUID(), user = UUID.randomUUID(), cycleId = UUID.randomUUID();
    final UUID inputId = UUID.randomUUID(), harvestId = UUID.randomUUID(), workerId = UUID.randomUUID();
    static final LocalDate SPRAY_DAY = LocalDate.of(2026, 9, 10), HARVEST_DAY = LocalDate.of(2026, 9, 20);

    private AgLabourCostService service() {
        return new AgLabourCostService(inputRepository, harvestRepository, cropCycleRepository, costEntryRepository, farmRepository, costEntryService, settingsService, hrFacade);
    }

    private static BigDecimal bd(String s) { return new BigDecimal(s); }
    private static void assertNumber(String expected, BigDecimal actual) { assertNotNull(actual, "expected " + expected + " but was null"); assertEquals(0, bd(expected).compareTo(actual), "expected " + expected + " but was " + actual); }

    // ---- fixtures: every mock is fully stubbed BEFORE it is handed to a when(...), because Mockito cannot stub inside an open stubbing ----

    private void farmExists() { when(farmRepository.findActiveById(eq(TENANT), eq(farmId))).thenReturn(Optional.of(mock(AgFarm.class))); }

    private void settings(String hours, String onCost) {
        Effective e = new Effective(bd(hours), bd(onCost), true);
        when(settingsService.effective(eq(TENANT))).thenReturn(e);
    }

    private void cycleOnFarm(UUID owner) {
        AgCropCycle c = mock(AgCropCycle.class);
        when(c.getFarmId()).thenReturn(owner);
        when(cropCycleRepository.findActiveById(eq(TENANT), eq(cycleId))).thenReturn(Optional.of(c));
    }

    private AgInputApplication input(UUID id, UUID worker, String workerName, String hours) {
        AgInputApplication a = mock(AgInputApplication.class);
        when(a.getId()).thenReturn(id);
        when(a.getCropCycleId()).thenReturn(cycleId);
        when(a.getApplicationDate()).thenReturn(SPRAY_DAY);
        when(a.getProductUsed()).thenReturn("Roundup");
        when(a.getInputType()).thenReturn("HERBICIDE");
        when(a.getAppliedBy()).thenReturn(worker);
        when(a.getAppliedByName()).thenReturn(workerName);
        when(a.getLaborHours()).thenReturn(hours == null ? null : bd(hours));
        return a;
    }

    private AgHarvestRecord harvest(UUID id, UUID worker, String workerName, String hours) {
        AgHarvestRecord h = mock(AgHarvestRecord.class);
        when(h.getId()).thenReturn(id);
        when(h.getCropCycleId()).thenReturn(cycleId);
        when(h.getHarvestDate()).thenReturn(HARVEST_DAY);
        when(h.getHarvestedBy()).thenReturn(worker);
        when(h.getHarvestedByName()).thenReturn(workerName);
        when(h.getLaborHours()).thenReturn(hours == null ? null : bd(hours));
        return h;
    }

    private void employeeInHr(UUID id, String salary, String frequency) {
        EmployeeResponse e = mock(EmployeeResponse.class);
        when(e.grossSalary()).thenReturn(salary == null ? null : bd(salary));
        when(e.payFrequency()).thenReturn(frequency);
        when(hrFacade.findEmployeeById(eq(TENANT), eq(id))).thenReturn(Optional.of(e));
    }

    private void uncosted(List<AgInputApplication> inputs, List<AgHarvestRecord> harvests) {
        when(inputRepository.findUncostedLabourForFarm(eq(TENANT), eq(farmId), any())).thenReturn(inputs);
        when(harvestRepository.findUncostedLabourForFarm(eq(TENANT), eq(farmId), any())).thenReturn(harvests);
    }

    private void inputIsFound(UUID id, AgInputApplication a) { when(inputRepository.findByTenantAndId(eq(TENANT), eq(id))).thenReturn(Optional.of(a)); }
    private void harvestIsFound(UUID id, AgHarvestRecord h) { when(harvestRepository.findByTenantAndId(eq(TENANT), eq(id))).thenReturn(Optional.of(h)); }

    private CostLabourRequest items(LabourItem... i) { return new CostLabourRequest(List.of(i)); }
    private LabourItem sprayItem(String rate) { return new LabourItem("INPUT_APPLICATION", inputId, rate == null ? null : bd(rate)); }

    private void verifyNothingRecorded() {
        verify(costEntryService, never()).record(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    // ---- overview ---------------------------------------------------------------------------------------------------

    @Test
    @DisplayName("a worker linked to HR gets their salary-based rate offered, with the on-cost in the estimate")
    void offersTheHrRate() {
        farmExists(); settings("45", "2"); employeeInHr(workerId, "19500", "MONTHLY");
        AgInputApplication a = input(inputId, workerId, "Thandi M.", "6");
        uncosted(List.of(a), List.of());

        LabourOverview o = service().overview(TENANT, farmId, true);

        assertTrue(o.hrRatesAvailable());
        assertEquals(1, o.candidates().size());
        LabourCandidate c = o.candidates().get(0);
        assertEquals("INPUT_APPLICATION", c.sourceType());
        assertEquals("application of Roundup", c.description());
        assertEquals(SPRAY_DAY, c.date());
        assertEquals("Thandi M.", c.workerName());
        assertNumber("6", c.hours());
        assertNumber("100.0000", c.suggestedRate());            // 19 500 / 195 ordinary hours in a month
        assertEquals("HR", c.rateSource());
        assertNull(c.rateNote());
        assertNumber("612.00", c.estimatedCost());              // 6 h x (100 + 2%)
        assertNumber("2", o.settings().labourOnCostPercent());
    }

    @Test
    @DisplayName("without HR access no salary is read and no HR rate is offered")
    void noHrAccessMeansNoHrRate() {
        farmExists(); settings("45", "0");
        AgInputApplication a = input(inputId, workerId, "Thandi M.", "6");
        uncosted(List.of(a), List.of());

        LabourOverview o = service().overview(TENANT, farmId, false);

        assertFalse(o.hrRatesAvailable());
        LabourCandidate c = o.candidates().get(0);
        assertNull(c.suggestedRate());
        assertNull(c.estimatedCost());
        assertEquals("NONE", c.rateSource());
        assertTrue(c.rateNote().contains("HR access"), c.rateNote());
        verifyNoInteractions(hrFacade);
    }

    @Test
    @DisplayName("work with no linked employee says so, naming the worker, and never asks HR")
    void noLinkedWorker() {
        farmExists(); settings("45", "0");
        AgInputApplication a = input(inputId, null, "Casual Joe", "4");
        uncosted(List.of(a), List.of());

        LabourCandidate c = service().overview(TENANT, farmId, true).candidates().get(0);

        assertNull(c.suggestedRate());
        assertTrue(c.rateNote().contains("no HR employee") && c.rateNote().contains("Casual Joe"), c.rateNote());
        verifyNoInteractions(hrFacade);
    }

    @Test
    @DisplayName("an employee with no usable salary, or a pay frequency it cannot convert, gets no rate and a reason")
    void unusableSalary() {
        farmExists(); settings("45", "0");
        AgInputApplication a = input(inputId, workerId, "Thandi M.", "6");
        uncosted(List.of(a), List.of());

        employeeInHr(workerId, "0", "MONTHLY");
        assertTrue(service().overview(TENANT, farmId, true).candidates().get(0).rateNote().contains("no usable salary"));

        employeeInHr(workerId, "19500", "DAILY");
        assertNull(service().overview(TENANT, farmId, true).candidates().get(0).suggestedRate());
    }

    @Test
    @DisplayName("an employee that HR cannot find says so")
    void employeeMissing() {
        farmExists(); settings("45", "0");
        AgInputApplication a = input(inputId, workerId, "Thandi M.", "6");
        uncosted(List.of(a), List.of());
        when(hrFacade.findEmployeeById(eq(TENANT), eq(workerId))).thenReturn(Optional.empty());

        assertTrue(service().overview(TENANT, farmId, true).candidates().get(0).rateNote().contains("not found in HR"));
    }

    @Test
    @DisplayName("each worker is looked up in HR once however much of their work is listed")
    void looksEachWorkerUpOnce() {
        farmExists(); settings("45", "0"); employeeInHr(workerId, "19500", "MONTHLY");
        AgInputApplication a = input(inputId, workerId, "Thandi M.", "6");
        AgHarvestRecord h = harvest(harvestId, workerId, "Thandi M.", "8");
        uncosted(List.of(a), List.of(h));

        service().overview(TENANT, farmId, true);

        verify(hrFacade, times(1)).findEmployeeById(eq(TENANT), eq(workerId));
    }

    @Test
    @DisplayName("input applications and harvests are listed together, newest first")
    void newestFirst() {
        farmExists(); settings("45", "0");
        AgInputApplication a = input(inputId, null, "Joe", "6");
        AgHarvestRecord h = harvest(harvestId, null, "Joe", "8");
        uncosted(List.of(a), List.of(h));

        List<LabourCandidate> rows = service().overview(TENANT, farmId, true).candidates();

        assertEquals("HARVEST", rows.get(0).sourceType());
        assertEquals(HARVEST_DAY, rows.get(0).date());
        assertEquals("harvest", rows.get(0).description());
        assertEquals("INPUT_APPLICATION", rows.get(1).sourceType());
    }

    @Test
    @DisplayName("the response shapes can never carry a salary: only the rate worked out from it")
    void neverReturnsASalary() {
        for (Class<?> type : List.of(LabourCandidate.class, LabourOverview.class)) {
            for (RecordComponent c : type.getRecordComponents()) {
                String n = c.getName().toLowerCase();
                assertFalse(n.contains("salary") || n.contains("gross") || n.contains("wage") || n.contains("pay"), type.getSimpleName() + " exposes " + c.getName());
            }
        }
    }

    @Test
    @DisplayName("an unknown farm is a 404")
    void unknownFarm() {
        assertThrows(ResourceNotFoundException.class, () -> service().overview(TENANT, UUID.randomUUID(), true));
        assertThrows(ResourceNotFoundException.class, () -> service().cost(TENANT, UUID.randomUUID(), user, true, items(sprayItem(null))));
    }

    // ---- costing ----------------------------------------------------------------------------------------------------

    @Test
    @DisplayName("costing at the HR rate snapshots the on-cost-loaded rate and prices the hours from it, against the crop cycle")
    void costsAtTheHrRate() {
        farmExists(); settings("45", "2"); employeeInHr(workerId, "19500", "MONTHLY"); cycleOnFarm(farmId);
        AgInputApplication a = input(inputId, workerId, "Thandi M.", "6");
        inputIsFound(inputId, a);
        CostEntryResponse entry = mock(CostEntryResponse.class);
        when(costEntryService.record(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(List.of(entry));

        List<CostEntryResponse> created = service().cost(TENANT, farmId, user, true, items(sprayItem(null)));

        assertEquals(1, created.size());
        verify(costEntryService).record(eq(TENANT), eq(farmId), eq(user), eq(SPRAY_DAY), eq("LABOUR"), eq("Labour: application of Roundup"), eq("HR_LABOUR"), eq(inputId),
                eq(bd("6")), eq("h"), eq(bd("102.0000")), eq(bd("612.00")), any(), any());
    }

    @Test
    @DisplayName("a typed-in rate replaces the HR one and HR is never asked: this is how casuals are costed")
    void typedRateNeverTouchesHr() {
        farmExists(); settings("45", "2"); cycleOnFarm(farmId);
        AgInputApplication a = input(inputId, null, "Casual Joe", "6");
        inputIsFound(inputId, a);

        service().cost(TENANT, farmId, user, false, items(sprayItem("80")));

        verifyNoInteractions(hrFacade);
        verify(costEntryService).record(eq(TENANT), eq(farmId), eq(user), eq(SPRAY_DAY), eq("LABOUR"), any(), eq("HR_LABOUR"), eq(inputId), eq(bd("6")), eq("h"),
                eq(bd("81.6000")), eq(bd("489.60")), any(), any());
    }

    @Test
    @DisplayName("harvest labour is costed the same way, on the harvest date")
    void costsAHarvest() {
        farmExists(); settings("45", "0"); cycleOnFarm(farmId);
        AgHarvestRecord h = harvest(harvestId, null, "Joe", "8");
        harvestIsFound(harvestId, h);

        service().cost(TENANT, farmId, user, true, items(new LabourItem("HARVEST", harvestId, bd("50"))));

        verify(costEntryService).record(eq(TENANT), eq(farmId), eq(user), eq(HARVEST_DAY), eq("LABOUR"), eq("Labour: harvest"), eq("HR_LABOUR"), eq(harvestId), eq(bd("8")), eq("h"),
                eq(bd("50.0000")), eq(bd("400.00")), any(), any());
    }

    @Test
    @DisplayName("no HR link and no typed rate is refused, and the message says what to do")
    void refusesWhenThereIsNoRate() {
        farmExists(); settings("45", "0"); cycleOnFarm(farmId);
        AgInputApplication a = input(inputId, null, "Casual Joe", "6");
        inputIsFound(inputId, a);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service().cost(TENANT, farmId, user, true, items(sprayItem(null))));

        assertTrue(ex.getMessage().contains("Enter an hourly rate"), ex.getMessage());
        verifyNothingRecorded();
    }

    @Test
    @DisplayName("an HR rate cannot be used by someone without HR access")
    void refusesHrRateWithoutHrAccess() {
        farmExists(); settings("45", "0"); cycleOnFarm(farmId);
        AgInputApplication a = input(inputId, workerId, "Thandi M.", "6");
        inputIsFound(inputId, a);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service().cost(TENANT, farmId, user, false, items(sprayItem(null))));

        assertTrue(ex.getMessage().contains("HR access"), ex.getMessage());
        verifyNoInteractions(hrFacade);
        verifyNothingRecorded();
    }

    @Test
    @DisplayName("work that is already costed is refused, so labour can never be counted twice")
    void refusesWorkAlreadyCosted() {
        farmExists(); settings("45", "0"); cycleOnFarm(farmId);
        AgInputApplication a = input(inputId, null, "Joe", "6");
        inputIsFound(inputId, a);
        when(costEntryRepository.countActiveBySource(eq(TENANT), eq("HR_LABOUR"), eq(inputId))).thenReturn(1L);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service().cost(TENANT, farmId, user, true, items(sprayItem("80"))));

        assertTrue(ex.getMessage().contains("already costed"), ex.getMessage());
        verifyNothingRecorded();
    }

    @Test
    @DisplayName("it is all or nothing: if the second item cannot be costed, the first is not recorded either")
    void allOrNothing() {
        farmExists(); settings("45", "0"); cycleOnFarm(farmId);
        AgInputApplication a = input(inputId, null, "Joe", "6");
        inputIsFound(inputId, a);
        AgHarvestRecord h = harvest(harvestId, null, "Joe", "8");
        harvestIsFound(harvestId, h);

        assertThrows(IllegalArgumentException.class, () -> service().cost(TENANT, farmId, user, true,
                items(sprayItem("80"), new LabourItem("HARVEST", harvestId, null))));            // the harvest has no worker and no typed rate

        verifyNothingRecorded();
    }

    @Test
    @DisplayName("bad requests are refused before anything is recorded")
    void refusesBadRequests() {
        farmExists(); settings("45", "0"); cycleOnFarm(farmId);
        AgInputApplication a = input(inputId, null, "Joe", "6");
        inputIsFound(inputId, a);

        assertThrows(IllegalArgumentException.class, () -> service().cost(TENANT, farmId, user, true, new CostLabourRequest(List.of())));
        assertThrows(IllegalArgumentException.class, () -> service().cost(TENANT, farmId, user, true, items(sprayItem("80"), sprayItem("90"))));          // the same work twice
        assertThrows(IllegalArgumentException.class, () -> service().cost(TENANT, farmId, user, true, items(new LabourItem("TRACTOR", inputId, bd("80")))));
        assertThrows(ResourceNotFoundException.class, () -> service().cost(TENANT, farmId, user, true, items(new LabourItem("HARVEST", UUID.randomUUID(), bd("80")))));
        assertThrows(ResourceNotFoundException.class, () -> service().cost(TENANT, farmId, user, true, items(new LabourItem("INPUT_APPLICATION", UUID.randomUUID(), bd("80")))));
        verifyNothingRecorded();
    }

    @Test
    @DisplayName("work on another farm's crop cycle is refused")
    void refusesWorkOnAnotherFarm() {
        farmExists(); settings("45", "0"); cycleOnFarm(otherFarm);
        AgInputApplication a = input(inputId, null, "Joe", "6");
        inputIsFound(inputId, a);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service().cost(TENANT, farmId, user, true, items(sprayItem("80"))));

        assertTrue(ex.getMessage().contains("does not belong to this farm"), ex.getMessage());
        verifyNothingRecorded();
    }

    @Test
    @DisplayName("work with no hours, or a cost that rounds to nothing, is refused")
    void refusesNothingToCost() {
        farmExists(); settings("45", "0"); cycleOnFarm(farmId);
        AgInputApplication noHours = input(inputId, null, "Joe", null);
        inputIsFound(inputId, noHours);
        assertThrows(IllegalArgumentException.class, () -> service().cost(TENANT, farmId, user, true, items(sprayItem("80"))));

        AgInputApplication tiny = input(inputId, null, "Joe", "0.001");
        inputIsFound(inputId, tiny);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service().cost(TENANT, farmId, user, true, items(sprayItem("0.01"))));
        assertTrue(ex.getMessage().contains("rounds to nothing"), ex.getMessage());
        verifyNothingRecorded();
    }
}
