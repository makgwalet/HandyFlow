import { describe, it, expect } from 'vitest'
import { availableSteps, actionsFor, complaintFormError, pathPosition, statusTone, stepFormError, categoryLabel, findingLabel } from './complaints.logic'

describe('complaints logic', () => {
  it('offers only the steps the server allows, in order', () => {
    expect(availableSteps(['WITHDRAW', 'START']).map(s => s.step)).toEqual(['START', 'WITHDRAW'])
    expect(availableSteps([])).toEqual([])
    expect(availableSteps(['CLOSE', 'ACTION']).map(s => s.label)).toEqual(['Record action', 'Close complaint'])
  })
  it('offers Reopen on a closed complaint and asks for a reason', () => {
    expect(availableSteps(['REOPEN']).map(s => s.label)).toEqual(['Reopen'])
    expect(stepFormError('REOPEN', { note: ' ' })).toMatch(/reopening/i)
    expect(stepFormError('REOPEN', { note: 'New evidence' })).toBeNull()
  })
  it('checks the log form', () => {
    expect(complaintFormError({ guardId: '', occurredOn: '2026-10-01', description: 'x' }, '2026-10-07')).toMatch(/guard/i)
    expect(complaintFormError({ guardId: 'g', occurredOn: '', description: 'x' }, '2026-10-07')).toMatch(/date/i)
    expect(complaintFormError({ guardId: 'g', occurredOn: '2026-10-08', description: 'x' }, '2026-10-07')).toMatch(/future/i)
    expect(complaintFormError({ guardId: 'g', occurredOn: '2026-10-07', description: '  ' }, '2026-10-07')).toMatch(/describe/i)
    expect(complaintFormError({ guardId: 'g', occurredOn: '2026-10-07', description: 'Late' }, '2026-10-07')).toBeNull()
  })
  it('checks each step form like the server does', () => {
    expect(stepFormError('FINDING', {})).toMatch(/finding/i)
    expect(stepFormError('FINDING', { finding: 'SUBSTANTIATED', note: ' ' })).toMatch(/found/i)
    expect(stepFormError('FINDING', { finding: 'SUBSTANTIATED', note: 'Gate log' })).toBeNull()
    expect(stepFormError('ACTION', { action: 'NO_ACTION', finding: 'SUBSTANTIATED' })).toMatch(/why no action/i)
    expect(stepFormError('ACTION', { action: 'NO_ACTION', finding: 'UNSUBSTANTIATED' })).toBeNull()
    expect(stepFormError('ACTION', { action: 'VERBAL_WARNING', finding: 'SUBSTANTIATED' })).toBeNull()
    expect(stepFormError('CLOSE', { note: '' })).toMatch(/resolved/i)
    expect(stepFormError('WITHDRAW', { note: 'Retracted' })).toBeNull()
    expect(stepFormError('START', {})).toBeNull()
  })
  it('limits actions after an unsubstantiated finding to no action', () => {
    expect(actionsFor('UNSUBSTANTIATED').map(a => a.value)).toEqual(['NO_ACTION'])
    expect(actionsFor('SUBSTANTIATED').length).toBe(8)
    expect(actionsFor('INCONCLUSIVE').length).toBe(8)
  })
  it('places a complaint on the path and picks tones and labels', () => {
    expect(pathPosition('RECEIVED')).toBe(0)
    expect(pathPosition('ACTION_TAKEN')).toBe(3)
    expect(pathPosition('WITHDRAWN')).toBe(-1)
    expect(statusTone('FINDING_MADE', 'SUBSTANTIATED')).toBe('bad')
    expect(statusTone('CLOSED', null)).toBe('ok')
    expect(statusTone('RECEIVED', null)).toBe('warn')
    expect(categoryLabel('SLEEPING_ON_DUTY')).toBe('Sleeping on duty')
    expect(findingLabel('UNSUBSTANTIATED')).toBe('Not substantiated')
    expect(findingLabel(null)).toBe('-')
  })
})
