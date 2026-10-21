package za.co.handyflow.platform.catalogue.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.catalogue.application.internal.CatalogueService.Caller;
import za.co.handyflow.platform.catalogue.domain.model.*;
import za.co.handyflow.platform.catalogue.domain.model.StructureRules.AttrSpec;
import za.co.handyflow.platform.catalogue.domain.repository.*;
import za.co.handyflow.platform.catalogue.dto.*;
import za.co.handyflow.platform.shared.ConflictException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Units of measure and attribute definitions/values. The category tree itself lives in {@link CatalogueService};
 * this class only reads it to decide which attributes apply to an item.
 * Rules that need no database are in {@link StructureRules}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CatalogueStructureService {

    /** Offered to every tenant that has no units yet. Code, name, kind, decimals. */
    static final List<Object[]> STANDARD_UNITS = List.of(
            new Object[]{"each", "Each", UnitKind.COUNT, 0}, new Object[]{"box", "Box", UnitKind.COUNT, 0},
            new Object[]{"pack", "Pack", UnitKind.COUNT, 0}, new Object[]{"set", "Set", UnitKind.COUNT, 0},
            new Object[]{"hour", "Hour", UnitKind.TIME, 2}, new Object[]{"day", "Day", UnitKind.TIME, 2},
            new Object[]{"m", "Metre", UnitKind.LENGTH, 2}, new Object[]{"m²", "Square metre", UnitKind.AREA, 2},
            new Object[]{"m³", "Cubic metre", UnitKind.VOLUME, 2}, new Object[]{"kg", "Kilogram", UnitKind.WEIGHT, 3},
            new Object[]{"t", "Tonne", UnitKind.WEIGHT, 3}, new Object[]{"l", "Litre", UnitKind.VOLUME, 2});

    private final CatalogueUnitRepository unitRepository;
    private final CatalogueAttributeDefinitionRepository definitionRepository;
    private final CatalogueAttributeValueRepository valueRepository;
    private final CatalogueCategoryRepository categoryRepository;
    private final CatalogueAuditEventRepository auditRepository;

    // ── Units ─────────────────────────────────────────────────────────────────

    /** Lists the tenant's units; a tenant with none yet gets the standard set first. */
    @Transactional
    public List<CatalogueUnitResponse> listUnits(TenantId tenantId) {
        if (unitRepository.countLive(tenantId) == 0) {
            for (Object[] u : STANDARD_UNITS)
                unitRepository.save(CatalogueUnit.create(tenantId, (String) u[0], (String) u[1], (UnitKind) u[2], (Integer) u[3]));
            log.info("Seeded standard catalogue units tenant={}", tenantId);
        }
        return unitRepository.findAllLive(tenantId).stream()
                .map(u -> toResponse(u, unitRepository.countItemsUsing(tenantId, u.getCode()))).toList();
    }

    @Transactional
    public CatalogueUnitResponse createUnit(TenantId tenantId, CatalogueUnitRequest r, Caller caller) {
        String code = StructureRules.unitCode(r.code());
        if (unitRepository.findLiveByCode(tenantId, code).isPresent()) throw new ConflictException("A unit with the code '" + code + "' already exists.");
        var unit = CatalogueUnit.create(tenantId, code, StructureRules.unitName(r.name(), code), r.kind(), StructureRules.unitDecimals(r.decimals()));
        unitRepository.save(unit);
        audit(tenantId, "UNIT", unit.getId(), code, CatalogueAuditAction.CREATED, "Created (" + unit.getName() + ")", caller);
        return toResponse(unit, 0L);
    }

    @Transactional
    public CatalogueUnitResponse updateUnit(TenantId tenantId, UUID id, CatalogueUnitRequest r, Caller caller) {
        var unit = unitRepository.findLiveById(tenantId, id).orElseThrow(() -> new ResourceNotFoundException("Unit", id.toString()));
        String name = StructureRules.unitName(r.name(), unit.getCode());
        int decimals = StructureRules.unitDecimals(r.decimals());
        boolean active = r.active() == null || r.active();
        var kind = r.kind() == null ? unit.getKind() : r.kind();
        List<String> changes = new ArrayList<>();
        if (!unit.getName().equals(name)) changes.add("name: " + unit.getName() + " -> " + name);
        if (unit.getKind() != kind) changes.add("kind: " + unit.getKind() + " -> " + kind);
        if (unit.getDecimals() != decimals) changes.add("decimals: " + unit.getDecimals() + " -> " + decimals);
        if (unit.isActive() != active) changes.add(active ? "switched on" : "switched off");
        unit.update(name, kind, decimals, active);
        if (!changes.isEmpty()) {
            unitRepository.save(unit);
            audit(tenantId, "UNIT", unit.getId(), unit.getCode(), CatalogueAuditAction.UPDATED, String.join("; ", changes), caller);
        }
        return toResponse(unit, unitRepository.countItemsUsing(tenantId, unit.getCode()));
    }

    /** A unit that items use is switched off instead of deleted, so their quantities keep their meaning. */
    @Transactional
    public void deleteUnit(TenantId tenantId, UUID id, Caller caller) {
        var unit = unitRepository.findLiveById(tenantId, id).orElseThrow(() -> new ResourceNotFoundException("Unit", id.toString()));
        long n = unitRepository.countItemsUsing(tenantId, unit.getCode());
        if (n > 0) throw new ConflictException("This unit is used by " + n + (n == 1 ? " item" : " items") + ". Switch it off instead of deleting it.");
        unit.softDelete(caller.userId());
        unitRepository.save(unit);
        audit(tenantId, "UNIT", unit.getId(), unit.getCode(), CatalogueAuditAction.DELETED, "Deleted", caller);
    }

    /**
     * The tenant's own spelling of a unit code for an item. {@code current} is the code the item already has:
     * keeping it is always allowed (even if the unit was switched off since), choosing a different one is not.
     */
    @Transactional(readOnly = true)
    public String resolveUnit(TenantId tenantId, String requested, String current) {
        String code = StructureRules.unitCode(requested);
        var unit = unitRepository.findLiveByCode(tenantId, code);
        if (unit.isEmpty()) {
            if (current != null && current.equalsIgnoreCase(code)) return current;
            throw new IllegalArgumentException("The unit '" + code + "' is not set up. Choose a unit from the list.");
        }
        if (!unit.get().isActive() && !(current != null && current.equalsIgnoreCase(code)))
            throw new IllegalArgumentException("The unit '" + unit.get().getCode() + "' is switched off. Choose another unit.");
        return unit.get().getCode();
    }

    // ── Attribute definitions ─────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<CatalogueAttributeResponse> listDefinitions(TenantId tenantId) {
        var cats = categoryMaps(tenantId);
        return definitionRepository.findAllLive(tenantId).stream()
                .map(d -> toResponse(d, cats, valueRepository.countForDefinition(tenantId, d.getId()))).toList();
    }

    @Transactional
    public CatalogueAttributeResponse createDefinition(TenantId tenantId, CatalogueAttributeRequest r, Caller caller) {
        String code = StructureRules.attributeCode(r.code());
        if (definitionRepository.codeTaken(tenantId, code)) throw new ConflictException("An attribute with the code '" + code + "' already exists.");
        if (r.categoryId() != null && categoryRepository.findActiveById(tenantId, r.categoryId()).isEmpty())
            throw new ResourceNotFoundException("Category", r.categoryId().toString());
        var settings = settings(r, r.type());
        var d = CatalogueAttributeDefinition.create(tenantId, r.categoryId(), code, r.type(), settings);
        definitionRepository.save(d);
        audit(tenantId, "ATTRIBUTE", d.getId(), d.getLabel(), CatalogueAuditAction.CREATED,
                "Created (" + d.getType().name().toLowerCase(Locale.ROOT) + (d.isRequired() ? ", required" : "") + ")", caller);
        return toResponse(d, categoryMaps(tenantId), 0L);
    }

    @Transactional
    public CatalogueAttributeResponse updateDefinition(TenantId tenantId, UUID id, CatalogueAttributeRequest r, Caller caller) {
        var d = definitionRepository.findLiveById(tenantId, id).orElseThrow(() -> new ResourceNotFoundException("Attribute", id.toString()));
        var settings = settings(r, d.getType());
        List<String> changes = new ArrayList<>();
        if (!d.getLabel().equals(settings.label())) changes.add("name: " + d.getLabel() + " -> " + settings.label());
        if (d.isRequired() != settings.required()) changes.add(settings.required() ? "now required" : "no longer required");
        if (d.isActive() != settings.active()) changes.add(settings.active() ? "switched on" : "switched off");
        if (!d.optionList().equals(settings.options())) changes.add("options changed");
        d.update(settings);
        if (!changes.isEmpty()) {
            definitionRepository.save(d);
            audit(tenantId, "ATTRIBUTE", d.getId(), d.getLabel(), CatalogueAuditAction.UPDATED, String.join("; ", changes), caller);
        }
        return toResponse(d, categoryMaps(tenantId), valueRepository.countForDefinition(tenantId, d.getId()));
    }

    @Transactional
    public void deleteDefinition(TenantId tenantId, UUID id, Caller caller) {
        var d = definitionRepository.findLiveById(tenantId, id).orElseThrow(() -> new ResourceNotFoundException("Attribute", id.toString()));
        long n = valueRepository.countForDefinition(tenantId, id);
        if (n > 0) throw new ConflictException("This attribute has a value on " + n + (n == 1 ? " item" : " items") + ". Switch it off instead of deleting it.");
        d.softDelete(caller.userId());
        definitionRepository.save(d);
        audit(tenantId, "ATTRIBUTE", d.getId(), d.getLabel(), CatalogueAuditAction.DELETED, "Deleted", caller);
    }

    /** How many attribute definitions are tied to this category (the category cannot be deleted while any are). */
    @Transactional(readOnly = true)
    public long countDefinitionsFor(TenantId tenantId, UUID categoryId) {
        return definitionRepository.countForCategory(tenantId, categoryId);
    }

    // ── Attribute values on items ─────────────────────────────────────────────

    /** The active attributes that apply to an item in this category: its own, its ancestors' and the global ones. */
    @Transactional(readOnly = true)
    public List<CatalogueAttributeDefinition> applicable(TenantId tenantId, UUID categoryId) {
        Map<UUID, UUID> parents = categoryMaps(tenantId).parent();
        Set<UUID> chain = categoryId == null ? Set.of() : new HashSet<>(StructureRules.selfAndAncestors(categoryId, parents::get));
        return definitionRepository.findAllLive(tenantId).stream()
                .filter(CatalogueAttributeDefinition::isActive)
                .filter(d -> d.getCategoryId() == null || chain.contains(d.getCategoryId()))
                .toList();
    }

    /**
     * Cleans the values sent with an item and checks required ones. Returns null when the request carries no
     * attributes and none are being enforced (leave stored values alone). A required attribute is only enforced
     * for items that are not drafts.
     */
    @Transactional(readOnly = true)
    public Map<UUID, String> check(TenantId tenantId, UUID categoryId, Map<UUID, String> supplied, boolean enforceRequired, Map<UUID, String> stored) {
        if (supplied == null && !enforceRequired) return null;
        List<AttrSpec> specs = applicable(tenantId, categoryId).stream().map(CatalogueAttributeDefinition::spec).toList();
        Map<UUID, String> clean = supplied == null ? new LinkedHashMap<>(stored == null ? Map.of() : stored) : StructureRules.checkValues(specs, supplied);
        if (enforceRequired) StructureRules.checkRequired(specs, clean);
        return supplied == null ? null : clean;
    }

    /** Labels of the required attributes this item has no value for. */
    @Transactional(readOnly = true)
    public List<String> missingRequired(TenantId tenantId, UUID categoryId, UUID itemId) {
        List<AttrSpec> specs = applicable(tenantId, categoryId).stream().map(CatalogueAttributeDefinition::spec).toList();
        return StructureRules.missingRequired(specs, stored(tenantId, itemId));
    }

    /** The values currently stored for an item, keyed by definition id. */
    @Transactional(readOnly = true)
    public Map<UUID, String> stored(TenantId tenantId, UUID itemId) {
        return valueRepository.findForItem(tenantId, itemId).stream()
                .collect(Collectors.toMap(CatalogueAttributeValue::getDefinitionId, CatalogueAttributeValue::getValue, (a, b) -> a, LinkedHashMap::new));
    }

    /** Makes the stored values equal to {@code values}; returns a short description of what changed ("" if nothing). */
    @Transactional
    public String store(TenantId tenantId, UUID itemId, Map<UUID, String> values) {
        if (values == null) return "";
        Map<UUID, CatalogueAttributeDefinition> defs = definitionRepository.findAllLive(tenantId).stream()
                .collect(Collectors.toMap(CatalogueAttributeDefinition::getId, d -> d, (a, b) -> a));
        List<String> changes = new ArrayList<>();
        Set<UUID> kept = new HashSet<>();
        for (var existing : valueRepository.findForItem(tenantId, itemId)) {
            String label = labelOf(defs, existing.getDefinitionId());
            String next = values.get(existing.getDefinitionId());
            if (next == null) {
                valueRepository.delete(existing);
                changes.add(label + ": " + existing.getValue() + " -> (empty)");
            } else {
                kept.add(existing.getDefinitionId());
                if (!next.equals(existing.getValue())) {
                    changes.add(label + ": " + existing.getValue() + " -> " + next);
                    existing.setValue(next);
                    valueRepository.save(existing);
                }
            }
        }
        for (var e : values.entrySet()) {
            if (kept.contains(e.getKey())) continue;
            valueRepository.save(CatalogueAttributeValue.of(tenantId, itemId, e.getKey(), e.getValue()));
            changes.add(labelOf(defs, e.getKey()) + ": " + e.getValue());
        }
        return String.join("; ", changes);
    }

    /** The item's values with their definitions, ready for display (only attributes that still exist). */
    @Transactional(readOnly = true)
    public List<CatalogueAttributeValueResponse> valuesFor(TenantId tenantId, UUID itemId) {
        Map<UUID, CatalogueAttributeDefinition> defs = definitionRepository.findAllLive(tenantId).stream()
                .collect(Collectors.toMap(CatalogueAttributeDefinition::getId, d -> d, (a, b) -> a));
        return valueRepository.findForItem(tenantId, itemId).stream()
                .filter(v -> defs.containsKey(v.getDefinitionId()))
                .sorted(Comparator.comparingInt((CatalogueAttributeValue v) -> defs.get(v.getDefinitionId()).getSortOrder())
                        .thenComparing(v -> defs.get(v.getDefinitionId()).getLabel()))
                .map(v -> {
                    var d = defs.get(v.getDefinitionId());
                    return new CatalogueAttributeValueResponse(d.getId(), d.getCode(), d.getLabel(), d.getType().name(), v.getValue());
                }).toList();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static CatalogueAttributeDefinition.Settings settings(CatalogueAttributeRequest r, AttributeType type) {
        StructureRules.checkLimits(type, r.min(), r.max(), r.maxLength());
        return new CatalogueAttributeDefinition.Settings(StructureRules.attributeLabel(r.label()), Boolean.TRUE.equals(r.required()),
                StructureRules.attributeOptions(type, r.options()), r.min(), r.max(), r.maxLength(),
                r.sortOrder() == null ? 0 : r.sortOrder(), r.active() == null || r.active());
    }

    private record CategoryMaps(Map<UUID, UUID> parent, Map<UUID, String> name) {}

    private CategoryMaps categoryMaps(TenantId tenantId) {
        Map<UUID, UUID> parent = new HashMap<>();
        Map<UUID, String> name = new HashMap<>();
        categoryRepository.findAllActive(tenantId).forEach(c -> { parent.put(c.getId(), c.getParentId()); name.put(c.getId(), c.getName()); });
        return new CategoryMaps(parent, name);
    }

    private static String labelOf(Map<UUID, CatalogueAttributeDefinition> defs, UUID id) {
        var d = defs.get(id);
        return d == null ? "(removed attribute)" : d.getLabel();
    }

    private static CatalogueUnitResponse toResponse(CatalogueUnit u, Long used) {
        return new CatalogueUnitResponse(u.getId(), u.getCode(), u.getName(), u.getKind().name(), u.getDecimals(), u.isActive(), used);
    }

    private static CatalogueAttributeResponse toResponse(CatalogueAttributeDefinition d, CategoryMaps cats, Long values) {
        String path = d.getCategoryId() == null ? null : StructureRules.pathOf(d.getCategoryId(), cats.parent()::get, cats.name()::get);
        return new CatalogueAttributeResponse(d.getId(), d.getCategoryId(), path, d.getCode(), d.getLabel(), d.getType().name(), d.isRequired(),
                d.optionList(), d.getMinValue(), d.getMaxValue(), d.getMaxLength(), d.getSortOrder(), d.isActive(), values);
    }

    private void audit(TenantId tenantId, String type, UUID id, String name, CatalogueAuditAction action, String detail, Caller caller) {
        auditRepository.save(CatalogueAuditEvent.of(tenantId, type, id, name, action, detail, caller.userId()));
    }
}
