// Visits: the patient's consultation history, newest first, with the notes, vitals, medicines, addenda and the people
// involved. Starting or resuming a consultation opens the consultation page. A signed visit is read-only: corrections and
// late medicines are added as addenda, never by changing the original.
import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { ChevronDown, ChevronUp, PlayCircle, Stethoscope } from "lucide-react"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"
import { fmtDay, fmtTimeOfDay } from "./briefing"
import PrescriptionForm from "./PrescriptionForm"
import ReferralLetterModal from "./ReferralLetterModal"
import ModalShell from "./ModalShell"
import { SickNoteModal } from "./QuickActionsCard"
import { startLabel, useStartConsultation } from "./useStartConsultation"
import { BORDER, Empty, GRAY, LIGHT, RED_TEXT, btnPrimary, downloadPdf, lbl, sinp } from "./patientFile.shared"
import { primaryBtn, smallBtn } from "./OverviewCard"
import { headline, isAmendable, isOpenVisit, resumeProblem, rxLine, statusOf, vitalLines, type Visit } from "./visitView"

const unwrap = (r: any) => r?.data?.data ?? r?.data
const TONE = { ok: ["var(--hf-success-soft)", "var(--hf-success-text-strong)"], warn: ["var(--hf-warning-soft)", "var(--hf-warning-text)"],
  info: ["var(--hf-info-soft)", "var(--hf-info-text)"], muted: ["var(--hf-surface-sunken)", "var(--hf-text-muted)"] } as const

export default function PatientVisitsTab({ patientId, appointments, defaultPractitionerId, onStartSession }: {
  patientId: string; appointments: any[]; defaultPractitionerId?: string; onStartSession: (appt: { id: string }) => void
}) {
  const qc = useQueryClient()
  const canAmend = usePermission("CLINIC_CONSULTATION_AMEND")
  const canRx = usePermission("CLINIC_PRESCRIPTION_CREATE")
  const canSick = usePermission("CLINIC_SICK_NOTE_SIGN")
  const canRef = usePermission("CLINIC_REFERRAL_SIGN")
  const key = ["pf-visits", patientId]
  const { data: visits = [], isLoading, isError } = useQuery<Visit[]>({ queryKey: key, retry: false,
    queryFn: async () => unwrap(await apiClient.get(`/api/v1/clinic/patients/${patientId}/visits`)) ?? [] })
  const { plan, begin, error } = useStartConsultation({ patientId, appointments, defaultPractitionerId, onStartSession })
  const hasOpen = visits.some(v => isOpenVisit(v.status))
  const [openId, setOpenId] = useState<string | null | undefined>(undefined)
  const expanded = openId === undefined ? visits[0]?.id ?? null : openId
  const [modal, setModal] = useState<{ kind: "addendum" | "rx" | "sick" | "referral"; visit: Visit } | null>(null)
  const done = () => { setModal(null); qc.invalidateQueries({ queryKey: key }) }

  return (
    <div>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 16, gap: 12, flexWrap: "wrap" }}>
        <div style={{ fontSize: 15, fontWeight: 700, color: "var(--hf-text)" }}>{visits.length} visit{visits.length !== 1 ? "s" : ""}</div>
        <button type="button" onClick={() => begin.mutate()} disabled={begin.isPending} style={{ ...btnPrimary, opacity: begin.isPending ? 0.7 : 1 }}>
          <PlayCircle size={14} /> {startLabel(plan.kind, hasOpen, begin.isPending)}
        </button>
      </div>
      {error && <div role="alert" style={{ fontSize: 13, color: RED_TEXT, marginBottom: 10 }}>{error}</div>}

      {isLoading ? <div style={{ color: GRAY, fontSize: 13 }}>Loading visits…</div>
        : isError ? <div role="alert" style={{ color: RED_TEXT, fontSize: 13 }}>The visits could not be loaded.</div>
        : visits.length === 0 ? <Empty icon={Stethoscope} msg="No visits yet"><div style={{ fontSize: 13, color: GRAY, marginTop: 4 }}>Start a consultation to record the first one.</div></Empty>
        : (
          <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
            {visits.map(v => {
              const open = expanded === v.id, st = statusOf(v.status), tone = TONE[st.tone], vit = vitalLines(v), problem = resumeProblem(v)
              return (
                <div key={v.id} style={{ border: `1px solid ${BORDER}`, borderRadius: 12, overflow: "hidden", background: "var(--hf-surface)" }}>
                  <div style={{ display: "flex", alignItems: "center", gap: 12, padding: "12px 16px", flexWrap: "wrap", background: open ? LIGHT : undefined }}>
                    <button type="button" aria-expanded={open} aria-label={`${open ? "Collapse" : "Expand"} visit of ${fmtDay(v.consultedAt)}`}
                      onClick={() => setOpenId(open ? null : v.id)} style={{ display: "flex", alignItems: "center", gap: 12, flex: "1 1 320px", minWidth: 0, textAlign: "left", background: "none", border: "none", cursor: "pointer", padding: 0 }}>
                      <div style={{ width: 84, flexShrink: 0 }}>
                        <div style={{ fontSize: 13, fontWeight: 700, color: "var(--hf-text)" }}>{fmtDay(v.consultedAt)}</div>
                        <div style={{ fontSize: 11, color: GRAY }}>{fmtTimeOfDay(v.consultedAt)}</div>
                      </div>
                      <div style={{ minWidth: 0 }}>
                        <div style={{ fontSize: 14, fontWeight: 700, color: "var(--hf-text)", overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>{headline(v)}</div>
                        <div style={{ fontSize: 12, color: GRAY }}>{v.diagnosis && v.chiefComplaint ? `${v.chiefComplaint} · ` : ""}{v.doctorName ? `Dr ${v.doctorName}` : "No practitioner recorded"}</div>
                      </div>
                      {open ? <ChevronUp size={15} style={{ color: GRAY, flexShrink: 0 }} /> : <ChevronDown size={15} style={{ color: GRAY, flexShrink: 0 }} />}
                    </button>
                    <span style={{ background: tone[0], color: tone[1], padding: "2px 10px", borderRadius: 20, fontSize: 11, fontWeight: 700 }}>{st.label}</span>
                    {v.addenda.length > 0 && <span style={{ fontSize: 11, color: GRAY }}>{v.addenda.length} addend{v.addenda.length === 1 ? "um" : "a"}</span>}
                  </div>

                  {open && (
                    <div style={{ padding: "14px 16px", borderTop: `1px solid ${BORDER}` }}>
                      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(300px, 1fr))", gap: 18 }}>
                        <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
                          <Field k="Reason for visit" v={v.chiefComplaint} />
                          <Field k="History" v={v.history} />
                          <Field k="Examination" v={v.examination} />
                          <Field k="Diagnosis" v={v.diagnosis}>
                            {(v.icd10Codes ?? []).length > 0 && <div style={{ display: "flex", gap: 6, flexWrap: "wrap", marginTop: 4 }}>{v.icd10Codes!.map(c =>
                              <span key={c} style={{ background: "var(--hf-info-soft)", color: "var(--hf-info-text)", padding: "1px 8px", borderRadius: 6, fontSize: 11, fontWeight: 700 }}>{c}</span>)}</div>}
                          </Field>
                          <Field k="Treatment plan" v={v.treatmentPlan} />
                          <Field k="Follow-up" v={v.followUpDays != null ? `in ${v.followUpDays} ${v.followUpDays === 1 ? "day" : "days"}` : null} />
                        </div>
                        <div style={{ display: "flex", flexDirection: "column", gap: 12 }}>
                          <Block title="Vitals">
                            {vit.length === 0 ? <Muted>None taken at this visit.</Muted> : (
                              <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(86px, 1fr))", gap: 6 }}>
                                {vit.map(([k, val]) => <div key={k} style={{ background: "var(--hf-surface-muted)", border: "1px solid var(--hf-border-subtle)", borderRadius: 8, padding: "5px 8px" }}>
                                  <div style={{ fontSize: 10, fontWeight: 700, color: GRAY }}>{k}</div><div style={{ fontSize: 13, fontWeight: 700 }}>{val}</div></div>)}
                              </div>)}
                          </Block>
                          <Block title="Medicines prescribed">
                            {v.prescriptions.length === 0 ? <Muted>None.</Muted> : v.prescriptions.map(r => (
                              <div key={r.id} style={{ fontSize: 13, padding: "3px 0" }}>
                                <b>{r.medicationName}</b>{r.dispensed && <span style={{ marginLeft: 6, fontSize: 10, fontWeight: 700, color: "var(--hf-success-text-strong)" }}>DISPENSED</span>}
                                <div style={{ fontSize: 12, color: GRAY }}>{rxLine(r)}</div>
                                {r.instructions && <div style={{ fontSize: 12, color: GRAY, fontStyle: "italic" }}>{r.instructions}</div>}
                              </div>))}
                          </Block>
                          <Block title="People involved">
                            {v.team.length === 0 ? <Muted>Not recorded for this visit.</Muted> : v.team.map(m => (
                              <div key={m.role + m.name} style={{ display: "flex", gap: 8, fontSize: 13, padding: "2px 0" }}>
                                <span style={{ width: 96, flexShrink: 0, color: GRAY, fontSize: 12 }}>{m.role}</span><span style={{ fontWeight: 600 }}>{m.name}</span>
                                {m.at && <span style={{ color: GRAY, fontSize: 11 }}>{fmtDay(m.at)} {fmtTimeOfDay(m.at)}</span>}
                              </div>))}
                          </Block>
                        </div>
                      </div>

                      {v.addenda.length > 0 && (
                        <div style={{ marginTop: 14 }}>
                          <Block title="Addenda (added after signing)">
                            {v.addenda.map(a => (
                              <div key={a.id} style={{ padding: "6px 0", borderTop: `1px solid ${BORDER}` }}>
                                <div style={{ fontSize: 11, color: GRAY }}>{fmtDay(a.createdAt)} {fmtTimeOfDay(a.createdAt)} · {a.authorName ?? "Author not recorded"}</div>
                                <div style={{ fontSize: 13, whiteSpace: "pre-wrap" }}>{a.text}</div>
                              </div>))}
                          </Block>
                        </div>)}

                      <div style={{ display: "flex", gap: 8, flexWrap: "wrap", marginTop: 14, paddingTop: 12, borderTop: `1px solid ${BORDER}` }}>
                        {isOpenVisit(v.status) && (
                          <button type="button" disabled={!!problem} title={problem ?? undefined} style={{ ...primaryBtn, opacity: problem ? 0.5 : 1 }}
                            onClick={() => v.appointmentId && onStartSession({ id: v.appointmentId })}>Resume consultation</button>)}
                        {isAmendable(v.status) && canAmend && <button type="button" style={smallBtn} onClick={() => setModal({ kind: "addendum", visit: v })}>Add addendum</button>}
                        {isAmendable(v.status) && canRx && <button type="button" style={smallBtn} onClick={() => setModal({ kind: "rx", visit: v })}>Add prescription</button>}
                        {v.prescriptions.length > 0 && <button type="button" style={smallBtn} onClick={() => downloadPdf(`/api/v1/clinic/consultations/${v.id}/prescription-pdf`, `rx-${v.id}.pdf`)}>Prescription PDF</button>}
                        <button type="button" style={smallBtn} onClick={() => downloadPdf(`/api/v1/clinic/consultations/${v.id}/summary-pdf`, `visit-${v.id}.pdf`)}>Visit summary PDF</button>
                        {canSick && <button type="button" style={smallBtn} onClick={() => setModal({ kind: "sick", visit: v })}>Sick note</button>}
                        {canRef && <button type="button" style={smallBtn} onClick={() => setModal({ kind: "referral", visit: v })}>Referral letter</button>}
                      </div>
                      {isAmendable(v.status) && <div style={{ fontSize: 11, color: GRAY, marginTop: 8 }}>A signed visit is not changed. Corrections and anything added later are recorded as addenda with who wrote them and when.</div>}
                    </div>
                  )}
                </div>
              )
            })}
          </div>
        )}

      {modal?.kind === "addendum" && <AddendumModal visit={modal.visit} onClose={() => setModal(null)} onDone={done} />}
      {modal?.kind === "rx" && <LateRxModal visit={modal.visit} onClose={() => setModal(null)} onDone={done} />}
      {modal?.kind === "sick" && <SickNoteModal patientId={patientId} list={[{ id: modal.visit.id, consultedAt: modal.visit.consultedAt, chiefComplaint: modal.visit.chiefComplaint ?? undefined }]} onClose={() => setModal(null)} />}
      {modal?.kind === "referral" && <ReferralLetterModal consultationId={modal.visit.id} onClose={() => setModal(null)} />}
    </div>
  )
}

function AddendumModal({ visit, onClose, onDone }: { visit: Visit; onClose: () => void; onDone: () => void }) {
  const [text, setText] = useState("")
  const [error, setError] = useState("")
  const save = useMutation({ mutationFn: () => apiClient.post(`/api/v1/clinic/consultations/${visit.id}/addenda`, { text: text.trim() }),
    onSuccess: onDone, onError: (e: any) => setError(e?.response?.data?.message ?? "Could not save the addendum.") })
  return (
    <ModalShell title={`Addendum — visit of ${fmtDay(visit.consultedAt)}`} onClose={onClose} width={560}
      footer={<><button onClick={onClose} style={smallBtn}>Cancel</button>
        <button onClick={() => save.mutate()} disabled={!text.trim() || save.isPending} style={{ ...primaryBtn, padding: "9px 18px", fontSize: 14, opacity: text.trim() ? 1 : 0.6 }}>{save.isPending ? "Saving…" : "Add addendum"}</button></>}>
      <div style={{ fontSize: 12, color: GRAY, marginBottom: 10 }}>The signed note stays exactly as it was. This is added under it with your name and the time.</div>
      <label style={lbl} htmlFor="addendum-text">Addendum</label>
      <textarea id="addendum-text" rows={5} maxLength={5000} value={text} onChange={e => setText(e.target.value)} style={{ ...sinp, resize: "vertical" }} />
      {error && <div role="alert" style={{ marginTop: 8, fontSize: 13, color: RED_TEXT }}>{error}</div>}
    </ModalShell>
  )
}

function LateRxModal({ visit, onClose, onDone }: { visit: Visit; onClose: () => void; onDone: () => void }) {
  const [error, setError] = useState("")
  const add = useMutation({ mutationFn: (body: any) => apiClient.post(`/api/v1/clinic/consultations/${visit.id}/prescriptions`, body),
    onSuccess: onDone, onError: (e: any) => setError(e?.response?.data?.message ?? "Could not add the prescription.") })
  return (
    <ModalShell title={`Add prescription — visit of ${fmtDay(visit.consultedAt)}`} onClose={onClose} width={560}>
      <div style={{ fontSize: 12, color: GRAY, marginBottom: 10 }}>This visit is signed. The medicine is added to it and an addendum records that it was added after signing.</div>
      <PrescriptionForm consultationId={visit.id} busy={add.isPending} error={error} onSubmit={body => { setError(""); return add.mutateAsync(body) }} />
    </ModalShell>
  )
}

const Muted = ({ children }: { children: React.ReactNode }) => <div style={{ fontSize: 12, color: GRAY }}>{children}</div>
function Field({ k, v, children }: { k: string; v?: string | null; children?: React.ReactNode }) {
  return (
    <div>
      <div style={{ fontSize: 10, fontWeight: 700, color: GRAY, textTransform: "uppercase", letterSpacing: "0.06em", marginBottom: 2 }}>{k}</div>
      {v?.trim() ? <div style={{ fontSize: 13, color: "var(--hf-text)", whiteSpace: "pre-wrap" }}>{v}</div> : <div style={{ fontSize: 13, color: "var(--hf-text-faint)" }}>Not recorded</div>}
      {children}
    </div>
  )
}
function Block({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div>
      <div style={{ fontSize: 10, fontWeight: 700, color: GRAY, textTransform: "uppercase", letterSpacing: "0.06em", marginBottom: 4 }}>{title}</div>
      {children}
    </div>
  )
}
