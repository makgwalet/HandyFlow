package za.co.handyflow.platform.crm.domain.repository;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import za.co.handyflow.platform.crm.domain.model.CrmAuditEvent;

public interface CrmAuditEventRepository extends JpaRepository<CrmAuditEvent, UUID>, JpaSpecificationExecutor<CrmAuditEvent> {}
