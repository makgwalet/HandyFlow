package za.co.handyflow.platform.fuel.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.fuel.domain.model.FuelSupplierBankAccount;
import za.co.handyflow.platform.fuel.domain.repository.FuelSupplierBankAccountRepository;
import za.co.handyflow.platform.fuel.domain.repository.FuelSupplierRepository;
import za.co.handyflow.platform.fuel.dto.SupplierBankDtos.BankAccountView;
import za.co.handyflow.platform.fuel.dto.SupplierBankDtos.ProposeRequest;
import za.co.handyflow.platform.fuel.dto.SupplierBankDtos.RevealedAccount;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

/**
 * Supplier banking details (B04). Changing where a supplier is paid is the classic route for payment fraud, so: a change is a new
 * record, a different person must verify it (and say how), only a verified account counts, the old one is kept as history, lists
 * show only the last four digits, and seeing the full number is a separate permission that leaves a log line. Account numbers are
 * never written to the log. Fuel does not pay suppliers; Accounts Payable does not read these yet.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FuelSupplierBankService {

    private final FuelSupplierBankAccountRepository accounts;
    private final FuelSupplierRepository suppliers;

    @Transactional(readOnly = true)
    public List<BankAccountView> list(TenantId tenantId, UUID supplierId) {
        requireSupplier(tenantId, supplierId);
        return accounts.findBySupplier(tenantId, supplierId).stream().map(FuelSupplierBankService::view).toList();
    }

    @Transactional
    public BankAccountView propose(TenantId tenantId, UUID supplierId, ProposeRequest req, UUID by) {
        requireSupplier(tenantId, supplierId);
        if (accounts.findPending(tenantId, supplierId).isPresent()) {
            throw new IllegalStateException("A change is already waiting to be verified. Have it decided or withdrawn first.");
        }
        FuelSupplierBankAccount a = FuelSupplierBankAccount.propose(tenantId, supplierId, req.accountHolder(), req.bankName(), req.branchCode(),
                req.accountNumber(), req.accountType(), req.reason(), by);
        var current = accounts.findVerified(tenantId, supplierId);
        if (current.isPresent()) {
            if (current.get().sameDetailsAs(a)) throw new IllegalStateException("These are already the verified details for this supplier.");
            if (req.reason() == null || req.reason().trim().length() < 5) throw new IllegalArgumentException("Say why the bank details are changing (at least 5 characters).");
        }
        log.info("Fuel supplier bank details proposed tenant={} supplier={} account={} by={}", tenantId, supplierId, a.getId(), by);
        return view(accounts.save(a));
    }

    @Transactional
    public BankAccountView verify(TenantId tenantId, UUID supplierId, UUID accountId, String note, UUID by) {
        FuelSupplierBankAccount a = lock(tenantId, supplierId, accountId);
        a.verify(by, note);   // refuses the proposer and a decided record before anything is retired
        accounts.findVerified(tenantId, supplierId).ifPresent(old -> { old.supersede(); accounts.saveAndFlush(old); });
        log.info("Fuel supplier bank details verified tenant={} supplier={} account={} by={}", tenantId, supplierId, accountId, by);
        return view(accounts.save(a));
    }

    @Transactional
    public BankAccountView reject(TenantId tenantId, UUID supplierId, UUID accountId, String note, UUID by) {
        FuelSupplierBankAccount a = lock(tenantId, supplierId, accountId);
        a.reject(by, note);
        log.info("Fuel supplier bank details rejected tenant={} supplier={} account={} by={}", tenantId, supplierId, accountId, by);
        return view(accounts.save(a));
    }

    @Transactional
    public BankAccountView withdraw(TenantId tenantId, UUID supplierId, UUID accountId, UUID by) {
        FuelSupplierBankAccount a = lock(tenantId, supplierId, accountId);
        a.withdraw(by);
        return view(accounts.save(a));
    }

    /** The full account number, for someone with the read permission. Each look is logged with who and which record, never the number. */
    @Transactional(readOnly = true)
    public RevealedAccount reveal(TenantId tenantId, UUID supplierId, UUID accountId, UUID by) {
        FuelSupplierBankAccount a = accounts.findOne(tenantId, supplierId, accountId).orElseThrow(() -> new ResourceNotFoundException("Bank account", accountId.toString()));
        log.info("Fuel supplier bank account number viewed tenant={} supplier={} account={} by={}", tenantId, supplierId, accountId, by);
        return new RevealedAccount(a.getId(), a.getAccountNumber());
    }

    private FuelSupplierBankAccount lock(TenantId tenantId, UUID supplierId, UUID accountId) {
        return accounts.findOneForUpdate(tenantId, supplierId, accountId).orElseThrow(() -> new ResourceNotFoundException("Bank account", accountId.toString()));
    }

    private void requireSupplier(TenantId tenantId, UUID supplierId) {
        suppliers.findActiveById(tenantId, supplierId).orElseThrow(() -> new ResourceNotFoundException("Supplier", supplierId.toString()));
    }

    static BankAccountView view(FuelSupplierBankAccount a) {
        return new BankAccountView(a.getId(), a.getSupplierId(), a.getAccountHolder(), a.getBankName(), a.getBranchCode(), a.maskedNumber(), a.getAccountType(),
                a.getStatus(), a.getReason(), a.getProposedBy(), a.getProposedAt(), a.getDecidedBy(), a.getDecidedAt(), a.getDecisionNote());
    }
}
