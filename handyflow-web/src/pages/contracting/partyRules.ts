// Rules for adding a signer to a contract that already exists.
export interface ExistingParty { fullName: string; signingOrder: number; signingStatus?: string }

const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

/** The number after everyone already added, so a forgotten field never leaves two people sharing order 1 by accident. */
export function nextSigningOrder(parties: ExistingParty[]): number {
  return parties.reduce((m, p) => Math.max(m, p.signingOrder || 0), 0) + 1
}

/** What stops the form being submitted, or null. A blank email is allowed (the person can sign by phone only). */
export function partyBlocker(form: { fullName: string; email: string; signingOrder: string }): string | null {
  if (!form.fullName.trim()) return 'Enter the signer’s name.'
  const email = form.email.trim()
  if (email && !EMAIL.test(email)) return `"${email}" is not a valid email address.`
  const order = Number(form.signingOrder)
  if (!Number.isInteger(order) || order < 1) return 'Signing order must be a whole number from 1.'
  return null
}

/** Plain-words reading of the chosen order: who they sign after, or who they sign together with. */
export function orderNote(order: number, parties: ExistingParty[]): string {
  const active = parties.filter(p => p.signingStatus !== 'DECLINED')
  const together = active.filter(p => p.signingOrder === order).map(p => p.fullName)
  if (together.length) return `Signs together with ${together.join(', ')} — they are sent their links at the same time.`
  const before = active.filter(p => p.signingOrder < order).length
  return before === 0
    ? 'Signs first — their link goes out when the contract is sent.'
    : `Signs after ${before === 1 ? '1 earlier party' : `${before} earlier parties`} — their link is sent once those have signed.`
}
