// src/pages/legalcompliance/DeclarationCampaignsTab.tsx
//
// Two views in one tab. Everyone sees "My declarations" (answer a campaign: nothing to declare / earlier declarations unchanged / declare an interest);
// managers also see campaigns and who has answered what. Managers never see the content of a declaration here.
// Endpoints (/api/v1/legalcompliance/declaration-campaigns): GET /mine · POST /returns/{id}/answer · POST /returns/{id}/declare · GET "" · GET /{id} · POST "" · POST /{id}/close · POST /{id}/update-audience
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"
import { input, primaryBtn, ghostBtn, unwrap, one, fmtDate, fmtDateTime, errMsg } from "./lcHelpers"
import { Modal, Field, Err, Badge } from "./LcUi"
import { ChevronDown, ChevronUp, Plus } from "lucide-react"

interface MyDecl { id: string; reference: string; declarationType: string; counterparty: string; status: string }
interface Mine {
  reviewDatesExtended: number
  id: string; campaignId: string; campaignTitle: string; instructions: string | null; dueDate: string; overdue: boolean; campaignOpen: boolean; status: string; respondedAt: string | null
  statement: string | null; existingDeclarations: number; declarations: MyDecl[]; nilStatement: string; unchangedStatement: string
}
interface Campaign {
  id: string; reference: string; title: string; instructions: string | null; dueDate: string; overdue: boolean; department: string | null; status: string; launchedByName: string | null
  closeReason: string | null; total: number; nil: number; unchanged: number; declared: number; pending: number; left: number; percentComplete: number
}
interface Row { id: string; userName: string; userEmail: string | null; status: string; respondedAt: string | null }
interface DeclForm { declarationType: string; counterparty: string; description: string; estimatedValue: string }

const RSTATUS: Record<string, { label: string; color: string; bg: string }> = {
  PENDING: { label: "Pending", color: "#92400E", bg: "#FEF3C7" }, NIL: { label: "Nothing to declare", color: "#166534", bg: "#DCFCE7" },
  UNCHANGED: { label: "Unchanged", color: "#166534", bg: "#DCFCE7" }, DECLARED: { label: "Declared", color: "#1D4ED8", bg: "#DBEAFE" },
  LEFT: { label: "No longer in audience", color: "#475569", bg: "#E2E8F0" },
}
const TYPES: [string, string][] = [
  ["OUTSIDE_EMPLOYMENT", "Outside employment"], ["DIRECTORSHIP", "Directorship"], ["FINANCIAL_INTEREST", "Financial interest"],
  ["RELATED_PARTY", "Related party"], ["FAMILY_RELATIONSHIP", "Family relationship"], ["OTHER", "Other"],
]
const base = "/api/v1/legalcompliance/declaration-campaigns"
const emptyDecl: DeclForm = { declarationType: "FINANCIAL_INTEREST", counterparty: "", description: "", estimatedValue: "" }

export default function DeclarationCampaignsTab() {
  const qc = useQueryClient()
  const admin = usePermission("LEGALCOMPLIANCE_ADMIN")
  const reviewer = usePermission("LEGALCOMPLIANCE_ETHICS_REVIEW")
  const canManage = reviewer || admin

  const [view, setView] = useState<"mine" | "campaigns">("mine")
  const [openMine, setOpenMine] = useState<string | null>(null)
  const [openCampaign, setOpenCampaign] = useState<string | null>(null)
  const [declaring, setDeclaring] = useState<{ ret: Mine; form: DeclForm } | null>(null)
  const [launch, setLaunch] = useState<{ title: string; instructions: string; dueDate: string; department: string } | null>(null)
  const [closing, setClosing] = useState<{ c: Campaign; reason: string } | null>(null)
  const [apiError, setApiError] = useState("")
  const [notice, setNotice] = useState("")

  const { data: mine = [], isLoading } = useQuery<Mine[]>({ queryKey: ["lc-decl", "mine"], queryFn: async () => unwrap(await apiClient.get(`${base}/mine`)) as Mine[] })
  const { data: campaigns = [] } = useQuery<Campaign[]>({ queryKey: ["lc-decl-campaigns", "list"], enabled: canManage && view === "campaigns", queryFn: async () => unwrap(await apiClient.get(base)) as Campaign[] })
  const { data: rows = [] } = useQuery<Row[]>({ queryKey: ["lc-decl-campaigns", "rows", openCampaign], enabled: !!openCampaign, queryFn: async () => (one(await apiClient.get(`${base}/${openCampaign}`)) as { returns: Row[] }).returns })

  const refresh = () => { qc.invalidateQueries({ queryKey: ["lc-decl"] }); qc.invalidateQueries({ queryKey: ["lc-decl-campaigns"] }); setApiError("") }
  const answer = useMutation({
    mutationFn: ({ id, a }: { id: string; a: "NIL" | "UNCHANGED" }) => apiClient.post(`${base}/returns/${id}/answer`, { answer: a }),
    onSuccess: (res) => {
      refresh()
      const n = (one(res) as { reviewDatesExtended?: number } | undefined)?.reviewDatesExtended ?? 0
      setNotice(n > 0 ? `Your answer was recorded, and the review date of ${n} earlier ${n === 1 ? "declaration was" : "declarations were"} extended by a full review period` : "Your answer was recorded")
    },
    onError: (e: unknown) => setApiError(errMsg(e, "Failed to record answer")),
  })
  const declare = useMutation({
    mutationFn: ({ id, f }: { id: string; f: DeclForm }) => apiClient.post(`${base}/returns/${id}/declare`, {
      declarationType: f.declarationType, counterparty: f.counterparty.trim(), description: f.description.trim(), estimatedValue: f.estimatedValue.trim() ? Number(f.estimatedValue) : null, reviewMonths: 12,
    }),
    onSuccess: () => { refresh(); setDeclaring(null); setNotice("Your declaration was submitted for review") }, onError: (e: unknown) => setApiError(errMsg(e, "Failed to submit declaration")),
  })
  const doLaunch = useMutation({
    mutationFn: (l: NonNullable<typeof launch>) => apiClient.post(base, { title: l.title.trim(), instructions: l.instructions.trim() || null, dueDate: l.dueDate, department: l.department.trim() || null }),
    onSuccess: () => { refresh(); setLaunch(null); setNotice("Campaign launched and everyone notified") }, onError: (e: unknown) => setApiError(errMsg(e, "Failed to launch campaign")),
  })
  const addJoiners = useMutation({
    mutationFn: (id: string) => apiClient.post(`${base}/${id}/update-audience`),
    onSuccess: (res) => {
      refresh()
      const r = one(res) as { added: number; excused: number; reinstated: number }
      const parts = [r.added > 0 && `${r.added} added and notified`, r.excused > 0 && `${r.excused} excused because they are no longer in the audience`, r.reinstated > 0 && `${r.reinstated} put back`].filter(Boolean)
      setNotice(parts.length === 0 ? "The audience is already up to date" : parts.join(", "))
    },
    onError: (e: unknown) => setApiError(errMsg(e, "Failed to update the audience")),
  })
  const doClose = useMutation({
    mutationFn: (c: { id: string; reason: string }) => apiClient.post(`${base}/${c.id}/close`, { reason: c.reason.trim() }),
    onSuccess: () => { refresh(); setClosing(null); setNotice("Campaign closed") }, onError: (e: unknown) => setApiError(errMsg(e, "Failed to close campaign")),
  })

  const pending = mine.filter(m => m.status === "PENDING" && m.campaignOpen).length
  const tab = (id: "mine" | "campaigns", label: string) => (
    <button onClick={() => { setView(id); setApiError(""); setNotice("") }} style={{ ...(view === id ? primaryBtn : ghostBtn), padding: "6px 14px" }}>{label}</button>
  )
  const valueOk = (v: string) => !v.trim() || (Number.isFinite(Number(v)) && Number(v) >= 0)

  return (
    <div>
      <div style={{ display: "flex", gap: 8, marginBottom: 14, alignItems: "center" }}>
        {tab("mine", `My declarations${pending ? ` (${pending})` : ""}`)}{canManage && tab("campaigns", "Campaigns")}
        {canManage && view === "campaigns" && <button style={{ ...primaryBtn, marginLeft: "auto" }} onClick={() => { setApiError(""); setLaunch({ title: "", instructions: "", dueDate: "", department: "" }) }}><Plus size={14} style={{ verticalAlign: -2 }} /> Launch campaign</button>}
      </div>
      {notice && <div style={{ background: "#ECFDF5", border: "1px solid #A7F3D0", color: "#065F46", borderRadius: 8, padding: "8px 12px", fontSize: 13, marginBottom: 12 }}>{notice}</div>}
      {apiError && !launch && !closing && !declaring && <Err m={apiError} />}

      {view === "mine" && (isLoading ? <p style={{ color: "#64748B" }}>Loading…</p> : mine.length === 0 ? <p style={{ color: "#64748B", fontSize: 14 }}>No declaration requests have been sent to you.</p> : mine.map(m => {
        const open = openMine === m.id, st = RSTATUS[m.status]
        return (
          <div key={m.id} style={{ border: `1px solid ${m.overdue && m.status === "PENDING" ? "#FECACA" : "#E2E8F0"}`, borderRadius: 10, background: "#fff", marginBottom: 8 }}>
            <button onClick={() => { setOpenMine(open ? null : m.id); setApiError("") }} style={{ all: "unset", cursor: "pointer", display: "flex", gap: 12, alignItems: "center", padding: "12px 16px", width: "calc(100% - 32px)" }}>
              <div style={{ flex: 1, minWidth: 0 }}>
                <div style={{ fontWeight: 700, fontSize: 14, color: "#0F172A" }}>{m.campaignTitle}</div>
                <div style={{ fontSize: 12, color: "#64748B" }}>due {fmtDate(m.dueDate)}{m.overdue && m.campaignOpen ? " (overdue)" : ""}{!m.campaignOpen ? " · campaign closed" : ""}</div>
              </div>
              <Badge text={st.label} color={st.color} bg={st.bg} />{open ? <ChevronUp size={16} /> : <ChevronDown size={16} />}
            </button>
            {open && (
              <div style={{ padding: "0 16px 14px", fontSize: 13, color: "#475569" }}>
                {m.instructions && <p style={{ marginTop: 0 }}><b>Instructions:</b> {m.instructions}</p>}
                {m.statement && <p style={{ margin: "0 0 8px" }}>You confirmed on {fmtDateTime(m.respondedAt)}: “{m.statement}”</p>}
                {m.declarations.length > 0 && (
                  <ul style={{ margin: "0 0 8px", paddingLeft: 18 }}>
                    {m.declarations.map(d => <li key={d.id}>{d.reference} — {d.counterparty} ({TYPES.find(t => t[0] === d.declarationType)?.[1] ?? d.declarationType}) · {d.status.toLowerCase().replace("_", " ")}</li>)}
                  </ul>
                )}
                {m.status === "LEFT" ? <p style={{ fontSize: 12, marginBottom: 0 }}>You are no longer part of this campaign, so nothing is needed from you.</p> : m.campaignOpen ? (
                  <>
                    <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
                      {m.status !== "DECLARED" && <button style={ghostBtn} disabled={answer.isPending} title={m.nilStatement} onClick={() => answer.mutate({ id: m.id, a: "NIL" })}>I have nothing to declare</button>}
                      {m.status !== "DECLARED" && <button style={ghostBtn} disabled={answer.isPending || m.existingDeclarations === 0} title={m.existingDeclarations === 0 ? "You have no earlier declarations to confirm" : m.unchangedStatement} onClick={() => answer.mutate({ id: m.id, a: "UNCHANGED" })}>My earlier declarations are unchanged ({m.existingDeclarations})</button>}
                      <button style={primaryBtn} onClick={() => { setApiError(""); setDeclaring({ ret: m, form: { ...emptyDecl } }) }}>Declare an interest</button>
                    </div>
                    {m.status !== "DECLARED" && <p style={{ fontSize: 12, color: "#64748B", marginBottom: 0 }}>“{m.nilStatement}” / “{m.unchangedStatement}” — you can change your answer until the campaign closes. Confirming your earlier declarations are unchanged also extends the review date of each one a reviewer has cleared. Once you declare an interest, “nothing to declare” is no longer available.</p>}
                  </>
                ) : <p style={{ fontSize: 12, marginBottom: 0 }}>This campaign is closed.</p>}
              </div>
            )}
          </div>
        )
      }))}

      {view === "campaigns" && (campaigns.length === 0 ? <p style={{ color: "#64748B", fontSize: 14 }}>No campaigns yet.</p> : campaigns.map(c => {
        const open = openCampaign === c.id
        return (
          <div key={c.id} style={{ border: `1px solid ${c.overdue && c.status === "OPEN" ? "#FECACA" : "#E2E8F0"}`, borderRadius: 10, background: "#fff", marginBottom: 8 }}>
            <button onClick={() => setOpenCampaign(open ? null : c.id)} style={{ all: "unset", cursor: "pointer", display: "flex", gap: 12, alignItems: "center", padding: "12px 16px", width: "calc(100% - 32px)" }}>
              <div style={{ flex: 1, minWidth: 0 }}>
                <div style={{ fontWeight: 700, fontSize: 14, color: "#0F172A" }}>{c.reference} — {c.title}</div>
                <div style={{ fontSize: 12, color: "#64748B" }}>due {fmtDate(c.dueDate)}{c.overdue && c.status === "OPEN" ? " (overdue)" : ""}{c.department ? ` · ${c.department}` : ""} · {c.nil} nil, {c.unchanged} unchanged, {c.declared} declared, {c.pending} pending of {c.total}{c.left > 0 ? ` (${c.left} excused)` : ""}</div>
              </div>
              <div style={{ width: 90, height: 8, background: "#E2E8F0", borderRadius: 4 }} aria-label={`${c.percentComplete}% answered`}><div style={{ width: `${c.percentComplete}%`, height: 8, background: "#16A34A", borderRadius: 4 }} /></div>
              <Badge text={c.status === "OPEN" ? "Open" : "Closed"} color={c.status === "OPEN" ? "#1D4ED8" : "#475569"} bg={c.status === "OPEN" ? "#DBEAFE" : "#E2E8F0"} />{open ? <ChevronUp size={16} /> : <ChevronDown size={16} />}
            </button>
            {open && (
              <div style={{ padding: "0 16px 14px", fontSize: 13, color: "#475569" }}>
                {c.closeReason && <p style={{ marginTop: 0 }}><b>Closed:</b> {c.closeReason}</p>}
                {c.status === "OPEN" && (
                  <div style={{ display: "flex", gap: 8, marginBottom: 8, flexWrap: "wrap" }}>
                    <button style={ghostBtn} disabled={addJoiners.isPending} title="Ask people who joined since launch, and excuse people who have left and not answered" onClick={() => { setApiError(""); setNotice(""); addJoiners.mutate(c.id) }}>Update audience</button>
                    <button style={{ ...ghostBtn, color: "#991B1B" }} onClick={() => { setApiError(""); setClosing({ c, reason: "" }) }}>Close campaign</button>
                  </div>
                )}
                {c.overdue && c.status === "OPEN" && c.pending > 0 && <p style={{ fontSize: 12, marginTop: 0, color: "#991B1B" }}>Overdue: {c.pending} still to answer. You get a weekly count by notification until it is closed or everyone has answered.</p>}
                <p style={{ fontSize: 12, marginTop: 0 }}>This shows who has answered, not what they declared. Declarations themselves are reviewed under Conflicts &amp; ethics.</p>
                <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 12 }}>
                  <thead><tr style={{ textAlign: "left", color: "#64748B" }}><th style={{ padding: 4 }}>Person</th><th>Status</th><th>When</th></tr></thead>
                  <tbody>{rows.map(r => <tr key={r.id} style={{ borderTop: "1px solid #F1F5F9" }}><td style={{ padding: 4 }}>{r.userName}{r.userEmail ? ` (${r.userEmail})` : ""}</td><td><Badge text={RSTATUS[r.status].label} color={RSTATUS[r.status].color} bg={RSTATUS[r.status].bg} /></td><td>{r.respondedAt ? fmtDateTime(r.respondedAt) : "—"}</td></tr>)}</tbody>
                </table>
              </div>
            )}
          </div>
        )
      }))}

      {declaring && (
        <Modal title={`Declare an interest — ${declaring.ret.campaignTitle}`} onClose={() => setDeclaring(null)}>
          <Err m={apiError} />
          <Field l="Type *"><select style={input} value={declaring.form.declarationType} onChange={e => setDeclaring({ ...declaring, form: { ...declaring.form, declarationType: e.target.value } })}>{TYPES.map(t => <option key={t[0]} value={t[0]}>{t[1]}</option>)}</select></Field>
          <Field l="Company or person involved *"><input style={input} value={declaring.form.counterparty} onChange={e => setDeclaring({ ...declaring, form: { ...declaring.form, counterparty: e.target.value } })} /></Field>
          <Field l="What is the interest? * (at least 15 characters)"><textarea style={{ ...input, minHeight: 70 }} value={declaring.form.description} onChange={e => setDeclaring({ ...declaring, form: { ...declaring.form, description: e.target.value } })} /></Field>
          <Field l="Estimated value in rand (optional)"><input style={input} inputMode="decimal" value={declaring.form.estimatedValue} onChange={e => setDeclaring({ ...declaring, form: { ...declaring.form, estimatedValue: e.target.value } })} /></Field>
          <div style={{ display: "flex", justifyContent: "flex-end", gap: 8 }}>
            <button style={ghostBtn} onClick={() => setDeclaring(null)}>Cancel</button>
            <button style={primaryBtn} disabled={declare.isPending || !declaring.form.counterparty.trim() || declaring.form.description.trim().length < 15 || !valueOk(declaring.form.estimatedValue)} onClick={() => declare.mutate({ id: declaring.ret.id, f: declaring.form })}>Submit declaration</button>
          </div>
        </Modal>
      )}

      {launch && (
        <Modal title="Launch declaration campaign" onClose={() => setLaunch(null)}>
          <Err m={apiError} />
          <Field l="Title *"><input style={input} value={launch.title} onChange={e => setLaunch({ ...launch, title: e.target.value })} placeholder="Annual declaration of interests" /></Field>
          <div style={{ display: "flex", gap: 12 }}>
            <div style={{ flex: 1 }}><Field l="Due date *"><input type="date" style={input} value={launch.dueDate} onChange={e => setLaunch({ ...launch, dueDate: e.target.value })} /></Field></div>
            <div style={{ flex: 1 }}><Field l="Department (blank = everyone)"><input style={input} value={launch.department} onChange={e => setLaunch({ ...launch, department: e.target.value })} /></Field></div>
          </div>
          <Field l="Instructions"><textarea style={{ ...input, minHeight: 60 }} value={launch.instructions} onChange={e => setLaunch({ ...launch, instructions: e.target.value })} /></Field>
          <p style={{ fontSize: 12, color: "#64748B", marginTop: 0 }}>Goes to every active user (or the department) as they are now; people who join later are not added. Each person is notified, and reminded 7 days before, the day before, on the due date, then weekly while overdue.</p>
          <div style={{ display: "flex", justifyContent: "flex-end", gap: 8 }}>
            <button style={ghostBtn} onClick={() => setLaunch(null)}>Cancel</button>
            <button style={primaryBtn} disabled={doLaunch.isPending || !launch.title.trim() || !launch.dueDate} onClick={() => doLaunch.mutate(launch)}>Launch</button>
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
