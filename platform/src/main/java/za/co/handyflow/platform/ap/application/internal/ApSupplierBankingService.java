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
    public SupplierBankingResponse update(TenantId tenantId, UUID id, SupplierBankingRequest req) {
        ApSupplierBanking b = bankingRepo.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("SupplierBanking", id.toString()));
        b.update(req.bankName(), req.accountHolder(), req.accountNumber(),
                req.branchCode(), req.vatNumber(), req.email(), req.notes());
        bankingRepo.save(b);
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
                b.getVatNumber(), b.getEmail(), b.getNotes(), b.getCreatedAt());
    }
}