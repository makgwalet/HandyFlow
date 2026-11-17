package za.co.handyflow.platform.fuel.application.internal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.fuel.domain.model.FuelArrivalCheck;
import za.co.handyflow.platform.fuel.domain.model.FuelDelivery;
import za.co.handyflow.platform.fuel.domain.model.FuelTanker;
import za.co.handyflow.platform.fuel.domain.repository.*;
import za.co.handyflow.platform.fuel.dto.AllocationRequest.CompartmentLoad;
import za.co.handyflow.platform.fuel.dto.ArrivalCheckDtos.ArrivalCheckView;
import za.co.handyflow.platform.fuel.dto.EligibilityDtos.EligibilityResult;
import za.co.handyflow.platform.fuel.dto.EligibilityDtos.Finding;
import za.co.handyflow.platform.hr.EmployeeSummary;
import za.co.handyflow.platform.hr.application.HrFacade;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The driver's own arrival check: the same eligibility rules as allocation, run against the tanker, driver and compartment
 * split already on the load, and recorded with its time. It changes nothing on the load. The app uses the result to decide
 * whether offloading may start; the server does not yet refuse a delivery because of it.
 */
@Service
@RequiredArgsConstructor
public class FuelArrivalCheckService {

    private final FuelDeliveryRepository deliveries;
    private final FuelTankerRepository tankers;
    private final FuelTankerCompartmentRepository compartments;
    private final FuelDeliveryCompartmentRepository loadSplits;
    private final FuelArrivalCheckRepository checks;
    private final FuelEligibilityService eligibility;
    private final HrFacade hr;
    private final ObjectMapper mapper;

    @Transactional
    public ArrivalCheckView check(TenantId tenantId, UUID userId, UUID deliveryId) {
        FuelDelivery d = ownLoad(tenantId, userId, deliveryId);
        if (!"SCHEDULED".equals(d.getStatus()) && !"IN_TRANSIT".equals(d.getStatus())) {
            throw new IllegalStateException("This load is " + d.getStatus().toLowerCase().replace('_', ' ') + ", so there is nothing to check.");
        }
        if (d.getTankerId() == null) throw new IllegalStateException("No tanker is allocated to this load yet. Ask the office.");
        FuelTanker tanker = tankers.findActiveById(tenantId, d.getTankerId())
                .orElseThrow(() -> new ResourceNotFoundException("Tanker", d.getTankerId().toString()));
        List<CompartmentLoad> loads = loadSplits.findByDelivery(tenantId, d.getId()).stream()
                .map(s -> new CompartmentLoad(s.getCompartmentId(), s.getLitres())).toList();
        EligibilityResult r = eligibility.evaluate(tenantId, d, tanker, d.getDriverEmployeeId(), d.getDriverName(),
                compartments.findByTanker(tenantId, tanker.getId()), loads);
        FuelArrivalCheck saved = checks.save(FuelArrivalCheck.record(tenantId, d.getId(), userId, r.allowed(), toJson(r.findings())));
        return new ArrivalCheckView(d.getId(), saved.isAllowed(), r.findings(), saved.getCheckedAt());
    }

    /** The latest check on one of the driver's own loads, or empty if none has been made. */
    @Transactional(readOnly = true)
    public Optional<ArrivalCheckView> latest(TenantId tenantId, UUID userId, UUID deliveryId) {
        FuelDelivery d = ownLoad(tenantId, userId, deliveryId);
        return checks.latest(tenantId, d.getId(), PageRequest.of(0, 1)).stream().findFirst()
                .map(c -> new ArrivalCheckView(c.getDeliveryId(), c.isAllowed(), fromJson(c.getFindings()), c.getCheckedAt()));
    }

    private FuelDelivery ownLoad(TenantId tenantId, UUID userId, UUID deliveryId) {
        if (userId == null) throw new IllegalStateException("You are not signed in as a user.");
        EmployeeSummary me = hr.findEmployeeByUserId(tenantId, userId)
                .orElseThrow(() -> new IllegalStateException("Your login is not linked to an employee record. Ask HR to link it."));
        FuelDelivery d = deliveries.findActiveById(tenantId, deliveryId)
                .orElseThrow(() -> new ResourceNotFoundException("Delivery", deliveryId.toString()));
        // Same message as a missing load so a driver cannot probe other drivers' loads.
        if (!me.id().equals(d.getDriverEmployeeId())) throw new ResourceNotFoundException("Delivery", deliveryId.toString());
        return d;
    }

    private String toJson(List<Finding> findings) {
        try { return mapper.writeValueAsString(findings); } catch (JsonProcessingException e) { throw new IllegalStateException("Could not record the check.", e); }
    }

    private List<Finding> fromJson(String json) {
        try { return mapper.readValue(json, new TypeReference<List<Finding>>() {}); } catch (JsonProcessingException e) { return List.of(); }
    }
}
