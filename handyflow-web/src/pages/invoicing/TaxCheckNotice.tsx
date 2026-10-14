// src/pages/invoicing/TaxCheckNotice.tsx
//
// Finance patch F14: what is missing from the business's or the customer's details for a compliant tax invoice (VAT Act
// section 20). Shown on an issued invoice; says nothing when there is nothing to say.
import { useQuery } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { Notice } from "./ui"

export interface TaxIssue { severity: "ERROR" | "WARNING" | "INFO"; message: string }

/** Keeps only well-formed issues, worst first, so a stray response never breaks the page. */
export function cleanIssues(raw: unknown): TaxIssue[] {
  if (!Array.isArray(raw)) return []
  const rank = { ERROR: 0, WARNING: 1, INFO: 2 } as const
  return raw.filter((i): i is TaxIssue => !!i && typeof i.message === "string" && i.severity in rank)
    .sort((a, b) => rank[a.severity] - rank[b.severity])
}

export default function TaxCheckNotice({ invoiceId }: { invoiceId: string }) {
  const { data } = useQuery<TaxIssue[]>({
    queryKey: ["tax-document-check", invoiceId],
    queryFn: async () => {
      const r = await apiClient.get(`/api/v1/invoicing/invoices/${invoiceId}/tax-document-check`)
      return cleanIssues(r.data?.data ?? r.data)
    },
    retry: false,
  })
  if (!data || data.length === 0) return null
  return (
    <div data-testid="tax-check" style={{ marginBottom: 14, display: "grid", gap: 8 }}>
      {data.map((i, n) => <Notice key={n} tone={i.severity === "ERROR" ? "bad" : i.severity === "WARNING" ? "warn" : "info"}>{i.message}</Notice>)}
    </div>
  )
}
