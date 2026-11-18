import { useEffect, useState } from "react"
import { useQuery } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { inputStyle, labelStyle } from "../warehousing/whseUi"
import { unwrap } from "./opsPolicyOps"

const OPTIONS = [
  { code: "", label: "Attended: a person receives the fuel and signs" },
  { code: "UNATTENDED_SITE", label: "Unattended: the site is never staffed" },
  { code: "AUTOMATED_RECEIVING", label: "Automated receiving: the site receives by itself" },
]

/** Saves a site's receiving arrangement. Only the two site-level exceptions can be set here; one-off exceptions are approved on the delivery. */
export function savePodDesignation(siteId: string | undefined, value: string) {
  if (!siteId) return Promise.resolve()
  return apiClient.put(`/api/v1/fuel/customer-sites/${siteId}/pod-exception`, { exceptionType: value || null })
}

/**
 * How this site receives fuel. An unattended or automated site lets a driver finish a delivery without a receiver, with a reason and
 * evidence, if the company's POD policy allows that exception. Needs the configuration permission; the server checks it again.
 */
export function SitePodField({ siteId, onChange }: { siteId?: string; onChange: (value: string, changed: boolean) => void }) {
  const q = useQuery<string | null>({ queryKey: ["fuel-site-pod", siteId], enabled: !!siteId, retry: false, queryFn: async () => unwrap(await apiClient.get(`/api/v1/fuel/customer-sites/${siteId}/pod-exception`)) ?? null })
  const [value, setValue] = useState("")
  useEffect(() => { if (q.isSuccess) setValue(q.data ?? "") }, [q.isSuccess, q.data])
  const original = q.data ?? ""
  return (
    <div>
      <label style={labelStyle} htmlFor="site-pod">How this site receives fuel</label>
      <select id="site-pod" aria-label="How this site receives fuel" value={value} onChange={e => { setValue(e.target.value); onChange(e.target.value, e.target.value !== original) }} style={inputStyle}>
        {OPTIONS.map(o => <option key={o.code} value={o.code}>{o.label}</option>)}
      </select>
      <div style={{ fontSize: 11, color: "var(--hf-text-muted)", marginTop: 3 }}>An unattended or automated site lets a driver finish a delivery with no receiver, with a reason and evidence on the load. The company's proof-of-delivery policy must allow that exception.</div>
      {q.isError && <div role="alert" style={{ fontSize: 11.5, color: "var(--hf-danger-text)" }}>Could not load this site's setting.</div>}
    </div>
  )
}
