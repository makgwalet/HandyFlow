// src/navigation/moduleSections.ts
//
// Modules with many sections register them here. Inside such a module the
// sidebar switches to "context mode": the module's grouped sections replace
// the global module list, and each section is a real route
// (`${basePath}/${section.id}`), so it can be deep-linked, bookmarked and
// reached with the back button.
//
// Kept separate from the page components so the sidebar never has to import
// (and bundle) a module's pages just to render its navigation.
import type { ElementType } from 'react'
import {
  AlertTriangle, Camera, ClipboardList, Clock, Crosshair, DoorOpen, FileBarChart,
  GitBranch, Key, LayoutDashboard, Lock, MapPin, Radio, RefreshCw, Repeat, Route,
  Shield, ShieldCheck, Siren, Tablet, DollarSign,
} from 'lucide-react'

export interface ModuleSection {
  id: string
  label: string
  icon: ElementType
  /** Short live/status marker shown next to the label, e.g. "LIVE". */
  badge?: string
}

export interface ModuleSectionGroup {
  label: string
  sections: ModuleSection[]
}

export interface ModuleSections {
  /** Key in MODULE_REGISTRY. */
  moduleKey: string
  basePath: string
  title: string
  icon: ElementType
  defaultSection: string
  groups: ModuleSectionGroup[]
}

export const SECURITY_SECTIONS: ModuleSections = {
  moduleKey: 'security',
  basePath: '/security',
  title: 'Security',
  icon: Shield,
  defaultSection: 'dashboard',
  groups: [
    {
      label: 'Overview',
      sections: [
        { id: 'dashboard', label: 'Dashboard', icon: LayoutDashboard },
        { id: 'control-room', label: 'Control Room', icon: Siren, badge: 'LIVE' },
        { id: 'live', label: 'Live Map', icon: Radio },
      ],
    },
    {
      label: 'Operations',
      sections: [
        { id: 'shifts', label: 'Shifts', icon: Clock },
        { id: 'incidents', label: 'Incidents', icon: AlertTriangle },
        { id: 'patrol-routes', label: 'Patrol Routes', icon: Route },
        { id: 'post-orders', label: 'Post Orders', icon: ClipboardList },
        { id: 'gate-access', label: 'Gate Access', icon: DoorOpen },
      ],
    },
    {
      label: 'Workforce',
      sections: [
        { id: 'guards', label: 'Guards', icon: Shield },
        { id: 'guard-screening', label: 'Guard Screening', icon: ShieldCheck },
        { id: 'rotation-patterns', label: 'Rotation Patterns', icon: RefreshCw },
        { id: 'shift-swaps', label: 'Shift Swaps', icon: Repeat },
        { id: 'payroll', label: 'Payroll', icon: DollarSign },
      ],
    },
    {
      label: 'Sites & assets',
      sections: [
        { id: 'sites', label: 'Sites', icon: MapPin },
        { id: 'branches', label: 'Branches', icon: GitBranch },
        { id: 'armoury', label: 'Armoury', icon: Crosshair },
        { id: 'cctv', label: 'CCTV', icon: Camera },
        { id: 'sessions', label: 'Devices', icon: Tablet },
      ],
    },
    {
      label: 'Services',
      sections: [
        { id: 'close-protection', label: 'Close Protection', icon: Lock },
      ],
    },
    {
      label: 'Insights & integration',
      sections: [
        { id: 'reports', label: 'Reports', icon: FileBarChart },
        { id: 'public-api', label: 'Public API', icon: Key },
      ],
    },
  ],
}

const REGISTRY: ModuleSections[] = [SECURITY_SECTIONS]

/** Section config for the module whose base path contains `pathname`. */
export function sectionsForPath(pathname: string): ModuleSections | undefined {
  return REGISTRY.find(m => pathname === m.basePath || pathname.startsWith(m.basePath + '/'))
}

export function findSection(config: ModuleSections, id: string | undefined):
  { section: ModuleSection; group: ModuleSectionGroup } | undefined {
  if (!id) return undefined
  for (const group of config.groups) {
    const section = group.sections.find(s => s.id === id)
    if (section) return { section, group }
  }
  return undefined
}

/**
 * Old in-page tab ids that were renamed when sections became routes, so old
 * bookmarks and hard-coded navigate() calls still land in the right place.
 */
export const SECURITY_SECTION_ALIASES: Record<string, string> = {
  'cp-overview': 'close-protection',
  'admin-overview': 'dashboard',
}
