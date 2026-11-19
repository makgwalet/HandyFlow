// src/pages/legalcompliance/FindingsTab.tsx
//
// Findings raised by partial or failed control tests. Endpoints (FindingController, /api/v1/legalcompliance/findings):
//   GET / · GET /summary · POST /{id}/remediation · /resolve · /reject-resolution · /close
//
// A finding is closed only by someone other than the person who marked it resolved, and only against a completed, passing RETEST of the
// same control. The server enforces both; this screen just offers the right actions.
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"
import { input, primaryBtn, ghostBtn, unwrap, one, fmtDate, fmtDateTime, errMsg } from "./lcHelpers"
import { Modal, Field, Err, Badge } from "./LcUi"
import { ChevronDown, ChevronUp } from "lucide-react"

interface Finding {
  id: string; reference: string; sourceId: string; controlId: string | null; controlLabel: string | null; title: string; description: string; severity: string; status: string
  ownerName: string; dueDate: string; overdue: boolean; remediationPlan: string | null; resolvedByName: string | null; resolvedAt: string | null; resolutionNote: string | null
  verifiedByName: string | null; verifiedAt: string | null; verificationNote: string | null; closingTestId: string | null; reopenedCount: number; closedAt: string | null
}
interface Summary { open: number; overdue: number; critical: number; high: number; awaitingVerification: number }
interface Test { id: string; reference: string; controlId: string; testType: string; status: string; result: string | null; reviewedAt: string | null }

const SEV: Record<string, { color: string; bg: string }> = { LOW: { color: "#166534", bg: "#DCFCE7" }, MEDIUM: { color: "#92400E", bg: "#FEF3C7" }, HIGH: { color: "#C2410C", bg: "#FFEDD5" }, CRITICAL: { color: "#991B1B", bg: "#FEE2E2" } }
const STATUS: Record<string, { label: string; color: string; bg: string }> = {
  OPEN: { label: "Open", color: "#991B1B", bg: "#FEE2E2" }, IN_REMEDIATION: { label: "In remediation", color: "#92400E", bg: "#FEF3C7" },
  RESOLVED: { label: "Awaiting verification", color: "#1D4ED8", bg: "#DBEAFE" }, CLOSED: { label: "Closed", color: "#166534", bg: "#DCFCE7" },
}
const base = "/api/v1/legalcompliance/findings"
type Action = "plan" | "resolve" | "reject" | "close"

export default function FindingsTab() {
  const qc = useQueryClient()
  const hasManage = usePermission("LEGALCOMPLIANCE_FINDING_MANAGE")
  const hasVerify = usePermission("LEGALCOMPLIANCE_FINDING_VERIFY")
  const hasAdmin = usePermission("LEGALCOMPLIANCE_ADMIN")
  const canManage = hasManage || hasAdmin
  const canVerify = hasVerify || hasAdmin

  const [includeClosed, setIncludeClosed] = useState(false)
  const [expanded, setExpanded] = useState<string | null>(null)
  const [act, setAct] = useState<{ f: Finding; action: Action } | null>(null)
  const [form, setForm] = useState({ plan: "", ownerName: "", dueDate: "", note: "", retestId: "" })
  const [apiError, setApiError] = useState("")
  const [notice, setNotice] = useState("")

  const { data: summary } = useQuery<Summary>({ queryKey: ["lc-findings", "summary"], queryFn: async () => one(await apiClient.get(`${base}/summary`)) })
  const { data: findings = [], isLoading } = useQuery<Finding[]>({ queryKey: ["lc-findings", "list", includeClosed], queryFn: async () => unwrap(await apiClient.get(base, { params: { includeClosed } })) })
  const { data: tests = [] } = useQuery<Test[]>({
    queryKey: ["lc-tests", "retests", act?.f.controlId], enabled: act?.action === "close" && !!act.f.controlId,
    queryFn: async () => unwrap(await apiClient.get("/api/v1/legalcompliance/control-tests", { params: { controlId: act!.f.controlId, status: "COMPLETED" } })),
  })
  const passingRetests = tests.filter(t => t.testType === "RETEST" && t.result === "PASS" && t.reviewedAt && act && new Date(t.reviewedAt) > new Date(0))

  const refresh = () => { qc.invalidateQueries({ queryKey: ["lc-findings"] }); setApiError("") }
  const call = useMutation({
    mutationFn: ({ id, path, body }: { id: string; path: string; body: any }) => apiClient.post(`${base}/${id}/${path}`, body),
    onSuccess: (r) => { refresh(); setAct(null); setNotice(one(r)?.status === "CLOSED" ? "Finding closed" : "Saved") }, onError: (e: any) => setApiError(errMsg(e, "Action failed")),
  })

  const open = (f: Finding, action: Action) => {
    setApiError(""); setForm({ plan: f.remediationPlan ?? "", ownerName: f.ownerName, dueDate: f.dueDate, note: "", retestId: "" }); setAct({ f, action })
  }
  const submit = () => {
    if (!act) return
    const { f, action } = act
    if (action === "plan") call.mutate({ id: f.id, path: "remediation", body: { plan: form.plan.trim(), ownerName: form.ownerName.trim(), dueDate: form.dueDate } })
    if (action === "resolve") call.mutate({ id: f.id, path: "resolve", body: { note: form.note.trim() } })
    if (action === "reject") call.mutate({ id: f.id, path: "reject-resolution", body: { note: form.note.trim() } })
    if (action === "close") call.mutate({ id: f.id, path: "close", body: { note: form.note.trim(), retestId: form.retestId } })
  }
  const titles: Record<Action, string> = { plan: "Plan remediation", resolve: "Mark resolved", reject: "Send back to remediation", close: "Verify and close" }
  const valid = !act ? false : act.action === "plan" ? form.plan.trim().length >= 15 && !!form.ownerName.trim() && !!form.dueDate
    : act.action === "close" ? form.note.trim().length >= 15 && !!form.retestId : form.note.trim().length >= 15

  const card = (label: string, v: number | undefined, color = "#0F172A") => (
    <div style={{ flex: 1, minWidth: 130, background: "#fff", border: "1px solid #E2E8F0", borderRadius: 12, padding: "12px 16px" }}>
      <div style={{ fontSize: 12, color: "#64748B", fontWeight: 600 }}>{label}</div><div style={{ fontSize: 24, fontWeight: 800, color }}>{v ?? "—"}</div>
    </div>
  )

  return (
    <div>
      <div style={{ display: "flex", gap: 12, flexWrap: "wrap", marginBottom: 16 }}>
        {card("Open findings", summary?.open)}{card("Overdue", summary?.overdue, summary?.overdue ? "#991B1B" : undefined)}{card("Critical", summary?.critical, "#991B1B")}
        {card("High", summary?.high, "#C2410C")}{card("Awaiting verification", summary?.awaitingVerification, "#1D4ED8")}
      </div>
      <label style={{ fontSize: 13, color: "#475569", display: "flex", gap: 6, alignItems: "center", marginBottom: 12 }}><input type="checkbox" checked={includeClosed} onChange={e => setIncludeClosed(e.target.checked)} /> Show closed</label>
      {notice && <div style={{ background: "#ECFDF5", border: "1px solid #A7F3D0", color: "#065F46", borderRadius: 8, padding: "8px 12px", fontSize: 13, marginBottom: 12 }}>{notice}</div>}

      {isLoading ? <p style={{ color: "#64748B" }}>Loading…</p> : findings.length === 0 ? <p style={{ color: "#64748B", fontSize: 14 }}>No findings. They are raised automatically when a control test is partial or fails.</p> : findings.map(f => {
        const isOpen = expanded === f.id
        return (
          <div key={f.id} style={{ border: `1px solid ${f.overdue ? "#FECACA" : "#E2E8F0"}`, borderRadius: 10, background: "#fff", marginBottom: 8 }}>
            <button onClick={() => setExpanded(isOpen ? null : f.id)} style={{ all: "unset", cursor: "pointer", display: "flex", gap: 12, alignItems: "center", padding: "12px 16px", width: "calc(100% - 32px)" }}>
              <div style={{ flex: 1, minWidth: 0 }}>
                <div style={{ fontWeight: 700, fontSize: 14, color: "#0F172A" }}>{f.reference} — {f.title}</div>
                <div style={{ fontSize: 12, color: "#64748B" }}>{f.controlLabel ?? "—"} · owner {f.ownerName} · due {fmtDate(f.dueDate)}{f.overdue ? " (overdue)" : ""}{f.reopenedCount ? ` · sent back ${f.reopenedCount}×` : ""}</div>
              </div>
              <Badge text={f.severity} {...SEV[f.severity]} /><Badge text={STATUS[f.status].label} color={STATUS[f.status].color} bg={STATUS[f.status].bg} />
              {isOpen ? <ChevronUp size={16} /> : <ChevronDown size={16} />}
            </button>
            {isOpen && (
              <div style={{ padding: "0 16px 14px", fontSize: 13, color: "#475569" }}>
                <p style={{ marginTop: 0 }}><b>What the test found:</b> {f.description}</p>
                {f.remediationPlan && <p><b>Remediation plan:</b> {f.remediationPlan}</p>}
                {f.resolvedAt && <p><b>Marked resolved</b> by {f.resolvedByName} on {fmtDateTime(f.resolvedAt)}: {f.resolutionNote}</p>}
                {f.verifiedAt && <p><b>{f.status === "CLOSED" ? "Verified and closed" : "Last verification"}</b> by {f.verifiedByName} on {fmtDateTime(f.verifiedAt)}: {f.verificationNote}</p>}
                <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
                  {canManage && (f.status === "OPEN" || f.status === "IN_REMEDIATION") && <button style={ghostBtn} onClick={() => open(f, "plan")}>{f.status === "OPEN" ? "Plan remediation" : "Edit plan"}</button>}
                  {canManage && f.status === "IN_REMEDIATION" && <button style={primaryBtn} onClick={() => open(f, "resolve")}>Mark resolved</button>}
                  {canVerify && f.status === "RESOLVED" && <button style={primaryBtn} onClick={() => open(f, "close")}>Verify and close</button>}
                  {canVerify && f.status === "RESOLVED" && <button style={ghostBtn} onClick={() => open(f, "reject")}>Send back</button>}
                </div>
                {f.status === "RESOLVED" && <p style={{ fontSize: 12, color: "#64748B" }}>Closing needs someone other than {f.resolvedByName} and a passing retest of this control — schedule a retest on the Control Testing tab.</p>}
              </div>
            )}
          </div>
        )
      })}

      {act && (
        <Modal title={`${titles[act.action]} — ${act.f.reference}`} onClose={() => setAct(null)}>
          <Err m={apiError} />
          {act.action === "plan" ? (
            <>
              <Field l="Plan * (at least 15 characters)"><textarea style={{ ...input, minHeight: 80 }} value={form.plan} onChange={e => setForm({ ...form, plan: e.target.value })} /></Field>
              <div style={{ display: "flex", gap: 12 }}>
                <div style={{ flex: 1 }}><Field l="Owner *"><input style={input} value={form.ownerName} onChange={e => setForm({ ...form, ownerName: e.target.value })} /></Field></div>
                <div style={{ flex: 1 }}><Field l="Due date *"><input type="date" style={input} value={form.dueDate} onChange={e => setForm({ ...form, dueDate: e.target.value })} /></Field></div>
              </div>
            </>
          ) : (
            <>
              {act.action === "close" && (
                <Field l="Passing retest *"><select style={input} value={form.retestId} onChange={e => setForm({ ...form, retestId: e.target.value })}>
                  <option value="">Choose a completed, passing retest…</option>{passingRetests.map(t => <option key={t.id} value={t.id}>{t.reference} — reviewed {fmtDate(t.reviewedAt)}</option>)}</select></Field>
              )}
              <Field l={act.action === "resolve" ? "What was done * (at least 15 characters)" : act.action === "reject" ? "What is still wrong * (at least 15 characters)" : "Verification note * (at least 15 characters)"}>
                <textarea style={{ ...input, minHeight: 80 }} value={form.note} onChange={e => setForm({ ...form, note: e.target.value })} /></Field>
            </>
          )}
          <div style={{ display: "flex", justifyContent: "flex-end", gap: 8 }}>
            <button style={ghostBtn} onClick={() => setAct(null)}>Cancel</button>
            <button style={primaryBtn} disabled={!valid || call.isPending} onClick={submit}>{titles[act.action]}</button>
          </div>
        </Modal>
      )}
    </div>
  )
}
