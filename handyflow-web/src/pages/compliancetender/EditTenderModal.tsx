// src/pages/compliancetender/EditTenderModal.tsx
//
// Correct a tender's details (PUT /compliance/tenders/{id}). The tender number and status are not editable here.
import { useState } from "react"
import { useMutation } from "@tanstack/react-query"
import { X } from "lucide-react"
import { apiClient } from "../../api/client"
import LookupInput from "../../components/ui/LookupInput"
import { TENDER_AUTHORITIES, INDUSTRIES, CLASS_OF_WORK } from "../../lookups/southAfrica"
import { type TenderForm, formFromTender, toTenderBody, validateTenderForm } from "./tender.logic"

type TenderLike = Parameters<typeof formFromTender>[0] & { id: string; tenderNumber: string }

const lbl: React.CSSProperties = { display: "block", fontSize: 12, fontWeight: 600, color: "var(--hf-text-secondary)", marginBottom: 4 }
const inp = (bad: boolean): React.CSSProperties => ({ width: "100%", padding: "9px 12px", border: `1.5px solid ${bad ? "var(--hf-danger)" : "var(--hf-border)"}`, borderRadius: 8, fontSize: 13, boxSizing: "border-box", background: "var(--hf-surface)", color: "var(--hf-text)" })

export default function EditTenderModal({ tender, onClose, onSaved }: { tender: TenderLike; onClose: () => void; onSaved: () => void }) {
  const [form, setForm] = useState<TenderForm>(formFromTender(tender))
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [apiError, setApiError] = useState("")
  const set = (patch: Partial<TenderForm>) => setForm(f => ({ ...f, ...patch }))

  const save = useMutation({
    mutationFn: () => apiClient.put(`/api/v1/compliance/tenders/${tender.id}`, toTenderBody(form)),
    onSuccess: () => { onSaved(); onClose() },
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    onError: (e: any) => setApiError(e.response?.data?.message ?? "The tender could not be saved"),
  })

  const submit = () => {
    const found = validateTenderForm(form)
    setErrors(found)
    if (Object.keys(found).length === 0) { setApiError(""); save.mutate() }
  }

  const err = (k: string) => errors[k] ? <div role="alert" style={{ fontSize: 11, color: "var(--hf-danger-text)", marginTop: 3 }}>{errors[k]}</div> : null
  const date = (k: "closingDate" | "briefingDate" | "siteInspectionDate", label: string) => (
    <div><label htmlFor={`et-${k}`} style={lbl}>{label}</label>
      <input id={`et-${k}`} type="date" value={form[k]} onChange={e => set({ [k]: e.target.value })} style={inp(!!errors[k])} />{err(k)}</div>
  )

  return (
    <div role="dialog" aria-modal="true" aria-label="Edit tender" style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.45)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1000, padding: 16 }}>
      <div style={{ background: "var(--hf-surface)", borderRadius: 14, width: "100%", maxWidth: 640, maxHeight: "92vh", overflowY: "auto", padding: 24 }}>
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 16 }}>
          <div>
            <div style={{ fontSize: 17, fontWeight: 700, color: "var(--hf-text)" }}>Edit tender</div>
            <div style={{ fontSize: 12, color: "var(--hf-text-faint)" }}>{tender.tenderNumber}</div>
          </div>
          <button onClick={onClose} aria-label="Close" style={{ background: "none", border: "none", cursor: "pointer", color: "var(--hf-text-muted)" }}><X size={18} /></button>
        </div>
        {apiError && <div role="alert" style={{ marginBottom: 14, padding: "10px 12px", background: "var(--hf-danger-soft)", border: "1px solid var(--hf-danger-border)", borderRadius: 8, fontSize: 13, color: "var(--hf-danger-text)" }}>{apiError}</div>}
        <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(240px, 1fr))", gap: 14 }}>
          <div style={{ gridColumn: "1 / -1" }}>
            <label htmlFor="et-name" style={lbl}>Tender name *</label>
            <input id="et-name" value={form.name} onChange={e => set({ name: e.target.value })} style={inp(!!errors.name)} />{err("name")}
          </div>
          <div><label htmlFor="et-auth" style={lbl}>Tender authority</label>
            <LookupInput id="et-auth" value={form.tenderAuthority} options={TENDER_AUTHORITIES} onChange={v => set({ tenderAuthority: v })} style={inp(false)} /></div>
          <div><label htmlFor="et-ref" style={lbl}>Authority reference</label>
            <input id="et-ref" value={form.authorityReferenceNumber} onChange={e => set({ authorityReferenceNumber: e.target.value })} style={inp(false)} /></div>
          {date("closingDate", "Closing date")}
          {date("briefingDate", "Briefing date")}
          {date("siteInspectionDate", "Site inspection")}
          <div><label htmlFor="et-value" style={lbl}>Estimated value (R)</label>
            <input id="et-value" inputMode="decimal" value={form.estimatedValue} onChange={e => set({ estimatedValue: e.target.value })} style={inp(!!errors.estimatedValue)} />{err("estimatedValue")}</div>
          <div><label htmlFor="et-industry" style={lbl}>Industry</label>
            <LookupInput id="et-industry" value={form.industry} options={INDUSTRIES} onChange={v => set({ industry: v })} style={inp(false)} /></div>
          <div><label htmlFor="et-class" style={lbl}>Required class of work</label>
            <LookupInput id="et-class" value={form.requiredClassOfWork} options={CLASS_OF_WORK} onChange={v => set({ requiredClassOfWork: v })} style={inp(false)} /></div>
          <div style={{ gridColumn: "1 / -1" }}>
            <label style={{ display: "inline-flex", alignItems: "center", gap: 8, fontSize: 13, color: "var(--hf-text-secondary)", cursor: "pointer" }}>
              <input type="checkbox" checked={form.requiresPricing} onChange={e => set({ requiresPricing: e.target.checked })} />
              The tender documents ask for a price (a package without pricing can't be marked ready to submit)
            </label>
          </div>
        </div>
        <div style={{ display: "flex", justifyContent: "flex-end", gap: 8, marginTop: 20 }}>
          <button onClick={onClose} style={{ padding: "9px 16px", borderRadius: 8, border: "1px solid var(--hf-border)", background: "var(--hf-surface)", color: "var(--hf-text)", fontSize: 13, fontWeight: 600, cursor: "pointer" }}>Cancel</button>
          <button onClick={submit} disabled={save.isPending} style={{ padding: "9px 18px", borderRadius: 8, border: "none", background: "var(--hf-sky-solid-strong)", color: "var(--hf-text-on-solid)", fontSize: 13, fontWeight: 700, cursor: "pointer" }}>{save.isPending ? "Saving…" : "Save changes"}</button>
        </div>
      </div>
    </div>
  )
}
