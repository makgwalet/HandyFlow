package za.co.handyflow.platform.clinic.application.internal;

import za.co.handyflow.platform.clinic.dto.VisitDtos.TeamMember;
import za.co.handyflow.platform.clinic.dto.VisitDtos.VisitResponse;

import java.util.ArrayList;
import java.util.List;

/** What the timeline shows for a visit: a few lines of summary and the people involved. Pure. */
final class TimelineVisitRules {
    private TimelineVisitRules() {}

    private static String clean(String v) { return v == null || v.isBlank() ? null : v.trim(); }

    static List<String> summary(VisitResponse v) {
        List<String> out = new ArrayList<>();
        if (clean(v.chiefComplaint()) != null) out.add("Reason: " + clean(v.chiefComplaint()));
        if (clean(v.diagnosis()) != null) {
            String codes = v.icd10Codes() == null || v.icd10Codes().isEmpty() ? "" : " (" + String.join(", ", v.icd10Codes()) + ")";
            out.add("Diagnosis: " + clean(v.diagnosis()) + codes);
        }
        if (clean(v.treatmentPlan()) != null) out.add("Plan: " + clean(v.treatmentPlan()));
        int meds = v.prescriptions() == null ? 0 : v.prescriptions().size();
        if (meds > 0) out.add(meds + (meds == 1 ? " medicine prescribed" : " medicines prescribed"));
        if (v.followUpDays() != null && v.followUpDays() > 0) out.add("Follow-up in " + v.followUpDays() + (v.followUpDays() == 1 ? " day" : " days"));
        int add = v.addenda() == null ? 0 : v.addenda().size();
        if (add > 0) out.add(add + (add == 1 ? " addendum" : " addenda") + " after signing");
        return out;
    }

    /** "Prepared by Sister Zodwa Nkosi", in the order the visit recorded them; a member without a name is left out. */
    static List<String> people(VisitResponse v) {
        List<String> out = new ArrayList<>();
        if (v.team() == null) return out;
        for (TeamMember m : v.team()) {
            if (m == null || clean(m.name()) == null) continue;
            out.add((clean(m.role()) == null ? "" : clean(m.role()) + " ") + clean(m.name()));
        }
        return out;
    }
}
