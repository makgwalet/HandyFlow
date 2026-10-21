package za.co.handyflow.platform.hr.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import za.co.handyflow.platform.hr.domain.model.HrPayrollSettings;

import java.util.UUID;

public interface HrPayrollSettingsRepository extends JpaRepository<HrPayrollSettings, UUID> {}
