package za.co.handyflow.platform.trainingprovider.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.model.*;
import za.co.handyflow.platform.trainingprovider.domain.repository.*;
import za.co.handyflow.platform.trainingprovider.domain.rules.PriceRules;
import za.co.handyflow.platform.trainingprovider.dto.PriceDtos.*;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Programme price books. Every fee is entered by the provider with a note of where it comes from; nothing is seeded. A line is never edited and
 * never deleted: a changed fee is a new line, and the old one is withdrawn with a reason. Two live lines for the same fee may not cover the same
 * day. Invoicing only shows a line as a suggestion: nothing is billed from here.
 */
@Service
@RequiredArgsConstructor
public class TrainProvPriceService {

    private final TrainProvPriceLineRepository lines;
    private final TrainProvProgrammeVersionRepository versions;
    private final TrainProvFunderRepository funders;
    private final TrainProvLearnerEnrolmentRepository enrolments;
    private final TrainProvEnrolmentFundingRepository funding;

    public List<PriceView> list(TenantId t, UUID versionId, String status, LocalDate today) {
        version(t, versionId);
        return lines.forVersion(t, versionId, blank(status)).stream().<PriceView>map(p -> view(t, p, today)).toList();
    }

    @Transactional
    public PriceView create(TenantId t, PriceRequest r, UUID by, LocalDate today) {
        version(t, r.programmeVersionId());
        if (r.funderId() != null) {
            funders.findForTenant(t, r.funderId()).orElseThrow(() -> new ResourceNotFoundException("TrainProvFunder", r.funderId().toString()));
        }
        TrainProvPriceLine p = guard(() -> TrainProvPriceLine.create(t, r.programmeVersionId(), r.funderId(), r.feeType(), r.description(), r.amount(), r.includesVat(),
                r.validFrom(), r.validTo(), r.sourceNote(), by));
        lines.lock("trainprov-price:" + t.getValue() + ":" + r.programmeVersionId());
        for (TrainProvPriceLine other : lines.liveForSameFee(t, r.programmeVersionId(), r.funderId(), r.feeType())) {
            if (PriceRules.sameFee(p.getFeeType(), p.getDescription(), other.getFeeType(), other.getDescription())
                    && PriceRules.overlaps(p.getValidFrom(), p.getValidTo(), other.getValidFrom(), other.getValidTo())) {
                throw new HandyFlowException("A fee for this already covers part of that period (from " + other.getValidFrom() + (other.getValidTo() == null ? ", no end date" : " to " + other.getValidTo())
                        + "). Withdraw it with a reason first, or start the new fee after it ends", HttpStatus.CONFLICT, "TP_PRICE_OVERLAP");
            }
        }
        lines.save(p);
        return view(t, p, today);
    }

    @Transactional
    public PriceView withdraw(TenantId t, UUID id, String reason, UUID by, LocalDate today) {
        TrainProvPriceLine p = lines.findForTenant(t, id).orElseThrow(() -> new ResourceNotFoundException("TrainProvPriceLine", id.toString()));
        guard(() -> { p.withdraw(reason, by); return null; });
        lines.save(p);
        return view(t, p, today);
    }

    /**
     * The price book's lines in force on a day for an enrolment's programme version: lines for no particular funder, and lines for a funder
     * that is paying for the enrolment. A suggestion only: the person confirms it, or types another amount with a reason.
     */
    public Suggestion suggest(TenantId t, UUID enrolmentId, LocalDate on) {
        TrainProvLearnerEnrolment e = enrolments.findForTenant(t, enrolmentId).orElseThrow(() -> new ResourceNotFoundException("TrainProvLearnerEnrolment", enrolmentId.toString()));
        String note = "A suggestion from the price book, not an invoice. Confirm it, or enter another amount with a reason.";
        if (e.getProgrammeVersionId() == null) {
            return new Suggestion(enrolmentId, on, "This enrolment is not on a programme version, so there is nothing to suggest.", List.of());
        }
        Set<UUID> payingFunders = new HashSet<>();
        funding.search(t, enrolmentId, null, "ACTIVE").forEach(l -> payingFunders.add(l.getFunderId()));
        List<SuggestionRow> rows = lines.inForce(t, e.getProgrammeVersionId(), on).stream()
                .filter(p -> p.getFunderId() == null || payingFunders.contains(p.getFunderId()))
                .<SuggestionRow>map(p -> new SuggestionRow(p.getId(), p.getFeeType(), p.getDescription(), p.getAmount(), p.isIncludesVat(), p.getFunderId() == null,
                        p.getFunderId() == null ? null : funderName(t, p.getFunderId()), p.getSourceNote())).toList();
        if (rows.isEmpty()) {
            note = "The price book has no fee in force on " + on + " for this enrolment. Nothing is suggested.";
        }
        return new Suggestion(enrolmentId, on, note, rows);
    }

    private PriceView view(TenantId t, TrainProvPriceLine p, LocalDate today) {
        return new PriceView(p.getId(), p.getProgrammeVersionId(), p.getFunderId(), p.getFunderId() == null ? null : funderName(t, p.getFunderId()), p.getFeeType(), p.getDescription(),
                p.getAmount(), p.isIncludesVat(), p.getValidFrom(), p.getValidTo(), p.getSourceNote(), p.getStatus(), p.getWithdrawnReason(),
                "ACTIVE".equals(p.getStatus()) && PriceRules.inForce(p.getValidFrom(), p.getValidTo(), today), p.getCreatedAt());
    }

    private String funderName(TenantId t, UUID id) {
        return funders.findForTenant(t, id).map(TrainProvFunder::getName).orElse(null);
    }

    private TrainProvProgrammeVersion version(TenantId t, UUID id) {
        return versions.findForTenant(t, id).orElseThrow(() -> new ResourceNotFoundException("TrainProvProgrammeVersion", id.toString()));
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static <T> T guard(Supplier<T> work) {
        try {
            return work.get();
        } catch (IllegalStateException e) {
            throw new HandyFlowException(e.getMessage(), HttpStatus.CONFLICT, "TP_PRICE_STATE");
        } catch (IllegalArgumentException e) {
            throw new HandyFlowException(e.getMessage(), HttpStatus.BAD_REQUEST, "TP_PRICE_INVALID");
        }
    }
}
