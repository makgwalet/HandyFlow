// src/pages/legalcompliance/ThirdPartiesTab.tsx
//
// Supplier, partner, contractor and service-provider compliance. Endpoints (/api/v1/legalcompliance/third-parties):
//   GET / · /summary · /{id} · POST / · PUT /{id} · POST /{id}/approve|suspend|offboard|review
//   POST /{id}/items · PUT|DELETE /{id}/items/{itemId} · GET /{id}/items/{itemId}/files · POST /{id}/items/{itemId}/verify|reject
// A third party can be approved only when every required item is verified and unexpired and, if it handles personal information, a verified operator
// agreement is on file. The person who created the record cannot approve it. Item files go through the shared evidence API (entity type THIRD_PARTY_ITEM).
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"
import { input, primaryBtn, ghostBtn, unwrap, one, fmtDate, errMsg } from "./lcHelpers"
import { Modal, Field, Err, Badge } from "./LcUi"
import { EvidenceFiles } from "./LcEvidence"
import { ChevronDown, ChevronUp, Plus } from "lucide-react"

const base = "/api/v1/legalcompliance/third-parties"
const pretty = (s: string) => s.replace(/_/g, " ").toLowerCase()
const MIN = 15
const RISK: Record<string, { color: string; bg: string }> = { LOW: { color: "#166534", bg: "#DCFCE7" }, MEDIUM: { color: "#92400E", bg: "#FEF3C7" }, HIGH: { color: "#991B1B", bg: "#FEE2E2" } }
const STATUS: Record<string, { label: string; color: string; bg: string }> = {
  ONBOARDING: { label: "Onboarding", color: "#475569", bg: "#F1F5F9" }, APPROVED: { label: "Approved", color: "#166534", bg: "#DCFCE7" }, SUSPENDED: { label: "Suspended", color: "#991B1B", bg: "#FEE2E2" }, OFFBOARDED: { label: "Offboarded", color: "#475569", bg: "#E2E8F0" },
}
const ISTATUS: Record<string, { color: string; bg: string }> = { PENDING: { color: "#475569", bg: "#F1F5F9" }, VERIFIED: { color: "#166534", bg: "#DCFCE7" }, REJECTED: { color: "#991B1B", bg: "#FEE2E2" } }
const EXPIRY: Record<string, { label: string; color: string; bg: string } | null> = { NO_EXPIRY: null, VALID: null, EXPIRING: { label: "Expiring soon", color: "#92400E", bg: "#FEF3C7" }, EXPIRED: { label: "Expired", color: "#991B1B", bg: "#FEE2E2" } }
const KINDS = ["DILIGENCE_CHECK", "CERTIFICATE", "OPERATOR_AGREEMENT", "INSURANCE", "OTHER"]

interface TP {
  id: string; reference: string; name: string; partyType: string; criticality: string; riskRating: string; status: string; ownerName: string; supplierRef: string | null; handlesPersonalInfo: boolean
  reviewMonths: number; lastReviewedOn: string | null; nextReviewDue: string | null; reviewOverdue: boolean; approvedByName: string | null; approvedOn: string | null; notes: string | null
  createdByName: string | null; blockers: string[]; itemCount: number; expiringCount: number; expiredCount: number
}
interface Item { id: string; kind: string; title: string; issuer: string | null; referenceNo: string | null; required: boolean; status: string; validTo: string | null; expiry: string; verifiedByName: string | null; verifiedOn: string | null; note: string | null; evidenceCount: number }
interface Detail { party: TP; items: Item[] }
interface Summary { onboarding: number; approved: number; suspended: number; reviewOverdue: number; expiring: number; expired: number }

const blank = { id: "", name: "", partyType: "SUPPLIER", criticality: "MEDIUM", riskRating: "MEDIUM", ownerName: "", supplierRef: "", handlesPersonalInfo: false, reviewMonths: "", notes: "" }
const blankItem = { id: "", kind: "DILIGENCE_CHECK", title: "", issuer: "", referenceNo: "", required: true, validTo: "" }
type Act = { t: TP; kind: "suspend" | "offboard" | "review"; a: string; risk: string }
type ItemAct = { t: TP; item: Item; a: string }

export default function ThirdPartiesTab() {
  const qc = useQueryClient()
  const hasManage = usePermission("LEGALCOMPLIANCE_THIRDPARTY_MANAGE")
  const hasAdmin = usePermission("LEGALCOMPLIANCE_ADMIN")
  const hasApprove = usePermission("LEGALCOMPLIANCE_THIRDPARTY_APPROVE")
  const canManage = hasManage || hasAdmin
  const canApprove = hasApprove || hasAdmin

  const [filter, setFilter] = useState("")
  const [expanded, setExpanded] = useState<string | null>(null)
  const [form, setForm] = useState<typeof blank | null>(null)
  const [itemForm, setItemForm] = useState<{ t: TP; v: typeof blankItem } | null>(null)
  const [act, setAct] = useState<Act | null>(null)
  const [rejectItem, setRejectItem] = useState<ItemAct | null>(null)
  const [apiError, setApiError] = useState("")
  const [notice, setNotice] = useState("")

  const { data: summary } = useQuery<Summary>({ queryKey: ["lc-tp", "summary"], queryFn: async () => one(await apiClient.get(`${base}/summary`)) })
  const { data: parties = [], isLoading } = useQuery<TP[]>({ queryKey: ["lc-tp", "list", filter], queryFn: async () => unwrap(await apiClient.get(base, { params: filter ? { status: filter } : {} })) })
  const { data: detail } = useQuery<Detail>({ queryKey: ["lc-tp", "detail", expanded], enabled: !!expanded, queryFn: async () => one(await apiClient.get(`${base}/${expanded}`)) })

  const refresh = () => { qc.invalidateQueries({ queryKey: ["lc-tp"] }); setApiError("") }
  const fail = (m: string) => (e: unknown) => setApiError(errMsg(e, m))
  const ok = (msg: string, after?: () => void) => () => { refresh(); after?.(); setNotice(msg) }

  const save = useMutation({
    mutationFn: (f: typeof blank) => {
      const body = { name: f.name.trim(), partyType: f.partyType, criticality: f.criticality, riskRating: f.riskRating, ownerName: f.ownerName.trim(), supplierRef: f.supplierRef || null, handlesPersonalInfo: f.handlesPersonalInfo, reviewMonths: f.reviewMonths ? +f.reviewMonths : 0, notes: f.notes.trim() || null }
      return f.id ? apiClient.put(`${base}/${f.id}`, body) : apiClient.post(`${base}`, body)
    }, onSuccess: ok("Saved", () => setForm(null)), onError: fail("Failed to save third party"),
  })
  const approve = useMutation({ mutationFn: (id: string) => apiClient.post(`${base}/${id}/approve`), onSuccess: ok("Approved"), onError: fail("Cannot approve") })
  const run = useMutation({
    mutationFn: (a: Act) => a.kind === "review" ? apiClient.post(`${base}/${a.t.id}/review`, { riskRating: a.risk || null, note: a.a.trim() }) : apiClient.post(`${base}/${a.t.id}/${a.kind}`, { reason: a.a.trim() }),
    onSuccess: ok("Saved", () => setAct(null)), onError: fail("Action failed"),
  })
  const saveItem = useMutation({
    mutationFn: (x: NonNullable<typeof itemForm>) => {
      const v = x.v; const body = { kind: v.kind, title: v.title.trim(), issuer: v.issuer.trim() || null, referenceNo: v.referenceNo.trim() || null, required: v.required, validTo: v.validTo || null }
      return v.id ? apiClient.put(`${base}/${x.t.id}/items/${v.id}`, body) : apiClient.post(`${base}/${x.t.id}/items`, body)
    }, onSuccess: ok("Item saved", () => setItemForm(null)), onError: fail("Failed to save item"),
  })
  const itemAct = useMutation({
    mutationFn: ({ t, i, kind }: { t: TP; i: Item; kind: "verify" | "remove" }) => kind === "verify" ? apiClient.post(`${base}/${t.id}/items/${i.id}/verify`) : apiClient.delete(`${base}/${t.id}/items/${i.id}`),
    onSuccess: ok("Updated"), onError: fail("Failed to update item"),
  })
  const reject = useMutation({ mutationFn: (a: ItemAct) => apiClient.post(`${base}/${a.t.id}/items/${a.item.id}/reject`, { reason: a.a.trim() }), onSuccess: ok("Item rejected", () => setRejectItem(null)), onError: fail("Failed to reject item") })

  const set = (k: keyof typeof blank, v: string | boolean) => setForm(f => f && { ...f, [k]: v })
  const card = (label: string, v: number | undefined, color = "#0F172A") => (
    <div style={{ flex: 1, minWidth: 120, background: "#fff", border: "1px solid #E2E8F0", borderRadius: 12, padding: "12px 16px" }}>
      <div style={{ fontSize: 12, color: "#64748B", fontWeight: 600 }}>{label}</div><div style={{ fontSize: 24, fontWeight: 800, color }}>{v ?? "—"}</div>
    </div>
  )
  const titles = { suspend: "Suspend", offboard: "Offboard", review: "Record review" }

  return (
    <div>
      <div style={{ display: "flex", gap: 12, flexWrap: "wrap", marginBottom: 16 }}>
        {card("Onboarding", summary?.onboarding)}{card("Approved", summary?.approved, "#166534")}{card("Suspended", summary?.suspended, summary?.suspended ? "#991B1B" : undefined)}
        {card("Review overdue", summary?.reviewOverdue, summary?.reviewOverdue ? "#991B1B" : undefined)}{card("Documents expiring", summary?.expiring, "#92400E")}{card("Documents expired", summary?.expired, summary?.expired ? "#991B1B" : undefined)}
      </div>
      <div style={{ display: "flex", gap: 12, alignItems: "center", marginBottom: 12 }}>
        {canManage && <button style={primaryBtn} onClick={() => { setApiError(""); setForm({ ...blank }) }}><Plus size={14} style={{ verticalAlign: -2 }} /> Add third party</button>}
        <select style={{ ...input, width: 180 }} value={filter} onChange={e => setFilter(e.target.value)}><option value="">All statuses</option>{Object.keys(STATUS).map(s => <option key={s} value={s}>{STATUS[s].label}</option>)}</select>
      </div>
      {notice && <div style={{ background: "#ECFDF5", border: "1px solid #A7F3D0", color: "#065F46", borderRadius: 8, padding: "8px 12px", fontSize: 13, marginBottom: 12 }}>{notice}</div>}
      {apiError && !form && !act && !itemForm && !rejectItem && <Err m={apiError} />}

      {isLoading ? <p style={{ color: "#64748B" }}>Loading…</p> : parties.length === 0 ? <p style={{ color: "#64748B", fontSize: 14 }}>No third parties.</p> : parties.map(t => {
        const open = expanded === t.id
        const d = open && detail?.party.id === t.id ? detail : null
        const locked = t.status === "OFFBOARDED"
        return (
          <div key={t.id} style={{ border: `1px solid ${t.reviewOverdue || t.expiredCount ? "#FECACA" : "#E2E8F0"}`, borderRadius: 10, background: "#fff", marginBottom: 8 }}>
            <button onClick={() => { setExpanded(open ? null : t.id); setApiError("") }} style={{ all: "unset", cursor: "pointer", display: "flex", gap: 12, alignItems: "center", padding: "12px 16px", width: "calc(100% - 32px)" }}>
              <div style={{ flex: 1, minWidth: 0 }}><div style={{ fontWeight: 700, fontSize: 14, color: "#0F172A" }}>{t.reference} — {t.name}</div>
                <div style={{ fontSize: 12, color: "#64748B" }}>{pretty(t.partyType)} · {pretty(t.criticality)} criticality · owner {t.ownerName}{t.nextReviewDue ? ` · review ${fmtDate(t.nextReviewDue)}` : ""}{t.handlesPersonalInfo ? " · handles personal information" : ""}</div></div>
              {t.reviewOverdue && <Badge text="Review overdue" color="#991B1B" bg="#FEE2E2" />}{t.expiredCount > 0 && <Badge text={`${t.expiredCount} expired`} color="#991B1B" bg="#FEE2E2" />}{t.expiringCount > 0 && <Badge text={`${t.expiringCount} expiring`} color="#92400E" bg="#FEF3C7" />}
              <Badge text={`Risk ${t.riskRating}`} {...RISK[t.riskRating]} /><Badge text={STATUS[t.status].label} color={STATUS[t.status].color} bg={STATUS[t.status].bg} />{open ? <ChevronUp size={16} /> : <ChevronDown size={16} />}
            </button>
            {open && (
              <div style={{ padding: "0 16px 14px", fontSize: 13, color: "#475569" }}>
                {t.notes && <p style={{ marginTop: 0, whiteSpace: "pre-wrap" }}>{t.notes}</p>}
                <p style={{ fontSize: 12 }}>Created by {t.createdByName ?? "—"}{t.approvedOn ? ` · approved ${fmtDate(t.approvedOn)} by ${t.approvedByName}` : ""}{t.lastReviewedOn ? ` · last reviewed ${fmtDate(t.lastReviewedOn)}` : ""} · reviewed every {t.reviewMonths} months</p>
                {t.blockers.length > 0 && (
                  <div style={{ background: "#FFFBEB", border: "1px solid #FDE68A", borderRadius: 8, padding: 10, marginBottom: 8, fontSize: 12, color: "#92400E" }}>
                    <b>Cannot be approved yet:</b><ul style={{ margin: "4px 0 0 18px", padding: 0 }}>{t.blockers.map(b => <li key={b}>{b}</li>)}</ul>
                  </div>
                )}
                <div style={{ fontWeight: 700, marginBottom: 4 }}>Due diligence and documents</div>
                {!d ? <p style={{ fontSize: 12 }}>Loading…</p> : d.items.length === 0 ? <p style={{ fontSize: 12 }}>Nothing recorded yet.</p> : d.items.map(i => {
                  const ex = EXPIRY[i.expiry]
                  return (
                    <div key={i.id} style={{ border: "1px solid #F1F5F9", borderRadius: 8, padding: "8px 10px", marginBottom: 6 }}>
                      <div style={{ display: "flex", gap: 8, alignItems: "center", flexWrap: "wrap" }}>
                        <div style={{ flex: 1, minWidth: 160 }}><b>{i.title}</b> <span style={{ fontSize: 12, color: "#64748B" }}>· {pretty(i.kind)}{i.required ? "" : " · optional"}{i.issuer ? ` · ${i.issuer}` : ""}{i.referenceNo ? ` · ${i.referenceNo}` : ""}{i.validTo ? ` · valid to ${fmtDate(i.validTo)}` : ""}</span></div>
                        {ex && <Badge text={ex.label} color={ex.color} bg={ex.bg} />}<Badge text={pretty(i.status)} {...ISTATUS[i.status]} />
                      </div>
                      {i.verifiedByName && <div style={{ fontSize: 11, color: "#94A3B8" }}>Verified by {i.verifiedByName} on {fmtDate(i.verifiedOn)}</div>}
                      {i.note && <div style={{ fontSize: 12, color: "#991B1B" }}>{i.note}</div>}
                      {!locked && <EvidenceFiles filesUrl={`${base}/${t.id}/items/${i.id}/files`} entityType="THIRD_PARTY_ITEM" entityId={i.id} evidenceType="THIRD_PARTY_EVIDENCE" canEdit={canManage} onChange={refresh} />}
                      {canManage && !locked && (
                        <div style={{ display: "flex", gap: 6, flexWrap: "wrap" }}>
                          {i.status !== "VERIFIED" && <button style={{ ...ghostBtn, padding: "4px 10px", fontSize: 12 }} disabled={itemAct.isPending} onClick={() => itemAct.mutate({ t, i, kind: "verify" })}>Verify</button>}
                          {i.status !== "REJECTED" && <button style={{ ...ghostBtn, padding: "4px 10px", fontSize: 12 }} onClick={() => { setApiError(""); setRejectItem({ t, item: i, a: "" }) }}>Reject</button>}
                          <button style={{ ...ghostBtn, padding: "4px 10px", fontSize: 12 }} onClick={() => { setApiError(""); setItemForm({ t, v: { id: i.id, kind: i.kind, title: i.title, issuer: i.issuer ?? "", referenceNo: i.referenceNo ?? "", required: i.required, validTo: i.validTo ?? "" } }) }}>Edit</button>
                          <button style={{ ...ghostBtn, padding: "4px 10px", fontSize: 12 }} onClick={() => { if (confirm(`Remove ${i.title}?`)) itemAct.mutate({ t, i, kind: "remove" }) }}>Remove</button>
                        </div>
                      )}
                    </div>
                  )
                })}
                {apiError && <Err m={apiError} />}
                <div style={{ display: "flex", gap: 8, flexWrap: "wrap", marginTop: 8 }}>
                  {canManage && !locked && <button style={ghostBtn} onClick={() => { setApiError(""); setItemForm({ t, v: { ...blankItem } }) }}>Add item</button>}
                  {canManage && !locked && <button style={ghostBtn} onClick={() => { setApiError(""); setForm({ id: t.id, name: t.name, partyType: t.partyType, criticality: t.criticality, riskRating: t.riskRating, ownerName: t.ownerName, supplierRef: t.supplierRef ?? "", handlesPersonalInfo: t.handlesPersonalInfo, reviewMonths: String(t.reviewMonths), notes: t.notes ?? "" }) }}>Edit details</button>}
                  {canApprove && (t.status === "ONBOARDING" || t.status === "SUSPENDED") && <button style={primaryBtn} disabled={approve.isPending || t.blockers.length > 0} onClick={() => approve.mutate(t.id)}>Approve</button>}
                  {canManage && t.status === "APPROVED" && <button style={ghostBtn} onClick={() => { setApiError(""); setAct({ t, kind: "review", a: "", risk: t.riskRating }) }}>Record review</button>}
                  {canManage && t.status === "APPROVED" && <button style={ghostBtn} onClick={() => { setApiError(""); setAct({ t, kind: "suspend", a: "", risk: "" }) }}>Suspend</button>}
                  {canManage && !locked && <button style={ghostBtn} onClick={() => { setApiError(""); setAct({ t, kind: "offboard", a: "", risk: "" }) }}>Offboard</button>}
                </div>
                {(t.status === "ONBOARDING" || t.status === "SUSPENDED") && <p style={{ fontSize: 12, color: "#64748B" }}>You cannot approve a third party you created.</p>}
              </div>
            )}
          </div>
        )
      })}

      {form && (
        <Modal title={form.id ? "Edit third party" : "Add third party"} onClose={() => setForm(null)}>
          <Err m={apiError} />
          <Field l="Name *"><input style={input} value={form.name} onChange={e => set("name", e.target.value)} /></Field>
          <div style={{ display: "flex", gap: 12 }}>
            <div style={{ flex: 1 }}><Field l="Type *"><select style={input} value={form.partyType} onChange={e => set("partyType", e.target.value)}>{["SUPPLIER", "PARTNER", "CONTRACTOR", "SERVICE_PROVIDER"].map(x => <option key={x} value={x}>{pretty(x)}</option>)}</select></Field></div>
            <div style={{ flex: 1 }}><Field l="Criticality *"><select style={input} value={form.criticality} onChange={e => set("criticality", e.target.value)}>{["LOW", "MEDIUM", "HIGH", "CRITICAL"].map(x => <option key={x} value={x}>{pretty(x)}</option>)}</select></Field></div>
            <div style={{ flex: 1 }}><Field l="Risk *"><select style={input} value={form.riskRating} onChange={e => set("riskRating", e.target.value)}>{["LOW", "MEDIUM", "HIGH"].map(x => <option key={x} value={x}>{pretty(x)}</option>)}</select></Field></div>
          </div>
          <div style={{ display: "flex", gap: 12 }}>
            <div style={{ flex: 2 }}><Field l="Relationship owner *"><input style={input} value={form.ownerName} onChange={e => set("ownerName", e.target.value)} /></Field></div>
            <div style={{ flex: 1 }}><Field l="Review every (months)"><input type="number" min={1} max={60} placeholder="default" style={input} value={form.reviewMonths} onChange={e => set("reviewMonths", e.target.value)} /></Field></div>
          </div>
          <label style={{ fontSize: 13, display: "flex", gap: 6, marginBottom: 8 }}><input type="checkbox" checked={form.handlesPersonalInfo} onChange={e => set("handlesPersonalInfo", e.target.checked)} /> Handles personal information on our behalf (operator)</label>
          {form.id && form.handlesPersonalInfo && <p style={{ fontSize: 12, color: "#92400E", marginTop: 0 }}>If this party was not handling personal information before and is approved, saving will suspend it until an operator agreement is verified.</p>}
          <Field l="Notes"><textarea style={{ ...input, minHeight: 50 }} value={form.notes} onChange={e => set("notes", e.target.value)} /></Field>
          <div style={{ display: "flex", justifyContent: "flex-end", gap: 8 }}><button style={ghostBtn} onClick={() => setForm(null)}>Cancel</button><button style={primaryBtn} disabled={save.isPending || !form.name.trim() || !form.ownerName.trim()} onClick={() => save.mutate(form)}>Save</button></div>
        </Modal>
      )}
      {itemForm && (
        <Modal title={`${itemForm.v.id ? "Edit" : "Add"} item — ${itemForm.t.name}`} onClose={() => setItemForm(null)}>
          <Err m={apiError} />
          <Field l="Kind *"><select style={input} value={itemForm.v.kind} onChange={e => setItemForm({ ...itemForm, v: { ...itemForm.v, kind: e.target.value } })}>{KINDS.map(k => <option key={k} value={k}>{pretty(k)}</option>)}</select></Field>
          <Field l="Title *"><input style={input} value={itemForm.v.title} onChange={e => setItemForm({ ...itemForm, v: { ...itemForm.v, title: e.target.value } })} /></Field>
          <div style={{ display: "flex", gap: 12 }}>
            <div style={{ flex: 1 }}><Field l="Issuer"><input style={input} value={itemForm.v.issuer} onChange={e => setItemForm({ ...itemForm, v: { ...itemForm.v, issuer: e.target.value } })} /></Field></div>
            <div style={{ flex: 1 }}><Field l="Reference number"><input style={input} value={itemForm.v.referenceNo} onChange={e => setItemForm({ ...itemForm, v: { ...itemForm.v, referenceNo: e.target.value } })} /></Field></div>
          </div>
          <Field l={itemForm.v.kind === "CERTIFICATE" || itemForm.v.kind === "INSURANCE" ? "Valid to *" : "Valid to (if it expires)"}><input type="date" style={input} value={itemForm.v.validTo} onChange={e => setItemForm({ ...itemForm, v: { ...itemForm.v, validTo: e.target.value } })} /></Field>
          <label style={{ fontSize: 13, display: "flex", gap: 6, marginBottom: 8 }}><input type="checkbox" checked={itemForm.v.required} onChange={e => setItemForm({ ...itemForm, v: { ...itemForm.v, required: e.target.checked } })} /> Required for approval</label>
          {itemForm.v.id && <p style={{ fontSize: 12, color: "#92400E", marginTop: 0 }}>Saving sends a verified item back to pending so it is checked again.</p>}
          <div style={{ display: "flex", justifyContent: "flex-end", gap: 8 }}><button style={ghostBtn} onClick={() => setItemForm(null)}>Cancel</button>
            <button style={primaryBtn} disabled={saveItem.isPending || !itemForm.v.title.trim() || ((itemForm.v.kind === "CERTIFICATE" || itemForm.v.kind === "INSURANCE") && !itemForm.v.validTo)} onClick={() => saveItem.mutate(itemForm)}>Save</button></div>
        </Modal>
      )}
      {rejectItem && (
        <Modal title={`Reject — ${rejectItem.item.title}`} onClose={() => setRejectItem(null)}>
          <Err m={apiError} />
          <Field l={`Reason * (at least ${MIN} characters)`}><textarea style={{ ...input, minHeight: 70 }} value={rejectItem.a} onChange={e => setRejectItem({ ...rejectItem, a: e.target.value })} /></Field>
          <div style={{ display: "flex", justifyContent: "flex-end", gap: 8 }}><button style={ghostBtn} onClick={() => setRejectItem(null)}>Cancel</button><button style={primaryBtn} disabled={reject.isPending || rejectItem.a.trim().length < MIN} onClick={() => reject.mutate(rejectItem)}>Reject</button></div>
        </Modal>
      )}
      {act && (
        <Modal title={`${titles[act.kind]} — ${act.t.name}`} onClose={() => setAct(null)}>
          <Err m={apiError} />
          {act.kind === "review" && <Field l="Risk rating after review"><select style={input} value={act.risk} onChange={e => setAct({ ...act, risk: e.target.value })}>{["LOW", "MEDIUM", "HIGH"].map(x => <option key={x} value={x}>{pretty(x)}</option>)}</select></Field>}
          <Field l={`${act.kind === "review" ? "Review outcome" : "Reason"} * (at least ${MIN} characters)`}><textarea style={{ ...input, minHeight: 70 }} value={act.a} onChange={e => setAct({ ...act, a: e.target.value })} /></Field>
          <div style={{ display: "flex", justifyContent: "flex-end", gap: 8 }}><button style={ghostBtn} onClick={() => setAct(null)}>Cancel</button><button style={primaryBtn} disabled={run.isPending || act.a.trim().length < MIN} onClick={() => run.mutate(act)}>{titles[act.kind]}</button></div>
        </Modal>
      )}
    </div>
  )
}
