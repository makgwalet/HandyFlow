// Small helpers for the training screens.

/** The reason the server gave, or a fallback. The server words its refusals for people (for example "Record attendance for the 2 people still shown as enrolled…"). */
export function apiMessage(err: unknown, fallback: string): string {
  const m = (err as { response?: { data?: { message?: unknown } } } | null)?.response?.data?.message
  return typeof m === "string" && m.trim() ? m : fallback
}
