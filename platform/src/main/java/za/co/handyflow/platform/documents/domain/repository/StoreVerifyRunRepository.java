package za.co.handyflow.platform.documents.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import za.co.handyflow.platform.documents.domain.model.StoreVerifyRun;

import java.util.Optional;
import java.util.UUID;

public interface StoreVerifyRunRepository extends JpaRepository<StoreVerifyRun, UUID> {

    Optional<StoreVerifyRun> findFirstByOrderByStartedAtDesc();

    Optional<StoreVerifyRun> findFirstByFinishedAtIsNotNullOrderByStartedAtDesc();
}
