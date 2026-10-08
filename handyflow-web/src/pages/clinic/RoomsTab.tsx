// src/pages/clinic/RoomsTab.tsx
// Consulting rooms. A booking can name a room, and a room holds one live booking at a time (the booking screen refuses
// a second one, with a "book anyway" override). Rooms are switched off, never deleted, so past appointments keep their room.
// Nothing is pre-filled: add your own rooms.
import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { useCan } from "./clinicAccess"

export interface Room { id: string; name: string; active: boolean }

const unwrap = (r: any) => r.data?.data ?? r.data
export const MAX_NAME = 60

/** Why the name cannot be used yet, or null. Mirrors the server's checks (duplicates are checked by the server). */
export function roomNameProblem(name: string): string | null {
  const n = name.trim().replace(/\s+/g, " ")
  if (!n) return "A room needs a name"
  if (n.length > MAX_NAME) return `The room name is too long (at most ${MAX_NAME} characters)`
  return null
}

export default function RoomsTab() {
  const qc = useQueryClient()
  const canAdmin = useCan("manageRooms")
  const [name, setName] = useState("")
  const [editing, setEditing] = useState<string | null>(null)
  const [editName, setEditName] = useState("")
  const [error, setError] = useState("")

  const list = useQuery<Room[]>({
    queryKey: ["clinic-rooms", "all"],
    queryFn: async () => unwrap(await apiClient.get("/api/v1/clinic/rooms", { params: { includeInactive: true } })),
  })
  const done = () => { setError(""); qc.invalidateQueries({ queryKey: ["clinic-rooms"] }) }
  const fail = (what: string) => (e: any) => setError(e?.response?.data?.message ?? what)
  const add = useMutation({
    mutationFn: () => apiClient.post("/api/v1/clinic/rooms", { name: name.trim() }),
    onSuccess: () => { setName(""); done() }, onError: fail("Could not add the room"),
  })
  const update = useMutation({
    mutationFn: ({ id, body }: { id: string; body: { name?: string; active?: boolean } }) => apiClient.put(`/api/v1/clinic/rooms/${id}`, body),
    onSuccess: () => { setEditing(null); done() }, onError: fail("Could not update the room"),
  })

  const field = { padding: "7px 10px", border: "1px solid var(--hf-border)", borderRadius: 8, fontSize: 13, background: "var(--hf-surface)", color: "var(--hf-text)" } as const
  const small = { padding: "5px 12px", borderRadius: 8, border: "1px solid var(--hf-border)", background: "transparent", color: "var(--hf-text-muted)", fontSize: 12, fontWeight: 600, cursor: "pointer" } as const

  return (
    <div style={{ maxWidth: 640 }}>
      <h2 style={{ margin: "0 0 4px", fontSize: 18, fontWeight: 700, color: "var(--hf-text)" }}>Rooms</h2>
      <p style={{ margin: "0 0 16px", fontSize: 13, color: "var(--hf-text-muted)" }}>
        Consulting rooms that appointments can be booked into. A room holds one booking at a time. Switching a room off keeps it on past appointments.
      </p>

      {error && <div role="alert" style={{ marginBottom: 12, fontSize: 13, color: "var(--hf-danger-text)" }}>{error}</div>}

      {canAdmin && (
        <div style={{ display: "flex", gap: 10, alignItems: "flex-end", marginBottom: 20 }}>
          <label style={{ fontSize: 12, fontWeight: 600, color: "var(--hf-text-muted)", display: "flex", flexDirection: "column", gap: 4, flex: 1 }}>
            New room
            <input aria-label="Room name" value={name} maxLength={MAX_NAME} placeholder="Room 1" onChange={e => setName(e.target.value)} style={field} />
          </label>
          <button type="button" disabled={add.isPending} onClick={() => {
            const p = roomNameProblem(name)
            if (p) { setError(p); return }
            setError(""); add.mutate()
          }} style={{ padding: "8px 16px", border: "none", borderRadius: 8, fontSize: 13, fontWeight: 600, cursor: "pointer",
            background: "var(--hf-accent)", color: "var(--hf-text-on-solid)" }}>
            {add.isPending ? "Adding..." : "Add room"}
          </button>
        </div>
      )}

      {list.isError ? (
        <div role="alert" style={{ fontSize: 13, color: "var(--hf-danger-text)" }}>Could not load the rooms.</div>
      ) : list.isLoading ? (
        <div style={{ fontSize: 13, color: "var(--hf-text-muted)" }}>Loading...</div>
      ) : (list.data ?? []).length === 0 ? (
        <div style={{ fontSize: 13, color: "var(--hf-text-muted)" }}>No rooms yet. Appointments can still be booked without a room.</div>
      ) : (
        <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
          {(list.data ?? []).map(r => (
            <div key={r.id} style={{ display: "flex", alignItems: "center", gap: 12, padding: "10px 14px", border: "1px solid var(--hf-border)", borderRadius: 10, opacity: r.active ? 1 : 0.65 }}>
              {editing === r.id ? (
                <>
                  <input aria-label={`Rename ${r.name}`} value={editName} maxLength={MAX_NAME} onChange={e => setEditName(e.target.value)} style={{ ...field, flex: 1 }} />
                  <button type="button" style={small} disabled={update.isPending} onClick={() => {
                    const p = roomNameProblem(editName)
                    if (p) { setError(p); return }
                    setError(""); update.mutate({ id: r.id, body: { name: editName.trim() } })
                  }}>Save</button>
                  <button type="button" style={small} onClick={() => setEditing(null)}>Cancel</button>
                </>
              ) : (
                <>
                  <div style={{ flex: 1 }}>
                    <div style={{ fontSize: 14, fontWeight: 600, color: "var(--hf-text)" }}>{r.name}</div>
                    {!r.active && <div style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>Switched off</div>}
                  </div>
                  {canAdmin && (
                    <>
                      <button type="button" style={small} onClick={() => { setEditing(r.id); setEditName(r.name); setError("") }}>Rename</button>
                      <button type="button" style={small} disabled={update.isPending}
                        onClick={() => update.mutate({ id: r.id, body: { active: !r.active } })}>{r.active ? "Switch off" : "Switch on"}</button>
                    </>
                  )}
                </>
              )}
            </div>
          ))}
        </div>
      )}
    </div>
  )
}
