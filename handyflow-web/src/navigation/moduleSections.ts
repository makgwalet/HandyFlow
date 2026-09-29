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
  Calculator, BookOpen, GitBranch as JournalIcon, Landmark, BarChart2, FileText,
  Briefcase, Calendar, FolderOpen, AlertOctagon, CalendarCheck, Settings,
  Building2, GraduationCap, CalendarDays, Award, Scale, Gavel, FileSearch, MapPin as PinIcon, HardHat,
  ShoppingCart, Package, CalendarClock, ClipboardCheck,
} from 'lucide-react'

export interface ModuleSection {
  id: string
  label: string
  icon: ElementType
  /** Short live/status marker shown next to the label, e.g. "LIVE". */
  badge?: string
  /**
   * A count fetched live for this section (see sectionBadges.ts), shown as
   * the badge while it is above zero. Hidden while the section is open.
   */
  liveBadge?: 'bookings-pending'
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

export const ACCOUNTING_SECTIONS: ModuleSections = {
  moduleKey: 'accounting',
  basePath: '/accounting',
  title: 'Accounting',
  icon: Calculator,
  defaultSection: 'dashboard',
  groups: [
    { label: 'Overview', sections: [{ id: 'dashboard', label: 'Dashboard', icon: LayoutDashboard }] },
    {
      label: 'Books',
      sections: [
        { id: 'accounts', label: 'Chart of Accounts', icon: BookOpen },
        { id: 'journal', label: 'Journal Entries', icon: JournalIcon },
        { id: 'bank', label: 'Bank Accounts', icon: Landmark },
      ],
    },
    {
      label: 'Reporting',
      sections: [
        { id: 'reports', label: 'Reports', icon: BarChart2 },
        { id: 'aging', label: 'AR / AP Aging', icon: Users },
      ],
    },
    { label: 'Tax', sections: [{ id: 'vat', label: 'VAT Returns', icon: FileText }] },
  ],
}

export const ACCOUNTANT_SECTIONS: ModuleSections = {
  moduleKey: 'accountant',
  basePath: '/accountant',
  title: 'Accountant',
  icon: Briefcase,
  defaultSection: 'dashboard',
  groups: [
    { label: 'Overview', sections: [{ id: 'dashboard', label: 'Dashboard', icon: BarChart2 }] },
    {
      label: 'Clients',
      sections: [
        { id: 'clients', label: 'Clients', icon: Users },
        { id: 'deadlines', label: 'Compliance', icon: Calendar },
      ],
    },
    {
      label: 'Work & billing',
      sections: [
        { id: 'time', label: 'Time', icon: Clock },
        { id: 'billing', label: 'Billing', icon: FileText },
      ],
    },
    {
      label: 'Records',
      sections: [
        { id: 'journals', label: 'Journals', icon: BookOpen },
        { id: 'workpapers', label: 'Workpapers', icon: FolderOpen },
      ],
    },
  ],
}

export const HR_SECTIONS: ModuleSections = {
  moduleKey: 'hr',
  basePath: '/hr',
  title: 'HR & Payroll',
  icon: Users,
  // HR has always opened on the employee register, not the dashboard.
  defaultSection: 'employees',
  groups: [
    { label: 'Overview', sections: [{ id: 'dashboard', label: 'Dashboard', icon: LayoutDashboard }] },
    {
      label: 'People',
      sections: [
        { id: 'employees', label: 'Employees', icon: Users },
        { id: 'leave', label: 'Leave', icon: Calendar },
        { id: 'disciplinary', label: 'Disciplinary', icon: AlertOctagon },
      ],
    },
    {
      label: 'Payroll & compliance',
      sections: [
        { id: 'payroll', label: 'Payroll', icon: DollarSign },
        { id: 'compliance', label: 'Compliance', icon: FileText },
      ],
    },
  ],
}

export const BOOKINGS_SECTIONS: ModuleSections = {
  moduleKey: 'bookings',
  basePath: '/bookings',
  title: 'Bookings',
  icon: CalendarCheck,
  defaultSection: 'dashboard',
  groups: [
    { label: 'Overview', sections: [{ id: 'dashboard', label: 'Dashboard', icon: LayoutDashboard }] },
    {
      label: 'Schedule',
      sections: [
        { id: 'calendar', label: 'Calendar', icon: Calendar },
        { id: 'bookings', label: 'Bookings', icon: Clock, liveBadge: 'bookings-pending' },
      ],
    },
    {
      label: 'Setup',
      sections: [
        { id: 'services', label: 'Services', icon: Briefcase },
        { id: 'staff', label: 'Staff', icon: Users },
        { id: 'availability', label: 'Availability', icon: Settings },
      ],
    },
  ],
}

export const TRAINING_PROVIDER_SECTIONS: ModuleSections = {
  moduleKey: 'trainingprovider',
  // Note: /training-provider/portal/* is the client portal, routed separately
  // outside the staff shell, and is unaffected.
  basePath: '/training-provider',
  title: 'Training Provider',
  icon: GraduationCap,
  defaultSection: 'dashboard',
  groups: [
    { label: 'Overview', sections: [{ id: 'dashboard', label: 'Dashboard', icon: LayoutDashboard }] },
    {
      label: 'Clients & courses',
      sections: [
        { id: 'clients', label: 'Clients', icon: Building2 },
        { id: 'courses', label: 'Courses', icon: GraduationCap },
      ],
    },
    {
      label: 'Delivery',
      sections: [
        { id: 'sessions', label: 'Sessions', icon: CalendarDays },
        { id: 'certificates', label: 'Certificates', icon: Award },
      ],
    },
    { label: 'Setup', sections: [{ id: 'profile', label: 'Academy Profile', icon: Landmark }] },
  ],
}

export const LEGAL_COMPLIANCE_SECTIONS: ModuleSections = {
  moduleKey: 'legalcompliance',
  basePath: '/legalcompliance',
  title: 'Legal & Compliance',
  icon: Scale,
  defaultSection: 'dashboard',
  groups: [
    { label: 'Overview', sections: [{ id: 'dashboard', label: 'Dashboard', icon: LayoutDashboard }] },
    {
      label: 'Compliance',
      sections: [
        { id: 'obligations', label: 'Obligations', icon: ClipboardList },
        { id: 'popia', label: 'POPIA Register', icon: Lock },
        { id: 'dsar', label: 'DSAR Requests', icon: FileSearch },
        { id: 'calendar', label: 'Calendar', icon: CalendarDays },
      ],
    },
    { label: 'Legal matters', sections: [{ id: 'litigation', label: 'Litigation', icon: Gavel }] },
  ],
}

export const EARTHMOVING_SECTIONS: ModuleSections = {
  moduleKey: 'earthmoving',
  basePath: '/earthmoving',
  title: 'Earthmoving',
  icon: HardHat,
  defaultSection: 'dashboard',
  groups: [
    { label: 'Overview', sections: [{ id: 'dashboard', label: 'Dashboard', icon: LayoutDashboard }] },
    {
      label: 'Fleet & sites',
      sections: [
        { id: 'assets', label: 'Fleet', icon: Truck },
        { id: 'deployments', label: 'Deployments', icon: PinIcon },
      ],
    },
    {
      label: 'Operations',
      sections: [
        { id: 'maintenance', label: 'Maintenance', icon: Wrench },
        { id: 'operators', label: 'Operator Logs', icon: Users },
      ],
    },
    { label: 'Safety', sections: [{ id: 'incidents', label: 'Incidents', icon: AlertTriangle }] },
  ],
}

export const SUPPLY_CHAIN_SECTIONS: ModuleSections = {
  // The sidebar registry key uses an underscore; the URL uses a hyphen.
  moduleKey: 'supply_chain',
  basePath: '/supply-chain',
  title: 'Supply Chain',
  icon: Truck,
  defaultSection: 'dashboard',
  groups: [
    { label: 'Overview', sections: [{ id: 'dashboard', label: 'Dashboard', icon: LayoutDashboard }] },
    {
      label: 'Purchasing',
      sections: [
        { id: 'suppliers', label: 'Suppliers', icon: Users },
        { id: 'purchase-orders', label: 'Purchase Orders', icon: ShoppingCart },
        { id: 'invoices', label: 'Supplier Invoices', icon: FileText },
      ],
    },
    { label: 'Stock', sections: [{ id: 'inventory', label: 'Inventory', icon: Package }] },
  ],
}

export const COMPLIANCE_TENDER_SECTIONS: ModuleSections = {
  moduleKey: 'compliancetender',
  // Tender detail pages live at /compliancetender/tenders/:id (a separate,
  // more specific route); the "Tenders" section stays highlighted on them.
  basePath: '/compliancetender',
  title: 'Business Compliance & Tender',
  icon: ClipboardCheck,
  defaultSection: 'dashboard',
  groups: [
    { label: 'Overview', sections: [{ id: 'dashboard', label: 'Dashboard', icon: LayoutDashboard }] },
    {
      label: 'Compliance',
      sections: [
        { id: 'registrations', label: 'Registrations', icon: ShieldCheck },
        { id: 'documents', label: 'Documents', icon: FileText },
        { id: 'deadlines', label: 'Deadlines', icon: CalendarClock },
      ],
    },
    { label: 'Tenders', sections: [{ id: 'tenders', label: 'Tenders', icon: Briefcase }] },
  ],
}

const REGISTRY: ModuleSections[] = [
  SECURITY_SECTIONS, AGRICULTURE_SECTIONS, FUEL_SECTIONS, FLEET_SECTIONS, ACCOUNTING_SECTIONS, ACCOUNTANT_SECTIONS,
  HR_SECTIONS, BOOKINGS_SECTIONS, TRAINING_PROVIDER_SECTIONS, LEGAL_COMPLIANCE_SECTIONS, EARTHMOVING_SECTIONS,
  SUPPLY_CHAIN_SECTIONS, COMPLIANCE_TENDER_SECTIONS,
]

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
