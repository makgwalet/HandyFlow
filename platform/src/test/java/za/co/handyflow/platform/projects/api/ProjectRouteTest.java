package za.co.handyflow.platform.projects.api;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ProjectRouteTest {

    private static final UUID ID = UUID.randomUUID();
    private static final UUID ID2 = UUID.randomUUID();
    private static final String BASE = "/api/v1/projects";

    @Test void collection()  { assertEquals(ProjectRoute.Kind.COLLECTION, ProjectRoute.parse(BASE).kind());
        assertEquals(ProjectRoute.Kind.COLLECTION, ProjectRoute.parse(BASE + "/").kind()); }
    @Test void special()     { assertEquals(ProjectRoute.Kind.SPECIAL, ProjectRoute.parse(BASE + "/summary").kind());
        assertEquals(ProjectRoute.Kind.SPECIAL, ProjectRoute.parse(BASE + "/time-approvals").kind()); }

    @Test void projectScoped() {
        ProjectRoute r = ProjectRoute.parse(BASE + "/" + ID + "/tasks");
        assertEquals(ProjectRoute.Kind.PROJECT_SCOPED, r.kind());
        assertEquals(ID, r.projectId());
        assertFalse(r.costDomain());
    }

    @Test void costRoutesAreFlagged() {
        assertTrue(ProjectRoute.parse(BASE + "/" + ID + "/budget/evm").costDomain());
        assertTrue(ProjectRoute.parse(BASE + "/" + ID + "/change-orders").costDomain());
        assertTrue(ProjectRoute.parse(BASE + "/" + ID + "/change-orders/" + ID2 + "/export").costDomain());
        ProjectRoute entity = ProjectRoute.parse(BASE + "/budget/" + ID);
        assertEquals(ProjectRoute.Kind.ENTITY_SCOPED, entity.kind());
        assertTrue(entity.costDomain());
        assertTrue(ProjectRoute.parse(BASE + "/change-orders/" + ID + "/approve").costDomain());
    }

    @Test void entityScoped() {
        ProjectRoute r = ProjectRoute.parse(BASE + "/tasks/" + ID + "/status");
        assertEquals(ProjectRoute.Kind.ENTITY_SCOPED, r.kind());
        assertEquals("tasks", r.entityKeyword());
        assertEquals(ID, r.entityId());
        assertNull(r.projectId());
        assertFalse(r.costDomain());
    }

    @Test void membersRoutesAreFlagged() {
        assertTrue(ProjectRoute.parse(BASE + "/" + ID + "/members").membersDomain());
        assertTrue(ProjectRoute.parse(BASE + "/" + ID + "/members/" + ID2).membersDomain());
        assertFalse(ProjectRoute.parse(BASE + "/" + ID + "/tasks").membersDomain());
    }

    @Test void unknownAndMalformedRoutesFailClosed() {
        assertEquals(ProjectRoute.Kind.UNKNOWN, ProjectRoute.parse(BASE + "/something-new").kind());
        assertEquals(ProjectRoute.Kind.UNKNOWN, ProjectRoute.parse(BASE + "/tasks/not-a-uuid").kind());
        assertEquals(ProjectRoute.Kind.UNKNOWN, ProjectRoute.parse(BASE + "/tasks").kind());
        assertEquals(ProjectRoute.Kind.UNKNOWN, ProjectRoute.parse(BASE + "/summary/extra").kind());
        assertEquals(ProjectRoute.Kind.UNKNOWN, ProjectRoute.parse("/api/v1/other").kind());
        assertEquals(ProjectRoute.Kind.UNKNOWN, ProjectRoute.parse(null).kind());
    }

    @Test void lenientUuidFormsAreRejected() {
        // Spring would trim the leading space / UUID.fromString accepts shorthand; we must not.
        assertEquals(ProjectRoute.Kind.UNKNOWN, ProjectRoute.parse(BASE + "/%20" + ID + "/budget").kind());
        assertEquals(ProjectRoute.Kind.UNKNOWN, ProjectRoute.parse(BASE + "/1-1-1-1-1/budget").kind());
    }
}