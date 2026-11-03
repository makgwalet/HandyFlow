package za.co.handyflow.platform.projects.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.projects.domain.model.ProjectDocumentReview;

import java.util.List;
import java.util.UUID;

public interface ProjectDocumentReviewRepository extends JpaRepository<ProjectDocumentReview, UUID> {

    @Query("SELECT r FROM ProjectDocumentReview r WHERE r.tenantId = :tenantId AND r.documentId = :documentId ORDER BY r.occurredAt")
    List<ProjectDocumentReview> findByDocument(@Param("tenantId") UUID tenantId, @Param("documentId") UUID documentId);
}