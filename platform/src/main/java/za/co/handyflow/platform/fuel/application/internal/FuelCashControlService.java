package za.co.handyflow.platform.fuel.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.fuel.domain.model.FuelSettings;
import za.co.handyflow.platform.fuel.domain.model.FuelShift;
import za.co.handyflow.platform.fuel.domain.model.FuelShiftAdjustment;
import za.co.handyflow.platform.fuel.domain.repository.FuelShiftAdjustmentRepository;
import za.co.handyflow.platform.fuel.domain.repository.FuelShiftRepository;
import za.co.handyflow.platform.fuel.dto.ForecourtDtos.AdjustmentRequest;
import za.co.handyflow.platform.fuel.dto.ForecourtDtos.AdjustmentView;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Retail cash controls: refunds, voids and discounts on an open shift (decided by a supervisor, never by the person who recorded them),
 * the supervisor's review of an out-of-tolerance shift, and the tolerance rules the shift close uses.
 * Nothing here posts stock or money: an approved adjustment only lowers the takings the shift is expected to hold.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FuelCashControlService {

    /** What the shift close needs to know. */
    public record CashPolicy(BigDecimal tolerance, boolean requireSeparateCloser) {}

    private final FuelShiftAdjustmentRepository adjustments;
    private final FuelShiftRepository shifts;
    private final FuelSettingsService settings;

    @Transactional(readOnly = true)
    public CashPolicy policy(TenantId tenantId) {
        FuelSettings s = settings.current(tenantId);
        return new CashPolicy(s.getCashVarianceTolerance(), s.isRequireSeparateCloser());
    }

    @Transactional(readOnly = true)
    public long pendingCount(TenantId tenantId, UUID shiftId) { return adjustments.countPending(tenantId, shiftId); }

    @Transactional(readOnly = true)
    public BigDecimal approvedTotal(TenantId tenantId, UUID shiftId) {
        BigDecimal v = adjustments.sumApproved(tenantId, shiftId);
        return v == null ? BigDecimal.ZERO : v;
    }

    @Transactional(readOnly = true)
    public List<AdjustmentView> list(TenantId tenantId, UUID shiftId) {
        shift(tenantId, shiftId);
        return adjustments.findByShift(tenantId, shiftId).stream().map(FuelCashControlService::view).toList();
    }

    @Transactional
    public AdjustmentView record(TenantId tenantId, UUID shiftId, AdjustmentRequest req, UUID by) {
        FuelShift s = shift(tenantId, shiftId);
        if (!s.isOpen()) throw new IllegalStateException("Refunds, voids and discounts can only be recorded while the shift is open.");
        return view(adjustments.save(FuelShiftAdjustment.record(tenantId, shiftId, req.kind(), req.amount(), req.reason(), by)));
    }

    @Transactional
    public AdjustmentView approve(TenantId tenantId, UUID id, String note, UUID by) {
        FuelShiftAdjustment a = lock(tenantId, id);
        if (!shift(tenantId, a.getShiftId()).isOpen()) throw new IllegalStateException("The shift is no longer open, so this can no longer be decided.");
        a.approve(by, note);
        log.info("Forecourt {} approved id={} amount={} by={}", a.getKind(), id, a.getAmount(), by);
        return view(adjustments.save(a));
    }

    @Transactional
    public AdjustmentView reject(TenantId tenantId, UUID id, String note, UUID by) {
        FuelShiftAdjustment a = lock(tenantId, id);
        a.reject(by, note);
        return view(adjustments.save(a));
    }

    @Transactional
    public AdjustmentView withdraw(TenantId tenantId, UUID id, UUID by) {
        FuelShiftAdjustment a = lock(tenantId, id);
        a.withdraw(by);
        return view(adjustments.save(a));
    }

    /** Supervisor sign-off of an out-of-tolerance close. Locks the shift row so two supervisors cannot both review it. */
    @Transactional
    public void reviewShift(TenantId tenantId, UUID shiftId, String note, UUID by) {
        FuelShift s = shift(tenantId, shiftId);
        s.review(by, note);
        shifts.save(s);
        log.info("Forecourt shift reviewed {} by={}", s.getShiftNumber(), by);
    }

    private FuelShift shift(TenantId t, UUID id) { return shifts.findByTenantAndId(t, id).orElseThrow(() -> new ResourceNotFoundException("Shift", id.toString())); }
    private FuelShiftAdjustment lock(TenantId t, UUID id) { return adjustments.findByIdForUpdate(t, id).orElseThrow(() -> new ResourceNotFoundException("Adjustment", id.toString())); }

    private static AdjustmentView view(FuelShiftAdjustment a) {
        return new AdjustmentView(a.getId(), a.getShiftId(), a.getKind(), a.getAmount(), a.getReason(), a.getStatus(), a.getRecordedBy(),
                a.getRecordedAt(), a.getDecidedBy(), a.getDecidedAt(), a.getDecisionNote());
    }
}
