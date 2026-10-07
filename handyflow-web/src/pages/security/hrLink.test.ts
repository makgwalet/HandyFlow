import { describe, it, expect } from 'vitest'
import { hrOutcomeLabel, linkLine, referHint, searchReady } from './hrLink.logic'

const link = (o: any = {}) => ({ linked: true, employeeMissing: false, employeeId: 'e', employeeNumber: 'EMP-0007', fullName: 'Gerhard Botha', jobTitle: 'Officer', department: null, status: 'ACTIVE', ...o })

describe('HR link', () => {
  it('needs two characters to search', () => {
    expect(searchReady(' a ')).toBe(false)
    expect(searchReady('bo')).toBe(true)
  })
  it('describes the link in one line', () => {
    expect(linkLine(link({ linked: false }))).toMatch(/Not linked/)
    expect(linkLine(link())).toBe('Gerhard Botha · EMP-0007 · Officer')
    expect(linkLine(link({ employeeMissing: true }))).toMatch(/no longer be found/)
  })
  it('words HR outcomes and the missing one', () => {
    expect(hrOutcomeLabel('FINAL_WRITTEN_WARNING')).toBe('Final written warning')
    expect(hrOutcomeLabel(null)).toBe('No outcome recorded yet')
  })
  it('explains why the button is missing only for a substantiated, unreferred complaint', () => {
    expect(referHint({ finding: 'SUBSTANTIATED', status: 'FINDING_MADE' }, false, false)).toMatch(/link the guard/i)
    expect(referHint({ finding: 'SUBSTANTIATED', status: 'FINDING_MADE' }, true, false)).toBeNull()
    expect(referHint({ finding: 'SUBSTANTIATED', status: 'CLOSED' }, false, true)).toBeNull()
    expect(referHint({ finding: 'INCONCLUSIVE', status: 'FINDING_MADE' }, false, false)).toBeNull()
  })
})
