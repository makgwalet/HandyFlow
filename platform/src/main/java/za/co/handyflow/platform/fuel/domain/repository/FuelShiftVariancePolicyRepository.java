package za.co.handyflow.platform.fuel.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import za.co.handyflow.platform.fuel.domain.model.FuelShiftVariancePolicy;

import java.util.UUID;

public interface FuelShiftVariancePolicyRepository extends JpaRepository<FuelShiftVariancePolicy, UUID> {}
