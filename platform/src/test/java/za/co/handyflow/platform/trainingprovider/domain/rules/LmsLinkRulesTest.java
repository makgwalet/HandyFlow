package za.co.handyflow.platform.trainingprovider.domain.rules;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LmsLinkRulesTest {

    @Test
    void acceptsPlainHttpsAddress() {
        assertNull(LmsLinkRules.linkProblem("Moodle", "https://lms.example.org/course/view.php?id=7"));
    }

    @Test
    void refusesNonHttpsOrEmbeddedCredentials() {
        assertNotNull(LmsLinkRules.linkProblem("Moodle", "http://lms.example.org/c"));
        assertNotNull(LmsLinkRules.linkProblem("Moodle", "https://user:pass@lms.example.org/c"));
        assertNotNull(LmsLinkRules.linkProblem("Moodle", "lms.example.org"));
        assertNotNull(LmsLinkRules.linkProblem("Moodle", null));
    }

    @Test
    void refusesSecretsInTheAddress() {
        assertNotNull(LmsLinkRules.linkProblem("Moodle", "https://lms.example.org/c?token=abc"));
        assertNotNull(LmsLinkRules.linkProblem("Moodle", "https://lms.example.org/c?id=1&password=x"));
        assertNotNull(LmsLinkRules.linkProblem("Moodle", "https://lms.example.org/c?API_KEY=x".replace("API_KEY", "api_key")));
    }

    @Test
    void needsAName() {
        assertNotNull(LmsLinkRules.linkProblem(" ", "https://lms.example.org/c"));
    }

    @Test
    void endedLinkCannotChangeOrEndAgain() {
        assertNotNull(LmsLinkRules.editProblem("ENDED"));
        assertNull(LmsLinkRules.editProblem("ACTIVE"));
        assertNull(LmsLinkRules.endProblem("ACTIVE", "Contract with the system ended"));
        assertNotNull(LmsLinkRules.endProblem("ACTIVE", "short"));
        assertNotNull(LmsLinkRules.endProblem("ENDED", "Contract with the system ended"));
    }
}
