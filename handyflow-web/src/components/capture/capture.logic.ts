export interface Capture {
  textFound: boolean
  supplierName: string | null
  documentNumber: string | null
  documentDate: string | null
  dueDate: string | null
  total: number | null
  vatAmount: number | null
  supplierVatNumber: string | null
  notes: string[]
}

const money = (n: number) => (Math.round(n * 100) / 100).toFixed(2)

/** Only the fields the file gave us, so nothing the person already typed is blanked. */
export function billFields(c: Capture): Record<string, string> {
  const out: Record<string, string> = {}
  if (c.supplierName) out.supplierName = c.supplierName
  if (c.documentNumber) out.billNumber = c.documentNumber
  if (c.documentDate) out.billDate = c.documentDate
  if (c.dueDate) out.dueDate = c.dueDate
  if (c.total != null) {
    // The bill form asks for the amount excluding VAT, the file shows the total.
    const vat = c.vatAmount != null && c.vatAmount < c.total ? c.vatAmount : null
    out.amount = money(vat != null ? c.total - vat : c.total)
    if (vat != null) out.vatAmount = money(vat)
  }
  return out
}

export function claimFields(c: Capture): Record<string, string> {
  const out: Record<string, string> = {}
  if (c.documentDate) out.claimDate = c.documentDate
  if (c.total != null) out.amount = money(c.total)
  if (c.supplierName) out.description = c.supplierName
  return out
}

export const isReadable = (name: string, type: string) =>
  type === "application/pdf" || /\.(pdf|jpe?g|png)$/i.test(name) || type === "image/jpeg" || type === "image/png"
