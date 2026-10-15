// src/pages/settings/VatSettingsSection.tsx
//
// The company's VAT treatment: registered or not, and the standard rate, each change taking effect from a date. New charges and
// invoice lines are made at the rate in force on the day; a document already made keeps its own rate, so a change never rewrites one.
// A change that has not started can be cancelled. Switching VAT on needs the VAT registration number on the company details above.
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"

interface VatChange { id: string; vatRegistered: boolean; standardRate: number; effectiveFrom: string; reason: string | null; createdAt: string }
interface VatOverview {
  vatRegistered: boolean; ratePercent: number; configured: boolean; effectiveFrom: string | null
  upcoming: VatChange[]; history: VatChange[]; vatNumberOnFile: boolean; platformRate: number
}

const URL = "/api/v1/invoicing/vat-settings"
const todayIso = () => new Date().toISOString().slice(0, 10)
const describe = (c: { vatRegistered: boolean; standardRate: number }) => (c.vatRegistered ? `VAT registered, ${c.standardRate}%` : "Not registered for VAT, no VAT charged")
const apiMsg = (e: unknown, fallback: string) =>
  (e as { response?: { data?: { message?: string } } })?.response?.data?.message ?? fallback

const field: React.CSSProperties = { padding: "8px 10px", border: "1px solid var(--hf-border)", borderRadius: 8, fontSize: 14, color: "var(--hf-text)", background: "var(--hf-surface)" }
const btn = (primary = false): React.CSSProperties => ({
  padding: "7px 14px", borderRadius: 8, fontSize: 13, fontWeight: 600, cursor: "pointer",
  border: primary ? "none" : "1px solid var(--hf-border)",
  background: primary ? "var(--hf-primary)" : "var(--hf-surface)",
  color: primary ? "var(--hf-text-on-solid)" : "var(--hf-text-secondary)",
})

export default function VatSettingsSection() {
  const qc = useQueryClient()
  const [registered, setRegistered] = useState(true)
  const [rate, setRate] = useState("15")
  const [from, setFrom] = useState(todayIso())
  const [reason, setReason] = useState("")
  const [err, setErr] = useState("")
  const [done, setDone] = useState("")

  const { data, isLoading } = useQuery<VatOverview>({
    queryKey: ["vat-settings"], staleTime: 15_000,
    queryFn: async () => { const r = await apiClient.get(URL); return (r.data?.data ?? r.data) as VatOverview },
  })
  const refresh = (o: VatOverview) => { qc.setQueryData(["vat-settings"], o); qc.invalidateQueries({ queryKey: ["em-settings"] }) }
  const change = useMutation({
    mutationFn: async () => {
      const r = await apiClient.post(URL, { vatRegistered: registered, standardRate: registered ? Number(rate) : null, effectiveFrom: from, reason: reason.trim() || null })
      return (r.data?.data ?? r.data) as VatOverview
    },
    onSuccess: o => { refresh(o); setErr(""); setReason(""); setDone("VAT change saved.") },
    onError: e => { setDone(""); setErr(apiMsg(e, "Could not save the VAT change")) },
  })
  const cancel = useMutation({
    mutationFn: async (id: string) => { const r = await apiClient.delete(`${URL}/${id}`); return (r.data?.data ?? r.data) as VatOverview },
    onSuccess: o => { refresh(o); setErr(""); setDone("VAT change cancelled.") },
    onError: e => { setDone(""); setErr(apiMsg(e, "Could not cancel the VAT change")) },
  })

  if (isLoading || !data) return <div style={{ color: "var(--hf-text-muted)", fontSize: 13 }}>Loading…</div>
  const rateOk = !registered || (Number(rate) > 0 && Number(rate) <= 100)
  const blocked = registered && !data.vatNumberOnFile

  return (
    <div>
      <div style={{ fontSize: 14, color: "var(--hf-text)", marginBottom: 4 }}>
        <strong>Today:</strong> {describe({ vatRegistered: data.vatRegistered, standardRate: data.ratePercent })}
        {!data.configured && <span style={{ color: "var(--hf-text-muted)" }}> (not set yet: treated as registered at the standard {data.platformRate}%)</span>}
      </div>
      <div style={{ fontSize: 12, color: "var(--hf-text-muted)", marginBottom: 14 }}>
        New hire charges and invoice lines use the rate in force on the day. Documents already made keep the rate they were made with.
      </div>

      {data.upcoming.length > 0 && (
        <div style={{ marginBottom: 14 }}>
          <div style={{ fontSize: 12, fontWeight: 700, color: "var(--hf-text-secondary)", marginBottom: 4 }}>Changes still to start</div>
          {data.upcoming.map(c => (
            <div key={c.id} style={{ display: "flex", justifyContent: "space-between", alignItems: "center", gap: 10, padding: "6px 0", fontSize: 13 }}>
              <span>From {c.effectiveFrom}: {describe(c)}{c.reason ? ` · ${c.reason}` : ""}</span>
              <button style={btn()} disabled={cancel.isPending} onClick={() => cancel.mutate(c.id)}>Cancel change</button>
            </div>
          ))}
        </div>
      )}

      <div style={{ display: "flex", gap: 14, alignItems: "flex-end", flexWrap: "wrap" }}>
        <label style={{ fontSize: 13 }}>Treatment<br />
          <select aria-label="VAT treatment" value={registered ? "yes" : "no"} onChange={e => setRegistered(e.target.value === "yes")} style={field}>
            <option value="yes">Registered for VAT</option><option value="no">Not registered for VAT</option></select></label>
        {registered && (
          <label style={{ fontSize: 13 }}>Standard rate %<br />
            <input aria-label="VAT rate" type="number" min="0" max="100" step="0.01" value={rate} onChange={e => setRate(e.target.value)} style={{ ...field, width: 100 }} /></label>
        )}
        <label style={{ fontSize: 13 }}>From<br />
          <input aria-label="VAT effective from" type="date" min={todayIso()} value={from} onChange={e => setFrom(e.target.value)} style={field} /></label>
        <label style={{ fontSize: 13, flex: 1, minWidth: 180 }}>Reason (optional)<br />
          <input aria-label="VAT reason" maxLength={300} value={reason} onChange={e => setReason(e.target.value)} style={{ ...field, width: "100%", boxSizing: "border-box" }} /></label>
        <button style={btn(true)} disabled={change.isPending || !rateOk || !from || from < todayIso() || blocked} onClick={() => change.mutate()}>Save VAT change</button>
      </div>
      {blocked && <div role="status" style={{ fontSize: 12, color: "var(--hf-danger-text)", marginTop: 8 }}>Add the company's VAT registration number in Company Identity above (and save) before switching VAT on.</div>}
      {err && <div role="alert" style={{ fontSize: 13, color: "var(--hf-danger-text)", marginTop: 8 }}>{err}</div>}
      {done && <div role="status" style={{ fontSize: 13, color: "var(--hf-accent-text)", marginTop: 8 }}>{done}</div>}

      {data.history.length > 0 && (
        <div style={{ fontSize: 12, color: "var(--hf-text-muted)", marginTop: 14 }}>
          History: {data.history.map(c => `${c.effectiveFrom} ${c.vatRegistered ? `${c.standardRate}%` : "not registered"}`).join(" · ")}
        </div>
      )}
    </div>
  )
}
