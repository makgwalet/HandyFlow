package za.co.handyflow.platform.complianceservices.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.complianceservices.domain.model.ClientProfileRules;
import za.co.handyflow.platform.complianceservices.domain.model.ComplianceClient;
import za.co.handyflow.platform.complianceservices.domain.model.ComplianceClientFieldSource;
import za.co.handyflow.platform.complianceservices.domain.repository.ComplianceClientFieldSourceRepository;
import za.co.handyflow.platform.complianceservices.domain.repository.ComplianceClientRepository;
import za.co.handyflow.platform.complianceservices.dto.ClientProfileDtos.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

/** A client's profile and the record of where each fact on it came from. */
@Service
@RequiredArgsConstructor
public class ComplianceClientProfileService {

    private final ComplianceClientRepository clientRepository;
    private final ComplianceClientFieldSourceRepository sourceRepository;

    @Transactional(readOnly = true)
    public ProfileResponse get(TenantId tenantId, UUID clientId) {
        return toResponse(tenantId, find(tenantId, clientId));
    }

    @Transactional
    public ProfileResponse save(TenantId tenantId, UUID clientId, SaveProfileRequest r, UUID userId) {
        ComplianceClient client = find(tenantId, clientId);
        String reg;
        try {
            reg = ClientProfileRules.registrationNumber(r.registrationNumber());
        } catch (IllegalArgumentException e) {
            throw bad(e);
        }
        if (reg != null) {
            clientRepository.findByRegistrationNumber(tenantId, reg).ifPresent(other -> {
                if (!other.getId().equals(clientId)) {
                    throw new HandyFlowException("Registration number " + reg + " is already on file for \"" + other.getName() + "\"",
                            HttpStatus.CONFLICT, "CLIENT_REGISTRATION_NUMBER_DUPLICATE");
                }
            });
        }
        List<String> changed;
        try {
            changed = client.updateProfile(new ComplianceClient.Profile(r.legalName(), r.tradingName(), r.registrationNumber(),
                    r.entityType(), r.taxNumber(), r.vatNumber(), r.registeredAddress(), r.postalAddress(), r.industry(),
                    r.employeeCount(), r.turnoverBand(), r.riskRating(), r.onboardingStatus()), userId);
        } catch (IllegalArgumentException e) {
            throw bad(e);
        }
        clientRepository.save(client);
        for (String field : changed) {
            var existing = sourceRepository.findForField(tenantId, clientId, field);
            if (!hasValue(client, field)) {
                existing.ifPresent(sourceRepository::delete);
            } else if (existing.isPresent()) {
                existing.get().restate("STAFF_ENTERED", null, null, null, userId);   // a changed value is not confirmed
                sourceRepository.save(existing.get());
            } else {
                sourceRepository.save(ComplianceClientFieldSource.record(tenantId, clientId, field, "STAFF_ENTERED", null, null, null, userId));
            }
        }
        return toResponse(tenantId, client);
    }

    @Transactional
    public ProfileResponse saveSource(TenantId tenantId, UUID clientId, String field, SaveSourceRequest r, UUID userId) {
        ComplianceClient client = find(tenantId, clientId);
        requireTracked(field);
        if (!hasValue(client, field)) throw new HandyFlowException("There is no " + field + " on this client's profile yet",
                HttpStatus.BAD_REQUEST, "CLIENT_FIELD_EMPTY");
        try {
            var existing = sourceRepository.findForField(tenantId, clientId, field);
            if (existing.isPresent()) {
                existing.get().restate(r.source(), r.method(), r.reference(), r.sourceDate(), userId);
                sourceRepository.save(existing.get());
            } else {
                sourceRepository.save(ComplianceClientFieldSource.record(tenantId, clientId, field, r.source(), r.method(), r.reference(), r.sourceDate(), userId));
            }
        } catch (IllegalArgumentException e) {
            throw bad(e);
        }
        return toResponse(tenantId, client);
    }

    @Transactional
    public ProfileResponse confirm(TenantId tenantId, UUID clientId, String field, String exceptionReason, UUID userId) {
        ComplianceClient client = find(tenantId, clientId);
        requireTracked(field);
        ComplianceClientFieldSource s = sourceRepository.findForField(tenantId, clientId, field)
                .orElseThrow(() -> new ResourceNotFoundException("ComplianceClientFieldSource", field));
        try {
            s.confirm(userId, exceptionReason);
        } catch (IllegalStateException e) {
            throw new HandyFlowException(e.getMessage(), HttpStatus.CONFLICT, "CLIENT_FIELD_ALREADY_CONFIRMED");
        } catch (IllegalArgumentException e) {
            throw bad(e);
        }
        sourceRepository.save(s);
        return toResponse(tenantId, client);
    }

    // ---- helpers ----

    private ComplianceClient find(TenantId tenantId, UUID id) {
        return clientRepository.findByIdForTenant(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("ComplianceClient", id.toString()));
    }

    private static void requireTracked(String field) {
        if (!ClientProfileRules.TRACKED_FIELDS.contains(field)) {
            throw new HandyFlowException("Unknown profile field: " + field, HttpStatus.BAD_REQUEST, "CLIENT_FIELD_UNKNOWN");
        }
    }

    private static HandyFlowException bad(IllegalArgumentException e) {
        return new HandyFlowException(e.getMessage(), HttpStatus.BAD_REQUEST, "CLIENT_PROFILE_INVALID");
    }

    static boolean hasValue(ComplianceClient c, String field) {
        return switch (field) {
            case "legalName" -> c.getLegalName() != null;
            case "registrationNumber" -> c.getRegistrationNumber() != null;
            case "entityType" -> c.getEntityType() != null;
            case "taxNumber" -> c.getTaxNumber() != null;
            case "vatNumber" -> c.getVatNumber() != null;
            case "registeredAddress" -> c.getRegisteredAddress() != null;
            case "industry" -> c.getIndustry() != null;
            case "employeeCount" -> c.getEmployeeCount() != null;
            case "turnoverBand" -> c.getTurnoverBand() != null;
            default -> false;
        };
    }

    private ProfileResponse toResponse(TenantId tenantId, ComplianceClient c) {
        List<ComplianceClientFieldSource> rows = sourceRepository.findForClient(tenantId, c.getId());
        List<FieldSourceView> views = rows.stream().map(s -> new FieldSourceView(s.getFieldName(), s.getSource(), s.getMethod(),
                s.getReference(), s.getSourceDate(), s.getRecordedBy(), s.getRecordedAt(), s.isVerified(), s.getVerifiedBy(),
                s.getVerifiedAt(), s.isVerified() && s.getVerifiedBy().equals(s.getRecordedBy()), s.getVerificationExceptionReason())).toList();
        List<String> unconfirmed = ClientProfileRules.TRACKED_FIELDS.stream().sorted()
                .filter(f -> hasValue(c, f))
                .filter(f -> rows.stream().noneMatch(s -> s.getFieldName().equals(f) && s.isVerified()))
                .toList();
        return new ProfileResponse(c.getId(), c.getLegalName(), c.getTradingName(), c.getRegistrationNumber(), c.getEntityType(),
                c.getTaxNumber(), c.getVatNumber(), c.getRegisteredAddress(), c.getPostalAddress(), c.getIndustry(),
                c.getEmployeeCount(), c.getTurnoverBand(), c.getRiskRating(), c.getOnboardingStatus(), views, unconfirmed);
    }
}
