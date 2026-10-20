// src/pages/fleet/FleetBars.tsx
//
// Small drawing pieces shared by the Fleet list and dashboards: a labelled horizontal bar and a column chart. Plain CSS, no chart library.
// They draw what the logic files give them; the rules (thresholds, tones, scales) are not decided here.
import { TONE_COLOUR, type Tone } from "./fleetTones"

/** A thin bar filled to `fraction` (0..1) in the tone's colour, with its meaning as text beside it. */
export function Meter({ fraction, tone, text, label }: { fraction: number; tone: Tone; text: string; label: string }) {
  const pct = Math.round(Math.max(0, Math.min(1, fraction)) * 100)
  return (
    <div role="img" aria-label={`${label}: ${text}`} style={{ minWidth: 130 }}>
      <div style={{ height: 6, background: "var(--hf-surface-sunken)", borderRadius: 99 }}>
        <div data-fraction={pct} style={{ height: "100%", width: `${pct}%`, background: TONE_COLOUR[tone === "none" ? "ok" : tone], opacity: tone === "none" ? 0.25 : 1, borderRadius: 99 }} />
      </div>
      <div style={{ fontSize: 11, color: TONE_COLOUR[tone], marginTop: 3 }}>{text}</div>
    </div>)
}

/** A row in a ranked bar list: name, bar scaled against the largest value, and the value as text. */
export function BarRow({ name, fraction, tone = "ok", valueText, note }: { name: string; fraction: number; tone?: Tone; valueText: string; note?: string }) {
  const pct = Math.round(Math.max(0, Math.min(1, fraction)) * 100)
  return (
    <div aria-label={name} style={{ marginBottom: 8 }}>
      <div style={{ display: "flex", justifyContent: "space-between", fontSize: 12.5 }}><span>{name}{note ? <span style={{ color: "var(--hf-text-muted)" }}> · {note}</span> : null}</span><strong>{valueText}</strong></div>
      <div style={{ height: 6, background: "var(--hf-surface-sunken)", borderRadius: 99 }}><div data-fraction={pct} style={{ height: "100%", width: `${pct}%`, background: TONE_COLOUR[tone], borderRadius: 99 }} /></div>
    </div>)
}

/** Columns for a short series (months). Heights are relative to the tallest; every column carries its value as text. */
export function Columns({ items, format, label }: { items: { key: string; label: string; value: number }[]; format: (n: number) => string; label: string }) {
  const max = Math.max(0, ...items.map(i => i.value))
  return (
    <div role="img" aria-label={label} style={{ display: "flex", gap: 8, alignItems: "flex-end", height: 130 }}>
      {items.map(i => (
        <div key={i.key} aria-label={`${i.label}: ${format(i.value)}`} style={{ flex: 1, textAlign: "center", display: "flex", flexDirection: "column", justifyContent: "flex-end", height: "100%" }}>
          <div style={{ fontSize: 10.5, color: "var(--hf-text-muted)" }}>{format(i.value)}</div>
          <div data-value={i.value} style={{ height: max > 0 ? `${Math.max(i.value > 0 ? 3 : 0, (i.value / max) * 90)}px` : 0, background: "var(--hf-accent)", borderRadius: "4px 4px 0 0", margin: "2px 4px 0" }} />
          <div style={{ fontSize: 11, marginTop: 3 }}>{i.label}</div>
        </div>))}
    </div>)
}
