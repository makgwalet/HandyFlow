package za.co.handyflow.platform.recruiter.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import za.co.handyflow.platform.recruiter.domain.model.RecSettings;

import java.util.UUID;

public interface RecSettingsRepository extends JpaRepository<RecSettings, UUID> {}
