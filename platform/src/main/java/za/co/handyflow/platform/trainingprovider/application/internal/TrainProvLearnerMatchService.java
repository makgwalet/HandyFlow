package za.co.handyflow.platform.trainingprovider.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvClient;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvDelegate;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvLearner;
import za.co.handyflow.platform.trainingprovider.domain.repository.TrainProvClientRepository;
import za.co.handyflow.platform.trainingprovider.domain.repository.TrainProvDelegateRepository;
import za.co.handyflow.platform.trainingprovider.domain.repository.TrainProvLearnerEventRepository;
import za.co.handyflow.platform.trainingprovider.domain.repository.TrainProvLearnerRepository;
import za.co.handyflow.platform.trainingprovider.domain.rules.LearnerMatchRules;
import za.co.handyflow.platform.trainingprovider.domain.rules.LearnerMatchRules.Match;
import za.co.handyflow.platform.trainingprovider.domain.rules.LearnerMatchRules.Person;
import za.co.handyflow.platform.trainingprovider.domain.rules.LearnerRules;
import za.co.handyflow.platform.trainingprovider.dto.LearnerMatchDtos.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Looks for people held twice, and for delegates with no learner record. It only points things out: it never links, merges or
 * changes a record. Archived learners are included in matches, so a returning person is not added a second time.
 */
@Service
@RequiredArgsConstructor
public class TrainProvLearnerMatchService {

    private final TrainProvLearnerRepository learners;
    private final TrainProvDelegateRepository delegates;
    private final TrainProvClientRepository clients;
    private final TrainProvLearnerEventRepository events;

    /** Who might already be held, from what is typed so far on a new learner. */
    public List<MatchView> check(TenantId t, MatchRequest r) {
        String name = ((r.firstNames() == null ? "" : r.firstNames()) + " " + (r.surname() == null ? "" : r.surname())).trim();
        Person probe = new Person(null, name, null, r.idNumber(), r.dateOfBirth(), r.email(), r.phone());
        return matchesAgainst(t, probe, null);
    }

    public List<MatchView> forLearner(TenantId t, UUID learnerId) {
        TrainProvLearner l = learners.findForTenant(t, learnerId).orElseThrow(() -> new ResourceNotFoundException("TrainProvLearner", learnerId.toString()));
        return matchesAgainst(t, learnerPerson(l), l.getId());
    }

    /** Every pair of live learners that may be one person. */
    public List<PairView> pairs(TenantId t) {
        List<TrainProvLearner> live = learners.search(t, "ACTIVE", null, null);
        Map<UUID, TrainProvLearner> byId = new HashMap<>();
        live.forEach(l -> byId.put(l.getId(), l));
        List<Person> people = live.stream().map(TrainProvLearnerMatchService::learnerPerson).toList();
        return LearnerMatchRules.candidatePairs(people).stream()
                .<PairView>map(p -> new PairView(view(byId.get(p.first().id())), view(byId.get(p.second().id())), p.match().strength(),
                        p.match().reasons(), p.match().conflicts()))
                .toList();
    }

    /** Delegates with no live learner linked, each with the learners that look like them. */
    public List<UnmatchedDelegateView> unmatchedDelegates(TenantId t) {
        List<TrainProvLearner> all = learners.search(t, null, null, null);
        Map<UUID, UUID> linked = new HashMap<>();
        for (TrainProvLearner l : all) {
            if (l.getDelegateId() != null && "ACTIVE".equals(l.getStatus())) {
                linked.put(l.getDelegateId(), l.getId());
            }
        }
        Map<UUID, String> clientNames = new HashMap<>();
        List<UnmatchedDelegateView> out = new ArrayList<>();
        for (TrainProvDelegate d : delegates.findAllActiveForTenant(t)) {
            if (linked.containsKey(d.getId())) {
                continue;
            }
            Person dp = delegatePerson(d);
            List<MatchView> candidates = new ArrayList<>();
            for (TrainProvLearner l : all) {
                if (l.getMergedIntoId() != null) {
                    continue;
                }
                Match m = LearnerMatchRules.compare(dp, learnerPerson(l));
                if (m.found()) {
                    candidates.add(new MatchView(view(l), m.strength(), m.reasons(), m.conflicts()));
                }
            }
            candidates.sort((a, b) -> Integer.compare(rank(a.strength()), rank(b.strength())));
            String clientName = clientNames.computeIfAbsent(d.getClientId(),
                    id -> clients.findActiveById(t, id).map(TrainProvClient::getTradingName).orElse(""));
            out.add(new UnmatchedDelegateView(view(d, null), d.getClientId(), clientName.isEmpty() ? null : clientName, candidates));
        }
        return out;
    }

    public List<LearnerEventView> history(TenantId t, UUID learnerId) {
        learners.findForTenant(t, learnerId).orElseThrow(() -> new ResourceNotFoundException("TrainProvLearner", learnerId.toString()));
        return events.forLearner(t, learnerId).stream()
                .<LearnerEventView>map(e -> new LearnerEventView(e.getId(), e.getEventType(), e.getDelegateId(), e.getOtherLearnerId(), e.getReason(),
                        e.getActedBy(), e.getActedAt()))
                .toList();
    }

    private List<MatchView> matchesAgainst(TenantId t, Person probe, UUID excludeLearner) {
        List<MatchView> out = new ArrayList<>();
        Map<UUID, UUID> linkedLearnerByDelegate = new HashMap<>();
        for (TrainProvLearner l : learners.search(t, null, null, null)) {
            if (l.getDelegateId() != null && "ACTIVE".equals(l.getStatus())) {
                linkedLearnerByDelegate.put(l.getDelegateId(), l.getId());
            }
            if (l.getId().equals(excludeLearner) || l.getMergedIntoId() != null) {
                continue;
            }
            Match m = LearnerMatchRules.compare(probe, learnerPerson(l));
            if (m.found()) {
                out.add(new MatchView(view(l), m.strength(), m.reasons(), m.conflicts()));
            }
        }
        UUID ownDelegate = excludeLearner == null ? null : learners.findForTenant(t, excludeLearner).map(TrainProvLearner::getDelegateId).orElse(null);
        for (TrainProvDelegate d : delegates.findAllActiveForTenant(t)) {
            if (d.getId().equals(ownDelegate)) {
                continue;
            }
            Match m = LearnerMatchRules.compare(probe, delegatePerson(d));
            if (m.found()) {
                out.add(new MatchView(view(d, linkedLearnerByDelegate.get(d.getId())), m.strength(), m.reasons(), m.conflicts()));
            }
        }
        out.sort((a, b) -> Integer.compare(rank(a.strength()), rank(b.strength())));
        return out;
    }

    private static int rank(String strength) {
        return LearnerMatchRules.STRONG.equals(strength) ? 0 : 1;
    }

    static Person learnerPerson(TrainProvLearner l) {
        return new Person(l.getId(), l.fullName(), l.getIdType(), l.getIdNumber(), l.getDateOfBirth(), l.getEmail(), l.getPhone());
    }

    static Person delegatePerson(TrainProvDelegate d) {
        return new Person(d.getId(), d.getFullName(), null, d.getIdNumber(), null, d.getEmail(), d.getPhone());
    }

    private static PersonView view(TrainProvLearner l) {
        return new PersonView("LEARNER", l.getId(), l.getLearnerNo(), l.fullName(), LearnerRules.mask(l.getIdNumber()), l.getDateOfBirth(),
                l.getEmail(), l.getPhone(), l.getStatus(), null);
    }

    private static PersonView view(TrainProvDelegate d, UUID linkedLearnerId) {
        return new PersonView("DELEGATE", d.getId(), d.getDelegateNumber(), d.getFullName(), LearnerRules.mask(d.getIdNumber()), null,
                d.getEmail(), d.getPhone(), d.getStatus(), linkedLearnerId);
    }
}
