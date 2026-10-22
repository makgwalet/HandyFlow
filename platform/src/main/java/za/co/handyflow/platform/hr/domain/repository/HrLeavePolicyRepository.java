package za.co.handyflow.platform.hr.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import za.co.handyflow.platform.hr.domain.model.HrLeavePolicy;

import java.util.UUID;

public interface HrLeavePolicyRepository extends JpaRepository<HrLeavePolicy, UUID> {}
