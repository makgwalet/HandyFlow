// What a user limited to some sites can do on the Reports screen. The server decides; this only keeps the screen honest about it.

export interface MyScope { restricted?: boolean }

/** True only when the server says so. No answer (an older server, a failed call) is treated as the whole company, never as limited. */
export function isLimited(scope: MyScope | null | undefined): boolean { return scope?.restricted === true }

export function monthlyOptionLabel(limited: boolean): string { return limited ? "Monthly Summary (your sites)" : "Monthly Summary (all sites)" }

/** The guard attendance report spans every guard, so the server only gives it to a user with the whole company. */
export function reportAvailable(limited: boolean, key: string): boolean { return !(limited && key === "guard-attendance") }

/** The monthly summary PDF is titled company-wide, so the server only gives it to a user with the whole company. */
export function pdfAvailable(limited: boolean, key: string): boolean { return !(limited && key === "monthly-summary") }

export const LIMITED_NOTE = "You see the reports for your sites. Guard attendance, and the PDF of the monthly summary, are for the whole company."
export const PDF_FAILED = "The PDF could not be downloaded. Try again."
