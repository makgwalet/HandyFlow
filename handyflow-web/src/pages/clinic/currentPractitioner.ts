// When the logged-in user is a practitioner, forms should default to them.
// A practitioner is matched to a user by e-mail (case-insensitive); no match, no default.
export interface PractitionerLike { id: string; email?: string | null; active?: boolean }

export function myPractitionerId(practitioners: PractitionerLike[], userEmail?: string | null): string {
  const mail = userEmail?.trim().toLowerCase()
  if (!mail) return ""
  const mine = practitioners.filter(p => p.email?.trim().toLowerCase() === mail && p.active !== false)
  // Two practitioner records on one e-mail is ambiguous: better to ask than to guess.
  return mine.length === 1 ? mine[0].id : ""
}
