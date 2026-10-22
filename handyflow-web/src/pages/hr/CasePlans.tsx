// Improvement plans linked to one employee relations case, with a way to make one for a poor performance case.
import { useState } from "react"
import { usePerfList } from "./perf.hooks"
import { PlanView, NewPlan } from "./PerfPlans"
import { fmtDate } from "./er.logic"
import { plain, primary, type PlanRow } from "./perf.shared"
import { PlanStatus, Section } from "./PerfParts"

export default function CasePlans({ caseId, caseType, category, employeeId, canManage }: { caseId: string; caseType: string; category: string; employeeId: string; canManage: boolean }) {
  const q = usePerfList<PlanRow>(["plans", "case", caseId], `/plans?scope=all&caseId=${caseId}`)
  const [openId, setOpenId] = useState<string | null>(null)
  const [making, setMaking] = useState(false)
  const poor = caseType === "INCAPACITY" && category === "POOR_PERFORMANCE"
  if (q.isLoading || q.isError) return null // nothing to show yet, or no right to see plans: leave the section out
  if (openId) return <Section title="Improvement plan"><PlanView id={openId} back={() => setOpenId(null)} /></Section>
  const rows = q.data ?? []
  if (rows.length === 0 && !(poor && canManage)) return null
  return (
    <Section title="Improvement plans">
      {rows.map(p => (
        <div key={p.id} style={{ display: "flex", gap: 10, alignItems: "center", marginBottom: 8, fontSize: 13, flexWrap: "wrap" }}>
          <button onClick={() => setOpenId(p.id)} style={{ ...plain, border: "none", padding: 0, fontWeight: 600 }}>{p.title}</button>
          <PlanStatus status={p.status} /><span style={{ color: "var(--hf-text-muted)" }}>ends {fmtDate(p.endDate)}</span>
        </div>
      ))}
      {rows.length === 0 && <div style={{ fontSize: 13, color: "var(--hf-text-muted)", marginBottom: 8 }}>No plan yet. A poor performance case needs a fair period to improve, with support, before any outcome.</div>}
      {poor && canManage && (making
        ? <NewPlan caseId={caseId} employeeId={employeeId} done={() => setMaking(false)} />
        : <button onClick={() => setMaking(true)} style={primary}>Make a plan</button>)}
    </Section>
  )
}
