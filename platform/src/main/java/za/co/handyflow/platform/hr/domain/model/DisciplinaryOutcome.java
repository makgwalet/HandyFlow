package za.co.handyflow.platform.hr.domain.model;

/**
 * FIX: backlog 3.5. Matches exactly the four progressive-discipline
 * stages South African labour practice (and the CCMA) expects to see
 * documented consistently — no extra stages invented beyond what was
 * asked for.
 */
public enum DisciplinaryOutcome {
    VERBAL_WARNING("Verbal Warning"),
    WRITTEN_WARNING("Written Warning"),
    FINAL_WRITTEN_WARNING("Final Written Warning"),
    DISMISSAL("Dismissal");

    private final String displayName;

    DisciplinaryOutcome(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}