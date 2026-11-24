import { describe, it, expect } from 'vitest'
import { actionsFor, actionProblem, showsNote, stepProblem, dueText, isProblem, isEditable, eventLabel, statusLabel } from './caseRules'

const base = { status: 'IN_PROGRESS', ownerId: 'u1', openSteps: 0, resolvedBy: null }
const act = (status: string, a: string) => actionsFor(status).find(x => x.action === a)!

describe('caseRules', () => {
  it('offers only the moves the server allows', () => {
    expect(actionsFor('OPEN').map(a => a.action)).toEqual(['START', 'CANCEL'])
    expect(actionsFor('RESOLVED').map(a => a.action)).toEqual(['CLOSE', 'REOPEN'])
    expect(actionsFor('CLOSED')).toEqual([])
    expect(actionsFor('CANCELLED')).toEqual([])
  })

  it('will not start without an owner', () => {
    expect(actionProblem(act('OPEN', 'START'), { ...base, status: 'OPEN', ownerId: null }, '', 'u1')).toMatch(/owner/)
    expect(actionProblem(act('OPEN', 'START'), { ...base, status: 'OPEN' }, '', 'u1')).toBeNull()
  })

  it('will not resolve with open steps or a thin note', () => {
    const r = act('IN_PROGRESS', 'RESOLVE')
    expect(actionProblem(r, { ...base, openSteps: 2 }, 'Appointed an information officer', 'u1')).toMatch(/2 steps are still open/)
    expect(actionProblem(r, { ...base, openSteps: 1 }, 'x', 'u1')).toMatch(/1 step is still open/)
    expect(actionProblem(r, base, 'short', 'u1')).toMatch(/how it was resolved/)
    expect(actionProblem(r, base, 'Appointed an information officer', 'u1')).toBeNull()
  })

  it('asks for a reason when you close your own resolution, not when someone else does', () => {
    const c = { ...base, status: 'RESOLVED', resolvedBy: 'u1' }
    const close = act('RESOLVED', 'CLOSE')
    expect(actionProblem(close, c, '', 'u1')).toMatch(/someone else/)
    expect(actionProblem(close, c, 'Sole practitioner, nobody else', 'u1')).toBeNull()
    expect(actionProblem(close, c, '', 'u2')).toBeNull()
    expect(showsNote(close, c, 'u1')).toBe(true)
    expect(showsNote(close, c, 'u2')).toBe(false)
  })

  it('needs a reason to cancel or reopen', () => {
    expect(actionProblem(act('OPEN', 'CANCEL'), { ...base, status: 'OPEN' }, 'no', 'u1')).toMatch(/Say why/)
    expect(actionProblem(act('RESOLVED', 'REOPEN'), { ...base, status: 'RESOLVED' }, 'The regulator rejected it', 'u1')).toBeNull()
  })

  it('checks a step', () => {
    expect(stepProblem(' a ')).toMatch(/3 characters/)
    expect(stepProblem('Collect the ID copy')).toBeNull()
    expect(stepProblem('x'.repeat(501))).toMatch(/500/)
  })

  it('words the due date, but only for work still being done', () => {
    expect(dueText(null, '2026-11-24', 'OPEN')).toBe('No due date')
    expect(dueText('2026-11-22', '2026-11-24', 'OPEN')).toBe('2 days overdue')
    expect(dueText('2026-11-23', '2026-11-24', 'OPEN')).toBe('1 day overdue')
    expect(dueText('2026-11-24', '2026-11-24', 'IN_PROGRESS')).toBe('Due today')
    expect(dueText('2026-11-25', '2026-11-24', 'IN_PROGRESS')).toBe('Due in 1 day')
    expect(dueText('2026-11-01', '2026-11-24', 'CLOSED')).toBe('Due 2026-11-01')
  })

  it('knows which assessed items are problems and which cases can change', () => {
    expect(isProblem('NON_COMPLIANT')).toBe(true)
    expect(isProblem('COMPLIANT')).toBe(false)
    expect(isProblem('NOT_ASSESSED')).toBe(false)
    expect(isEditable('RESOLVED')).toBe(false)
    expect(isEditable('AWAITING_CLIENT')).toBe(true)
    expect(statusLabel('AWAITING_CLIENT')).toBe('Waiting on the client')
    expect(eventLabel('STEP_DONE')).toBe('Step done')
    expect(eventLabel('NEW_ONE')).toBe('NEW_ONE')
  })
})
