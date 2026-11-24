package za.co.handyflow.platform.contracting.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.contracting.domain.model.ContractEscalationSettings;

import java.util.List;
import java.util.UUID;

public interface ContractEscalationSettingsRepository extends JpaRepository<ContractEscalationSettings, UUID> {

    /** Every organisation that has switched escalation on: the sweep visits only these. */
    @Query("SELECT s FROM ContractEscalationSettings s WHERE s.enabled = true")
    List<ContractEscalationSettings> findAllEnabled();
}
