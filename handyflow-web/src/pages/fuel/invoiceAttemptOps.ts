// G02: helpers for the invoice attempts that need a person.
export interface InvoiceAttempt {
  id: string; customerName: string | null; status: "STARTED" | "CREATED" | "FAILED" | "LINK_FAILED" | "RESOLVED"; needsAttention: boolean
  receiptNumbers: string[]; deliveryCount: number; invoiceNumber: string | null; error: string | null; startedAt: string
}

export const attemptHeadline = (a: Pick<InvoiceAttempt, "status" | "invoiceNumber" | "deliveryCount">): string =>
  a.status === "LINK_FAILED"
    ? `Invoice ${a.invoiceNumber ?? "(number unknown)"} was created for ${a.deliveryCount} deliver${a.deliveryCount === 1 ? "y" : "ies"} but they were not linked to it.`
    : `An invoice attempt for ${a.deliveryCount} deliver${a.deliveryCount === 1 ? "y" : "ies"} never finished.`

export const attemptAdvice = (a: Pick<InvoiceAttempt, "status">): string =>
  a.status === "LINK_FAILED"
    ? "Do not invoice these again. Link them to the invoice that was created."
    : "Check Invoicing for an invoice for this customer first. Only if there is none, release the deliveries back to the queue."

/** The release note must say what was checked. Mirrors the server (5 to 300 characters). */
export const validateReleaseNote = (note: string): string | null => {
  const n = note.trim().length
  return n < 5 || n > 300 ? "Say what you checked, in 5 to 300 characters." : null
}
