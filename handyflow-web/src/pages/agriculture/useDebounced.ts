// src/pages/agriculture/useDebounced.ts
import { useEffect, useState } from "react"

/** The value, but only after it has stopped changing for `ms`, so typing in a search box asks the server once and not per key. */
export function useDebounced<T>(value: T, ms = 300): T {
  const [v, setV] = useState(value)
  useEffect(() => {
    const t = setTimeout(() => setV(value), ms)
    return () => clearTimeout(t)
  }, [value, ms])
  return v
}
