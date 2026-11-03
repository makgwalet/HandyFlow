package za.co.handyflow.platform.projects.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.projects.domain.enums.ProjectMemberRole;
import za.co.handyflow.platform.projects.domain.model.ProjectMember;
import za.co.handyflow.platform.projects.domain.model.ProjectMemberEvent;
import za.co.handyflow.platform.projects.domain.repository.ProjectMemberEventRepository;
import za.co.handyflow.platform.projects.domain.repository.ProjectMemberRepository;
import za.co.handyflow.platform.projects.domain.repository.ProjectRepository;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Manages who is on a project and in what role, and records every change.
 *
 * RULES
 *  - A project must always keep at least one MANAGER: removing or demoting the
 *    last one is refused (409 LAST_MANAGER).
 *  - Adding someone already on the project is refused (409 ALREADY_MEMBER) — use
 *    the role-change endpoint instead.
 *
 * KNOWN LIMITATION: userId / userName come from the caller and are NOT verified
 * against the identity module (no facade for that was visible). A bogus userId
 * grants nothing — access is always evaluated for (tenant, user) of the caller —
 * but the display name is unverified. Resolve it through an identity facade when
 * one exists.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectMemberService {

    private final ProjectRepository            projectRepo;
    private final ProjectMemberRepository      memberRepo;
    private final ProjectMemberEventRepository eventRepo;

    @Transactional(readOnly = true)
    public List<ProjectMember> list(TenantId tenantId, UUID projectId) {
        verifyProject(tenantId, projectId);
        return memberRepo.findByProject(tenantId.getValue(), projectId);
    }

    @Transactional(readOnly = true)
    public Optional<ProjectMember> find(TenantId tenantId, UUID projectId, UUID userId) {
        return memberRepo.findByProjectAndUser(tenantId.getValue(), projectId, userId);
    }

    @Transactional
    public ProjectMember add(TenantId tenantId, UUID projectId, UUID userId, String userName,
                             ProjectMemberRole role, UUID actorId, String actorName) {
        verifyProject(tenantId, projectId);
        if (memberRepo.findByProjectAndUser(tenantId.getValue(), projectId, userId).isPresent()) {
            throw new HandyFlowException("User is already a member of this project",
                    HttpStatus.CONFLICT, "ALREADY_MEMBER");
        }
        return insert(tenantId, projectId, userId, userName, role, actorId, actorName);
    }

    /**
     * Idempotent: adds the user with the given role only if they aren't on the
     * project yet; never changes an existing member's role. Used when a project is
     * created / its manager or sponsor is set. Caller has already verified the project.
     */
    @Transactional
    public void ensureMember(TenantId tenantId, UUID projectId, UUID userId, String userName,
                             ProjectMemberRole role, UUID actorId, String actorName) {
        if (userId == null) return;
        if (memberRepo.findByProjectAndUser(tenantId.getValue(), projectId, userId).isPresent()) return;
        insert(tenantId, projectId, userId, userName, role, actorId, actorName);
    }

    @Transactional
    public ProjectMember changeRole(TenantId tenantId, UUID projectId, UUID userId,
                                    ProjectMemberRole newRole, UUID actorId, String actorName) {
        verifyProject(tenantId, projectId);
        ProjectMember m = memberRepo.findByProjectAndUser(tenantId.getValue(), projectId, userId)
                .orElseThrow(() -> notFoundMember());
        ProjectMemberRole old = m.getRole();
        if (old == newRole) return m;
        if (old == ProjectMemberRole.MANAGER) requireAnotherManager(tenantId, projectId);

        m.changeRole(newRole);
        ProjectMember saved = memberRepo.save(m);
        eventRepo.save(ProjectMemberEvent.record(tenantId.getValue(), projectId, userId,
                ProjectMemberEvent.ROLE_CHANGED, old, newRole, actorId, actorName));
        log.info("Project={} member={} role {} -> {} by={}", projectId, userId, old, newRole, actorId);
        return saved;
    }

    @Transactional
    public void remove(TenantId tenantId, UUID projectId, UUID userId, UUID actorId, String actorName) {
        verifyProject(tenantId, projectId);
        ProjectMember m = memberRepo.findByProjectAndUser(tenantId.getValue(), projectId, userId)
                .orElseThrow(() -> notFoundMember());
        if (m.getRole() == ProjectMemberRole.MANAGER) requireAnotherManager(tenantId, projectId);

        memberRepo.delete(m);
        eventRepo.save(ProjectMemberEvent.record(tenantId.getValue(), projectId, userId,
                ProjectMemberEvent.REMOVED, m.getRole(), null, actorId, actorName));
        log.info("Project={} member={} removed by={}", projectId, userId, actorId);
    }

    @Transactional(readOnly = true)
    public List<ProjectMemberEvent> history(TenantId tenantId, UUID projectId) {
        verifyProject(tenantId, projectId);
        return eventRepo.findByProject(tenantId.getValue(), projectId);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private ProjectMember insert(TenantId tenantId, UUID projectId, UUID userId, String userName,
                                 ProjectMemberRole role, UUID actorId, String actorName) {
        ProjectMember saved = memberRepo.save(ProjectMember.create(
                tenantId.getValue(), projectId, userId, blankToNull(userName), role, actorId));
        eventRepo.save(ProjectMemberEvent.record(tenantId.getValue(), projectId, userId,
                ProjectMemberEvent.ADDED, null, role, actorId, actorName));
        log.info("Project={} member={} added as {} by={}", projectId, userId, role, actorId);
        return saved;
    }

    private void requireAnotherManager(TenantId tenantId, UUID projectId) {
        if (memberRepo.countByProjectAndRole(tenantId.getValue(), projectId, ProjectMemberRole.MANAGER) <= 1) {
            throw new HandyFlowException(
                    "A project must keep at least one manager — assign another manager first",
                    HttpStatus.CONFLICT, "LAST_MANAGER");
        }
    }

    private void verifyProject(TenantId tenantId, UUID projectId) {
        projectRepo.findByTenantAndId(tenantId.getValue(), projectId)
                .orElseThrow(() -> new HandyFlowException("Project not found", HttpStatus.NOT_FOUND, "NOT_FOUND"));
    }

    private static HandyFlowException notFoundMember() {
        return new HandyFlowException("Member not found on this project", HttpStatus.NOT_FOUND, "NOT_FOUND");
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}