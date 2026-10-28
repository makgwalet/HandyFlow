// src/pages/training/RequiredTrainingTab.tsx
//
// Required training: which certificates a role must hold (a course, and optionally a job title and a department), and a matrix of who holds
// them. Every status is worked out by the server; this lays them out, lists the people with something to put right first, and lets an
// administrator add and remove requirements.
import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { card, errorText, input, label, plain, primary, td, th, unwrap } from "../hr/letters.shared"
import { Chip, Notice, Section } from "../hr/ErParts"
import {
  cellText, requirementBody, requirementProblem, STATUS_TONE, summaryText, visibleRows, type Matrix, type Requirement,
} from "../hr/trainingRequired.logic"
import type { CourseResponse } from "./TrainingCoursesTab"

const BASE = "/api/v1/training"
interface CoursePage { content: CourseResponse[] }

export default function RequiredTrainingTab() {
  const qc = useQueryClient()
  const [courseId, setCourseId] = useState("")
  const [jobTitle, setJobTitle] = useState("")
  const [department, setDepartment] = useState("")
  const [onlyGaps, setOnlyGaps] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const requirements = useQuery<Requirement[]>({ queryKey: ["training-requirements"], queryFn: async () => unwrap<Requirement[]>(await apiClient.get(`${BASE}/requirements`)) ?? [] })
  const matrix = useQuery<Matrix>({ queryKey: ["training-matrix"], queryFn: async () => unwrap<Matrix>(await apiClient.get(`${BASE}/matrix`)) })
  const courses = useQuery<CoursePage>({
    queryKey: ["training-courses", "ACTIVE", "for-requirements"],
    queryFn: async () => (await apiClient.get(`${BASE}/courses`, { params: { status: "ACTIVE", size: 200 } })).data,
  })
  const certified = (courses.data?.content ?? []).filter(c => c.certificationOffered)
  const refresh = () => { qc.invalidateQueries({ queryKey: ["training-requirements"] }); qc.invalidateQueries({ queryKey: ["training-matrix"] }) }

  const add = useMutation({
    mutationFn: async () => apiClient.post(`${BASE}/requirements`, requirementBody(courseId, jobTitle, department)),
    onSuccess: () => { setError(null); setCourseId(""); setJobTitle(""); setDepartment(""); refresh() },
    onError: e => setError(errorText(e, "The requirement could not be saved.")),
  })
  const remove = useMutation({
    mutationFn: async (id: string) => apiClient.delete(`${BASE}/requirements/${id}`),
    onSuccess: () => { setError(null); refresh() },
    onError: e => setError(errorText(e, "The requirement could not be removed.")),
  })
  const problem = requirementProblem(courseId, jobTitle, department)
  const m = matrix.data
  const rows = m ? visibleRows(m.rows, onlyGaps) : []

  return (
    <div>
      <p style={{ margin: "0 0 14px", fontSize: 13, color: "var(--hf-text-muted)" }}>
        Say which certificates people must hold, by job title and department. Leave both empty to ask it of everyone. Only courses that issue a certificate can be required.
      </p>
      {error && <Notice kind="error">{error}</Notice>}

      <Section title="Requirements">
        <form aria-label="Add requirement" onSubmit={e => { e.preventDefault(); if (!problem) add.mutate() }}
          style={{ display: "flex", gap: 10, alignItems: "end", flexWrap: "wrap", marginBottom: 12 }}>
          <div style={{ minWidth: 220 }}>
            <label htmlFor="req-course" style={label}>Course</label>
            <select id="req-course" style={input} value={courseId} onChange={e => setCourseId(e.target.value)}>
              <option value="">Choose a course…</option>
              {certified.map(c => <option key={c.id} value={c.id}>{c.title}</option>)}
            </select>
          </div>
          <div style={{ minWidth: 180 }}>
            <label htmlFor="req-title" style={label}>Job title (optional)</label>
            <input id="req-title" style={input} value={jobTitle} onChange={e => setJobTitle(e.target.value)} />
          </div>
          <div style={{ minWidth: 180 }}>
            <label htmlFor="req-dept" style={label}>Department (optional)</label>
            <input id="req-dept" style={input} value={department} onChange={e => setDepartment(e.target.value)} />
          </div>
          <button type="submit" style={primary} disabled={!!problem || add.isPending}>Add requirement</button>
        </form>
        <div style={card}>
          <table style={{ width: "100%", borderCollapse: "collapse" }}>
            <thead><tr><th style={th}>Course</th><th style={th}>Required of</th><th style={th} /></tr></thead>
            <tbody>
              {requirements.isLoading && <tr><td style={td} colSpan={3}>Loading…</td></tr>}
              {requirements.isError && <tr><td style={td} colSpan={3}>{errorText(requirements.error, "Requirements could not be loaded.")}</td></tr>}
              {!requirements.isLoading && !requirements.isError && (requirements.data ?? []).length === 0 && <tr><td style={td} colSpan={3}>Nothing is required yet.</td></tr>}
              {(requirements.data ?? []).map(r => (
                <tr key={r.id}>
                  <td style={td}>{r.courseTitle}</td>
                  <td style={td}>{r.appliesTo}</td>
                  <td style={td}><button style={plain} disabled={remove.isPending} onClick={() => remove.mutate(r.id)} aria-label={`Remove requirement ${r.courseTitle} for ${r.appliesTo}`}>Remove</button></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </Section>

      <Section title="Who holds what">
        {matrix.isError && <Notice kind="error">{errorText(matrix.error, "The matrix could not be loaded.")}</Notice>}
        {m && (
          <>
            <div style={{ display: "flex", gap: 14, alignItems: "center", flexWrap: "wrap", marginBottom: 10 }}>
              <span style={{ fontSize: 13 }} aria-live="polite">{summaryText(m.summary)}</span>
              <label style={{ fontSize: 13, display: "flex", gap: 6, alignItems: "center" }}>
                <input type="checkbox" checked={onlyGaps} onChange={e => setOnlyGaps(e.target.checked)} /> Only people with something to put right
              </label>
            </div>
            <div style={{ ...card, overflowX: "auto" }}>
              <table style={{ width: "100%", borderCollapse: "collapse" }}>
                <thead>
                  <tr><th style={th}>Person</th>{m.columns.map(c => <th key={c.courseId} style={th}>{c.courseTitle}</th>)}</tr>
                </thead>
                <tbody>
                  {rows.length === 0 && <tr><td style={td} colSpan={m.columns.length + 1}>{onlyGaps ? "Nobody has anything to put right." : "Nobody is required to hold a certificate yet."}</td></tr>}
                  {rows.map(r => (
                    <tr key={r.employeeId}>
                      <td style={td}>{r.name}<div style={{ color: "var(--hf-text-muted)" }}>{[r.jobTitle, r.department].filter(Boolean).join(" · ")}</div></td>
                      {r.cells.map((c, i) => (
                        <td key={m.columns[i].courseId} style={td}>
                          {c ? <Chip text={cellText(c)} color={STATUS_TONE[c.status].color} bg={STATUS_TONE[c.status].bg} /> : <span style={{ color: "var(--hf-text-faint)" }}>Not required</span>}
                        </td>
                      ))}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </>
        )}
      </Section>
    </div>
  )
}
