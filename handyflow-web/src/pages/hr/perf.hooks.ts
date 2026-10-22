// Data hooks for the performance screens. Every query key starts with "perf", so one invalidation refreshes whatever changed.
import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { BASE, errorText, unwrap, type Me } from "./perf.shared"

/** A GET that returns a list (or null on an empty answer). */
export function usePerfList<T>(key: unknown[], path: string, enabled = true) {
  return useQuery<T[]>({ queryKey: ["perf", ...key], enabled, queryFn: async () => unwrap<T[]>(await apiClient.get(`${BASE}${path}`)) ?? [] })
}

export function usePerfOne<T>(key: unknown[], path: string, enabled = true) {
  return useQuery<T>({ queryKey: ["perf", ...key], enabled, queryFn: async () => unwrap<T>(await apiClient.get(`${BASE}${path}`)) })
}

export const useMe = () => usePerfOne<Me>(["me"], "/me")

/**
 * A change to send: post, put or delete to the path with a body. On success everything performance-related is refreshed and {@code onDone} gets the
 * answer; on failure the server's message is kept in {@code error}.
 */
export function useSend<TBody, TRes = unknown>(method: "post" | "put" | "delete", path: string, fallback: string, onDone?: (r: TRes) => void) {
  const qc = useQueryClient()
  const [error, setError] = useState<string | null>(null)
  const m = useMutation({
    mutationFn: async (body: TBody) => unwrap<TRes>(method === "delete" ? await apiClient.delete(`${BASE}${path}`) : await apiClient[method](`${BASE}${path}`, body)),
    onSuccess: r => { setError(null); qc.invalidateQueries({ queryKey: ["perf"] }); onDone?.(r) },
    onError: e => setError(errorText(e, fallback)),
  })
  return { send: (body: TBody) => { setError(null); m.mutate(body) }, pending: m.isPending, error, setError }
}
