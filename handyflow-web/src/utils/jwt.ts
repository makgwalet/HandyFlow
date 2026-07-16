// src/utils/jwt.ts
//
// Minimal, dependency-free JWT payload decoding — reading the expiry
// claim for UX purposes only (the session-expiry warning's countdown),
// never for anything security-relevant. The signature is never verified
// client-side, which is correct and expected: the server is the only
// party that needs to trust this token's authenticity. This just reads a
// number out of it to know when to show a warning.

export function decodeJwtExpiry(token: string): number | null {
  try {
    const payloadB64 = token.split('.')[1]
    if (!payloadB64) return null

    // JWTs use base64url, not standard base64 — swap the two characters
    // that differ, then pad to a multiple of 4 as atob() requires.
    const base64 = payloadB64.replace(/-/g, '+').replace(/_/g, '/')
    const padded = base64 + '='.repeat((4 - (base64.length % 4)) % 4)
    const payload = JSON.parse(atob(padded))

    // exp is seconds since epoch (standard JWT claim) — JS Date math
    // needs milliseconds.
    return typeof payload.exp === 'number' ? payload.exp * 1000 : null
  } catch {
    return null
  }
}