// Pure helpers for the Control Room alarm banner and sound.

export interface AlarmLike {
  id: string
  source: string
  severity: string
  status: string
  description?: string | null
  createdAt: string
}

/** Poll fast enough that a panic is seen within seconds. */
export const ALARM_POLL_MS = 5000
/** How often the siren repeats while something is unacknowledged. */
export const BEEP_EVERY_MS = 3000
export const SOUND_KEY = "hf.controlroom.sound"

/** A NEW event that is CRITICAL or a duress press needs a person now. */
export function needsAttention(e: AlarmLike): boolean {
  return e.status === "NEW" && (e.severity === "CRITICAL" || e.source === "DURESS")
}

/** Unacknowledged urgent events, duress first, then oldest first. */
export function attentionList<T extends AlarmLike>(events: T[]): T[] {
  return events.filter(needsAttention).sort((a, b) => {
    const da = a.source === "DURESS" ? 0 : 1
    const db = b.source === "DURESS" ? 0 : 1
    if (da !== db) return da - db
    return a.createdAt.localeCompare(b.createdAt)
  })
}

export function bannerText(list: AlarmLike[]): string {
  if (list.length === 0) return ""
  const duress = list.filter(e => e.source === "DURESS").length
  const first = duress > 0 ? "PANIC pressed" : "CRITICAL alarm"
  const more = list.length > 1 ? ` (+${list.length - 1} more)` : ""
  return `${first} — not yet acknowledged${more}`
}

/** Server message if there is one, otherwise the fallback. */
export function errorMessage(e: unknown, fallback: string): string {
  const m = (e as { response?: { data?: { message?: unknown } } } | null)?.response?.data?.message
  return typeof m === "string" && m.trim() ? m : fallback
}

export function readSoundPref(): boolean {
  try { return localStorage.getItem(SOUND_KEY) === "1" } catch { return false }
}
export function writeSoundPref(on: boolean): void {
  try { localStorage.setItem(SOUND_KEY, on ? "1" : "0") } catch { /* private mode: keep in memory only */ }
}

/** Two-tone beep. Returns false if the browser refused audio. */
export function playBeep(): boolean {
  try {
    const Ctx = window.AudioContext ?? (window as unknown as { webkitAudioContext?: typeof AudioContext }).webkitAudioContext
    if (!Ctx) return false
    const ctx = new Ctx()
    const tone = (freq: number, start: number) => {
      const o = ctx.createOscillator(); const g = ctx.createGain()
      o.frequency.value = freq; o.connect(g); g.connect(ctx.destination)
      g.gain.setValueAtTime(0.25, ctx.currentTime + start)
      g.gain.exponentialRampToValueAtTime(0.001, ctx.currentTime + start + 0.35)
      o.start(ctx.currentTime + start); o.stop(ctx.currentTime + start + 0.4)
    }
    tone(880, 0); tone(660, 0.4)
    setTimeout(() => { try { void ctx.close() } catch { /* ignore */ } }, 1200)
    return true
  } catch { return false }
}
