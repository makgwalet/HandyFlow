package za.co.handyflow.platform.fuel.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import za.co.handyflow.platform.fuel.domain.model.FuelEligibilityOverride;

import java.util.UUID;

public interface FuelEligibilityOverrideRepository extends JpaRepository<FuelEligibilityOverride, UUID> {}
