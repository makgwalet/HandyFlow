package za.co.handyflow.platform.earthmoving;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.audittrail.application.AuditTrailFacade;
import za.co.handyflow.platform.crm.CrmFacade;
import za.co.handyflow.platform.crm.CustomerSummary;
import za.co.handyflow.platform.earthmoving.application.internal.*;
import za.co.handyflow.platform.earthmoving.domain.model.*;
import za.co.handyflow.platform.earthmoving.domain.repository.EarthDeploymentRepository;
import za.co.handyflow.platform.earthmoving.domain.repository.EarthSiteRepository;
import za.co.handyflow.platform.earthmoving.dto.*;
import za.co.handyflow.platform.notifications.application.Recipient;
import za.co.handyflow.platform.notifications.application.UserRecipientResolver;
import za.co.handyflow.platform.projects.ProjectSummary;
import za.co.handyflow.platform.projects.ProjectsFacade;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class EarthSiteServiceTest {
    EarthSiteRepository sites = mock(EarthSiteRepository.class);
    EarthDeploymentRepository deployments = mock(EarthDeploymentRepository.class);
    CrmFacade crm = mock(CrmFacade.class);
    ProjectsFacade projects = mock(ProjectsFacade.class);
    UserRecipientResolver users = mock(UserRecipientResolver.class);
    AuditTrailFacade trail = mock(AuditTrailFacade.class);
    Set<String> granted = new HashSet<>();
    EarthmovingAccess access = new EarthmovingAccess() {
        @Override public boolean has(String authority) { return granted.contains(authority); }
    };
    EarthSiteService service;
    EarthSiteReconciliationService recon;
    TenantId tenant = TenantId.generate();
    UUID actor = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new EarthSiteService(sites, deployments, crm, projects, users, access, trail);
        recon = new EarthSiteReconciliationService(deployments, sites, crm, projects, access, trail);
        when(sites.save(any())).thenAnswer(i -> i.getArgument(0));
        when(sites.findActiveByExactName(any(), any())).thenReturn(List.of());
        when(sites.findAllForTenant(any(), anyBoolean())).thenReturn(List.of());
        when(crm.findCustomersByExactName(any(), any())).thenReturn(List.of());
        when(deployments.findUnlinked(any())).thenReturn(List.of());
    }

    static CustomerSummary customer(UUID id, String name) {
        return new CustomerSummary(id, name, null, null, null, null, null, "ACTIVE");
    }
    static ProjectSummary project(UUID id, String name) {
        return new ProjectSummary(id, "P-1", name, null, null, null, "ACTIVE");
    }
    static SaveSiteRequest save(String name, UUID customer, UUID project, UUID supervisor) {
        return new SaveSiteRequest(name, null, null, null, customer, project, supervisor, null);
    }
    EarthSite site(String name, UUID supervisor) {
        return EarthSite.create(tenant, name, null, null, null, null, null, supervisor, null);
    }
    EarthDeployment deployment(String siteName, String client) {
        return EarthDeployment.create(tenant, UUID.randomUUID(), siteName, client, null, null, LocalDate.now(), null, null);
    }

    // ── sites ────────────────────────────────────────────────────────────────

    @Test void creatingASiteNeedsWrite() {
        granted.add("EARTHMOVING_READ");
        assertThrows(HandyFlowException.class, () -> service.create(tenant, save("Mine A", null, null, null)));
        verify(sites, never()).save(any());
    }
    @Test void aSiteCanExistWithoutAProjectOrCustomer() {
        granted.add("EARTHMOVING_WRITE");
        SiteResponse r = service.create(tenant, save("Mine A", null, null, null));
        assertEquals("Mine A", r.name());
        verify(trail).record(eq(tenant), eq("EARTHMOVING_SITE_CREATED"), eq("EARTHMOVING_SITE"), any(), any(), any());
    }
    @Test void aDuplicateActiveNameIsRefused() {
        granted.add("EARTHMOVING_WRITE");
        when(sites.findActiveByExactName(tenant, "mine a")).thenReturn(List.of(site("Mine A", null)));
        assertThrows(IllegalStateException.class, () -> service.create(tenant, save("mine a", null, null, null)));
        verify(sites, never()).save(any());
    }
    @Test void anotherCompanysCustomerProjectOrUserIsRefused() {
        granted.add("EARTHMOVING_WRITE");
        UUID foreign = UUID.randomUUID();
        when(crm.customerExists(tenant, foreign)).thenReturn(false);
        when(projects.findProject(tenant, foreign)).thenReturn(Optional.empty());
        when(users.resolveUser(tenant, foreign)).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> service.create(tenant, save("A", foreign, null, null)));
        assertThrows(IllegalArgumentException.class, () -> service.create(tenant, save("B", null, foreign, null)));
        assertThrows(IllegalArgumentException.class, () -> service.create(tenant, save("C", null, null, foreign)));
        verify(sites, never()).save(any());
    }
    @Test void ownCustomerProjectAndSupervisorAreAccepted() {
        granted.add("EARTHMOVING_WRITE");
        UUID c = UUID.randomUUID(), p = UUID.randomUUID(), u = UUID.randomUUID();
        when(crm.customerExists(tenant, c)).thenReturn(true);
        when(projects.findProject(tenant, p)).thenReturn(Optional.of(project(p, "Pit")));
        when(users.resolveUser(tenant, u)).thenReturn(Optional.of(new Recipient(u, "Sam", null, null)));
        assertDoesNotThrow(() -> service.create(tenant, save("A", c, p, u)));
    }
    @Test void deactivatingKeepsTheRecordAndIsAudited() {
        granted.add("EARTHMOVING_WRITE");
        EarthSite s = site("Mine A", null);
        when(sites.findByTenantAndId(tenant, s.getId())).thenReturn(Optional.of(s));
        service.deactivate(tenant, s.getId());
        assertFalse(s.isActive());
        verify(sites, never()).delete(any());
        verify(trail).record(eq(tenant), eq("EARTHMOVING_SITE_DEACTIVATED"), any(), any(), any(), any());
    }

    // ── deploy target ────────────────────────────────────────────────────────

    @Test void aTypedNameStillWorksWithoutASite() {
        var t = service.resolveForDeploy(tenant, new DeployAssetRequest(" Quarry ", "Acme", null, null, null, null, null));
        assertNull(t.siteId());
        assertEquals("Quarry", t.siteName());
        assertEquals("Acme", t.clientName());
    }
    @Test void noSiteAndNoNameIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> service.resolveForDeploy(tenant,
                new DeployAssetRequest(" ", null, null, null, null, null, null)));
    }
    @Test void aSiteSuppliesItsNameAndItsCustomerAndProject() {
        UUID c = UUID.randomUUID(), p = UUID.randomUUID();
        EarthSite s = EarthSite.create(tenant, "Mine A", null, null, null, c, p, null, null);
        when(sites.findByTenantAndId(tenant, s.getId())).thenReturn(Optional.of(s));
        when(crm.findCustomerById(tenant, c)).thenReturn(Optional.of(customer(c, "Anglo American")));
        when(projects.findProject(tenant, p)).thenReturn(Optional.of(project(p, "Pit")));
        var t = service.resolveForDeploy(tenant, new DeployAssetRequest(null, null, null, null, null, null, null, s.getId(), null, null));
        assertEquals("Mine A", t.siteName());
        assertEquals("Anglo American", t.clientName());
        assertEquals(c, t.customerId());
        assertEquals(p, t.projectId());
    }
    @Test void aSiteFromAnotherCompanyOrAnInactiveOneIsRefused() {
        UUID id = UUID.randomUUID();
        when(sites.findByTenantAndId(tenant, id)).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> service.resolveForDeploy(tenant,
                new DeployAssetRequest(null, null, null, null, null, null, null, id, null, null)));
        EarthSite gone = site("Old", null);
        gone.deactivate();
        when(sites.findByTenantAndId(tenant, gone.getId())).thenReturn(Optional.of(gone));
        assertThrows(IllegalArgumentException.class, () -> service.resolveForDeploy(tenant,
                new DeployAssetRequest(null, null, null, null, null, null, null, gone.getId(), null, null)));
    }
    @Test void aChosenCustomerFromAnotherCompanyIsRefusedButAStaleSiteCustomerIsDropped() {
        UUID foreign = UUID.randomUUID();
        when(crm.findCustomerById(tenant, foreign)).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> service.resolveForDeploy(tenant,
                new DeployAssetRequest("Q", null, null, null, null, null, null, null, foreign, null)));
        EarthSite s = EarthSite.create(tenant, "Mine A", null, null, null, foreign, null, null, null);
        when(sites.findByTenantAndId(tenant, s.getId())).thenReturn(Optional.of(s));
        var t = service.resolveForDeploy(tenant, new DeployAssetRequest(null, "Typed Co", null, null, null, null, null, s.getId(), null, null));
        assertNull(t.customerId());
        assertEquals("Typed Co", t.clientName());
    }
    @Test void supervisorForAnAssetComesFromTheOpenDeploymentsSite() {
        UUID sup = UUID.randomUUID(), asset = UUID.randomUUID();
        EarthSite s = site("Mine A", sup);
        EarthDeployment d = deployment("Mine A", null).withLinks(s.getId(), null, null);
        when(deployments.findOpenForAsset(tenant, asset)).thenReturn(Optional.of(d));
        when(sites.findByTenantAndId(tenant, s.getId())).thenReturn(Optional.of(s));
        assertEquals(Optional.of(sup), service.supervisorForAsset(tenant, asset));
        s.deactivate();
        assertTrue(service.supervisorForAsset(tenant, asset).isEmpty());
        when(deployments.findOpenForAsset(tenant, asset)).thenReturn(Optional.of(deployment("Typed", null)));
        assertTrue(service.supervisorForAsset(tenant, asset).isEmpty());
    }

    // ── reconciliation ───────────────────────────────────────────────────────

    @Test void reconciliationIsAdminOnly() {
        granted.add("EARTHMOVING_MAINTAIN");
        assertThrows(HandyFlowException.class, () -> recon.report(tenant));
        assertThrows(HandyFlowException.class, () -> recon.applyVerified(tenant, actor));
        assertThrows(HandyFlowException.class, () -> recon.resolve(tenant, new ResolveLinksRequest("A", null, UUID.randomUUID(), null, null), actor));
    }
    @Test void onlyAnExactOneToOneMatchIsLinked() {
        granted.add("EARTHMOVING_ADMIN");
        EarthSite mine = site("Mine A", null);
        UUID cOne = UUID.randomUUID();
        EarthDeployment exact = deployment("  mine  a ", "Anglo");
        EarthDeployment ambiguous = deployment("Mine B", "Twin");
        EarthDeployment unmatched = deployment("Nowhere", "Ghost");
        when(deployments.findUnlinked(tenant)).thenReturn(List.of(exact, ambiguous, unmatched));
        when(sites.findActiveByExactName(tenant, "  mine  a ")).thenReturn(List.of(mine));
        when(sites.findActiveByExactName(tenant, "Mine B")).thenReturn(List.of(site("Mine B", null), site("Mine B", null)));
        when(crm.findCustomersByExactName(tenant, "Anglo")).thenReturn(List.of(customer(cOne, "Anglo")));
        when(crm.findCustomersByExactName(tenant, "Twin")).thenReturn(List.of(customer(UUID.randomUUID(), "Twin"), customer(UUID.randomUUID(), "Twin")));

        LinkResultResponse r = recon.applyVerified(tenant, actor);

        assertEquals(mine.getId(), exact.getSiteId());
        assertEquals(cOne, exact.getCustomerId());
        assertNull(ambiguous.getSiteId());
        assertNull(ambiguous.getCustomerId());
        assertNull(unmatched.getSiteId());
        assertEquals(1, r.deploymentsLinkedToSite());
        assertEquals(1, r.deploymentsLinkedToCustomer());
        assertEquals("  mine  a ", exact.getSiteName());   // the typed text is never changed
        assertEquals("Anglo", exact.getClientName());
        verify(trail).record(eq(tenant), eq("EARTHMOVING_DEPLOYMENTS_LINKED"), any(), any(), any(), any());
    }
    @Test void aLinkAlreadyThereIsNeverReplaced() {
        granted.add("EARTHMOVING_ADMIN");
        UUID existing = UUID.randomUUID();
        EarthDeployment d = deployment("Mine A", null).withLinks(existing, null, null);
        EarthSite other = site("Mine A", null);
        when(deployments.findUnlinked(tenant)).thenReturn(List.of(d));
        when(sites.findActiveByExactName(tenant, "Mine A")).thenReturn(List.of(other));
        recon.applyVerified(tenant, actor);
        assertEquals(existing, d.getSiteId());
    }
    @Test void aManualLinkOnlyTouchesDeploymentsWithThatExactText() {
        granted.add("EARTHMOVING_ADMIN");
        EarthSite target = site("Mine A", null);
        UUID c = UUID.randomUUID();
        when(sites.findByTenantAndId(tenant, target.getId())).thenReturn(Optional.of(target));
        when(crm.customerExists(tenant, c)).thenReturn(true);
        EarthDeployment same = deployment("Mine A (old)", "Anglo");
        EarthDeployment differentSite = deployment("Mine A new", "Anglo");
        EarthDeployment differentClient = deployment("Mine A (old)", "Other");
        when(deployments.findUnlinked(tenant)).thenReturn(List.of(same, differentSite, differentClient));
        LinkResultResponse r = recon.resolve(tenant, new ResolveLinksRequest("mine a (old)", "anglo", target.getId(), c, null), actor);
        assertEquals(target.getId(), same.getSiteId());
        assertEquals(c, same.getCustomerId());
        assertNull(differentSite.getSiteId());
        assertNull(differentClient.getSiteId());
        assertEquals(1, r.deploymentsLinkedToSite());
    }
    @Test void aManualLinkToAnotherCompanysRecordIsRefused() {
        granted.add("EARTHMOVING_ADMIN");
        UUID foreign = UUID.randomUUID();
        when(sites.findByTenantAndId(tenant, foreign)).thenReturn(Optional.empty());
        when(crm.customerExists(tenant, foreign)).thenReturn(false);
        when(projects.findProject(tenant, foreign)).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> recon.resolve(tenant, new ResolveLinksRequest("A", null, foreign, null, null), actor));
        assertThrows(IllegalArgumentException.class, () -> recon.resolve(tenant, new ResolveLinksRequest("A", null, null, foreign, null), actor));
        assertThrows(IllegalArgumentException.class, () -> recon.resolve(tenant, new ResolveLinksRequest("A", null, null, null, foreign), actor));
        assertThrows(IllegalArgumentException.class, () -> recon.resolve(tenant, new ResolveLinksRequest("A", null, null, null, null), actor));
    }
}
