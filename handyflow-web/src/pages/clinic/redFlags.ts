// Red flags raised by the questionnaires (Q-5, patch 0181). A flag stays on screen until a clinician has reviewed it; reviewed means seen,
// not resolved. Nothing here judges a flag or suggests what it means.

export interface ActiveRedFlag {
  groupCode: string; groupName: string; groupVersion: number
  flagCode: string; label: string; severity: "INFO" | "URGENT" | string; message?: string | null
  reviewed: boolean; reviewedBy?: string | null; reviewedAt?: string | null
}

export const flagKey = (f: { groupCode: string; flagCode: string }) => `${f.groupCode}:${f.flagCode}`

/** Open flags first (urgent before info), then the reviewed ones. */
export function splitFlags(flags: ActiveRedFlag[]) {
  const rank = (f: ActiveRedFlag) => (f.severity === "URGENT" ? 0 : 1)
  const open = flags.filter(f => !f.reviewed).sort((a, b) => rank(a) - rank(b))
  return { open, done: flags.filter(f => f.reviewed), urgentOpen: open.some(f => f.severity === "URGENT") }
}

/** "Source: Respiratory (v2)" — where the flag came from. */
export const flagSource = (f: ActiveRedFlag) => `${f.groupName} (v${f.groupVersion})`

export const FLAG_REASON_MAX = 500
export function flagReasonProblem(reason: string): string | null {
  if (!reason.trim()) return "Give a reason to sign without reviewing the red flags."
  if (reason.trim().length > FLAG_REASON_MAX) return `Keep the reason under ${FLAG_REASON_MAX} characters.`
  return null
}
