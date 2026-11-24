// src/pages/complianceservices/ClientProfileTab.tsx
//
// The client's registered details, and where each key fact came from. A fact someone typed in is
// "unconfirmed" until a second person confirms it; changing it starts it again as unconfirmed.
import { useEffect, useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermissions } from "../../hooks/usePermission"
import { useAuth } from "../../hooks/useAuth"
import { BadgeCheck, CircleDashed } from "lucide-react"
import {
  ENTITY_TYPES, TURNOVER_BANDS, RISK_RATINGS, ONBOARDING_STATUSES, SOURCES, TRACKED_FIELDS,
  fieldProblem, labelOf, sourceState, confirmNeedsReason, confirmBlocker, profileProgress,
  type FieldSource,
} from "./profileRules"

interface SourceView extends FieldSource {
  method: string | null; reference: string | null; sourceDate: string | null
  verifiedBy: string | null; exceptionReason: string | null
}
interface Profile {
  clientId: string; legalName: string | null; tradingName: string | null; registrationNumber: string | null
  entityType: string | null; taxNumber: string | null; vatNumber: string | null; registeredAddress: string | null
  postalAddress: string | null; industry: string | null; employeeCount: number | null; turnoverBand: string | null
  riskRating: string | null; onboardingStatus: string; sources: SourceView[]; unconfirmedFields: string[]
}
type Form = {
  legalName: string; tradingName: string; registrationNumber: string; entityType: string; taxNumber: string; vatNumber: string
  registeredAddress: string; postalAddress: string; industry: string; employeeCount: string; turnoverBand: string
  riskRating: string; onboardingStatus: string
}

const ACCENT = "var(--hf-success-solid-strong)"
const ACCENT_TEXT = "var(--hf-success-text-strong)"
const unwrap = <T,>(r: { data: unknown }): T => ((r.data as { data?: T })?.data ?? r.data) as T
const errText = (e: unknown): string => {
  const x = e as { response?: { data?: { message?: string } }; message?: string }
  return x?.response?.data?.message ?? x?.message ?? "Something went wrong"
}

const input: React.CSSProperties = { width: "100%", padding: "8px 10px", border: "1px solid var(--hf-border)", borderRadius: 8, fontSize: 13, background: "var(--hf-surface)", color: "var(--hf-text)", boxSizing: "border-box" }
const label: React.CSSProperties = { display: "block", fontSize: 12, fontWeight: 600, color: "var(--hf-text-muted)", marginBottom: 4 }
const button: React.CSSProperties = { padding: "8px 14px", border: "none", borderRadius: 8, background: ACCENT, color: "#fff", fontSize: 13, fontWeight: 600, cursor: "pointer" }
const ghost: React.CSSProperties = { padding: "5px 10px", border: "1px solid var(--hf-border)", borderRadius: 8, background: "transparent", color: "var(--hf-text-muted)", fontSize: 12, cursor: "pointer" }

const toForm = (p: Profile): Form => ({
  legalName: p.legalName ?? "", tradingName: p.tradingName ?? "", registrationNumber: p.registrationNumber ?? "",
  entityType: p.entityType ?? "", taxNumber: p.taxNumber ?? "", vatNumber: p.vatNumber ?? "",
  registeredAddress: p.registeredAddress ?? "", postalAddress: p.postalAddress ?? "", industry: p.industry ?? "",
  employeeCount: p.employeeCount == null ? "" : String(p.employeeCount), turnoverBand: p.turnoverBand ?? "",
  riskRating: p.riskRating ?? "", onboardingStatus: p.onboardingStatus ?? "NOT_STARTED",
})

export default function ClientProfileTab({ clientId }: { clientId: string }) {
  const qc = useQueryClient()
  const canManage = usePermissions(["COMPLIANCE_SERVICES_MANAGE", "COMPLIANCE_SERVICES_ADMIN"])
  const { user } = useAuth()
  const base = `/api/v1/compliance-services/clients/${clientId}/profile`

  const { data: profile, isLoading } = useQuery<Profile>({
    queryKey: ["cs-profile", clientId],
    queryFn: async () => unwrap<Profile>(await apiClient.get(base)),
  })

  const [form, setForm] = useState<Form | null>(null)
  const [error, setError] = useState("")
  const [saved, setSaved] = useState(false)
  useEffect(() => { if (profile) setForm(toForm(profile)) }, [profile])

  const [openField, setOpenField] = useState<string | null>(null)
  const [src, setSrc] = useState({ source: "DOCUMENT", method: "", reference: "", sourceDate: "" })
  const [reason, setReason] = useState("")
  const [sourceError, setSourceError] = useState("")

  const refresh = (p: Profile) => { qc.setQueryData(["cs-profile", clientId], p); setSaved(true) }

  const save = useMutation({
    mutationFn: async (f: Form) => unwrap<Profile>(await apiClient.put(base, {
      ...f, employeeCount: f.employeeCount.trim() === "" ? null : Number(f.employeeCount),
    })),
    onSuccess: (p: Profile) => { setError(""); refresh(p) },
    onError: (e: unknown) => { setSaved(false); setError(errText(e)) },
  })
  const saveSource = useMutation({
    mutationFn: async (v: { field: string }) => unwrap<Profile>(await apiClient.put(`${base}/sources/${v.field}`, {
      source: src.source, method: src.method || null, reference: src.reference || null, sourceDate: src.sourceDate || null,
    })),
    onSuccess: (p: Profile) => { setSourceError(""); setOpenField(null); refresh(p) },
    onError: (e: unknown) => setSourceError(errText(e)),
  })
  const confirm = useMutation({
    mutationFn: async (v: { field: string; reason: string }) => unwrap<Profile>(await apiClient.post(`${base}/sources/${v.field}/confirm`, { exceptionReason: v.reason || null })),
    onSuccess: (p: Profile) => { setSourceError(""); setReason(""); refresh(p) },
    onError: (e: unknown) => setSourceError(errText(e)),
  })

  if (isLoading || !profile || !form) return <div style={{ padding: 24, color: "var(--hf-text-faint)" }}>Loading profile...</div>

  const set = (k: keyof Form) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement>) => {
    setSaved(false)
    setForm({ ...form, [k]: e.target.value })
  }
  const problems: Partial<Record<keyof Form, string | null>> = {
    registrationNumber: fieldProblem("registrationNumber", form.registrationNumber),
    taxNumber: fieldProblem("taxNumber", form.taxNumber),
    vatNumber: fieldProblem("vatNumber", form.vatNumber),
    employeeCount: fieldProblem("employeeCount", form.employeeCount),
  }
  const hasProblem = Object.values(problems).some(Boolean)
  const hasValueOf = (f: string) => { const v = profile[f as keyof Profile]; return v != null && v !== "" }
  const filled = TRACKED_FIELDS.map(t => t[0]).filter(hasValueOf)
  const progress = profileProgress(filled, profile.unconfirmedFields)
  const sourceOf = (f: string) => profile.sources.find(s => s.fieldName === f)

  const select = (k: keyof Form, options: [string, string][], blank = true) => (
    <select value={form[k]} onChange={set(k)} disabled={!canManage} style={input}>
      {blank && <option value="">—</option>}
      {options.map(o => <option key={o[0]} value={o[0]}>{o[1]}</option>)}
    </select>
  )
  const text = (k: keyof Form, props: React.InputHTMLAttributes<HTMLInputElement> = {}) => (
    <>
      <input value={form[k]} onChange={set(k)} disabled={!canManage} style={{ ...input, borderColor: problems[k] ? "var(--hf-danger-solid, #c0392b)" : "var(--hf-border)" }} {...props} />
      {problems[k] && <div style={{ fontSize: 11, color: "var(--hf-danger-text, #c0392b)", marginTop: 3 }}>{problems[k]}</div>}
    </>
  )
  const field = (title: string, control: React.ReactNode, wide = false) => (
    <div style={{ gridColumn: wide ? "1 / -1" : undefined }}><label style={label}>{title}</label>{control}</div>
  )

  return (
    <div>
      <div style={{ fontSize: 13, color: "var(--hf-text-muted)", marginBottom: 16 }}>
        {progress.filled} of {progress.total} key facts recorded, {progress.confirmed} confirmed by a second person.
      </div>

      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(240px, 1fr))", gap: 14 }}>
        {field("Legal name", text("legalName"))}
        {field("Trading name", text("tradingName"))}
        {field("Registration number", text("registrationNumber", { placeholder: "2015/123456/07" }))}
        {field("Entity type", select("entityType", ENTITY_TYPES))}
        {field("Tax number", text("taxNumber", { inputMode: "numeric" }))}
        {field("VAT number", text("vatNumber", { inputMode: "numeric" }))}
        {field("Industry", text("industry"))}
        {field("Employees", text("employeeCount", { inputMode: "numeric" }))}
        {field("Turnover band", select("turnoverBand", TURNOVER_BANDS))}
        {field("Risk rating", select("riskRating", RISK_RATINGS))}
        {field("Onboarding", select("onboardingStatus", ONBOARDING_STATUSES, false))}
        {field("Registered address", <textarea value={form.registeredAddress} onChange={set("registeredAddress")} disabled={!canManage} rows={2} style={input} />, true)}
        {field("Postal address", <textarea value={form.postalAddress} onChange={set("postalAddress")} disabled={!canManage} rows={2} style={input} />, true)}
      </div>

      {canManage && (
        <div style={{ display: "flex", alignItems: "center", gap: 12, marginTop: 16 }}>
          <button style={{ ...button, opacity: hasProblem || save.isPending ? 0.5 : 1 }} disabled={hasProblem || save.isPending} onClick={() => save.mutate(form)}>
            {save.isPending ? "Saving..." : "Save profile"}
          </button>
          {saved && <span style={{ fontSize: 12, color: ACCENT_TEXT }}>Saved</span>}
          {error && <span role="alert" style={{ fontSize: 12, color: "var(--hf-danger-text, #c0392b)" }}>{error}</span>}
        </div>
      )}

      <h3 style={{ fontSize: 14, margin: "28px 0 4px", color: "var(--hf-text)" }}>Where the facts came from</h3>
      <div style={{ fontSize: 12, color: "var(--hf-text-faint)", marginBottom: 12 }}>
        Record how a fact was obtained, then have a second person confirm it. Changing a fact clears its confirmation.
      </div>
      {sourceError && <div role="alert" style={{ fontSize: 12, color: "var(--hf-danger-text, #c0392b)", marginBottom: 8 }}>{sourceError}</div>}

      <div style={{ display: "grid", gap: 8 }}>
        {TRACKED_FIELDS.map(([name, title]) => {
          const s = sourceOf(name)
          const state = sourceState(s)
          const hasValue = hasValueOf(name)
          const needsReason = confirmNeedsReason(s, user?.userId)
          const blocker = confirmBlocker(needsReason, reason)
          return (
            <div key={name} style={{ border: "1px solid var(--hf-border)", borderRadius: 10, padding: "10px 12px", opacity: hasValue ? 1 : 0.55 }}>
              <div style={{ display: "flex", alignItems: "center", gap: 10, flexWrap: "wrap" }}>
                <span style={{ fontSize: 13, fontWeight: 600, minWidth: 150 }}>{title}</span>
                {!hasValue && <span style={{ fontSize: 12, color: "var(--hf-text-faint)" }}>Not recorded</span>}
                {hasValue && state === "NONE" && <span style={{ fontSize: 12, color: "var(--hf-text-faint)" }}>No source recorded</span>}
                {hasValue && s && (
                  <span style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>
                    {labelOf(SOURCES, s.source)}{s.method ? ` · ${s.method}` : ""}{s.reference ? ` · ${s.reference}` : ""}{s.sourceDate ? ` · ${s.sourceDate}` : ""}
                  </span>
                )}
                {hasValue && s && (
                  <span style={{ display: "inline-flex", alignItems: "center", gap: 4, fontSize: 12, color: state === "UNCONFIRMED" ? "var(--hf-text-faint)" : ACCENT_TEXT }}>
                    {state === "UNCONFIRMED" ? <CircleDashed size={13} /> : <BadgeCheck size={13} />}
                    {state === "UNCONFIRMED" ? "Unconfirmed" : state === "CONFIRMED_BY_ENTERER" ? "Confirmed by the person who entered it" : "Confirmed"}
                  </span>
                )}
                <span style={{ flex: 1 }} />
                {canManage && hasValue && (
                  <>
                    <button style={ghost} onClick={() => { setOpenField(openField === name ? null : name); setSourceError("") }}>Record source</button>
                    {s && state === "UNCONFIRMED" && (
                      <button style={ghost} disabled={!!blocker || confirm.isPending} onClick={() => confirm.mutate({ field: name, reason })}>Confirm</button>
                    )}
                  </>
                )}
              </div>
              {s && state === "UNCONFIRMED" && needsReason && canManage && (
                <div style={{ marginTop: 8 }}>
                  <input style={input} placeholder="You entered this. If no one else can confirm it, say why" value={reason} onChange={e => setReason(e.target.value)} />
                  {blocker && reason.length > 0 && <div style={{ fontSize: 11, color: "var(--hf-text-faint)", marginTop: 3 }}>{blocker}</div>}
                </div>
              )}
              {s?.exceptionReason && <div style={{ fontSize: 11, color: "var(--hf-text-faint)", marginTop: 6 }}>Reason given: {s.exceptionReason}</div>}
              {openField === name && (
                <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(160px, 1fr))", gap: 8, marginTop: 10 }}>
                  <select value={src.source} onChange={e => setSrc({ ...src, source: e.target.value })} style={input}>
                    {SOURCES.map(o => <option key={o[0]} value={o[0]}>{o[1]}</option>)}
                  </select>
                  <input style={input} placeholder="How it was checked" value={src.method} onChange={e => setSrc({ ...src, method: e.target.value })} />
                  <input style={input} placeholder="Document or reference" value={src.reference} onChange={e => setSrc({ ...src, reference: e.target.value })} />
                  <input style={input} type="date" value={src.sourceDate} onChange={e => setSrc({ ...src, sourceDate: e.target.value })} />
                  <button style={button} disabled={saveSource.isPending} onClick={() => saveSource.mutate({ field: name })}>Save source</button>
                </div>
              )}
            </div>
          )
        })}
      </div>
    </div>
  )
}
