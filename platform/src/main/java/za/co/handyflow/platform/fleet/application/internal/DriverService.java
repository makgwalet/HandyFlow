package za.co.handyflow.platform.fleet.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.fleet.domain.model.Driver;
import za.co.handyflow.platform.fleet.domain.repository.DriverRepository;
import za.co.handyflow.platform.fleet.dto.CreateDriverRequest;
import za.co.handyflow.platform.fleet.dto.DriverResponse;
import za.co.handyflow.platform.fleet.dto.UpdateDriverRequest;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DriverService {

    private final DriverRepository driverRepository;

    @Transactional(readOnly = true)
    public Page<DriverResponse> getDrivers(TenantId tenantId, Pageable pageable) {
        return driverRepository.findAllActive(tenantId, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public DriverResponse getDriver(TenantId tenantId, UUID id) {
        return findActive(tenantId, id).map(this::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Driver", id.toString()));
    }

    @Transactional
    public DriverResponse createDriver(TenantId tenantId, CreateDriverRequest req) {
        Driver driver = Driver.create(
                tenantId, req.firstName(), req.lastName(), req.phone(), req.email(), req.idNumber(),
                req.licenseNumber(), req.licenseCode(), req.licenseExpiry(),
                req.prdpRequired(), req.prdpNumber(), req.prdpCategory(), req.prdpExpiry(),
                req.notes()
        );
        driverRepository.save(driver);
        log.info("Driver registered id={} name={} tenant={}", driver.getId(), driver.getFullName(), tenantId);
        return toResponse(driver);
    }

    @Transactional
    public DriverResponse updateDriver(TenantId tenantId, UUID id, UpdateDriverRequest req) {
        Driver driver = requireActive(tenantId, id);
        driver.update(
                req.firstName(), req.lastName(), req.phone(), req.email(), req.idNumber(),
                req.licenseNumber(), req.licenseCode(), req.licenseExpiry(),
                req.prdpRequired(), req.prdpNumber(), req.prdpCategory(), req.prdpExpiry(),
                req.notes()
        );
        driverRepository.save(driver);
        return toResponse(driver);
    }

    @Transactional
    public DriverResponse setStatus(TenantId tenantId, UUID id, boolean active) {
        Driver driver = requireActive(tenantId, id);
        driver.setStatus(active ? "ACTIVE" : "INACTIVE");
        driverRepository.save(driver);
        log.info("Driver status updated id={} active={}", id, active);
        return toResponse(driver);
    }

    @Transactional
    public void deleteDriver(TenantId tenantId, UUID id, UUID deletedByUserId) {
        Driver driver = requireActive(tenantId, id);
        driver.softDelete(deletedByUserId);
        driverRepository.save(driver);
    }

    private Optional<Driver> findActive(TenantId tenantId, UUID id) {
        return driverRepository.findActiveById(tenantId, id);
    }

    private Driver requireActive(TenantId tenantId, UUID id) {
        return findActive(tenantId, id).orElseThrow(() -> new ResourceNotFoundException("Driver", id.toString()));
    }

    private DriverResponse toResponse(Driver d) {
        return new DriverResponse(
                d.getId(), d.getFirstName(), d.getLastName(), d.getPhone(), d.getEmail(), d.getIdNumber(),
                d.getLicenseNumber(), d.getLicenseCode(), d.getLicenseExpiry(), d.isLicenseExpiringSoon(),
                d.isPrdpRequired(), d.getPrdpNumber(), d.getPrdpCategory(), d.getPrdpExpiry(), d.isPrdpExpiringSoon(),
                d.getStatus(), d.getNotes(), d.getCreatedAt()
        );
    }
}
