// Finance patch F10: what a supplier bill still owes, and the checks the server also makes, so the screen can say why before sending.

export const PAYABLE = ["APPROVED", "OVERDUE", "PARTIALLY_PAID"]

const cents = (n: number) => Math.round(n * 100)

export interface BillAmounts { totalAmount: number; paidAmount?: number | null; creditedAmount?: number | null; balance?: number | null }

/** What the bill still owes. Uses the server's balance when sent, otherwise total less paid and credited. */
export function balanceOf(b: BillAmounts): number {
  if (b.balance != null) return Number(b.balance)
  return Math.max(0, (cents(Number(b.totalAmount)) - cents(Number(b.paidAmount ?? 0)) - cents(Number(b.creditedAmount ?? 0))) / 100)
}

/** Null when {@code text} may be paid against {@code balance}, otherwise why not. Empty text pays the whole balance. */
export function paymentError(text: string, balance: number, hasBank: boolean): string | null {
  const t = text.trim()
  if (t !== "") {
    const n = Number(t.replace(",", "."))
    if (!isFinite(n) || n <= 0) return "Enter the amount to pay"
    if (cents(n) > cents(balance)) return `That is more than the ${money(balance)} this bill still owes`
  } else if (cents(balance) <= 0) {
    return "This bill has nothing left to pay"
  }
  if (!hasBank) return "Choose the bank account the payment is made from"
  return null
}

export function amountToPay(text: string, balance: number): number {
  const t = text.trim()
  return t === "" ? balance : Number(t.replace(",", "."))
}

export interface OpenBill { id: string; balance: number }
export interface Line { billId: string; amount: number }

/** Applies {@code amount} to the open bills in the order given, until it runs out. */
export function allocateOldestFirst(amount: number, open: OpenBill[]): Line[] {
  const out: Line[] = []
  let left = cents(amount)
  for (const o of open) {
    if (left <= 0) break
    const take = Math.min(left, cents(o.balance))
    if (take > 0) { out.push({ billId: o.id, amount: take / 100 }); left -= take }
  }
  return out
}

/** Null when these lines may be applied from {@code available} credit, otherwise why not. */
export function allocationError(lines: Line[], available: number, open: OpenBill[]): string | null {
  if (lines.length === 0) return "Choose at least one bill to apply the credit to"
  let sum = 0
  for (const l of lines) {
    if (!(l.amount > 0)) return "Each amount must be more than zero"
    const o = open.find(x => x.id === l.billId)
    if (!o) return "One of the bills is not open for this supplier"
    if (cents(l.amount) > cents(o.balance)) return `${money(l.amount)} is more than the ${money(o.balance)} that bill still owes`
    sum += cents(l.amount)
  }
  if (sum > cents(available)) return `${money(sum / 100)} is more than the ${money(available)} of credit left`
  return null
}

/** Null when the credit note's amounts are usable, otherwise why not. */
export function creditAmountError(amount: string, vat: string): string | null {
  const a = Number(amount.replace(",", "."))
  if (amount.trim() === "" || !isFinite(a) || a <= 0) return "Enter the amount of the credit note, before VAT"
  const v = vat.trim() === "" ? 0 : Number(vat.replace(",", "."))
  if (!isFinite(v) || v < 0) return "VAT cannot be less than zero"
  if (cents(v) > cents(a)) return "VAT cannot be more than the amount it is charged on"
  return null
}

export function money(n: number): string {
  return `R ${Number(n).toLocaleString("en-ZA", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
}
