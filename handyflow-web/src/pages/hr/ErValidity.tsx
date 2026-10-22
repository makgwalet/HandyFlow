// An earlier warning that was recorded before warnings had a validity. Someone who may decide says how long it stays in force.
import { useState } from "react"
import { useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { fmtDate, words } from "./er.logic"
import { errorText, input, plain, type WarningView } from "./er.shared"

const BASE = "/api/v1/hr/employee-relations"

export default function ValidityRow({ w, canSet }: { w: WarningView; canSet: boolean }) {
  const qc = useQueryClient()
  const [months, setMonths] = useState("")
  const [error, setError] = useState<string | null>(null)
  const n = Number(months)
  const valid = Number.isInteger(n) && n >= 1 && n <= 60
  const m = useMutation({
    mutationFn: async () => apiClient.post(`${BASE}/cases/${w.caseId}/warning-validity`, { months: n }),
    onSuccess: () => { setError(null); qc.invalidateQueries({ queryKey: ["er-case"] }); qc.invalidateQueries({ queryKey: ["er-cases"] }) },
    onError: e => setError(errorText(e, "That could not be saved.")),
  })
  return (
    <li>
      {words(w.sanction)} ({w.number}), given {fmtDate(w.issuedOn)}
      {canSet && (
        <span style={{ marginLeft: 10, display: "inline-flex", gap: 6, alignItems: "center" }}>
          <input aria-label={`Months valid for ${w.number}`} type="number" min={1} max={60} value={months} onChange={e => setMonths(e.target.value)}
            style={{ ...input, width: 70, padding: "3px 6px" }} />
          <button type="button" disabled={!valid || m.isPending} onClick={() => m.mutate()} style={plain}>Set validity</button>
          {error && <span role="alert" style={{ color: "var(--hf-danger-text)", fontSize: 12 }}>{error}</span>}
        </span>
      )}
    </li>
  )
}
