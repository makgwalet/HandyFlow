package za.co.handyflow.platform.ap.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.ap.domain.model.ApSupplierBanking;
import za.co.handyflow.platform.ap.domain.repository.ApBillRepository;
import za.co.handyflow.platform.ap.domain.repository.ApSupplierBankingRepository;
import za.co.handyflow.platform.ap.dto.SupplierBankingRequest;
import za.co.handyflow.platform.ap.dto.SupplierBankingResponse;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

/**
 * Separate small service, same modular-separation precedent as
 * ApRecurringBillService — a self-contained CRUD concern kept out of the
 * already-large ApService.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApSupplierBankingService {

    private final ApSupplierBankingRepository bankingRepo;
    private final ApBillRepository            billRepo;
    private final ApDutyChecks                duties;

    @Transactional(readOnly = true)
    public List<SupplierBankingResponse> getAll(TenantId tenantId) {
        return bankingRepo.findAll(tenantId).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<String> getKnownSupplierNames(TenantId tenantId) {
        return billRepo.findDistinctSupplierNames(tenantId);
    }

    @Transactional
    public SupplierBankingResponse create(TenantId tenantId, UUID createdBy, SupplierBankingRequest req) {
        if (bankingRepo.existsByTenantIdAndSupplierNameIgnoreCase(tenantId, req.supplierName())) {
            throw new HandyFlowException(
                    "Banking details for '" + req.supplierName() + "' already exist — edit the existing entry instead",
                    HttpStatus.BAD_REQUEST, "SUPPLIER_BANKING_EXISTS");
        }
        ApSupplierBanking b = ApSupplierBanking.create(tenantId, req.supplierName(),
                req.bankName(), req.accountHolder(), req.accountNumber(), req.branchCode(),
                req.vatNumber(), req.email(), req.notes(), createdBy);
        bankingRepo.save(b);
        log.info("Created supplier banking details for={} tenant={}", req.supplierName(), tenantId);
        return toResponse(b);
    }

    @Transactional
    public SupplierBankingResponse update(TenantId tenantId, UUID id, SupplierBankingRequest req, UUID changedBy) {
        ApSupplierBanking b = bankingRepo.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("SupplierBanking", id.toString()));
        boolean moneyChanges = b.update(req.bankName(), req.accountHolder(), req.accountNumber(),
                req.branchCode(), req.vatNumber(), req.email(), req.notes(), changedBy);
        bankingRepo.save(b);
        if (moneyChanges) {
            // Finance patch F6: the classic payment fraud is a changed account number; nothing is paid to it until checked.
            log.warn("[AP] Bank details CHANGED for supplier={} tenant={} by={} — waiting for a second person's check",
                    b.getSupplierName(), tenantId, changedBy);
        }
        return toResponse(b);
    }

    /**
     * A second person confirms the bank details (finance patch F6), ideally after phoning the supplier on a number they
     * already had. The person who entered them cannot, unless nobody else can.
     */
    @Transactional
    public SupplierBankingResponse verify(TenantId tenantId, UUID id, UUID by, String note) {
        ApSupplierBanking b = bankingRepo.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("SupplierBanking", id.toString()));
        if (b.isVerified())
            throw new HandyFlowException("These bank details are already checked", HttpStatus.CONFLICT, "ALREADY_VERIFIED");
        duties.require(tenantId, ApDutyRules.Duty.VERIFY_OWN_BANKING, "AP_SUPPLIER_BANKING",
                b.getChangedBy(), by, "supplier=" + b.getSupplierName());
        String trimmed = note == null || note.isBlank() ? null : note.trim();
        b.verify(by, trimmed != null && trimmed.length() > 200 ? trimmed.substring(0, 200) : trimmed);
        bankingRepo.save(b);
        log.info("[AP] Bank details checked for supplier={} tenant={} by={}", b.getSupplierName(), tenantId, by);
        return toResponse(b);
    }

    @Transactional
    public void delete(TenantId tenantId, UUID id) {
        ApSupplierBanking b = bankingRepo.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("SupplierBanking", id.toString()));
        bankingRepo.delete(b);
        log.info("Deleted supplier banking details={} tenant={}", id, tenantId);
    }

    private SupplierBankingResponse toResponse(ApSupplierBanking b) {
        return new SupplierBankingResponse(b.getId(), b.getSupplierName(), b.getBankName(),
                b.getAccountHolder(), b.getAccountNumber(), b.getBranchCode(),
                b.getVatNumber(), b.getEmail(), b.getNotes(), b.getCreatedAt(),
                b.isVerified(), b.getChangedBy(), b.getChangedAt(), b.getVerifiedBy(), b.getVerifiedAt(),
                b.getVerificationNote());
    }
}