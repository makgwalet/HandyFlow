package za.co.handyflow.platform.admin.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import za.co.handyflow.platform.admin.domain.model.AdminUser;

import java.util.Optional;
import java.util.UUID;

public interface AdminUserRepository extends JpaRepository<AdminUser, UUID> {
    Optional<AdminUser> findByEmail(String email);
    boolean existsByEmail(String email);
}
