package za.co.handyflow.platform.recruiter.dto;

import java.util.List;

public final class RecruiterImportDtos {
    private RecruiterImportDtos() {}

    /** {@code csv} is the file's text. {@code lawfulBasis} must be true: these people did not use the careers form. */
    public record ImportRequest(String csv, Boolean lawfulBasis) {}
    public record LineProblem(int line, String problem) {}
    /** {@code dryRun} results describe what would happen; nothing was saved. */
    public record ImportResult(boolean dryRun, int lines, int added, int alreadyApplied, int invalid, List<LineProblem> problems, int moreProblems) {}
}
