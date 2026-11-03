package za.co.handyflow.platform.projects.domain.enums;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ProjectMemberRoleTest {

    @Test
    void onlyManagersAndDocumentControllersControlDocuments() {
        for (ProjectMemberRole r : ProjectMemberRole.values()) {
            boolean expected = r == ProjectMemberRole.MANAGER || r == ProjectMemberRole.DOCUMENT_CONTROLLER;
            assertEquals(expected, r.canControlDocuments(), r.name());
        }
    }

    @Test
    void aDocumentControllerDoesNotGainOtherPowers() {
        assertFalse(ProjectMemberRole.DOCUMENT_CONTROLLER.canManageMembers());
        assertFalse(ProjectMemberRole.DOCUMENT_CONTROLLER.canViewCost());
    }
}
