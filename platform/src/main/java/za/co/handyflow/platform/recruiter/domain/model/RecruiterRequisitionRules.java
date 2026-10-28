package za.co.handyflow.platform.recruiter.domain.model;

/** How many people a job is meant to hire, and what follows when they have been hired. Pure. */
public final class RecruiterRequisitionRules {
    private RecruiterRequisitionRules() {}

    public static final int MAX_VACANCIES = 500, MAX_REF = 40;

    /** Why this headcount or reference cannot be saved, or null. */
    public static String problem(Integer vacancies, String ref, int alreadyHired) {
        if (vacancies == null || vacancies < 1) return "A job needs at least one vacancy.";
        if (vacancies > MAX_VACANCIES) return "A job can have " + MAX_VACANCIES + " vacancies at most.";
        if (vacancies < alreadyHired) return alreadyHired + " people are already hired for this job, so it needs at least " + alreadyHired + " vacancies.";
        if (ref != null && ref.trim().length() > MAX_REF) return "The requisition reference is too long (" + MAX_REF + " characters at most).";
        return null;
    }

    /** Why another candidate cannot be hired into this job, or null. */
    public static String hireProblem(int vacancies, int alreadyHired) {
        return alreadyHired >= vacancies
                ? (vacancies == 1 ? "The vacancy for this job is already filled." : "All " + vacancies + " vacancies for this job are filled.")
                : null;
    }

    /** Whether the job should now be marked filled: everyone wanted is hired and it is still taking applications. */
    public static boolean fillsJob(int vacancies, int hiredAfter, String jobStatus) {
        return hiredAfter >= vacancies && ("OPEN".equals(jobStatus) || "PAUSED".equals(jobStatus));
    }

    /** Whether saving these fields is a new version of the advertised wording. */
    public static boolean wordingChanged(String t0, String d0, String r0, String b0, java.math.BigDecimal min0, java.math.BigDecimal max0,
                                         String t1, String d1, String r1, String b1, java.math.BigDecimal min1, java.math.BigDecimal max1) {
        return !java.util.Objects.equals(t0, t1) || !java.util.Objects.equals(d0, d1) || !java.util.Objects.equals(r0, r1)
                || !java.util.Objects.equals(b0, b1) || cmp(min0, min1) || cmp(max0, max1);
    }

    private static boolean cmp(java.math.BigDecimal a, java.math.BigDecimal b) {
        return a == null || b == null ? a != b : a.compareTo(b) != 0;
    }
}
