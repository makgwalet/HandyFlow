package za.co.handyflow.platform.bookkeeping.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.bookkeeping.domain.model.BkAccount;
import za.co.handyflow.platform.bookkeeping.domain.model.BkBankAccount;
import za.co.handyflow.platform.bookkeeping.domain.repository.BkAccountRepository;
import za.co.handyflow.platform.bookkeeping.domain.repository.BkBankAccountRepository;
import za.co.handyflow.platform.bookkeeping.domain.repository.BkClientRepository;
import za.co.handyflow.platform.bookkeeping.dto.BkBankAccountResponse;
import za.co.handyflow.platform.bookkeeping.dto.CreateBkBankAccountRequest;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class BkBankAccountService {

    private final BkBankAccountRepository bankAccountRepository;
    private final BkClientRepository clientRepository;
    private final BkAccountRepository accountRepository;

    @Transactional(readOnly = true)
    public Page<BkBankAccountResponse> getBankAccounts(TenantId tenantId, UUID clientId, Pageable pageable) {
        return bankAccountRepository.findAllForClient(tenantId, clientId, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public BkBankAccountResponse getBankAccount(TenantId tenantId, UUID id) {
        return toResponse(findActive(tenantId, id));
    }

    @Transactional
    public BkBankAccountResponse createBankAccount(TenantId tenantId, CreateBkBankAccountRequest req) {
        clientRepository.findActiveById(tenantId, req.clientId())
                .orElseThrow(() -> new ResourceNotFoundException("BkClient", req.clientId().toString()));

        BkBankAccount bankAccount = BkBankAccount.create(tenantId, req.clientId(), req.bankName(), req.accountName(),
                req.accountNumber(), req.branchCode(), req.accountType());
        bankAccountRepository.save(bankAccount);
        log.info("Bookkeeping bank account created id={} client={} tenant={}", bankAccount.getId(), req.clientId(), tenantId);
        return toResponse(bankAccount);
    }

    /** Links this bank account to a line in the client's own chart of accounts — required before match-candidates can be suggested. */
    @Transactional
    public BkBankAccountResponse linkAccount(TenantId tenantId, UUID id, UUID accountId) {
        BkBankAccount bankAccount = findActive(tenantId, id);
        BkAccount account = accountRepository.findActiveById(tenantId, accountId)
                .filter(a -> a.getClientId().equals(bankAccount.getClientId()))
                .orElseThrow(() -> new ResourceNotFoundException("BkAccount", accountId.toString()));
        bankAccount.linkAccount(account.getId());
        bankAccountRepository.save(bankAccount);
        return toResponse(bankAccount);
    }

    @Transactional
    public void deleteBankAccount(TenantId tenantId, UUID id) {
        BkBankAccount bankAccount = findActive(tenantId, id);
        bankAccount.softDelete();
        bankAccountRepository.save(bankAccount);
    }

    BkBankAccount findActive(TenantId tenantId, UUID id) {
        return bankAccountRepository.findActiveById(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("BkBankAccount", id.toString()));
    }

    private BkBankAccountResponse toResponse(BkBankAccount b) {
        return new BkBankAccountResponse(b.getId(), b.getClientId(), b.getAccountId(), b.getBankName(), b.getAccountName(),
                b.getAccountNumber(), b.getBranchCode(), b.getAccountType(), b.getCurrency(), b.getCurrentBalance(),
                b.isActive(), b.getCreatedAt());
    }
}
