package za.co.handyflow.platform.trainingprovider.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvClient;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvEnrolmentFunding;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvFunder;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvLearnerEnrolment;
import za.co.handyflow.platform.trainingprovider.domain.repository.TrainProvClientRepository;
import za.co.handyflow.platform.trainingprovider.domain.repository.TrainProvEnrolmentFundingRepository;
import za.co.handyflow.platform.trainingprovider.domain.repository.TrainProvFunderRepository;
import za.co.handyflow.platform.trainingprovider.domain.repository.TrainProvLearnerEnrolmentRepository;
import za.co.handyflow.platform.trainingprovider.domain.repository.TrainProvLearnerRepository;
import za.co.handyflow.platform.trainingprovider.domain.rules.FundingRules;
import za.co.handyflow.platform.trainingprovider.dto.FundingDtos.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Funders and the links from enrolments to the funders paying for them. A link records who is paying and a reference. No amounts, rates, percentages or
 * eligibility rules are held, and a link never means funding was approved or paid. Nothing is deleted.
 */
@Service
@RequiredArgsConstructor
public class TrainProvFundingService {

    private final TrainProvFunderRepository funders;
    private final TrainProvEnrolmentFundingRepository links;
    private final TrainProvLearnerEnrolmentRepository enrolments;
    private final TrainProvLearnerRepository learners;
    private final TrainProvClientRepository clients;
    private final TrainProvNumberGenerator numbers;

    public List<FunderView> listFunders(TenantId t, String type, String status) {
        return funders.search(t, blank(type), blank(status)).stream().<FunderView>map(f -> view(t, f)).toList();
    }

    public FunderView getFunder(TenantId t, UUID id) {
        return view(t, funder(t, id));
    }

    @Transactional
    public FunderView createFunder(TenantId t, FunderRequest r, UUID by) {
        check(t, r, null);
        TrainProvFunder f = guard(() -> TrainProvFunder.create(t, numbers.nextFunderNumber(t), r.name(), r.funderType(), r.clientId(), r.contactName(), r.contactEmail(),
                r.contactPhone(), r.note(), by));
        funders.save(f);
        return view(t, f);
    }

    @Transactional
    public FunderView updateFunder(TenantId t, UUID id, FunderRequest r) {
        TrainProvFunder f = funder(t, id);
        check(t, r, f.getId());
        guard(() -> { f.change(r.name(), r.funderType(), r.clientId(), r.contactName(), r.contactEmail(), r.contactPhone(), r.note()); return null; });
        funders.save(f);
        return view(t, f);
    }

    @Transactional
    public FunderView retireFunder(TenantId t, UUID id, String reason, UUID by) {
        TrainProvFunder f = funder(t, id);
        int active = (int) links.activeForFunder(t, f.getId());
        guard(() -> { f.retire(reason, active, by); return null; });
        funders.save(f);
        return view(t, f);
    }

    public List<LinkView> listLinks(TenantId t, UUID enrolmentId, UUID funderId, String status) {
        return links.search(t, enrolmentId, funderId, blank(status)).stream().<LinkView>map(l -> linkView(t, l)).toList();
    }

    public EnrolmentFunding forEnrolment(TenantId t, UUID enrolmentId) {
        enrolment(t, enrolmentId);
        List<LinkView> all = listLinks(t, enrolmentId, null, null);
        List<String> names = all.stream().filter(l -> FundingRules.ACTIVE.equals(l.status())).map(l -> l.funderName()).toList();
        return new EnrolmentFunding(all, FundingRules.summary(names));
    }

    @Transactional
    public LinkView link(TenantId t, LinkRequest r, UUID by, LocalDate today) {
        TrainProvLearnerEnrolment e = enrolment(t, r.enrolmentId());
        TrainProvFunder f = funder(t, r.funderId());
        String problem = FundingRules.linkProblem(e.getStatus(), f.getStatus(), r.fundedFrom(), e.getStartDate(), today, links.liveExists(t, e.getId(), f.getId()));
        if (problem != null) {
            throw new HandyFlowException(problem, HttpStatus.CONFLICT, "TP_FUNDING_STATE");
        }
        TrainProvEnrolmentFunding l = TrainProvEnrolmentFunding.link(t, e.getId(), f.getId(), r.fundedFrom(), r.agreementRef(), r.funderReference(), r.note(), by);
        links.save(l);
        return linkView(t, l);
    }

    @Transactional
    public LinkView end(TenantId t, UUID id, EndRequest r, UUID by, LocalDate today) {
        TrainProvEnrolmentFunding l = links.findForTenant(t, id).orElseThrow(() -> new ResourceNotFoundException("TrainProvEnrolmentFunding", id.toString()));
        guard(() -> { l.end(r.endedOn(), r.reason(), today, by); return null; });
        links.save(l);
        return linkView(t, l);
    }

    private void check(TenantId t, FunderRequest r, UUID exceptId) {
        String problem = FundingRules.funderProblem(r.funderType(), r.name(), r.contactEmail(), r.name() != null && funders.nameTaken(t, r.name().trim(), exceptId));
        if (problem != null) {
            throw new HandyFlowException(problem, HttpStatus.BAD_REQUEST, "TP_FUNDER_INVALID");
        }
        if (r.clientId() != null) {
            clients.findActiveById(t, r.clientId()).orElseThrow(() -> new ResourceNotFoundException("TrainProvClient", r.clientId().toString()));
        }
    }

    private FunderView view(TenantId t, TrainProvFunder f) {
        String client = f.getClientId() == null ? null : clients.findActiveById(t, f.getClientId()).map(TrainProvClient::getTradingName).orElse(null);
        return new FunderView(f.getId(), f.getFunderNo(), f.getName(), f.getFunderType(), f.getClientId(), client, f.getContactName(), f.getContactEmail(), f.getContactPhone(),
                f.getNote(), f.getStatus(), f.getRetireReason(), links.activeForFunder(t, f.getId()));
    }

    private LinkView linkView(TenantId t, TrainProvEnrolmentFunding l) {
        TrainProvLearnerEnrolment e = enrolment(t, l.getEnrolmentId());
        TrainProvFunder f = funder(t, l.getFunderId());
        String learner = learners.findForTenant(t, e.getLearnerId()).map(x -> x.getFirstNames() + " " + x.getSurname()).orElse(null);
        return new LinkView(l.getId(), e.getId(), e.getEnrolmentNo(), learner, f.getId(), f.getFunderNo(), f.getName(), f.getFunderType(), l.getFundedFrom(), l.getAgreementRef(),
                l.getFunderReference(), l.getNote(), l.getStatus(), l.getEndedOn(), l.getEndReason());
    }

    private TrainProvFunder funder(TenantId t, UUID id) {
        return funders.findForTenant(t, id).orElseThrow(() -> new ResourceNotFoundException("TrainProvFunder", id.toString()));
    }

    private TrainProvLearnerEnrolment enrolment(TenantId t, UUID id) {
        return enrolments.findForTenant(t, id).orElseThrow(() -> new ResourceNotFoundException("TrainProvLearnerEnrolment", id.toString()));
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static <T> T guard(Supplier<T> work) {
        try {
            return work.get();
        } catch (IllegalStateException e) {
            throw new HandyFlowException(e.getMessage(), HttpStatus.CONFLICT, "TP_FUNDING_STATE");
        } catch (IllegalArgumentException e) {
            throw new HandyFlowException(e.getMessage(), HttpStatus.BAD_REQUEST, "TP_FUNDING_INVALID");
        }
    }
}
