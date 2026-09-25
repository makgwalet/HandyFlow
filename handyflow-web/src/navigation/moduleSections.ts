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
  Shield, ShieldCheck, Siren, Tablet, DollarSign, Wheat, Tractor, PawPrint,
  Droplets, ArrowDownToLine, Fuel, Truck, Users, TrendingUp, Car, Wrench,
} from 'lucide-react'

export interface ModuleSection {
  id: string
  label: string
  icon: ElementType
  /** Short live/status marker shown next to the label, e.g. "LIVE". */
  badge?: string
  /**
   * Permission required to see this section. Hidden from the sidebar and
   * redirected away from in the page when missing. This mirrors the server's
   * own check to avoid showing a section that would 403; it does not replace it.
   */
  permission?: string
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

export const AGRICULTURE_SECTIONS: ModuleSections = {
  moduleKey: 'agriculture',
  basePath: '/agriculture',
  title: 'Agriculture',
  icon: Wheat,
  defaultSection: 'dashboard',
  groups: [
    {
      label: 'Farm operations',
      sections: [
        { id: 'dashboard', label: 'Dashboard', icon: LayoutDashboard },
        { id: 'farms', label: 'Farms', icon: Tractor },
        { id: 'species', label: 'Species', icon: PawPrint },
      ],
    },
  ],
}

export const FUEL_SECTIONS: ModuleSections = {
  moduleKey: 'fuel',
  basePath: '/fuel',
  title: 'Fuel & Logistics',
  icon: Droplets,
  defaultSection: 'dashboard',
  groups: [
    { label: 'Overview', sections: [{ id: 'dashboard', label: 'Dashboard', icon: LayoutDashboard }] },
    {
      label: 'Stock',
      sections: [
        { id: 'tanks', label: 'Tanks', icon: Droplets },
        { id: 'receipts', label: 'Stock In', icon: ArrowDownToLine },
        { id: 'dispatches', label: 'Dispatches', icon: Fuel },
      ],
    },
    {
      label: 'Logistics',
      sections: [
        { id: 'deliveries', label: 'Deliveries', icon: Truck },
        { id: 'suppliers', label: 'Suppliers', icon: Users },
      ],
    },
    {
      label: 'Insights',
      sections: [{ id: 'margin', label: 'Cost & Margin', icon: TrendingUp, permission: 'FUEL_MARGIN_READ' }],
    },
  ],
}

export const FLEET_SECTIONS: ModuleSections = {
  moduleKey: 'fleet',
  basePath: '/fleet',
  title: 'Fleet',
  icon: Car,
  defaultSection: 'dashboard',
  groups: [
    { label: 'Overview', sections: [{ id: 'dashboard', label: 'Dashboard', icon: LayoutDashboard }] },
    {
      label: 'Fleet',
      sections: [
        { id: 'vehicles', label: 'Vehicles', icon: Car },
        { id: 'drivers', label: 'Drivers', icon: Users },
      ],
    },
    {
      label: 'Operations',
      sections: [
        { id: 'trips', label: 'Logbook', icon: Route },
        { id: 'services', label: 'Service History', icon: Wrench },
        { id: 'fuel', label: 'Fuel Log', icon: Fuel },
      ],
    },
    { label: 'Compliance', sections: [{ id: 'compliance', label: 'Compliance', icon: Shield }] },
  ],
}

const REGISTRY: ModuleSections[] = [SECURITY_SECTIONS, AGRICULTURE_SECTIONS, FUEL_SECTIONS, FLEET_SECTIONS]

/** Groups with sections the user may not see removed (and empty groups dropped). */
export function visibleGroups(config: ModuleSections, permissions: readonly string[]): ModuleSectionGroup[] {
  return config.groups
    .map(g => ({ ...g, sections: g.sections.filter(s => !s.permission || permissions.includes(s.permission)) }))
    .filter(g => g.sections.length > 0)
}

/** Section config for the module whose base path contains `pathname`. */
export function sectionsForPath(pathname: string): ModuleSections | undefined {
  return REGISTRY.find(m => pathname === m.basePath || pathname.startsWith(m.basePath + '/'))
}

/**
 * Section by id. When `permissions` is given, sections the user may not see
 * are treated as not found, so pages redirect away from them.
 */
export function findSection(config: ModuleSections, id: string | undefined, permissions?: readonly string[]):
  { section: ModuleSection; group: ModuleSectionGroup } | undefined {
  if (!id) return undefined
  const groups = permissions ? visibleGroups(config, permissions) : config.groups
  for (const group of groups) {
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
