// Pure rules for the page an outside signer sees: where they are, whether it is their turn, and what a dead link means.

export type PortalStep = 'review' | 'otp' | 'sign' | 'done'
export interface ProgressItem { id: 'review' | 'verify' | 'sign'; label: string; state: 'done' | 'current' | 'todo' }

/** Three plain steps: read it, confirm with a code, sign. */
export function progressSteps(step: string): ProgressItem[] {
  const at = step === 'review' ? 0 : step === 'sign' || step === 'otp' ? 1 : step === 'done' ? 3 : 0
  const items: [ProgressItem['id'], string][] = [['review', 'Read'], ['verify', 'Confirm with a code'], ['sign', 'Sign']]
  return items.map(([id, label], i) => ({
    id, label,
    // the code is entered on the same screen as the signature, so "Sign" becomes current together with "Confirm"
    state: i < at ? 'done' : i === at ? 'current' : at === 1 && i === 2 ? 'current' : 'todo',
  }))
}

interface PartyLike { fullName: string; signingOrder: number; signingStatus: string }

/** Whoever must sign before this person. Empty when it is their turn. */
export function waitingOn(mine: { signingOrder: number }, others: PartyLike[]): string[] {
  return others.filter(p => p.signingOrder < mine.signingOrder && p.signingStatus !== 'SIGNED').map(p => p.fullName)
}

export function turnMessage(names: string[]): string | null {
  if (names.length === 0) return null
  const who = names.length === 1 ? names[0] : `${names.slice(0, -1).join(', ')} and ${names[names.length - 1]}`
  return `${who} must sign before you. You will get a message when it is your turn, and you can come back to this link then.`
}

export interface LinkProblem { title: string; advice: string; tone: 'info' | 'warn' | 'bad' }

/** What to tell someone whose link does not work, from the server's message. */
export function linkProblem(message: string): LinkProblem {
  const m = message.toLowerCase()
  if (m.includes('withdrawn'))
    return { title: 'This signing request was withdrawn', advice: 'The sender cancelled it, so nothing more is needed from you. If a revised version is sent you will get a new link.', tone: 'info' }
  if (m.includes('already been used') || m.includes('already used'))
    return { title: 'This link has already been used', advice: 'If you have signed, you are done. If not, ask the sender for a new link.', tone: 'warn' }
  if (m.includes('revoked'))
    return { title: 'This link was replaced', advice: 'The sender issued you a newer link. Look for the latest message from them, or ask them to resend it.', tone: 'warn' }
  if (m.includes('no longer open'))
    return { title: 'This contract is no longer open for signing', advice: 'Contact the person who sent it if you were expecting to sign.', tone: 'warn' }
  return { title: 'This link is not valid', advice: 'It may have expired or been mistyped. Contact the person who sent the contract to ask for a new link.', tone: 'bad' }
}
