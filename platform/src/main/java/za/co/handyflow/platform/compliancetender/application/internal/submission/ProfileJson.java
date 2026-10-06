package za.co.handyflow.platform.compliancetender.application.internal.submission;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;

/** The frozen copies kept on a package record: the effective submission profile and the issues found, as JSON text. */
final class ProfileJson {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ProfileJson() {}

    static String of(SubmissionProfile profile) { return write(profile); }

    static String issues(List<PackageIssue> issues) { return write(issues); }

    private static String write(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not record the package's profile and issues.", e);
        }
    }
}
