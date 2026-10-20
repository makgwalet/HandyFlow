package za.co.handyflow.platform.fleet.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.fleet.domain.model.Vehicle;
import za.co.handyflow.platform.fleet.domain.model.VehicleDocumentType;
import za.co.handyflow.platform.fleet.domain.model.VehicleInsurance;
import za.co.handyflow.platform.fleet.domain.repository.VehicleInsuranceRepository;
import za.co.handyflow.platform.fleet.domain.repository.VehicleRepository;
import za.co.handyflow.platform.fleet.dto.InsuranceResponse;
import za.co.handyflow.platform.fleet.dto.UpsertInsuranceRequest;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class VehicleInsuranceService {

    private final VehicleRepository vehicleRepository;
    private final VehicleInsuranceRepository insuranceRepository;

    @Transactional(readOnly = true)
    public Optional<InsuranceResponse> get(TenantId tenantId, UUID vehicleId) {
        findVehicle(tenantId, vehicleId);
        return insuranceRepository.findByTenantIdAndVehicleId(tenantId, vehicleId).map(this::toResponse);
    }

    /**
     * Creates or replaces the vehicle's insurance profile and writes the policy expiry
     * through to {@code Vehicle.insuranceExpiry} (cleared when the vehicle is recorded as
     * uninsured), so the existing daily expiry alerts keep working off this data.
     */
    @Transactional
    public InsuranceResponse upsert(TenantId tenantId, UUID vehicleId, UUID userId, UpsertInsuranceRequest req) {
        Vehicle vehicle = findVehicle(tenantId, vehicleId);
        validate(req);

        VehicleInsurance ins = insuranceRepository.findByTenantIdAndVehicleId(tenantId, vehicleId)
                .orElseGet(() -> VehicleInsurance.create(tenantId, vehicleId));

        ins.apply(req.insured(),
                blankToNull(req.insurer()), blankToNull(req.policyNumber()), req.policyType(),
                req.policyStart(), req.policyExpiry(), req.premium(), req.excess(),
                blankToNull(req.broker()), blankToNull(req.brokerContact()),
                req.coverAmount(), req.vehicleValue(),
                req.trackingRequired(), blankToNull(req.trackingProvider()), blankToNull(req.trackingDeviceNumber()),
                req.windscreenCover(), req.thirdPartyCoverAmount(), blankToNull(req.specialConditions()),
                userId);
        insuranceRepository.save(ins);

        vehicle.syncComplianceExpiry(VehicleDocumentType.INSURANCE,
                req.insured() ? req.policyExpiry() : null);
        vehicleRepository.save(vehicle);

        log.info("Insurance profile saved vehicle={} insured={} tenant={}", vehicleId, req.insured(), tenantId);
        return toResponse(ins);
    }

    private void validate(UpsertInsuranceRequest req) {
        if (req.insured()) {
            if (blankToNull(req.insurer()) == null) {
                throw new IllegalArgumentException("Insurer is required when the vehicle is insured");
            }
            if (req.policyExpiry() == null) {
                throw new IllegalArgumentException("Policy expiry is required when the vehicle is insured");
            }
        }
        if (req.policyStart() != null && req.policyExpiry() != null
                && req.policyExpiry().isBefore(req.policyStart())) {
            throw new IllegalArgumentException("Policy expiry cannot be before the policy start");
        }
    }

    private Vehicle findVehicle(TenantId tenantId, UUID vehicleId) {
        return vehicleRepository.findActiveById(tenantId, vehicleId)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle", vehicleId.toString()));
    }

    private String blankToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private InsuranceResponse toResponse(VehicleInsurance i) {
        Long days = i.getPolicyExpiry() == null ? null
                : ChronoUnit.DAYS.between(LocalDate.now(), i.getPolicyExpiry());
        return new InsuranceResponse(
                i.getId(), i.getVehicleId(), i.isInsured(), i.getInsurer(), i.getPolicyNumber(),
                i.getPolicyType() != null ? i.getPolicyType().name() : null,
                i.getPolicyStart(), i.getPolicyExpiry(), days,
                i.getPremium(), i.getExcess(), i.getBroker(), i.getBrokerContact(),
                i.getCoverAmount(), i.getVehicleValue(),
                i.isTrackingRequired(), i.getTrackingProvider(), i.getTrackingDeviceNumber(),
                i.isWindscreenCover(), i.getThirdPartyCoverAmount(), i.getSpecialConditions(),
                i.getUpdatedAt()
        );
    }
}