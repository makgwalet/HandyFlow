// src/pages/legalcompliance/AttestationsTab.tsx
//
// Two views in one tab. Everyone sees "My attestations" (read the pinned policy text and accept or decline); managers also see campaigns.
// Endpoints (/api/v1/legalcompliance/attestations): GET /mine · POST /{id}/respond · GET /campaigns · GET /campaigns/{id} · POST /campaigns · POST /campaigns/{id}/close
//
// A campaign goes to all active users (optionally one department) as they were when it launched. Declining needs a comment. A person can change their
// answer until the campaign closes. Each answer is stored with the hash of the exact text they were shown.
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"
import { input, primaryBtn, ghostBtn, unwrap, one, fmtDate, fmtDateTime, errMsg } from "./lcHelpers"
import { Modal, Field, Err, Badge } from "./LcUi"
import { ChevronDown, ChevronUp, Plus } from "lucide-react"

interface Mine {
  id: string; campaignTitle: string; instructions: string | null; policyTitle: string; versionNumber: number; content: string; dueDate: string; overdue: boolean; campaignOpen: boolean
  status: string; respondedAt: string | null; comment: string | null
}
interface Campaign {
  id: string; reference: string; policyTitle: string | null; versionNumber: number; title: string; dueDate: string; overdue: boolean; department: string | null; status: string
  launchedByName: string | null; closeReason: string | null; total: number; accepted: number; declined: number; pending: number; percentComplete: number
}
interface Row { id: string; userName: string; userEmail: string | null; status: string; respondedAt: string | null; comment: string | null }
interface PolicyOpt { id: string; reference: string; title: string; publishedVersion: number | null }
interface PolicyDetail { versions: { id: string; versionNumber: number; status: string }[] }

const ASTATUS: Record<string, { label: string; color: string; bg: string }> = {
  PENDING: { label: "Pending", color: "#92400E", bg: "#FEF3C7" }, ACCEPTED: { label: "Accepted", color: "#166534", bg: "#DCFCE7" }, DECLINED: { label: "Declined", color: "#991B1B", bg: "#FEE2E2" },
}
const base = "/api/v1/legalcompliance/attestations"

export default function AttestationsTab() {
  const qc = useQueryClient()
  const admin = usePermission("LEGALCOMPLIANCE_ADMIN")
  const canManage = usePermission("LEGALCOMPLIANCE_ATTESTATION_MANAGE") || admin

  const [view, setView] = useState<"mine" | "campaigns">("mine")
  const [openMine, setOpenMine] = useState<string | null>(null)
  const [comment, setComment] = useState("")
  const [openCampaign, setOpenCampaign] = useState<string | null>(null)
  const [launch, setLaunch] = useState<{ policyId: string; title: string; instructions: string; dueDate: string; department: string } | null>(null)
  const [closing, setClosing] = useState<{ c: Campaign; reason: string } | null>(null)
  const [apiError, setApiError] = useState("")
  const [notice, setNotice] = useState("")

  const { data: mine = [], isLoading } = useQuery<Mine[]>({ queryKey: ["lc-attest", "mine"], queryFn: async () => unwrap(await apiClient.get(`${base}/mine`)) })
  const { data: campaigns = [] } = useQuery<Campaign[]>({ queryKey: ["lc-campaigns", "list"], enabled: canManage && view === "campaigns", queryFn: async () => unwrap(await apiClient.get(`${base}/campaigns`)) })
  const { data: rows = [] } = useQuery<Row[]>({ queryKey: ["lc-campaigns", "rows", openCampaign], enabled: !!openCampaign, queryFn: async () => (one(await apiClient.get(`${base}/campaigns/${openCampaign}`)) as { attestations: Row[] }).attestations })
  const { data: policies = [] } = useQuery<PolicyOpt[]>({ queryKey: ["lc-policies", "published"], enabled: !!launch, queryFn: async () => (unwrap(await apiClient.get("/api/v1/legalcompliance/policies")) as PolicyOpt[]).filter(p => p.publishedVersion) })
  const { data: pdetail } = useQuery<PolicyDetail>({ queryKey: ["lc-policies", "detail", launch?.policyId], enabled: !!launch?.policyId, queryFn: async () => one(await apiClient.get(`/api/v1/legalcompliance/policies/${launch!.policyId}`)) })
  const publishedId = pdetail?.versions.find(v => v.status === "PUBLISHED")?.id

  const refresh = () => { qc.invalidateQueries({ queryKey: ["lc-attest"] }); qc.invalidateQueries({ queryKey: ["lc-campaigns"] }); qc.invalidateQueries({ queryKey: ["lc-policies"] }); setApiError("") }
  const respond = useMutation({
    mutationFn: ({ id, accept }: { id: string; accept: boolean }) => apiClient.post(`${base}/${id}/respond`, { accept, comment: comment.trim() || null }),
    onSuccess: () => { refresh(); setComment(""); setNotice("Your response was recorded") }, onError: (e: any) => setApiError(errMsg(e, "Failed to record response")),
  })
  const doLaunch = useMutation({
    mutationFn: (l: NonNullable<typeof launch>) => apiClient.post(`${base}/campaigns`, { versionId: publishedId, title: l.title.trim(), instructions: l.instructions.trim() || null, dueDate: l.dueDate, department: l.department.trim() || null }),
    onSuccess: () => { refresh(); setLaunch(null); setNotice("Campaign launched") }, onError: (e: any) => setApiError(errMsg(e, "Failed to launch campaign")),
  })
  const doClose = useMutation({
    mutationFn: (c: { id: string; reason: string }) => apiClient.post(`${base}/campaigns/${c.id}/close`, { reason: c.reason.trim() }),
    onSuccess: () => { refresh(); setClosing(null); setNotice("Campaign closed") }, onError: (e: any) => setApiError(errMsg(e, "Failed to close campaign")),
  })

  const pending = mine.filter(m => m.status === "PENDING" && m.campaignOpen).length
  const tab = (id: "mine" | "campaigns", label: string) => (
    <button onClick={() => { setView(id); setApiError("") }} style={{ ...(view === id ? primaryBtn : ghostBtn), padding: "6px 14px" }}>{label}</button>
  )

  return (
    <div>
      <div style={{ display: "flex", gap: 8, marginBottom: 14, alignItems: "center" }}>
        {tab("mine", `My attestations${pending ? ` (${pending})` : ""}`)}{canManage && tab("campaigns", "Campaigns")}
        {canManage && view === "campaigns" && <button style={{ ...primaryBtn, marginLeft: "auto" }} onClick={() => { setApiError(""); setLaunch({ policyId: "", title: "", instructions: "", dueDate: "", department: "" }) }}><Plus size={14} style={{ verticalAlign: -2 }} /> Launch campaign</button>}
      </div>
      {notice && <div style={{ background: "#ECFDF5", border: "1px solid #A7F3D0", color: "#065F46", borderRadius: 8, padding: "8px 12px", fontSize: 13, marginBottom: 12 }}>{notice}</div>}
      {apiError && !launch && !closing && <Err m={apiError} />}

      {view === "mine" && (isLoading ? <p style={{ color: "#64748B" }}>Loading…</p> : mine.length === 0 ? <p style={{ color: "#64748B", fontSize: 14 }}>Nothing has been sent to you.</p> : mine.map(m => {
        const open = openMine === m.id, st = ASTATUS[m.status]
        return (
          <div key={m.id} style={{ border: `1px solid ${m.overdue ? "#FECACA" : "#E2E8F0"}`, borderRadius: 10, background: "#fff", marginBottom: 8 }}>
            <button onClick={() => { setOpenMine(open ? null : m.id); setComment(m.comment ?? ""); setApiError("") }} style={{ all: "unset", cursor: "pointer", display: "flex", gap: 12, alignItems: "center", padding: "12px 16px", width: "calc(100% - 32px)" }}>
              <div style={{ flex: 1, minWidth: 0 }}>
                <div style={{ fontWeight: 700, fontSize: 14, color: "#0F172A" }}>{m.campaignTitle}</div>
                <div style={{ fontSize: 12, color: "#64748B" }}>{m.policyTitle} v{m.versionNumber} · due {fmtDate(m.dueDate)}{m.overdue ? " (overdue)" : ""}{!m.campaignOpen ? " · campaign closed" : ""}</div>
              </div>
              <Badge text={st.label} color={st.color} bg={st.bg} />{open ? <ChevronUp size={16} /> : <ChevronDown size={16} />}
            </button>
            {open && (
              <div style={{ padding: "0 16px 14px", fontSize: 13, color: "#475569" }}>
                {m.instructions && <p style={{ marginTop: 0 }}><b>Instructions:</b> {m.instructions}</p>}
                <div style={{ whiteSpace: "pre-wrap", border: "1px solid #E2E8F0", borderRadius: 8, padding: 12, background: "#F8FAFC", maxHeight: 320, overflow: "auto", color: "#0F172A" }}>{m.content}</div>
                {m.respondedAt && <p style={{ fontSize: 12 }}>You {m.status === "ACCEPTED" ? "accepted" : "declined"} this on {fmtDateTime(m.respondedAt)}.</p>}
                {m.campaignOpen && (
                  <>
                    <Field l="Comment (required if you decline, at least 15 characters)"><textarea style={{ ...input, minHeight: 60 }} value={comment} onChange={e => setComment(e.target.value)} /></Field>
                    <div style={{ display: "flex", gap: 8 }}>
                      <button style={primaryBtn} disabled={respond.isPending} onClick={() => respond.mutate({ id: m.id, accept: true })}>I have read and accept this</button>
                      <button style={ghostBtn} disabled={respond.isPending || comment.trim().length < 15} onClick={() => respond.mutate({ id: m.id, accept: false })}>I cannot accept</button>
                    </div>
                  </>
                )}
              </div>
            )}
          </div>
        )
      }))}

      {view === "campaigns" && (campaigns.length === 0 ? <p style={{ color: "#64748B", fontSize: 14 }}>No campaigns yet. Launch one on a published policy.</p> : campaigns.map(c => {
        const open = openCampaign === c.id
        return (
          <div key={c.id} style={{ border: `1px solid ${c.overdue ? "#FECACA" : "#E2E8F0"}`, borderRadius: 10, background: "#fff", marginBottom: 8 }}>
            <button onClick={() => setOpenCampaign(open ? null : c.id)} style={{ all: "unset", cursor: "pointer", display: "flex", gap: 12, alignItems: "center", padding: "12px 16px", width: "calc(100% - 32px)" }}>
              <div style={{ flex: 1, minWidth: 0 }}>
                <div style={{ fontWeight: 700, fontSize: 14, color: "#0F172A" }}>{c.reference} — {c.title}</div>
                <div style={{ fontSize: 12, color: "#64748B" }}>{c.policyTitle} v{c.versionNumber} · due {fmtDate(c.dueDate)}{c.overdue ? " (overdue)" : ""}{c.department ? ` · ${c.department}` : ""} · {c.accepted} accepted, {c.declined} declined, {c.pending} pending of {c.total}</div>
              </div>
              <div style={{ width: 90, height: 8, background: "#E2E8F0", borderRadius: 4 }} aria-label={`${c.percentComplete}% responded`}><div style={{ width: `${c.percentComplete}%`, height: 8, background: "#16A34A", borderRadius: 4 }} /></div>
              <Badge text={c.status === "OPEN" ? "Open" : "Closed"} color={c.status === "OPEN" ? "#1D4ED8" : "#475569"} bg={c.status === "OPEN" ? "#DBEAFE" : "#E2E8F0"} />{open ? <ChevronUp size={16} /> : <ChevronDown size={16} />}
            </button>
            {open && (
              <div style={{ padding: "0 16px 14px", fontSize: 13, color: "#475569" }}>
                {c.closeReason && <p style={{ marginTop: 0 }}><b>Closed:</b> {c.closeReason}</p>}
                {c.status === "OPEN" && <button style={{ ...ghostBtn, color: "#991B1B", marginBottom: 8 }} onClick={() => { setApiError(""); setClosing({ c, reason: "" }) }}>Close campaign</button>}
                <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 12 }}>
                  <thead><tr style={{ textAlign: "left", color: "#64748B" }}><th style={{ padding: 4 }}>Person</th><th>Status</th><th>When</th><th>Comment</th></tr></thead>
                  <tbody>{rows.map(r => <tr key={r.id} style={{ borderTop: "1px solid #F1F5F9" }}><td style={{ padding: 4 }}>{r.userName}{r.userEmail ? ` (${r.userEmail})` : ""}</td><td><Badge text={ASTATUS[r.status].label} color={ASTATUS[r.status].color} bg={ASTATUS[r.status].bg} /></td><td>{r.respondedAt ? fmtDateTime(r.respondedAt) : "—"}</td><td>{r.comment ?? ""}</td></tr>)}</tbody>
                </table>
              </div>
            )}
          </div>
        )
      }))}

      {launch && (
        <Modal title="Launch attestation campaign" onClose={() => setLaunch(null)}>
          <Err m={apiError} />
          <Field l="Policy * (published only)"><select style={input} value={launch.policyId} onChange={e => { const p = policies.find(x => x.id === e.target.value); setLaunch({ ...launch, policyId: e.target.value, title: launch.title || (p ? `Acknowledge: ${p.title}` : "") }) }}>
            <option value="">Choose…</option>{policies.map(p => <option key={p.id} value={p.id}>{p.reference} — {p.title} (v{p.publishedVersion})</option>)}</select></Field>
          <Field l="Title *"><input style={input} value={launch.title} onChange={e => setLaunch({ ...launch, title: e.target.value })} /></Field>
          <div style={{ display: "flex", gap: 12 }}>
            <div style={{ flex: 1 }}><Field l="Due date *"><input type="date" style={input} value={launch.dueDate} onChange={e => setLaunch({ ...launch, dueDate: e.target.value })} /></Field></div>
            <div style={{ flex: 1 }}><Field l="Department (blank = everyone)"><input style={input} value={launch.department} onChange={e => setLaunch({ ...launch, department: e.target.value })} /></Field></div>
          </div>
          <Field l="Instructions"><textarea style={{ ...input, minHeight: 60 }} value={launch.instructions} onChange={e => setLaunch({ ...launch, instructions: e.target.value })} /></Field>
          <p style={{ fontSize: 12, color: "#64748B", marginTop: 0 }}>Goes to every active user (or the department) as they are now; people who join later are not added. Each person is notified.</p>
          <div style={{ display: "flex", justifyContent: "flex-end", gap: 8 }}>
            <button style={ghostBtn} onClick={() => setLaunch(null)}>Cancel</button>
            <button style={primaryBtn} disabled={doLaunch.isPending || !publishedId || !launch.title.trim() || !launch.dueDate} onClick={() => doLaunch.mutate(launch)}>Launch</button>
          </div>
        </Modal>
      )}

      {closing && (
        <Modal title={`Close ${closing.c.reference}`} onClose={() => setClosing(null)}>
          <Err m={apiError} />
          <Field l="Reason * (at least 15 characters)"><textarea style={{ ...input, minHeight: 70 }} value={closing.reason} onChange={e => setClosing({ ...closing, reason: e.target.value })} /></Field>
          <div style={{ display: "flex", justifyContent: "flex-end", gap: 8 }}>
            <button style={ghostBtn} onClick={() => setClosing(null)}>Keep open</button>
            <button style={{ ...primaryBtn, background: "#B91C1C" }} disabled={doClose.isPending || closing.reason.trim().length < 15} onClick={() => doClose.mutate({ id: closing.c.id, reason: closing.reason })}>Close campaign</button>
          </div>
        </Modal>
      )}
    </div>
  )
}
