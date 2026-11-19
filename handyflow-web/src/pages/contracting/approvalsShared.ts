// Shared types and rules for the approvals screen.

export type ApprovalAction =
  | 'SEND_FOR_SIGNING' | 'TERMINATE_CONTRACT' | 'PUBLISH_CLAUSE' | 'PUBLISH_TEMPLATE'

export interface Approval {
  id: string
  action: ApprovalAction
  subjectId: string
  subjectLabel: string | null
  status: 'PENDING' | 'APPROVED' | 'REJECTED' | 'CANCELLED' | 'INVALIDATED' | 'CONSUMED'
  requestedBy: string
  requestedAt: string
  requestNote: string | null
  decidedBy: string | null
  decidedAt: string | null
  decisionNote: string | null
  soleUserException: boolean
  exceptionReason: string | null
  mine: boolean
  canDecide: boolean
}

export interface PolicyRow { action: ApprovalAction; required: boolean; allowSoleUserException: boolean; usingDefault: boolean }

export const MIN_EXCEPTION_REASON = 15

export const ACTION_LABEL: Record<string, string> = {
  SEND_FOR_SIGNING: 'Send for signing',
  TERMINATE_CONTRACT: 'Terminate contract',
  TERMINATE: 'Terminate contract',
  PUBLISH_CLAUSE: 'Publish clause',
  PUBLISH_TEMPLATE: 'Publish template',
}

/** Pure rule for whether the decision form may be submitted. Exported for tests. */
export function decisionBlocker(a: Pick<Approval, 'mine'>, soleUser: boolean, reason: string, rejecting: boolean): string | null {
  if (rejecting) return null
  if (a.mine && !soleUser) return 'You asked for this, so someone else needs to approve it.'
  if (soleUser && reason.trim().length < MIN_EXCEPTION_REASON) return `Give a reason of at least ${MIN_EXCEPTION_REASON} characters.`
  return null
}


/** Unwrap ApiResponse<List<T>>. */
export function listOf<T>(r: { data?: { data?: unknown } & unknown }): T[] {
  const payload = (r.data as { data?: unknown } | undefined)?.data ?? r.data
  return Array.isArray(payload) ? (payload as T[]) : []
}
