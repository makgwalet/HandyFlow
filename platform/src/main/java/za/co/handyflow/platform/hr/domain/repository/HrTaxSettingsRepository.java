package za.co.handyflow.platform.hr.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import za.co.handyflow.platform.hr.domain.model.HrTaxSettings;

import java.util.UUID;

public interface HrTaxSettingsRepository extends JpaRepository<HrTaxSettings, UUID> {}
