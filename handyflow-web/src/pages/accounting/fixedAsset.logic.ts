// Finance F25: fixed-asset register and depreciation.

export interface Account { id: string; accountCode: string; accountName: string; accountType: string; accountSubtype?: string | null }

export interface Asset {
  id: string; assetNumber: string; name: string; description: string | null; serialNumber: string | null; location: string | null
  costAccountId: string; accumulatedAccountId: string; expenseAccountId: string; costCentreId: string | null
  acquiredOn: string; cost: number; residualValue: number; method: string; lifeMonths: number | null; ratePercent: number | null
  status: string; disposedOn: string | null; disposalProceeds: number | null; accumulated: number; bookValue: number
}

export interface FormState {
  name: string; description: string; serialNumber: string; location: string; costCentreId: string
  costAccountId: string; accumulatedAccountId: string; expenseAccountId: string
  acquiredOn: string; cost: string; residualValue: string; method: string; lifeMonths: string; ratePercent: string
}

export const emptyForm = (): FormState => ({
  name: "", description: "", serialNumber: "", location: "", costCentreId: "", costAccountId: "", accumulatedAccountId: "", expenseAccountId: "",
  acquiredOn: "", cost: "", residualValue: "0", method: "STRAIGHT_LINE", lifeMonths: "60", ratePercent: "",
})

export const formOf = (a: Asset): FormState => ({
  name: a.name, description: a.description ?? "", serialNumber: a.serialNumber ?? "", location: a.location ?? "", costCentreId: a.costCentreId ?? "",
  costAccountId: a.costAccountId, accumulatedAccountId: a.accumulatedAccountId, expenseAccountId: a.expenseAccountId,
  acquiredOn: a.acquiredOn, cost: String(a.cost), residualValue: String(a.residualValue), method: a.method,
  lifeMonths: a.lifeMonths != null ? String(a.lifeMonths) : "", ratePercent: a.ratePercent != null ? String(a.ratePercent) : "",
})

/** First problem found in the form, or null. The server checks again. */
export function formProblem(f: FormState): string | null {
  if (!f.name.trim()) return "Give the asset a name."
  if (!f.acquiredOn) return "Enter the date it was acquired."
  const cost = Number(f.cost), residual = Number(f.residualValue || "0")
  if (!(cost > 0)) return "Cost must be more than zero."
  if (!(residual >= 0)) return "Residual value cannot be negative."
  if (residual >= cost) return "Residual value must be less than the cost."
  if (!f.costAccountId || !f.accumulatedAccountId || !f.expenseAccountId) return "Choose the three accounts."
  if (f.method === "STRAIGHT_LINE") {
    const life = Number(f.lifeMonths)
    if (!Number.isInteger(life) || life < 1 || life > 600) return "Useful life must be 1 to 600 months."
  } else {
    const rate = Number(f.ratePercent)
    if (!(rate > 0 && rate <= 100)) return "The yearly rate must be more than 0 and at most 100 percent."
  }
  return null
}

export function toRequest(f: FormState) {
  const sl = f.method === "STRAIGHT_LINE"
  return {
    name: f.name.trim(), description: f.description.trim() || null, serialNumber: f.serialNumber.trim() || null, location: f.location.trim() || null,
    costCentreId: f.costCentreId || null, costAccountId: f.costAccountId, accumulatedAccountId: f.accumulatedAccountId, expenseAccountId: f.expenseAccountId,
    acquiredOn: f.acquiredOn, cost: Number(f.cost), residualValue: Number(f.residualValue || "0"), method: f.method,
    lifeMonths: sl ? Number(f.lifeMonths) : null, ratePercent: sl ? null : Number(f.ratePercent),
  }
}

/** Picks the usual account for a role from the chart, by subtype, so a new asset needs fewer clicks. */
export function suggestAccount(accounts: Account[], role: "cost" | "accumulated" | "expense"): string {
  const pick = (f: (a: Account) => boolean) => accounts.find(f)?.id ?? ""
  if (role === "cost") return pick(a => a.accountSubtype === "FIXED_ASSET")
  if (role === "accumulated") return pick(a => a.accountSubtype === "DEPRECIATION" && a.accountType === "ASSET")
  return pick(a => a.accountSubtype === "DEPRECIATION" && a.accountType === "EXPENSE")
}

export const METHOD_LABEL: Record<string, string> = { STRAIGHT_LINE: "Straight line", REDUCING_BALANCE: "Reducing balance" }

export function lifeText(a: Pick<Asset, "method" | "lifeMonths" | "ratePercent">): string {
  if (a.method === "STRAIGHT_LINE" && a.lifeMonths) {
    const y = Math.floor(a.lifeMonths / 12), m = a.lifeMonths % 12
    return [y ? `${y} yr` : "", m ? `${m} mo` : ""].filter(Boolean).join(" ")
  }
  return a.ratePercent != null ? `${a.ratePercent}% a year` : "—"
}

export const money = (n: number | null | undefined) =>
  n == null ? "—" : `R ${n.toLocaleString("en-ZA", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`

export const monthName = (iso: string | null | undefined) =>
  iso ? new Date(iso + "T00:00:00").toLocaleDateString("en-ZA", { month: "long", year: "numeric" }) : "—"
