package za.co.handyflow.platform.clinic.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.clinic.domain.model.*;
import za.co.handyflow.platform.clinic.domain.repository.*;
import za.co.handyflow.platform.clinic.dto.LetterDtos.*;
import za.co.handyflow.platform.identity.TenantFacade;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.shared.UserContext;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Reusable letter presets for sick notes, referrals, prescription letters and general letters (patch 0164). */
@Service
@RequiredArgsConstructor
public class ClinicLetterTemplateService {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH);

    private final ClinicLetterTemplateRepository repo;
    private final ClinicLetterValues             letterValues;

    @Transactional(readOnly = true)
    public List<TemplateResponse> list(TenantId t, String kind) {
        String k = kind == null || kind.isBlank() ? null : kind.trim().toUpperCase();
        return repo.findLive(t, k).stream().map(ClinicLetterTemplateService::toResponse).toList();
    }

    @Transactional
    public TemplateResponse create(TenantId t, TemplateRequest r) {
        var c = LetterTemplateRules.clean(r.kind(), r.name(), r.title(), r.body(), r.specialty(), r.urgency(), r.unfitDays());
        if (repo.nameTaken(t, c.kind(), c.name(), null)) throw new IllegalStateException("A template called \"" + c.name() + "\" already exists for this kind of letter");
        return toResponse(repo.save(ClinicLetterTemplate.create(t, c.kind(), c.name(), c.title(), c.body(), c.specialty(), c.urgency(), c.unfitDays(), currentUserOrNull())));
    }

    @Transactional
    public TemplateResponse update(TenantId t, UUID id, TemplateRequest r) {
        ClinicLetterTemplate x = live(t, id);
        var c = LetterTemplateRules.clean(x.getKind(), r.name(), r.title(), r.body(), r.specialty(), r.urgency(), r.unfitDays());
        if (repo.nameTaken(t, c.kind(), c.name(), id)) throw new IllegalStateException("A template called \"" + c.name() + "\" already exists for this kind of letter");
        x.update(c.name(), c.title(), c.body(), c.specialty(), c.urgency(), c.unfitDays());
        return toResponse(repo.save(x));
    }

    @Transactional
    public void archive(TenantId t, UUID id) {
        ClinicLetterTemplate x = repo.findOne(t, id).orElseThrow(() -> new ResourceNotFoundException("Letter template", id.toString()));
        x.archive();
        repo.save(x);
    }

    /** The template with {{patient.name}} and the other merge fields filled in, for a visit or for a patient with no visit. */
    @Transactional(readOnly = true)
    public RenderedTemplate render(TenantId t, UUID id, UUID patientId, UUID consultationId, String recipientName, String recipientCompany) {
        return render(t, id, patientId, consultationId, recipientName, recipientCompany, null);
    }

    /** {@code signedBy} is the practitioner the letter will be signed off by; it fills {{doctor.name}} and the other doctor fields. */
    @Transactional(readOnly = true)
    public RenderedTemplate render(TenantId t, UUID id, UUID patientId, UUID consultationId, String recipientName, String recipientCompany, UUID signedBy) {
        ClinicLetterTemplate x = live(t, id);
        var v = letterValues.load(t, patientId, consultationId, recipientName, recipientCompany, signedBy).values();
        return new RenderedTemplate(x.getId(), x.getKind(), x.getName(), LetterMerge.render(x.getTitle(), v), LetterMerge.render(x.getBody(), v),
                x.getSpecialty(), x.getUrgency(), x.getUnfitDays());
    }

    public List<String> mergeFields() { return LetterMerge.FIELDS; }

    private ClinicLetterTemplate live(TenantId t, UUID id) {
        return repo.findOne(t, id).filter(x -> !x.isArchived()).orElseThrow(() -> new ResourceNotFoundException("Letter template", id.toString()));
    }

    private static TemplateResponse toResponse(ClinicLetterTemplate x) {
        return new TemplateResponse(x.getId(), x.getKind(), x.getName(), x.getTitle(), x.getBody(), x.getSpecialty(), x.getUrgency(), x.getUnfitDays());
    }

    private static UUID currentUserOrNull() {
        try { return UserContext.getCurrentUserId(); } catch (RuntimeException e) { return null; }
    }
}
