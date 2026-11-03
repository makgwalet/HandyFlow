package za.co.handyflow.platform.projects.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.projects.domain.model.ProjectTransmittalItem;

import java.util.List;
import java.util.UUID;

public interface ProjectTransmittalItemRepository extends JpaRepository<ProjectTransmittalItem, UUID> {

    @Query("SELECT i FROM ProjectTransmittalItem i WHERE i.transmittalId = :transmittalId ORDER BY i.documentNumber, i.revision")
    List<ProjectTransmittalItem> findByTransmittal(@Param("transmittalId") UUID transmittalId);
}