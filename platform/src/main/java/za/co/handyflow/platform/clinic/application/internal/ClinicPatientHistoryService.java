package za.co.handyflow.platform.clinic.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.clinic.domain.model.*;
import za.co.handyflow.platform.clinic.domain.repository.*;
import za.co.handyflow.platform.clinic.dto.PatientHistoryDtos.*;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.shared.UserContext;

import java.util.List;
import java.util.UUID;

/** Family history, lifestyle and social history, and medical aid on a patient's file (patch 0158). */
@Service
@RequiredArgsConstructor
public class ClinicPatientHistoryService {

    private final ClinicPatientRepository                patientRepo;
    private final ClinicPatientFamilyHistoryRepository   familyRepo;
    private final ClinicPatientSocialHistoryRepository   socialRepo;
    private final ClinicMedicalAidRepository             aidRepo;

    // ── Family history ───────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<FamilyHistoryResponse> listFamily(TenantId t, UUID patientId, boolean includeInactive) {
        requirePatient(t, patientId);
        return familyRepo.findByPatient(t, patientId).stream()
                .filter(h -> includeInactive || h.isActive()).map(ClinicPatientHistoryService::toResponse).toList();
    }

    @Transactional
    public FamilyHistoryResponse addFamily(TenantId t, UUID patientId, FamilyHistoryRequest req) {
        requirePatient(t, patientId);
        String relative = HistoryRules.oneOf(req.relative(), HistoryRules.RELATIVES, "Relative", null);
        if (relative == null) throw new IllegalArgumentException("Relative is required.");
        String condition = HistoryRules.required(req.conditionName(), 200, "Condition");
        boolean dup = familyRepo.findByPatient(t, patientId).stream()
                .anyMatch(h -> h.isActive() && h.getRelative().equals(relative) && h.getConditionName().equalsIgnoreCase(condition));
        if (dup) throw new IllegalArgumentException("That condition is already recorded for this relative.");
        var h = ClinicPatientFamilyHistory.create(t, patientId, relative, condition, HistoryRules.ageAtOnset(req.ageAtOnset()),
                req.deceased(), HistoryRules.text(req.notes(), 1000, "Notes"), currentUserOrNull());
        return toResponse(familyRepo.save(h));
    }

    @Transactional
    public FamilyHistoryResponse updateFamily(TenantId t, UUID patientId, UUID id, FamilyHistoryRequest req) {
        requirePatient(t, patientId);
        var h = familyRepo.findOne(t, patientId, id).orElseThrow(() -> new ResourceNotFoundException("Family history", id.toString()));
        String relative = HistoryRules.oneOf(req.relative(), HistoryRules.RELATIVES, "Relative", h.getRelative());
        String condition = req.conditionName() == null || req.conditionName().isBlank() ? h.getConditionName()
                : HistoryRules.required(req.conditionName(), 200, "Condition");
        h.update(relative, condition, req.ageAtOnset() == null ? h.getAgeAtOnset() : HistoryRules.ageAtOnset(req.ageAtOnset()),
                req.deceased() == null ? h.getDeceased() : req.deceased(),
                req.notes() == null ? h.getNotes() : HistoryRules.text(req.notes(), 1000, "Notes"),
                HistoryRules.oneOf(req.status(), HistoryRules.FAMILY_STATUSES, "Status", null));
        return toResponse(familyRepo.save(h));
    }

    // ── Lifestyle and social history ─────────────────────────────────────────

    @Transactional(readOnly = true)
    public SocialHistoryResponse getSocial(TenantId t, UUID patientId) {
        requirePatient(t, patientId);
        return socialRepo.findForPatient(t, patientId).map(ClinicPatientHistoryService::toResponse)
                .orElse(new SocialHistoryResponse(false, "UNKNOWN", "UNKNOWN", "UNKNOWN", null, null, null, null, null));
    }

    @Transactional
    public SocialHistoryResponse putSocial(TenantId t, UUID patientId, SocialHistoryRequest req) {
        requirePatient(t, patientId);
        var s = socialRepo.findForPatient(t, patientId).orElseGet(() -> ClinicPatientSocialHistory.create(t, patientId));
        s.replace(HistoryRules.oneOf(req.smokingStatus(), HistoryRules.SMOKING, "Smoking", "UNKNOWN"),
                HistoryRules.oneOf(req.alcoholUse(), HistoryRules.ALCOHOL, "Alcohol", "UNKNOWN"),
                HistoryRules.oneOf(req.substanceUse(), HistoryRules.SUBSTANCE, "Substance use", "UNKNOWN"),
                HistoryRules.text(req.occupation(), 150, "Occupation"),
                HistoryRules.text(req.livingSituation(), 200, "Living situation"),
                HistoryRules.text(req.physicalActivity(), 200, "Physical activity"),
                HistoryRules.text(req.notes(), 1000, "Notes"), currentUserOrNull());
        return toResponse(socialRepo.save(s));
    }

    // ── Medical aid ──────────────────────────────────────────────────────────

    /** The patient's own active record; a dependant with none sees the principal's, marked as inherited. Null when there is none. */
    @Transactional(readOnly = true)
    public MedicalAidResponse getMedicalAid(TenantId t, UUID patientId) {
        ClinicPatient patient = requirePatient(t, patientId);
        var own = aidRepo.findActiveByPatient(t, patientId);
        if (!own.isEmpty()) return toResponse(own.get(0), false, null);
        if (patient.getPrincipalId() != null) {
            var inherited = aidRepo.findActiveByPatient(t, patient.getPrincipalId());
            if (!inherited.isEmpty()) {
                String from = patientRepo.findActiveById(t, patient.getPrincipalId()).map(pr -> pr.getFirstName() + " " + pr.getLastName()).orElse(null);
                return toResponse(inherited.get(0), true, from);
            }
        }
        return null;
    }

    /** Saves the patient's own medical aid: updates the active record, or creates one. An inherited record is never changed here. */
    @Transactional
    public MedicalAidResponse putMedicalAid(TenantId t, UUID patientId, MedicalAidRequest req) {
        requirePatient(t, patientId);
        var a = HistoryRules.aid(req.schemeName(), req.planName(), req.memberNumber(), req.dependentCode(),
                req.principalMember(), req.schemeContactPhone());
        var own = aidRepo.findActiveByPatient(t, patientId);
        ClinicMedicalAid rec;
        if (own.isEmpty()) {
            rec = ClinicMedicalAid.create(t.getValue(), patientId, a.schemeName(), a.planName(), a.memberNumber(), a.dependentCode(), a.principalMember());
        } else {
            rec = own.get(0);
        }
        rec.update(a.schemeName(), a.planName(), a.memberNumber(), a.dependentCode(), a.principalMember(), a.phone());
        return toResponse(aidRepo.save(rec), false, null);
    }

    /** Stops using the patient's own medical aid; the row stays so earlier claims still show their scheme. */
    @Transactional
    public void removeMedicalAid(TenantId t, UUID patientId) {
        requirePatient(t, patientId);
        for (var rec : aidRepo.findActiveByPatient(t, patientId)) { rec.setActive(false); aidRepo.save(rec); }
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private ClinicPatient requirePatient(TenantId t, UUID patientId) {
        return patientRepo.findActiveById(t, patientId).orElseThrow(() -> new ResourceNotFoundException("Patient", patientId.toString()));
    }

    private static UUID currentUserOrNull() {
        try { return UserContext.getCurrentUserId(); } catch (RuntimeException e) { return null; }
    }

    static FamilyHistoryResponse toResponse(ClinicPatientFamilyHistory h) {
        return new FamilyHistoryResponse(h.getId(), h.getPatientId(), h.getRelative(), h.getConditionName(),
                h.getAgeAtOnset() == null ? null : h.getAgeAtOnset().intValue(), h.getDeceased(), h.getNotes(), h.getStatus(), h.getCreatedAt());
    }

    static SocialHistoryResponse toResponse(ClinicPatientSocialHistory s) {
        return new SocialHistoryResponse(true, s.getSmokingStatus(), s.getAlcoholUse(), s.getSubstanceUse(), s.getOccupation(),
                s.getLivingSituation(), s.getPhysicalActivity(), s.getNotes(), s.getUpdatedAt());
    }

    static MedicalAidResponse toResponse(ClinicMedicalAid m, boolean inherited, String from) {
        return new MedicalAidResponse(m.getId(), m.getSchemeName(), m.getPlanName(), m.getMemberNumber(), m.getDependentCode(),
                m.getPrincipalMember(), m.getSchemeContactPhone(), inherited, from);
    }
}
