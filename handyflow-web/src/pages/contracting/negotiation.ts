// Types and helpers for counterparty negotiation. Kept out of the components so fast refresh works and the rules can be tested alone.
import axios from 'axios'
import { toText } from './wordDiff'

export type ProposalKind = 'ADD' | 'DELETE' | 'REPLACE'
export type ProposalStatus = 'PENDING_REVIEW' | 'ACCEPTED' | 'REJECTED' | 'WITHDRAWN' | 'SUPERSEDED' | 'CONFLICTED' | 'APPLIED' | 'COUNTERED'
export type NegotiationStatus = 'OPEN' | 'STAFF_REVIEW' | 'CHANGES_REQUIRED' | 'READY_FOR_SIGNATURE' | 'CLOSED'

export interface NegProposal {
  id: string; partyId: string; partyName?: string | null; kind: ProposalKind; baseVersionNo: number; blockIndex?: number | null
  originalHtml?: string | null; proposedHtml?: string | null; rationale?: string | null; status: ProposalStatus; conflictWith?: string | null
  decidedAt?: string | null; decisionNote?: string | null; appliedVersionNo?: number | null; targetStillPresent: boolean; createdAt: string
  /** Two-way negotiation. Older responses carry none of these, which reads as a counterparty proposal in round 1. */
  authorType?: 'STAFF' | 'COUNTERPARTY'; roundNo?: number; parentId?: string | null; awaiting?: 'STAFF' | 'COUNTERPARTY' | null; decidedByCounterparty?: boolean
}
export interface NegComment {
  id: string; authorType: 'STAFF' | 'COUNTERPARTY'; partyId?: string | null; blockIndex?: number | null; blockExcerpt?: string | null
  proposalId?: string | null; parentId?: string | null; body: string; internalOnly: boolean; createdAt: string
}
export interface NegEvent { id: string; actorType: string; actorUserId?: string | null; partyId?: string | null; eventType: string; detail?: string | null; createdAt: string }
export interface NegCounts { pending: number; conflicted: number; accepted: number; applied: number; rejected: number; withdrawn: number; superseded: number; countered?: number }
export interface NegSummary {
  id: string; contractId: string; status: NegotiationStatus; responseDeadline?: string | null; deadlinePassed: boolean; baseVersionNo: number
  finalVersionNo?: number | null; finalBodyHash?: string | null; openedAt: string; readyAt?: string | null; closedAt?: string | null; closeReason?: string | null; counts: NegCounts
}
export interface StaffNegotiation { negotiation: NegSummary | null; currentVersionNo: number; body: string; blocks: string[]; proposals: NegProposal[]; comments: NegComment[]; events: NegEvent[] }
export interface CounterpartyNegotiation {
  status: NegotiationStatus; responseDeadline?: string | null; canPropose: boolean; currentVersionNo: number; body: string; blocks: string[]
  proposals: NegProposal[]; comments: NegComment[]
}
export interface NegLink { id: string; partyId: string; partyName?: string | null; expiresAt: string; revokedAt?: string | null; revokedReason?: string | null; deliveryStatus?: string | null; lastAccessedAt?: string | null; createdAt: string; active: boolean }
export interface IssuedLink { linkId: string; partyId: string; url: string; expiresAt: string; deliveryStatus: string }
export interface LinkInfo { contractTitle: string; channel: 'SMS' | 'EMAIL'; maskedDestination: string; expiresAt: string }

export const STATUS_LABEL: Record<NegotiationStatus | 'NOT_STARTED', string> = {
  NOT_STARTED: 'Not started', OPEN: 'Open for proposals', STAFF_REVIEW: 'Waiting for your review', CHANGES_REQUIRED: 'Accepted changes to apply',
  READY_FOR_SIGNATURE: 'Ready for signature', CLOSED: 'Closed',
}
export const COUNTERPARTY_STATUS_LABEL: Record<NegotiationStatus, string> = {
  OPEN: 'Open for your proposals', STAFF_REVIEW: 'Waiting for the sender to review', CHANGES_REQUIRED: 'The sender is applying accepted changes',
  READY_FOR_SIGNATURE: 'Final wording agreed', CLOSED: 'Closed',
}
export const PROPOSAL_LABEL: Record<ProposalStatus, string> = {
  PENDING_REVIEW: 'Waiting for review', ACCEPTED: 'Accepted', REJECTED: 'Rejected', WITHDRAWN: 'Withdrawn', SUPERSEDED: 'Superseded',
  CONFLICTED: 'Conflicts with another proposal', APPLIED: 'Applied to the contract', COUNTERED: 'Answered with new wording',
}
export const KIND_LABEL: Record<ProposalKind, string> = { ADD: 'Addition', DELETE: 'Deletion', REPLACE: 'Replacement' }
export const EVENT_LABEL: Record<string, string> = {
  OPENED: 'Negotiation opened', DEADLINE_SET: 'Deadline changed', READY_FOR_SIGNATURE: 'Marked ready for signature', CLOSED: 'Closed', REOPENED: 'Reopened',
  PROPOSAL_SUBMITTED: 'Proposal submitted', PROPOSAL_WITHDRAWN: 'Proposal withdrawn', PROPOSAL_ACCEPTED: 'Proposal accepted', PROPOSAL_REJECTED: 'Proposal rejected',
  PROPOSALS_APPLIED: 'Accepted proposals applied', STAFF_COMMENT: 'Staff comment', INTERNAL_NOTE: 'Internal note', COUNTERPARTY_COMMENT: 'Counterparty comment',
  LINK_ISSUED: 'Link issued', LINK_REVOKED: 'Link revoked', CODE_REQUESTED: 'Code requested', CODE_FAILED: 'Wrong code entered', ACCESS_VERIFIED: 'Identity confirmed',
  FROZEN_ON_SEND: 'Closed because the contract was sent',
  PROPOSAL_COUNTERED: 'Proposal answered with new wording', STAFF_PROPOSAL_SUBMITTED: 'You proposed a change', STAFF_PROPOSAL_WITHDRAWN: 'You withdrew a proposal',
  STAFF_PROPOSAL_ACCEPTED: 'Counterparty accepted your proposal', STAFF_PROPOSAL_REJECTED: 'Counterparty rejected your proposal',
}

/** Proposals that need a decision from staff. A proposal staff made themselves waits on the counterparty, not on staff. */
export const needsDecision = (p: NegProposal): boolean => (p.status === 'PENDING_REVIEW' || p.status === 'CONFLICTED') && p.awaiting !== 'COUNTERPARTY'
/** Proposals staff made that the counterparty has not answered yet. */
export const waitingOnCounterparty = (p: NegProposal): boolean => p.status === 'PENDING_REVIEW' && p.awaiting === 'COUNTERPARTY'
/** Proposals staff sent that the counterparty can still answer, seen from the counterparty side. */
export const needsMyAnswer = (p: NegProposal): boolean => p.status === 'PENDING_REVIEW' && p.awaiting === 'COUNTERPARTY'
export const byStaff = (p: NegProposal): boolean => p.authorType === 'STAFF'
export const roundOf = (p: NegProposal): number => p.roundNo ?? 1
/** Proposals that are still in play (can be withdrawn by their author, or rejected by the other side). */
export const isLive = (p: NegProposal): boolean => p.status === 'PENDING_REVIEW' || p.status === 'CONFLICTED' || p.status === 'ACCEPTED'

/** Every round of one exchange, oldest first, ending with the proposal that is currently in play. Proposals with no parent stand alone. */
export function threadOf(all: NegProposal[], p: NegProposal): NegProposal[] {
  const byId = new Map(all.map(x => [x.id, x]))
  const chain: NegProposal[] = [p]
  const seen = new Set([p.id])
  for (let cur = p; cur.parentId; ) {
    const up = byId.get(cur.parentId)
    if (!up || seen.has(up.id)) break
    chain.unshift(up); seen.add(up.id); cur = up
  }
  return chain
}

/** The proposals to show as exchanges: those no later round answers. Earlier rounds appear inside their exchange. */
export function exchanges(all: NegProposal[]): NegProposal[][] {
  const answered = new Set(all.map(x => x.parentId).filter((x): x is string => !!x))
  return all.filter(p => !answered.has(p.id)).map(p => threadOf(all, p))
}

/** The wording to propose, for a change staff make. Same shape the counterparty page builds. */
export function staffProposalBody(kind: ProposalKind, blocks: string[], blockIndex: number, text: string, rationale: string) {
  const block = blockIndex >= 0 ? blocks[blockIndex] ?? null : null
  return {
    kind, blockIndex, originalHtml: block,
    proposedHtml: kind === 'DELETE' ? null : buildReplacement(kind === 'REPLACE' ? block : null, text),
    rationale: rationale.trim() || null,
  }
}

const escapeHtml = (s: string) => s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')

/** The tag a block is written in, when it is a plain paragraph or heading that can be rewritten as text; otherwise null. */
export function textBlockTag(blockHtml: string): string | null {
  const m = /^<(p|h[1-6])(\s[^>]*)?>/i.exec(blockHtml.trim())
  return m ? m[1].toLowerCase() : null
}

/** True when the block is plain text in a paragraph or heading, so it can be edited as text without losing structure. */
export const canEditAsText = (blockHtml: string): boolean => {
  const tag = textBlockTag(blockHtml)
  return tag != null && !/<(?!\/?(?:p|h[1-6]|br)\b)/i.test(blockHtml)
}

/** The wording to propose for a block, from text typed by the counterparty. Keeps the block's tag; blank lines start new paragraphs. */
export function buildReplacement(originalBlock: string | null, text: string): string {
  const tag = (originalBlock && textBlockTag(originalBlock)) || 'p'
  const parts = text.replace(/\r\n/g, '\n').split(/\n{2,}/).map(t => t.trim()).filter(Boolean)
  return parts.map((t, i) => `<${i === 0 ? tag : 'p'}>${escapeHtml(t).replace(/\n/g, '<br>')}</${i === 0 ? tag : 'p'}>`).join('')
}

/** A block's text, for prefilling an editor and for short labels. */
export const blockText = (html: string): string => toText(html)

export const excerpt = (html: string, max = 80): string => {
  const t = toText(html).replace(/\s+/g, ' ').trim()
  return t.length > max ? t.slice(0, max - 1) + '…' : t
}

/** Whole days from today to the deadline; negative once it has passed. Null with no deadline. */
export function daysLeft(deadline: string | null | undefined, now: Date = new Date()): number | null {
  if (!deadline) return null
  const [y, m, d] = deadline.split('-').map(Number)
  const end = Date.UTC(y, m - 1, d)
  const today = Date.UTC(now.getFullYear(), now.getMonth(), now.getDate())
  return Math.round((end - today) / 86_400_000)
}

export function deadlineText(deadline: string | null | undefined, now: Date = new Date()): string {
  const n = daysLeft(deadline, now)
  if (n == null) return 'No deadline'
  if (n < 0) return `Deadline passed (${deadline})`
  if (n === 0) return `Respond today (${deadline})`
  return `Respond by ${deadline} (${n} day${n === 1 ? '' : 's'} left)`
}

/** A link is shown to staff as a short status. */
export function linkState(l: NegLink, now: Date = new Date()): 'Active' | 'Revoked' | 'Expired' {
  if (l.revokedAt) return 'Revoked'
  return new Date(l.expiresAt).getTime() <= now.getTime() ? 'Expired' : 'Active'
}

// ── public (counterparty) client ─────────────────────────────────────────────

const BASE_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080'
/** A plain client with no staff token and no refresh handling: a 401 here means "confirm your code", never "log in". */
export const publicApi = axios.create({ baseURL: BASE_URL, headers: { 'Content-Type': 'application/json' } })
export const SESSION_HEADER = 'X-Negotiation-Session'

/** The HTTP status of a failed request. The server's body carries a message but no error code, so 404 means the link is not valid,
 *  401 means a code or session is needed, and 429 means too many requests. */
export const httpStatus = (e: unknown): number | undefined => (e as { response?: { status?: number } })?.response?.status

const sessionKey = (token: string) => `hf-neg-session:${token.slice(0, 12)}`
export function loadSession(token: string): string | null {
  try { return window.sessionStorage.getItem(sessionKey(token)) } catch { return null }
}
export function saveSession(token: string, session: string | null): void {
  try { if (session) window.sessionStorage.setItem(sessionKey(token), session); else window.sessionStorage.removeItem(sessionKey(token)) } catch { /* private mode */ }
}
