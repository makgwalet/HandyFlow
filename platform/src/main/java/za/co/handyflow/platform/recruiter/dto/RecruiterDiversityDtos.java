package za.co.handyflow.platform.recruiter.dto;

public final class RecruiterDiversityDtos {
    private RecruiterDiversityDtos() {}

    /** Every answer is optional. Values are those in RecruiterDiversityRules. */
    public record Declaration(String gender, String race, String disability) {}
}
