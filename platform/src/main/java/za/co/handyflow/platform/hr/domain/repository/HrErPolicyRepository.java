package za.co.handyflow.platform.hr.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import za.co.handyflow.platform.hr.domain.model.HrErPolicy;

import java.util.UUID;

/** Keyed by tenant id. */
public interface HrErPolicyRepository extends JpaRepository<HrErPolicy, UUID> {}
