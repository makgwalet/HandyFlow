// Wording and checks for links to an outside learning system. The server enforces every rule.
// A link is a plain https address. No password, key or token is kept, and nothing is synced from the other system.

export type LmsLink = {
  id: string; programmeVersionId: string; versionNo: number | null; programmeId: string; programmeTitle: string | null; systemName: string; launchUrl: string
  notes: string | null; status: string; endedReason: string | null; createdAt: string
}

export const MIN_REASON = 10
export const LMS_NOTE = "Learners sign in on the other system. Nothing is synced, so lesson progress is still recorded here by staff. Do not put a password, key or token in the link."
export const STATUSES: Record<string, string> = { ACTIVE: "Active", ENDED: "Ended" }
export const label = (m: Record<string, string>, k: string | null | undefined) => (k ? m[k] ?? k : "—")
export const statusTone = (s: string): "good" | "warn" | "bad" | "neutral" => (s === "ACTIVE" ? "good" : "neutral")

const SECRET = /[?&;](token|password|passwd|pwd|secret|key|apikey|api_key|access_token|auth)=/i
export type LinkForm = { programmeId: string; programmeVersionId: string; systemName: string; launchUrl: string; notes: string }
export const emptyLink: LinkForm = { programmeId: "", programmeVersionId: "", systemName: "", launchUrl: "", notes: "" }
export const linkForm = (l: LmsLink): LinkForm => ({ programmeId: "", programmeVersionId: l.programmeVersionId, systemName: l.systemName, launchUrl: l.launchUrl, notes: l.notes ?? "" })

export function linkProblem(f: LinkForm, isNew: boolean): string | null {
  if (isNew && !f.programmeVersionId) return "Choose the programme and version"
  if (f.systemName.trim().length < 2) return "Give the name of the learning system"
  const u = f.launchUrl.trim()
  if (!/^https:\/\/[^\s@]+$/i.test(u)) return "Enter a link that starts with https:// and has no username or password in it"
  if (SECRET.test(u)) return "The link must not carry a password, key or token. Remove it and let people sign in on the other system"
  return null
}
export const linkBody = (f: LinkForm) => ({ programmeVersionId: f.programmeVersionId, systemName: f.systemName.trim(), launchUrl: f.launchUrl.trim(), notes: f.notes.trim() || null })
export const updateBody = (f: LinkForm) => ({ systemName: f.systemName.trim(), launchUrl: f.launchUrl.trim(), notes: f.notes.trim() || null })
export const endProblem = (r: string): string | null => (r.trim().length >= MIN_REASON ? null : `Say why, in at least ${MIN_REASON} characters`)
