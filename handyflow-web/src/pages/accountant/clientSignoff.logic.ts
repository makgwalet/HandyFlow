// Pure helpers for the client team and engagement sign-off panel (finance F15).

export type EngagementStatus = "OPEN" | "PREPARED" | "REVIEWED" | "SIGNED_OFF"
export type EngagementAction = "prepare" | "review" | "sign-off" | "reopen" | "reopen-signed-off"

export interface EngagementLike { status: EngagementStatus | string; unpostedJournals?: number }

export interface ActionSpec {
  action: EngagementAction
  label: string
  needsReason: boolean
  permission: "ACCOUNTANT_WRITE" | "ACCOUNTANT_SIGNOFF"
}

export const STATUS_LABEL: Record<string, string> = {
  OPEN: "Open", PREPARED: "Prepared", REVIEWED: "Reviewed", SIGNED_OFF: "Signed off",
}

export const ROLE_LABEL: Record<string, string> = {
  PREPARER: "Preparer", REVIEWER: "Reviewer", PARTNER: "Partner",
}

/** The next step forward, then the ways back, for an engagement in this status. */
export function actionsFor(e: EngagementLike): ActionSpec[] {
  switch (e.status) {
    case "OPEN":
      return [{ action: "prepare", label: "Mark prepared", needsReason: false, permission: "ACCOUNTANT_WRITE" }]
    case "PREPARED":
      return [
        { action: "review", label: "Mark reviewed", needsReason: false, permission: "ACCOUNTANT_WRITE" },
        { action: "reopen", label: "Send back", needsReason: true, permission: "ACCOUNTANT_WRITE" },
      ]
    case "REVIEWED":
      return [
        { action: "sign-off", label: "Sign off", needsReason: false, permission: "ACCOUNTANT_SIGNOFF" },
        { action: "reopen", label: "Send back", needsReason: true, permission: "ACCOUNTANT_WRITE" },
      ]
    case "SIGNED_OFF":
      return [{ action: "reopen-signed-off", label: "Reopen signed-off year", needsReason: true, permission: "ACCOUNTANT_SIGNOFF" }]
    default:
      return []
  }
}

/** Actions the person may take given the permissions they hold. */
export function allowedActions(e: EngagementLike, can: (permission: string) => boolean): ActionSpec[] {
  return actionsFor(e).filter(a => can(a.permission))
}

/** Reasons must be at least 10 characters, as the server requires. */
export function reasonOk(reason: string): boolean {
  return reason.trim().length >= 10
}

/** Roles already held by this person on the client, so the same one is not offered twice. */
export function rolesLeftFor(userId: string, assignments: { userId: string; role: string }[]): string[] {
  const held = new Set(assignments.filter(a => a.userId === userId).map(a => a.role))
  return Object.keys(ROLE_LABEL).filter(r => !held.has(r))
}

/** Plain message for a failed call. */
export function failureText(err: any): string {
  const code = err?.response?.data?.code ?? err?.response?.data?.errorCode
  if (code === "CLIENT_NOT_ASSIGNED" || err?.response?.status === 403) {
    return "You are not assigned to this client."
  }
  return err?.response?.data?.message ?? "Something went wrong. Please try again."
}
