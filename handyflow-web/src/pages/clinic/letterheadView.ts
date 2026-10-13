// Practice and branch letterheads (patch 0194; server rules in LetterheadRules, CLINIC-DEC-026). Pure helpers for the admin screen and the letter dialog.
export interface Letterhead {
  id: string; name: string; branchName: string | null; practiceName: string; lines: string[]
  isDefault: boolean; active: boolean; hasLogo: boolean
}

export const NAME_MAX = 120
export const LINES_MAX = 6
export const LINE_MAX = 120
export const LOGO_MAX_BYTES = 300 * 1024

/** One address line per row of text; blanks dropped. */
export function linesFromText(text: string): string[] {
  return text.split(/\r?\n/).map(l => l.trim()).filter(l => l !== "")
}

export const linesToText = (lines: readonly string[]): string => lines.join("\n")

export interface LetterheadForm { name: string; branchName: string; practiceName: string; linesText: string }

/** Why the letterhead cannot be saved yet. Mirrors the server, which has the last word. */
export function problems(f: LetterheadForm): string[] {
  const out: string[] = []
  if (!f.name.trim()) out.push("Give the letterhead a name")
  else if (f.name.trim().length > NAME_MAX) out.push(`The name is longer than ${NAME_MAX} characters`)
  if (!f.practiceName.trim()) out.push("Give the practice name that is printed at the top")
  else if (f.practiceName.trim().length > NAME_MAX) out.push(`The practice name is longer than ${NAME_MAX} characters`)
  if (f.branchName.trim().length > NAME_MAX) out.push(`The branch name is longer than ${NAME_MAX} characters`)
  const lines = linesFromText(f.linesText)
  if (lines.length > LINES_MAX) out.push(`Use at most ${LINES_MAX} address lines`)
  if (lines.some(l => l.length > LINE_MAX)) out.push(`An address line is longer than ${LINE_MAX} characters`)
  return out
}

/** Whether a chosen picture can be uploaded, by what the browser says about it; the server checks the real bytes. */
export function logoProblem(file: { type: string; size: number }): string | null {
  if (file.type !== "image/png" && file.type !== "image/jpeg") return "The logo must be a PNG or JPEG picture"
  if (file.size > LOGO_MAX_BYTES) return `The logo is larger than ${LOGO_MAX_BYTES / 1024} KB`
  if (file.size === 0) return "That file is empty"
  return null
}

export const kindLabel = (l: Pick<Letterhead, "branchName" | "isDefault">): string =>
  l.branchName ? `Branch: ${l.branchName}` : l.isDefault ? "Practice-wide, used when none is chosen" : "Practice-wide"

/** Text for the letter dialog's choice. */
export const choiceLabel = (l: Pick<Letterhead, "name" | "branchName" | "isDefault">): string =>
  `${l.name}${l.isDefault ? " (default)" : l.branchName ? ` (${l.branchName})` : ""}`

export const formFrom = (l: Letterhead | null): LetterheadForm =>
  l ? { name: l.name, branchName: l.branchName ?? "", practiceName: l.practiceName, linesText: linesToText(l.lines) }
    : { name: "", branchName: "", practiceName: "", linesText: "" }
