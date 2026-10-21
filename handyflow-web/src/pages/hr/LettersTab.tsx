// src/pages/hr/LettersTab.tsx
//
// The Letter & Document Generation Centre: letters HR has made, the templates behind them, and the letters issued to the signed-in
// employee. The server fills templates in and decides who may see and do what; this screen shows that and offers only what is allowed.
import { useState } from "react"
import { useAuth } from "../../hooks/useAuth"
import { letterRights } from "./letters.logic"
import LetterDetail from "./LetterDetail"
import LetterList from "./LetterList"
import MyLetters from "./MyLetters"
import TemplatesPanel from "./TemplatesPanel"

type Section = "letters" | "templates" | "mine"

export default function LettersTab() {
  const { user } = useAuth() as { user?: { permissions?: string[] } }
  const rights = letterRights(user?.permissions ?? [])
  const [section, setSection] = useState<Section>(rights.read ? "letters" : "mine")
  const [openId, setOpenId] = useState<string | null>(null)

  const tabs: { id: Section; label: string; show: boolean }[] = [
    { id: "letters", label: "Letters", show: rights.read },
    { id: "templates", label: "Templates", show: rights.read },
    { id: "mine", label: "My letters", show: true },
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
      {section === "letters" && rights.read && (openId ? <LetterDetail id={openId} back={() => setOpenId(null)} /> : <LetterList canIssue={rights.issue} open={setOpenId} />)}
      {section === "templates" && rights.read && <TemplatesPanel manage={rights.manage} />}
      {section === "mine" && <MyLetters />}
    </div>
  )
}
