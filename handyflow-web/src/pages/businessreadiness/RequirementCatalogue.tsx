// src/pages/businessreadiness/RequirementCatalogue.tsx
//
// The tracked-requirements catalogue (ADR-003): the list of things a business is checked against (CSD active, tax compliant, BBBEE certificate...) and, for each, WHAT SATISFIES IT:
// a registration (authority and/or type) and/or a document type. That is configuration, not code, so what proves a requirement can change without a release.
// One screen serves a tenant's own catalogue and a client's; only the URLs differ. Requirements are versioned: saving an edit creates a NEW version, and tenders already linked to
// the old one keep being judged on it (and are told a newer version exists).
import { useState } from "react"
import { Plus, Pencil, X, Search, ListChecks, ShieldCheck, Hand, CircleDashed } from "lucide-react"
import Chip from "../../components/ui/Chip"
import StatTile from "../../components/ui/StatTile"
import { useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { useTrackedRequirements, type TrackedRequirement } from "./readiness.api"
import { catalogueStats, createBody, emptyForm, errorText, formFrom, formProblem, newVersionBody, ruleKind, ruleText, searchRequirements, type RequirementForm } from "./readiness.logic"
import LookupInput from "../../components/ui/LookupInput"
import { APPLIES_TO, REGISTRATION_AUTHORITIES, DOCUMENT_TYPES, registrationTypesFor } from "../../lookups/southAfrica"

const lbl: React.CSSProperties = { display: "block", fontSize: 11.5, fontWeight: 700, color: "var(--hf-text-muted)", marginBottom: 3 }
const inp: React.CSSProperties = { width: "100%", padding: "8px 10px", border: "1px solid var(--hf-border-strong)", borderRadius: 8, fontSize: 13, background: "var(--hf-surface)", color: "var(--hf-text)", boxSizing: "border-box" }
const btn: React.CSSProperties = { display: "inline-flex", alignItems: "center", gap: 5, padding: "8px 14px", borderRadius: 8, border: "none", background: "var(--hf-sky-solid-strong)", color: "var(--hf-text-on-solid)", fontSize: 13, fontWeight: 700, cursor: "pointer" }
const ghost: React.CSSProperties = { ...btn, background: "transparent", color: "var(--hf-text-secondary)", border: "1px solid var(--hf-border-strong)" }

export default function RequirementCatalogue({ listUrl, createUrl, newVersionUrl, canManage, scope }: {
  listUrl: string; createUrl: string; newVersionUrl: (id: string) => string; canManage: boolean; scope: string
}) {
  const qc = useQueryClient()
  const { data: items = [], isLoading, isError, refetch } = useTrackedRequirements(listUrl)
  const [editing, setEditing] = useState<TrackedRequirement | null>(null)
  const [adding, setAdding] = useState(false)
  const [form, setForm] = useState<RequirementForm>(emptyForm())
  const [problem, setProblem] = useState<string | null>(null)
  const [search, setSearch] = useState("")

  const done = () => {
    qc.invalidateQueries({ queryKey: ["readiness-catalogue"] }); qc.invalidateQueries({ queryKey: ["readiness"] })
    setEditing(null); setAdding(false); setProblem(null)
  }
  const save = useMutation({
    mutationFn: () => editing ? apiClient.post(newVersionUrl(editing.id), newVersionBody(form)) : apiClient.post(createUrl, createBody(form)),
    onSuccess: done,
  })

  const open = (r: TrackedRequirement | null) => { setAdding(r === null); setEditing(r); setForm(r ? formFrom(r) : emptyForm()); setProblem(null); save.reset() }
  const close = () => { setAdding(false); setEditing(null); setProblem(null); save.reset() }
  const submit = () => { const p = formProblem(form, !editing); setProblem(p); if (!p) save.mutate() }
  const set = (patch: Partial<RequirementForm>) => setForm(f => ({ ...f, ...patch }))
  const formOpen = adding || editing !== null

  return (
    <div style={{ display: "grid", gap: 14 }}>
      <p style={{ fontSize: 13, color: "var(--hf-text-secondary)", margin: 0 }}>
        Requirements {scope} is checked against. For each one, say what satisfies it (a registration, a document, or both) and tenders that use it are checked automatically. A requirement with no rule can still be ticked by hand, but is not checked.
      </p>
      {items.length > 0 && (() => {
        const st = catalogueStats(items)
        return (
          <div style={{ display: "flex", gap: 12, flexWrap: "wrap" }}>
            <StatTile label="Requirements" value={st.total} icon={<ListChecks size={18} />} tone="info" />
            <StatTile label="Checked automatically" value={st.checked} icon={<ShieldCheck size={18} />} tone="ok" />
            <StatTile label="Ticked by hand" value={st.manual} icon={<Hand size={18} />} tone={st.manual > 0 ? "warn" : "neutral"} hint={st.manual > 0 ? "No rule set" : undefined} />
            <StatTile label="Optional" value={st.optional} icon={<CircleDashed size={18} />} />
          </div>
        )
      })()}
      <div style={{ display: "flex", gap: 10, alignItems: "center", flexWrap: "wrap" }}>
        {items.length > 0 && (
          <div style={{ position: "relative", flex: "1 1 220px", maxWidth: 340 }}>
            <Search size={14} aria-hidden="true" style={{ position: "absolute", left: 11, top: 10, color: "var(--hf-text-faint)" }} />
            <input aria-label="Search requirements" value={search} onChange={e => setSearch(e.target.value)} placeholder="Search code, name or what satisfies it" style={{ ...inp, paddingLeft: 32 }} />
          </div>
        )}
        {canManage && !formOpen && <button type="button" style={btn} onClick={() => open(null)}><Plus size={14} />Add requirement</button>}
      </div>

      {formOpen && (
        <form onSubmit={e => { e.preventDefault(); submit() }} aria-label={editing ? `Edit ${editing.code}` : "Add requirement"} style={{ border: "1px solid var(--hf-border)", borderRadius: 10, padding: 16, background: "var(--hf-surface)" }}>
          <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 10 }}>
            <strong style={{ fontSize: 13.5, color: "var(--hf-text)" }}>{editing ? `Edit ${editing.code}` : "Add requirement"}</strong>
            <button type="button" aria-label="Close" onClick={close} style={{ background: "none", border: "none", cursor: "pointer", color: "var(--hf-text-muted)" }}><X size={16} /></button>
          </div>
          {editing && <p style={{ fontSize: 12, color: "var(--hf-text-muted)", margin: "0 0 10px" }}>Saving creates version {editing.requirementVersion + 1}. Tenders already linked to this version keep being judged on it, and are told a newer one exists.</p>}
          <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(190px, 1fr))", gap: 12 }}>
            <div><label style={lbl} htmlFor="rq-code">Code</label><input id="rq-code" style={inp} value={form.code} disabled={!!editing} onChange={e => set({ code: e.target.value })} placeholder="CSD_ACTIVE" /></div>
            <div><label style={lbl} htmlFor="rq-name">Name</label><input id="rq-name" style={inp} value={form.name} onChange={e => set({ name: e.target.value })} placeholder="Valid CSD registration" /></div>
            <div><label style={lbl} htmlFor="rq-applies">Applies to</label><LookupInput id="rq-applies" style={inp} value={form.appliesTo} options={APPLIES_TO} onChange={v => set({ appliesTo: v })} placeholder="Government tender" /></div>
          </div>
          <fieldset style={{ border: "1px solid var(--hf-border-subtle)", borderRadius: 8, margin: "14px 0 0", padding: "10px 12px" }}>
            <legend style={{ fontSize: 12, fontWeight: 700, color: "var(--hf-text-secondary)", padding: "0 6px" }}>What satisfies it (leave blank if it can't be checked automatically)</legend>
            <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(190px, 1fr))", gap: 12 }}>
              <div><label style={lbl} htmlFor="rq-auth">Registration authority</label><LookupInput id="rq-auth" style={inp} value={form.authority} options={REGISTRATION_AUTHORITIES.map(value => ({ value }))} onChange={v => set({ authority: v })} placeholder="CSD, CIDB, SARS…" /></div>
              <div><label style={lbl} htmlFor="rq-type">Registration type</label><LookupInput id="rq-type" style={inp} value={form.registrationType} options={registrationTypesFor(form.authority)} onChange={v => set({ registrationType: v })} placeholder="Optional, e.g. Supplier" /></div>
              <div><label style={lbl} htmlFor="rq-doc">Document type</label><LookupInput id="rq-doc" style={inp} value={form.evidenceType} options={DOCUMENT_TYPES} onChange={v => set({ evidenceType: v })} placeholder="e.g. BBBEE Certificate" /></div>
            </div>
            <p style={{ fontSize: 11.5, color: "var(--hf-text-faint)", margin: "8px 0 0" }}>A registration must be active and not expired on the tender's closing date. A document must be unexpired on that date and verified. If you set both, both must hold.</p>
          </fieldset>
          <label style={{ display: "flex", alignItems: "center", gap: 6, fontSize: 13, color: "var(--hf-text)", margin: "12px 0" }}>
            <input type="checkbox" checked={form.required} onChange={e => set({ required: e.target.checked })} />Required
          </label>
          {problem && <p role="alert" style={{ fontSize: 12.5, color: "var(--hf-danger-text)", margin: "0 0 8px" }}>{problem}</p>}
          {save.isError && <p role="alert" style={{ fontSize: 12.5, color: "var(--hf-danger-text)", margin: "0 0 8px" }}>{errorText(save.error)}</p>}
          <div style={{ display: "flex", gap: 8 }}>
            <button type="submit" style={{ ...btn, opacity: save.isPending ? 0.6 : 1 }} disabled={save.isPending}>{editing ? "Save new version" : "Add requirement"}</button>
            <button type="button" style={ghost} onClick={close}>Cancel</button>
          </div>
        </form>
      )}

      {isLoading && <p style={{ fontSize: 13, color: "var(--hf-text-faint)", margin: 0 }}>Loading requirements…</p>}
      {isError && <p role="alert" style={{ fontSize: 13, color: "var(--hf-danger-text)", margin: 0 }}>We couldn't load the requirements. <button type="button" onClick={() => refetch()} style={{ background: "none", border: "none", textDecoration: "underline", cursor: "pointer", color: "inherit", fontWeight: 700 }}>Try again</button></p>}
      {!isLoading && !isError && items.length === 0 && <p style={{ fontSize: 13, color: "var(--hf-text-faint)", margin: 0 }}>No requirements yet. {canManage ? "Add the first one above." : "Ask someone with manage access to add them."}</p>}
      {items.length > 0 && (
        <div style={{ overflowX: "auto" }}>
          <table aria-label="Tracked requirements" style={{ width: "100%", borderCollapse: "collapse", fontSize: 12.5 }}>
            <thead><tr style={{ textAlign: "left" }}>{["Code", "Requirement", "Satisfied by", "Version", ""].map(h => <th key={h} scope="col" style={{ padding: "8px 10px", fontSize: 11, fontWeight: 700, textTransform: "uppercase", letterSpacing: 0.4, color: "var(--hf-text-faint)", borderBottom: "1px solid var(--hf-border)" }}>{h}</th>)}</tr></thead>
            <tbody>
              {searchRequirements([...items].sort((a, b) => a.code.localeCompare(b.code)), search).map(r => (
                <tr key={r.id} aria-label={`Requirement ${r.code}`} style={{ borderTop: "1px solid var(--hf-border-subtle)" }}>
                  <td style={{ padding: "9px 10px", fontFamily: "monospace", fontWeight: 600 }}>{r.code}</td>
                  <td style={{ padding: "9px 10px" }}><div style={{ fontWeight: 600, color: "var(--hf-text)" }}>{r.name}</div>{(r.appliesTo || !r.required) && <div style={{ fontSize: 11, color: "var(--hf-text-faint)" }}>{[r.appliesTo, r.required ? null : "not required"].filter(Boolean).join(" · ")}</div>}</td>
                  <td style={{ padding: "9px 10px" }}>
                    {(() => { const k = ruleKind(r); return <Chip tone={k === "NONE" ? "warn" : "ok"}>{k === "NONE" ? "Ticked by hand" : k === "BOTH" ? "Registration and document" : k === "REGISTRATION" ? "Registration" : "Document"}</Chip> })()}
                    <div style={{ fontSize: 12, marginTop: 4, color: ruleKind(r) === "NONE" ? "var(--hf-text-faint)" : "var(--hf-text-secondary)" }}>{ruleText(r)}</div>
                  </td>
                  <td style={{ padding: "9px 10px" }}><Chip tone="neutral">v{r.requirementVersion}</Chip></td>
                  <td style={{ padding: "9px 10px", textAlign: "right" }}>{canManage && <button type="button" style={{ ...ghost, padding: "4px 10px", fontSize: 12 }} aria-label={`Edit ${r.code}`} onClick={() => open(r)}><Pencil size={12} />Edit</button>}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}
