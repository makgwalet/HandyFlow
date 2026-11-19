// Shared types and rules for the approvals screen.

export type ApprovalAction =
  | 'SEND_FOR_SIGNING' | 'TERMINATE' | 'PUBLISH_CLAUSE' | 'PUBLISH_TEMPLATE'

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

export type ApprovalState = 'NONE' | 'PENDING' | 'APPROVED'

/** Where approval stands for one action on one subject. Rejected, withdrawn, invalidated and used-up requests count as none. */
export function approvalState(list: Pick<Approval, 'action' | 'subjectId' | 'status'>[], action: ApprovalAction, subjectId: string): ApprovalState {
  const mine = list.filter(a => a.action === action && a.subjectId === subjectId)
  if (mine.some(a => a.status === 'APPROVED')) return 'APPROVED'
  if (mine.some(a => a.status === 'PENDING')) return 'PENDING'
  return 'NONE'
}

/** Approvals a person can still ask for on a contract in this status (none already waiting or granted). */
export function requestableActions(status: string, list: Pick<Approval, 'action' | 'subjectId' | 'status'>[], contractId: string): ApprovalAction[] {
  const out: ApprovalAction[] = []
  if (status === 'UNDER_REVIEW' && approvalState(list, 'SEND_FOR_SIGNING', contractId) === 'NONE') out.push('SEND_FOR_SIGNING')
  if (status === 'SIGNED' && approvalState(list, 'TERMINATE', contractId) === 'NONE') out.push('TERMINATE')
  return out
}
