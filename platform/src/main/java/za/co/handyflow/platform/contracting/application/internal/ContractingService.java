package za.co.handyflow.platform.contracting.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.contracting.domain.model.*;
import za.co.handyflow.platform.contracting.domain.repository.*;
import za.co.handyflow.platform.contracting.dto.*;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContractingService {

    private final ContractRepository        contractRepo;
    private final ContractTemplateRepository templateRepo;
    private final ContractPartyRepository   partyRepo;
    private final ContractSignatureRepository signatureRepo;
    private final ContractNumberGenerator   numberGen;
    private final ContractTemplateSeeder    templateSeeder;
    private final OtpService               otpService;
    private final ContractPdfGenerator pdfGenerator;
    ContractVariableResolver variableResolver;

    // ── Templates ─────────────────────────────────────────────────────────────

    @Transactional
    public List<TemplateResponse> getTemplates(TenantId tenantId) {
        templateSeeder.seedForTenant(tenantId);
        return templateRepo.findAllActive(tenantId)
                .stream().map(this::toTemplateResponse).toList();
    }

    @Transactional
    public TemplateResponse createTemplate(TenantId tenantId, CreateTemplateRequest req) {
        ContractTemplate t = ContractTemplate.create(tenantId, req.name(),
                req.contractType(), req.description(), req.bodyTemplate(),
                req.variables(), false);
        templateRepo.save(t);
        return toTemplateResponse(t);
    }

    // ── Contracts ─────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<ContractResponse> getContracts(TenantId tenantId, String status,
                                               String type, Pageable pageable) {
        return contractRepo.findAllActive(tenantId, status, type, pageable)
                .map(this::toContractResponse);
    }

    @Transactional(readOnly = true)
    public ContractResponse getContract(TenantId tenantId, UUID id) {
        return contractRepo.findActiveById(tenantId, id)
                .map(this::toContractResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Contract", id.toString()));
    }

    @Transactional
    public ContractResponse createContract(TenantId tenantId, CreateContractRequest req,
                                           UUID createdByUserId) {
        templateSeeder.seedForTenant(tenantId);

        String body = req.body();
        if (body == null && req.templateId() != null) {
            ContractTemplate tmpl = templateRepo.findActiveById(tenantId, req.templateId())
                    .orElseThrow(() -> new ResourceNotFoundException("Template",
                            req.templateId().toString()));
            body = tmpl.getBodyTemplate();
        }
        if (body == null) body = "<p>Contract body — edit to add content.</p>";

        String number  = numberGen.next(tenantId);
        Contract contract = Contract.create(tenantId, number, req.title(),
                req.contractType(), body, req.templateId(), createdByUserId);

        if (req.startDate() != null || req.endDate() != null)
            contract.setDates(req.startDate(), req.endDate());
        if (req.valueAmount() != null)
            contract.setValueAmount(req.valueAmount());
        if (req.notes() != null)
            contract.setNotes(req.notes());
        if (req.variables() != null && !req.variables().isEmpty()) {
            body = variableResolver.resolve(body, req.variables());
        }

        contractRepo.save(contract);
        log.info("Created contract={} tenant={}", number, tenantId);
        return toContractResponse(contract);
    }

    @Transactional
    public ContractResponse submitForReview(TenantId tenantId, UUID id) {
        Contract c = findActive(tenantId, id);
        c.submitForReview();
        contractRepo.save(c);
        return toContractResponse(c);
    }

    @Transactional
    public ContractResponse sendForSigning(TenantId tenantId, UUID id) {
        Contract c = findActive(tenantId, id);
        if (c.getParties().isEmpty())
            throw new IllegalStateException("Add at least one party before sending for signing");
        c.send();
        contractRepo.save(c);
        // TODO: send signing links via email/SMS to each party
        log.info("Contract {} sent for signing — {} parties", c.getContractNumber(), c.getParties().size());
        return toContractResponse(c);
    }

    @Transactional
    public ContractResponse terminate(TenantId tenantId, UUID id, String reason) {
        Contract c = findActive(tenantId, id);
        c.terminate(reason);
        contractRepo.save(c);
        log.info("Contract {} terminated: {}", c.getContractNumber(), reason);
        return toContractResponse(c);
    }

    // ── Parties ───────────────────────────────────────────────────────────────

    @Transactional
    public PartyResponse addParty(TenantId tenantId, UUID contractId, AddPartyRequest req) {
        findActive(tenantId, contractId);
        ContractParty party = ContractParty.create(tenantId, contractId,
                req.partyType(), req.partyRole(), req.fullName(),
                req.email(), req.phone(), req.companyName(),
                req.signingOrder() != null ? req.signingOrder() : 1);
        partyRepo.save(party);
        return toPartyResponse(party);
    }

    @Transactional
    public PartyResponse requestOtp(TenantId tenantId, UUID contractId, UUID partyId) {
        Contract c = findActive(tenantId, contractId);
        if (!"SENT".equals(c.getStatus()))
            throw new IllegalStateException("Contract must be SENT before OTP can be requested");
        ContractParty party = partyRepo.findByTenantAndId(tenantId, partyId)
                .orElseThrow(() -> new ResourceNotFoundException("Party", partyId.toString()));
        if (party.getPhone() == null)
            throw new IllegalStateException("Party has no phone number — cannot send OTP");

        String otp = otpService.generateAndStore(partyId.toString());
        party.markOtpSent();
        partyRepo.save(party);

        // TODO: Send SMS via Clickatell: "Your HandyFlow signing OTP is: " + otp
        log.info("OTP for party={} phone={}...{} contract={}",
                partyId, party.getPhone().substring(0, 3),
                party.getPhone().substring(party.getPhone().length() - 2),
                contractId);

        return toPartyResponse(party);
    }

    @Transactional
    public ContractResponse signContract(TenantId tenantId, UUID contractId,
                                         UUID partyId, SignContractRequest req,
                                         String ipAddress, String userAgent) {
        Contract c = findActive(tenantId, contractId);
        ContractParty party = partyRepo.findByTenantAndId(tenantId, partyId)
                .orElseThrow(() -> new ResourceNotFoundException("Party", partyId.toString()));

        if (!otpService.verify(partyId.toString(), req.otpCode()))
            throw new IllegalArgumentException("Invalid or expired OTP");

        String phone = party.getPhone() != null ? party.getPhone() : "";
        String last4 = phone.length() >= 4 ? phone.substring(phone.length() - 4) : phone;
        String otpHash = otpService.hashOtp(req.otpCode());

        ContractSignature sig = ContractSignature.create(tenantId, contractId, partyId,
                otpHash, last4, ipAddress, userAgent, req.signatureData());
        signatureRepo.save(sig);

        party.markSigned(ipAddress, userAgent);
        partyRepo.save(party);

        if (c.allPartiesSigned()) {
            c.markSigned();
            contractRepo.save(c);
            log.info("Contract {} fully signed by all parties", c.getContractNumber());
        }

        return toContractResponse(c);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Contract findActive(TenantId tenantId, UUID id) {
        return contractRepo.findActiveById(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Contract", id.toString()));
    }

    private void setField(Object obj, String field, Object value) {
        try {
            java.lang.reflect.Field f = obj.getClass().getDeclaredField(field);
            f.setAccessible(true);
            f.set(obj, value);
        } catch (Exception e) {
            log.warn("Could not set field {}: {}", field, e.getMessage());
        }
    }

    // ── Mappers ───────────────────────────────────────────────────────────────

    private ContractResponse toContractResponse(Contract c) {
        List<PartyResponse> parties = c.getParties().stream()
                .map(this::toPartyResponse).toList();
        return new ContractResponse(c.getId(), c.getContractNumber(), c.getTitle(),
                c.getContractType(), c.getStatus(), c.getValueAmount(), c.getCurrency(),
                c.getStartDate(), c.getEndDate(), c.isAutoRenew(), c.getNotes(),
                c.getSentAt(), c.getSignedAt(), c.getTerminatedAt(), c.getTerminationReason(),
                parties, c.getCreatedAt());
    }

    private PartyResponse toPartyResponse(ContractParty p) {
        return new PartyResponse(p.getId(), p.getPartyType(), p.getPartyRole(),
                p.getFullName(), p.getEmail(), p.getPhone(), p.getCompanyName(),
                p.getSigningOrder(), p.getSigningStatus(), p.getSignedAt(), p.getOtpSentAt());
    }

    private TemplateResponse toTemplateResponse(ContractTemplate t) {
        return new TemplateResponse(t.getId(), t.getName(), t.getContractType(),
                t.getDescription(), t.getBodyTemplate(), t.getVariables(),
                t.isSystem(), t.getCreatedAt());
    }

    @Transactional(readOnly = true)
    public byte[] generatePdf(TenantId tenantId, UUID contractId) {
        Contract contract = findActive(tenantId, contractId);
        List<ContractSignature> signatures = signatureRepo.findByContract(contractId);
        return pdfGenerator.generate(contract, signatures,
                "HandyFlow Tenant", null);  // TODO: pull real tenant name from identity module
    }
}