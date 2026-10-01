// src/navigation/actionUrl.ts
//
// Where a notification's actionUrl may send the user.
//
// FIX: "clicking a notification redirects to dashboard" — confirmed root
// cause via App.tsx: <Route path="*" element={<Navigate to="/dashboard" />} />
// is a catch-all, and several modules (Clinic, Projects' list view, etc.)
// are single-page shells with internal tab state, not real sub-routes —
// there's no /clinic/claims or /clinic/appointments route at all, only
// /clinic itself. Any notification whose actionUrl pointed one level
// deeper than a module's real route was silently hitting that catch-all
// and bouncing to dashboard, on every single click, regardless of which
// notification — matching exactly what was reported.
//
// This list is the exact set of top-level module routes from App.tsx's
// <ModuleLayout> route group (plus /dashboard) — not guessed, copied
// directly from that file. Deliberately checking only the FIRST path
// segment rather than trying to replicate React Router's full matching
// (including dynamic segments like /quotes/:id, /projects/:id): if the
// actionUrl's base segment is a real route, land on that module's page —
// even if the deeper path wasn't real, landing on the right module is a
// far better outcome than bouncing to dashboard. If the base segment
// itself isn't recognized, that's a genuine dead link, and dashboard is
// the correct fallback for that case.
//
// Update: the allow-list used to be a hand-kept copy of the routes, which drifted (the booking, payroll
// and recruitment agencies, Compliance Services and Control Exceptions were missing, so their
// notifications bounced to the dashboard). It is now derived from the navigation registry. The base is
// read from the PATH only: "/tasks?board=1&task=2" has the base "tasks", not "tasks?board=1&task=2".
import { MODULE_REGISTRY, WORKSPACE_NAV } from './modules'

const baseOf = (route: string) => route.split('/').filter(Boolean)[0]

const KNOWN_ROUTES = new Set<string>([
  'dashboard', 'invite', 'profile',   // real routes that are not modules
  ...Object.values(MODULE_REGISTRY).flatMap(m => [m.route, ...(m.aliases ?? [])]).map(baseOf),
  ...WORKSPACE_NAV.map(w => baseOf(w.route)),
])

// Routes whose deeper path or query string is real and must survive:
//   quotes, projects : /quotes/:id, /projects/<id> (the Projects page redirects an id to its detail)
//   tasks            : /tasks?board=&task= opens that task
const PRESERVES_DEEP_PATH = new Set(['quotes', 'projects', 'tasks'])

/** Returns a safe URL to navigate to, or null if actionUrl doesn't target any known route at all. */
export function resolveSafeActionUrl(actionUrl: string): string | null {
  const path = actionUrl.split(/[?#]/)[0]
  const segments = path.split('/').filter(Boolean)
  if (segments.length === 0) return null
  const base = segments[0]
  if (!KNOWN_ROUTES.has(base)) return null
  return PRESERVES_DEEP_PATH.has(base) ? actionUrl : '/' + base
}
