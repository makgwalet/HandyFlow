package za.co.handyflow.platform.bookkeeping.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.bookkeeping.domain.model.BkJournalEntry;
import za.co.handyflow.platform.bookkeeping.domain.model.BkJournalLine;
import za.co.handyflow.platform.bookkeeping.domain.model.BkPeriod;
import za.co.handyflow.platform.bookkeeping.domain.repository.BkAccountRepository;
import za.co.handyflow.platform.bookkeeping.domain.repository.BkClientRepository;
import za.co.handyflow.platform.bookkeeping.domain.repository.BkJournalEntryRepository;
import za.co.handyflow.platform.bookkeeping.dto.BkJournalEntryResponse;
import za.co.handyflow.platform.bookkeeping.dto.CreateBkJournalEntryRequest;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * A client's own journal — DRAFT -> POSTED lifecycle, mirroring {@code
 * accounting.AccountingService.createJournalEntry}'s exact validation
 * (each line carries exactly one of a debit or a credit amount; the
 * entry must balance) — PLUS a real control {@code accounting}'s own
 * tenant-level journal doesn't have: a new/edited entry is only allowed
 * in an OPEN period, resolved-or-created via {@link BkPeriodService},
 * matching {@code BkPeriod}'s own Javadoc intent.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BkJournalService {

    private final BkJournalEntryRepository journalEntryRepository;
    private final BkClientRepository clientRepository;
    private final BkAccountRepository accountRepository;
    private final BkPeriodService periodService;
    private final BkNumberGenerator numberGenerator;

    @Transactional(readOnly = true)
    public Page<BkJournalEntryResponse> getJournalEntries(TenantId tenantId, UUID clientId, Pageable pageable) {
        return journalEntryRepository.findAllForClient(tenantId, clientId, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public BkJournalEntryResponse getJournalEntry(TenantId tenantId, UUID id) {
        return toResponse(findActive(tenantId, id));
    }

    @Transactional
    public BkJournalEntryResponse createJournal(TenantId tenantId, UUID createdBy, CreateBkJournalEntryRequest req) {
        clientRepository.findActiveById(tenantId, req.clientId())
                .orElseThrow(() -> new ResourceNotFoundException("BkClient", req.clientId().toString()));

        BkPeriod period = periodService.resolveOrCreate(tenantId, req.clientId(), req.entryDate());
        if (!period.isOpen()) {
            throw new IllegalStateException("Cannot create a journal entry — the " + period.getPeriodYear() + "/"
                    + period.getPeriodMonth() + " period for this client is CLOSED");
        }

        String entryNumber = numberGenerator.nextEntryNumber(tenantId);
        BkJournalEntry entry = BkJournalEntry.create(tenantId, req.clientId(), period.getId(), entryNumber,
                req.entryDate(), req.description(), req.reference(), req.entryType(), createdBy);

        int sortOrder = 0;
        for (CreateBkJournalEntryRequest.JournalLineRequest lineReq : req.lines()) {
            accountRepository.findActiveById(tenantId, lineReq.accountId())
                    .filter(a -> a.getClientId().equals(req.clientId()))
                    .orElseThrow(() -> new ResourceNotFoundException("BkAccount", lineReq.accountId().toString()));

            BigDecimal debit = lineReq.debitAmount() != null ? lineReq.debitAmount() : BigDecimal.ZERO;
            BigDecimal credit = lineReq.creditAmount() != null ? lineReq.creditAmount() : BigDecimal.ZERO;
            if (debit.signum() != 0 && credit.signum() != 0) {
                throw new IllegalArgumentException("A journal line cannot have both a debit and a credit amount");
            }
            if (debit.signum() == 0 && credit.signum() == 0) {
                throw new IllegalArgumentException("A journal line must have either a debit or a credit amount");
            }

            BkJournalLine line = debit.signum() != 0
                    ? BkJournalLine.debit(entry.getId(), lineReq.accountId(), debit, lineReq.description(), sortOrder)
                    : BkJournalLine.credit(entry.getId(), lineReq.accountId(), credit, lineReq.description(), sortOrder);
            entry.addLine(line);
            sortOrder++;
        }

        if (!entry.isBalanced()) {
            throw new IllegalStateException("Journal does not balance — total debit " + entry.getTotalDebit()
                    + " does not equal total credit " + entry.getTotalCredit());
        }

        journalEntryRepository.save(entry);
        log.info("Bookkeeping journal entry created number={} client={} tenant={}", entryNumber, req.clientId(), tenantId);
        return toResponse(entry);
    }

    @Transactional
    public BkJournalEntryResponse postJournalEntry(TenantId tenantId, UUID id) {
        BkJournalEntry entry = findActive(tenantId, id);
        BkPeriod period = periodService.findActive(tenantId, entry.getPeriodId());
        if (!period.isOpen()) {
            throw new IllegalStateException("Cannot post — the period this entry belongs to is CLOSED");
        }
        entry.post();
        journalEntryRepository.save(entry);
        log.info("Bookkeeping journal entry posted number={} tenant={}", entry.getEntryNumber(), tenantId);
        return toResponse(entry);
    }

    BkJournalEntry findActive(TenantId tenantId, UUID id) {
        return journalEntryRepository.findActiveById(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("BkJournalEntry", id.toString()));
    }

    private BkJournalEntryResponse toResponse(BkJournalEntry e) {
        List<BkJournalEntryResponse.JournalLineResponse> lines = e.getLines().stream()
                .map(l -> new BkJournalEntryResponse.JournalLineResponse(
                        l.getId(), l.getAccountId(), l.getDescription(), l.getDebitAmount(), l.getCreditAmount(), l.getSortOrder()))
                .toList();
        return new BkJournalEntryResponse(e.getId(), e.getClientId(), e.getPeriodId(), e.getEntryNumber(), e.getEntryDate(),
                e.getDescription(), e.getReference(), e.getEntryType(), e.getStatus(), e.getTotalDebit(), e.getTotalCredit(),
                e.getCreatedBy(), e.getCreatedAt(), e.getPostedAt(), lines);
    }
}
