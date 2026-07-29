package za.co.handyflow.platform.clinic.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.clinic.domain.model.ClinicPatientConsent;
import za.co.handyflow.platform.clinic.domain.repository.ClinicPatientConsentRepository;
import za.co.handyflow.platform.clinic.dto.ConsentEventResponse;
import za.co.handyflow.platform.clinic.dto.ConsentStatusResponse;
import za.co.handyflow.platform.clinic.dto.RecordConsentRequest;
import za.co.handyflow.platform.shared.TenantId;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * FIX: "no POPIA consent tracking" gap. See ClinicPatientConsent for the
 * design rationale (append-only event log, current status = most recent
 * event per type).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClinicConsentService {

    private final ClinicPatientConsentRepository consentRepo;

    /** The consent categories this tracker covers. Extend here if a practice needs more. */
    public static final List<String> CONSENT_TYPES = List.of(
            "TREATMENT", "MEDICAL_AID_SHARING", "THIRD_PARTY_REFERRAL", "MARKETING", "RESEARCH"
    );

    @Transactional
    public ConsentEventResponse recordConsent(TenantId tenantId, UUID patientId, RecordConsentRequest req) {
        ClinicPatientConsent event = ClinicPatientConsent.record(
                tenantId, patientId,
                req.consentType().toUpperCase(), req.action().toUpperCase(),
                req.method(), req.capturedByName(), req.notes());
        consentRepo.save(event);
        log.info("Recorded consent patient={} type={} action={}",
                patientId, event.getConsentType(), event.getAction());
        return toEventResponse(event);
    }

    /** Current status per known consent type — most recent event per type wins. */
    @Transactional(readOnly = true)
    public List<ConsentStatusResponse> getConsentStatus(TenantId tenantId, UUID patientId) {
        List<ClinicPatientConsent> events = consentRepo.findByPatient(tenantId, patientId); // newest first

        // Seed every known type with null so unset types still appear (as
        // NOT_RECORDED) and the output preserves CONSENT_TYPES' order.
        Map<String, ClinicPatientConsent> latestByType = new LinkedHashMap<>();
        for (String type : CONSENT_TYPES) latestByType.put(type, null);
        for (ClinicPatientConsent e : events) {
            // Map.merge treats a null-valued existing mapping as absent, so
            // the first (i.e. newest, since events are DESC-sorted) event
            // per type wins here; any older event of the same type is
            // dropped by the remapping function.
            latestByType.merge(e.getConsentType(), e, (existing, incoming) -> existing);
        }

        return CONSENT_TYPES.stream()
                .map(type -> {
                    ClinicPatientConsent latest = latestByType.get(type);
                    return latest == null
                            ? new ConsentStatusResponse(type, "NOT_RECORDED", null, null, null)
                            : new ConsentStatusResponse(type, latest.getAction(), latest.getCreatedAt(),
                            latest.getMethod(), latest.getNotes());
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ConsentEventResponse> getConsentHistory(TenantId tenantId, UUID patientId) {
        return consentRepo.findByPatient(tenantId, patientId).stream()
                .map(this::toEventResponse)
                .toList();
    }

    private ConsentEventResponse toEventResponse(ClinicPatientConsent e) {
        return new ConsentEventResponse(e.getId(), e.getConsentType(), e.getAction(),
                e.getMethod(), e.getCapturedByName(), e.getNotes(), e.getCreatedAt());
    }
}