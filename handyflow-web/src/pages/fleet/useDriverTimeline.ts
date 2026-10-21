import { useQuery } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { unwrapList } from "./apiShape"
import { buildDriverTimeline, type TimelineAccident, type TimelineDoc } from "./driverTimeline.logic"

/** The documents of a driver including replaced versions, newest first, so the timeline can say what changed. */
export function useDriverTimeline(driverId: string, createdAt: string | null | undefined, accidents: TimelineAccident[]) {
  const docs = useQuery<TimelineDoc[]>({ queryKey: ["fleet", "driver-documents", driverId, "history"], queryFn: async () => unwrapList<TimelineDoc>(await apiClient.get(`/api/v1/fleet/drivers/${driverId}/documents?includeSuperseded=true`)) })
  return { items: buildDriverTimeline({ createdAt, documents: docs.data ?? [], accidents }), isLoading: docs.isLoading, isError: docs.isError }
}
