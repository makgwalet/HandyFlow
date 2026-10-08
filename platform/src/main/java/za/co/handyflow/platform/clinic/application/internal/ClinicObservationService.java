package za.co.handyflow.platform.clinic.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.clinic.domain.model.ClinicConsultation;
import za.co.handyflow.platform.clinic.domain.model.ClinicObservation;
import za.co.handyflow.platform.clinic.domain.model.ObservationCode;
import za.co.handyflow.platform.clinic.domain.repository.ClinicConsultationRepository;
import za.co.handyflow.platform.clinic.domain.repository.ClinicObservationRepository;
import za.co.handyflow.platform.clinic.domain.repository.ClinicPatientRepository;
import za.co.handyflow.platform.clinic.dto.ObservationDtos.ObservationRequest;
import za.co.handyflow.platform.clinic.dto.ObservationDtos.ObservationResponse;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.shared.UserContext;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Observations (vitals and other measurements) as a first-class, queryable model. */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClinicObservationService {

    private static final Pattern BP = Pattern.compile("^\\s*(\\d{2,3})\\s*/\\s*(\\d{2,3})\\s*$");
    private static final int MAX_BATCH = 50;
    /** Clock differences between devices; anything further ahead than this is a typing mistake, not a measurement. */
    private static final java.time.Duration FUTURE_ALLOWANCE = java.time.Duration.ofMinutes(10);

    private final ClinicObservationRepository  observationRepo;
    private final ClinicPatientRepository      patientRepo;
    private final ClinicConsultationRepository consultationRepo;
    private final ClinicQuestionLibraryService questionLibrary;

    @Transactional
    public List<ObservationResponse> record(TenantId t, UUID patientId, List<ObservationRequest> reqs) {
        var patient = patientRepo.findActiveById(t, patientId)
                .orElseThrow(() -> new ResourceNotFoundException("Patient", patientId.toString()));
        if (reqs == null || reqs.isEmpty()) throw new IllegalArgumentException("At least one observation is required");
        if (reqs.size() > MAX_BATCH) throw new IllegalArgumentException("At most " + MAX_BATCH + " observations per request");

        UUID takenBy = currentUserOrNull();
        List<ClinicObservation> toSave = new ArrayList<>();
        for (ObservationRequest r : reqs) {
            ObservationCode code = ObservationCode.parse(r.code()).orElseThrow(() ->
                    new IllegalArgumentException("Unknown observation code '" + r.code() + "'"));
            if (r.value() == null) throw new IllegalArgumentException("value is required for " + code);
            if (r.takenAt() != null && r.takenAt().isAfter(Instant.now().plus(FUTURE_ALLOWANCE)))
                throw new IllegalArgumentException("A measurement cannot be dated in the future");
            if (r.takenAt() != null && patient.getDateOfBirth() != null
                    && r.takenAt().atZone(java.time.ZoneId.of("Africa/Johannesburg")).toLocalDate().isBefore(patient.getDateOfBirth()))
                throw new IllegalArgumentException("A measurement cannot be dated before the patient was born");
            if (r.refLow() != null && r.refHigh() != null && r.refLow().compareTo(r.refHigh()) > 0) {
                throw new IllegalArgumentException("refLow cannot be above refHigh for " + code);
            }
            if (r.consultationId() != null) {
                var c = consultationRepo.findActiveById(t, r.consultationId())
                        .orElseThrow(() -> new ResourceNotFoundException("Consultation", r.consultationId().toString()));
                if (!c.getPatientId().equals(patientId)) {
                    throw new IllegalArgumentException("Consultation does not belong to this patient");
                }
            }
            toSave.add(ClinicObservation.of(t, patientId, r.consultationId(), code, r.value(),
                    r.refLow(), r.refHigh(), r.takenAt(), takenBy, "MANUAL", clean(r.notes())));
        }
        observationRepo.saveAll(toSave);
        return toSave.stream().map(ClinicObservationService::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<ObservationResponse> list(TenantId t, UUID patientId, String code, Instant from, Instant to,
                                          UUID consultationId, boolean includeVoided) {
        requirePatient(t, patientId);
        String codeFilter = null;
        if (code != null && !code.isBlank()) {
            codeFilter = ObservationCode.parse(code).orElseThrow(() ->
                    new IllegalArgumentException("Unknown observation code '" + code + "'")).name();
        }
        final String cf = codeFilter;
        return observationRepo.findByPatient(t, patientId).stream()
                .filter(o -> includeVoided || "FINAL".equals(o.getStatus()))
                .filter(o -> cf == null || cf.equals(o.getCode()))
                .filter(o -> from == null || !o.getTakenAt().isBefore(from))
                .filter(o -> to == null || o.getTakenAt().isBefore(to))
                .filter(o -> consultationId == null || consultationId.equals(o.getConsultationId()))
                .map(ClinicObservationService::toResponse).toList();
    }

    /** Most recent final value of each code (newest first overall). */
    @Transactional(readOnly = true)
    public List<ObservationResponse> latest(TenantId t, UUID patientId) {
        requirePatient(t, patientId);
        Map<String, ClinicObservation> newest = new LinkedHashMap<>();
        for (ClinicObservation o : observationRepo.findByPatient(t, patientId)) {   // already newest first
            if ("FINAL".equals(o.getStatus())) newest.putIfAbsent(o.getCode(), o);
        }
        return newest.values().stream().map(ClinicObservationService::toResponse).toList();
    }

    @Transactional
    public ObservationResponse voidObservation(TenantId t, UUID patientId, UUID id) {
        requirePatient(t, patientId);
        var o = observationRepo.findOne(t, patientId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Observation", id.toString()));
        o.markEnteredInError();
        observationRepo.save(o);
        return toResponse(o);
    }

    /**
     * Rebuilds the observation rows derived from a consultation's vitals fields. Called when a
     * consultation is recorded, signed or edited after signing (not for autosaved drafts, so
     * trends only ever show vitals that belong to a real visit).
     */
    @Transactional
    public void syncConsultationVitals(TenantId t, ClinicConsultation c) {
        observationRepo.deleteConsultationDerived(t, c.getId());
        Instant at = c.getConsultedAt();
        List<ClinicObservation> rows = new ArrayList<>();

        if (c.getBloodPressure() != null) {
            Matcher m = BP.matcher(c.getBloodPressure());
            if (m.matches()) {
                rows.add(derived(t, c, ObservationCode.BP_SYSTOLIC, new BigDecimal(m.group(1)), at));
                rows.add(derived(t, c, ObservationCode.BP_DIASTOLIC, new BigDecimal(m.group(2)), at));
            }
        }
        if (c.getPulseBpm() != null)
            rows.add(derived(t, c, ObservationCode.PULSE, BigDecimal.valueOf(c.getPulseBpm()), at));
        if (c.getTemperatureC() != null)
            rows.add(derived(t, c, ObservationCode.TEMPERATURE, c.getTemperatureC(), at));
        if (c.getOxygenSatPct() != null)
            rows.add(derived(t, c, ObservationCode.SPO2, c.getOxygenSatPct(), at));
        if (c.getWeightKg() != null)
            rows.add(derived(t, c, ObservationCode.WEIGHT, c.getWeightKg(), at));
        if (c.getHeightCm() != null)
            rows.add(derived(t, c, ObservationCode.HEIGHT, c.getHeightCm(), at));
        BigDecimal bmi = bmi(c.getWeightKg(), c.getHeightCm());
        if (bmi != null) rows.add(derived(t, c, ObservationCode.BMI, bmi, at));

        // Measurable questionnaire answers. A value from the vitals fields wins; the form only fills what is missing.
        java.util.Set<String> have = rows.stream().map(ClinicObservation::getCode).collect(java.util.stream.Collectors.toSet());
        for (var m : questionLibrary.measuredAnswers(t, c.getId(),
                code -> ObservationCode.parse(code).map(ObservationCode::unit).orElse(null))) {
            ObservationCode oc = ObservationCode.parse(m.observationCode()).orElse(null);
            if (oc == null || !have.add(oc.name())) continue;
            rows.add(derived(t, c, oc, m.value(), at));
        }

        if (!rows.isEmpty()) observationRepo.saveAll(rows);
    }

    static BigDecimal bmi(BigDecimal weightKg, BigDecimal heightCm) {
        if (weightKg == null || heightCm == null
                || weightKg.signum() <= 0 || heightCm.signum() <= 0) return null;
        BigDecimal m = heightCm.divide(new BigDecimal("100"), 6, RoundingMode.HALF_UP);
        return weightKg.divide(m.multiply(m), 1, RoundingMode.HALF_UP);
    }

    private ClinicObservation derived(TenantId t, ClinicConsultation c, ObservationCode code,
                                      BigDecimal value, Instant at) {
        return ClinicObservation.of(t, c.getPatientId(), c.getId(), code, value, null, null,
                at, null, "CONSULTATION", null);
    }

    private void requirePatient(TenantId t, UUID patientId) {
        patientRepo.findActiveById(t, patientId)
                .orElseThrow(() -> new ResourceNotFoundException("Patient", patientId.toString()));
    }

    private static String clean(String v) { return v == null || v.isBlank() ? null : v.trim(); }

    private static UUID currentUserOrNull() {
        try { return UserContext.getCurrentUserId(); } catch (RuntimeException e) { return null; }
    }

    static ObservationResponse toResponse(ClinicObservation o) {
        String label = ObservationCode.parse(o.getCode()).map(ObservationCode::label).orElse(o.getCode());
        return new ObservationResponse(o.getId(), o.getPatientId(), o.getConsultationId(), o.getCode(),
                label, o.getValueNumeric(), o.getUnit(), o.getRefLow(), o.getRefHigh(), o.getAbnormalFlag(),
                o.getTakenAt(), o.getTakenBy(), o.getSource(), o.getStatus(), o.getNotes());
    }
}
