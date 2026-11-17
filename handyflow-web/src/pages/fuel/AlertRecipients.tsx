import { useEffect, useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { btnPrimary, card } from "../warehousing/whseUi"
import { toggle, userLabel, type UserOption } from "./scopeOps"
import { onlyTopics, topicSummary, type AlertTopic } from "./alertOps"

const unwrap = (r: any) => r.data?.data ?? r.data
const msg = (e: any, fb: string) => e?.response?.data?.message ?? fb

/** Choose who is told about each kind of alert, on top of administrators. */
export default function AlertRecipients({ users }: { users: UserOption[] }) {
  const qc = useQueryClient()
  const q = useQuery<AlertTopic[]>({ queryKey: ["fuel-alert-recipients"], queryFn: async () => onlyTopics(unwrap(await apiClient.get("/api/v1/fuel/alert-recipients"))) })
  const [topic, setTopic] = useState("")
  const [ids, setIds] = useState<string[]>([])
  const [err, setErr] = useState("")
  const topics = q.data ?? []

  useEffect(() => { setIds(topics.find(t => t.topic === topic)?.userIds ?? []) }, [topic, q.data])

  const save = useMutation({
    mutationFn: () => apiClient.put(`/api/v1/fuel/alert-recipients/${topic}`, { userIds: ids }),
    onSuccess: () => { setErr(""); setTopic(""); qc.invalidateQueries({ queryKey: ["fuel-alert-recipients"] }) },
    onError: (e: any) => setErr(msg(e, "Could not save")),
  })

  if (topics.length === 0) return null
  const choices = [...users, ...ids.filter(id => !users.some(u => u.id === id)).map(id => ({ id, name: userLabel(id, users) }))]

  return (
    <div style={{ ...card, padding: 16, marginTop: 14 }}>
      <h3 style={{ margin: "0 0 4px", fontSize: 14 }}>Who is told about alerts</h3>
      <p style={{ margin: "0 0 12px", fontSize: 12.5, color: "var(--hf-text-muted)" }}>
        Administrators are always told. Add people here who should be told as well. Only active users with an email address can be added.
      </p>
      {topics.map(t => (
        <div key={t.topic} style={{ display: "flex", gap: 10, alignItems: "center", padding: "8px 0", borderTop: "1px solid var(--hf-border)" }}>
          <div style={{ flex: 1 }}>
            <strong style={{ fontSize: 13 }}>{t.label}</strong>
            <div style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>{topicSummary(t, users)}</div>
          </div>
          <button onClick={() => { setErr(""); setTopic(t.topic) }} aria-label={`Change who is told about ${t.label}`}>Change</button>
        </div>
      ))}
      {topic && (
        <fieldset style={{ border: "none", padding: 0, margin: "10px 0 0" }}>
          <legend style={{ fontSize: 12.5, fontWeight: 600 }}>Also tell</legend>
          {users.length === 0 && <span style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>The list of people could not be loaded.</span>}
          {choices.map(u => (
            <label key={u.id} style={{ display: "block", fontSize: 13 }}>
              <input type="checkbox" checked={ids.includes(u.id)} onChange={() => setIds(toggle(ids, u.id))} /> {u.name}
            </label>
          ))}
          {err && <p role="alert" style={{ color: "var(--hf-danger-text)", fontSize: 12 }}>{err}</p>}
          <button style={{ ...btnPrimary, marginTop: 8 }} disabled={save.isPending} onClick={() => save.mutate()}>Save</button>
        </fieldset>
      )}
    </div>
  )
}
