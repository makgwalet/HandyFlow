// src/pages/clinic/ConsultationSession.tsx
// The consultation workspace: safety bar, five stages, clinical context beside the notes, and a sign check.
// Hosted by ConsultationWorkspacePage (route /clinic/consult/:appointmentId), not a modal.

import { useDialogs } from "./dialogs"
import { useState, useEffect, useRef, useCallback } from "react"
import { useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { useCan } from "./clinicAccess"
import QuestionForm from "./QuestionForm"
import { VitalsPanel, SoapFields } from "./ConsultationNotesPanels"
import LiveBillPanel from "./LiveBillPanel"
import RxDraftsPanel from "./RxDraftsPanel"
import ConsultationStepper from "./ConsultationStepper"
import { consultSteps } from "./consultSteps"
import { FOLLOW_UP_CHOICES, WIZARD_ORDER, nextStep, prevStep, stepNumber, vitalsLine, type WizardStep } from "./consultWizard"
import SafetyBar from "./SafetyBar"
import ClinicalContextDrawer, { useBriefing } from "./ClinicalContextDrawer"
import SignReviewPanel from "./SignReviewPanel"
import type { SignedSummary } from "./NextPatientPanel"
import { Recap, StepTitle, chipStyle } from "./ConsultationParts"
import { signChecklist, signVerdict } from "./signRules"
import { useVisitStages } from "./useVisitStages"
import { isWithOtherClinician, lifecycleLabel } from "./workspace"
import { PatientAlertBanner } from "./PatientNotes"
import { missingReasons, useAllergyChecks } from "./PrescriptionAllergyCheck"
import {
  Mic, MicOff, X,
  Loader, Sparkles, Search,
} from "lucide-react"

import {
  type Patient,
  type Appointment,
  type BillLine,
  type RxDraft,
  NAVY_TEXT,
  TEAL,
  RED,
  RED_TEXT,
  PURPLE,
  PURPLE_TEXT,
  GRAY,
  GRAY_TEXT,
  BORDER,
  LIGHT,
  QUICK_PROCEDURES,
  fmtR,
  fmtTimer,
  unwrap,
  sectionLabel,
  sinp,
  primaryBtn,
  cancelBtn,
  voiceBtn,
} from "./consultationSession.shared"

// ── Main Component ─────────────────────────────────────────────────────────────

interface Props {
  patient: Patient
  appointment: Appointment
  onComplete: (consultationId: string, summary: SignedSummary) => void
  onMinimise: () => void
  onCancel: () => void
  /** Bumped by the parent to discard this draft. */
  discardToken?: number
}

export default function ConsultationSession({ patient, appointment, onComplete, onMinimise, onCancel, discardToken = 0 }: Props) {
  const qc = useQueryClient()
  const { confirm, prompt, notify, dialogs } = useDialogs()

  // ── Timer ──────────────────────────────────────────────────────────────────
  const startTimeRef = useRef(Date.now())
  const [elapsed, setElapsed] = useState(0)
  useEffect(() => {
    const id = setInterval(() => setElapsed(Math.floor((Date.now()-startTimeRef.current)/1000)), 1000)
    return () => clearInterval(id)
  }, [])
  const durationMinutes = Math.max(1, Math.round(elapsed/60))

  // ── SOAP state ────────────────────────────────────────────────────────────
  const [soap, setSoap] = useState({
    chiefComplaint: appointment.reason || "",
    history:"", examination:"", diagnosis:"", icd10Codes:"", treatmentPlan:"", followUpDays:"",
    weightKg:"", heightCm:"", bloodPressure:"", pulseBpm:"", temperatureC:"", oxygenSatPct:""
  })
  const sf = (k: keyof typeof soap, v: string) => setSoap(p=>({...p,[k]:v}))

  // ── Server-side DRAFT + autosave ───────────────────────────────────────────
  // The consultation is persisted as a DRAFT on open and PATCHed (debounced) as the
  // clinician types, so a refresh, crash or navigation no longer loses the note.
  // Re-opening the same appointment resumes the existing draft.
  const draftIdRef = useRef<string|null>(null)
  const initStartedRef = useRef(false)
  const [draftReady, setDraftReady] = useState(false)
  const [returnNote, setReturnNote] = useState<{ reason: string; comment: string } | null>(null)
  const [draftState, setDraftState] = useState<"idle"|"saving"|"saved"|"error">("idle")
  // Where the consultation is in the nurse/doctor handoff; decides who may edit and which buttons show.
  const [status, setStatusState] = useState("DRAFT")
  const statusRef = useRef("DRAFT")
  const setStatus = (v: string) => { statusRef.current = v; setStatusState(v) }
  const [drawerOpen, setDrawerOpen] = useState(true)
  const [handoffNote, setHandoffNote] = useState<{ comment: string; at: string } | null>(null)
  const canSign = useCan("signConsultation")
  const canWrite = useCan("editConsultation")

  // Text fields are sent as "" (not null) so clearing a field actually clears it server-side.
  const draftPayload = () => ({
    chiefComplaint: soap.chiefComplaint,
    weightKg:       parseFloat(soap.weightKg)||null,
    heightCm:       parseFloat(soap.heightCm)||null,
    bloodPressure:  soap.bloodPressure,
    pulseBpm:       parseInt(soap.pulseBpm)||null,
    temperatureC:   parseFloat(soap.temperatureC)||null,
    oxygenSatPct:   parseFloat(soap.oxygenSatPct)||null,
    history:        soap.history,
    examination:    soap.examination,
    diagnosis:      soap.diagnosis,
    icd10Codes:     soap.icd10Codes?soap.icd10Codes.split(",").map((x:string)=>x.trim()).filter(Boolean):[],
    treatmentPlan:  soap.treatmentPlan,
    followUpDays:   parseInt(soap.followUpDays)||null,
  })

  useEffect(() => {
    if (initStartedRef.current) return
    initStartedRef.current = true
    ;(async () => {
      try {
        // Any unsigned consultation for this appointment, including one the nurse handed over.
        let existing: any = null
        try {
          const r = await apiClient.get(`/api/v1/clinic/appointments/${appointment.id}/consultation`)
          const c = r.data?.data ?? r.data
          if (c && c.id) existing = c
        } catch (e: any) { if (e?.response?.status !== 404) throw e }
        if (existing) {
          draftIdRef.current = existing.id
          setStatus(existing.status)
          if (existing.status === "READY_FOR_DOCTOR" || existing.status === "DOCTOR_REVIEWING") {
            // What the nurse said when handing over, shown above the notes.
            try {
              const t = unwrap(await apiClient.get(`/api/v1/clinic/consultations/${existing.id}/transitions`))
              const last = [...t].reverse().find((x:any) => x.toStatus === "READY_FOR_DOCTOR")
              if (last) setHandoffNote({ comment: last.comment||"", at: last.createdAt })
            } catch { /* the notes still open */ }
          }
          if (existing.status === "RETURNED_TO_NURSE") {
            // Doctor sent it back: show why, and take it back into nurse work.
            try {
              const t = unwrap(await apiClient.get(`/api/v1/clinic/consultations/${existing.id}/transitions`))
              const last = [...t].reverse().find((x:any) => x.toStatus === "RETURNED_TO_NURSE")
              if (last) setReturnNote({ reason: String(last.reasonCode||"").replace(/_/g," ").toLowerCase(), comment: last.comment||"" })
              await apiClient.post(`/api/v1/clinic/consultations/${existing.id}/resume-nurse-work`)
              setStatus("NURSE_IN_PROGRESS")
            } catch { /* the draft still opens; the note is a convenience */ }
          }
          const str = (v:any) => v==null ? "" : String(v)
          setSoap(p => ({ ...p,
            chiefComplaint: existing.chiefComplaint || p.chiefComplaint,
            history: str(existing.history), examination: str(existing.examination),
            diagnosis: str(existing.diagnosis),
            icd10Codes: (existing.icd10Codes||[]).join(", "),
            treatmentPlan: str(existing.treatmentPlan), followUpDays: str(existing.followUpDays),
            weightKg: str(existing.weightKg), heightCm: str(existing.heightCm),
            bloodPressure: str(existing.bloodPressure), pulseBpm: str(existing.pulseBpm),
            temperatureC: str(existing.temperatureC), oxygenSatPct: str(existing.oxygenSatPct),
          }))
        } else {
          const c = await apiClient.post(
            `/api/v1/clinic/patients/${patient.id}/consultations/draft`,
            { appointmentId: appointment.id, practitionerId: appointment.practitionerId||null,
              chiefComplaint: soap.chiefComplaint||"" })
          draftIdRef.current = (c.data?.data ?? c.data).id
          setStatus("DRAFT")
        }
        setDraftReady(true); setDraftState("saved")
      } catch { setDraftState("error") }
    })()
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  useEffect(() => {
    if (!draftReady || !draftIdRef.current || isWithOtherClinician(statusRef.current)) return
    const t = setTimeout(async () => {
      setDraftState("saving")
      try {
        await apiClient.patch(`/api/v1/clinic/consultations/${draftIdRef.current}`, draftPayload())
        setDraftState("saved")
      } catch { setDraftState("error") }
    }, 1500)
    return () => clearTimeout(t)
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [soap, draftReady, status])

  const discardDraft = async () => {
    const id = draftIdRef.current
    if (id) { try { await apiClient.post(`/api/v1/clinic/consultations/${id}/abandon`) } catch { /* leave draft */ } }
    onCancel()
  }
  // Nurse hands the consultation to a doctor: flush the latest notes, then hand over.
  const canHandoff = !canSign && canWrite
  const [handoffBusy, setHandoffBusy] = useState(false)
  const [handoffError, setHandoffError] = useState("")
  const sendToDoctor = async () => {
    const id = draftIdRef.current
    if (!id) return
    setHandoffBusy(true); setHandoffError("")
    try {
      await apiClient.patch(`/api/v1/clinic/consultations/${id}`, draftPayload())
      const note = await prompt({ title: "Send to doctor", label: "Note for the doctor", optional: true, multiline: true, confirmLabel: "Send" })
      if (note === null) { setHandoffBusy(false); return }
      await apiClient.post(`/api/v1/clinic/consultations/${id}/send-to-doctor`, { comment: note })
      qc.invalidateQueries({ queryKey: ["clinic-handoff-queue"] })
      onCancel()
    } catch (e: any) {
      setHandoffError(e?.response?.data?.message ?? "Could not send to doctor")
    } finally { setHandoffBusy(false) }
  }
  const handleCancel = async () => {
    if (await confirm({ title: "Discard this draft?", body: "The notes entered so far will be abandoned.", confirmLabel: "Discard draft", danger: true })) discardDraft()
  }
  useEffect(() => {
    if (discardToken > 0) discardDraft()
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [discardToken])

  // ── Voice recording ───────────────────────────────────────────────────────
  const [isRecording, setIsRecording] = useState(false)
  const [transcript, setTranscript]   = useState("")
  const [extracting]   = useState(false)
  const recRef = useRef<any>(null)

  const startRec = () => {
    const SR=(window as any).SpeechRecognition||(window as any).webkitSpeechRecognition
    if (!SR) { void notify({ title: "Voice notes unavailable", body: "Speech recognition needs Chrome or Edge." }); return }
    const r=new SR(); r.continuous=true; r.interimResults=true; r.lang="en-ZA"
    r.onresult=(e:any)=>{
      let final=""
      for (let i=0;i<e.results.length;i++) if(e.results[i].isFinal) final+=e.results[i][0].transcript+" "
      setTranscript(t=>t+final)
    }
    r.onerror=()=>setIsRecording(false); r.onend=()=>setIsRecording(false)
    recRef.current=r; r.start(); setIsRecording(true)
  }
  const stopRec = () => { recRef.current?.stop(); setIsRecording(false) }

  const extractSOAP = async () => {
    // DISABLED (audit F-03): this used to POST the transcript (patient health information)
    // straight from the browser to a third-party API with no auth, consent or audit.
    // It returns once a backend scribe endpoint exists (consent gate, then draft, then
    // clinician review, then sign). Nothing is sent anywhere from here.
    await notify({ title: "AI notes are switched off", body: "AI note extraction stays off until the secure, consent-gated scribe is available. Your transcript was not sent anywhere." })
  }

  // ── Bill lines ────────────────────────────────────────────────────────────
  const [billLines, setBillLines] = useState<BillLine[]>([
    // Auto-add consultation tariff on session start
    { id:"consult-0191", type:"CONSULTATION", description:"Consultation — intermediate",
      tariffCode:"0191", quantity:1, unitPrice:520, gross:520 }
  ])
  const billTotal = billLines.reduce((s,l)=>s+l.gross,0)

  const addBillLine = useCallback((line: Omit<BillLine,"id">) => {
    setBillLines(b=>[...b,{...line, id:crypto.randomUUID()}])
  },[])

  const removeBillLine = (id: string) => setBillLines(b=>b.filter(l=>l.id!==id))

  const addQuickProcedure = (proc: typeof QUICK_PROCEDURES[0]) => {
    addBillLine({ type:"PROCEDURE", description:proc.label,
      tariffCode:proc.tariff, quantity:1, unitPrice:proc.price, gross:proc.price })
  }

  // ── Rx drafts ─────────────────────────────────────────────────────────────
  const [rxDrafts, setRxDrafts] = useState<RxDraft[]>([])
  const updateRx = (id: string, k: keyof RxDraft, v: any) =>
    setRxDrafts(d=>d.map(x=>x.id===id?{...x,[k]:v}:x))
  const removeRx = (id: string) => setRxDrafts(d=>d.filter(x=>x.id!==id))
  const allergyResults = useAllergyChecks(draftReady ? (draftIdRef.current ?? null) : null, rxDrafts)

  // ── Med search + dual add ─────────────────────────────────────────────────
  const [medSearch, setMedSearch]   = useState("")
  const [showMedSearch, setShowMedSearch] = useState(false)
  const [medResults, setMedResults] = useState<any[]>([])
  const [medLoading, setMedLoading] = useState(false)

  useEffect(() => {
    if (!medSearch.trim() || medSearch.length < 2) { setMedResults([]); return }
    const t = setTimeout(async () => {
      setMedLoading(true)
      try {
        const r = await apiClient.get(`/api/v1/clinic/medications?search=${encodeURIComponent(medSearch)}`)
        setMedResults(unwrap(r).slice(0,8))
      } catch { setMedResults([]) }
      setMedLoading(false)
    }, 300)
    return () => clearTimeout(t)
  }, [medSearch])

  const addMedicationToBillAndRx = (med: {
    medicationName: string; nappiCode?: string
    dosage?: string; frequency?: string; duration?: string; unitPrice: number
  }) => {
    // Add to bill
    addBillLine({
      type:"MEDICINE", description:med.medicationName,
      nappiCode:med.nappiCode,
      quantity:1, unitPrice:med.unitPrice, gross:med.unitPrice
    })
    // Add to Rx drafts
    setRxDrafts(d=>[...d,{
      id:crypto.randomUUID(),
      medicationName:med.medicationName, nappiCode:med.nappiCode,
      dosage:med.dosage||"", frequency:med.frequency||"", duration:med.duration||"",
      quantity:30, instructions:"", fromBill:true
    }])
    setMedSearch(""); setMedResults([]); setShowMedSearch(false)
  }

  // ── Sign ──────────────────────────────────────────────────────────────────
  const [completeError, setCompleteError] = useState("")
  const savedRxRef = useRef<Set<string>>(new Set())
  const allergyBlocks = missingReasons(rxDrafts.filter(r=>!savedRxRef.current.has(r.id)), allergyResults)

  const complete = useMutation({
    mutationFn: async (overrideReason?: string) => {
      if (allergyBlocks.length) throw new Error("Give a reason to prescribe despite the recorded allergy: " + allergyBlocks.join(", ") + ".")
      const draftId = draftIdRef.current
      if (!draftId) throw new Error("This consultation was never saved, so it cannot be signed. Check your connection and open it again.")
      // 1. Save the notes (not again once the doctor has completed their review: the record is then read-only).
      if (statusRef.current !== "DOCTOR_COMPLETED") await apiClient.patch(`/api/v1/clinic/consultations/${draftId}`, draftPayload())

      // 2. Save prescriptions (each only once, so a retry after a later failure cannot duplicate them)
      for (const rx of rxDrafts) {
        if (!rx.medicationName.trim() || savedRxRef.current.has(rx.id)) continue
        await apiClient.post(`/api/v1/clinic/consultations/${draftId}/prescriptions`,{
          medicationName: rx.medicationName,
          nappiCode:      rx.nappiCode||null,
          dosage:         rx.dosage||null,
          frequency:      rx.frequency||null,
          duration:       rx.duration||null,
          quantity:       rx.quantity||30,
          repeats:        0,
          instructions:   rx.instructions||null,
          allergyOverrideReason: rx.allergyReason?.trim() || null,
        })
        savedRxRef.current.add(rx.id)
      }

      // 3. A consultation the doctor accepted from a nurse finishes its review, then is signed. Signing also completes
      //    the appointment and never emails the patient (DEC-CLINIC-002). Missing Symptoms or Diagnosis need a reason.
      if (statusRef.current === "DOCTOR_REVIEWING") {
        await apiClient.post(`/api/v1/clinic/consultations/${draftId}/doctor-complete`)
        setStatus("DOCTOR_COMPLETED")
      }
      await apiClient.post(`/api/v1/clinic/consultations/${draftId}/sign`, overrideReason ? { overrideReason } : {})
      return draftId
    },
    onSuccess: (consultationId) => {
      for (const k of ["pf-appointments","pf-consultations","clinic-appts-dashboard","schedule-appts","clinic-patients","clinic-dock","clinic-handoff-queue","pf-briefing"])
        qc.invalidateQueries({queryKey:[k]})
      onComplete(consultationId, { consultationId, diagnosis: soap.diagnosis.trim(), rxCount: rxDrafts.filter(r=>r.medicationName.trim()).length,
        followUpDays: soap.followUpDays, durationMinutes })
    },
    onError: (e:any) => setCompleteError(e.response?.data?.message ?? e.message ?? "Failed to sign the consultation"),
  })

  const [step, setStep] = useState<WizardStep>("symptoms")
  const required = useVisitStages(appointment.appointmentType || "CONSULTATION")
  const checklist = signChecklist(soap, rxDrafts, allergyBlocks, required)
  const verdict = signVerdict(checklist)
  const locked = isWithOtherClinician(status)
  const { data: briefing } = useBriefing(patient.id)
  const canDiscard = ["DRAFT","NURSE_IN_PROGRESS","RETURNED_TO_NURSE"].includes(status)

  const [handoffActionError, setHandoffActionError] = useState("")
  const acceptHandoff = async () => {
    const id = draftIdRef.current; if (!id) return
    setHandoffActionError("")
    try {
      await apiClient.post(`/api/v1/clinic/consultations/${id}/accept`, { practitionerId: appointment.practitionerId || null })
      setStatus("DOCTOR_REVIEWING"); qc.invalidateQueries({ queryKey: ["clinic-handoff-queue"] })
    } catch (e: any) { setHandoffActionError(e?.response?.data?.message ?? "Could not accept the handoff") }
  }
  const returnToNurse = async () => {
    const id = draftIdRef.current; if (!id) return
    const comment = await prompt({ title: "Return to nurse", label: "What does the nurse need to fix?", multiline: true, confirmLabel: "Return" })
    if (comment === null) return
    try {
      await apiClient.post(`/api/v1/clinic/consultations/${id}/return-to-nurse`, { reasonCode: "OTHER", comment })
      qc.invalidateQueries({ queryKey: ["clinic-handoff-queue"] }); qc.invalidateQueries({ queryKey: ["clinic-dock"] }); onCancel()
    } catch (e: any) { setHandoffActionError(e?.response?.data?.message ?? "Could not return it to the nurse") }
  }

  const barBtn: React.CSSProperties = { padding:"8px 14px", borderRadius:8, border:`1px solid ${BORDER}`, background:"var(--hf-surface)", color:"var(--hf-text-secondary)", fontSize:13, fontWeight:600, cursor:"pointer" }
  const banner = (tone: "warn"|"info", children: React.ReactNode) => (
    <div role="status" style={{ margin:"10px 0 0", padding:"10px 14px", borderRadius:10, fontSize:13, display:"flex", gap:10, alignItems:"center", flexWrap:"wrap",
      background: tone==="warn" ? "var(--hf-warning-soft)" : "var(--hf-info-soft)", color: tone==="warn" ? "var(--hf-warning-text)" : "var(--hf-info-text)" }}>{children}</div>)

  return (
    <div style={{ fontFamily:"'Inter',system-ui,sans-serif", display:"flex", flexDirection:"column", minHeight:"calc(100vh - 140px)" }}>
      {dialogs}

      <SafetyBar patient={patient} briefing={briefing ?? undefined}
        visit={`${(appointment.appointmentType||"Consultation").replace(/_/g," ").toLowerCase().replace(/^./, c=>c.toUpperCase())}${appointment.reason ? ` · ${appointment.reason}` : ""}`}
        stateLabel={lifecycleLabel(status, { ...soap, hasVitals: [soap.weightKg, soap.heightCm, soap.bloodPressure, soap.pulseBpm, soap.temperatureC, soap.oxygenSatPct].some(v => v.trim()) }, required)} timer={fmtTimer(elapsed)}
        saveText={locked ? "Read only" : draftState==="saving" ? "Saving…" : draftState==="saved" ? "Auto-saved" : draftState==="error" ? "Not saved, check connection" : ""}>
        {canSign && !locked && <button id="consult-complete" onClick={()=>setStep("sign")} style={{ ...barBtn, background:TEAL, color:"var(--hf-text-on-solid)", border:"none" }}>Review &amp; sign</button>}
        {canHandoff && !locked && <button onClick={sendToDoctor} disabled={handoffBusy || !draftReady}
          title={handoffError || "Hand this consultation to a doctor; it leaves your drafts until returned"} style={barBtn}>{handoffBusy ? "Sending…" : "Send to doctor"}</button>}
        <button onClick={onMinimise} title="Leave the consultation; it stays saved and shows in the dock" style={barBtn}>Leave</button>
        {canDiscard && <button onClick={handleCancel} title="Discard this draft" aria-label="Discard draft" style={{ ...barBtn, padding:8, display:"flex" }}><X size={16}/></button>}
      </SafetyBar>

      {returnNote && banner("warn", <>
        <div style={{ flex:1 }}><strong>Returned by the doctor</strong> ({returnNote.reason}): {returnNote.comment}</div>
        <button onClick={() => setReturnNote(null)} aria-label="Dismiss" style={{ background:"none", border:"none", cursor:"pointer", color:"inherit" }}>×</button></>)}
      {status==="READY_FOR_DOCTOR" && canSign && banner("info", <>
        <div style={{ flex:1 }}><strong>Handed over by the nurse.</strong> Accept it to review and edit.{handoffNote?.comment ? ` Nurse note: ${handoffNote.comment}` : ""}</div>
        <button onClick={acceptHandoff} style={{ ...barBtn, background:TEAL, color:"var(--hf-text-on-solid)", border:"none" }}>Accept handoff</button>
        <button onClick={returnToNurse} style={barBtn}>Return to nurse</button></>)}
      {status==="READY_FOR_DOCTOR" && !canSign && banner("info", <div>With the doctor for review. It is read only until they accept or return it.</div>)}
      {status==="DOCTOR_REVIEWING" && handoffNote && banner("info", <div><strong>Nurse intake</strong> is filled in below{handoffNote.comment ? `. Note: ${handoffNote.comment}` : "."}</div>)}
      {status==="DOCTOR_COMPLETED" && banner("info", <div>Review finished. Go to Review &amp; sign to sign it.</div>)}
      {handoffActionError && <div role="alert" style={{ marginTop:8, fontSize:13, color:RED_TEXT }}>{handoffActionError}</div>}

      <PatientAlertBanner patientId={patient.id}/>

      <div style={{ display:"flex", gap:16, alignItems:"flex-start", flexWrap:"wrap", marginTop:12, flex:1 }}>
      <div style={{ flex:"1 1 560px", minWidth:0, display:"flex", flexDirection:"column" }}>
      <ConsultationStepper steps={consultSteps(soap, rxDrafts, required)} current={step} onSelect={setStep}/>

      {/* ── One page per step ──────────────────────────────────────────── */}
      <fieldset disabled={locked} style={{ border:0, margin:0, padding:0, minWidth:0, flex:1, display:"flex", flexDirection:"column", gap:10, marginBottom:12 }} data-step={step}>

        {/* Stays mounted on every step (hidden off the Symptoms page) so answers still being saved are never cut off. */}
        <div hidden={step !== "symptoms"}>
          <QuestionForm consultationId={draftReady ? (draftIdRef.current ?? null) : null}
            patientId={patient.id} visitType={appointment.appointmentType || "CONSULTATION"} />
        </div>

        {step === "symptoms" && (<>
          <StepTitle n={1} title="Symptoms" help="Why the patient came and what they report." />
          {/* Voice panel */}
          <div style={{ padding:"12px 14px", background:"var(--hf-violet-soft)", border:"1px solid var(--hf-violet-border)",
            borderRadius:10 }}>
            <div style={{ display:"flex", justifyContent:"space-between", alignItems:"center", marginBottom:8 }}>
              <span style={{ fontSize:12, fontWeight:700, color:PURPLE_TEXT, display:"flex", alignItems:"center", gap:5 }}>
                <Mic size={13}/> Voice-to-notes
              </span>
              <div style={{ display:"flex", gap:6 }}>
                {!isRecording
                  ? <button onClick={startRec} style={voiceBtn(PURPLE)}>
                      <Mic size={11}/> Record
                    </button>
                  : <button onClick={stopRec} style={voiceBtn(RED)}>
                      <MicOff size={11}/> Stop
                    </button>
                }
                {transcript && (
                  <>
                    <button onClick={extractSOAP} disabled={extracting} style={voiceBtn(extracting?"var(--hf-text-faint)":TEAL)}>
                      {extracting ? <><Loader size={11}/> Extracting</> : <><Sparkles size={11}/> Extract SOAP</>}
                    </button>
                    <button onClick={()=>setTranscript("")} style={voiceBtn(GRAY)}>Clear</button>
                  </>
                )}
              </div>
            </div>
            {isRecording && (
              <div style={{ display:"flex", alignItems:"center", gap:5, fontSize:11, color:RED_TEXT, marginBottom:4 }}>
                <div style={{ width:6, height:6, borderRadius:"50%", background:RED }}/>
                Recording
              </div>
            )}
            <textarea value={transcript} onChange={e=>setTranscript(e.target.value)} rows={2}
              style={{ ...sinp, fontSize:11, resize:"vertical" as const,
                background:"rgba(255,255,255,0.7)", color:"var(--hf-text-tertiary)" }}
              placeholder="Speak or type transcript here, then Extract SOAP…"/>
          </div>

          <SoapFields soap={soap} sf={sf} only={["chiefComplaint","history"]}/>
        </>)}

        {step === "examination" && (<>
          <StepTitle n={2} title="Examination" help="Vitals and what you found on examination. Vitals the nurse took are already here." />
          <VitalsPanel soap={soap} sf={sf}/>
          <SoapFields soap={soap} sf={sf} only={["examination"]}/>
        </>)}

        {step === "diagnose" && (<>
          <StepTitle n={3} title="Assessment & treatment" help="Your assessment, then what you are doing about it." />
          <div style={{ display:"flex", gap:14, flexWrap:"wrap", alignItems:"flex-start" }}>
            <div style={{ flex:"1 1 340px", display:"flex", flexDirection:"column", gap:10 }}>
              <SoapFields soap={soap} sf={sf} only={["diagnosis","icd10Codes"]}/>
          {/* Medication search — adds to bill + Rx */}
          <div style={{ padding:"12px 14px", background:"var(--hf-surface)", border:`1px solid ${BORDER}`, borderRadius:10, position:"relative" }}>
            <div style={sectionLabel}>Add medication (prescription + bill)</div>
            <div style={{ position:"relative" }}>
              <Search size={13} style={{ position:"absolute", left:8, top:"50%", transform:"translateY(-50%)", color:GRAY_TEXT }}/>
              <input value={medSearch} onChange={e=>{setMedSearch(e.target.value);setShowMedSearch(true)}}
                onFocus={()=>setShowMedSearch(true)}
                placeholder="Search NAPPI catalogue…"
                style={{...sinp, paddingLeft:28}}/>
            </div>
            {showMedSearch && (medLoading || medResults.length>0) && (
              <div style={{ position:"absolute", left:14, right:14, zIndex:50,
                background:"var(--hf-surface)", border:`1px solid ${BORDER}`, borderRadius:8,
                boxShadow:"0 8px 32px rgba(0,0,0,0.12)", maxHeight:240, overflowY:"auto" }}>
                {medLoading && <div style={{padding:"10px 14px",fontSize:12,color:GRAY_TEXT}}>Searching…</div>}
                {medResults.map((med:any)=>(
                  <div key={med.id||med.nappiCode}
                    onClick={()=>addMedicationToBillAndRx({
                      medicationName:`${med.genericName} ${med.strength||""}`.trim(),
                      nappiCode:med.nappiCode,
                      unitPrice:parseFloat(med.singleExitPrice)||0
                    })}
                    style={{ padding:"8px 14px", cursor:"pointer", borderBottom:`1px solid var(--hf-border-subtle)` }}
                    onMouseEnter={e=>(e.currentTarget.style.background=LIGHT)}
                    onMouseLeave={e=>(e.currentTarget.style.background="var(--hf-surface)")}>
                    <div style={{fontWeight:600,fontSize:13,color:"var(--hf-text)"}}>{med.genericName} <span style={{color:GRAY_TEXT,fontWeight:400}}>{med.strength}</span></div>
                    <div style={{fontSize:11,color:GRAY_TEXT}}>{med.brandName} · NAPPI: {med.nappiCode} · SEP: {fmtR(parseFloat(med.singleExitPrice)||0)}</div>
                  </div>
                ))}
                <div onClick={()=>setShowMedSearch(false)}
                  style={{padding:"6px 14px",fontSize:11,color:GRAY_TEXT,cursor:"pointer",borderTop:`1px solid var(--hf-border-subtle)`,textAlign:"center" as const}}>
                  Close
                </div>
              </div>
            )}
          </div>

              <RxDraftsPanel rxDrafts={rxDrafts} allergyResults={allergyResults} updateRx={updateRx} removeRx={removeRx}
                addBlankRx={()=>setRxDrafts(d=>[...d,{id:crypto.randomUUID(),
                  medicationName:"",dosage:"",frequency:"",duration:"",quantity:30,instructions:"",fromBill:false}])}/>
            </div>
            <div style={{ flex:"1 1 260px", display:"flex", flexDirection:"column", gap:10 }}>
          {/* Quick-add procedures */}
          <div style={{ padding:"12px 14px", background:"var(--hf-surface)", border:`1px solid ${BORDER}`, borderRadius:10 }}>
            <div style={sectionLabel}>Quick add — procedures</div>
            <div style={{ display:"flex", gap:6, flexWrap:"wrap" as const }}>
              {QUICK_PROCEDURES.map(proc=>(
                <button key={proc.tariff} onClick={()=>addQuickProcedure(proc)}
                  style={{ display:"flex", alignItems:"center", gap:5, padding:"5px 10px",
                    background:LIGHT, border:`1px solid ${BORDER}`, borderRadius:7,
                    fontSize:11, fontWeight:600, color:NAVY_TEXT, cursor:"pointer" }}
                  onMouseEnter={e=>(e.currentTarget.style.borderColor=TEAL)}
                  onMouseLeave={e=>(e.currentTarget.style.borderColor=BORDER)}>
                  <proc.icon size={11}/>{proc.label} <span style={{color:GRAY_TEXT}}>R{proc.price}</span>
                </button>
              ))}
            </div>
            <div style={{ fontSize:11, color:GRAY_TEXT, marginTop:8 }}>Procedures and medicines are added to the running bill in the side panel.</div>
          </div>
            </div>
          </div>
        </>)}

        {step === "plan" && (<>
          <StepTitle n={4} title="Plan" help="Treatment, advice and when to see the patient again." />
          <SoapFields soap={soap} sf={sf} only={["treatmentPlan"]}/>
          <div style={{ padding:"12px 14px", background:"var(--hf-surface)", border:`1px solid ${BORDER}`, borderRadius:10 }}>
            <div style={sectionLabel}>Follow-up</div>
            <div style={{ display:"flex", gap:6, flexWrap:"wrap", alignItems:"center", marginBottom:8 }}>
              <button type="button" aria-pressed={!soap.followUpDays.trim()} onClick={()=>sf("followUpDays","")}
                style={chipStyle(!soap.followUpDays.trim())}>No follow-up</button>
              {FOLLOW_UP_CHOICES.map(d=>(
                <button key={d} type="button" aria-pressed={soap.followUpDays.trim()===String(d)} onClick={()=>sf("followUpDays",String(d))}
                  style={chipStyle(soap.followUpDays.trim()===String(d))}>{d} days</button>
              ))}
            </div>
            <SoapFields soap={soap} sf={sf} only={["followUpDays"]}/>
            <div style={{ fontSize:11, color:GRAY_TEXT, marginTop:6 }}>The patient shows on Recalls when the follow-up date passes without a booking.</div>
          </div>
        </>)}

        {step === "sign" && (<>
          <StepTitle n={5} title={canSign ? "Review & sign" : "Review & hand over"} help={canSign ? "Check the record, then sign it." : "Check the record, then hand it to the doctor."} />
          <SignReviewPanel mode={canSign ? "sign" : "handoff"} items={checklist} verdict={verdict} busy={complete.isPending || handoffBusy} error={completeError || handoffError}
            onGoTo={setStep} onSign={(reason)=>{ setCompleteError(""); complete.mutate(reason) }} onHandoff={sendToDoctor}>
            <div style={{ display:"grid", gridTemplateColumns:"repeat(auto-fit,minmax(240px,1fr))", gap:10 }}>
              <Recap title="Symptoms" onEdit={()=>setStep("symptoms")} rows={[["Chief complaint", soap.chiefComplaint], ["History", soap.history]]} />
              <Recap title="Examination" onEdit={()=>setStep("examination")} rows={[["Vitals", vitalsLine(soap)], ["Findings", soap.examination]]} />
              <Recap title="Assessment & treatment" onEdit={()=>setStep("diagnose")} rows={[["Diagnosis", soap.diagnosis], ["ICD-10", soap.icd10Codes],
                ["Prescriptions", rxDrafts.filter(r=>r.medicationName.trim()).map(r=>[r.medicationName, r.dosage, r.frequency, r.duration].filter(Boolean).join(" ")).join("\n")]]} />
              <Recap title="Plan" onEdit={()=>setStep("plan")} rows={[["Treatment plan", soap.treatmentPlan], ["Follow-up", soap.followUpDays.trim() ? `In ${soap.followUpDays.trim()} days` : ""]]} />
            </div>
          </SignReviewPanel>
        </>)}
      </fieldset>

      {/* ── Back / Next ─────────────────────────────────────────────────── */}
      <div style={{ position:"sticky", bottom:0, background:"var(--hf-bg, var(--hf-surface))", display:"flex", justifyContent:"space-between", alignItems:"center", gap:10, padding:"10px 0", borderTop:`1px solid ${BORDER}` }}>
        <button disabled={!prevStep(step)} onClick={()=>prevStep(step) && setStep(prevStep(step)!)}
          style={{ ...cancelBtn, opacity: prevStep(step) ? 1 : 0.4 }}>← Back</button>
        <span style={{ fontSize:12, color:GRAY_TEXT }}>Step {stepNumber(step)} of {WIZARD_ORDER.length}</span>
        {nextStep(step)
          ? <button onClick={()=>setStep(nextStep(step)!)} style={primaryBtn}>Next →</button>
          : <span style={{ width:80 }}/>}
      </div>
      </div>

      <ClinicalContextDrawer patientId={patient.id} open={drawerOpen} onToggle={()=>setDrawerOpen(o=>!o)} billSummary={fmtR(billTotal)}
        billSlot={<LiveBillPanel billLines={billLines} billTotal={billTotal} removeBillLine={removeBillLine} addBillLine={addBillLine}/>}/>
      </div>
    </div>
  )
}

