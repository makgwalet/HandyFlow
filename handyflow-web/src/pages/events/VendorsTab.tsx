// src/pages/events/VendorsTab.tsx
//
// Vendors of the selected event as a searchable, sortable table. Anyone who can read events sees it; adding and confirming a vendor needs EVENTS_SUPPLIER_MANAGE
// (or manage). People who may read the budget also see what is committed, paid and still owed per vendor, and which confirmed vendors have no commitment yet.
// The server has no edit or remove for vendors, so none is offered.
import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermissions } from "../../hooks/usePermission"
import { btnGhost, btnPrimary, card, ErrorLine, inputStyle, labelStyle, Modal } from "../warehousing/whseUi"
import { rand } from "./paymentOps"
import { emptyVendor, vendorBody, vendorProblem, VENDOR_TYPES, type VendorDraft } from "./wizardOps"
import { filterVendors, finFor, sortVendors, typeCounts, typeText, uncommitted, vendorTotals, type SortKey, type StatusFilter, type SupplierFin, type Vendor } from "./vendorOps"

const field: React.CSSProperties = { ...inputStyle, width: "100%" }
const money = (n: number | null | undefined) => (n == null || n <= 0 ? "–" : rand(n))

export default function VendorsTab({ eventId, eventTitle, onChangeEvent }: { eventId: string | null; eventTitle: string; onChangeEvent: () => void }) {
  const qc = useQueryClient()
  const canManage = usePermissions(["EVENTS_SUPPLIER_MANAGE", "EVENTS_MANAGE"])
  const canFin = usePermissions(["EVENTS_BUDGET_READ", "EVENTS_BUDGET_MANAGE", "EVENTS_SUPPLIER_MANAGE", "EVENTS_MANAGE"])
  const [q, setQ] = useState(""); const [type, setType] = useState("ALL"); const [status, setStatus] = useState<StatusFilter>("ALL")
  const [sort, setSort] = useState<{ key: SortKey; dir: "asc" | "desc" }>({ key: "companyName", dir: "asc" })
  const [adding, setAdding] = useState(false); const [f, setF] = useState<VendorDraft>(emptyVendor())
  const base = `/api/v1/events/${eventId}`
  const { data: vendors = [], isLoading, error } = useQuery<Vendor[]>({ queryKey: ["event-vendors", eventId], enabled: !!eventId, queryFn: async () => { const r = await apiClient.get(`${base}/vendors`); return r.data?.data ?? r.data ?? [] } })
  const { data: fin } = useQuery<SupplierFin[]>({ queryKey: ["event-supplier-fin", eventId], enabled: !!eventId && canFin, queryFn: async () => { const r = await apiClient.get(`${base}/budget/suppliers`); return r.data?.data ?? r.data ?? [] } })
  const done = () => { qc.invalidateQueries({ queryKey: ["event-vendors", eventId] }); qc.invalidateQueries({ queryKey: ["event-supplier-fin", eventId] }) }
  const add = useMutation({ mutationFn: () => apiClient.post(`${base}/vendors`, vendorBody(f)), onSuccess: () => { done(); setAdding(false) } })
  const confirm = useMutation({ mutationFn: (id: string) => apiClient.post(`${base}/vendors/${id}/confirm`), onSuccess: done })

  if (!eventId) return <div style={{ padding: 30, textAlign: "center", color: "var(--hf-text-muted)" }}>Pick an event first. <button type="button" style={inputStyle} onClick={onChangeEvent}>Choose event</button></div>
  const rows = sortVendors(filterVendors(vendors, { q, type, status }), sort.key, sort.dir)
  const t = vendorTotals(vendors)
  const head = (key: SortKey, text: string) => <th scope="col" aria-sort={sort.key === key ? (sort.dir === "asc" ? "ascending" : "descending") : "none"} style={{ padding: "6px 10px", textAlign: "left", fontSize: 11.5, color: "var(--hf-text-muted)" }}>
    <button type="button" style={{ background: "none", border: "none", padding: 0, font: "inherit", color: "inherit", cursor: "pointer" }} onClick={() => setSort(s => ({ key, dir: s.key === key && s.dir === "asc" ? "desc" : "asc" }))}>{text}{sort.key === key ? (sort.dir === "asc" ? " ▲" : " ▼") : ""}</button></th>
  const plain = (text: string) => <th scope="col" style={{ padding: "6px 10px", textAlign: "left", fontSize: 11.5, color: "var(--hf-text-muted)" }}>{text}</th>
  const problem = vendorProblem(f)
  const set = (k: keyof VendorDraft, v: string) => setF(p => ({ ...p, [k]: v }))

  return (
    <div>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", gap: 10, flexWrap: "wrap", marginBottom: 12 }}>
        <p style={{ fontSize: 13, color: "var(--hf-text-muted)", margin: 0 }}>Vendors for {eventTitle}</p>
        {canManage && <button type="button" style={btnPrimary} onClick={() => { setF(emptyVendor()); add.reset(); setAdding(true) }}>Add vendor</button>}
      </div>
      <div style={{ display: "flex", gap: 10, flexWrap: "wrap", marginBottom: 12 }}>
        {([["Vendors", t.count], ["Confirmed", t.confirmed], ["Awaiting confirmation", t.awaiting], ["Total quoted", money(t.quoted)]] as [string, string | number][]).map(([k, v]) =>
          <div key={k} style={{ ...card, padding: "8px 14px", minWidth: 110 }}><div style={{ fontSize: 18, fontWeight: 800 }}>{v}</div><div style={{ fontSize: 11.5, color: "var(--hf-text-muted)" }}>{k}</div></div>)}
      </div>
      <div style={{ display: "flex", gap: 8, flexWrap: "wrap", marginBottom: 10 }}>
        <input aria-label="Search vendors" placeholder="Search company, contact or service" style={{ ...inputStyle, minWidth: 220 }} value={q} onChange={e => setQ(e.target.value)} />
        <select aria-label="Vendor type" style={inputStyle} value={type} onChange={e => setType(e.target.value)}><option value="ALL">All types</option>{typeCounts(vendors).map(([k, n]) => <option key={k} value={k}>{typeText(k)} ({n})</option>)}</select>
        <select aria-label="Vendor status" style={inputStyle} value={status} onChange={e => setStatus(e.target.value as StatusFilter)}><option value="ALL">Any status</option><option value="CONFIRMED">Confirmed</option><option value="AWAITING">Awaiting confirmation</option></select>
      </div>
      <ErrorLine error={error ?? confirm.error} fallback="That could not be completed" />
      {isLoading ? <p>Loading…</p> : vendors.length === 0 ? <p style={{ color: "var(--hf-text-muted)" }}>No vendors yet. Add caterers, AV, security and the rest.</p> : rows.length === 0 ? <p style={{ color: "var(--hf-text-muted)" }}>No vendors match.</p> : (
        <div style={{ overflowX: "auto" }}>
          <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 13 }}>
            <thead><tr>{head("companyName", "Vendor")}{head("vendorType", "Supplies")}{plain("Contact")}{head("quotedAmount", "Quoted")}{canFin && <>{plain("Committed")}{plain("Paid")}{plain("Still owed")}</>}{head("status", "Status")}{canManage && plain("")}</tr></thead>
            <tbody>
              {rows.map(v => {
                const x = canFin && fin ? finFor(fin, v.id) : undefined
                return (
                  <tr key={v.id} style={{ borderTop: "1px solid var(--hf-border)", verticalAlign: "top" }}>
                    <td style={{ padding: "8px 10px" }}><strong>{v.companyName}</strong>{v.serviceDescription && <div style={{ fontSize: 11.5, color: "var(--hf-text-muted)" }}>{v.serviceDescription}</div>}</td>
                    <td style={{ padding: "8px 10px" }}>{typeText(v.vendorType)}</td>
                    <td style={{ padding: "8px 10px", fontSize: 12 }}>{v.contactName ?? ""}{v.contactPhone && <div><a href={`tel:${v.contactPhone.replace(/[^\d+]/g, "")}`}>{v.contactPhone}</a></div>}{v.contactEmail && <div><a href={`mailto:${v.contactEmail}`}>{v.contactEmail}</a></div>}</td>
                    <td style={{ padding: "8px 10px" }}>{money(v.quotedAmount)}</td>
                    {canFin && <><td style={{ padding: "8px 10px" }}>{money(x?.committed)}</td><td style={{ padding: "8px 10px" }}>{money(x?.paid)}</td><td style={{ padding: "8px 10px", fontWeight: (x?.outstanding ?? 0) > 0 ? 700 : 400 }}>{money(x?.outstanding)}</td></>}
                    <td style={{ padding: "8px 10px" }}>
                      <span style={{ fontSize: 11, padding: "1px 8px", borderRadius: 20, background: v.confirmed ? "var(--hf-success-soft-strong)" : "var(--hf-warning-soft)", color: v.confirmed ? "var(--hf-success-text-strong)" : "var(--hf-warning-text)" }}>{v.confirmed ? "Confirmed" : "Awaiting"}</span>
                      {uncommitted(v, canFin ? fin : undefined) && <div role="note" style={{ fontSize: 11, color: "var(--hf-warning-text)" }}>Not committed in the budget</div>}
                    </td>
                    {canManage && <td style={{ padding: "8px 10px" }}>{!v.confirmed && <button type="button" style={btnGhost} disabled={confirm.isPending} aria-label={`Confirm ${v.companyName}`} onClick={() => confirm.mutate(v.id)}>Confirm</button>}</td>}
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      )}
      {adding && (
        <Modal title="Add vendor" onClose={() => setAdding(false)} width={540}>
          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 10 }}>
            <div><label style={labelStyle} htmlFor="vd-co">Company *</label><input id="vd-co" style={field} maxLength={200} value={f.companyName} onChange={e => set("companyName", e.target.value)} /></div>
            <div><label style={labelStyle} htmlFor="vd-type">Supplies *</label><select id="vd-type" style={field} value={f.vendorType} onChange={e => set("vendorType", e.target.value)}>{VENDOR_TYPES.map(k => <option key={k} value={k}>{typeText(k)}</option>)}</select></div>
            <div><label style={labelStyle} htmlFor="vd-ct">Contact name</label><input id="vd-ct" style={field} maxLength={200} value={f.contactName} onChange={e => set("contactName", e.target.value)} /></div>
            <div><label style={labelStyle} htmlFor="vd-ph">Phone</label><input id="vd-ph" style={field} maxLength={50} value={f.contactPhone} onChange={e => set("contactPhone", e.target.value)} /></div>
            <div><label style={labelStyle} htmlFor="vd-em">Email</label><input id="vd-em" style={field} maxLength={254} value={f.contactEmail} onChange={e => set("contactEmail", e.target.value)} /></div>
            <div><label style={labelStyle} htmlFor="vd-q">Quoted amount (R)</label><input id="vd-q" style={field} inputMode="decimal" value={f.quotedAmount} onChange={e => set("quotedAmount", e.target.value)} /></div>
            <div style={{ gridColumn: "1/-1" }}><label style={labelStyle} htmlFor="vd-sv">What they will provide</label><textarea id="vd-sv" style={{ ...field, minHeight: 56 }} maxLength={5000} value={f.serviceDescription} onChange={e => set("serviceDescription", e.target.value)} /></div>
          </div>
          <ErrorLine error={add.error} fallback="The vendor could not be added" />
          {problem && <p style={{ fontSize: 12, color: "var(--hf-text-muted)", margin: "10px 0 0" }}>{problem}</p>}
          <div style={{ display: "flex", gap: 8, marginTop: 14 }}>
            <button type="button" style={{ ...btnGhost, flex: 1, justifyContent: "center" }} onClick={() => setAdding(false)}>Back</button>
            <button type="button" style={{ ...btnPrimary, flex: 1, justifyContent: "center", opacity: problem ? 0.5 : 1 }} disabled={!!problem || add.isPending} onClick={() => add.mutate()}>{add.isPending ? "Working…" : "Add vendor"}</button>
          </div>
        </Modal>
      )}
    </div>
  )
}
