// Pure helpers for committee proxies (kept out of the component file so react-refresh stays happy). They mirror the server's rules so people are told before the round trip;
// the server decides.

export interface PMember { id: string; memberName: string; memberRole: string; appointedOn: string; endedOn: string | null; active: boolean }
export interface LiveProxy { id: string; grantorMemberId: string; holderMemberId: string; status: string }

export const MAX_PER_HOLDER = 2
export const MIN_CLAUSE = 3, MIN_REASON = 10

/** Voting members who were on the committee on the meeting date: not observers, appointed on or before it, and not ended before it. */
export function votingMembers(members: PMember[], meetingDate: string): PMember[] {
  return members.filter(m => m.memberRole !== "OBSERVER" && m.appointedOn <= meetingDate && (m.endedOn === null || m.endedOn >= meetingDate))
}

const liveOnly = (proxies: LiveProxy[]) => proxies.filter(p => p.status === "ACTIVE")

/** Who can still give a proxy: not already giving one, and not holding one for somebody else. */
export function grantorChoices(members: PMember[], meetingDate: string, proxies: LiveProxy[]): PMember[] {
  const live = liveOnly(proxies)
  return votingMembers(members, meetingDate).filter(m => !live.some(p => p.grantorMemberId === m.id) && !live.some(p => p.holderMemberId === m.id))
}

/** Who can still hold one for this grantor: not the grantor, not someone who has given a proxy, and carrying fewer than the limit. */
export function holderChoices(members: PMember[], meetingDate: string, proxies: LiveProxy[], grantorId: string): PMember[] {
  const live = liveOnly(proxies)
  return votingMembers(members, meetingDate).filter(m => m.id !== grantorId && !live.some(p => p.grantorMemberId === m.id) && live.filter(p => p.holderMemberId === m.id).length < MAX_PER_HOLDER)
}

export function formProblem(f: { grantor: string; holder: string; clause: string; charter: boolean }): string | null {
  if (!f.grantor) return "Choose who is giving the proxy"
  if (!f.holder) return "Choose who will hold it"
  if (f.grantor === f.holder) return "A member cannot be their own proxy"
  if (f.clause.trim().length < MIN_CLAUSE) return `Say which clause allows proxies (at least ${MIN_CLAUSE} characters)`
  if (!f.charter) return "Confirm that the charter or standing orders allow proxies"
  return null
}

export const PROXY_CHOICES: { value: "FOR" | "AGAINST" | "ABSTAIN"; label: string }[] = [{ value: "FOR", label: "For" }, { value: "AGAINST", label: "Against" }, { value: "ABSTAIN", label: "Abstain" }]

export function revokeValid(reason: string): boolean { return reason.trim().length >= MIN_REASON }
