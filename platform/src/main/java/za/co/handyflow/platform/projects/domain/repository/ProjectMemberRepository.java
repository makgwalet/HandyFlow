package za.co.handyflow.platform.projects.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.projects.domain.enums.ProjectMemberRole;
import za.co.handyflow.platform.projects.domain.model.ProjectMember;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectMemberRepository extends JpaRepository<ProjectMember, UUID> {

    @Query("""
            SELECT m FROM ProjectMember m
            WHERE m.tenantId = :tenantId AND m.projectId = :projectId
            ORDER BY m.addedAt
            """)
    List<ProjectMember> findByProject(@Param("tenantId") UUID tenantId,
                                      @Param("projectId") UUID projectId);

    @Query("""
            SELECT m FROM ProjectMember m
            WHERE m.tenantId = :tenantId AND m.projectId = :projectId AND m.userId = :userId
            """)
    Optional<ProjectMember> findByProjectAndUser(@Param("tenantId") UUID tenantId,
                                                 @Param("projectId") UUID projectId,
                                                 @Param("userId") UUID userId);

    /** One query for a whole page of projects — avoids N lookups when listing. */
    @Query("""
            SELECT m FROM ProjectMember m
            WHERE m.tenantId = :tenantId AND m.userId = :userId AND m.projectId IN :projectIds
            """)
    List<ProjectMember> findByUserAndProjects(@Param("tenantId") UUID tenantId,
                                              @Param("userId") UUID userId,
                                              @Param("projectIds") Collection<UUID> projectIds);

    @Query("""
            SELECT COUNT(m) FROM ProjectMember m
            WHERE m.tenantId = :tenantId AND m.projectId = :projectId AND m.role = :role
            """)
    long countByProjectAndRole(@Param("tenantId") UUID tenantId,
                               @Param("projectId") UUID projectId,
                               @Param("role") ProjectMemberRole role);
}