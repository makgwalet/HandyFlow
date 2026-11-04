// A small ring chart with the total in the middle and a legend beside it.
import { color } from "../accountant-portal/portal-theme"
import { donutArcs, type Slice } from "./portalStats"

export function Donut({ slices, total }: { slices: Slice[]; total: number }) {
  const R = 46, arcs = donutArcs(slices, R)
  return (
    <div style={{ display: "flex", alignItems: "center", gap: 24, flexWrap: "wrap" }}>
      <svg width="132" height="132" viewBox="0 0 132 132" role="img" aria-label={`${total} requests in all`}>
        <circle cx="66" cy="66" r={R} fill="none" stroke="var(--hf-surface-sunken)" strokeWidth="16" />
        {arcs.map(a => <circle key={a.key} cx="66" cy="66" r={R} fill="none" stroke={a.colour} strokeWidth="16" strokeDasharray={`${a.dash} ${a.gap}`} strokeDashoffset={a.offset} transform="rotate(-90 66 66)" />)}
        <text x="66" y="62" textAnchor="middle" fontSize="11" fill="var(--hf-text-muted)" fontWeight="700">Total</text>
        <text x="66" y="82" textAnchor="middle" fontSize="22" fill="var(--hf-text)" fontWeight="800">{total}</text>
      </svg>
      <div style={{ display: "grid", gap: 8, fontSize: 13 }}>
        {slices.map(s => <div key={s.key} style={{ display: "flex", alignItems: "center", gap: 8, color: color.slate }}>
          <span style={{ width: 10, height: 10, borderRadius: "50%", background: s.colour }} /><span style={{ minWidth: 86 }}>{s.label}</span><b style={{ color: color.ink }}>{s.count}</b>
        </div>)}
      </div>
    </div>
  )
}
