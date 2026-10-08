// src/pages/clinic/ConsultationSession.tsx
// Live consultation session — timer, SOAP notes, live bill, prescriptions
// Opened when a doctor starts a consultation from an appointment

import { useDialogs } from "./dialogs"
import ModalShell from "./ModalShell"
import { useState, useEffect, useRef, useCallback } from "react"
import { useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"
import QuestionForm from "./QuestionForm"
import { VitalsPanel, SoapFields } from "./ConsultationNotesPanels"
import LiveBillPanel from "./LiveBillPanel"
import RxDraftsPanel from "./RxDraftsPanel"
import ConsultationStepper from "./ConsultationStepper"
import { consultSteps } from "./consultSteps"
import { FOLLOW_UP_CHOICES, WIZARD_ORDER, nextStep, prevStep, stepNumber, vitalsLine, type WizardStep } from "./consultWizard"
import { PatientAlertBanner } from "./PatientNotes"
import { missingReasons, useAllergyChecks } from "./PrescriptionAllergyCheck"
import {
  Mic, MicOff, X,
  CheckCircle,
  AlertCircle, Loader, Sparkles, Search,
} from "lucide-react"

import {
  type Patient,
  type Appointment,
  type BillLine,
  type RxDraft,
  NAVY,
  NAVY_TEXT,
  TEAL,
  TEAL_TEXT,
  RED,
  RED_TEXT,
  GREEN_TEXT,
  AMBER_TEXT,
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
  onComplete: (consultationId: string) => void
  onMinimise: () => void
  onCancel: () => void
  /** Bumped by the parent (e.g. "Discard session" on the minimised bar) to discard this draft. */
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
        const r = await apiClient.get("/api/v1/clinic/consultations/drafts")
        const existing = unwrap(r).find((d:any) => d.appointmentId === appointment.id)
        if (existing) {
          draftIdRef.current = existing.id
          if (existing.status === "RETURNED_TO_NURSE") {
            // Doctor sent it back: show why, and take it back into nurse work.
            try {
              const t = unwrap(await apiClient.get(`/api/v1/clinic/consultations/${existing.id}/transitions`))
              const last = [...t].reverse().find((x:any) => x.toStatus === "RETURNED_TO_NURSE")
              if (last) setReturnNote({ reason: String(last.reasonCode||"").replace(/_/g," ").toLowerCase(), comment: last.comment||"" })
              await apiClient.post(`/api/v1/clinic/consultations/${existing.id}/resume-nurse-work`)
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
        }
        setDraftReady(true); setDraftState("saved")
      } catch { setDraftState("error") }
    })()
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  useEffect(() => {
    if (!draftReady || !draftIdRef.current) return
    const t = setTimeout(async () => {
      setDraftState("saving")
      try {
        await apiClient.patch(`/api/v1/clinic/consultations/${draftIdRef.current}`, draftPayload())
        setDraftState("saved")
      } catch { setDraftState("error") }
    }, 1500)
    return () => clearTimeout(t)
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [soap, draftReady])

  const discardDraft = async () => {
    const id = draftIdRef.current
    if (id) { try { await apiClient.post(`/api/v1/clinic/consultations/${id}/abandon`) } catch { /* leave draft */ } }
    onCancel()
  }
  // Nurse hands the consultation to a doctor: flush the latest notes, then hand over.
  const canHandoff = usePermission("CLINIC_CLINICAL_WRITE")
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

  // ── Complete consultation ─────────────────────────────────────────────────
  const [showComplete, setShowComplete] = useState(false)
  const [completeError, setCompleteError] = useState("")
  const savedRxRef = useRef<Set<string>>(new Set())

  const complete = useMutation({
    mutationFn: async () => {
      const needReason = missingReasons(rxDrafts.filter(r=>!savedRxRef.current.has(r.id)), allergyResults)
      if (needReason.length) throw new Error("Give a reason to prescribe despite the recorded allergy: " + needReason.join(", ") + ".")
      // 1. Save consultation: finalise the draft (PATCH) or, if the draft could not be
      //    created, fall back to creating it in one shot as before.
      let consultId: string
      const draftId = draftIdRef.current
      if (draftId) {
        await apiClient.patch(`/api/v1/clinic/consultations/${draftId}`, {
          ...draftPayload(),
          chiefComplaint: soap.chiefComplaint||"Consultation",
        })
        consultId = draftId
      } else {
        const consultRes = await apiClient.post(
          `/api/v1/clinic/patients/${patient.id}/consultations`,
          {
            appointmentId:   appointment.id,
            practitionerId:  appointment.practitionerId||null,
            chiefComplaint:  soap.chiefComplaint||"Consultation",
            weightKg:        parseFloat(soap.weightKg)||null,
            heightCm:        parseFloat(soap.heightCm)||null,
            bloodPressure:   soap.bloodPressure||null,
            pulseBpm:        parseInt(soap.pulseBpm)||null,
            temperatureC:    parseFloat(soap.temperatureC)||null,
            oxygenSatPct:    parseFloat(soap.oxygenSatPct)||null,
            history:         soap.history||null,
            examination:     soap.examination||null,
            diagnosis:       soap.diagnosis||null,
            icd10Codes:      soap.icd10Codes?soap.icd10Codes.split(",").map((x:string)=>x.trim()).filter(Boolean):[],
            treatmentPlan:   soap.treatmentPlan||null,
            followUpDays:    parseInt(soap.followUpDays)||null,
            durationMinutes, // from timer
          }
        )
        consultId = (consultRes.data?.data ?? consultRes.data).id
      }

      // 2. Save prescriptions (each only once, so a retry after a later failure cannot duplicate them)
      for (const rx of rxDrafts) {
        if (!rx.medicationName.trim() || savedRxRef.current.has(rx.id)) continue
        await apiClient.post(`/api/v1/clinic/consultations/${consultId}/prescriptions`,{
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

      // 2b. Sign the draft (DRAFT -> SIGNED). No email is sent (DEC-CLINIC-002).
      if (draftId) await apiClient.post(`/api/v1/clinic/consultations/${draftId}/sign`)

      // 3. Complete the appointment
      await apiClient.post(`/api/v1/clinic/appointments/${appointment.id}/complete`)

      return consultId
    },
    onSuccess: (consultationId) => {
      qc.invalidateQueries({queryKey:["pf-appointments"]})
      qc.invalidateQueries({queryKey:["pf-consultations"]})
      qc.invalidateQueries({queryKey:["clinic-appts-dashboard"]})
      qc.invalidateQueries({queryKey:["schedule-appts"]})
      qc.invalidateQueries({queryKey:["clinic-patients"]})
      onComplete(consultationId)
    },
    onError: (e:any) => setCompleteError(e.response?.data?.message ?? e.message ?? "Failed to complete consultation"),
  })

  const [step, setStep] = useState<WizardStep>("symptoms")

  return (
    <div style={{ fontFamily:"'Inter',system-ui,sans-serif", height:"100%", display:"flex", flexDirection:"column" }}>
      {dialogs}

      {/* ── Session header ─────────────────────────────────────────────── */}
      <div style={{ background:`linear-gradient(135deg,${NAVY} 0%,var(--hf-primary-deep) 100%)`,
        borderRadius:12, padding:"16px 24px", marginBottom:16,
        display:"flex", justifyContent:"space-between", alignItems:"center", flexWrap:"wrap", gap:12 }}>
        <div style={{ display:"flex", alignItems:"center", gap:16 }}>
          <div style={{ width:48, height:48, borderRadius:"50%",
            background:"rgba(255,255,255,0.15)", display:"flex", alignItems:"center",
            justifyContent:"center", fontSize:18, fontWeight:800, color:"var(--hf-text-on-solid)" }}>
            {patient.fullName.split(" ").map(n=>n[0]).join("").slice(0,2)}
          </div>
          <div>
            <div style={{ display:"flex", alignItems:"center", gap:8, marginBottom:2 }}>
              <span style={{ fontSize:18, fontWeight:800, color:"var(--hf-text-on-solid)" }}>{patient.fullName}</span>
              <span style={{ background:"rgba(13,148,136,0.3)", color:"var(--hf-accent-on-brand)",
                padding:"2px 8px", borderRadius:20, fontSize:11, fontWeight:700 }}>
                {appointment.appointmentType?.replace("_"," ")}
              </span>
            </div>
            <div style={{ fontSize:13, color:"rgba(255,255,255,0.6)" }}>
              {appointment.practitionerName && `Dr. ${appointment.practitionerName}`}
              {appointment.reason && ` · ${appointment.reason}`}
            </div>
            {patient.allergies && patient.allergies.length > 0 && (
              <div style={{ display:"flex", alignItems:"center", gap:4, marginTop:4 }}>
                <AlertCircle size={11} style={{ color: 'var(--hf-danger-on-brand)' }}/>
                <span style={{ fontSize:11, color:"var(--hf-danger-on-brand)", fontWeight:600 }}>
                  ⚠ {patient.allergies.join(", ")}
                </span>
              </div>
            )}
          </div>
        </div>

        <div style={{ display:"flex", alignItems:"center", gap:16 }}>
          {/* Live timer */}
          <div style={{ textAlign:"center" }}>
            <div style={{ display:"flex", alignItems:"center", gap:6,
              background:"rgba(255,255,255,0.1)", borderRadius:10, padding:"8px 16px" }}>
              <div style={{ width:8, height:8, borderRadius:"50%", background:RED,
                animation:"pulse 1.5s infinite" }}/>
              <span style={{ fontSize:22, fontWeight:800, color:"var(--hf-text-on-solid)", fontVariantNumeric:"tabular-nums" }}>
                {fmtTimer(elapsed)}
              </span>
              <span title="Notes are saved to the server as you type"
                style={{ fontSize:11, marginLeft:8, color:"rgba(255,255,255,0.7)" }}>
                {draftState==="saving" ? "Saving…"
                  : draftState==="saved" ? "Draft saved"
                  : draftState==="error" ? "Not saved, check connection" : ""}
              </span>
            </div>
            <div style={{ fontSize:10, color:"rgba(255,255,255,0.5)", marginTop:2 }}>
              {durationMinutes} min
            </div>
          </div>

          {/* Bill total */}
          <div style={{ textAlign:"center",
            background:"rgba(255,255,255,0.1)", borderRadius:10, padding:"8px 16px" }}>
            <div style={{ fontSize:11, color:"rgba(255,255,255,0.6)" }}>Running bill</div>
            <div style={{ fontSize:18, fontWeight:800, color:"var(--hf-text-on-solid)" }}>{fmtR(billTotal)}</div>
          </div>

          {canHandoff && <button onClick={sendToDoctor} disabled={handoffBusy || !draftReady}
            title={handoffError || "Hand this consultation to a doctor; it leaves your drafts until returned"}
            style={{ padding:"10px 16px", background:"rgba(255,255,255,0.12)", color:"var(--hf-text-on-solid)",
              border:`1px solid ${handoffError ? "var(--hf-danger)" : "rgba(255,255,255,0.3)"}`, borderRadius:10,
              fontSize:13, fontWeight:700, cursor:"pointer" }}>
            {handoffBusy ? "Sending…" : "Send to doctor"}
          </button>}
          <button id="consult-complete" onClick={() => { setShowComplete(true); setCompleteError("") }}
            style={{ display:"flex", alignItems:"center", gap:8, padding:"10px 20px",
              background:TEAL, color:"var(--hf-text-on-solid)", border:"none", borderRadius:10,
              fontSize:14, fontWeight:700, cursor:"pointer" }}>
            <CheckCircle size={16}/> Complete
          </button>
          <button onClick={onMinimise}
            title="Minimise — navigate tabs freely"
            style={{ background:"rgba(255,255,255,0.1)", border:"none", borderRadius:8,
              cursor:"pointer", color:"rgba(255,255,255,0.7)", padding:"8px 12px",
              fontSize:12, fontWeight:600, display:"flex", alignItems:"center", gap:4 }}>
            ↓ Minimise
          </button>
          <button onClick={handleCancel}
            title="Discard session"
            style={{ background:"rgba(255,255,255,0.1)", border:"none", borderRadius:8,
              cursor:"pointer", color:"rgba(255,255,255,0.7)", padding:8, display:"flex" }}>
            <X size={18}/>
          </button>
        </div>
      </div>

      {returnNote && (
        <div role="status" style={{ margin:"8px 12px 0", padding:"10px 14px", borderRadius:10,
          background:"var(--hf-warning-soft)", color:"var(--hf-warning-text)", fontSize:13,
          display:"flex", gap:10, alignItems:"flex-start" }}>
          <div style={{ flex:1 }}>
            <strong>Returned by the doctor</strong> ({returnNote.reason}): {returnNote.comment}
          </div>
          <button onClick={() => setReturnNote(null)} aria-label="Dismiss"
            style={{ background:"none", border:"none", cursor:"pointer", color:"inherit" }}>×</button>
        </div>
      )}

      <PatientAlertBanner patientId={patient.id}/>

      <ConsultationStepper steps={consultSteps(soap, rxDrafts)} current={step} onSelect={setStep}/>

      {/* ── One page per step ──────────────────────────────────────────── */}
      <div style={{ flex:1, minHeight:0, display:"flex", flexDirection:"column", gap:10, marginBottom:12 }} data-step={step}>

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
          <StepTitle n={2} title="Examination" help="Vitals and what you found on examination." />
          <VitalsPanel soap={soap} sf={sf}/>
          <SoapFields soap={soap} sf={sf} only={["examination"]}/>
        </>)}

        {step === "diagnose" && (<>
          <StepTitle n={3} title="Diagnose & prescribe" help="Your assessment, the medicines, and what to bill." />
          <div style={{ display:"flex", gap:14, flexWrap:"wrap", alignItems:"flex-start" }}>
            <div style={{ flex:"1 1 340px", display:"flex", flexDirection:"column", gap:10 }}>
              <SoapFields soap={soap} sf={sf} only={["diagnosis","icd10Codes"]}/>
          {/* Medication search — adds to bill + Rx */}
          <div style={{ padding:"12px 14px", background:"var(--hf-surface)", border:`1px solid ${BORDER}`, borderRadius:10, position:"relative" }}>
            <div style={sectionLabel}>Add medication (bill + Rx)</div>
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
            <div style={{ flex:"1 1 300px", display:"flex", flexDirection:"column", gap:10 }}>
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
          </div>

              <LiveBillPanel billLines={billLines} billTotal={billTotal} removeBillLine={removeBillLine} addBillLine={addBillLine}/>
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
          <StepTitle n={5} title="Review & sign" help="Check the record, then complete the consultation." />
          <div style={{ display:"grid", gridTemplateColumns:"repeat(auto-fit,minmax(260px,1fr))", gap:10 }}>
            <Recap title="Symptoms" onEdit={()=>setStep("symptoms")} rows={[["Chief complaint", soap.chiefComplaint], ["History", soap.history]]} />
            <Recap title="Examination" onEdit={()=>setStep("examination")} rows={[["Vitals", vitalsLine(soap)], ["Findings", soap.examination]]} />
            <Recap title="Diagnosis" onEdit={()=>setStep("diagnose")} rows={[["Diagnosis", soap.diagnosis], ["ICD-10", soap.icd10Codes],
              ["Prescriptions", rxDrafts.filter(r=>r.medicationName.trim()).map(r=>[r.medicationName, r.dosage, r.frequency, r.duration].filter(Boolean).join(" ")).join("\n")],
              ["Bill", billLines.length ? `${billLines.length} line${billLines.length===1?"":"s"} · ${fmtR(billTotal)}` : ""]]} />
            <Recap title="Plan" onEdit={()=>setStep("plan")} rows={[["Treatment plan", soap.treatmentPlan], ["Follow-up", soap.followUpDays.trim() ? `In ${soap.followUpDays.trim()} days` : ""]]} />
          </div>
          <div style={{ display:"flex", gap:10, alignItems:"center", flexWrap:"wrap" }}>
            <button onClick={() => { setShowComplete(true); setCompleteError("") }}
              style={{ ...primaryBtn, background:TEAL, display:"flex", alignItems:"center", gap:8 }}>
              <CheckCircle size={15}/> Complete consultation
            </button>
            <span style={{ fontSize:12, color:GRAY_TEXT }}>{consultSteps(soap, rxDrafts).find(x=>x.id==="sign")?.hint}</span>
          </div>
        </>)}
      </div>

      {/* ── Back / Next ─────────────────────────────────────────────────── */}
      <div style={{ display:"flex", justifyContent:"space-between", alignItems:"center", gap:10, padding:"10px 0", borderTop:`1px solid ${BORDER}` }}>
        <button disabled={!prevStep(step)} onClick={()=>prevStep(step) && setStep(prevStep(step)!)}
          style={{ ...cancelBtn, opacity: prevStep(step) ? 1 : 0.4 }}>← Back</button>
        <span style={{ fontSize:12, color:GRAY_TEXT }}>Step {stepNumber(step)} of {WIZARD_ORDER.length}</span>
        {nextStep(step)
          ? <button onClick={()=>setStep(nextStep(step)!)} style={primaryBtn}>Next →</button>
          : <span style={{ width:80 }}/>}
      </div>

      {/* ── Complete modal ─────────────────────────────────────────────────── */}
      {showComplete && (
        <ModalShell title="Complete consultation" onClose={()=>setShowComplete(false)} width={520} footer={<>
              <button onClick={()=>setShowComplete(false)} style={cancelBtn}>Back to session</button>
              <button onClick={()=>complete.mutate()} disabled={complete.isPending}
                style={{...primaryBtn,background:TEAL,display:"flex",alignItems:"center",gap:7}}>
                {complete.isPending
                  ? <><Loader size={14}/> Completing…</>
                  : <><CheckCircle size={14}/> Complete & save</>}
              </button>
        </>}>
            <div style={{display:"grid",gridTemplateColumns:"1fr 1fr",gap:12,marginBottom:16}}>
              {[
                {label:"Duration",    value:`${durationMinutes} minutes`, color:NAVY_TEXT},
                {label:"Bill total",  value:fmtR(billTotal),              color:GREEN_TEXT},
                {label:"Bill items",  value:`${billLines.length} lines`,  color:TEAL_TEXT},
                {label:"Prescriptions",value:`${rxDrafts.length} items`,  color:PURPLE_TEXT},
              ].map(s=>(
                <div key={s.label} style={{padding:"10px 14px",background:LIGHT,borderRadius:8}}>
                  <div style={{fontSize:10,fontWeight:700,color:GRAY_TEXT,textTransform:"uppercase",letterSpacing:"0.05em",marginBottom:2}}>{s.label}</div>
                  <div style={{fontSize:16,fontWeight:800,color:s.color}}>{s.value}</div>
                </div>
              ))}
            </div>

            {!soap.chiefComplaint.trim() && (
              <div style={{marginBottom:12,padding:"8px 12px",background:"var(--hf-warning-soft)",border:"1px solid var(--hf-warning-border)",borderRadius:8,fontSize:12,color:AMBER_TEXT}}>
                ⚠ Chief complaint is empty — add a reason for the visit before completing.
              </div>
            )}
            {rxDrafts.some(rx=>rx.medicationName&&!rx.dosage) && (
              <div style={{marginBottom:12,padding:"8px 12px",background:"var(--hf-info-soft)",border:"1px solid var(--hf-info-border)",borderRadius:8,fontSize:12,color:"var(--hf-info-text)"}}>
                ℹ Some prescriptions are missing dosage details — they will still be saved.
              </div>
            )}

            {completeError && (
              <div style={{marginBottom:12,padding:"8px 12px",background:"var(--hf-danger-soft)",border:"1px solid var(--hf-danger-border)",borderRadius:8,fontSize:12,color:RED_TEXT}}>
                {completeError}
              </div>
            )}

        </ModalShell>
      )}
    </div>
  )
}

// ── Style helpers ─────────────────────────────────────────────────────────────


const chipStyle = (on: boolean): React.CSSProperties => ({ padding:"6px 12px", borderRadius:20, fontSize:12, fontWeight:600, cursor:"pointer",
  border:`1px solid ${on ? "var(--hf-primary)" : BORDER}`, background: on ? "var(--hf-primary-text)" : "var(--hf-surface)", color: on ? "var(--hf-surface)" : "var(--hf-text)" })

function StepTitle({ n, title, help }: { n: number; title: string; help: string }) {
  return (
    <div>
      <h2 style={{ margin:0, fontSize:17, fontWeight:800, color:"var(--hf-text)" }}>{n}. {title}</h2>
      <div style={{ fontSize:12, color:GRAY_TEXT }}>{help}</div>
    </div>
  )
}

function Recap({ title, rows, onEdit }: { title: string; rows: [string, string][]; onEdit: () => void }) {
  return (
    <section aria-label={title} style={{ padding:"12px 14px", background:"var(--hf-surface)", border:`1px solid ${BORDER}`, borderRadius:10 }}>
      <div style={{ display:"flex", justifyContent:"space-between", alignItems:"center", marginBottom:6 }}>
        <span style={sectionLabel}>{title}</span>
        <button type="button" onClick={onEdit} style={{ background:"none", border:"none", color:"var(--hf-accent-text)", fontSize:12, fontWeight:600, cursor:"pointer" }}>Edit</button>
      </div>
      {rows.map(([k, v]) => (
        <div key={k} style={{ marginBottom:6 }}>
          <div style={{ fontSize:11, color:GRAY_TEXT }}>{k}</div>
          {v.trim() ? <div style={{ fontSize:13, color:"var(--hf-text)", whiteSpace:"pre-wrap" }}>{v}</div>
                    : <div style={{ fontSize:13, color:"var(--hf-text-disabled)" }}>Not recorded</div>}
        </div>
      ))}
    </section>
  )
}
