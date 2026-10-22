// What the recruiter screens need to decide about approvals and applicant data retention. Plain values in, plain values out.

export interface ApprovalView {
  status: 'NOT_SUBMITTED' | 'SUBMITTED' | 'APPROVED' | 'REJECTED'
  required: boolean
  submittedBy: string | null; submittedByName: string | null; submittedAt: string | null
  decidedBy: string | null; decidedByName: string | null; decidedAt: string | null
  note: string | null
  canDecide: boolean
}

export interface RecSettings { requireJobApproval: boolean; requireOfferApproval: boolean; retentionMonths: number }

export interface RemovalCandidate { applicantId: string; name: string; email: string; lastActivity: string; dueSince: string; applications: number; hasCv: boolean }

export interface RemovalResult { removed: number; refused: { applicantId: string; reason: string }[] }

export const APPROVAL_LABEL: Record<ApprovalView['status'], string> = {
  NOT_SUBMITTED: 'Not sent for approval', SUBMITTED: 'Waiting for approval', APPROVED: 'Approved', REJECTED: 'Not approved',
}

export function recruiterRights(permissions: readonly string[]) {
  const has = (p: string) => permissions.includes(p)
  return { read: has('RECRUITER_READ') || has('RECRUITER_MANAGE') || has('RECRUITER_ADMIN'), manage: has('RECRUITER_MANAGE') || has('RECRUITER_ADMIN'), admin: has('RECRUITER_ADMIN') }
}

/** Whether the panel has anything to say: the company asks for the approval, or one has already been started. */
export function showApproval(v: ApprovalView | undefined): boolean {
  return !!v && (v.required || v.status !== 'NOT_SUBMITTED')
}

/** Whether to offer "send for approval": it has not been sent, or it came back not approved. */
export function mayResubmit(v: ApprovalView): boolean {
  return v.status === 'NOT_SUBMITTED' || v.status === 'REJECTED'
}

export function decisionProblem(approve: boolean, note: string): string | null {
  if (!approve && !note.trim()) return 'Say why it is not approved.'
  if (note.length > 500) return 'The note is too long (500 characters at most).'
  return null
}

export function retentionProblem(months: string): string | null {
  const n = Number(months)
  if (!Number.isInteger(n) || n < 3 || n > 60) return 'Keep applicant details for between 3 and 60 months.'
  return null
}

const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec']
/** 22 Oct 2026, in South African time. */
export function fmtDay(iso: string | null | undefined): string {
  if (!iso) return '–'
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return '–'
  const parts = new Intl.DateTimeFormat('en-ZA', { timeZone: 'Africa/Johannesburg', year: 'numeric', month: 'numeric', day: 'numeric' }).formatToParts(d)
  const get = (t: string) => Number(parts.find(p => p.type === t)?.value)
  return `${get('day')} ${MONTHS[get('month') - 1]} ${get('year')}`
}
