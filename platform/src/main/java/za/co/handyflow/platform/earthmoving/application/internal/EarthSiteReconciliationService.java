package za.co.handyflow.platform.earthmoving.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.audittrail.application.AuditTrailFacade;
import za.co.handyflow.platform.crm.CrmFacade;
import za.co.handyflow.platform.crm.CustomerSummary;
import za.co.handyflow.platform.earthmoving.domain.model.EarthDeployment;
import za.co.handyflow.platform.earthmoving.domain.model.EarthSite;
import za.co.handyflow.platform.earthmoving.domain.model.EarthmovingRules;
import za.co.handyflow.platform.earthmoving.domain.model.SiteRules;
import za.co.handyflow.platform.earthmoving.domain.model.SiteRules.MatchState;
import za.co.handyflow.platform.earthmoving.domain.repository.EarthDeploymentRepository;
import za.co.handyflow.platform.earthmoving.domain.repository.EarthSiteRepository;
import za.co.handyflow.platform.earthmoving.dto.LinkResultResponse;
import za.co.handyflow.platform.earthmoving.dto.ReconciliationResponse;
import za.co.handyflow.platform.earthmoving.dto.ReconciliationResponse.Candidate;
import za.co.handyflow.platform.earthmoving.dto.ReconciliationResponse.Row;
import za.co.handyflow.platform.earthmoving.dto.ResolveLinksRequest;
import za.co.handyflow.platform.projects.ProjectsFacade;
import za.co.handyflow.platform.shared.TenantId;

import java.util.*;

/**
 * Moves older deployments, which carry only typed site and client text, onto site and customer records.
 * It never guesses: only an exact name (ignoring case and spaces) with exactly one record is treated as verified, anything
 * with several candidates or none is reported and left for a person to resolve. The typed text is never changed or removed,
 * and an existing link is never replaced.
 */
@Service
@RequiredArgsConstructor
public class EarthSiteReconciliationService {

    private final EarthDeploymentRepository deployments;
    private final EarthSiteRepository sites;
    private final CrmFacade crm;
    private final ProjectsFacade projects;
    private final EarthmovingAccess access;
    private final AuditTrailFacade trail;

    /** Typed site and client text as one group, with the deployments that carry it. */
    private record Group(String site, String client, List<EarthDeployment> rows) {}

    private record Matches(MatchState state, List<Candidate> candidates) {}

    private List<Group> groups(TenantId tenantId) {
        Map<String, Group> byKey = new LinkedHashMap<>();
        for (EarthDeployment d : deployments.findUnlinked(tenantId)) {
            String key = SiteRules.normalize(d.getSiteName()) + "|" + SiteRules.normalize(d.getClientName());
            byKey.computeIfAbsent(key, k -> new Group(d.getSiteName(), d.getClientName(), new ArrayList<>())).rows().add(d);
        }
        return new ArrayList<>(byKey.values());
    }

    private Matches siteMatches(TenantId tenantId, Group g, Map<String, Matches> cache) {
        return cache.computeIfAbsent("s:" + SiteRules.normalize(g.site()), k -> {
            List<Candidate> c = sites.findActiveByExactName(tenantId, g.site()).stream().map(s -> new Candidate(s.getId(), s.getName())).toList();
            return new Matches(SiteRules.classify(g.site(), c.size()), c);
        });
    }

    private Matches customerMatches(TenantId tenantId, Group g, Map<String, Matches> cache) {
        return cache.computeIfAbsent("c:" + SiteRules.normalize(g.client()), k -> {
            List<Candidate> c = crm.findCustomersByExactName(tenantId, g.client()).stream().map(x -> new Candidate(x.id(), x.name())).toList();
            return new Matches(SiteRules.classify(g.client(), c.size()), c);
        });
    }

    @Transactional(readOnly = true)
    public ReconciliationResponse report(TenantId tenantId) {
        access.require(EarthmovingRules.ADMIN, "review deployment links");
        Map<String, Matches> cache = new HashMap<>();
        List<Row> rows = new ArrayList<>();
        int sm = 0, sa = 0, su = 0, cm = 0, ca = 0, cu = 0;
        for (Group g : groups(tenantId)) {
            Matches s = siteMatches(tenantId, g, cache), c = customerMatches(tenantId, g, cache);
            boolean needsSite = g.rows().stream().anyMatch(d -> d.getSiteId() == null);
            boolean needsCustomer = g.rows().stream().anyMatch(d -> d.getCustomerId() == null && d.getClientName() != null);
            if (needsSite) { switch (s.state()) { case MATCHED -> sm++; case AMBIGUOUS -> sa++; case UNMATCHED -> su++; default -> { } } }
            if (needsCustomer) { switch (c.state()) { case MATCHED -> cm++; case AMBIGUOUS -> ca++; case UNMATCHED -> cu++; default -> { } } }
            rows.add(new Row(g.site(), g.client(), g.rows().size(), needsSite ? s.state().name() : "NONE", s.candidates(),
                    needsCustomer ? c.state().name() : "NONE", c.candidates()));
        }
        return new ReconciliationResponse(deployments.countAll(tenantId), deployments.countWithSite(tenantId),
                deployments.countWithClientText(tenantId), deployments.countWithCustomer(tenantId),
                rows.size(), sm, sa, su, cm, ca, cu, rows);
    }

    /** Links only the verified matches: exactly one record for the exact name. Everything else stays as it is. */
    @Transactional
    public LinkResultResponse applyVerified(TenantId tenantId, UUID actor) {
        access.require(EarthmovingRules.ADMIN, "link deployments");
        Map<String, Matches> cache = new HashMap<>();
        int toSite = 0, toCustomer = 0;
        for (Group g : groups(tenantId)) {
            Matches s = siteMatches(tenantId, g, cache), c = customerMatches(tenantId, g, cache);
            for (EarthDeployment d : g.rows()) {
                if (s.state() == MatchState.MATCHED && d.linkSiteIfEmpty(s.candidates().get(0).id())) toSite++;
                if (c.state() == MatchState.MATCHED && d.linkCustomerIfEmpty(c.candidates().get(0).id())) toCustomer++;
            }
        }
        deployments.flush();
        int left = groups(tenantId).size();
        trail.record(tenantId, "EARTHMOVING_DEPLOYMENTS_LINKED", "EARTHMOVING_DEPLOYMENT", actor == null ? UUID.randomUUID() : actor, null,
                Map.of("mode", "VERIFIED_MATCHES", "linkedToSite", String.valueOf(toSite), "linkedToCustomer", String.valueOf(toCustomer),
                        "groupsStillUnresolved", String.valueOf(left)));
        return new LinkResultResponse(toSite, toCustomer, 0, left);
    }

    /** A person decides which record a typed name means. Applies to unlinked deployments carrying exactly that text. */
    @Transactional
    public LinkResultResponse resolve(TenantId tenantId, ResolveLinksRequest req, UUID actor) {
        access.require(EarthmovingRules.ADMIN, "link deployments");
        if (req.siteId() == null && req.customerId() == null && req.projectId() == null)
            throw new IllegalArgumentException("Choose a site, a customer or a project to link.");
        EarthSite site = null;
        if (req.siteId() != null) {
            site = sites.findByTenantAndId(tenantId, req.siteId()).orElseThrow(() -> new IllegalArgumentException("That site was not found."));
        }
        if (req.customerId() != null && !crm.customerExists(tenantId, req.customerId())) throw new IllegalArgumentException("That customer was not found.");
        if (req.projectId() != null && projects.findProject(tenantId, req.projectId()).isEmpty()) throw new IllegalArgumentException("That project was not found.");

        String wantSite = SiteRules.normalize(req.legacySite()), wantClient = SiteRules.normalize(req.legacyClient());
        int toSite = 0, toCustomer = 0, toProject = 0;
        for (EarthDeployment d : deployments.findUnlinked(tenantId)) {
            if (!SiteRules.normalize(d.getSiteName()).equals(wantSite) || !SiteRules.normalize(d.getClientName()).equals(wantClient)) continue;
            if (d.linkSiteIfEmpty(site == null ? null : site.getId())) toSite++;
            if (d.linkCustomerIfEmpty(req.customerId())) toCustomer++;
            if (d.linkProjectIfEmpty(req.projectId())) toProject++;
        }
        deployments.flush();
        trail.record(tenantId, "EARTHMOVING_DEPLOYMENTS_LINKED", "EARTHMOVING_DEPLOYMENT", actor == null ? UUID.randomUUID() : actor, null,
                Map.of("mode", "MANUAL", "legacySite", req.legacySite(), "legacyClient", req.legacyClient() == null ? "" : req.legacyClient(),
                        "linkedToSite", String.valueOf(toSite), "linkedToCustomer", String.valueOf(toCustomer), "linkedToProject", String.valueOf(toProject)));
        return new LinkResultResponse(toSite, toCustomer, toProject, groups(tenantId).size());
    }
}
