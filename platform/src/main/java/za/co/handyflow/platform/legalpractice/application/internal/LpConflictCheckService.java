package za.co.handyflow.platform.legalpractice.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.legalpractice.domain.model.*;
import za.co.handyflow.platform.legalpractice.domain.repository.*;
import za.co.handyflow.platform.legalpractice.dto.DecideConflictCheckRequest;
import za.co.handyflow.platform.legalpractice.dto.LpConflictCheckResponse;
import za.co.handyflow.platform.legalpractice.dto.RunConflictCheckRequest;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.*;

/**
 * Conflict-of-interest checks before a matter is opened. A check searches the client's name and ID and every other party
 * against the firm's clients (current and former) and the parties on all earlier matters. No hits means CLEAR at once;
 * any hit, or a search too broad to compare in full, needs a person to decide. A check can open one matter, within
 * {@link LpConflictCheck#VALID_DAYS} days, and the other parties are then recorded on that matter so later checks find them.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LpConflictCheckService {

    /** Rows compared per search word. If a word fills this, the search is flagged incomplete and a person must review. */
    static final int CANDIDATE_LIMIT = 200;
    private static final int MAX_WORDS = 5;

    private final LpConflictCheckRepository checkRepo;
    private final LpConflictCheckSubjectRepository subjectRepo;
    private final LpConflictCheckHitRepository hitRepo;
    private final LpMatterPartyRepository partyRepo;
    private final LpConflictClientSearchRepository clientSearch;
    private final LpClientRepository clientRepo;
    private final LpMatterRepository matterRepo;
    private final LpTrustSettingsService settings;

    @Transactional
    public LpConflictCheckResponse run(TenantId tenantId, RunConflictCheckRequest req, UUID by, String byName) {
        LpClient client = clientRepo.findActiveById(tenantId, req.clientId())
                .orElseThrow(() -> new ResourceNotFoundException("LpClient", req.clientId().toString()));
        List<RunConflictCheckRequest.Party> given = req.parties() == null ? List.of() : req.parties();
        if (given.size() > 25) throw new IllegalArgumentException("At most 25 other parties per check");

        UUID checkId = UUID.randomUUID();
        List<LpConflictCheckSubject> subjects = new ArrayList<>();
        subjects.add(LpConflictCheckSubject.of(tenantId, checkId, client.getName(), client.getIdOrRegistrationNumber(), "CLIENT"));
        for (var p : given) {
            if (p == null || p.name() == null || p.name().isBlank()) continue; // an empty row on the form is not a party
            subjects.add(LpConflictCheckSubject.of(tenantId, checkId, p.name(), p.idNumber(),
                    p.role() == null || p.role().isBlank() ? "OPPOSING_PARTY" : p.role().trim().toUpperCase()));
        }

        List<LpConflictCheckHit> hits = new ArrayList<>();
        boolean[] truncated = {false};
        Set<String> seen = new HashSet<>();
        for (var s : subjects) {
            search(tenantId, checkId, client.getId(), s, hits, seen, truncated);
        }

        LpConflictCheck check = LpConflictCheck.run(checkId, tenantId, client.getId(), client.getName(), hits.size(), truncated[0], by, byName);
        checkRepo.save(check);
        subjectRepo.saveAll(subjects);
        hitRepo.saveAll(hits);
        log.info("Conflict check={} client={} hits={} truncated={} status={} by={} tenant={}",
                check.getId(), client.getId(), hits.size(), truncated[0], check.getStatus(), by, tenantId);
        return toResponse(tenantId, check);
    }

    private void search(TenantId tenantId, UUID checkId, UUID clientId, LpConflictCheckSubject s,
                        List<LpConflictCheckHit> hits, Set<String> seen, boolean[] truncated) {
        Pageable limit = PageRequest.of(0, CANDIDATE_LIMIT);
        Map<UUID, LpClient> clients = new LinkedHashMap<>();
        Map<UUID, LpMatterParty> parties = new LinkedHashMap<>();

        String id = ConflictMatcher.normalizeId(s.getIdNumber());
        if (!id.isEmpty()) {
            for (LpClient c : clientSearch.byIdNumber(tenantId, id, limit)) clients.put(c.getId(), c);
            for (LpMatterParty p : partyRepo.byIdNumber(tenantId, id, limit)) parties.put(p.getId(), p);
        }
        List<String> words = new ArrayList<>(new LinkedHashSet<>(ConflictMatcher.tokens(s.getName())));
        words.removeIf(w -> w.length() < 2);
        words.sort(Comparator.comparingInt(String::length).reversed());
        for (String w : words.subList(0, Math.min(MAX_WORDS, words.size()))) {
            List<LpClient> cs = clientSearch.candidatesByName(tenantId, "%" + w + "%", limit);
            List<LpMatterParty> ps = partyRepo.candidatesByName(tenantId, "%" + w + "%", limit);
            if (cs.size() >= CANDIDATE_LIMIT || ps.size() >= CANDIDATE_LIMIT) truncated[0] = true;
            for (LpClient c : cs) clients.put(c.getId(), c);
            for (LpMatterParty p : ps) parties.put(p.getId(), p);
        }

        for (LpClient c : clients.values()) {
            if (c.getId().equals(clientId)) continue; // the client is not in conflict with themselves
            var kind = ConflictMatcher.compare(s.getName(), s.getIdNumber(), c.getName(), c.getIdOrRegistrationNumber());
            if (kind.isEmpty() || !seen.add(s.getName() + "|C|" + c.getId())) continue;
            String detail = "INACTIVE".equals(c.getStatus()) ? "Former client of the firm" : "Current client of the firm";
            hits.add(LpConflictCheckHit.of(tenantId, checkId, s.getName(), kind.get(), "CLIENT", c.getName(), "CLIENT", detail, c.getId(), null));
        }
        for (LpMatterParty p : parties.values()) {
            if (p.getClientId().equals(clientId)) continue; // the same side on the client's own earlier matters
            var kind = ConflictMatcher.compare(s.getName(), s.getIdNumber(), p.getName(), p.getIdNumber());
            if (kind.isEmpty() || !seen.add(s.getName() + "|P|" + p.getId())) continue;
            hits.add(LpConflictCheckHit.of(tenantId, checkId, s.getName(), kind.get(), "MATTER_PARTY", p.getName(), p.getRole(),
                    describeParty(tenantId, p), p.getClientId(), p.getMatterId()));
        }
    }

    private String describeParty(TenantId tenantId, LpMatterParty p) {
        String matter = matterRepo.findActiveById(tenantId, p.getMatterId()).map(LpMatter::getMatterNumber).orElse("an earlier matter");
        String client = clientRepo.findActiveById(tenantId, p.getClientId()).map(LpClient::getName).orElse("a client");
        return label(p.getRole()) + " on matter " + matter + " for " + client;
    }

    private static String label(String role) {
        String s = role == null ? "party" : role.toLowerCase().replace('_', ' ');
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    @Transactional
    public LpConflictCheckResponse decide(TenantId tenantId, UUID id, DecideConflictCheckRequest req, UUID by, String byName) {
        LpConflictCheck check = find(tenantId, id);
        check.decide(req.outcome() == null ? null : req.outcome().trim().toUpperCase(), req.reason(), by, byName,
                settings.selfApprovalAllowed(tenantId));
        checkRepo.save(check);
        log.info("Conflict check={} decided={} selfReviewed={} by={} tenant={}", id, check.getStatus(), check.isSelfReviewed(), by, tenantId);
        return toResponse(tenantId, check);
    }

    @Transactional(readOnly = true)
    public LpConflictCheckResponse get(TenantId tenantId, UUID id) {
        return toResponse(tenantId, find(tenantId, id));
    }

    @Transactional(readOnly = true)
    public Page<LpConflictCheckResponse> list(TenantId tenantId, String status, UUID clientId, Pageable pageable) {
        String s = status == null || status.isBlank() ? null : status.trim().toUpperCase();
        return checkRepo.search(tenantId, s, clientId, pageable).map(c -> toResponse(tenantId, c));
    }

    /** Called before a matter is created: refuses unless this check cleared this client and is still unused and in date. */
    @Transactional(readOnly = true)
    public LpConflictCheck requireUsableForMatter(TenantId tenantId, UUID checkId, UUID clientId) {
        LpConflictCheck check = find(tenantId, checkId);
        String problem = check.problemFor(clientId, Instant.now());
        if (problem != null) throw new IllegalStateException(problem);
        return check;
    }

    /** Called once the matter exists: marks the check used and records the other parties on the matter. */
    @Transactional
    public void consume(TenantId tenantId, LpConflictCheck check, UUID matterId) {
        check.consume(matterId, Instant.now());
        checkRepo.save(check);
        Set<String> done = new HashSet<>();
        for (LpConflictCheckSubject s : subjectRepo.findByCheck(tenantId, check.getId())) {
            if ("CLIENT".equals(s.getRole())) continue;
            if (!done.add(String.join(" ", ConflictMatcher.tokens(s.getName())) + "|" + ConflictMatcher.normalizeId(s.getIdNumber()))) continue;
            partyRepo.save(LpMatterParty.of(tenantId, matterId, check.getClientId(), s.getName(), s.getIdNumber(), s.getRole()));
        }
    }

    private LpConflictCheck find(TenantId tenantId, UUID id) {
        return checkRepo.findActiveById(tenantId, id).orElseThrow(() -> new ResourceNotFoundException("LpConflictCheck", id.toString()));
    }

    private LpConflictCheckResponse toResponse(TenantId tenantId, LpConflictCheck c) {
        var subjects = subjectRepo.findByCheck(tenantId, c.getId()).stream()
                .map(s -> new LpConflictCheckResponse.Subject(s.getName(), s.getIdNumber(), s.getRole())).toList();
        var hits = hitRepo.findByCheck(tenantId, c.getId()).stream()
                .map(h -> new LpConflictCheckResponse.Hit(h.getSubjectName(), h.getMatchKind(), h.getMatchedKind(), h.getMatchedName(),
                        h.getMatchedRole(), h.getMatchedDetail(), h.getMatchedClientId(), h.getMatchedMatterId())).toList();
        return new LpConflictCheckResponse(c.getId(), c.getClientId(), c.getClientName(), c.getStatus(), c.isTruncated(), subjects, hits,
                c.getRunByName(), c.getRunAt(), c.getDecidedByName(), c.getDecidedAt(), c.getDecisionReason(), c.isSelfReviewed(),
                c.getValidUntil(), c.getMatterId(), c.isUsableAt(Instant.now()));
    }
}
