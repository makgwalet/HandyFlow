// Pure rules for withdrawing a contract that was sent for signing, and for starting a new draft from the withdrawn one.

export const MIN_WITHDRAW_REASON = 10
export type ApprovalProgress = 'NONE' | 'PENDING' | 'APPROVED'

interface PartyLike { signingStatus: string; partyRole?: string }

const signers = (p: PartyLike[]) => p.filter(x => x.partyRole !== 'WITNESS')

/** Only a contract that is out for signature can be withdrawn. One every signer has signed is executed: it is ended another way. */
export function withdrawAvailability(status: string, parties: PartyLike[]): { available: boolean; why?: string } {
  if (status !== 'SENT') return { available: false, why: 'Only a contract that is out for signature can be withdrawn.' }
  const s = signers(parties)
  if (s.length > 0 && s.every(p => p.signingStatus === 'SIGNED'))
    return { available: false, why: 'Everyone has signed. End it through termination or an amendment instead.' }
  return { available: true }
}

export interface WithdrawStep {
  /** What the main button does: ask a second person first, wait for them, or withdraw. */
  step: 'ASK' | 'WAITING' | 'WITHDRAW'
  label: string
  disabled: boolean
  why?: string
}

export function withdrawStep(required: boolean, state: ApprovalProgress, reason: string, busy: boolean): WithdrawStep {
  if (required && state === 'NONE') return { step: 'ASK', label: 'Ask for approval to withdraw', disabled: busy }
  if (required && state === 'PENDING')
    return { step: 'WAITING', label: 'Waiting for approval to withdraw', disabled: true, why: 'Someone else needs to approve this under Approvals.' }
  const short = reason.trim().length < MIN_WITHDRAW_REASON
  return { step: 'WITHDRAW', label: 'Withdraw signing request', disabled: busy || short, why: short ? `Give a reason of at least ${MIN_WITHDRAW_REASON} characters.` : undefined }
}

/** The new draft is offered once, on a withdrawn contract. */
export function canReviseWithdrawn(status: string, canManage: boolean): boolean {
  return status === 'WITHDRAWN' && canManage
}
