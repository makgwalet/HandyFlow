package za.co.handyflow.platform.earthmoving.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.audittrail.application.AuditTrailFacade;
import za.co.handyflow.platform.crm.CrmFacade;
import za.co.handyflow.platform.crm.CustomerSummary;
import za.co.handyflow.platform.earthmoving.domain.model.EarthSite;
import za.co.handyflow.platform.earthmoving.domain.model.EarthmovingRules;
import za.co.handyflow.platform.earthmoving.domain.model.SiteRules;
import za.co.handyflow.platform.earthmoving.domain.repository.EarthDeploymentRepository;
import za.co.handyflow.platform.earthmoving.domain.repository.EarthSiteRepository;
import za.co.handyflow.platform.earthmoving.dto.DeployAssetRequest;
import za.co.handyflow.platform.earthmoving.dto.SaveSiteRequest;
import za.co.handyflow.platform.earthmoving.dto.SiteResponse;
import za.co.handyflow.platform.notifications.application.UserRecipientResolver;
import za.co.handyflow.platform.projects.ProjectSummary;
import za.co.handyflow.platform.projects.ProjectsFacade;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.*;

/**
 * Sites (reusable places) and the checks that keep every link to a customer, project or person inside the caller's
 * company. A site can exist without a project or customer.
 */
@Service
@RequiredArgsConstructor
public class EarthSiteService {

    private final EarthSiteRepository sites;
    private final EarthDeploymentRepository deployments;
    private final CrmFacade crm;
    private final ProjectsFacade projects;
    private final UserRecipientResolver users;
    private final EarthmovingAccess access;
    private final AuditTrailFacade trail;

    @Transactional(readOnly = true)
    public List<SiteResponse> list(TenantId tenantId, boolean includeInactive) {
        access.require(EarthmovingRules.READ, "view sites");
        Map<UUID, String> customerNames = new HashMap<>();
        Map<UUID, String> projectNames = new HashMap<>();
        return sites.findAllForTenant(tenantId, includeInactive).stream()
                .map(s -> toResponse(tenantId, s, customerNames, projectNames)).toList();
    }

    @Transactional
    public SiteResponse create(TenantId tenantId, SaveSiteRequest req) {
        access.require(EarthmovingRules.WRITE, "add a site");
        SiteRules.checkSite(req.name(), req.latitude(), req.longitude());
        requireNameFree(tenantId, req.name(), null);
        checkLinks(tenantId, req.customerId(), req.projectId(), req.supervisorUserId());
        EarthSite s = sites.save(EarthSite.create(tenantId, req.name(), req.address(), req.latitude(), req.longitude(),
                req.customerId(), req.projectId(), req.supervisorUserId(), req.notes()));
        trail.record(tenantId, "EARTHMOVING_SITE_CREATED", "EARTHMOVING_SITE", s.getId(), null, snapshot(s));
        return toResponse(tenantId, s, new HashMap<>(), new HashMap<>());
    }

    @Transactional
    public SiteResponse update(TenantId tenantId, UUID id, SaveSiteRequest req) {
        access.require(EarthmovingRules.WRITE, "change a site");
        EarthSite s = find(tenantId, id);
        if (!s.isActive()) throw new IllegalStateException("This site is inactive. Add a new site instead.");
        SiteRules.checkSite(req.name(), req.latitude(), req.longitude());
        requireNameFree(tenantId, req.name(), id);
        checkLinks(tenantId, req.customerId(), req.projectId(), req.supervisorUserId());
        Map<String, Object> before = snapshot(s);
        s.update(req.name(), req.address(), req.latitude(), req.longitude(), req.customerId(), req.projectId(),
                req.supervisorUserId(), req.notes());
        trail.record(tenantId, "EARTHMOVING_SITE_UPDATED", "EARTHMOVING_SITE", id, before, snapshot(s));
        return toResponse(tenantId, s, new HashMap<>(), new HashMap<>());
    }

    /** Hides the site from pickers. Past deployments keep their link and their text. */
    @Transactional
    public SiteResponse deactivate(TenantId tenantId, UUID id) {
        access.require(EarthmovingRules.WRITE, "deactivate a site");
        EarthSite s = find(tenantId, id);
        s.deactivate();
        trail.record(tenantId, "EARTHMOVING_SITE_DEACTIVATED", "EARTHMOVING_SITE", id, Map.of("active", "true"), Map.of("active", "false"));
        return toResponse(tenantId, s, new HashMap<>(), new HashMap<>());
    }

    // ── used by deploy and by alert routing ──────────────────────────────────

    /** The supervisor of the site this machine is deployed to now, if it is on a saved site that has one. */
    @Transactional(readOnly = true)
    public Optional<UUID> supervisorForAsset(TenantId tenantId, UUID assetId) {
        return deployments.findOpenForAsset(tenantId, assetId)
                .flatMap(d -> d.getSiteId() == null ? Optional.<EarthSite>empty() : sites.findByTenantAndId(tenantId, d.getSiteId()))
                .filter(EarthSite::isActive)
                .map(EarthSite::getSupervisorUserId);
    }

    /** What a deployment will record. The names are copied now, so later renames never change this deployment. */
    public record DeployTarget(UUID siteId, String siteName, UUID customerId, String clientName, UUID projectId) {}

    @Transactional(readOnly = true)
    public DeployTarget resolveForDeploy(TenantId tenantId, DeployAssetRequest req) {
        EarthSite site = null;
        if (req.siteId() != null) {
            site = sites.findByTenantAndId(tenantId, req.siteId()).orElseThrow(() -> new IllegalArgumentException("That site was not found."));
            if (!site.isActive()) throw new IllegalArgumentException("That site is inactive.");
        }
        String siteName = site != null ? site.getName() : (req.siteName() == null ? "" : req.siteName().trim());
        if (siteName.isEmpty()) throw new IllegalArgumentException("Choose a site or give a site name.");

        UUID customerId = null;
        String clientName = req.clientName();
        UUID wantedCustomer = req.customerId() != null ? req.customerId() : (site != null ? site.getCustomerId() : null);
        if (wantedCustomer != null) {
            Optional<CustomerSummary> c = crm.findCustomerById(tenantId, wantedCustomer);
            if (c.isPresent()) {
                customerId = c.get().id();
                clientName = c.get().name();
            } else if (req.customerId() != null) {
                throw new IllegalArgumentException("That customer was not found.");
            } // a customer that was linked to the site and has since gone: fall back to the typed name
        }

        UUID projectId = null;
        UUID wantedProject = req.projectId() != null ? req.projectId() : (site != null ? site.getProjectId() : null);
        if (wantedProject != null) {
            if (projects.findProject(tenantId, wantedProject).isPresent()) projectId = wantedProject;
            else if (req.projectId() != null) throw new IllegalArgumentException("That project was not found.");
        }
        return new DeployTarget(site == null ? null : site.getId(), siteName, customerId, clientName, projectId);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    /** Every id must belong to this company; the lookups are tenant-scoped so another company's record reads as not found. */
    void checkLinks(TenantId tenantId, UUID customerId, UUID projectId, UUID supervisorUserId) {
        if (customerId != null && !crm.customerExists(tenantId, customerId))
            throw new IllegalArgumentException("That customer was not found.");
        if (projectId != null && projects.findProject(tenantId, projectId).isEmpty())
            throw new IllegalArgumentException("That project was not found.");
        if (supervisorUserId != null && users.resolveUser(tenantId, supervisorUserId).isEmpty())
            throw new IllegalArgumentException("The supervisor is not a user of this company.");
    }

    private void requireNameFree(TenantId tenantId, String name, UUID ignoring) {
        boolean taken = sites.findActiveByExactName(tenantId, name).stream().anyMatch(s -> !s.getId().equals(ignoring));
        if (taken) throw new IllegalStateException("A site called \"" + name.trim() + "\" already exists.");
    }

    private EarthSite find(TenantId tenantId, UUID id) {
        return sites.findByTenantAndId(tenantId, id).orElseThrow(() -> new ResourceNotFoundException("Site", id.toString()));
    }

    private static Map<String, Object> snapshot(EarthSite s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", s.getName());
        m.put("customerId", s.getCustomerId() == null ? "" : s.getCustomerId().toString());
        m.put("projectId", s.getProjectId() == null ? "" : s.getProjectId().toString());
        m.put("supervisorUserId", s.getSupervisorUserId() == null ? "" : s.getSupervisorUserId().toString());
        return m;
    }

    private SiteResponse toResponse(TenantId tenantId, EarthSite s, Map<UUID, String> customerNames, Map<UUID, String> projectNames) {
        String customerName = s.getCustomerId() == null ? null
                : customerNames.computeIfAbsent(s.getCustomerId(), id -> crm.findCustomerById(tenantId, id).map(CustomerSummary::name).orElse(null));
        String projectName = s.getProjectId() == null ? null
                : projectNames.computeIfAbsent(s.getProjectId(), id -> projects.findProject(tenantId, id).map(ProjectSummary::name).orElse(null));
        return new SiteResponse(s.getId(), s.getName(), s.getAddress(), s.getLatitude(), s.getLongitude(), s.getCustomerId(), customerName,
                s.getProjectId(), projectName, s.getSupervisorUserId(), s.getNotes(), s.isActive());
    }
}
