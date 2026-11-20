// Pure helpers for the committee charter screen (chunk 35). They mirror the server's checks (CommitteeCharterRules.record and CommitteeCharterService.record) so people are told before
// the round trip; the server decides.

export type WrittenMode = "NOT_ALLOWED" | "UNANIMOUS" | "MAJORITY_OF_ALL" | "EITHER"

export const WRITTEN_CHOICES: { value: WrittenMode; label: string }[] = [
  { value: "NOT_ALLOWED", label: "Not allowed" },
  { value: "UNANIMOUS", label: "Only if every member agrees" },
  { value: "MAJORITY_OF_ALL", label: "Only by a majority of all members" },
  { value: "EITHER", label: "Either way" },
]
export const writtenLabel = (m: WrittenMode) => WRITTEN_CHOICES.find(c => c.value === m)?.label ?? m

export const MIN_REASON = 10
export const MIN_PERCENT = 51, MAX_PERCENT = 100, DEFAULT_PERCENT = 75

export interface CharterForm {
  reference: string; adoptedOn: string; effectiveFrom: string; castingVote: boolean; writtenMode: WrittenMode; quorumAfterRecusal: boolean; percent: string; reason: string
}
export interface CharterRulesView { castingVote: boolean; writtenMode: WrittenMode; quorumAfterRecusal: boolean; specialPercent: number }

/** Start a new version from the rules now in force (or the house defaults when nothing is recorded), so only the change needs typing. */
export function startForm(current: CharterRulesView | null, today: string): CharterForm {
  return {
    reference: "", adoptedOn: "", effectiveFrom: today,
    castingVote: current?.castingVote ?? false, writtenMode: current?.writtenMode ?? "EITHER", quorumAfterRecusal: current?.quorumAfterRecusal ?? true,
    percent: String(current?.specialPercent ?? DEFAULT_PERCENT), reason: "",
  }
}

export const percentOk = (s: string) => /^\d{1,3}$/.test(s.trim()) && Number(s) >= MIN_PERCENT && Number(s) <= MAX_PERCENT

/** First problem with the form, or null when it can be sent. */
export function formProblem(f: CharterForm): string | null {
  if (!f.adoptedOn) return "Say when the charter was adopted"
  if (!f.effectiveFrom) return "Say when the rules take effect"
  if (f.effectiveFrom < f.adoptedOn) return "The rules cannot take effect before the charter was adopted"
  if (!percentOk(f.percent)) return `A special resolution percentage must be a whole number from ${MIN_PERCENT} to ${MAX_PERCENT}`
  if (f.reference.length > 120) return "The charter reference is too long (120 characters at most)"
  if (f.reason.trim().length < MIN_REASON) return `Give a reason of at least ${MIN_REASON} characters`
  return null
}

export function sameAsCurrent(f: CharterForm, c: CharterRulesView | null): boolean {
  return !!c && c.castingVote === f.castingVote && c.writtenMode === f.writtenMode && c.quorumAfterRecusal === f.quorumAfterRecusal && percentOk(f.percent) && c.specialPercent === Number(f.percent)
}

/** What changes between two versions, in words (oldest rules first). Empty when nothing differs. */
export function changes(from: CharterRulesView | null, to: CharterRulesView): string[] {
  const yn = (b: boolean) => (b ? "yes" : "no")
  const base: CharterRulesView = from ?? { castingVote: false, writtenMode: "EITHER", quorumAfterRecusal: true, specialPercent: DEFAULT_PERCENT }
  const out: string[] = []
  if (base.castingVote !== to.castingVote) out.push(`Casting vote: ${yn(base.castingVote)} → ${yn(to.castingVote)}`)
  if (base.writtenMode !== to.writtenMode) out.push(`Written resolutions: ${writtenLabel(base.writtenMode).toLowerCase()} → ${writtenLabel(to.writtenMode).toLowerCase()}`)
  if (base.quorumAfterRecusal !== to.quorumAfterRecusal) out.push(`Quorum after recusal: ${yn(base.quorumAfterRecusal)} → ${yn(to.quorumAfterRecusal)}`)
  if (base.specialPercent !== to.specialPercent) out.push(`Special resolutions: ${base.specialPercent}% → ${to.specialPercent}%`)
  return out
}

/** React Query key for one committee's charter (under "lc-com", so the tab's refresh also refreshes it). */
export const charterKey = (committeeId: string) => ["lc-com", "charter", committeeId]
