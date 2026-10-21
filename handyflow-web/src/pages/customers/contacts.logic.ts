// Rules for a customer's contacts: checking the form, ordering and display. Pure functions.
export interface Contact {
  id: string; customerId: string; firstName: string; lastName: string; fullName: string
  email: string | null; phone: string | null; jobTitle: string | null; primary: boolean; notes: string | null
}
export interface ContactForm { firstName: string; lastName: string; email: string; phone: string; jobTitle: string; notes: string; primary: boolean }
export const EMPTY_CONTACT: ContactForm = { firstName: "", lastName: "", email: "", phone: "", jobTitle: "", notes: "", primary: false }
export const toForm = (c: Contact): ContactForm => ({ firstName: c.firstName, lastName: c.lastName, email: c.email ?? "", phone: c.phone ?? "", jobTitle: c.jobTitle ?? "", notes: c.notes ?? "", primary: c.primary })

const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
const PHONE = /^\+?[0-9 ()-]{7,20}$/
/** Why the form cannot be saved yet, or null. The server checks the same rules. */
export function contactProblem(f: ContactForm, others: Contact[], editingId: string | null): string | null {
  if (!f.firstName.trim()) return "Enter a first name."
  if (!f.lastName.trim()) return "Enter a last name."
  if (f.firstName.trim().length > 100 || f.lastName.trim().length > 100) return "Names can be at most 100 characters."
  const email = f.email.trim().toLowerCase()
  if (email) {
    if (!EMAIL.test(email)) return "That email address doesn't look right."
    if (others.some(c => c.id !== editingId && c.email?.toLowerCase() === email)) return "Another contact here already has that email."
  }
  if (f.phone.trim() && !PHONE.test(f.phone.trim())) return "That phone number doesn't look right."
  if (f.jobTitle.trim().length > 100) return "Job title can be at most 100 characters."
  if (f.notes.length > 2000) return "Notes can be at most 2000 characters."
  return null
}
/** Blank boxes are sent as null so the server clears them. */
export const contactBody = (f: ContactForm) => ({
  firstName: f.firstName.trim(), lastName: f.lastName.trim(), email: f.email.trim() || null, phone: f.phone.trim() || null,
  jobTitle: f.jobTitle.trim() || null, notes: f.notes.trim() || null, primary: f.primary,
})
/** Main contact first, then by last name. */
export const sortContacts = (cs: Contact[]) => [...cs].sort((a, b) => Number(b.primary) - Number(a.primary) || a.lastName.localeCompare(b.lastName) || a.firstName.localeCompare(b.firstName))
export const initials = (c: Pick<Contact, "firstName" | "lastName">) => `${c.firstName.trim().charAt(0)}${c.lastName.trim().charAt(0)}`.toUpperCase()
/** The people to try for this customer: the main contact's details are what other screens should offer first. */
export const mainContact = (cs: Contact[]) => cs.find(c => c.primary) ?? null
