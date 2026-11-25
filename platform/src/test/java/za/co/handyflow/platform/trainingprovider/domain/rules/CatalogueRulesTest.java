package za.co.handyflow.platform.trainingprovider.domain.rules;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CatalogueRulesTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 25);
    private static final UUID QUAL = UUID.randomUUID();
    private static final UUID MAKER = UUID.randomUUID();
    private static final UUID OTHER = UUID.randomUUID();

    @Test
    void accreditedProgrammeNeedsItsQualification() {
        assertNotNull(CatalogueRules.programmeProblem("ACCREDITED", "QUALIFICATION", "CLASSROOM", null));
        assertNull(CatalogueRules.programmeProblem("ACCREDITED", "QUALIFICATION", "CLASSROOM", QUAL));
    }

    @Test
    void nonAccreditedCourseCanNeverBeFiledAsAQualification() {
        assertNotNull(CatalogueRules.programmeProblem("NON_ACCREDITED", "QUALIFICATION", "CLASSROOM", null));
        assertNotNull(CatalogueRules.programmeProblem("NON_ACCREDITED", "SKILLS_PROGRAMME", "CLASSROOM", null));
        assertNull(CatalogueRules.programmeProblem("NON_ACCREDITED", "NON_ACCREDITED_COURSE", "CLASSROOM", null));
        assertNotNull(CatalogueRules.programmeProblem("ACCREDITED", "NON_ACCREDITED_COURSE", "CLASSROOM", QUAL));
    }

    @Test
    void unknownActivityOrModeIsRefused() {
        assertNotNull(CatalogueRules.programmeProblem("OTHER", "QUALIFICATION", "CLASSROOM", QUAL));
        assertNotNull(CatalogueRules.programmeProblem("ACCREDITED", "QUALIFICATION", "TELEPATHY", QUAL));
        assertNull(CatalogueRules.programmeProblem("WORKPLACE_BASED", "SKILLS_PROGRAMME", "WORKPLACE", QUAL));
    }

    @Test
    void qualificationVersionNeedsASourceAndAnInDateRegistration() {
        assertNotNull(CatalogueRules.qualificationActivationProblem("", "REGISTERED", null, TODAY));
        assertNotNull(CatalogueRules.qualificationActivationProblem("abc", "REGISTERED", null, TODAY));
        assertNotNull(CatalogueRules.qualificationActivationProblem("Register entry", "BOGUS", null, TODAY));
        assertNotNull(CatalogueRules.qualificationActivationProblem("Register entry", "REGISTERED", TODAY.minusDays(1), TODAY));
        assertNull(CatalogueRules.qualificationActivationProblem("Register entry", "REGISTERED", TODAY, TODAY));
        assertNull(CatalogueRules.qualificationActivationProblem("Register entry", "LAPSED", TODAY.minusDays(1), TODAY));
        assertNull(CatalogueRules.qualificationActivationProblem("Register entry", "UNKNOWN", null, TODAY));
    }

    @Test
    void versionNeedsModulesBeforeApproval() {
        assertNotNull(CatalogueRules.versionApprovalProblem("NON_ACCREDITED", 0, null, null, null, OTHER, MAKER, null));
        assertNull(CatalogueRules.versionApprovalProblem("NON_ACCREDITED", 1, null, null, null, OTHER, MAKER, null));
    }

    @Test
    void accreditedVersionMustBePinnedToAPublishedVersionOfItsOwnQualification() {
        assertNotNull(CatalogueRules.versionApprovalProblem("ACCREDITED", 2, QUAL, null, null, OTHER, MAKER, null));
        assertNotNull(CatalogueRules.versionApprovalProblem("ACCREDITED", 2, QUAL, UUID.randomUUID(), "CURRENT", OTHER, MAKER, null));
        assertNotNull(CatalogueRules.versionApprovalProblem("ACCREDITED", 2, QUAL, QUAL, "DRAFT", OTHER, MAKER, null));
        assertNull(CatalogueRules.versionApprovalProblem("ACCREDITED", 2, QUAL, QUAL, "CURRENT", OTHER, MAKER, null));
        assertNull(CatalogueRules.versionApprovalProblem("ACCREDITED", 2, QUAL, QUAL, "SUPERSEDED", OTHER, MAKER, null));
    }

    @Test
    void whoDraftedTheVersionNeedsAReasonToApproveIt() {
        assertNotNull(CatalogueRules.versionApprovalProblem("NON_ACCREDITED", 1, null, null, null, MAKER, MAKER, null));
        assertNotNull(CatalogueRules.versionApprovalProblem("NON_ACCREDITED", 1, null, null, null, MAKER, MAKER, "just me"));
        assertNull(CatalogueRules.versionApprovalProblem("NON_ACCREDITED", 1, null, null, null, MAKER, MAKER, "Sole owner, nobody else available"));
    }

    @Test
    void creditsNoteIsSoftAndOnlyWhenTheNumbersCannotBothBeRight() {
        assertNull(CatalogueRules.creditsNote(null, 50, 50));
        assertNull(CatalogueRules.creditsNote(120, 100, 130));
        assertNotNull(CatalogueRules.creditsNote(120, 130, 140));
        assertNotNull(CatalogueRules.creditsNote(120, 60, 100));
    }
}
