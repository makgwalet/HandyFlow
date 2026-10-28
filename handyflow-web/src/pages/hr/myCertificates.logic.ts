// Wording and file names for the employee's own tax certificates.
export interface MineCertificate {
  id: string; taxYear: number; certificateNumber: string; certificateType: string; periodFrom: string | null; periodTo: string | null; releasedAt: string | null
}

/** "IRP5 for the 2027 tax year" or "IT3(a) for the 2027 tax year". */
export const certificateTitle = (c: Pick<MineCertificate, "certificateType" | "taxYear">): string =>
  `${c.certificateType === "IT3A" ? "IT3(a)" : c.certificateType} for the ${c.taxYear} tax year`

export const certificateFileName = (c: Pick<MineCertificate, "certificateNumber" | "certificateType" | "taxYear">): string =>
  `${c.certificateType}-${c.taxYear}-${c.certificateNumber}`.replace(/[^A-Za-z0-9._-]+/g, "_") + ".pdf"
