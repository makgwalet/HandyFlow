package za.co.handyflow.platform.fleet.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.fleet.domain.model.GeofenceState;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface GeofenceStateRepository extends JpaRepository<GeofenceState, UUID> {

    @Query("SELECT s FROM GeofenceState s WHERE s.vehicleId IN :vehicleIds")
    List<GeofenceState> findByVehicleIds(Collection<UUID> vehicleIds);
}