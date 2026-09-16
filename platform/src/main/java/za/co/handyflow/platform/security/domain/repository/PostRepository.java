package za.co.handyflow.platform.security.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.security.domain.model.Post;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PostRepository extends JpaRepository<Post, UUID> {

    @Query("SELECT p FROM Post p WHERE p.tenantId = :tenantId AND p.id = :id")
    Optional<Post> findByTenantAndId(TenantId tenantId, UUID id);

    @Query("SELECT p FROM Post p WHERE p.tenantId = :tenantId AND p.siteId = :siteId AND p.active = true ORDER BY p.name")
    List<Post> findActiveForSite(TenantId tenantId, UUID siteId);
}
