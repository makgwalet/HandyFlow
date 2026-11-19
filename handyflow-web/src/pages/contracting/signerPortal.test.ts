import { describe, it, expect } from 'vitest'
import { progressSteps, waitingOn, turnMessage, linkProblem } from './signerPortal'

const states = (step: string) => progressSteps(step).map(s => s.state)

describe('progressSteps', () => {
  it('starts on Read', () => expect(states('review')).toEqual(['current', 'todo', 'todo']))
  it('moves to the code and signature screen as one step pair', () => expect(states('sign')).toEqual(['done', 'current', 'current']))
  it('is all done once signed', () => expect(states('done')).toEqual(['done', 'done', 'done']))
})

describe('turn order', () => {
  const others = [
    { fullName: 'Alice', signingOrder: 1, signingStatus: 'PENDING' },
    { fullName: 'Bob', signingOrder: 2, signingStatus: 'SIGNED' },
    { fullName: 'Cara', signingOrder: 3, signingStatus: 'PENDING' },
  ]
  it('names everyone who has not yet signed and comes first', () => {
    expect(waitingOn({ signingOrder: 4 }, others)).toEqual(['Alice', 'Cara'])
    expect(waitingOn({ signingOrder: 2 }, others)).toEqual(['Alice'])
  })
  it('is empty when it is the signer\'s turn, including a shared position', () => {
    expect(waitingOn({ signingOrder: 1 }, others)).toEqual([])
    expect(waitingOn({ signingOrder: 1 }, [{ fullName: 'Dan', signingOrder: 1, signingStatus: 'PENDING' }])).toEqual([])
  })
  it('words the message for one or several people, and says nothing when nobody is ahead', () => {
    expect(turnMessage([])).toBeNull()
    expect(turnMessage(['Alice'])).toContain('Alice must sign before you')
    expect(turnMessage(['Alice', 'Cara', 'Dan'])).toContain('Alice, Cara and Dan must sign before you')
  })
})

describe('linkProblem', () => {
  it('explains a withdrawn request without asking for a new link', () => {
    const p = linkProblem('The sender has withdrawn this signing request. Nothing more is needed from you.')
    expect(p.title).toBe('This signing request was withdrawn')
    expect(p.tone).toBe('info')
    expect(p.advice).toContain('nothing more is needed')
  })
  it('tells a used link from a replaced link from a closed contract', () => {
    expect(linkProblem('This signing link has already been used.').title).toContain('already been used')
    expect(linkProblem('This signing link has been revoked. Ask the sender to resend.').title).toContain('replaced')
    expect(linkProblem('This contract is no longer open for signing.').title).toContain('no longer open')
  })
  it('falls back to a general message', () => {
    expect(linkProblem('').title).toBe('This link is not valid')
    expect(linkProblem('Signing link not found or already used').tone).not.toBe('info')
  })
})
