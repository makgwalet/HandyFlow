package za.co.handyflow.platform.complianceservices.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.complianceservices.domain.model.*;
import za.co.handyflow.platform.complianceservices.domain.repository.*;
import za.co.handyflow.platform.complianceservices.dto.ProviderDtos.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.*;
import java.util.function.Supplier;

/** Outside professionals and what they say they hold. An entered accreditation is a claim until someone else has checked it. */
@Service
@RequiredArgsConstructor
public class ComplianceProviderService {

    private final ComplianceProviderRepository providers;
    private final ComplianceProviderServiceLinkRepository links;
    private final ComplianceProviderAccreditationRepository accreditations;

    @Transactional(readOnly = true)
    public List<ProviderSummary> list(TenantId tenantId, LocalDate today) {
        Map<UUID, List<String>> services = new HashMap<>();
        for (ComplianceProviderServiceLink l : links.findAllForTenant(tenantId)) services.computeIfAbsent(l.getProviderId(), k -> new ArrayList<>()).add(l.getServiceCode());
        Map<UUID, List<ComplianceProviderAccreditation>> accred = new HashMap<>();
        for (ComplianceProvider p : providers.findAllForTenant(tenantId)) accred.put(p.getId(), accreditations.findForProvider(tenantId, p.getId()));
        List<ProviderSummary> out = new ArrayList<>();
        for (ComplianceProvider p : providers.findAllForTenant(tenantId)) {
            List<String> codes = services.getOrDefault(p.getId(), new ArrayList<>());
            Collections.sort(codes);
            out.add(summary(p, codes, accred.getOrDefault(p.getId(), List.of()), today));
        }
        return out;
    }

    @Transactional(readOnly = true)
    public ProviderDetail get(TenantId tenantId, UUID id, LocalDate today) {
        return detail(tenantId, find(tenantId, id), today);
    }

    @Transactional
    public ProviderDetail create(TenantId tenantId, ProviderRequest r, UUID by, LocalDate today) {
        ComplianceProvider p = guard(() -> ComplianceProvider.create(tenantId, r.name(), r.kind(), r.email(), r.phone(), r.coverage(), r.notes(), by));
        requireFreeEmail(tenantId, p.getEmail(), null);
        providers.save(p);
        return detail(tenantId, p, today);
    }

    @Transactional
    public ProviderDetail update(TenantId tenantId, UUID id, ProviderRequest r, LocalDate today) {
        ComplianceProvider p = find(tenantId, id);
        guard(() -> { p.edit(r.name(), r.kind(), r.email(), r.phone(), r.coverage(), r.notes()); return null; });
        requireFreeEmail(tenantId, p.getEmail(), p.getId());
        providers.save(p);
        return detail(tenantId, p, today);
    }

    @Transactional
    public ProviderDetail deactivate(TenantId tenantId, UUID id, String reason, LocalDate today) {
        ComplianceProvider p = find(tenantId, id);
        guard(() -> { p.deactivate(reason); return null; });
        return detail(tenantId, providers.save(p), today);
    }

    @Transactional
    public ProviderDetail reactivate(TenantId tenantId, UUID id, LocalDate today) {
        ComplianceProvider p = find(tenantId, id);
        guard(() -> { p.reactivate(); return null; });
        return detail(tenantId, providers.save(p), today);
    }

    @Transactional
    public ProviderDetail setServices(TenantId tenantId, UUID id, List<String> codes, LocalDate today) {
        ComplianceProvider p = find(tenantId, id);
        List<String> wanted = guard(() -> ProviderRules.serviceCodes(codes));
        List<ComplianceProviderServiceLink> current = links.findForProvider(tenantId, id);
        Set<String> have = new HashSet<>();
        for (ComplianceProviderServiceLink l : current) {
            if (wanted.contains(l.getServiceCode())) have.add(l.getServiceCode()); else links.delete(l);
        }
        for (String code : wanted) if (!have.contains(code)) links.save(ComplianceProviderServiceLink.of(tenantId, id, code));
        return detail(tenantId, p, today);
    }

    @Transactional
    public ProviderDetail claim(TenantId tenantId, UUID id, AccreditationRequest r, UUID by, LocalDate today) {
        ComplianceProvider p = find(tenantId, id);
        ComplianceProviderAccreditation a = guard(() -> ComplianceProviderAccreditation.claim(tenantId, id, r.body(), r.reference(), r.issuedOn(), r.expiresOn(), by));
        accreditations.save(a);
        return detail(tenantId, p, today);
    }

    @Transactional
    public ProviderDetail check(TenantId tenantId, UUID accreditationId, boolean accepted, String note, UUID by, LocalDate today) {
        ComplianceProviderAccreditation a = findAccreditation(tenantId, accreditationId);
        guard(() -> { a.check(accepted, by, note); return null; });
        accreditations.save(a);
        return detail(tenantId, find(tenantId, a.getProviderId()), today);
    }

    @Transactional
    public ProviderDetail retire(TenantId tenantId, UUID accreditationId, String note, UUID by, LocalDate today) {
        ComplianceProviderAccreditation a = findAccreditation(tenantId, accreditationId);
        guard(() -> { a.retire(by, note); return null; });
        accreditations.save(a);
        return detail(tenantId, find(tenantId, a.getProviderId()), today);
    }

    // ---- internals ----

    private ProviderDetail detail(TenantId tenantId, ComplianceProvider p, LocalDate today) {
        List<String> codes = new ArrayList<>();
        for (ComplianceProviderServiceLink l : links.findForProvider(tenantId, p.getId())) codes.add(l.getServiceCode());
        List<ComplianceProviderAccreditation> list = accreditations.findForProvider(tenantId, p.getId());
        List<AccreditationView> views = new ArrayList<>();
        for (ComplianceProviderAccreditation a : list) views.add(view(a, today));
        return new ProviderDetail(summary(p, codes, list, today), p.getNotes(), p.getInactiveReason(), p.getCreatedAt(), views);
    }

    private static ProviderSummary summary(ComplianceProvider p, List<String> codes, List<ComplianceProviderAccreditation> list, LocalDate today) {
        int usable = 0, unchecked = 0;
        LocalDate next = null;
        for (ComplianceProviderAccreditation a : list) {
            if (a.isUsable(today)) {
                usable++;
                if (a.getExpiresOn() != null && (next == null || a.getExpiresOn().isBefore(next))) next = a.getExpiresOn();
            }
            if (ProviderRules.CLAIMED.equals(a.getStatus())) unchecked++;
        }
        return new ProviderSummary(p.getId(), p.getName(), p.getKind(), p.getStatus(), p.getEmail(), p.getPhone(), p.getCoverage(), codes, usable, unchecked, next);
    }

    private static AccreditationView view(ComplianceProviderAccreditation a, LocalDate today) {
        return new AccreditationView(a.getId(), a.getBody(), a.getReference(), a.getIssuedOn(), a.getExpiresOn(), a.getStatus(), ProviderRules.expiry(a.getExpiresOn(), today), a.isUsable(today),
                a.getRecordedBy(), a.getRecordedAt(), a.getCheckedBy(), a.getCheckedAt(), a.getCheckNote(), a.getRetireNote());
    }

    private void requireFreeEmail(TenantId tenantId, String email, UUID selfId) {
        if (email == null) return;
        providers.findByEmail(tenantId, email).filter(o -> !o.getId().equals(selfId))
                .ifPresent(o -> { throw new HandyFlowException("Another provider already uses that email address", HttpStatus.CONFLICT, "PROVIDER_EMAIL"); });
    }

    private ComplianceProvider find(TenantId tenantId, UUID id) {
        return providers.findOne(tenantId, id).orElseThrow(() -> new ResourceNotFoundException("ComplianceProvider", id.toString()));
    }

    private ComplianceProviderAccreditation findAccreditation(TenantId tenantId, UUID id) {
        return accreditations.findOne(tenantId, id).orElseThrow(() -> new ResourceNotFoundException("ComplianceProviderAccreditation", id.toString()));
    }

    private static <T> T guard(Supplier<T> work) {
        try {
            return work.get();
        } catch (IllegalStateException e) {
            throw new HandyFlowException(e.getMessage(), HttpStatus.CONFLICT, "PROVIDER_STATE");
        } catch (IllegalArgumentException e) {
            throw new HandyFlowException(e.getMessage(), HttpStatus.BAD_REQUEST, "PROVIDER_INVALID");
        }
    }
}
