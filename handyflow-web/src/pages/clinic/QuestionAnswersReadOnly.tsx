// src/pages/clinic/QuestionAnswersReadOnly.tsx
// Read-only view of the question-library answers stored on a consultation. Used on signed consultations
// and in the doctor's handoff queue. Labels come from the group when it is still served; otherwise the
// stored code is shown, so a retired group never hides what was recorded.
import { useQuery } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { formatAnswer, type AnswerValue, type QuestionOption } from "./questionForm.logic"

interface ServedQuestion { code: string; label: string; answerType: string; options?: QuestionOption[] }
interface ServedGroup { code: string; name: string; questions: ServedQuestion[] }
interface StoredGroup { version?: number; answers?: Record<string, AnswerValue> }

const unwrap = (r: any) => r.data?.data ?? r.data

export default function QuestionAnswersReadOnly({ consultationId }: { consultationId: string }) {
  const { data } = useQuery<{ code: string; stored: StoredGroup; group: ServedGroup | null }[]>({
    queryKey: ["clinic-form-data-readonly", consultationId],
    queryFn: async () => {
      const fd = unwrap(await apiClient.get(`/api/v1/clinic/consultations/${consultationId}/form-data`))
      const groups: Record<string, StoredGroup> = fd?.groups ?? {}
      const out = []
      for (const [code, stored] of Object.entries(groups)) {
        let group: ServedGroup | null = null
        try { group = unwrap(await apiClient.get(`/api/v1/clinic/question-groups/${code}`)) } catch { /* no longer served */ }
        out.push({ code, stored, group })
      }
      return out
    },
  })
  const withAnswers = (data ?? []).filter(g => Object.keys(g.stored.answers ?? {}).length > 0)
  if (withAnswers.length === 0) return null

  return (
    <div style={{ marginTop: 16 }}>
      <div style={{ fontSize: 10, fontWeight: 700, color: "var(--hf-text-faint)", letterSpacing: "0.06em", marginBottom: 8 }}>
        QUESTIONNAIRE ANSWERS
      </div>
      {withAnswers.map(g => (
        <div key={g.code} style={{ marginBottom: 10 }}>
          <div style={{ fontSize: 12, fontWeight: 700, color: "var(--hf-text)", marginBottom: 4 }}>
            {g.group?.name ?? g.code}{g.stored.version ? ` (v${g.stored.version})` : ""}
          </div>
          {Object.entries(g.stored.answers ?? {}).map(([qCode, value]) => {
            const q = g.group?.questions.find(x => x.code === qCode)
            const text = formatAnswer(q?.answerType ?? "TEXT", value, q?.options ?? [])
            if (text === "") return null
            return (
              <div key={qCode} style={{ display: "flex", gap: 8, fontSize: 13, padding: "2px 0" }}>
                <span style={{ color: "var(--hf-text-muted)", minWidth: 160 }}>{q?.label ?? qCode}</span>
                <span style={{ color: "var(--hf-text)", whiteSpace: "pre-wrap" }}>{text}</span>
              </div>
            )
          })}
        </div>
      ))}
    </div>
  )
}
