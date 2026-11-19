// src/pages/legalcompliance/EthicsTab.tsx
//
// Conflict of interest and ethics. Endpoints (/api/v1/legalcompliance/ethics):
//   GET /summary · /declarations (status) · /declarations/{id} · POST /declarations · PUT /declarations/{id} · POST /declarations/{id}/confirm|withdraw|review|decide
//   GET /gifts (status) · POST /gifts · POST /gifts/{id}/approve|decline
// Anyone can declare and record gifts as themselves, and sees only their own entries. People with the ethics review permission see everyone's, review declarations and
// approve gifts, but never their own. Declarations are personal: counts in the executive overview carry no names.
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"
import { input, primaryBtn, ghostBtn, unwrap, one, fmtDate, errMsg } from "./lcHelpers"
import { Modal, Field, Err, Badge } from "./LcUi"
import { ChevronDown, ChevronUp, Plus } from "lucide-react"

const base = "/api/v1/legalcompliance/ethics"
const pretty = (s: string) => s.replace(/_/g, " ").toLowerCase()
const MIN = 15
const fmtR = (n: number) => `R ${Number(n).toLocaleString("en-ZA", { minimumFractionDigits: 2 })}`
// Mirrors EthicsGovernanceRules.DEFAULT_GIFT_THRESHOLD on the server, which is what actually decides; this only warns the person in advance.
const DEFAULT_THRESHOLD = 500
const today = () => new Date().toISOString().slice(0, 10)
const small = { ...ghostBtn, padding: "4px 10px", fontSize: 12 }
const DTYPES = ["OUTSIDE_EMPLOYMENT", "DIRECTORSHIP", "FINANCIAL_INTEREST", "RELATED_PARTY", "FAMILY_RELATIONSHIP", "OTHER"]
const DSTATUS: Record<string, { label: string; color: string; bg: string }> = {
  DECLARED: { label: "Declared", color: "#475569", bg: "#F1F5F9" }, UNDER_REVIEW: { label: "Under review", color: "#1E40AF", bg: "#DBEAFE" }, CLEARED: { label: "Cleared", color: "#166534", bg: "#DCFCE7" },
  MITIGATED: { label: "Mitigated", color: "#92400E", bg: "#FEF3C7" }, NOT_PERMITTED: { label: "Not permitted", color: "#991B1B", bg: "#FEE2E2" }, WITHDRAWN: { label: "Withdrawn", color: "#475569", bg: "#E2E8F0" },
}
const GSTATUS: Record<string, { label: string; color: string; bg: string }> = {
  RECORDED: { label: "Recorded", color: "#475569", bg: "#F1F5F9" }, PENDING: { label: "Awaiting approval", color: "#92400E", bg: "#FEF3C7" }, APPROVED: { label: "Approved", color: "#166534", bg: "#DCFCE7" }, DECLINED: { label: "Declined", color: "#991B1B", bg: "#FEE2E2" },
}

interface Decl { id: string; reference: string; declarantName: string; declarationType: string; counterparty: string; description: string; estimatedValue: number | null; declaredOn: string; status: string; reviewerName: string | null; reviewedOn: string | null; decisionNote: string | null; mitigation: string | null; reviewMonths: number; nextReviewDue: string | null; reviewOverdue: boolean; mine: boolean }
interface Gift { id: string; reference: string; direction: string; declarantName: string; counterparty: string; description: string; occasion: string | null; entryDate: string; valueZar: number; thresholdZar: number; status: string; decidedByName: string | null; decidedOn: string | null; decisionNote: string | null; mine: boolean }
interface Summary { awaitingReview: number; inForce: number; reviewOverdue: number; giftsPending: number; notPermitted: number }

const blankD = { id: "", declarationType: "DIRECTORSHIP", counterparty: "", description: "", estimatedValue: "", reviewMonths: "12" }
const blankG = { direction: "RECEIVED", counterparty: "", description: "", occasion: "", entryDate: today(), valueZar: "" }

export default function EthicsTab() {
  const qc = useQueryClient()
  const hasReview = usePermission("LEGALCOMPLIANCE_ETHICS_REVIEW")
  const hasAdmin = usePermission("LEGALCOMPLIANCE_ADMIN")
  const canReview = hasReview || hasAdmin

  const [view, setView] = useState<"declarations" | "gifts">("declarations")
  const [expanded, setExpanded] = useState<string | null>(null)
  const [dform, setDform] = useState<typeof blankD | null>(null)
  const [gform, setGform] = useState<typeof blankG | null>(null)
  const [decide, setDecide] = useState<{ d: Decl; outcome: string; note: string; mitigation: string } | null>(null)
  const [reason, setReason] = useState<{ kind: "withdraw-d" | "decline-g"; id: string; a: string } | null>(null)
  const [apiError, setApiError] = useState("")
  const [notice, setNotice] = useState("")

  const { data: summary } = useQuery<Summary>({ queryKey: ["lc-eth", "summary"], queryFn: async () => one(await apiClient.get(`${base}/summary`)) })
  const { data: decls = [], isLoading: ld } = useQuery<Decl[]>({ queryKey: ["lc-eth", "decls"], queryFn: async () => unwrap(await apiClient.get(`${base}/declarations`)) })
  const { data: gifts = [], isLoading: lg } = useQuery<Gift[]>({ queryKey: ["lc-eth", "gifts"], queryFn: async () => unwrap(await apiClient.get(`${base}/gifts`)) })

  const refresh = () => { qc.invalidateQueries({ queryKey: ["lc-eth"] }); setApiError("") }
  const fail = (m: string) => (e: unknown) => setApiError(errMsg(e, m))
  const ok = (msg: string, after?: () => void) => () => { refresh(); after?.(); setNotice(msg) }

  const saveD = useMutation({
    mutationFn: (f: typeof blankD) => { const b = { declarationType: f.declarationType, counterparty: f.counterparty.trim(), description: f.description.trim(), estimatedValue: f.estimatedValue ? +f.estimatedValue : null, reviewMonths: +f.reviewMonths || 12 }; return f.id ? apiClient.put(`${base}/declarations/${f.id}`, b) : apiClient.post(`${base}/declarations`, b) },
    onSuccess: ok("Declaration saved", () => setDform(null)), onError: fail("Failed to save declaration"),
  })
  const act = useMutation({ mutationFn: ({ id, kind }: { id: string; kind: "confirm" | "review" }) => apiClient.post(`${base}/declarations/${id}/${kind}`), onSuccess: ok("Updated"), onError: fail("Action failed") })
  const doDecide = useMutation({ mutationFn: (x: NonNullable<typeof decide>) => apiClient.post(`${base}/declarations/${x.d.id}/decide`, { outcome: x.outcome, note: x.note.trim(), mitigation: x.outcome === "MITIGATED" ? x.mitigation.trim() : null }), onSuccess: ok("Decision recorded", () => setDecide(null)), onError: fail("Failed to record decision") })
  const saveG = useMutation({ mutationFn: (f: typeof blankG) => apiClient.post(`${base}/gifts`, { direction: f.direction, counterparty: f.counterparty.trim(), description: f.description.trim(), occasion: f.occasion.trim() || null, entryDate: f.entryDate, valueZar: +f.valueZar }), onSuccess: ok("Entry recorded", () => setGform(null)), onError: fail("Failed to record entry") })
  const approveG = useMutation({ mutationFn: (id: string) => apiClient.post(`${base}/gifts/${id}/approve`, {}), onSuccess: ok("Approved"), onError: fail("Cannot approve") })
  const doReason = useMutation({
    mutationFn: (x: NonNullable<typeof reason>) => x.kind === "withdraw-d" ? apiClient.post(`${base}/declarations/${x.id}/withdraw`, { reason: x.a.trim() }) : apiClient.post(`${base}/gifts/${x.id}/decline`, { note: x.a.trim() }),
    onSuccess: ok("Saved", () => setReason(null)), onError: fail("Action failed"),
  })

  const card = (label: string, v: number | undefined, color = "#0F172A") => (
    <div style={{ flex: 1, minWidth: 120, background: "#fff", border: "1px solid #E2E8F0", borderRadius: 12, padding: "12px 16px" }}>
      <div style={{ fontSize: 12, color: "#64748B", fontWeight: 600 }}>{label}</div><div style={{ fontSize: 24, fontWeight: 800, color }}>{v ?? "—"}</div>
    </div>
  )
  const bad = (n?: number) => (n ? "#991B1B" : undefined)
  const modalOpen = dform || gform || decide || reason
  const overThreshold = gform && gform.valueZar !== "" && +gform.valueZar > DEFAULT_THRESHOLD

  return (
    <div>
      <div style={{ display: "flex", gap: 12, flexWrap: "wrap", marginBottom: 16 }}>
        {card("Awaiting review", summary?.awaitingReview, "#92400E")}{card("Cleared or mitigated", summary?.inForce, "#166534")}{card("Review overdue", summary?.reviewOverdue, bad(summary?.reviewOverdue))}
        {card("Gifts awaiting approval", summary?.giftsPending, "#92400E")}{card("Not permitted", summary?.notPermitted)}
      </div>
      <div style={{ display: "flex", gap: 12, alignItems: "center", marginBottom: 12, flexWrap: "wrap" }}>
        <button style={primaryBtn} onClick={() => { setApiError(""); if (view === "declarations") setDform({ ...blankD }); else setGform({ ...blankG }) }}><Plus size={14} style={{ verticalAlign: -2 }} /> {view === "declarations" ? "Declare an interest" : "Record a gift or hospitality"}</button>
        <button style={view === "declarations" ? primaryBtn : ghostBtn} onClick={() => setView("declarations")}>Declarations</button>
        <button style={view === "gifts" ? primaryBtn : ghostBtn} onClick={() => setView("gifts")}>Gifts &amp; hospitality</button>
        {!canReview && <span style={{ fontSize: 12, color: "#64748B" }}>You can see only your own entries.</span>}
      </div>
      {notice && <div style={{ background: "#ECFDF5", border: "1px solid #A7F3D0", color: "#065F46", borderRadius: 8, padding: "8px 12px", fontSize: 13, marginBottom: 12 }}>{notice}</div>}
      {apiError && !modalOpen && <Err m={apiError} />}

      {view === "declarations" ? (ld ? <p style={{ color: "#64748B" }}>Loading…</p> : decls.length === 0 ? <p style={{ color: "#64748B", fontSize: 14 }}>No declarations.</p> : decls.map(d => {
        const open = expanded === d.id
        const st = DSTATUS[d.status]
        return (
          <div key={d.id} style={{ border: `1px solid ${d.reviewOverdue ? "#FECACA" : "#E2E8F0"}`, borderRadius: 10, background: "#fff", marginBottom: 8 }}>
            <button onClick={() => { setExpanded(open ? null : d.id); setApiError("") }} style={{ all: "unset", cursor: "pointer", display: "flex", gap: 12, alignItems: "center", padding: "12px 16px", width: "calc(100% - 32px)" }}>
              <div style={{ flex: 1, minWidth: 0 }}><div style={{ fontWeight: 700, fontSize: 14, color: "#0F172A" }}>{d.reference} — {pretty(d.declarationType)}: {d.counterparty}</div>
                <div style={{ fontSize: 12, color: "#64748B" }}>{d.declarantName}{d.mine ? " (you)" : ""} · declared {fmtDate(d.declaredOn)}{d.nextReviewDue ? ` · review ${fmtDate(d.nextReviewDue)}` : ""}</div></div>
              {d.reviewOverdue && <Badge text="Review overdue" color="#991B1B" bg="#FEE2E2" />}<Badge text={st.label} color={st.color} bg={st.bg} />{open ? <ChevronUp size={16} /> : <ChevronDown size={16} />}
            </button>
            {open && (
              <div style={{ padding: "0 16px 14px", fontSize: 13, color: "#475569" }}>
                <p style={{ marginTop: 0, whiteSpace: "pre-wrap" }}>{d.description}</p>
                {d.estimatedValue !== null && <p style={{ fontSize: 12 }}>Estimated value {fmtR(d.estimatedValue)}</p>}
                {d.reviewerName && <p style={{ fontSize: 12 }}>Reviewer {d.reviewerName}{d.reviewedOn ? ` on ${fmtDate(d.reviewedOn)}` : ""}</p>}
                {d.decisionNote && <p style={{ fontSize: 12, whiteSpace: "pre-wrap" }}>{d.decisionNote}</p>}
                {d.mitigation && <p style={{ fontSize: 12 }}><b>Mitigation:</b> {d.mitigation}</p>}
                {apiError && <Err m={apiError} />}
                <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
                  {d.mine && d.status === "DECLARED" && <button style={small} onClick={() => { setApiError(""); setDform({ id: d.id, declarationType: d.declarationType, counterparty: d.counterparty, description: d.description, estimatedValue: d.estimatedValue === null ? "" : String(d.estimatedValue), reviewMonths: String(d.reviewMonths) }) }}>Amend</button>}
                  {d.mine && (d.status === "CLEARED" || d.status === "MITIGATED") && <button style={small} disabled={act.isPending} onClick={() => act.mutate({ id: d.id, kind: "confirm" })}>Confirm nothing has changed</button>}
                  {d.mine && ["DECLARED", "UNDER_REVIEW", "CLEARED", "MITIGATED"].includes(d.status) && <button style={small} onClick={() => { setApiError(""); setReason({ kind: "withdraw-d", id: d.id, a: "" }) }}>Withdraw</button>}
                  {canReview && !d.mine && d.status === "DECLARED" && <button style={small} disabled={act.isPending} onClick={() => act.mutate({ id: d.id, kind: "review" })}>Start review</button>}
                  {canReview && !d.mine && (d.status === "DECLARED" || d.status === "UNDER_REVIEW") && <button style={primaryBtn} onClick={() => { setApiError(""); setDecide({ d, outcome: "CLEARED", note: "", mitigation: "" }) }}>Decide</button>}
                </div>
                {canReview && d.mine && (d.status === "DECLARED" || d.status === "UNDER_REVIEW") && <p style={{ fontSize: 12, color: "#64748B" }}>You cannot review your own declaration; another reviewer must.</p>}
              </div>
            )}
          </div>
        )
      })) : (lg ? <p style={{ color: "#64748B" }}>Loading…</p> : gifts.length === 0 ? <p style={{ color: "#64748B", fontSize: 14 }}>No gifts or hospitality recorded.</p> : gifts.map(g => {
        const st = GSTATUS[g.status]
        return (
          <div key={g.id} style={{ border: "1px solid #E2E8F0", borderRadius: 10, background: "#fff", padding: "12px 16px", marginBottom: 8 }}>
            <div style={{ display: "flex", gap: 12, alignItems: "center", flexWrap: "wrap" }}>
              <div style={{ flex: 1, minWidth: 200 }}><div style={{ fontWeight: 700, fontSize: 14, color: "#0F172A" }}>{g.reference} — {pretty(g.direction)} {g.direction === "RECEIVED" ? "from" : "to"} {g.counterparty}</div>
                <div style={{ fontSize: 12, color: "#64748B" }}>{g.declarantName}{g.mine ? " (you)" : ""} · {fmtDate(g.entryDate)} · {fmtR(g.valueZar)} (approval needed above {fmtR(g.thresholdZar)}){g.occasion ? ` · ${g.occasion}` : ""}</div></div>
              <Badge text={st.label} color={st.color} bg={st.bg} />
            </div>
            <p style={{ fontSize: 13, color: "#475569", margin: "6px 0" }}>{g.description}</p>
            {g.decidedByName && <p style={{ fontSize: 12, color: "#64748B", margin: "0 0 6px" }}>{pretty(g.status)} by {g.decidedByName} on {fmtDate(g.decidedOn)}{g.decisionNote ? `: ${g.decisionNote}` : ""}</p>}
            {canReview && !g.mine && g.status === "PENDING" && (
              <div style={{ display: "flex", gap: 8 }}>
                <button style={primaryBtn} disabled={approveG.isPending} onClick={() => approveG.mutate(g.id)}>Approve</button>
                <button style={ghostBtn} onClick={() => { setApiError(""); setReason({ kind: "decline-g", id: g.id, a: "" }) }}>Decline</button>
              </div>
            )}
            {g.mine && g.status === "PENDING" && <p style={{ fontSize: 12, color: "#64748B", margin: 0 }}>Waiting for someone else to approve it; you cannot approve your own entry.</p>}
          </div>
        )
      }))}

      {dform && (
        <Modal title={dform.id ? "Amend declaration" : "Declare an interest"} onClose={() => setDform(null)}>
          <Err m={apiError} />
          <Field l="Kind *"><select style={input} value={dform.declarationType} onChange={e => setDform({ ...dform, declarationType: e.target.value })}>{DTYPES.map(x => <option key={x} value={x}>{pretty(x)}</option>)}</select></Field>
          <Field l="Who or what is the interest in? *"><input style={input} value={dform.counterparty} onChange={e => setDform({ ...dform, counterparty: e.target.value })} /></Field>
          <Field l={`Describe it * (at least ${MIN} characters)`}><textarea style={{ ...input, minHeight: 80 }} value={dform.description} onChange={e => setDform({ ...dform, description: e.target.value })} /></Field>
          <div style={{ display: "flex", gap: 12 }}>
            <div style={{ flex: 1 }}><Field l="Estimated value (R)"><input type="number" min={0} style={input} value={dform.estimatedValue} onChange={e => setDform({ ...dform, estimatedValue: e.target.value })} /></Field></div>
            <div style={{ flex: 1 }}><Field l="Re-review every (months)"><input type="number" min={1} max={36} style={input} value={dform.reviewMonths} onChange={e => setDform({ ...dform, reviewMonths: e.target.value })} /></Field></div>
          </div>
          <p style={{ fontSize: 12, color: "#64748B" }}>This is recorded in your name. Someone else reviews it; you can amend it only until review starts.</p>
          <div style={{ display: "flex", justifyContent: "flex-end", gap: 8 }}><button style={ghostBtn} onClick={() => setDform(null)}>Cancel</button><button style={primaryBtn} disabled={saveD.isPending || !dform.counterparty.trim() || dform.description.trim().length < MIN} onClick={() => saveD.mutate(dform)}>Save</button></div>
        </Modal>
      )}
      {gform && (
        <Modal title="Record a gift or hospitality" onClose={() => setGform(null)}>
          <Err m={apiError} />
          <div style={{ display: "flex", gap: 12 }}>
            <div style={{ flex: 1 }}><Field l="Direction *"><select style={input} value={gform.direction} onChange={e => setGform({ ...gform, direction: e.target.value })}><option value="RECEIVED">Received</option><option value="GIVEN">Given</option></select></Field></div>
            <div style={{ flex: 2 }}><Field l={gform.direction === "RECEIVED" ? "From *" : "To *"}><input style={input} value={gform.counterparty} onChange={e => setGform({ ...gform, counterparty: e.target.value })} /></Field></div>
          </div>
          <Field l="What was it? * (at least 10 characters)"><textarea style={{ ...input, minHeight: 60 }} value={gform.description} onChange={e => setGform({ ...gform, description: e.target.value })} /></Field>
          <div style={{ display: "flex", gap: 12 }}>
            <div style={{ flex: 1 }}><Field l="Date *"><input type="date" max={today()} style={input} value={gform.entryDate} onChange={e => setGform({ ...gform, entryDate: e.target.value })} /></Field></div>
            <div style={{ flex: 1 }}><Field l="Value (R) *"><input type="number" min={0} style={input} value={gform.valueZar} onChange={e => setGform({ ...gform, valueZar: e.target.value })} /></Field></div>
            <div style={{ flex: 1 }}><Field l="Occasion"><input style={input} value={gform.occasion} onChange={e => setGform({ ...gform, occasion: e.target.value })} /></Field></div>
          </div>
          {overThreshold && <p style={{ fontSize: 12, color: "#92400E" }}>Above R 500 (the default threshold): it will probably need approval from someone other than you.</p>}
          <div style={{ display: "flex", justifyContent: "flex-end", gap: 8 }}><button style={ghostBtn} onClick={() => setGform(null)}>Cancel</button><button style={primaryBtn} disabled={saveG.isPending || !gform.counterparty.trim() || gform.description.trim().length < 10 || gform.valueZar === "" || !gform.entryDate} onClick={() => saveG.mutate(gform)}>Record</button></div>
        </Modal>
      )}
      {decide && (
        <Modal title={`Decide — ${decide.d.reference}`} onClose={() => setDecide(null)}>
          <Err m={apiError} />
          <p style={{ fontSize: 13, color: "#475569", marginTop: 0 }}>{decide.d.declarantName}: {pretty(decide.d.declarationType)} — {decide.d.counterparty}</p>
          <Field l="Outcome *"><select style={input} value={decide.outcome} onChange={e => setDecide({ ...decide, outcome: e.target.value })}><option value="CLEARED">Cleared — no conflict</option><option value="MITIGATED">Mitigated — allowed with conditions</option><option value="NOT_PERMITTED">Not permitted</option></select></Field>
          <Field l={`Reasoning * (at least ${MIN} characters)`}><textarea style={{ ...input, minHeight: 70 }} value={decide.note} onChange={e => setDecide({ ...decide, note: e.target.value })} /></Field>
          {decide.outcome === "MITIGATED" && <Field l={`Mitigation * (for example recusal) — at least ${MIN} characters`}><textarea style={{ ...input, minHeight: 60 }} value={decide.mitigation} onChange={e => setDecide({ ...decide, mitigation: e.target.value })} /></Field>}
          <div style={{ display: "flex", justifyContent: "flex-end", gap: 8 }}><button style={ghostBtn} onClick={() => setDecide(null)}>Cancel</button>
            <button style={primaryBtn} disabled={doDecide.isPending || decide.note.trim().length < MIN || (decide.outcome === "MITIGATED" && decide.mitigation.trim().length < MIN)} onClick={() => doDecide.mutate(decide)}>Record decision</button></div>
        </Modal>
      )}
      {reason && (
        <Modal title={reason.kind === "withdraw-d" ? "Withdraw declaration" : "Decline gift or hospitality"} onClose={() => setReason(null)}>
          <Err m={apiError} />
          <Field l={reason.kind === "withdraw-d" ? `Why? * (at least ${MIN} characters)` : `What happens to it — returned, donated or paid back? * (at least ${MIN} characters)`}><textarea style={{ ...input, minHeight: 70 }} value={reason.a} onChange={e => setReason({ ...reason, a: e.target.value })} /></Field>
          <div style={{ display: "flex", justifyContent: "flex-end", gap: 8 }}><button style={ghostBtn} onClick={() => setReason(null)}>Back</button><button style={primaryBtn} disabled={doReason.isPending || reason.a.trim().length < MIN} onClick={() => doReason.mutate(reason)}>Confirm</button></div>
        </Modal>
      )}
    </div>
  )
}
