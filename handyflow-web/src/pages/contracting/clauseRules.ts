// Rules the clause screen mirrors from the backend (the server still decides).

export const CLAUSE_KINDS = [
  ['STANDARD', 'Standard wording', 'Ordinary wording. Evidence of the legal review is optional.'],
  ['CUSTOM', 'Custom wording', 'Written for this business. Evidence of the legal review is required before publishing.'],
  ['REGULATORY', 'Regulatory', 'Wording that reflects a legal requirement. Evidence is required before publishing.'],
  ['SENSITIVE', 'Sensitive', 'Liability, indemnity and similar. Evidence is required before publishing.'],
  ['SOURCED', 'From an outside source', 'Taken from a precedent or another firm. Evidence and the source are required.'],
] as const

export const kindLabel = (k?: string | null) => CLAUSE_KINDS.find(([v]) => v === k)?.[1] ?? 'Standard wording'

interface PublishInput { evidenceRequired?: boolean; evidenceRequiredReason?: string | null; evidenceDocumentId?: string | null }

/** Why Publish should be off for this clause, or null when nothing here blocks it. */
export function publishBlocker(c: PublishInput): string | null {
  if (c.evidenceRequired && !c.evidenceDocumentId) {
    return c.evidenceRequiredReason?.trim() || 'Evidence of the legal review is required before this clause can be published.'
  }
  return null
}

/** Whether the add/edit form may be saved. A sourced clause must say where it came from. */
export function clauseFormProblem(f: { title: string; clauseKind: string; sourceReference: string }, hasBody: boolean): string | null {
  if (!f.title.trim()) return 'Give the clause a title.'
  if (!hasBody) return 'Add the wording.'
  if (f.clauseKind === 'SOURCED' && !f.sourceReference.trim()) return 'Say where this wording came from.'
  return null
}

export const needsApprovalMessage = (msg: string) => /approval/i.test(msg)
