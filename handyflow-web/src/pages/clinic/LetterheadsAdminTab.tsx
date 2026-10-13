// The practice letterheads: one practice-wide default and, if wanted, one per branch (CLINIC-DEC-026, patch 0194). A letter is printed on the one
// chosen when it is written, else the branch's, else the default. Changing a letterhead never changes a document already issued.
import { useRef, useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { useCan } from "./clinicAccess"
import { BORDER, GRAY, lbl, sinp } from "./patientFile.shared"
import { primaryBtn, smallBtn } from "./OverviewCard"
import { formFrom, kindLabel, linesFromText, logoProblem, problems, type Letterhead, type LetterheadForm } from "./letterheadView"

const unwrap = (r: any) => r.data?.data ?? r.data

export default function LetterheadsAdminTab() {
  const qc = useQueryClient()
  const canManage = useCan("manageLetterheads")
  const [editing, setEditing] = useState<Letterhead | "new" | null>(null)
  const [form, setForm] = useState<LetterheadForm>(formFrom(null))
  const [makeDefault, setMakeDefault] = useState(false)
  const [error, setError] = useState("")
  const [note, setNote] = useState("")
  const file = useRef<HTMLInputElement>(null)
  const [logoFor, setLogoFor] = useState<string | null>(null)

  const list = useQuery<Letterhead[]>({
    queryKey: ["clinic-letterheads"], retry: false,
    queryFn: async () => { const d = unwrap(await apiClient.get("/api/v1/clinic/letterheads")); return Array.isArray(d) ? d : [] },
  })
  const done = (msg = "") => { setError(""); setNote(msg); qc.invalidateQueries({ queryKey: ["clinic-letterheads"] }) }
  const fail = (what: string) => (e: any) => { setNote(""); setError(e?.response?.data?.message ?? what) }

  const body = () => ({
    name: form.name.trim(), branchName: form.branchName.trim() || null, practiceName: form.practiceName.trim(),
    lines: linesFromText(form.linesText), makeDefault: form.branchName.trim() ? false : makeDefault,
  })
  const save = useMutation({
    mutationFn: () => editing === "new"
      ? apiClient.post("/api/v1/clinic/letterheads", body())
      : apiClient.put(`/api/v1/clinic/letterheads/${(editing as Letterhead).id}`, body()),
    onSuccess: () => { setEditing(null); done("Letterhead saved.") }, onError: fail("Could not save the letterhead."),
  })
  const remove = useMutation({
    mutationFn: (id: string) => apiClient.delete(`/api/v1/clinic/letterheads/${id}`),
    onSuccess: () => done("Letterhead removed. Documents already issued keep what was printed on them."), onError: fail("Could not remove the letterhead."),
  })
  const upload = useMutation({
    mutationFn: ({ id, f }: { id: string; f: File }) => {
      const data = new FormData(); data.append("file", f)
      return apiClient.put(`/api/v1/clinic/letterheads/${id}/logo`, data)
    },
    onSuccess: () => done("Logo saved."), onError: fail("Could not save the logo."),
  })
  const dropLogo = useMutation({
    mutationFn: (id: string) => apiClient.delete(`/api/v1/clinic/letterheads/${id}/logo`),
    onSuccess: () => done("Logo removed."), onError: fail("Could not remove the logo."),
  })

  const begin = (l: Letterhead | "new") => {
    setEditing(l); setForm(formFrom(l === "new" ? null : l)); setMakeDefault(l !== "new" && l.isDefault); setError(""); setNote("")
  }
  const found = problems(form)
  const chooseLogo = (f: File | undefined) => {
    if (!f || !logoFor) return
    const p = logoProblem(f)
    if (p) { setError(p); setNote(""); return }
    upload.mutate({ id: logoFor, f })
  }
  const set = (k: keyof LetterheadForm) => (e: { target: { value: string } }) => setForm(f => ({ ...f, [k]: e.target.value }))

  return (
    <div>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", gap: 12, marginBottom: 12, flexWrap: "wrap" }}>
        <div>
          <div style={{ fontWeight: 700 }}>Letterheads</div>
          <div style={{ fontSize: 12, color: GRAY }}>The practice name, address and logo printed at the top of letters. The practice-wide default is used unless a branch or another letterhead is chosen.</div>
        </div>
        {canManage && <button type="button" style={primaryBtn} onClick={() => begin("new")}>Add a letterhead</button>}
      </div>
      {note && <div role="status" style={{ fontSize: 13, marginBottom: 8 }}>{note}</div>}
      {error && <div role="alert" style={{ fontSize: 13, color: "var(--hf-danger-text)", marginBottom: 8 }}>{error}</div>}
      {list.isLoading && <div style={{ fontSize: 13, color: GRAY }}>Loading…</div>}
      {list.isError && <div role="alert" style={{ fontSize: 13, color: "var(--hf-danger-text)" }}>The letterheads could not be loaded.</div>}
      <input ref={file} type="file" accept="image/png,image/jpeg" aria-label="Logo file" hidden
        onChange={e => { chooseLogo(e.target.files?.[0]); e.target.value = "" }} />

      {(list.data ?? []).map(l => (
        <div key={l.id} style={{ border: `1px solid ${BORDER}`, borderRadius: 10, padding: "10px 14px", marginBottom: 8 }}>
          <div style={{ display: "flex", justifyContent: "space-between", gap: 8, flexWrap: "wrap" }}>
            <div><b>{l.name}</b> <span style={{ fontSize: 12, color: GRAY }}>{kindLabel(l)}</span></div>
            <div style={{ fontSize: 12, color: GRAY }}>{l.hasLogo ? "Logo set" : "No logo"}</div>
          </div>
          <div style={{ fontSize: 13, marginTop: 4 }}>{l.practiceName}</div>
          {l.lines.map((x, i) => <div key={i} style={{ fontSize: 12, color: GRAY }}>{x}</div>)}
          {canManage && <div style={{ display: "flex", gap: 8, marginTop: 8, flexWrap: "wrap" }}>
            <button type="button" style={smallBtn} onClick={() => begin(l)}>Edit</button>
            <button type="button" style={smallBtn} disabled={upload.isPending} onClick={() => { setLogoFor(l.id); file.current?.click() }}>{l.hasLogo ? "Change logo" : "Add logo"}</button>
            {l.hasLogo && <button type="button" style={smallBtn} onClick={() => dropLogo.mutate(l.id)}>Remove logo</button>}
            <button type="button" style={smallBtn} onClick={() => { if (window.confirm(`Remove the letterhead "${l.name}"? Documents already issued keep what was printed on them.`)) remove.mutate(l.id) }}>Remove</button>
          </div>}
        </div>
      ))}
      {!list.isLoading && !list.isError && (list.data ?? []).length === 0 &&
        <div style={{ fontSize: 13, color: GRAY }}>No letterhead yet. Letters print the practice name only. {canManage ? "Add one to put your address and logo at the top." : "Ask an administrator to add one."}</div>}

      {editing && canManage && (
        <div role="group" aria-label="Letterhead details" style={{ border: `1px solid ${BORDER}`, borderRadius: 10, padding: 14, marginTop: 12, display: "flex", flexDirection: "column", gap: 10 }}>
          <div style={{ fontWeight: 600 }}>{editing === "new" ? "New letterhead" : "Change letterhead"}</div>
          <div><label style={lbl} htmlFor="lh-name">Name (what people choose from)</label><input id="lh-name" style={sinp} value={form.name} maxLength={120} onChange={set("name")} /></div>
          <div><label style={lbl} htmlFor="lh-branch">Branch (leave empty for the whole practice)</label><input id="lh-branch" style={sinp} value={form.branchName} maxLength={120} onChange={set("branchName")} placeholder="e.g. Northside" /></div>
          <div><label style={lbl} htmlFor="lh-practice">Practice name printed at the top</label><input id="lh-practice" style={sinp} value={form.practiceName} maxLength={120} onChange={set("practiceName")} /></div>
          <div><label style={lbl} htmlFor="lh-lines">Address, phone and practice number (one line each, up to 6)</label>
            <textarea id="lh-lines" rows={4} style={{ ...sinp, resize: "vertical" }} value={form.linesText} onChange={set("linesText")} /></div>
          {!form.branchName.trim() && <label style={{ fontSize: 13 }}><input type="checkbox" checked={makeDefault} onChange={e => setMakeDefault(e.target.checked)} /> Use as the practice-wide default</label>}
          {found.length > 0 && <ul style={{ margin: 0, paddingLeft: 18, fontSize: 12, color: GRAY }}>{found.map(p => <li key={p}>{p}</li>)}</ul>}
          <div style={{ display: "flex", gap: 8 }}>
            <button type="button" style={{ ...primaryBtn, opacity: found.length === 0 && !save.isPending ? 1 : 0.6 }} disabled={found.length > 0 || save.isPending} onClick={() => save.mutate()}>Save</button>
            <button type="button" style={smallBtn} onClick={() => setEditing(null)}>Cancel</button>
          </div>
          {editing === "new" && <div style={{ fontSize: 11, color: GRAY }}>Save first, then add the logo.</div>}
        </div>
      )}
    </div>
  )
}
