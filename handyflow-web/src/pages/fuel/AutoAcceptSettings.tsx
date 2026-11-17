import { useEffect, useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { btnPrimary, card } from "../warehousing/whseUi"
import { asAutoAccept, autoAcceptProblem, autoAcceptSummary, type AutoAccept } from "./autoAcceptOps"

const unwrap = (r: any) => r.data?.data ?? r.data
const msg = (e: any, fb: string) => e?.response?.data?.message ?? fb

export default function AutoAcceptSettings() {
  const qc = useQueryClient()
  const q = useQuery<AutoAccept>({ queryKey: ["fuel-auto-accept"], queryFn: async () => asAutoAccept(unwrap(await apiClient.get("/api/v1/fuel/import-auto-accept"))) })
  const [shifts, setShifts] = useState(false)
  const [gauges, setGauges] = useState(false)
  const [limit, setLimit] = useState("")
  const [err, setErr] = useState("")

  useEffect(() => {
    if (!q.data) return
    setShifts(q.data.shiftsEnabled); setGauges(q.data.gaugesEnabled); setLimit(q.data.gaugeMaxDifferenceLitres == null ? "" : String(q.data.gaugeMaxDifferenceLitres))
  }, [q.data])

  const save = useMutation({
    mutationFn: () => apiClient.put("/api/v1/fuel/import-auto-accept", { shiftsEnabled: shifts, gaugesEnabled: gauges, gaugeMaxDifferenceLitres: limit.trim() === "" ? null : Number(limit) }),
    onSuccess: () => { setErr(""); qc.invalidateQueries({ queryKey: ["fuel-auto-accept"] }) },
    onError: (e: any) => setErr(msg(e, "Could not save")),
  })
  const problem = autoAcceptProblem(gauges, limit)
  if (!q.data) return <div style={{ ...card, padding: 16, marginTop: 14, fontSize: 12.5 }}>Loading automatic acceptance…</div>

  return (
    <div style={{ ...card, padding: 16, marginTop: 14 }}>
      <h3 style={{ margin: "0 0 4px", fontSize: 14 }}>Automatic acceptance</h3>
      <p style={{ margin: "0 0 8px", fontSize: 12.5, color: "var(--hf-text-muted)" }}>
        Off by default: every import waits for a person. When on, an import with no problems is accepted as soon as it arrives, using the same rules a person's acceptance uses. Anything those rules refuse stays waiting for a person.
      </p>
      <p style={{ margin: "0 0 8px", fontSize: 12.5 }}>{autoAcceptSummary(q.data)}</p>
      <label style={{ display: "block", fontSize: 13 }}><input type="checkbox" checked={shifts} onChange={e => setShifts(e.target.checked)} /> Accept clean pump/POS shifts automatically</label>
      <label style={{ display: "block", fontSize: 13, marginTop: 4 }}><input type="checkbox" checked={gauges} onChange={e => setGauges(e.target.checked)} /> Accept gauge readings close to the book level automatically</label>
      {gauges && (
        <label style={{ display: "block", fontSize: 12.5, marginTop: 6 }}>Largest difference from the book level (litres)
          <input aria-label="Largest difference in litres" inputMode="decimal" value={limit} onChange={e => setLimit(e.target.value)} style={{ display: "block", marginTop: 4, width: 140 }} />
        </label>
      )}
      {(problem || err) && <p role="alert" style={{ color: "var(--hf-danger-text)", fontSize: 12 }}>{problem ?? err}</p>}
      <button style={{ ...btnPrimary, marginTop: 8 }} disabled={save.isPending || !!problem} onClick={() => save.mutate()}>Save</button>
    </div>
  )
}
