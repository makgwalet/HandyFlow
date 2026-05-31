// security/domain/repository/CheckpointRepository.java

package za.co.handyflow.platform.security.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.security.domain.model.Checkpoint;

import java.util.Optional;
import java.util.UUID;

public interface CheckpointRepository extends JpaRepository<Checkpoint, UUID> {

    @Query("SELECT c FROM Checkpoint c WHERE c.qrCode = :qrCode AND c.active = true")
    Optional<Checkpoint> findByQrCode(String qrCode);
}