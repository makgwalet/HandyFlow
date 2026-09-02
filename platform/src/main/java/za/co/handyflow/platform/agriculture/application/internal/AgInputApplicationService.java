package za.co.handyflow.platform.agriculture.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.agriculture.domain.model.AgCropCycle;
import za.co.handyflow.platform.agriculture.domain.model.AgInputApplication;
import za.co.handyflow.platform.agriculture.domain.model.AgInventoryItem;
import za.co.handyflow.platform.agriculture.domain.model.AgStockMovement;
import za.co.handyflow.platform.agriculture.domain.repository.AgCropCycleRepository;
import za.co.handyflow.platform.agriculture.domain.repository.AgInputApplicationRepository;
import za.co.handyflow.platform.agriculture.domain.repository.AgInventoryItemRepository;
import za.co.handyflow.platform.agriculture.domain.repository.AgStockMovementRepository;
import za.co.handyflow.platform.agriculture.dto.CreateInputApplicationRequest;
import za.co.handyflow.platform.agriculture.dto.InputApplicationResponse;
import za.co.handyflow.platform.hr.application.HrFacade;
import za.co.handyflow.platform.hr.dto.EmployeeResponse;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Optional;
import java.util.UUID;

/**
 * Recording an input application optionally issues a matching
 * {@link AgStockMovement} (ISSUE) against the referenced
 * {@link AgInventoryItem} and calls {@code AgInventoryItem.issue()} — one
 * transaction, mirroring {@code AgFeedRecordService}'s own shape exactly.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgInputApplicationService {

    private final AgInputApplicationRepository inputApplicationRepository;
    private final AgCropCycleRepository cropCycleRepository;
    private final AgInventoryItemRepository inventoryItemRepository;
    private final AgStockMovementRepository stockMovementRepository;
    private final HrFacade hrFacade;

    @Transactional(readOnly = true)
    public Page<InputApplicationResponse> getHistoryForCropCycle(TenantId tenantId, UUID cropCycleId, Pageable pageable) {
        return inputApplicationRepository.findByCropCycle(tenantId, cropCycleId, pageable).map(this::toResponse);
    }

    @Transactional
    public InputApplicationResponse createInputApplication(TenantId tenantId, UUID cropCycleId, CreateInputApplicationRequest req) {
        AgCropCycle cycle = cropCycleRepository.findActiveById(tenantId, cropCycleId)
                .orElseThrow(() -> new ResourceNotFoundException("CropCycle", cropCycleId.toString()));
        String appliedByName = resolveEmployeeName(tenantId, req.appliedBy());

        AgInputApplication application = AgInputApplication.create(tenantId, cropCycleId, req.applicationDate(),
                req.inputType(), req.inventoryItemId(), req.productUsed(), req.quantityApplied(), req.unitOfMeasure(),
                req.applicationMethod(), req.appliedBy(), appliedByName, req.laborHours(), req.cost(),
                req.weatherConditions(), req.notes());
        inputApplicationRepository.save(application);

        if (req.inventoryItemId() != null) {
            AgInventoryItem item = inventoryItemRepository.findActiveById(tenantId, req.inventoryItemId())
                    .orElseThrow(() -> new ResourceNotFoundException("InventoryItem", req.inventoryItemId().toString()));
            item.issue(req.quantityApplied());
            // FIX (applied alongside AgCostReportingService, for the same
            // reason as AgCropCycleService.issueSeed()): snapshot the
            // item's unit cost into the stock movement instead of leaving
            // it null. Not required by AgCostReportingService itself — that
            // service reads AgInputApplication.cost directly, which the
            // caller supplies independently — but leaving this movement's
            // cost null would be a real gap for any future inventory-
            // valuation reporting over AgStockMovement, and there's no
            // reason to leave it inconsistent with AgFeedRecordService's
            // own (already-correct) equivalent.
            AgStockMovement movement = AgStockMovement.create(tenantId, req.inventoryItemId(), "ISSUE",
                    req.applicationDate(), req.quantityApplied(), item.getUnitCost(), "AgInputApplication", application.getId(),
                    null, null, "Input applied: " + req.inputType() + (req.productUsed() != null ? " (" + req.productUsed() + ")" : ""));
            stockMovementRepository.save(movement);
        }

        log.info("Input application recorded id={} cropCycle={} tenant={}", application.getId(), cropCycleId, tenantId.getValue());
        return toResponse(application);
    }

    private String resolveEmployeeName(TenantId tenantId, UUID employeeId) {
        if (employeeId == null) return null;
        Optional<EmployeeResponse> employee = hrFacade.findEmployeeById(tenantId, employeeId);
        if (employee.isEmpty()) {
            throw new IllegalArgumentException("Employee not found: " + employeeId);
        }
        return employee.get().fullName();
    }

    private InputApplicationResponse toResponse(AgInputApplication a) {
        return new InputApplicationResponse(
                a.getId(), a.getCropCycleId(), a.getApplicationDate(), a.getInputType(), a.getInventoryItemId(),
                a.getProductUsed(), a.getQuantityApplied(), a.getUnitOfMeasure(), a.getApplicationMethod(),
                a.getAppliedBy(), a.getAppliedByName(), a.getLaborHours(), a.getCost(), a.getWeatherConditions(),
                a.getNotes(), a.getCreatedAt()
        );
    }
}
