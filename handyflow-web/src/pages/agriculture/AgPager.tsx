// src/pages/agriculture/AgPager.tsx
import type React from "react"
import { ChevronLeft, ChevronRight } from "lucide-react"
import { lastPage } from "./agPaging.logic"

/** Previous / next with the page number. Hidden when everything fits on one page. */
export default function AgPager({ page, total, size, onPage }: { page: number; total: number; size: number; onPage: (p: number) => void }) {
  const last = lastPage(total, size)
  if (last === 0) return null
  return (
    <div style={{ display: "flex", alignItems: "center", justifyContent: "flex-end", gap: 8, marginTop: 10 }}>
      <button type="button" disabled={page <= 0} onClick={() => onPage(page - 1)} style={{ ...btn, opacity: page <= 0 ? 0.5 : 1 }} aria-label="Previous page">
        <ChevronLeft size={13} style={{ verticalAlign: -2 }} />Previous
      </button>
      <span style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>Page {page + 1} of {last + 1}</span>
      <button type="button" disabled={page >= last} onClick={() => onPage(page + 1)} style={{ ...btn, opacity: page >= last ? 0.5 : 1 }} aria-label="Next page">
        Next<ChevronRight size={13} style={{ verticalAlign: -2 }} />
      </button>
    </div>
  )
}

const btn: React.CSSProperties = { padding: "6px 10px", borderRadius: 7, border: "1px solid var(--hf-border)", background: "var(--hf-surface)", color: "var(--hf-text-muted)", fontSize: 12, fontWeight: 600, cursor: "pointer" }
