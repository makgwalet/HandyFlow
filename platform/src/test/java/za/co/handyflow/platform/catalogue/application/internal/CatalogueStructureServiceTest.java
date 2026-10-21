package za.co.handyflow.platform.catalogue.application.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import za.co.handyflow.platform.catalogue.application.internal.CatalogueService.Caller;
import za.co.handyflow.platform.catalogue.domain.model.*;
import za.co.handyflow.platform.catalogue.domain.repository.*;
import za.co.handyflow.platform.catalogue.dto.CatalogueAttributeRequest;
import za.co.handyflow.platform.catalogue.dto.CatalogueUnitRequest;
import za.co.handyflow.platform.shared.ConflictException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CatalogueStructureServiceTest {
    private static final TenantId TENANT = TenantId.of(UUID.randomUUID());
    private static final TenantId OTHER = TenantId.of(UUID.randomUUID());
    private static final Caller ADMIN = new Caller(UUID.randomUUID(), true, true, true);

    @Mock CatalogueUnitRepository units;
    @Mock CatalogueAttributeDefinitionRepository definitions;
    @Mock CatalogueAttributeValueRepository values;
    @Mock CatalogueCategoryRepository categories;
    @Mock CatalogueAuditEventRepository audit;
    CatalogueStructureService service;

    @BeforeEach
    void setUp() {
        service = new CatalogueStructureService(units, definitions, values, categories, audit);
    }

    private static CatalogueUnit unit(String code, boolean active) {
        var u = CatalogueUnit.create(TENANT, code, code, UnitKind.COUNT, 0);
        u.update(code, UnitKind.COUNT, 0, active);
        return u;
    }

    private static CatalogueAttributeDefinition def(UUID categoryId, String code, AttributeType type, boolean required, List<String> options) {
        return CatalogueAttributeDefinition.create(TENANT, categoryId, code, type,
                new CatalogueAttributeDefinition.Settings(code.toUpperCase(), required, options, null, null, null, 0, true));
    }

    // ── units ─────────────────────────────────────────────────────────────────

    @Test
    void aTenantWithNoUnitsGetsTheStandardSet() {
        when(units.countLive(TENANT)).thenReturn(0L);
        when(units.findAllLive(TENANT)).thenReturn(List.of());
        service.listUnits(TENANT);
        verify(units, times(CatalogueStructureService.STANDARD_UNITS.size())).save(any());
    }

    @Test
    void aTenantWithUnitsIsNotSeededAgain() {
        when(units.countLive(TENANT)).thenReturn(3L);
        when(units.findAllLive(TENANT)).thenReturn(List.of());
        service.listUnits(TENANT);
        verify(units, never()).save(any());
    }

    @Test
    void anItemUnitIsResolvedToTheTenantsSpelling() {
        when(units.findLiveByCode(TENANT, "EACH")).thenReturn(Optional.of(unit("each", true)));
        assertThat(service.resolveUnit(TENANT, " EACH ", null)).isEqualTo("each");
    }

    @Test
    void anUnknownOrSwitchedOffUnitCannotBeChosen() {
        when(units.findLiveByCode(TENANT, "bag")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.resolveUnit(TENANT, "bag", null)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("The unit 'bag' is not set up. Choose a unit from the list.");
        when(units.findLiveByCode(TENANT, "box")).thenReturn(Optional.of(unit("box", false)));
        assertThatThrownBy(() -> service.resolveUnit(TENANT, "box", null)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("The unit 'box' is switched off. Choose another unit.");
    }

    @Test
    void anItemMayKeepAUnitThatWasSwitchedOffOrRemovedSinceItWasChosen() {
        when(units.findLiveByCode(TENANT, "box")).thenReturn(Optional.of(unit("box", false)));
        assertThat(service.resolveUnit(TENANT, "box", "box")).isEqualTo("box");
        when(units.findLiveByCode(TENANT, "bag")).thenReturn(Optional.empty());
        assertThat(service.resolveUnit(TENANT, "bag", "bag")).isEqualTo("bag");
    }

    @Test
    void aDuplicateUnitCodeIsAConflictAndANewOneIsRecorded() {
        when(units.findLiveByCode(TENANT, "kg")).thenReturn(Optional.of(unit("kg", true)));
        assertThatThrownBy(() -> service.createUnit(TENANT, new CatalogueUnitRequest("kg", null, null, null, null), ADMIN))
                .isInstanceOf(ConflictException.class).hasMessage("A unit with the code 'kg' already exists.");
        var out = service.createUnit(TENANT, new CatalogueUnitRequest(" bag ", "Bag", UnitKind.COUNT, 0, null), ADMIN);
        assertThat(out.code()).isEqualTo("bag");
        assertThat(out.decimals()).isEqualTo(0);
        var ev = ArgumentCaptor.forClass(CatalogueAuditEvent.class);
        verify(audit).save(ev.capture());
        assertThat(ev.getValue().getAction()).isEqualTo(CatalogueAuditAction.CREATED);
    }

    @Test
    void aUnitInUseIsSwitchedOffNotDeleted() {
        var u = unit("each", true);
        when(units.findLiveById(TENANT, u.getId())).thenReturn(Optional.of(u));
        when(units.countItemsUsing(TENANT, "each")).thenReturn(4L);
        assertThatThrownBy(() -> service.deleteUnit(TENANT, u.getId(), ADMIN)).isInstanceOf(ConflictException.class)
                .hasMessage("This unit is used by 4 items. Switch it off instead of deleting it.");
        assertThat(u.getDeletedAt()).isNull();
        when(units.countItemsUsing(TENANT, "each")).thenReturn(0L);
        service.deleteUnit(TENANT, u.getId(), ADMIN);
        assertThat(u.getDeletedBy()).isEqualTo(ADMIN.userId());
    }

    @Test
    void anotherTenantsUnitIsNotFound() {
        var u = unit("each", true);
        when(units.findLiveById(TENANT, u.getId())).thenReturn(Optional.of(u));
        when(units.findLiveById(OTHER, u.getId())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.deleteUnit(OTHER, u.getId(), ADMIN)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.updateUnit(OTHER, u.getId(), new CatalogueUnitRequest("each", "x", null, null, null), ADMIN))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── definitions ───────────────────────────────────────────────────────────

    private static CatalogueAttributeRequest attr(UUID cat, String code, AttributeType t, List<String> options) {
        return new CatalogueAttributeRequest(cat, code, code, t, false, options, null, null, null, null, null);
    }

    @Test
    void aDuplicateAttributeCodeIsAConflict() {
        when(definitions.codeTaken(TENANT, "colour")).thenReturn(true);
        assertThatThrownBy(() -> service.createDefinition(TENANT, attr(null, "Colour", AttributeType.TEXT, null), ADMIN))
                .isInstanceOf(ConflictException.class).hasMessage("An attribute with the code 'colour' already exists.");
    }

    @Test
    void anAttributeOnAnUnknownCategoryIsNotFound() {
        UUID cat = UUID.randomUUID();
        when(categories.findActiveById(TENANT, cat)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.createDefinition(TENANT, attr(cat, "colour", AttributeType.TEXT, null), ADMIN))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void aListAttributeNeedsOptions() {
        assertThatThrownBy(() -> service.createDefinition(TENANT, attr(null, "grade", AttributeType.LIST, List.of()), ADMIN))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("A list attribute needs at least one option.");
        verify(definitions, never()).save(any());
    }

    @Test
    void anAttributeWithValuesIsSwitchedOffNotDeleted() {
        var d = def(null, "colour", AttributeType.TEXT, false, List.of());
        when(definitions.findLiveById(TENANT, d.getId())).thenReturn(Optional.of(d));
        when(values.countForDefinition(TENANT, d.getId())).thenReturn(1L);
        assertThatThrownBy(() -> service.deleteDefinition(TENANT, d.getId(), ADMIN)).isInstanceOf(ConflictException.class)
                .hasMessage("This attribute has a value on 1 item. Switch it off instead of deleting it.");
    }

    @Test
    void editingADefinitionKeepsItsCodeAndType() {
        var d = def(null, "grade", AttributeType.LIST, false, List.of("A", "B"));
        when(definitions.findLiveById(TENANT, d.getId())).thenReturn(Optional.of(d));
        var r = new CatalogueAttributeRequest(UUID.randomUUID(), "other", "Grade", AttributeType.TEXT, true, List.of("A", "B", "C"), null, null, null, 2, true);
        var out = service.updateDefinition(TENANT, d.getId(), r, ADMIN);
        assertThat(out.code()).isEqualTo("grade");
        assertThat(out.type()).isEqualTo("LIST");
        assertThat(out.required()).isTrue();
        assertThat(out.options()).isEqualTo(List.of("A", "B", "C"));
        assertThat(out.categoryId()).isNull();
    }

    // ── which attributes apply, and values ────────────────────────────────────

    @Test
    void anItemGetsItsCategoryAncestorAndGlobalAttributesButNotSiblings() {
        var building = CatalogueCategory.create(TENANT, "Building", null);
        var cement = CatalogueCategory.create(TENANT, "Cement", null, building.getId());
        var tools = CatalogueCategory.create(TENANT, "Tools", null);
        when(categories.findAllActive(TENANT)).thenReturn(List.of(building, cement, tools));
        var global = def(null, "note", AttributeType.TEXT, false, List.of());
        var onBuilding = def(building.getId(), "grade", AttributeType.TEXT, false, List.of());
        var onCement = def(cement.getId(), "bag_kg", AttributeType.NUMBER, false, List.of());
        var onTools = def(tools.getId(), "voltage", AttributeType.NUMBER, false, List.of());
        when(definitions.findAllLive(TENANT)).thenReturn(List.of(global, onBuilding, onCement, onTools));
        var codes = service.applicable(TENANT, cement.getId()).stream().map(CatalogueAttributeDefinition::getCode).toList();
        assertThat(codes).isEqualTo(List.of("note", "grade", "bag_kg"));
        assertThat(service.applicable(TENANT, null).size()).isEqualTo(1);
    }

    @Test
    void switchedOffAttributesDoNotApply() {
        var d = def(null, "note", AttributeType.TEXT, true, List.of());
        d.update(new CatalogueAttributeDefinition.Settings("Note", true, List.of(), null, null, null, 0, false));
        when(categories.findAllActive(TENANT)).thenReturn(List.of());
        when(definitions.findAllLive(TENANT)).thenReturn(List.of(d));
        assertThat(service.applicable(TENANT, null).size()).isEqualTo(0);
    }

    @Test
    void valuesAreCheckedAndRequiredOnesEnforcedOnlyWhenAsked() {
        when(categories.findAllActive(TENANT)).thenReturn(List.of());
        var colour = def(null, "colour", AttributeType.TEXT, true, List.of());
        var size = def(null, "size", AttributeType.NUMBER, false, List.of());
        when(definitions.findAllLive(TENANT)).thenReturn(List.of(colour, size));

        Map<UUID, String> sent = new LinkedHashMap<>();
        sent.put(size.getId(), "2,5");
        assertThat(service.check(TENANT, null, sent, false, null).get(size.getId())).isEqualTo("2.5");
        assertThatThrownBy(() -> service.check(TENANT, null, sent, true, null)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Fill in the required attribute: COLOUR.");
        sent.put(colour.getId(), "Red");
        assertThat(service.check(TENANT, null, sent, true, null).size()).isEqualTo(2);
    }

    @Test
    void noAttributesSentLeavesStoredValuesAloneButStillEnforcesRequired() {
        when(categories.findAllActive(TENANT)).thenReturn(List.of());
        var colour = def(null, "colour", AttributeType.TEXT, true, List.of());
        when(definitions.findAllLive(TENANT)).thenReturn(List.of(colour));
        assertThat(service.check(TENANT, null, null, false, null)).isNull();
        assertThat(service.check(TENANT, null, null, true, Map.of(colour.getId(), "Red"))).isNull();
        assertThatThrownBy(() -> service.check(TENANT, null, null, true, Map.of())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void storingReplacesChangesAndClearsValues() {
        var colour = def(null, "colour", AttributeType.TEXT, false, List.of());
        var size = def(null, "size", AttributeType.NUMBER, false, List.of());
        var grade = def(null, "grade", AttributeType.TEXT, false, List.of());
        when(definitions.findAllLive(TENANT)).thenReturn(List.of(colour, size, grade));
        UUID item = UUID.randomUUID();
        var oldColour = CatalogueAttributeValue.of(TENANT, item, colour.getId(), "Red");
        var oldSize = CatalogueAttributeValue.of(TENANT, item, size.getId(), "2");
        when(values.findForItem(TENANT, item)).thenReturn(List.of(oldColour, oldSize));

        Map<UUID, String> next = new LinkedHashMap<>();
        next.put(colour.getId(), "Blue");      // changed
        next.put(grade.getId(), "A");          // added; size is dropped
        String change = service.store(TENANT, item, next);

        assertThat(oldColour.getValue()).isEqualTo("Blue");
        verify(values).delete(oldSize);
        verify(values, times(2)).save(any()); // the changed colour and the new grade
        assertThat(change).isEqualTo("COLOUR: Red -> Blue; SIZE: 2 -> (empty); GRADE: A");
    }

    @Test
    void storingNothingSentChangesNothing() {
        assertThat(service.store(TENANT, UUID.randomUUID(), null)).isEqualTo("");
        verify(values, never()).save(any());
        verify(values, never()).delete(any());
    }
}
