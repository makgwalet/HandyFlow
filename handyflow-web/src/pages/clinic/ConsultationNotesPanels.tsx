// src/pages/clinic/ConsultationNotesPanels.tsx
// The vitals box and the SOAP note fields of the live consultation session.
// Moved out of ConsultationSession.tsx; the BMI is now only shown for real numbers.

import { BORDER, GRAY_TEXT, lbl, sectionLabel, sinp } from "./consultationSession.shared"

export interface NotesState {
  chiefComplaint: string; history: string; examination: string; diagnosis: string
  icd10Codes: string; treatmentPlan: string; followUpDays: string
  weightKg: string; heightCm: string; bloodPressure: string
  pulseBpm: string; temperatureC: string; oxygenSatPct: string
}

/** BMI to one decimal, or null when weight or height is missing, not a number, or not above zero. */
export function bmi(weightKg: string, heightCm: string): string | null {
  const w = parseFloat(weightKg), h = parseFloat(heightCm)
  if (!(w > 0) || !(h > 0)) return null
  return (w / Math.pow(h / 100, 2)).toFixed(1)
}

interface PanelProps { soap: NotesState; sf: (k: keyof NotesState, v: string) => void }

export function VitalsPanel({ soap, sf }: PanelProps) {
  const bmiText = bmi(soap.weightKg, soap.heightCm)
  return (
          <div style={{ padding:"12px 14px", background:"var(--hf-surface)", border:`1px solid ${BORDER}`, borderRadius:10 }}>
            <div style={sectionLabel}>Vitals</div>
            <div style={{ display:"grid", gridTemplateColumns:"repeat(3,1fr)", gap:8 }}>
              {[
                {k:"weightKg",     label:"Weight (kg)", placeholder:"82"},
                {k:"heightCm",     label:"Height (cm)", placeholder:"175"},
                {k:"bloodPressure",label:"BP",          placeholder:"120/80"},
                {k:"pulseBpm",     label:"Pulse (bpm)", placeholder:"72"},
                {k:"temperatureC", label:"Temp (°C)",   placeholder:"36.6"},
                {k:"oxygenSatPct", label:"SpO₂ (%)",   placeholder:"98"},
              ].map(f=>(
                <div key={f.k}>
                  <label style={lbl}>{f.label}</label>
                  <input value={(soap as any)[f.k]}
                    onChange={e=>sf(f.k as keyof typeof soap,e.target.value)}
                    placeholder={f.placeholder} style={{...sinp,padding:"6px 8px",fontSize:12}}/>
                </div>
              ))}
            </div>
            {bmiText && (
              <div style={{ marginTop:6, fontSize:11, color:GRAY_TEXT }}>BMI: {bmiText}</div>
            )}
          </div>
  )
}

export function SoapFields({ soap, sf }: PanelProps) {
  return (
          <div style={{ flex:1, overflowY:"auto", display:"flex", flexDirection:"column", gap:8 }}>
            {([
              {k:"chiefComplaint", label:"Chief complaint *", rows:1, ph:"Main reason for visit"},
              {k:"history",        label:"History (S)",       rows:2, ph:"Subjective — patient history"},
              {k:"examination",    label:"Examination (O)",   rows:2, ph:"Objective — physical findings"},
              {k:"diagnosis",      label:"Diagnosis (A)",     rows:2, ph:"Assessment — working diagnosis"},
              {k:"icd10Codes",     label:"ICD-10 codes",      rows:1, ph:"J06.9, Z00.0"},
              {k:"treatmentPlan",  label:"Treatment plan (P)",rows:2, ph:"Plan — management and treatment"},
              {k:"followUpDays",   label:"Follow-up (days)",  rows:1, ph:"7"},
            ] as any[]).map((f:any)=>(
              <div key={f.k}>
                <label style={lbl}>{f.label}</label>
                {f.rows===1
                  ? <input value={(soap as any)[f.k]} onChange={e=>sf(f.k as keyof NotesState,e.target.value)}
                      placeholder={f.ph} style={sinp}/>
                  : <textarea value={(soap as any)[f.k]} onChange={e=>sf(f.k as keyof NotesState,e.target.value)}
                      rows={f.rows} placeholder={f.ph}
                      style={{...sinp,resize:"vertical" as const}}/>
                }
              </div>
            ))}
          </div>
  )
}
