// src/components/ui/ProgressRing.tsx
//
// A small ring showing "value of max" with the count in the middle. A count, not a score: it never shows a percentage the data cannot justify.
interface ProgressRingProps {
  value: number
  max: number
  tone?: "ok" | "warn" | "bad" | "neutral"
  size?: number
  label: string                 // spoken description, e.g. "4 of 6 sections ready"
  showValue?: boolean           // the count in the middle; turn off when the same number is written beside the ring
}

const TONES = { ok: "var(--hf-success)", warn: "var(--hf-warning)", bad: "var(--hf-danger)", neutral: "var(--hf-text-faint)" }

export default function ProgressRing({ value, max, tone = "ok", size = 48, label, showValue = true }: ProgressRingProps) {
  const stroke = 5
  const r = (size - stroke) / 2
  const c = 2 * Math.PI * r
  const share = max <= 0 ? 0 : Math.min(1, Math.max(0, value / max))
  return (
    <svg width={size} height={size} viewBox={`0 0 ${size} ${size}`} role="img" aria-label={label} style={{ flexShrink: 0 }}>
      <circle cx={size / 2} cy={size / 2} r={r} fill="none" stroke="var(--hf-surface-strong)" strokeWidth={stroke} />
      <circle
        cx={size / 2} cy={size / 2} r={r} fill="none" stroke={TONES[tone]} strokeWidth={stroke} strokeLinecap="round"
        strokeDasharray={`${c * share} ${c}`} transform={`rotate(-90 ${size / 2} ${size / 2})`}
      />
      {showValue && <text x="50%" y="50%" textAnchor="middle" dominantBaseline="central" fontSize={size * 0.3} fontWeight={800} fill="var(--hf-text)">{value}</text>}
    </svg>
  )
}
