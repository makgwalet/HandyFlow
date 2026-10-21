// src/pages/fleet/ListControls.tsx
// The search box and the "Showing 1–25 of 60 / Previous / Next" bar shared by the Fleet lists.
import { paginate } from "./listKit"

const box: React.CSSProperties = { padding: "7px 12px", border: "1px solid var(--hf-border)", borderRadius: 8, fontSize: 13, minWidth: 240, background: "var(--hf-surface)", color: "var(--hf-text)" }
const step: React.CSSProperties = { padding: "5px 12px", border: "1px solid var(--hf-border)", borderRadius: 8, background: "var(--hf-surface)", color: "var(--hf-text)" }

export function SearchBox({ value, onChange, label, placeholder }: { value: string; onChange: (q: string) => void; label: string; placeholder: string }) {
  return <input aria-label={label} placeholder={placeholder} value={value} onChange={e => onChange(e.target.value)} style={box} />
}

export function Pager({ paged, onPage }: { paged: ReturnType<typeof paginate>; onPage: (page: number) => void }) {
  if (paged.total === 0) return null
  const first = paged.page === 0, last = paged.page >= paged.pages - 1
  return (
    <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginTop: 10, fontSize: 12.5, color: "var(--hf-text-muted)", flexWrap: "wrap", gap: 8 }}>
      <span aria-label="Showing">Showing {paged.from}–{paged.to} of {paged.total}</span>
      {paged.pages > 1 && <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
        <button type="button" disabled={first} onClick={() => onPage(paged.page - 1)} style={{ ...step, cursor: first ? "default" : "pointer" }}>Previous</button>
        <span>Page {paged.page + 1} of {paged.pages}</span>
        <button type="button" disabled={last} onClick={() => onPage(paged.page + 1)} style={{ ...step, cursor: last ? "default" : "pointer" }}>Next</button>
      </div>}
    </div>)
}
