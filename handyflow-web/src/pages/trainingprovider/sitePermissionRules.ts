// Wording and checks for site delivery permissions: the provider's own record of who may deliver, assess or moderate at which site.
// It sits beside the appointment checks and does not replace them, and it is not a regulatory approval. The server enforces every rule.

export type SitePermission = {
  id: string; practitionerId: string; practitionerNo: string | null; practitionerName: string | null; siteId: string; siteName: string | null; role: string
  programmeId: string | null; programmeTitle: string | null; validFrom: string; validTo: string | null; basis: string; status: string; withdrawnReason: string | null
  inForceToday: boolean; createdAt: string
}

export const MIN_NOTE = 10
export const PERMISSION_NOTE = "This is the provider's own record of who works where. It sits beside the appointment checks and does not replace them, and it is not a regulatory approval."
export const COVER: Record<string, string> = {
  COVERED: "Placed at this site", NOT_IN_FORCE: "A site permission is recorded but not in force on the day", NOT_RECORDED: "No site permission recorded for this person at this site",
}

export type PermissionForm = { practitionerId: string; siteId: string; role: string; programmeId: string; validFrom: string; validTo: string; basis: string }
export const emptyPermission = (today: string): PermissionForm => ({ practitionerId: "", siteId: "", role: "FACILITATOR", programmeId: "", validFrom: today, validTo: "", basis: "" })
export function permissionProblem(f: PermissionForm): string | null {
  if (!f.practitionerId) return "Choose the person"
  if (!f.siteId) return "Choose the site"
  if (!f.role) return "Choose a role"
  if (!f.validFrom) return "Give the day the permission starts"
  if (f.validTo && f.validTo < f.validFrom) return "The end date cannot be before the start date"
  if (f.basis.trim().length < MIN_NOTE) return `Say why the person is placed at this site (at least ${MIN_NOTE} characters)`
  return null
}
export const permissionBody = (f: PermissionForm) => ({
  practitionerId: f.practitionerId, siteId: f.siteId, role: f.role, programmeId: f.programmeId || null, validFrom: f.validFrom, validTo: f.validTo || null, basis: f.basis.trim(),
})
export const withdrawProblem = (reason: string): string | null => (reason.trim().length >= MIN_NOTE ? null : `Give a reason of at least ${MIN_NOTE} characters`)
export const periodText = (p: Pick<SitePermission, "validFrom" | "validTo">): string => (p.validTo ? `${p.validFrom} to ${p.validTo}` : `from ${p.validFrom}, no end date`)
export const stateText = (p: Pick<SitePermission, "status" | "inForceToday" | "validFrom">, today: string): string =>
  p.status === "WITHDRAWN" ? "Withdrawn" : p.inForceToday ? "In force" : p.validFrom > today ? "Not started" : "Ended"
