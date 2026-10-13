// Downloading a visit's prescription, with the reissue question when it changed since it was issued (patch 0203).
import { useState } from "react"
import { useDialogs } from "./dialogs"
import { downloadPrescription } from "./issuedDocs"

export function usePrescriptionDownload() {
  const { prompt, dialogs } = useDialogs()
  const [error, setError] = useState("")
  const [busy, setBusy] = useState(false)

  const download = async (consultationId: string) => {
    setError(""); setBusy(true)
    try {
      const problem = await downloadPrescription(consultationId, (number, message) => prompt({
        title: "Reissue the prescription?",
        body: <>
          <p style={{ margin: "0 0 8px" }}>{message}</p>
          <p style={{ margin: 0 }}>Reissuing gives it a new number and withdraws {number ?? "the earlier copy"}, so only one valid prescription is in circulation. The pharmacy's check of the old number will say it was withdrawn.</p>
        </>,
        label: "Reason for reissuing", placeholder: "For example: medicine added after signing", confirmLabel: "Reissue",
      }))
      if (problem) setError(problem)
    } finally { setBusy(false) }
  }

  return { download, error, busy, dialogs, clearError: () => setError("") }
}
