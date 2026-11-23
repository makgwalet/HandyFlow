// src/pages/agriculture/AgLinkedFarm.tsx
// Opens the farm a link names (a notification, say), then hands over to the usual farm page.
import { useQuery } from "@tanstack/react-query"
import { useNavigate } from "react-router-dom"
import { apiClient } from "../../api/client"
import type { FarmResponse } from "./AgFarmsTab"
import AgFarmDetail from "./AgFarmDetail"
import type { FarmLink } from "./agDeepLink.logic"

export default function AgLinkedFarm({ link }: { link: FarmLink }) {
  const navigate = useNavigate()
  const { data: farm, isLoading, isError } = useQuery<FarmResponse>({
    queryKey: ["ag-farm", link.farmId], retry: false,
    queryFn: async () => (await apiClient.get(`/api/v1/agriculture/farms/${link.farmId}`)).data,
  })
  if (isLoading) return <p style={{ fontSize: 13, color: "var(--hf-text-faint)" }}>Opening the farm…</p>
  if (isError || !farm) {
    return (
      <p role="alert" style={{ fontSize: 13, color: "var(--hf-text-muted)" }}>
        That farm can't be opened. It may have been removed, or you may not have access.{" "}
        <button type="button" onClick={() => navigate("/agriculture/farms", { replace: true })} style={{ background: "none", border: "none", textDecoration: "underline", cursor: "pointer", color: "inherit", fontWeight: 700 }}>Go to your farms</button>
      </p>
    )
  }
  return <AgFarmDetail farm={farm} link={link} onBack={() => navigate("/agriculture/farms")} />
}
