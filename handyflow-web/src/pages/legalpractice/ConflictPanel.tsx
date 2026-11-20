// Run a conflict check for one client and the other parties to a prospective matter, and show what it found.
// Used inside the New matter dialog (where a usable check is required) and on the Conflict checks screen.
import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { toast } from "../../store/toast.store"
import { Btn, ErrorLine, Input, Pick, Pill, day, errorText, muted, pageOf } from "./kit"
import { BASE } from "./lookups"
import { CONFLICT_STATUS, MATCH_KIND, PARTY_ROLES, conflictHeadline, emptyParty, partiesRequest, usableChecks, type ConflictCheck, type PartyDraft } from "./legal.logic"

const unwrap = <T,>(r: { data?: { data?: T } | T }): T => ((r.data as { data?: T } | undefined)?.data ?? r.data) as T

export function HitList({ check }: { check: Pick<ConflictCheck, "hits" | "truncated"> }) {
  if (check.hits.length === 0 && !check.truncated) return null
  return (
    <div style={{ marginTop: 6 }}>
      {check.hits.map((h, i) => (
        <div key={i} style={{ fontSize: 13, padding: "6px 0", borderTop: i ? "1px solid var(--hf-border)" : undefined }}>
          <b>{h.subjectName}</b> matches <b>{h.matchedName}</b> <span style={muted}>({MATCH_KIND[h.matchKind] ?? h.matchKind})</span>
          {h.matchedDetail && <div style={muted}>{h.matchedDetail}</div>}
        </div>))}
      {check.truncated && <div style={{ ...muted, marginTop: 4 }}>A search word matched too many records to compare them all, so a person must review this check.</div>}
    </div>
  )
}

export default function ConflictPanel({ clientId, value, onChange }: { clientId: string; value?: string; onChange?: (checkId: string) => void }) {
  const qc = useQueryClient()
  const [parties, setParties] = useState<PartyDraft[]>([emptyParty()]), [err, setErr] = useState(""), [last, setLast] = useState<ConflictCheck | null>(null)
  const earlier = useQuery({
    queryKey: ["lp-conflict-checks", "client", clientId], enabled: !!clientId,
    queryFn: async () => pageOf<ConflictCheck>(await apiClient.get(`${BASE}/conflict-checks`, { params: { clientId, size: 25 } })),
  })
  const usable = usableChecks(earlier.data?.content ?? [], clientId)
  const run = useMutation({
    mutationFn: async () => unwrap<ConflictCheck>(await apiClient.post(`${BASE}/conflict-checks`, { clientId, parties: partiesRequest(parties) })),
    onSuccess: c => {
      setLast(c); qc.invalidateQueries({ queryKey: ["lp-conflict-checks"] })
      if (c.usable) { onChange?.(c.id); toast.success("No conflicts found") } else onChange?.("")
    },
    onError: async e => setErr(await errorText(e, "Could not run the conflict check")),
  })
  const setParty = (i: number, k: keyof PartyDraft) => (v: string) => setParties(ps => ps.map((p, j) => j === i ? { ...p, [k]: v } : p))
  if (!clientId) return <div style={muted}>Choose the client first. The conflict check searches their name and ID, and anyone else involved.</div>
  const st = last ? CONFLICT_STATUS[last.status] : null
  return (
    <div style={{ border: "1px solid var(--hf-border)", borderRadius: 8, padding: 12, margin: "8px 0" }}>
      <div style={{ fontWeight: 700, fontSize: 13, marginBottom: 6 }}>Conflict check</div>
      {onChange && usable.length > 0 && (
        <Pick label="Use a check already run for this client" value={value ?? ""} onChange={onChange} blank="Choose a check"
          options={usable.map(c => [c.id, `${day(c.runAt)} · ${CONFLICT_STATUS[c.status]?.label ?? c.status}${c.validUntil ? ` · valid until ${day(c.validUntil)}` : ""}`] as const)} />
      )}
      <div style={{ ...muted, marginBottom: 6 }}>Who else is involved? List the other side, co-parties, related parties and witnesses. The client is searched automatically.</div>
      {parties.map((p, i) => (
        <div key={i} style={{ display: "grid", gridTemplateColumns: "2fr 1.4fr 1.2fr auto", gap: 8, alignItems: "end" }}>
          <Input label={i === 0 ? "Name" : ""} value={p.name} onChange={setParty(i, "name")} maxLength={255} />
          <Input label={i === 0 ? "ID or registration no." : ""} value={p.idNumber} onChange={setParty(i, "idNumber")} maxLength={100} />
          <Pick label={i === 0 ? "Role" : ""} value={p.role} onChange={setParty(i, "role")} options={PARTY_ROLES} />
          <div style={{ paddingBottom: 12 }}>{parties.length > 1 && <Btn small kind="ghost" onClick={() => setParties(ps => ps.filter((_, j) => j !== i))}>Remove</Btn>}</div>
        </div>))}
      <div style={{ display: "flex", gap: 8, marginTop: 4 }}>
        <Btn small onClick={() => setParties(ps => ps.length < 25 ? [...ps, emptyParty()] : ps)}>Add another party</Btn>
        <Btn small kind="primary" disabled={run.isPending} onClick={() => { setErr(""); setLast(null); run.mutate() }}>{run.isPending ? "Searching…" : "Run conflict check"}</Btn>
      </div>
      {err && <ErrorLine text={err} />}
      {last && st && (
        <div style={{ marginTop: 10 }}>
          <Pill tone={st.tone}>{st.label}</Pill> <span style={{ fontSize: 13 }}>{conflictHeadline(last)}</span>
          <HitList check={last} />
        </div>)}
    </div>
  )
}
