// property/domain/repository/InspectionRepository.java

package za.co.handyflow.platform.property.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.property.domain.model.Inspection;

import java.util.UUID;

public interface InspectionRepository extends JpaRepository<Inspection, UUID> {

    @Query("SELECT i FROM Inspection i WHERE i.unitId = :unitId ORDER BY i.inspectedAt DESC")
    Page<Inspection> findByUnit(UUID unitId, Pageable pageable);
}