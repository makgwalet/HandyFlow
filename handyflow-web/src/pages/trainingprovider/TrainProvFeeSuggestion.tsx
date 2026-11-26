// src/pages/trainingprovider/TrainProvFeeSuggestion.tsx
//
// What the programme's price book suggests for one enrolment today. A suggestion only: nothing is billed from here, and whoever raises an invoice
// confirms the amount or enters another with a reason.
import { useState } from "react"
import { useQuery } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { suggestionText, type Suggestion } from "./priceRules"
import { ghost, muted, unwrap } from "./tpStyle"
import { LRN } from "./tpApi"

export default function TrainProvFeeSuggestion({ enrolmentId }: { enrolmentId: string }) {
  const [open, setOpen] = useState(false)
  const { data, isLoading } = useQuery<Suggestion>({
    queryKey: ["tp-fee-suggestion", enrolmentId], enabled: open,
    queryFn: async () => unwrap<Suggestion>(await apiClient.get(`${LRN}/price-lines/suggest`, { params: { enrolmentId } })),
  })
  if (!open) return <div><button style={ghost} onClick={() => setOpen(true)}>Fee suggestion from the price book</button></div>
  return (
    <div style={{ display: "grid", gap: 4, fontSize: 13 }}>
      {isLoading || !data ? <div style={muted}>Loading…</div> : (
        <>
          <div style={muted}>{data.note}</div>
          {data.rows.map(r => <div key={r.priceLineId}>{suggestionText(r)}<div style={muted}>Source: {r.sourceNote}</div></div>)}
        </>
      )}
      <button style={{ ...ghost, justifySelf: "start" }} onClick={() => setOpen(false)}>Hide suggestion</button>
    </div>
  )
}
