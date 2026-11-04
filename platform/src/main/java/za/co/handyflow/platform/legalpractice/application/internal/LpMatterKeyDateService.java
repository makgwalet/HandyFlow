package za.co.handyflow.platform.legalpractice.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.legalpractice.domain.model.LpMatterKeyDate;
import za.co.handyflow.platform.legalpractice.domain.repository.LpMatterKeyDateRepository;
import za.co.handyflow.platform.legalpractice.dto.CreateLpMatterKeyDateRequest;
import za.co.handyflow.platform.legalpractice.domain.model.LpMatter;
import za.co.handyflow.platform.legalpractice.dto.LpFirmKeyDateResponse;
import za.co.handyflow.platform.legalpractice.dto.LpMatterKeyDateResponse;
import za.co.handyflow.platform.legalpractice.dto.UpdateLpMatterKeyDateRequest;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/** CRUD plus the complete/markMissed/acknowledge lifecycle for per-matter court/filing/prescription deadlines. */
@Slf4j
@Service
@RequiredArgsConstructor
public class LpMatterKeyDateService {

    private final LpMatterKeyDateRepository keyDateRepo;

    @Transactional(readOnly = true)
    public List<LpMatterKeyDateResponse> listForMatter(TenantId tenantId, UUID matterId) {
        return keyDateRepo.findAllForMatter(tenantId, matterId).stream().map(this::toResponse).toList();
    }

    /** Key dates across all the firm's matters in [from, to]; at most 400 days at a time. */
    @Transactional(readOnly = true)
    public List<LpFirmKeyDateResponse> listForFirm(TenantId tenantId, LocalDate from, LocalDate to) {
        if (from == null || to == null) throw new IllegalArgumentException("from and to are required");
        if (to.isBefore(from)) throw new IllegalArgumentException("to cannot be before from");
        if (ChronoUnit.DAYS.between(from, to) > 400) throw new IllegalArgumentException("Choose a range of at most 400 days");
        return keyDateRepo.findInRangeWithMatter(tenantId, from, to).stream().map(row -> {
            LpMatterKeyDate k = (LpMatterKeyDate) row[0];
            LpMatter m = (LpMatter) row[1];
            return new LpFirmKeyDateResponse(k.getId(), k.getMatterId(), m.getMatterNumber(), m.getMatterName(), m.getStatus(),
                    k.getDateType(), k.getDueDate(), k.getDescription(), k.isAcknowledged(), k.getStatus());
        }).toList();
    }

    @Transactional
    public LpMatterKeyDateResponse createKeyDate(TenantId tenantId, UUID matterId, CreateLpMatterKeyDateRequest req) {
        LpMatterKeyDate keyDate = LpMatterKeyDate.create(tenantId, matterId, req.dateType(),
                req.dueDate(), req.description(), req.notes());
        keyDateRepo.save(keyDate);
        return toResponse(keyDate);
    }

    @Transactional
    public LpMatterKeyDateResponse updateKeyDate(TenantId tenantId, UUID id, UpdateLpMatterKeyDateRequest req) {
        LpMatterKeyDate keyDate = findOwn(tenantId, id);
        keyDate.update(req.dateType(), req.dueDate(), req.description(), req.notes());
        keyDateRepo.save(keyDate);
        return toResponse(keyDate);
    }

    @Transactional
    public LpMatterKeyDateResponse complete(TenantId tenantId, UUID id) {
        LpMatterKeyDate keyDate = findOwn(tenantId, id);
        keyDate.complete();
        keyDateRepo.save(keyDate);
        return toResponse(keyDate);
    }

    @Transactional
    public LpMatterKeyDateResponse markMissed(TenantId tenantId, UUID id) {
        LpMatterKeyDate keyDate = findOwn(tenantId, id);
        keyDate.markMissed();
        keyDateRepo.save(keyDate);
        return toResponse(keyDate);
    }

    @Transactional
    public LpMatterKeyDateResponse acknowledge(TenantId tenantId, UUID id) {
        LpMatterKeyDate keyDate = findOwn(tenantId, id);
        keyDate.acknowledge();
        keyDateRepo.save(keyDate);
        return toResponse(keyDate);
    }

    private LpMatterKeyDate findOwn(TenantId tenantId, UUID id) {
        return keyDateRepo.findActiveById(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("LpMatterKeyDate", id.toString()));
    }

    private LpMatterKeyDateResponse toResponse(LpMatterKeyDate k) {
        return new LpMatterKeyDateResponse(k.getId(), k.getMatterId(), k.getDateType(), k.getDueDate(),
                k.getDescription(), k.isAcknowledged(), k.getStatus(), k.getNotes(), k.getCreatedAt(), k.getUpdatedAt());
    }
}
