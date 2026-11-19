// Pure rules for the contract workspace: which tabs exist and what the person should do next.

export type WorkspaceTab = 'overview' | 'document' | 'parties' | 'negotiation' | 'approvals' | 'activity'
export const WORKSPACE_TABS: { id: WorkspaceTab; label: string }[] = [
  { id: 'overview', label: 'Overview' },
  { id: 'document', label: 'Document' },
  { id: 'parties', label: 'Parties & signatures' },
  { id: 'negotiation', label: 'Negotiation' },
  { id: 'approvals', label: 'Approvals' },
  { id: 'activity', label: 'Activity' },
]

export interface WorkspacePartyInput { fullName: string; email?: string | null; phone?: string | null; signingStatus: string; partyRole?: string }
export interface WorkspaceInput {
  status: string
  parties?: WorkspacePartyInput[]
  unresolvedAmendments?: number
  canManage: boolean
  /** Whether sending needs approval for this tenant, and where that approval stands. */
  sendApproval?: { required: boolean; state: 'NONE' | 'PENDING' | 'APPROVED' }
}

export interface NextAction {
  /** Short instruction shown to the person. */
  title: string
  detail?: string
  /** Tab that holds what they need, if any. */
  tab?: WorkspaceTab
  /** Contract action the banner can trigger directly (the same one the action bar offers). */
  action?: 'submit-for-review' | 'send-for-signing'
  tone: 'todo' | 'waiting' | 'done'
}

const signers = (p: WorkspacePartyInput[]) => p.filter(x => x.partyRole !== 'WITNESS')

/** The single most useful next step for this contract, from what is already on screen. */
export function nextRequiredAction(c: WorkspaceInput): NextAction {
  const parties = c.parties ?? []
  const draftish = c.status === 'DRAFT' || c.status === 'UNDER_REVIEW'

  if (draftish) {
    if (!c.canManage) return { title: 'This contract is being prepared', detail: 'You can read it; changes need contract manage permission.', tone: 'waiting' }
    if (signers(parties).length === 0)
      return { title: 'Add the parties who will sign', detail: 'A contract cannot be sent without at least one party.', tab: 'parties', tone: 'todo' }
    const noContact = signers(parties).filter(p => !p.email?.trim() && !p.phone?.trim())
    if (noContact.length)
      return { title: `Add contact details for ${noContact.map(p => p.fullName).join(', ')}`, detail: 'A signing link needs an email address or phone number.', tab: 'parties', tone: 'todo' }
    if ((c.unresolvedAmendments ?? 0) > 0)
      return { title: `Resolve ${c.unresolvedAmendments} amendment request${c.unresolvedAmendments === 1 ? '' : 's'}`, detail: 'Counterparties have asked for changes.', tab: 'activity', tone: 'todo' }
    if (c.status === 'DRAFT') return { title: 'Submit for review', detail: 'Review the document and parties, then submit.', action: 'submit-for-review', tone: 'todo' }
    const ap = c.sendApproval
    if (ap?.required && ap.state === 'NONE')
      return { title: 'Ask for approval to send', detail: 'Your approval policy needs a second person to approve before this is sent.', tab: 'approvals', tone: 'todo' }
    if (ap?.required && ap.state === 'PENDING')
      return { title: 'Waiting for approval to send', detail: 'Someone else needs to approve this under Approvals.', tab: 'approvals', tone: 'waiting' }
    return { title: 'Send for signing', detail: 'Every placeholder must be filled before it can be sent.', action: 'send-for-signing', tone: 'todo' }
  }
  if (c.status === 'SENT') {
    const waiting = signers(parties).filter(p => p.signingStatus !== 'SIGNED')
    return {
      title: waiting.length ? `Waiting for ${waiting.map(p => p.fullName).join(', ')} to sign` : 'Waiting for signatures',
      detail: 'You can resend a signing link from the parties tab.', tab: 'parties', tone: 'waiting',
    }
  }
  if (c.status === 'SIGNED') return { title: 'Fully signed', detail: 'Share the executed copy or download the PDF.', tone: 'done' }
  if (c.status === 'TERMINATED') return { title: 'This contract was terminated', tone: 'done' }
  if (c.status === 'EXPIRED') return { title: 'This contract has expired', tone: 'done' }
  return { title: 'No action needed', tone: 'done' }
}
