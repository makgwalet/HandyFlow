// Pure rules for the persistent video dock: which address is allowed in the call frame and how the dock is sized.

/** The room address from the video-room response (the API wraps results as { data: ... }; older callers read it bare). */
export function roomUrlFrom(res: any): string | null {
  const body = res?.data?.data ?? res?.data ?? res
  const url = body?.videoRoomUrl
  return typeof url === "string" && url.trim() ? url.trim() : null
}

/** Only an https address is put in the call frame. Anything else (javascript:, data:, plain http) is refused. */
export function isSafeRoomUrl(url: string | null | undefined): url is string {
  if (!url) return false
  try { return new URL(url).protocol === "https:" } catch { return false }
}

export type DockSize = "mini" | "full"
/** Both sizes keep the frame mounted: hiding it would drop the call. */
export const DOCK_DIMENSIONS: Record<DockSize, { width: number; height: number }> = {
  mini: { width: 220, height: 124 },
  full: { width: 400, height: 300 },
}

/** Is the dock already showing this appointment's call? Joining it again just opens it up. */
export const sameCall = (current: { appointmentId: string } | null, appointmentId: string) => current?.appointmentId === appointmentId
