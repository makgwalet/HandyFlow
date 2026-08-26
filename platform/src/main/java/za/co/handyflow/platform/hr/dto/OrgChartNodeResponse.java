package za.co.handyflow.platform.hr.dto;

import java.util.UUID;

/**
 * FIX: backlog 3.6. Deliberately a flat list, not a pre-nested tree —
 * every real org-chart UI library (d3-based or otherwise) wants a flat
 * parent-id list to build the tree from client-side, not a server-built
 * recursive structure. This also sidesteps a real data-integrity risk a
 * server-side tree build would have to handle: nothing currently
 * prevents managerId from forming a cycle (A reports to B, B reports to
 * A) or pointing at a terminated/nonexistent employee — a flat list
 * means that's the frontend's rendering concern, not something this
 * endpoint needs to detect and reject.
 */
public record OrgChartNodeResponse(
        UUID id, String fullName, String jobTitle, String department, UUID managerId
) {}