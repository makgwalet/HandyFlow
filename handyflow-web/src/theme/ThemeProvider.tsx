// src/theme/ThemeProvider.tsx
//
// Loads appearance preferences from the server, applies them to <html>, and
// saves changes optimistically:
//   - a change is applied immediately (local overrides),
//   - saves are debounced and serialised, always sending the latest state,
//     so rapid toggles never race each other on the backend's @Version,
//   - on failure the last server state is restored and saveError is set.
//
// Until the first load completes, the DOM is left exactly as the pre-paint
// script in index.html set it, so a dark-mode user never sees a light flash.
import { useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { isAxiosError } from 'axios'
import { useAuthStore } from '../store/auth.store'
import { uiPreferencesApi } from '../api/uiPreferences.api'
import type {
  UiPreferences, UpdateTenantUiPreferencesRequest, UserUiOverrides,
} from '../types/uiPreferences.types'
import { ThemeContext, type ThemeContextValue } from './ThemeContext'
import { EMPTY_OVERRIDES, SYSTEM_DEFAULTS, resolveEffective } from './resolve'
import { applyAppearance, systemPrefersDark, type ResolvedTheme } from './applyTheme'

const SAVE_DEBOUNCE_MS = 400

function errorMessage(e: unknown): string {
  if (isAxiosError(e)) {
    const msg = (e.response?.data as { message?: string } | undefined)?.message
    if (msg) return msg
    if (!e.response) return 'Could not reach the server. Your change was not saved.'
  }
  return 'Your change could not be saved. Please try again.'
}

export function ThemeProvider({ children }: { children: ReactNode }) {
  const token = useAuthStore(s => s.token)
  const userId = useAuthStore(s => s.user?.userId ?? null)
  const queryClient = useQueryClient()
  const queryKey = useMemo(() => ['ui-preferences', userId] as const, [userId])

  const query = useQuery({
    queryKey,
    queryFn: uiPreferencesApi.get,
    enabled: !!token,
    staleTime: 5 * 60_000,
    retry: 1,
  })

  const [localUser, setLocalUser] = useState<UserUiOverrides | null>(null)
  const [saveError, setSaveError] = useState<string | null>(null)
  const [isSaving, setIsSaving] = useState(false)
  const [systemDark, setSystemDark] = useState(systemPrefersDark)

  const data = query.data
  const isLoaded = !!data
  const isSupportSession = isLoaded && data.user === null
  const tenant = data?.tenant ?? SYSTEM_DEFAULTS
  const user = localUser ?? data?.user ?? null
  const prefs = useMemo(() => resolveEffective(tenant, user), [tenant, user])

  const resolvedTheme: ResolvedTheme =
    prefs.theme === 'SYSTEM' ? (systemDark ? 'dark' : 'light') : prefs.theme === 'DARK' ? 'dark' : 'light'

  // Follow the OS setting live when the user chose SYSTEM.
  useEffect(() => {
    const mq = window.matchMedia?.('(prefers-color-scheme: dark)')
    if (!mq) return
    const onChange = (e: MediaQueryListEvent) => setSystemDark(e.matches)
    mq.addEventListener('change', onChange)
    return () => mq.removeEventListener('change', onChange)
  }, [])

  useEffect(() => {
    if (!isLoaded) return
    applyAppearance({
      theme: resolvedTheme,
      themeMode: prefs.theme,
      brand: prefs.brandColor,
      sidebar: prefs.sidebar,
      container: prefs.container,
    })
  }, [isLoaded, resolvedTheme, prefs.theme, prefs.brandColor, prefs.sidebar, prefs.container])

  // A different user (or logout) must not inherit someone else's pending
  // edits. Reset during render (React's recommended pattern for resetting
  // state when an input changes) rather than in an effect.
  const [stateOwner, setStateOwner] = useState(userId)
  if (stateOwner !== userId) {
    setStateOwner(userId)
    setLocalUser(null)
    setSaveError(null)
  }

  // ── Debounced, serialised saving ────────────────────────────────────────
  const latest = useRef<UserUiOverrides | null>(null)
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null)
  const chain = useRef<Promise<void>>(Promise.resolve())
  const seq = useRef(0)

  useEffect(() => () => { if (timer.current) clearTimeout(timer.current) }, [])

  const flush = useCallback(() => {
    const body = latest.current
    if (!body) return
    const mySeq = ++seq.current
    setIsSaving(true)
    chain.current = chain.current.then(async () => {
      try {
        const saved = await uiPreferencesApi.updateMine(body)
        queryClient.setQueryData<UiPreferences>(queryKey, saved)
        if (mySeq === seq.current) {
          setLocalUser(null)
          setSaveError(null)
        }
      } catch (e) {
        if (mySeq === seq.current) {
          setLocalUser(null) // fall back to the last state the server confirmed
          setSaveError(errorMessage(e))
        }
      } finally {
        if (mySeq === seq.current) setIsSaving(false)
      }
    })
  }, [queryClient, queryKey])

  const updateMine = useCallback((change: Partial<UserUiOverrides>) => {
    const base = localUser ?? data?.user ?? EMPTY_OVERRIDES
    const next: UserUiOverrides = { ...base, ...change }
    setLocalUser(next)
    // Support sessions and logged-out states apply locally only.
    if (!token || isSupportSession) return
    latest.current = next
    if (timer.current) clearTimeout(timer.current)
    timer.current = setTimeout(flush, SAVE_DEBOUNCE_MS)
  }, [localUser, data?.user, token, isSupportSession, flush])

  const updateTenant = useCallback(async (req: UpdateTenantUiPreferencesRequest) => {
    setIsSaving(true)
    try {
      const saved = await uiPreferencesApi.updateTenant(req)
      queryClient.setQueryData<UiPreferences>(queryKey, saved)
      setSaveError(null)
    } catch (e) {
      setSaveError(errorMessage(e))
      throw e
    } finally {
      setIsSaving(false)
    }
  }, [queryClient, queryKey])

  const value: ThemeContextValue = {
    prefs,
    resolvedTheme,
    tenant,
    user: isSupportSession ? null : user,
    canEditTenant: data?.canEditTenant ?? false,
    isSupportSession,
    isLoaded,
    isSaving,
    saveError,
    updateMine,
    updateTenant,
  }

  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>
}
