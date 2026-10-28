// How a dismissal ends employment. Mirrors the server (DisciplineRules.endTypeProblem).

export type DismissalEnd = "EMPLOYER_NOTICE" | "SUMMARY_DISMISSAL"

export const DISMISSAL_ENDS: { value: DismissalEnd; label: string }[] = [
  { value: "EMPLOYER_NOTICE", label: "With notice: the employer gives notice, or pays for the days not given" },
  { value: "SUMMARY_DISMISSAL", label: "Summarily: serious misconduct, no notice" },
]

export type EndForm = { lastDay: string; endType: string; noticeGivenOn: string }

/** Poor performance and ill health are dismissed with notice; misconduct is left for the person deciding to choose. */
export const defaultEndType = (caseType: string): string => (caseType === "INCAPACITY" ? "EMPLOYER_NOTICE" : "")

export function endProblem(f: EndForm, told: string | null | undefined, today: string): string | null {
  if (!f.lastDay) return "Give the last day of employment."
  if (told && f.lastDay < told) return "The last day cannot be before the employee was told."
  if (f.lastDay > today) return "The last day cannot be in the future."
  if (f.endType !== "EMPLOYER_NOTICE" && f.endType !== "SUMMARY_DISMISSAL") return "Say how the dismissal ends employment: with notice, or summarily."
  if (f.endType === "EMPLOYER_NOTICE" && f.noticeGivenOn) {
    if (told && f.noticeGivenOn < told) return "Notice cannot be given before the employee was told."
    if (f.noticeGivenOn > f.lastDay) return "Notice cannot be given after the last day worked."
  }
  return null
}

export const endBody = (f: EndForm) => ({
  lastDay: f.lastDay,
  endType: f.endType,
  noticeGivenOn: f.endType === "EMPLOYER_NOTICE" && f.noticeGivenOn ? f.noticeGivenOn : null,
})
