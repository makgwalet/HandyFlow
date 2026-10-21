// src/pages/hr/EmployeeRelationsTab.tsx
//
// Employee relations: discipline, grievances and incapacity. HR works cases here; every employee sees their own matters.
// The server decides what each case allows and who may see it; this screen shows that and offers only what is allowed.
import { useState } from "react"
import { useAuth } from "../../hooks/useAuth"
import { erRights } from "./er.logic"
import ErCaseDetail from "./ErCaseDetail"
import ErCaseList from "./ErCaseList"
import ErPolicyPanel from "./ErPolicyPanel"
import MyCases from "./MyCases"

type Section = "cases" | "mine" | "policy"

export default function EmployeeRelationsTab() {
  const { user } = useAuth() as { user?: { permissions?: string[] } }
  const rights = erRights(user?.permissions ?? [])
  const [section, setSection] = useState<Section>(rights.read ? "cases" : "mine")
  const [openId, setOpenId] = useState<string | null>(null)

  const tabs: { id: Section; label: string; show: boolean }[] = [
    { id: "cases", label: "Cases", show: rights.read },
    { id: "mine", label: "My cases", show: true },
    { id: "policy", label: "Policy and warnings", show: rights.read },
  ]

  return (
    <div>
      <div style={{ display: "flex", gap: 8, marginBottom: 18, flexWrap: "wrap" }} role="tablist">
        {tabs.filter(t => t.show).map(t => (
          <button key={t.id} role="tab" aria-selected={section === t.id} onClick={() => { setSection(t.id); setOpenId(null) }}
            style={{ padding: "6px 14px", borderRadius: 20, fontSize: 13, cursor: "pointer", border: "none", fontWeight: section === t.id ? 600 : 400,
              background: section === t.id ? "var(--hf-primary)" : "var(--hf-surface-sunken)", color: section === t.id ? "var(--hf-text-on-solid)" : "var(--hf-text-muted)" }}>
            {t.label}
          </button>
        ))}
      </div>
      {section === "cases" && rights.read && (openId ? <ErCaseDetail id={openId} back={() => setOpenId(null)} /> : <ErCaseList manage={rights.manage} open={setOpenId} />)}
      {section === "mine" && <MyCases />}
      {section === "policy" && rights.read && <ErPolicyPanel manage={rights.manage} />}
    </div>
  )
}
