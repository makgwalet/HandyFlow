package za.co.handyflow.platform.earthmoving.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import za.co.handyflow.platform.earthmoving.domain.model.EarthSettings;

import java.util.UUID;

public interface EarthSettingsRepository extends JpaRepository<EarthSettings, UUID> {}
