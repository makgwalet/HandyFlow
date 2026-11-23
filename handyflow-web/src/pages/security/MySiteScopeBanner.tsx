// src/pages/security/MySiteScopeBanner.tsx
//
// Tells a user whose access is limited to some sites that they are seeing part of the company. Nothing for everyone else.
import { useQuery } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { limitedBanner, type ScopeView } from "./siteScopeLogic"

export default function MySiteScopeBanner() {
  const { data } = useQuery<ScopeView>({
    queryKey: ["my-site-scope"], staleTime: 60_000, retry: false,
    queryFn: async () => { const r = await apiClient.get("/api/v1/security/my-site-scope"); return (r.data?.data ?? r.data) as ScopeView },
  })
  const text = limitedBanner(data)
  if (!text) return null
  return <div role="status" style={{ background: "var(--hf-info-soft)", color: "var(--hf-info-text)", border: "1px solid var(--hf-info-border)", borderRadius: 10, padding: "8px 12px", fontSize: 13, marginBottom: 12 }}>{text}</div>
}
