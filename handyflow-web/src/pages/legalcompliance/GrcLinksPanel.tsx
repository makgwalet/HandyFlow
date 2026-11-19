// src/pages/legalcompliance/GrcLinksPanel.tsx
// Shows what a risk, control or obligation is linked to and lets a permitted user add or remove links.
//   GET /api/v1/legalcompliance/controls/links?kind=&id=  ·  POST /controls/links  ·  DELETE /controls/links/{linkId}
// Link types: RISK_CONTROL (source risk, target control), RISK_OBLIGATION (risk → obligation), CONTROL_OBLIGATION (control → obligation).
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { input, primaryBtn, ghostBtn, unwrap, errMsg } from "./lcHelpers"
import { Badge } from "./LcUi"
import { Link2, X } from "lucide-react"

interface LinkItem { linkId: string; kind: string; id: string; reference: string | null; title: string; status: string }
interface Option { id: string; label: string }
const KIND_CFG: Record<string, { color: string; bg: string }> = {
  RISK: { color: "#9A3412", bg: "#FFEDD5" }, CONTROL: { color: "#1D4ED8", bg: "#DBEAFE" }, OBLIGATION: { color: "#4338CA", bg: "#E0E7FF" },
}
const base = "/api/v1/legalcompliance/controls"

// Which kinds can be added from each kind, and the link type + direction that results.
const ADDABLE: Record<string, { kind: string; type: string; ownIsSource: boolean }[]> = {
  RISK: [{ kind: "CONTROL", type: "RISK_CONTROL", ownIsSource: true }, { kind: "OBLIGATION", type: "RISK_OBLIGATION", ownIsSource: true }],
  CONTROL: [{ kind: "RISK", type: "RISK_CONTROL", ownIsSource: false }, { kind: "OBLIGATION", type: "CONTROL_OBLIGATION", ownIsSource: true }],
  OBLIGATION: [{ kind: "RISK", type: "RISK_OBLIGATION", ownIsSource: false }, { kind: "CONTROL", type: "CONTROL_OBLIGATION", ownIsSource: false }],
}

export default function GrcLinksPanel({ kind, id, canEdit }: { kind: "RISK" | "CONTROL" | "OBLIGATION"; id: string; canEdit: boolean }) {
  const qc = useQueryClient()
  const [adding, setAdding] = useState(false)
  const [pickKind, setPickKind] = useState(ADDABLE[kind][0].kind)
  const [pickId, setPickId] = useState("")
  const [err, setErr] = useState("")

  const { data: links = [] } = useQuery<LinkItem[]>({
    queryKey: ["lc-grc-links", kind, id], queryFn: async () => unwrap(await apiClient.get(`${base}/links`, { params: { kind, id } })),
  })
  const { data: options = [] } = useQuery<Option[]>({
    queryKey: ["lc-grc-options", pickKind], enabled: adding,
    queryFn: async () => {
      if (pickKind === "RISK") return unwrap(await apiClient.get("/api/v1/legalcompliance/risks")).map((r: any) => ({ id: r.id, label: `${r.reference} — ${r.title}` }))
      if (pickKind === "CONTROL") return unwrap(await apiClient.get(base)).map((c: any) => ({ id: c.id, label: `${c.reference} — ${c.title}` }))
      return unwrap(await apiClient.get("/api/v1/legalcompliance/obligations?size=200")).map((o: any) => ({ id: o.id, label: `${o.regulationReference ?? "—"} — ${o.title}` }))
    },
  })

  const refresh = () => {
    qc.invalidateQueries({ queryKey: ["lc-grc-links"] }); qc.invalidateQueries({ queryKey: ["lc-risks"] }); qc.invalidateQueries({ queryKey: ["lc-controls"] }); qc.invalidateQueries({ queryKey: ["lc-coverage"] })
  }
  const add = useMutation({
    mutationFn: (body: any) => apiClient.post(`${base}/links`, body),
    onSuccess: () => { refresh(); setAdding(false); setPickId(""); setErr("") }, onError: (e: any) => setErr(errMsg(e, "Failed to link")),
  })
  const remove = useMutation({
    mutationFn: (linkId: string) => apiClient.delete(`${base}/links/${linkId}`), onSuccess: refresh, onError: (e: any) => setErr(errMsg(e, "Failed to unlink")),
  })

  const submit = () => {
    const spec = ADDABLE[kind].find(a => a.kind === pickKind)!
    add.mutate({ type: spec.type, sourceId: spec.ownIsSource ? id : pickId, targetId: spec.ownIsSource ? pickId : id })
  }

  return (
    <div style={{ marginTop: 10 }}>
      <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 6, fontSize: 12, fontWeight: 700, color: "#475569" }}>
        <Link2 size={13} /> Linked items
        {canEdit && !adding && <button style={{ ...ghostBtn, padding: "3px 10px", fontSize: 12 }} onClick={() => setAdding(true)}>Add link</button>}
      </div>
      {err && <div style={{ color: "#DC2626", fontSize: 12, marginBottom: 6 }}>{err}</div>}
      {links.length === 0 && <div style={{ fontSize: 12, color: "#94A3B8" }}>Nothing linked yet.</div>}
      {links.map(l => (
        <div key={l.linkId} style={{ display: "flex", alignItems: "center", gap: 8, fontSize: 12, padding: "3px 0" }}>
          <Badge text={l.kind} color={KIND_CFG[l.kind]?.color ?? "#475569"} bg={KIND_CFG[l.kind]?.bg ?? "#F1F5F9"} />
          <span style={{ color: "#0F172A" }}>{l.reference ? `${l.reference} — ` : ""}{l.title}</span>
          <span style={{ color: "#94A3B8" }}>{l.status}</span>
          {canEdit && <button aria-label="Remove link" onClick={() => { if (confirm("Remove this link? The change is kept in the audit log.")) remove.mutate(l.linkId) }}
            style={{ background: "none", border: "none", cursor: "pointer", color: "#94A3B8" }}><X size={13} /></button>}
        </div>
      ))}
      {adding && (
        <div style={{ display: "flex", gap: 8, marginTop: 8, flexWrap: "wrap" }}>
          <select style={{ ...input, width: 130 }} value={pickKind} onChange={e => { setPickKind(e.target.value); setPickId("") }}>
            {ADDABLE[kind].map(a => <option key={a.kind} value={a.kind}>{a.kind.charAt(0) + a.kind.slice(1).toLowerCase()}</option>)}
          </select>
          <select style={{ ...input, flex: 1, minWidth: 220 }} value={pickId} onChange={e => setPickId(e.target.value)}>
            <option value="">Choose…</option>
            {options.map(o => <option key={o.id} value={o.id}>{o.label}</option>)}
          </select>
          <button style={primaryBtn} disabled={!pickId || add.isPending} onClick={submit}>Link</button>
          <button style={ghostBtn} onClick={() => { setAdding(false); setErr("") }}>Cancel</button>
        </div>
      )}
    </div>
  )
}
