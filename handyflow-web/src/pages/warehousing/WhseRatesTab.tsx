// src/pages/warehousing/WhseRatesTab.tsx
//
// A client's storage rates and minimum charge, as effective-dated versions.
// Versions are never edited: a change is a new version that starts today or later, so past days keep the
// rate they were charged. Rates shown include the warehouse-wide default's versions (marked "All clients").
// Only WAREHOUSING_ADMIN can add one (the server enforces it and audits every addition).
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"
import { WHSE_ACCENT } from "./constants"

interface TariffRow { id: string; clientId: string | null; itemId: string | null; monthlyRate: number; effectiveFrom: string; note: string | null }
interface TermsRow { id: string; clientId: string | null; minimumStorageCharge: number; freeDays: number; locationFeePerMonth: number; effectiveFrom: string; note: string | null }
interface ItemResponse { id: string; sku: string }

const cardStyle: React.CSSProperties = { background: "var(--hf-surface)", border: "1px solid var(--hf-border)", borderRadius: 14, padding: 20, marginBottom: 20 }
const inputStyle: React.CSSProperties = { width: "100%", padding: "9px 12px", border: "1px solid var(--hf-border)", borderRadius: 8, fontSize: 13, boxSizing: "border-box", fontFamily: "inherit" }
const labelStyle: React.CSSProperties = { fontSize: 12, fontWeight: 600, color: "var(--hf-text-secondary)", marginBottom: 5, display: "block" }
const money = (n: number) => new Intl.NumberFormat("en-ZA", { style: "currency", currency: "ZAR" }).format(n)
// The local calendar date, not UTC: just after midnight in South Africa UTC is still "yesterday",
// and the server refuses a start date in the past.
const today = () => { const d = new Date(); return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}` }
const errMsg = (e: unknown, fallback: string) => (e as any)?.response?.data?.message ?? fallback

/** The version in force today for each scope; older ones are history. */
function inForce<T extends { effectiveFrom: string }>(rows: T[], scope: (r: T) => string): Set<T> {
  const best = new Map<string, T>()
  for (const r of rows) {
    if (r.effectiveFrom > today()) continue
    const k = scope(r), cur = best.get(k)
    if (!cur || r.effectiveFrom > cur.effectiveFrom) best.set(k, r)   // rows arrive newest-first, so ties keep the newer
  }
  return new Set(best.values())
}

function Badge({ children, tone }: { children: React.ReactNode; tone: "on" | "soon" | "old" }) {
  const c = tone === "on" ? { bg: "var(--hf-success-soft-strong)", fg: "var(--hf-success-text-strong)" }
    : tone === "soon" ? { bg: "var(--hf-info-soft-strong)", fg: "var(--hf-info-text)" } : { bg: "var(--hf-surface-sunken)", fg: "var(--hf-text-muted)" }
  return <span style={{ fontSize: 10, fontWeight: 700, padding: "2px 8px", borderRadius: 20, background: c.bg, color: c.fg }}>{children}</span>
}

export default function WhseRatesTab({ clientId }: { clientId: string }) {
  const qc = useQueryClient()
  const canEdit = usePermission("WAREHOUSING_ADMIN")
  const { data: tariffs = [] } = useQuery<TariffRow[]>({ queryKey: ["whse-tariffs", clientId], queryFn: async () => (await apiClient.get(`/api/v1/warehousing/storage-tariffs?clientId=${clientId}`)).data })
  const { data: terms = [] } = useQuery<TermsRow[]>({ queryKey: ["whse-terms", clientId], queryFn: async () => (await apiClient.get(`/api/v1/warehousing/billing-terms?clientId=${clientId}`)).data })
  const { data: items = [] } = useQuery<ItemResponse[]>({ queryKey: ["whse-items", clientId], queryFn: async () => (await apiClient.get(`/api/v1/warehousing/clients/${clientId}/items`)).data })
  const skuOf = (id: string | null) => items.find(i => i.id === id)?.sku ?? "item"

  const [rate, setRate] = useState(""); const [itemId, setItemId] = useState(""); const [from, setFrom] = useState(today()); const [note, setNote] = useState("")
  const [sent, setSent] = useState<string | null>(null)
  const { data: rule } = useQuery<{ required: boolean }>({ queryKey: ["whse-tariff-rule"], queryFn: async () => (await apiClient.get("/api/v1/warehousing/tariff-approval-rule")).data })
  const needsApproval = !!rule?.required
  // A change that needs a second person comes back as a request id, not a version: say so rather than leave the list looking unchanged.
  const afterSave = (res: { data?: { requestId?: string } | null }, key: string) => {
    if (res?.data?.requestId) { setSent("Sent for approval. It applies once a second person approves it."); qc.invalidateQueries({ queryKey: ["whse-tariff-requests"] }) }
    else { setSent(null); qc.invalidateQueries({ queryKey: [key, clientId] }) }
  }
  const [minCharge, setMinCharge] = useState(""); const [minFrom, setMinFrom] = useState(today())
  const [freeDays, setFreeDays] = useState(""); const [binFee, setBinFee] = useState("")
  const num = (v: string) => (v.trim() === "" ? null : parseFloat(v))
  const termsInvalid = [minCharge, binFee].some(v => v.trim() !== "" && Number.isNaN(parseFloat(v)))
    || (freeDays.trim() !== "" && !/^\d+$/.test(freeDays.trim()))
  const termsEmpty = minCharge.trim() === "" && freeDays.trim() === "" && binFee.trim() === ""

  const addTariff = useMutation({
    mutationFn: async () => apiClient.post("/api/v1/warehousing/storage-tariffs", { clientId, itemId: itemId || null, monthlyRate: parseFloat(rate), effectiveFrom: from, note: note.trim() || null }),
    onSuccess: (res) => { afterSave(res, "whse-tariffs"); setRate(""); setNote("") },
  })
  const addTerms = useMutation({
    mutationFn: async () => apiClient.post("/api/v1/warehousing/billing-terms", {
      clientId, minimumStorageCharge: num(minCharge), freeDays: freeDays.trim() === "" ? null : parseInt(freeDays, 10), locationFeePerMonth: num(binFee), effectiveFrom: minFrom }),
    onSuccess: (res) => { afterSave(res, "whse-terms"); setMinCharge(""); setFreeDays(""); setBinFee("") },
  })

  const tariffNow = inForce(tariffs, t => `${t.clientId ?? "default"}|${t.itemId ?? ""}`)
  const termsNow = inForce(terms, t => t.clientId ?? "default")
  const tone = (on: boolean, startsLater: boolean) => (on ? "on" : startsLater ? "soon" : "old") as "on" | "soon" | "old"

  return (
    <div>
      {sent && <p role="status" style={{ background: "var(--hf-info-soft-strong)", color: "var(--hf-info-text)", borderRadius: 10, padding: "10px 14px", fontSize: 13, fontWeight: 600, margin: "0 0 16px" }}>{sent}</p>}
      <div style={cardStyle}>
        <p style={{ fontSize: 14, fontWeight: 800, color: "var(--hf-text)", margin: "0 0 4px" }}>Storage rates</p>
        <p style={{ fontSize: 12.5, color: "var(--hf-text-muted)", margin: "0 0 14px" }}>
          Price per unit per month (charged by the day, over 30). An item rate beats the client's rate, which beats the warehouse default. A new rate starts today or later; days already charged keep their rate.
        </p>
        {tariffs.length === 0 ? <p style={{ fontSize: 13, color: "var(--hf-text-faint)" }}>No rate versions yet; the rate set on the client, item or warehouse profile applies.</p> : (
          <div style={{ border: "1px solid var(--hf-border)", borderRadius: 10, overflow: "hidden", marginBottom: 16 }}>
            {tariffs.map((t, i) => (
              <div key={t.id} style={{ display: "flex", justifyContent: "space-between", alignItems: "center", padding: "10px 14px", borderTop: i === 0 ? "none" : "1px solid var(--hf-border-subtle)" }}>
                <div>
                  <p style={{ fontSize: 13, fontWeight: 700, color: "var(--hf-text)", margin: 0 }}>
                    {money(t.monthlyRate)} <span style={{ fontWeight: 500, color: "var(--hf-text-muted)" }}>/ unit / month · {t.itemId ? `item ${skuOf(t.itemId)}` : t.clientId ? "this client" : "All clients"}</span>
                  </p>
                  <p style={{ fontSize: 11.5, color: "var(--hf-text-faint)", margin: 0 }}>From {t.effectiveFrom}{t.note ? ` · ${t.note}` : ""}</p>
                </div>
                <Badge tone={tone(tariffNow.has(t), t.effectiveFrom > today())}>{tariffNow.has(t) ? "IN FORCE" : t.effectiveFrom > today() ? "STARTS LATER" : "SUPERSEDED"}</Badge>
              </div>
            ))}
          </div>
        )}
        {canEdit ? (
          <div style={{ display: "grid", gap: 12, maxWidth: 460 }}>
            <div><label htmlFor="rate" style={labelStyle}>New monthly rate per unit (R) *</label><input id="rate" type="number" min="0" step="0.01" style={inputStyle} value={rate} onChange={e => setRate(e.target.value)} /></div>
            <div><label htmlFor="rate-item" style={labelStyle}>Applies to</label>
              <select id="rate-item" style={inputStyle} value={itemId} onChange={e => setItemId(e.target.value)}>
                <option value="">All of this client's stock</option>
                {items.map(i => <option key={i.id} value={i.id}>Only {i.sku}</option>)}
              </select></div>
            <div><label htmlFor="rate-from" style={labelStyle}>Starts *</label><input id="rate-from" type="date" min={today()} style={inputStyle} value={from} onChange={e => setFrom(e.target.value)} /></div>
            <div><label htmlFor="rate-note" style={labelStyle}>Note</label><input id="rate-note" style={inputStyle} value={note} onChange={e => setNote(e.target.value)} placeholder="e.g. 2027 contract renewal" /></div>
            {addTariff.isError && <p style={{ color: "var(--hf-danger-text)", fontSize: 12, margin: 0 }}>{errMsg(addTariff.error, "Could not add this rate")}</p>}
            <button onClick={() => addTariff.mutate()} disabled={!rate || Number.isNaN(parseFloat(rate)) || !from || addTariff.isPending}
              style={{ padding: 10, borderRadius: 8, border: "none", background: WHSE_ACCENT, color: "var(--hf-text-on-solid)", fontSize: 13, fontWeight: 700, cursor: "pointer", opacity: (!rate || addTariff.isPending) ? 0.6 : 1 }}>
              {addTariff.isPending ? "Saving…" : needsApproval ? "Request rate" : "Add rate"}
            </button>
          </div>
        ) : <p style={{ fontSize: 12, color: "var(--hf-text-faint)", margin: 0 }}>Only a warehouse administrator can add a rate.</p>}
      </div>

      <div style={cardStyle}>
        <p style={{ fontSize: 14, fontWeight: 800, color: "var(--hf-text)", margin: "0 0 4px" }}>Minimum charge, free days and pallet/bin fee</p>
        <p style={{ fontSize: 12.5, color: "var(--hf-text-muted)", margin: "0 0 14px" }}>
          Minimum: per 30 days, shared out over the days each invoice covers; when storage comes to less, the invoice is topped up and the top-up is shown on it.
          Free days: stock received in the last N days (the day it arrives is day 1) is not charged per-unit storage. Pallet/bin fee: a flat monthly fee for each pallet or bin position the client occupies, charged by the day and waived while everything in that position is still inside its free days.
          A new version starts from the date you give; anything you leave empty carries over from the terms in force.
        </p>
        {terms.length === 0 ? <p style={{ fontSize: 13, color: "var(--hf-text-faint)" }}>No minimum set.</p> : (
          <div style={{ border: "1px solid var(--hf-border)", borderRadius: 10, overflow: "hidden", marginBottom: 16 }}>
            {terms.map((t, i) => (
              <div key={t.id} style={{ display: "flex", justifyContent: "space-between", alignItems: "center", padding: "10px 14px", borderTop: i === 0 ? "none" : "1px solid var(--hf-border-subtle)" }}>
                <p style={{ fontSize: 13, fontWeight: 700, color: "var(--hf-text)", margin: 0 }}>
                  {money(t.minimumStorageCharge)} <span style={{ fontWeight: 500, color: "var(--hf-text-muted)" }}>/ 30 days minimum
                    {t.freeDays > 0 ? ` · ${t.freeDays} free days` : ""}{t.locationFeePerMonth > 0 ? ` · ${money(t.locationFeePerMonth)} / pallet or bin / month` : ""}
                    {" "}· {t.clientId ? "this client" : "All clients"} · from {t.effectiveFrom}</span>
                </p>
                <Badge tone={tone(termsNow.has(t), t.effectiveFrom > today())}>{termsNow.has(t) ? "IN FORCE" : t.effectiveFrom > today() ? "STARTS LATER" : "SUPERSEDED"}</Badge>
              </div>
            ))}
          </div>
        )}
        {canEdit && (
          <div style={{ display: "grid", gap: 12, maxWidth: 460 }}>
            <div><label htmlFor="min" style={labelStyle}>Minimum per 30 days (R), 0 removes it</label><input id="min" type="number" min="0" step="0.01" style={inputStyle} value={minCharge} onChange={e => setMinCharge(e.target.value)} /></div>
            <div><label htmlFor="free-days" style={labelStyle}>Free days, 0 removes them</label><input id="free-days" type="number" min="0" max="3650" step="1" style={inputStyle} value={freeDays} onChange={e => setFreeDays(e.target.value)} /></div>
            <div><label htmlFor="bin-fee" style={labelStyle}>Pallet / bin fee per month (R), 0 removes it</label><input id="bin-fee" type="number" min="0" step="0.01" style={inputStyle} value={binFee} onChange={e => setBinFee(e.target.value)} /></div>
            <div><label htmlFor="min-from" style={labelStyle}>Starts *</label><input id="min-from" type="date" min={today()} style={inputStyle} value={minFrom} onChange={e => setMinFrom(e.target.value)} /></div>
            {addTerms.isError && <p style={{ color: "var(--hf-danger-text)", fontSize: 12, margin: 0 }}>{errMsg(addTerms.error, "Could not add this minimum")}</p>}
            <button onClick={() => addTerms.mutate()} disabled={termsEmpty || termsInvalid || !minFrom || addTerms.isPending}
              style={{ padding: 10, borderRadius: 8, border: "none", background: WHSE_ACCENT, color: "var(--hf-text-on-solid)", fontSize: 13, fontWeight: 700, cursor: "pointer", opacity: (termsEmpty || termsInvalid || addTerms.isPending) ? 0.6 : 1 }}>
              {addTerms.isPending ? "Saving…" : needsApproval ? "Request minimum" : "Set minimum"}
            </button>
          </div>
        )}
      </div>
    </div>
  )
}
