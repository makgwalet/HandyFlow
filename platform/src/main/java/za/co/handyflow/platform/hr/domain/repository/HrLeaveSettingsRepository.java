package za.co.handyflow.platform.hr.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import za.co.handyflow.platform.hr.domain.model.HrLeaveSettings;

import java.util.UUID;

public interface HrLeaveSettingsRepository extends JpaRepository<HrLeaveSettings, UUID> {}
