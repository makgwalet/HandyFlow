import { useState } from "react"

/**
 * Lets a submit button stay clickable. When the form has a problem, a click shows what is
 * missing instead of doing nothing; once the problem is fixed the message goes away.
 */
export function useSubmitGuard(problem: string | null | undefined) {
  const [tried, setTried] = useState(false)
  return {
    shown: tried && problem ? problem : null,
    guard: (go: () => void) => () => {
      if (problem) { setTried(true); return }
      setTried(false)
      go()
    },
  }
}
