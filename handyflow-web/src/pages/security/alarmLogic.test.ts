import { describe, it, expect, beforeEach } from 'vitest'
import { needsAttention, attentionList, bannerText, errorMessage, readSoundPref, writeSoundPref, SOUND_KEY } from './alarmLogic'

const ev = (o: Record<string, unknown> = {}) => ({ id: 'a', source: 'ALARM_PANEL', severity: 'LOW', status: 'NEW', createdAt: '2026-11-20T10:00:00Z', ...o })

describe('needsAttention', () => {
  it('flags NEW critical', () => expect(needsAttention(ev({ severity: 'CRITICAL' }))).toBe(true))
  it('flags NEW duress even if severity is low', () => expect(needsAttention(ev({ source: 'DURESS' }))).toBe(true))
  it('ignores triaged critical', () => expect(needsAttention(ev({ severity: 'CRITICAL', status: 'TRIAGED' }))).toBe(false))
  it('ignores NEW medium', () => expect(needsAttention(ev({ severity: 'MEDIUM' }))).toBe(false))
})

describe('attentionList', () => {
  it('puts duress first then oldest first', () => {
    const l = attentionList([
      ev({ id: 'c2', severity: 'CRITICAL', createdAt: '2026-11-20T10:05:00Z' }),
      ev({ id: 'c1', severity: 'CRITICAL', createdAt: '2026-11-20T10:01:00Z' }),
      ev({ id: 'd', source: 'DURESS', severity: 'CRITICAL', createdAt: '2026-11-20T10:09:00Z' }),
      ev({ id: 'x' }),
    ])
    expect(l.map(e => e.id)).toEqual(['d', 'c1', 'c2'])
  })
})

describe('bannerText', () => {
  it('empty when nothing', () => expect(bannerText([])).toBe(''))
  it('says PANIC for duress and counts the rest', () =>
    expect(bannerText([ev({ source: 'DURESS' }), ev({ severity: 'CRITICAL' })])).toBe('PANIC pressed — not yet acknowledged (+1 more)'))
  it('says CRITICAL otherwise', () =>
    expect(bannerText([ev({ severity: 'CRITICAL' })])).toBe('CRITICAL alarm — not yet acknowledged'))
})

describe('errorMessage', () => {
  it('uses server message', () => expect(errorMessage({ response: { data: { message: 'Nope' } } }, 'x')).toBe('Nope'))
  it('falls back', () => { expect(errorMessage(new Error('x'), 'Failed')).toBe('Failed'); expect(errorMessage(null, 'F')).toBe('F') })
})

describe('sound preference', () => {
  beforeEach(() => localStorage.clear())
  it('defaults off and round-trips', () => {
    expect(readSoundPref()).toBe(false)
    writeSoundPref(true); expect(localStorage.getItem(SOUND_KEY)).toBe('1'); expect(readSoundPref()).toBe(true)
    writeSoundPref(false); expect(readSoundPref()).toBe(false)
  })
})
