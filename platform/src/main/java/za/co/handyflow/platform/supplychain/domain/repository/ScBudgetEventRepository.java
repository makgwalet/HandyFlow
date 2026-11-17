package za.co.handyflow.platform.supplychain.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import za.co.handyflow.platform.supplychain.domain.model.ScBudgetEvent;

import java.util.UUID;

public interface ScBudgetEventRepository extends JpaRepository<ScBudgetEvent, UUID> {
}
