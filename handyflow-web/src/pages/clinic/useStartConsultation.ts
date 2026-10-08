// Starting (or resuming) a consultation from the patient file: uses today's appointment, or creates a walk-in, moves it
// along to in-progress, and hands the appointment to the workspace. Shared by the overview and the Visits tab.
import { useState } from "react"
import { useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { startPlan } from "./briefing"

const unwrap = (r: any) => r.data?.data ?? r.data

export function useStartConsultation({ patientId, appointments, defaultPractitionerId, onStartSession }: {
  patientId: string; appointments: any[]; defaultPractitionerId?: string; onStartSession: (appt: any) => void
}) {
  const qc = useQueryClient()
  const [error, setError] = useState("")
  const plan = startPlan(appointments, new Date())

  const begin = useMutation({
    mutationFn: async () => {
      let appt = plan.appt
      if (!appt) {
        const res = await apiClient.post("/api/v1/clinic/appointments", {
          patientId, practitionerId: defaultPractitionerId || null, scheduledAt: new Date().toISOString(),
          durationMinutes: 30, appointmentType: "CONSULTATION", reason: "Walk-in",
        })
        appt = unwrap(res)
      }
      for (const step of plan.steps) appt = unwrap(await apiClient.post(`/api/v1/clinic/appointments/${appt!.id}/${step}`)) ?? appt
      return appt
    },
    onSuccess: appt => {
      setError("")
      qc.invalidateQueries({ queryKey: ["pf-appointments", patientId] })
      qc.invalidateQueries({ queryKey: ["pf-briefing", patientId] })
      qc.invalidateQueries({ queryKey: ["pf-visits", patientId] })
      onStartSession(appt)
    },
    onError: (e: any) => setError(e.response?.data?.message ?? "Could not start the consultation"),
  })
  return { plan, begin, error }
}

/** The button text for the plan; {@code openDraft} is true when the briefing knows an unsigned visit exists. */
export function startLabel(kind: "resume" | "start" | "walk-in", openDraft: boolean, pending: boolean): string {
  if (pending) return "Starting…"
  if (kind === "resume" || openDraft) return "Resume consultation"
  return kind === "walk-in" ? "Start walk-in consultation" : "Start consultation"
}
