// The four traffic-light tones the Fleet list and dashboards share, and their colours.
export type Tone = "ok" | "warn" | "bad" | "none"
export const TONE_COLOUR: Record<Tone, string> = { ok: "var(--hf-success-text-strong)", warn: "var(--hf-warning-text)", bad: "var(--hf-danger-text)", none: "var(--hf-text-faint)" }
