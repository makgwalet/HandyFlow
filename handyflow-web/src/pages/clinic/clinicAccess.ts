// Client-side mirror of the Clinic permission catalogue (platform: ClinicPermission.java). Screens ask for the specific
// capability ("can this person sign?"), not the old module-wide permission. The server is the real check; this only
// avoids showing a button whose click would answer 403.
//
// A person holds a capability if they have its permission, or the coarse permission it replaced (V363 gave everyone who
// had the coarse one the new one, so this only matters before they sign in again after the upgrade).
import { usePermission } from "../../hooks/usePermission"

/** The capabilities screens check today, each with the coarse permission it replaced. */
export const CAN = {
  signConsultation:   { fine: "CLINIC_CONSULTATION_SIGN",       legacy: "CLINIC_CLINICAL_SIGN" },
  editConsultation:   { fine: "CLINIC_CONSULTATION_UPDATE",     legacy: "CLINIC_CLINICAL_WRITE" },
  startConsultation:  { fine: "CLINIC_CONSULTATION_CREATE",     legacy: "CLINIC_CLINICAL_WRITE" },
  addendum:           { fine: "CLINIC_CONSULTATION_AMEND",      legacy: "CLINIC_CLINICAL_WRITE" },
  acceptHandoff:      { fine: "CLINIC_NURSE_HANDOFF_ACCEPT",    legacy: "CLINIC_CLINICAL_SIGN" },
  editClinicalSummary:{ fine: "CLINIC_ALLERGY_WRITE",           legacy: "CLINIC_CLINICAL_WRITE" },
  addNote:            { fine: "CLINIC_NOTE_CREATE",             legacy: "CLINIC_CLINICAL_WRITE" },
  reviewResult:       { fine: "CLINIC_RESULT_REVIEW",           legacy: "CLINIC_LAB_WRITE" },
  dispense:           { fine: "CLINIC_PRESCRIPTION_DISPENSE",   legacy: "CLINIC_PRESCRIPTION_WRITE" },
  checkIn:            { fine: "CLINIC_APPOINTMENT_UPDATE",      legacy: "CLINIC_WRITE" },
  manageRooms:        { fine: "CLINIC_ROOM_MANAGE",             legacy: "CLINIC_ADMIN" },
  manageClosures:     { fine: "CLINIC_CLOSURE_MANAGE",          legacy: "CLINIC_ADMIN" },
  editWorkingHours:   { fine: "CLINIC_WORKING_HOURS_WRITE",     legacy: "CLINIC_WRITE" },
  editTimeOff:        { fine: "CLINIC_TIME_OFF_WRITE",          legacy: "CLINIC_WRITE" },
  viewAccessLog:      { fine: "CLINIC_ACCESS_LOG_READ",         legacy: "CLINIC_ADMIN" },
} as const

export type Capability = keyof typeof CAN

/** True if the permission list holds the capability (new permission or the coarse one it replaced). */
export function holds(permissions: readonly string[], cap: Capability): boolean {
  const c = CAN[cap]
  return permissions.includes(c.fine) || permissions.includes(c.legacy)
}

/** Can the signed-in person do this? Always calls the same hooks in the same order. */
export function useCan(cap: Capability): boolean {
  const fine = usePermission(CAN[cap].fine)
  const legacy = usePermission(CAN[cap].legacy)
  return fine || legacy
}
