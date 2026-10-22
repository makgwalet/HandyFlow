// Types and helpers shared by the letter screens.
export { card, errorText, input, label, plain, primary, td, th, unwrap } from "./attendance.shared"
import { apiClient } from "../../api/client"

export interface FieldView { key: string; label: string; group: string }
export interface TemplateView {
  id: string; code: string; name: string; category: string; subject: string; body: string; requiresAck: boolean; active: boolean
  customised: boolean; standard: boolean; revision: number; fields: string[]
}
export interface Templates { templates: TemplateView[]; fields: FieldView[] }
export interface LetterRow {
  id: string; number: string; subject: string; templateName: string; employeeId: string; employeeName: string; caseId: string | null; caseNumber: string | null
  status: string; issuedOn: string | null; requiresAck: boolean; acknowledgedAt: string | null; createdAt: string
}
export interface LetterEvent { action: string; note: string | null; doneBy: string | null; doneAt: string }
export interface LetterDetail {
  row: LetterRow; body: string; gaps: string[]; events: LetterEvent[]; actions: string[]; fileName: string | null; fileSha256: string | null
  voidReason: string | null; templateRevision: number
}
export interface MineLetter {
  id: string; number: string; subject: string; body: string; templateName: string; issuedOn: string; requiresAck: boolean
  acknowledgedAt: string | null; canAcknowledge: boolean
}

/** Downloads a PDF the server holds. Resolves to an error message, or null on success. */
export async function downloadLetter(path: string, fileName: string): Promise<string | null> {
  try {
    const res = await apiClient.get(path, { responseType: "blob" })
    const url = URL.createObjectURL(new Blob([res.data], { type: "application/pdf" }))
    const a = document.createElement("a")
    a.href = url; a.download = fileName
    document.body.appendChild(a); a.click(); a.remove()
    URL.revokeObjectURL(url)
    return null
  } catch {
    return "The PDF could not be downloaded. Please try again."
  }
}

export interface BulkItem { employeeId: string; employeeName: string; letterId: string | null; number: string | null; gaps: string[]; problem: string | null }
export interface BulkPrepared { items: BulkItem[]; made: number; refused: number }
export interface BulkIssueItem { letterId: string; number: string | null; employeeName: string | null; problem: string | null }
export interface BulkIssued { items: BulkIssueItem[]; issued: number; refused: number }
