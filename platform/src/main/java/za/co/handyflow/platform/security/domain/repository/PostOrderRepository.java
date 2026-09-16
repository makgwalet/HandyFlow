package za.co.handyflow.platform.security.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.security.domain.model.PostOrder;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PostOrderRepository extends JpaRepository<PostOrder, UUID> {

    @Query("SELECT o FROM PostOrder o WHERE o.tenantId = :tenantId AND o.id = :id")
    Optional<PostOrder> findByTenantAndId(TenantId tenantId, UUID id);

    // The currently-ACTIVE site-level order (postId IS NULL) for a site
    // — what a guard's own "My Post" screen shows for the general,
    // everyone-at-this-site instructions.
    @Query("SELECT o FROM PostOrder o WHERE o.tenantId = :tenantId AND o.siteId = :siteId AND o.postId IS NULL AND o.status = 'ACTIVE'")
    Optional<PostOrder> findActiveSiteLevel(TenantId tenantId, UUID siteId);

    // The currently-ACTIVE order for one specific post.
    @Query("SELECT o FROM PostOrder o WHERE o.tenantId = :tenantId AND o.postId = :postId AND o.status = 'ACTIVE'")
    Optional<PostOrder> findActiveForPost(TenantId tenantId, UUID postId);

    // Backs publish()'s own supersede step — finds whatever is currently
    // ACTIVE for this exact site/post combination (postId may be null,
    // meaning site-level) so it can be superseded before the new draft
    // is published in its place.
    @Query("SELECT o FROM PostOrder o WHERE o.tenantId = :tenantId AND o.siteId = :siteId AND " +
            "((:postId IS NULL AND o.postId IS NULL) OR o.postId = :postId) AND o.status = 'ACTIVE'")
    Optional<PostOrder> findCurrentActive(TenantId tenantId, UUID siteId, UUID postId);

    // Full version history for a site/post, newest first — the "Post
    // Order v1... v2..." history view.
    @Query("SELECT o FROM PostOrder o WHERE o.tenantId = :tenantId AND o.siteId = :siteId AND " +
            "((:postId IS NULL AND o.postId IS NULL) OR o.postId = :postId) ORDER BY o.version DESC")
    List<PostOrder> findHistory(TenantId tenantId, UUID siteId, UUID postId);

    // Next version number for a site/post — MAX(version)+1, or 1 if
    // nothing exists yet for this site/post combination.
    @Query("SELECT COALESCE(MAX(o.version), 0) + 1 FROM PostOrder o WHERE o.tenantId = :tenantId AND o.siteId = :siteId AND " +
            "((:postId IS NULL AND o.postId IS NULL) OR o.postId = :postId)")
    int nextVersion(TenantId tenantId, UUID siteId, UUID postId);
}
