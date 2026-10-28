package za.co.handyflow.platform.internalaudit.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import za.co.handyflow.platform.internalaudit.domain.model.AuditSettingsRow;

import java.util.UUID;

public interface AuditSettingsRepository extends JpaRepository<AuditSettingsRow, UUID> {}
