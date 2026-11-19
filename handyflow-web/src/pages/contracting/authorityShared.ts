// Pure rules for the delegated-authority screen. The server decides everything; these mirror its checks so a form can say what is wrong
// before it is sent, and describe grants in plain words.

export type AuthorityAction = 'APPROVE' | 'NEGOTIATE' | 'SIGN' | 'RENEW' | 'TERMINATE'
export const AUTHORITY_ACTIONS: { id: AuthorityAction; label: string; hint: string }[] = [
  { id: 'APPROVE', label: 'Approve', hint: 'Approve sending a contract for signing, or withdrawing one' },
  { id: 'NEGOTIATE', label: 'Negotiate', hint: 'Accept a change a counterparty proposed' },
  { id: 'SIGN', label: 'Sign', hint: 'Sign for the organisation or for someone in person. Separate from approving' },
  { id: 'RENEW', label: 'Renew', hint: 'Decide to renew a contract' },
  { id: 'TERMINATE', label: 'Terminate', hint: 'Approve or carry out a termination' },
]
export const ACTION_WORD: Record<string, string> = Object.fromEntries(AUTHORITY_ACTIONS.map(a => [a.id, a.label]))

/** The roles an organisation can grant authority to (the contract permissions). */
export const AUTHORITY_ROLES: { id: string; label: string }[] = [
  { id: 'CONTRACTS_MANAGE', label: 'Contract managers' },
  { id: 'CONTRACTS_ADMIN', label: 'Contract administrators' },
  { id: 'CONTRACTS_SIGN', label: 'Contract signers' },
]

export interface Grant {
  id: string; userId: string | null; role: string | null; businessUnit: string | null
  contractTypes: string[]; actions: AuthorityAction[]
  unlimited: boolean; maxAmount: number | null
  effectiveFrom: string; expiresOn: string | null; conditions: string | null
  delegatedFromId: string | null; delegatedBy: string | null
  status: string; inForce: boolean; mine: boolean
  revokedAt: string | null; revokedReason: string | null; createdAt: string
}

export type GrantState = 'IN_FORCE' | 'SCHEDULED' | 'EXPIRED' | 'REVOKED' | 'SOURCE_ENDED'

export function grantState(g: Pick<Grant, 'status' | 'inForce' | 'effectiveFrom' | 'expiresOn' | 'delegatedFromId'>, today: string): GrantState {
  if (g.status === 'REVOKED') return 'REVOKED'
  if (g.inForce) return 'IN_FORCE'
  if (g.effectiveFrom > today) return 'SCHEDULED'
  if (g.expiresOn && g.expiresOn < today) return 'EXPIRED'
  return g.delegatedFromId ? 'SOURCE_ENDED' : 'EXPIRED'
}

export const STATE_LABEL: Record<GrantState, string> = {
  IN_FORCE: 'In force', SCHEDULED: 'Starts later', EXPIRED: 'Expired', REVOKED: 'Revoked', SOURCE_ENDED: 'Ended: the authority it came from ended',
}

export function limitText(g: Pick<Grant, 'unlimited' | 'maxAmount'>): string {
  if (g.unlimited) return 'No financial limit'
  return `Up to R${Number(g.maxAmount ?? 0).toLocaleString('en-ZA')}`
}

export function typesText(types: string[], label: (t: string) => string): string {
  return types.length === 0 ? 'All contract types' : types.map(label).join(', ')
}

export interface GrantForm {
  kind: 'USER' | 'ROLE'; userId: string; role: string
  actions: AuthorityAction[]; types: string[]
  unlimited: boolean; maxAmount: string
  from: string; to: string
}

/** What is missing or wrong with a new grant, or null when it can be sent. */
export function grantProblem(f: GrantForm): string | null {
  if (f.kind === 'USER' && !f.userId) return 'Choose the person.'
  if (f.kind === 'ROLE' && !f.role) return 'Choose the role.'
  if (f.actions.length === 0) return 'Choose at least one action.'
  if (!f.unlimited) {
    if (f.maxAmount.trim() === '' || Number(f.maxAmount) < 0 || Number.isNaN(Number(f.maxAmount))) return 'Enter the most this authority covers, or choose no financial limit.'
  }
  if (f.to && f.from && f.to < f.from) return 'The end date cannot be before the start date.'
  return null
}

export interface DelegationForm {
  toUserId: string; actions: AuthorityAction[]; types: string[]
  unlimited: boolean; maxAmount: string; from: string; to: string
}

/** The same limits the server applies: a delegation can never carry more than the authority it is made from. */
export function delegationProblem(src: Grant, f: DelegationForm, today: string): string | null {
  if (!f.toUserId) return 'Choose who it is delegated to.'
  if (f.actions.length === 0) return 'Choose at least one action to delegate.'
  if (!f.actions.every(a => src.actions.includes(a))) return 'You can only delegate actions you hold.'
  if (src.contractTypes.length > 0) {
    if (f.types.length === 0) return 'Your authority covers only some contract types, so choose the types you are delegating.'
    if (!f.types.every(t => src.contractTypes.includes(t))) return 'You can only delegate contract types you hold.'
  }
  if (!src.unlimited) {
    if (f.unlimited) return 'You cannot delegate more than your own financial limit.'
    if (f.maxAmount.trim() === '' || Number(f.maxAmount) < 0) return 'Enter the most the delegate may authorise.'
    if (Number(f.maxAmount) > Number(src.maxAmount)) return `You cannot delegate more than your own limit of R${Number(src.maxAmount).toLocaleString('en-ZA')}.`
  } else if (!f.unlimited && (f.maxAmount.trim() === '' || Number(f.maxAmount) < 0)) {
    return 'Enter the most the delegate may authorise, or choose no limit.'
  }
  if (!f.from || !f.to) return 'A delegation needs a start date and an end date.'
  if (f.to < f.from) return 'The end date cannot be before the start date.'
  if (f.to < today) return 'The end date has already passed.'
  if (f.from < src.effectiveFrom) return 'A delegation cannot start before your own authority does.'
  if (src.expiresOn && f.to > src.expiresOn) return `A delegation cannot outlast your own authority, which ends on ${src.expiresOn}.`
  return null
}

/** Enforcement cannot be switched on while nothing is in force, or nobody could approve, sign or terminate. */
export function enforcementBlocker(grants: Pick<Grant, 'inForce'>[]): string | null {
  return grants.some(g => g.inForce) ? null : 'Give someone authority first. With enforcement on and nobody authorised, no one could approve, sign or terminate.'
}
