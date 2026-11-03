package za.co.handyflow.platform.projects.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.projects.domain.model.ChangeOrderRevision;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChangeOrderRevisionRepository extends JpaRepository<ChangeOrderRevision, UUID> {

    @Query("SELECT r FROM ChangeOrderRevision r WHERE r.changeOrderId = :coId ORDER BY r.revision DESC")
    List<ChangeOrderRevision> findByChangeOrder(@Param("coId") UUID changeOrderId);

    @Query("SELECT r FROM ChangeOrderRevision r WHERE r.changeOrderId = :coId AND r.revision = :revision")
    Optional<ChangeOrderRevision> findByChangeOrderAndRevision(@Param("coId") UUID changeOrderId,
                                                               @Param("revision") int revision);
}