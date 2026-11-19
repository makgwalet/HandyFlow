package za.co.handyflow.platform.contracting.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import za.co.handyflow.platform.contracting.domain.model.ContractAuthoritySettings;

import java.util.UUID;

public interface ContractAuthoritySettingsRepository extends JpaRepository<ContractAuthoritySettings, UUID> {}
