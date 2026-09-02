package za.co.handyflow.platform.bookkeeping.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.bookkeeping.domain.model.BkPeriod;
import za.co.handyflow.platform.bookkeeping.domain.repository.BkPeriodRepository;
import za.co.handyflow.platform.bookkeeping.dto.BkPeriodResponse;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.UUID;

/**
 * A client's monthly bookkeeping period — materializes on first use via
 * {@link #resolveOrCreate}, mirroring {@code AccountantService}'s own
 * resolve-or-create logic for {@code AccPeriod} (per {@code BkPeriod}'s
 * own Javadoc): the caller never pre-creates periods, {@code
 * BkJournalService} calls this to find-or-create the OPEN period for a
 * given entry date before it will let a journal entry be created there.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BkPeriodService {

    private final BkPeriodRepository periodRepository;

    @Transactional(readOnly = true)
    public Page<BkPeriodResponse> getPeriods(TenantId tenantId, UUID clientId, Pageable pageable) {
        return periodRepository.findAllForClient(tenantId, clientId, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public BkPeriodResponse getPeriod(TenantId tenantId, UUID id) {
        return toResponse(findActive(tenantId, id));
    }

    /** Find-or-create the period covering {@code date} for this client — package-visible for {@code BkJournalService}. */
    @Transactional
    BkPeriod resolveOrCreate(TenantId tenantId, UUID clientId, LocalDate date) {
        int year = date.getYear();
        int month = date.getMonthValue();
        return periodRepository.findByClientAndYearMonth(tenantId, clientId, year, month)
                .orElseGet(() -> {
                    BkPeriod created = periodRepository.save(BkPeriod.create(tenantId, clientId, year, month));
                    log.info("Bookkeeping period created client={} tenant={} {}/{}", clientId, tenantId.getValue(), year, month);
                    return created;
                });
    }

    @Transactional
    public BkPeriodResponse close(TenantId tenantId, UUID id, UUID closedBy) {
        BkPeriod period = findActive(tenantId, id);
        period.close(closedBy);
        periodRepository.save(period);
        log.info("Bookkeeping period closed id={} tenant={}", id, tenantId);
        return toResponse(period);
    }

    @Transactional
    public BkPeriodResponse reopen(TenantId tenantId, UUID id) {
        BkPeriod period = findActive(tenantId, id);
        period.reopen();
        periodRepository.save(period);
        log.info("Bookkeeping period reopened id={} tenant={}", id, tenantId);
        return toResponse(period);
    }

    BkPeriod findActive(TenantId tenantId, UUID id) {
        return periodRepository.findActiveById(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("BkPeriod", id.toString()));
    }

    private BkPeriodResponse toResponse(BkPeriod p) {
        return new BkPeriodResponse(p.getId(), p.getClientId(), p.getPeriodYear(), p.getPeriodMonth(),
                p.getStatus(), p.getClosedAt(), p.getClosedBy(), p.getCreatedAt());
    }
}
