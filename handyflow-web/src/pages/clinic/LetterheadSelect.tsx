import { useEffect, useState } from "react"
import { apiClient } from "../../api/client"
import { choiceLabel, type Letterhead } from "./letterheadView"

/** The practice's letterheads. With none, or if the lookup fails, the document prints on the practice default. */
export function useLetterheads(): Letterhead[] {
  const [heads, setHeads] = useState<Letterhead[]>([])
  useEffect(() => {
    let live = true
    ;(async () => {
      try {
        const r: any = await apiClient.get("/api/v1/clinic/letterheads")
        const d = r?.data?.data ?? r?.data
        if (live) setHeads(Array.isArray(d) ? d.filter((h: Letterhead) => h.active !== false) : [])
      } catch { if (live) setHeads([]) }
    })()
    return () => { live = false }
  }, [])
  return heads
}

/** Shown only when there is a real choice (more than one letterhead). Empty value means the practice default. */
export default function LetterheadSelect({ id, value, onChange, style, labelStyle }: {
  id: string; value: string; onChange: (v: string) => void
  style?: React.CSSProperties; labelStyle?: React.CSSProperties
}) {
  const heads = useLetterheads()
  if (heads.length < 2) return null
  return (
    <div>
      <label style={labelStyle} htmlFor={id}>Letterhead</label>
      <select id={id} style={style} value={value} onChange={e => onChange(e.target.value)}>
        <option value="">The practice default</option>
        {heads.map(h => <option key={h.id} value={h.id}>{choiceLabel(h)}</option>)}
      </select>
    </div>
  )
}
