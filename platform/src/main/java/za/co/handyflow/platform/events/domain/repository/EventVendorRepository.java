package za.co.handyflow.platform.events.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.events.domain.model.EventVendor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EventVendorRepository extends JpaRepository<EventVendor, UUID> {

    @Query("SELECT v FROM EventVendor v WHERE v.eventId = :eventId ORDER BY v.vendorType, v.companyName")
    List<EventVendor> findByEvent(UUID eventId);

    @Query("SELECT v FROM EventVendor v WHERE v.id = :id AND v.eventId = :eventId")
    Optional<EventVendor> findByIdAndEvent(UUID id, UUID eventId);
}