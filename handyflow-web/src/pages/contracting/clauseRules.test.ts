import { describe, it, expect } from 'vitest'
import { publishBlocker, clauseFormProblem, kindLabel, needsApprovalMessage } from './clauseRules'

describe('publishBlocker', () => {
  it('blocks when evidence is required and missing, using the server reason', () => {
    expect(publishBlocker({ evidenceRequired: true, evidenceRequiredReason: 'Because custom.' })).toBe('Because custom.')
    expect(publishBlocker({ evidenceRequired: true })).toMatch(/Evidence of the legal review/)
  })
  it('does not block ordinary clauses or ones with evidence', () => {
    expect(publishBlocker({})).toBeNull()
    expect(publishBlocker({ evidenceRequired: false })).toBeNull()
    expect(publishBlocker({ evidenceRequired: true, evidenceDocumentId: 'd1' })).toBeNull()
  })
})

describe('clauseFormProblem', () => {
  const ok = { title: 'T', clauseKind: 'STANDARD', sourceReference: '' }
  it('needs a title and wording', () => {
    expect(clauseFormProblem({ ...ok, title: ' ' }, true)).toMatch(/title/)
    expect(clauseFormProblem(ok, false)).toMatch(/wording/)
    expect(clauseFormProblem(ok, true)).toBeNull()
  })
  it('needs a source for sourced wording only', () => {
    expect(clauseFormProblem({ ...ok, clauseKind: 'SOURCED' }, true)).toMatch(/came from/)
    expect(clauseFormProblem({ ...ok, clauseKind: 'SOURCED', sourceReference: 'Precedent book' }, true)).toBeNull()
    expect(clauseFormProblem({ ...ok, clauseKind: 'CUSTOM' }, true)).toBeNull()
  })
})

describe('labels', () => {
  it('names kinds, defaulting to standard', () => {
    expect(kindLabel('SENSITIVE')).toBe('Sensitive')
    expect(kindLabel(undefined)).toBe('Standard wording')
  })
  it('spots an approval message', () => {
    expect(needsApprovalMessage("needs a second person's approval")).toBe(true)
    expect(needsApprovalMessage('File too big')).toBe(false)
  })
})
