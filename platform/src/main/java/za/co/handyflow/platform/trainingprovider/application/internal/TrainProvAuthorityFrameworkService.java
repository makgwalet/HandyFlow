package za.co.handyflow.platform.trainingprovider.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvAccreditation;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvAccreditationEvent;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvAccreditationEvidence;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvAccreditationScope;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvAuthority;
import za.co.handyflow.platform.trainingprovider.domain.repository.*;
import za.co.handyflow.platform.trainingprovider.domain.rules.AccreditationRules;
import za.co.handyflow.platform.trainingprovider.domain.rules.AuthorityFrameworkRules;
import za.co.handyflow.platform.trainingprovider.dto.AccreditationDtos.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * The provider's authorities in its own order, with what each has said: status, scope, dates, where the information came from, and the evidence
 * behind it. Current, transitional and historical records are kept apart and nothing is overwritten: a record that stops applying is marked
 * historical with the day it ended. This never says the provider is accredited. It shows what was recorded, checked against its dates.
 */
@Service
@RequiredArgsConstructor
public class TrainProvAuthorityFrameworkService {

    private final TrainProvAuthorityRepository authorities;
    private final TrainProvAccreditationRepository accreditations;
    private final TrainProvAccreditationScopeRepository scopes;
    private final TrainProvAccreditationEventRepository events;
    private final TrainProvAccreditationEvidenceRepository evidence;
    private final TrainProvReportObligationRepository obligations;

    public List<FrameworkAuthority> overview(TenantId t, LocalDate today) {
        List<TrainProvAccreditation> all = accreditations.findAllForTenant(t, null, null);
        Map<UUID, List<TrainProvAccreditationEvidence>> ev = evidence.allForTenant(t).stream().collect(Collectors.groupingBy(TrainProvAccreditationEvidence::getAccreditationId));
        Map<UUID, List<TrainProvAccreditationScope>> sc = new HashMap<>();
        for (TrainProvAccreditation a : all) {
            sc.put(a.getId(), scopes.findForAccreditation(t, a.getId()));
        }
        Map<UUID, Long> liveObligations = obligations.search(t, null, "ACTIVE").stream()
                .collect(Collectors.groupingBy(o -> o.getAuthorityId(), Collectors.counting()));
        List<FrameworkAuthority> out = new ArrayList<>();
        for (TrainProvAuthority au : authorities.findAllForTenant(t)) {
            List<FrameworkRow> rows = all.stream().filter(a -> a.getAuthorityId().equals(au.getId()))
                    .sorted(Comparator.comparing((TrainProvAccreditation a) -> currencyOrder(a.getCurrency())).thenComparing(TrainProvAccreditation::getTitle))
                    .<FrameworkRow>map(a -> row(a, sc.getOrDefault(a.getId(), List.of()), ev.getOrDefault(a.getId(), List.of()), today)).toList();
            out.add(new FrameworkAuthority(au.getId(), au.getName(), au.getAuthorityType(), AuthorityFrameworkRules.rank(au.getAuthorityType()),
                    au.getStatus(), liveObligations.getOrDefault(au.getId(), 0L).intValue(), rows));
        }
        out.sort(Comparator.comparingInt(FrameworkAuthority::rank).thenComparing(FrameworkAuthority::name));
        return out;
    }

    @Transactional
    public FrameworkRow updateFramework(TenantId t, UUID id, FrameworkRequest r, UUID by, LocalDate today) {
        TrainProvAccreditation a = accreditation(t, id);
        guard(() -> { a.setFramework(r.effectiveFrom(), r.reviewDueOn(), r.sourceType(), r.sourceReference(), r.sourceVersionDate(), r.sourceLocation(), today); return null; });
        accreditations.save(a);
        events.save(TrainProvAccreditationEvent.of(t, id, a.getStatus(), a.getStatus(), "Dates and source updated", null, by));
        return row(a, today);
    }

    @Transactional
    public FrameworkRow changeCurrency(TenantId t, UUID id, CurrencyRequest r, UUID by, LocalDate today) {
        TrainProvAccreditation a = accreditation(t, id);
        if (r.supersededById() != null) {
            TrainProvAccreditation next = accreditation(t, r.supersededById());
            if (!next.getAuthorityId().equals(a.getAuthorityId())) {
                throw new HandyFlowException("The record that replaced this one must be from the same authority", HttpStatus.BAD_REQUEST, "TP_ACCREDITATION_INVALID");
            }
        }
        String before = a.getCurrency();
        guard(() -> { a.setCurrency(r.currency(), r.note(), r.endedOn(), r.supersededById(), today); return null; });
        accreditations.save(a);
        events.save(TrainProvAccreditationEvent.of(t, id, a.getStatus(), a.getStatus(),
                "Marked " + r.currency().toLowerCase() + " (was " + before.toLowerCase() + ")" + (r.note() == null || r.note().isBlank() ? "" : ": " + r.note().trim()), null, by));
        return row(a, today);
    }

    public List<EvidenceView> evidenceFor(TenantId t, UUID accreditationId) {
        accreditation(t, accreditationId);
        return evidence.forAccreditation(t, accreditationId).stream().<EvidenceView>map(TrainProvAuthorityFrameworkService::view).toList();
    }

    @Transactional
    public EvidenceView addEvidence(TenantId t, UUID accreditationId, EvidenceRequest r, UUID by, LocalDate today) {
        TrainProvAccreditation a = accreditation(t, accreditationId);
        if (AccreditationRules.isTerminal(a.getStatus())) {
            throw new HandyFlowException("This accreditation is closed", HttpStatus.CONFLICT, "TP_ACCREDITATION_STATE");
        }
        TrainProvAccreditationEvidence e = guard(() -> TrainProvAccreditationEvidence.add(t, accreditationId, r.kind(), r.description(), r.reference(),
                r.documentDate(), r.receivedOn() == null ? today : r.receivedOn(), by, today));
        evidence.save(e);
        return view(e);
    }

    @Transactional
    public EvidenceView decideEvidence(TenantId t, UUID evidenceId, EvidenceDecisionRequest r, UUID by) {
        TrainProvAccreditationEvidence e = evidence.findForTenant(t, evidenceId)
                .orElseThrow(() -> new ResourceNotFoundException("TrainProvAccreditationEvidence", evidenceId.toString()));
        guard(() -> { e.decide(r.toStatus(), r.note(), by); return null; });
        evidence.save(e);
        return view(e);
    }

    private FrameworkRow row(TrainProvAccreditation a, LocalDate today) {
        return row(a, scopes.findForAccreditation(accreditationTenant(a), a.getId()), evidence.forAccreditation(accreditationTenant(a), a.getId()), today);
    }

    private static TenantId accreditationTenant(TrainProvAccreditation a) {
        return TenantId.of(a.getTenantId());
    }

    static FrameworkRow row(TrainProvAccreditation a, List<TrainProvAccreditationScope> sc, List<TrainProvAccreditationEvidence> ev, LocalDate today) {
        String effective = AccreditationRules.effectiveStatus(a.getStatus(), a.getExpiryDate(), today);
        List<TrainProvAccreditationScope> live = sc.stream().filter(s -> "ACTIVE".equals(s.getStatus())).toList();
        int verified = (int) ev.stream().filter(e -> "VERIFIED".equals(e.getStatus())).count();
        int pending = (int) ev.stream().filter(e -> "PENDING".equals(e.getStatus())).count();
        return new FrameworkRow(a.getId(), a.getTitle(), a.getAccreditationNumber(), a.getStatus(), effective, a.getCurrency(), a.getCurrencyNote(),
                a.getEndedOn(), a.getSupersededById(), a.getEffectiveFrom(), a.getApprovalDate(), a.getExpiryDate(), a.getReviewDueOn(),
                a.getLastVerifiedOn(), a.getSourceType(), a.getSourceReference(), a.getSourceVersionDate(), a.getSourceLocation(), live.size(),
                (int) live.stream().map(TrainProvAccreditationScope::getSiteId).distinct().count(), verified, pending,
                AuthorityFrameworkRules.flags(effective, a.getCurrency(), verified, pending, a.getReviewDueOn(), today));
    }

    private static int currencyOrder(String currency) {
        return switch (currency) {
            case "CURRENT" -> 0;
            case "TRANSITIONAL" -> 1;
            default -> 2;
        };
    }

    private static EvidenceView view(TrainProvAccreditationEvidence e) {
        return new EvidenceView(e.getId(), e.getAccreditationId(), e.getKind(), e.getDescription(), e.getReference(), e.getDocumentDate(),
                e.getReceivedOn(), e.getStatus(), e.getAddedBy(), e.getAddedAt(), e.getDecidedBy(), e.getDecidedAt(), e.getDecisionNote());
    }

    private TrainProvAccreditation accreditation(TenantId t, UUID id) {
        return accreditations.findForTenant(t, id).orElseThrow(() -> new ResourceNotFoundException("TrainProvAccreditation", id.toString()));
    }

    private static <T> T guard(Supplier<T> work) {
        try {
            return work.get();
        } catch (IllegalStateException e) {
            throw new HandyFlowException(e.getMessage(), HttpStatus.CONFLICT, "TP_ACCREDITATION_STATE");
        } catch (IllegalArgumentException e) {
            throw new HandyFlowException(e.getMessage(), HttpStatus.BAD_REQUEST, "TP_ACCREDITATION_INVALID");
        }
    }
}
