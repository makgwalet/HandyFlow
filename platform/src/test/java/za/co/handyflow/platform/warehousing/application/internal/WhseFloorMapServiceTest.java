package za.co.handyflow.platform.warehousing.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.warehousing.domain.model.WhseLocation;
import za.co.handyflow.platform.warehousing.domain.repository.WhseLocationRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WhseFloorMapServiceTest {

    private final UUID tenant = UUID.randomUUID();
    private final UUID actor = UUID.randomUUID();
    private JdbcTemplate jdbc;
    private WhseLocationRepository repo;
    private WhseFloorMapService service;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        repo = mock(WhseLocationRepository.class);
        service = new WhseFloorMapService(jdbc, repo);
        when(repo.save(any(WhseLocation.class))).thenAnswer(i -> i.getArgument(0));
    }

    private TenantId t() { return TenantId.of(tenant); }
    private WhseFloorLayout.Spec spec() { return new WhseFloorLayout.Spec("Z", List.of("A"), 2, 1, "BIN", 0, 0, 2); }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void existing(List<String> codes, List<String> cells) {
        when(jdbc.queryForObject(contains("max(pick_sequence)"), eq(Integer.class), any(Object[].class))).thenReturn(null);
        when(jdbc.queryForList(contains("SELECT code"), eq(String.class), any(Object[].class))).thenReturn(codes);
        when(jdbc.query(contains("SELECT pos_x, pos_y"), any(RowMapper.class), any(Object[].class))).thenReturn(cells);
    }

    @Test @DisplayName("generate creates every location with its cell and picking order")
    void generates() {
        existing(List.of(), List.of());
        assertEquals(2, service.generate(t(), spec(), null, actor));
        ArgumentCaptor<WhseLocation> c = ArgumentCaptor.forClass(WhseLocation.class);
        verify(repo, times(2)).save(c.capture());
        WhseLocation first = c.getAllValues().get(0);
        assertEquals("A-01-01", first.getCode());
        assertEquals(0, first.getPosX());
        assertEquals(1, first.getPickSequence());
        assertEquals("BIN", first.getLocationType());
        verify(jdbc).update(contains("WHSE_LOCATIONS_GENERATED"), any(Object[].class));
    }

    @Test @DisplayName("picking order continues after the highest existing one")
    void continuesSequence() {
        existing(List.of(), List.of());
        when(jdbc.queryForObject(contains("max(pick_sequence)"), eq(Integer.class), any(Object[].class))).thenReturn(40);
        service.generate(t(), spec(), null, actor);
        ArgumentCaptor<WhseLocation> c = ArgumentCaptor.forClass(WhseLocation.class);
        verify(repo, times(2)).save(c.capture());
        assertEquals(41, c.getAllValues().get(0).getPickSequence());
    }

    @Test @DisplayName("a clashing code creates nothing")
    void codeClash() {
        existing(List.of("A-02-01"), List.of());
        HandyFlowException e = assertThrows(HandyFlowException.class, () -> service.generate(t(), spec(), null, actor));
        assertEquals("FLOOR_LAYOUT_CONFLICT", e.getErrorCode());
        verify(repo, never()).save(any());
    }

    @Test @DisplayName("an occupied cell creates nothing")
    void cellClash() {
        existing(List.of(), List.of("1:0:1"));
        assertThrows(HandyFlowException.class, () -> service.generate(t(), spec(), null, actor));
        verify(repo, never()).save(any());
    }

    @Test @DisplayName("preview flags conflicts without saving")
    void previewFlags() {
        existing(List.of("A-01-01"), List.of());
        List<WhseFloorMapService.PreviewSlot> p = service.preview(t(), spec());
        assertNotNull(p.get(0).conflict());
        assertNull(p.get(1).conflict());
        verify(repo, never()).save(any());
    }

    @Test @DisplayName("moving a location to an occupied cell is refused")
    void moveClash() {
        UUID id = UUID.randomUUID();
        when(repo.findActiveById(tenant, id)).thenReturn(Optional.of(WhseLocation.create(tenant, "X", null, null, null)));
        when(jdbc.queryForObject(contains("SELECT count(*)"), eq(Integer.class), any(Object[].class))).thenReturn(1);
        HandyFlowException e = assertThrows(HandyFlowException.class, () -> service.setLayout(t(), id, "A", 1, 1, "BIN", 1, 3, 4));
        assertEquals("CELL_OCCUPIED", e.getErrorCode());
    }

    @Test @DisplayName("a free cell is accepted, and no position takes the location off the map")
    void moveAndRemove() {
        UUID id = UUID.randomUUID();
        WhseLocation l = WhseLocation.create(tenant, "X", null, null, null);
        when(repo.findActiveById(tenant, id)).thenReturn(Optional.of(l));
        when(jdbc.queryForObject(contains("SELECT count(*)"), eq(Integer.class), any(Object[].class))).thenReturn(0);
        service.setLayout(t(), id, "a", 1, 1, "SHELF", 5, 3, 4);
        assertEquals(3, l.getPosX());
        assertEquals("A", l.getAisle());
        service.setLayout(t(), id, "A", 1, 1, "SHELF", 5, null, null);
        assertNull(l.getPosX());
        assertNull(l.getPosY());
    }

    @Test @DisplayName("half a position is refused, and an unknown location is not found")
    void validation() {
        UUID id = UUID.randomUUID();
        when(repo.findActiveById(tenant, id)).thenReturn(Optional.of(WhseLocation.create(tenant, "X", null, null, null)));
        assertThrows(IllegalArgumentException.class, () -> service.setLayout(t(), id, null, null, null, "BIN", null, 3, null));
        when(repo.findActiveById(tenant, id)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.setLayout(t(), id, null, null, null, "BIN", null, null, null));
    }

    @Test @DisplayName("utilisation is stock over capacity, empty when there is no capacity")
    void utilisation() {
        assertEquals(new BigDecimal("62.5"), WhseFloorMapService.utilisation(new BigDecimal("25"), new BigDecimal("40")));
        assertNull(WhseFloorMapService.utilisation(new BigDecimal("25"), null));
        assertNull(WhseFloorMapService.utilisation(new BigDecimal("25"), BigDecimal.ZERO));
    }

    // ── editing after generation ────────────────────────────────────────────

    private WhseLocation at(String aisle, int bay, int level, int x, int y) {
        WhseLocation l = WhseLocation.create(tenant, WhseFloorLayout.code(aisle, bay, level), "Z", null, new BigDecimal("10"));
        l.applyLayout(aisle, bay, level, "BIN", bay, x, y);
        return l;
    }

    private void onMap(WhseLocation... ls) {
        when(repo.findAllActive(tenant)).thenReturn(List.of(ls));
    }

    @Test @DisplayName("dragging a bay moves all its levels and keeps their codes")
    void moveCellMovesEveryLevel() {
        WhseLocation l1 = at("A", 1, 1, 0, 0), l2 = at("A", 1, 2, 0, 0), other = at("A", 2, 1, 1, 0);
        onMap(l1, l2, other);
        assertEquals(2, service.moveCell(t(), 0, 0, 0, 3, actor));
        assertEquals(3, l1.getPosY());
        assertEquals(3, l2.getPosY());
        assertEquals("A-01-01", l1.getCode());
        assertEquals(0, other.getPosY());
        verify(jdbc).update(contains("audit_events"), eq(tenant), eq(actor), eq("WHSE_FLOOR_CELL_MOVED"), anyString());
    }

    @Test @DisplayName("a bay cannot be dropped on an occupied cell, and nothing moves")
    void moveCellOccupied() {
        WhseLocation l1 = at("A", 1, 1, 0, 0), blocker = at("A", 2, 1, 1, 0);
        onMap(l1, blocker);
        HandyFlowException e = assertThrows(HandyFlowException.class, () -> service.moveCell(t(), 0, 0, 1, 0, actor));
        assertEquals("CELL_OCCUPIED", e.getErrorCode());
        assertEquals(0, l1.getPosX());
        verify(repo, never()).save(any());
    }

    @Test @DisplayName("moving needs a real source, a different target and non-negative cells")
    void moveCellValidation() {
        onMap(at("A", 1, 1, 0, 0));
        assertThrows(ResourceNotFoundException.class, () -> service.moveCell(t(), 5, 5, 6, 6, actor));
        assertThrows(IllegalArgumentException.class, () -> service.moveCell(t(), 0, 0, 0, 0, actor));
        assertThrows(IllegalArgumentException.class, () -> service.moveCell(t(), 0, 0, -1, 0, actor));
    }

    @Test @DisplayName("editing a bay changes the type and capacity of every level, and can clear the capacity")
    void editBay() {
        WhseLocation l1 = at("A", 1, 1, 0, 0), l2 = at("A", 1, 2, 0, 0);
        onMap(l1, l2);
        assertEquals(2, service.editBay(t(), 0, 0, "SHELF", new BigDecimal("25"), false, null, actor));
        assertEquals("SHELF", l1.getLocationType());
        assertEquals(new BigDecimal("25"), l2.getCapacityUnits());
        assertEquals("A-01-02", l2.getCode());
        service.editBay(t(), 0, 0, null, null, true, null, actor);
        assertNull(l1.getCapacityUnits());
        assertThrows(IllegalArgumentException.class, () -> service.editBay(t(), 0, 0, null, null, false, null, actor));
        assertThrows(IllegalArgumentException.class, () -> service.editBay(t(), 0, 0, "NOPE", null, false, null, actor));
        assertThrows(IllegalArgumentException.class, () -> service.editBay(t(), 0, 0, null, BigDecimal.ONE, true, null, actor));
    }

    @Test @DisplayName("a bay holding stock cannot be switched off; an empty one can, and comes back")
    void editBayActive() {
        WhseLocation l1 = at("A", 1, 1, 0, 0);
        onMap(l1);
        when(jdbc.queryForObject(contains("whse_inventory"), eq(BigDecimal.class), any(Object[].class))).thenReturn(new BigDecimal("4"));
        HandyFlowException e = assertThrows(HandyFlowException.class, () -> service.editBay(t(), 0, 0, null, null, false, false, actor));
        assertEquals("LOCATION_HAS_STOCK", e.getErrorCode());
        assertTrue(l1.isActive());
        when(jdbc.queryForObject(contains("whse_inventory"), eq(BigDecimal.class), any(Object[].class))).thenReturn(BigDecimal.ZERO);
        service.editBay(t(), 0, 0, null, null, false, false, actor);
        assertFalse(l1.isActive());
        service.editBay(t(), 0, 0, null, null, false, true, actor);
        assertTrue(l1.isActive());
    }

    @Test @DisplayName("adding levels continues the numbering on the same cell with the bay's own type and zone")
    void addLevels() {
        WhseLocation l1 = at("A", 1, 1, 2, 3), l2 = at("A", 1, 2, 2, 3);
        onMap(l1, l2);
        existing(List.of("A-01-01", "A-01-02"), List.of("2:3:1", "2:3:2"));
        assertEquals(2, service.addLevels(t(), 2, 3, 2, null, actor));
        ArgumentCaptor<WhseLocation> c = ArgumentCaptor.forClass(WhseLocation.class);
        verify(repo, times(2)).save(c.capture());
        assertEquals("A-01-03", c.getAllValues().get(0).getCode());
        assertEquals("A-01-04", c.getAllValues().get(1).getCode());
        assertEquals(2, c.getAllValues().get(0).getPosX());
        assertEquals(3, c.getAllValues().get(0).getPosY());
        assertEquals("Z", c.getAllValues().get(0).getZone());
        assertEquals(new BigDecimal("10"), c.getAllValues().get(0).getCapacityUnits());
    }

    @Test @DisplayName("a bay cannot grow past ten levels, and a code that is already used stops the whole add")
    void addLevelsLimits() {
        onMap(at("A", 1, 9, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> service.addLevels(t(), 0, 0, 2, null, actor));
        onMap(at("A", 1, 1, 0, 0));
        existing(List.of("A-01-02"), List.of());
        assertThrows(HandyFlowException.class, () -> service.addLevels(t(), 0, 0, 1, null, actor));
        verify(repo, never()).save(any());
    }

    @Test @DisplayName("extending an aisle adds bays after the last one, with the same levels, on the same row, last in picking order")
    void extendAisle() {
        onMap(at("A", 1, 1, 0, 4), at("A", 1, 2, 0, 4), at("A", 2, 1, 1, 4), at("A", 2, 2, 1, 4), at("B", 1, 1, 0, 6));
        existing(List.of(), List.of());
        when(jdbc.queryForObject(contains("max(pick_sequence)"), eq(Integer.class), any(Object[].class))).thenReturn(10);
        assertEquals(4, service.extendAisle(t(), " a ", 2, null, actor));
        ArgumentCaptor<WhseLocation> c = ArgumentCaptor.forClass(WhseLocation.class);
        verify(repo, times(4)).save(c.capture());
        List<WhseLocation> made = c.getAllValues();
        assertEquals("A-03-01", made.get(0).getCode());
        assertEquals("A-03-02", made.get(1).getCode());
        assertEquals("A-04-02", made.get(3).getCode());
        assertEquals(2, made.get(0).getPosX());
        assertEquals(3, made.get(2).getPosX());
        assertEquals(4, made.get(0).getPosY());
        assertEquals(11, made.get(0).getPickSequence());
    }

    @Test @DisplayName("an unknown aisle is not found, and a clash with an occupied cell creates nothing")
    void extendAisleErrors() {
        onMap(at("A", 1, 1, 0, 0));
        assertThrows(ResourceNotFoundException.class, () -> service.extendAisle(t(), "Q", 1, null, actor));
        existing(List.of(), List.of("1:0:1"));
        assertThrows(HandyFlowException.class, () -> service.extendAisle(t(), "A", 1, null, actor));
        verify(repo, never()).save(any());
    }

    // ── warehouses ───────────────────────────────────────────────────────────

    private final UUID cpt = UUID.randomUUID(), jhb = UUID.randomUUID();

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void warehouseIsActive() {
        when(jdbc.query(contains("SELECT active FROM whse_warehouses"), any(RowMapper.class), any(Object[].class))).thenReturn(List.of(true));
    }

    @Test @DisplayName("generating into a warehouse puts every new location in it")
    void generatesIntoWarehouse() {
        existing(List.of(), List.of());
        warehouseIsActive();
        assertEquals(2, service.generate(t(), spec(), null, actor, jhb));
        ArgumentCaptor<WhseLocation> c = ArgumentCaptor.forClass(WhseLocation.class);
        verify(repo, times(2)).save(c.capture());
        assertEquals(jhb, c.getAllValues().get(0).getWarehouseId());
        assertEquals(jhb, c.getAllValues().get(1).getWarehouseId());
    }

    @Test @DisplayName("a warehouse that is not this tenant's, or not found, is refused and nothing is created")
    @SuppressWarnings({"unchecked", "rawtypes"})
    void unknownWarehouse() {
        existing(List.of(), List.of());
        when(jdbc.query(contains("SELECT active FROM whse_warehouses"), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());
        assertThrows(ResourceNotFoundException.class, () -> service.generate(t(), spec(), null, actor, jhb));
        verify(repo, never()).save(any());
    }

    @Test @DisplayName("a warehouse that is switched off is refused")
    @SuppressWarnings({"unchecked", "rawtypes"})
    void inactiveWarehouse() {
        existing(List.of(), List.of());
        when(jdbc.query(contains("SELECT active FROM whse_warehouses"), any(RowMapper.class), any(Object[].class))).thenReturn(List.of(false));
        assertThrows(HandyFlowException.class, () -> service.generate(t(), spec(), null, actor, jhb));
        verify(repo, never()).save(any());
    }

    @Test @DisplayName("two warehouses can have a bay in the same cell: moving one warehouse's bay leaves the other's alone")
    void cellsArePerWarehouse() {
        WhseLocation inCpt = at("A", 1, 1, 0, 0), inJhb = at("A", 1, 1, 0, 0);
        inCpt.assignWarehouse(cpt);
        inJhb.assignWarehouse(jhb);
        onMap(inCpt, inJhb);
        warehouseIsActive();
        assertEquals(1, service.moveCell(t(), 0, 0, 0, 3, actor, jhb));
        assertEquals(3, inJhb.getPosY());
        assertEquals(0, inCpt.getPosY());
    }

    @Test @DisplayName("a cell that holds nothing in this warehouse is not found, even if another warehouse has a bay there")
    void emptyInThisWarehouse() {
        WhseLocation inCpt = at("A", 1, 1, 0, 0);
        inCpt.assignWarehouse(cpt);
        onMap(inCpt);
        warehouseIsActive();
        assertThrows(ResourceNotFoundException.class, () -> service.moveCell(t(), 0, 0, 0, 3, actor, jhb));
    }

    @Test @DisplayName("a location cannot change warehouse once it has one")
    void warehouseIsFixed() {
        WhseLocation l = at("A", 1, 1, 0, 0);
        l.assignWarehouse(cpt);
        assertDoesNotThrow(() -> l.assignWarehouse(cpt));
        assertThrows(IllegalStateException.class, () -> l.assignWarehouse(jhb));
    }
}
