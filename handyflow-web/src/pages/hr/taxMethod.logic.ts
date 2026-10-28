// src/pages/hr/taxMethod.logic.ts
//
// How PAYE on regular pay is worked out. Mirrors PayeAveragingRules on the server.
export type TaxMethod = "PERIODIC" | "AVERAGING"

export const TAX_METHODS: { value: TaxMethod; label: string; hint: string }[] = [
  { value: "PERIODIC", label: "Periodic (each month on its own)", hint: "Each month's regular pay is taxed as if it recurred all year. Simple, but pay that goes up and down is over- or under-taxed until the year is assessed." },
  { value: "AVERAGING", label: "Averaging (year to date)", hint: "The year to date is annualised, the year's tax is spread over the months gone and what was already paid is taken off, so the year ends on the tax it bears. A month never goes below nil, so an overpayment is caught up later. Bonuses, back pay and notice pay are still taxed as annual payments on top." },
]

export const taxMethodHint = (m: string | undefined) => TAX_METHODS.find(t => t.value === m)?.hint ?? ""
export const isTaxMethod = (m: string): m is TaxMethod => m === "PERIODIC" || m === "AVERAGING"
/** The server reads null as "leave as it is". */
export const taxMethodBody = (allowSelfApproval: boolean, method: TaxMethod) => ({ allowSelfApproval, taxMethod: method })
