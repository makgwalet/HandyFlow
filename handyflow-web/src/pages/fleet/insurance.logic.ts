// Insurance profile form: a full replace of the vehicle's policy details. When "insured" is on, insurer and policy expiry are required.
export const POLICY_TYPES = ["COMPREHENSIVE", "THIRD_PARTY_ONLY", "THIRD_PARTY_FIRE_THEFT", "OTHER"]
export const POLICY_TYPE_LABEL: Record<string, string> = { COMPREHENSIVE: "Comprehensive", THIRD_PARTY_ONLY: "Third party only", THIRD_PARTY_FIRE_THEFT: "Third party, fire and theft", OTHER: "Other" }

export interface InsuranceData {
  insured: boolean; insurer: string | null; policyNumber: string | null; policyType: string | null; policyStart?: string | null; policyExpiry: string | null; daysToExpiry: number | null
  premium: number | null; excess: number | null; broker?: string | null; brokerContact?: string | null; coverAmount: number | null; vehicleValue?: number | null
  trackingRequired: boolean; trackingProvider: string | null; trackingDeviceNumber?: string | null; windscreenCover?: boolean; thirdPartyCoverAmount?: number | null; specialConditions?: string | null
}
export interface InsuranceForm {
  insured: boolean; insurer: string; policyNumber: string; policyType: string; policyStart: string; policyExpiry: string; premium: string; excess: string
  broker: string; brokerContact: string; coverAmount: string; vehicleValue: string; trackingRequired: boolean; trackingProvider: string; trackingDeviceNumber: string
  windscreenCover: boolean; thirdPartyCoverAmount: string; specialConditions: string
}
const t = (v: unknown) => (v == null ? "" : String(v))
export const toInsuranceForm = (d: InsuranceData | null | undefined): InsuranceForm => ({
  insured: d?.insured ?? true, insurer: t(d?.insurer), policyNumber: t(d?.policyNumber), policyType: d?.policyType ?? "COMPREHENSIVE", policyStart: t(d?.policyStart), policyExpiry: t(d?.policyExpiry),
  premium: t(d?.premium), excess: t(d?.excess), broker: t(d?.broker), brokerContact: t(d?.brokerContact), coverAmount: t(d?.coverAmount), vehicleValue: t(d?.vehicleValue),
  trackingRequired: d?.trackingRequired ?? false, trackingProvider: t(d?.trackingProvider), trackingDeviceNumber: t(d?.trackingDeviceNumber),
  windscreenCover: d?.windscreenCover ?? false, thirdPartyCoverAmount: t(d?.thirdPartyCoverAmount), specialConditions: t(d?.specialConditions),
})

const MONEY: { key: keyof InsuranceForm; label: string }[] = [
  { key: "premium", label: "Premium" }, { key: "excess", label: "Excess" }, { key: "coverAmount", label: "Cover amount" },
  { key: "vehicleValue", label: "Vehicle value" }, { key: "thirdPartyCoverAmount", label: "Third-party cover" },
]
export function insuranceProblem(f: InsuranceForm): string | null {
  if (f.insured) {
    if (!f.insurer.trim()) return "Insurer is required when the vehicle is insured."
    if (!f.policyExpiry) return "Policy expiry is required when the vehicle is insured."
  }
  if (f.policyStart && f.policyExpiry && f.policyExpiry < f.policyStart) return "The policy cannot expire before it starts."
  for (const m of MONEY) { const v = f[m.key] as string; if (v !== "" && (!Number.isFinite(Number(v)) || Number(v) < 0)) return `${m.label} must be zero or more.` }
  if (f.trackingRequired && !f.trackingProvider.trim()) return "Name the tracking provider the insurer requires."
  return null
}

const s = (v: string) => (v.trim() === "" ? null : v.trim())
const n = (v: string) => (v.trim() === "" ? null : Number(v))
export function insuranceBody(f: InsuranceForm) {
  return {
    insured: f.insured, insurer: s(f.insurer), policyNumber: s(f.policyNumber), policyType: f.policyType || null, policyStart: s(f.policyStart), policyExpiry: s(f.policyExpiry),
    premium: n(f.premium), excess: n(f.excess), broker: s(f.broker), brokerContact: s(f.brokerContact), coverAmount: n(f.coverAmount), vehicleValue: n(f.vehicleValue),
    trackingRequired: f.trackingRequired, trackingProvider: f.trackingRequired ? s(f.trackingProvider) : null, trackingDeviceNumber: f.trackingRequired ? s(f.trackingDeviceNumber) : null,
    windscreenCover: f.windscreenCover, thirdPartyCoverAmount: n(f.thirdPartyCoverAmount), specialConditions: s(f.specialConditions),
  }
}
