// Merging two records of the same patient (patch 0188): the shapes the server returns and the small decisions the window makes.

export interface MergeCard { id: string; patientNumber?: string | null; fullName?: string | null; dateOfBirth?: string | null; idNumber?: string | null }
export interface MergeCount { table: string; label: string; rows: number }
export interface MergeConflict { field: string; text: string; blocking: boolean }
export interface MergePreview {
  keep: MergeCard; mergeAway: MergeCard; moves: MergeCount[]; fills: string[]; conflicts: MergeConflict[]
  blockers: string[]; staysBehind: string[]; needsAcknowledgement: boolean
}

const FIELD: Record<string, string> = {
  id_number: "ID number", date_of_birth: "date of birth", gender: "gender", sex_at_birth: "sex at birth", phone: "phone", email: "email",
  blood_type: "blood type", emergency_contact_name: "emergency contact name", emergency_contact_phone: "emergency contact phone",
}
export const fieldLabel = (c: string) => FIELD[c] ?? c.replace(/_/g, " ")

/** "P000001 · Thandi Nkosi · DOB 1980-01-01" */
export function cardLine(c: MergeCard): string {
  return [c.patientNumber, c.fullName, c.dateOfBirth && `DOB ${c.dateOfBirth}`].filter(Boolean).join(" · ")
}

export const totalRows = (p: MergePreview) => p.moves.reduce((n, m) => n + m.rows, 0)

/** One sentence on what will happen, for the confirm area. */
export function consequence(p: MergePreview): string {
  const n = totalRows(p)
  return `${n} record${n === 1 ? "" : "s"} will move to ${p.keep.fullName ?? "the record to keep"}. ${p.mergeAway.fullName ?? "The other record"} will be archived, not deleted, and the merge is kept on file.`
}

/** The merge button is on only when nothing blocks it, a reason is given, and any identity conflict has been confirmed. */
export function canConfirm(p: MergePreview | undefined, reason: string, acknowledged: boolean): boolean {
  if (!p || p.blockers.length > 0) return false
  if (reason.trim().length < 5) return false
  return !p.needsAcknowledgement || acknowledged
}
