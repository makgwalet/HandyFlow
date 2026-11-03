package za.co.handyflow.platform.projects.dto;

/**
 * What the CURRENT user can do on this project — lets the UI hide buttons and
 * sections instead of letting the user click into a 403.
 *
 * @param restricted false = project access restriction is switched off (everyone has full access)
 * @param role       the user's project role, or null (PM_ADMIN bypass / restriction off)
 */
public record MyAccessResponse(
        boolean restricted, String role,
        boolean view, boolean write, boolean cost, boolean manageMembers
) {}