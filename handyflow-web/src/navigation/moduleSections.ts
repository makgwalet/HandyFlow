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
  AlertTriangle, Coins, Camera, ClipboardList, Clock, Crosshair, DoorOpen, FileBarChart,
  GitBranch, Key, LayoutDashboard, Lock, MapPin, Radio, RefreshCw, Repeat, Route,
  Gauge, Shield, ShieldCheck, Siren, Tablet, DollarSign, Wheat, Tractor, PawPrint,
  Droplets, ArrowDownToLine, Fuel, Truck, Users, TrendingUp, Car, Wrench,
  Calculator, BookOpen, GitBranch as JournalIcon, Landmark, BarChart2, FileText,
  Briefcase, Calendar, FolderOpen, AlertOctagon, CalendarCheck, Settings,
  Building2, GraduationCap, CalendarDays, Award, Scale, Gavel, FileSearch, MapPin as PinIcon, HardHat,
  ShoppingCart, Package, CalendarClock, ClipboardCheck,
  PartyPopper, FilePlus, LayoutTemplate, CreditCard,
  Stethoscope, ListPlus,
  Megaphone, Warehouse, Handshake, UserCog,
  Database, CalendarRange,
  UserCheck, Palette, Headphones, Wallet, Receipt, CheckSquare, LayoutGrid, ListChecks, Sprout,
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
        { id: 'complaints', label: 'Complaints', icon: AlertOctagon },
        { id: 'risk-rules', label: 'Risk Rules', icon: Gauge },
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
    {
      label: 'Crops',
      sections: [
        { id: 'crop-cycles', label: 'Crop cycles', icon: Sprout },
        { id: 'seasons', label: 'Seasons', icon: CalendarRange },
        { id: 'crop-types', label: 'Crop types', icon: Wheat },
      ],
    },
    {
      label: 'Insights',
      sections: [
        { id: 'profitability', label: 'Profitability', icon: Scale },
        { id: 'trends', label: 'Trends', icon: TrendingUp },
        { id: 'costs', label: 'Cost reports', icon: BarChart2 },
        { id: 'ledger', label: 'Cost ledger', icon: Receipt },
        { id: 'sales', label: 'Sales', icon: Wallet },
        { id: 'labour', label: 'Labour', icon: Users },
        { id: 'equipment', label: 'Equipment & fuel', icon: Tractor },
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
        { id: 'equipment', label: 'Equipment', icon: Tractor },
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
        { id: 'requirements', label: 'Requirements', icon: ListChecks },
      ],
    },
    { label: 'Tenders', sections: [{ id: 'tenders', label: 'Tenders', icon: Briefcase }, { id: 'rates', label: 'Rates library', icon: Coins }] },
  ],
}

export const EVENTS_SECTIONS: ModuleSections = {
  moduleKey: 'events',
  basePath: '/events',
  title: 'Events',
  icon: PartyPopper,
  defaultSection: 'events',
  groups: [
    { label: 'Events', sections: [{ id: 'events', label: 'Events', icon: Calendar }] },
    {
      // These three work on the event picked in the list; the page shows the
      // current selection under its title.
      label: 'Selected event',
      sections: [
        { id: 'guests', label: 'Guests', icon: Users },
        { id: 'vendors', label: 'Vendors', icon: Truck },
        { id: 'analytics', label: 'Analytics', icon: BarChart2 },
      ],
    },
  ],
}

export const CONTRACTING_SECTIONS: ModuleSections = {
  moduleKey: 'contracting',
  basePath: '/contracts',
  title: 'Contracting',
  icon: FilePlus,
  // Contracting has always opened on the contract list, not the dashboard.
  defaultSection: 'contracts',
  groups: [
    { label: 'Overview', sections: [{ id: 'dashboard', label: 'Dashboard', icon: BarChart2 }] },
    {
      label: 'Contracts',
      sections: [
        { id: 'contracts', label: 'Contracts', icon: FileText },
        { id: 'templates', label: 'Templates', icon: LayoutTemplate },
      ],
    },
  ],
}

export const AP_SECTIONS: ModuleSections = {
  moduleKey: 'ap',
  basePath: '/ap',
  title: 'Accounts Payable',
  icon: CreditCard,
  defaultSection: 'bills',
  groups: [
    {
      label: 'Payables',
      sections: [
        { id: 'bills', label: 'Bills', icon: FileText },
        { id: 'batches', label: 'EFT Batches', icon: CreditCard },
        { id: 'recurring', label: 'Recurring', icon: RefreshCw },
      ],
    },
    {
      label: 'Suppliers & reporting',
      sections: [
        { id: 'suppliers', label: 'Suppliers', icon: Landmark },
        { id: 'aging', label: 'Aging', icon: Users },
      ],
    },
  ],
}

export const PROPERTY_SECTIONS: ModuleSections = {
  moduleKey: 'property',
  basePath: '/property',
  title: 'Property',
  icon: Building2,
  defaultSection: 'dashboard',
  groups: [
    { label: 'Overview', sections: [{ id: 'dashboard', label: 'Dashboard', icon: BarChart2 }] },
    {
      label: 'Portfolio',
      sections: [
        { id: 'properties', label: 'Properties', icon: Building2 },
        { id: 'leases', label: 'Leases', icon: FileText },
      ],
    },
    {
      label: 'Operations',
      sections: [
        { id: 'payments', label: 'Payments', icon: CreditCard },
        { id: 'inspections', label: 'Inspections', icon: ClipboardList },
      ],
    },
  ],
}

export const CLINIC_SECTIONS: ModuleSections = {
  moduleKey: 'clinic',
  basePath: '/clinic',
  title: 'Clinic',
  icon: Stethoscope,
  defaultSection: 'dashboard',
  groups: [
    { label: 'Overview', sections: [{ id: 'dashboard', label: 'Dashboard', icon: LayoutDashboard }] },
    {
      label: 'Patients',
      sections: [
        { id: 'patients', label: 'Patients', icon: Users },
        { id: 'consultations', label: 'Consultations', icon: FileText },
        { id: 'recalls', label: 'Recalls', icon: CalendarClock },
        { id: 'waitlist', label: 'Waitlist', icon: ListPlus },
      ],
    },
    { label: 'Scheduling', sections: [{ id: 'schedule', label: 'Schedule', icon: Calendar }] },
    {
      label: 'Practice & finance',
      sections: [
        { id: 'practitioners', label: 'Practitioners', icon: Stethoscope },
        { id: 'claims', label: 'Claims', icon: CreditCard },
        { id: 'billing', label: 'Billing', icon: BarChart2 },
      ],
    },
  ],
}

export const DEBT_COLLECTION_SECTIONS: ModuleSections = {
  moduleKey: 'debtcollection',
  basePath: '/debtcollection',
  title: 'Debt Collection',
  icon: Landmark,
  defaultSection: 'dashboard',
  groups: [
    { label: 'Overview', sections: [{ id: 'dashboard', label: 'Dashboard', icon: LayoutDashboard }] },
    { label: 'Collections', sections: [{ id: 'cases', label: 'Cases', icon: Landmark }] },
  ],
}

export const MARKETING_SECTIONS: ModuleSections = {
  moduleKey: 'marketing',
  basePath: '/marketing',
  title: 'Marketing',
  icon: Megaphone,
  defaultSection: 'campaigns',
  groups: [
    {
      label: 'Campaigns',
      sections: [
        { id: 'campaigns', label: 'Campaigns', icon: Megaphone },
        { id: 'templates', label: 'Templates', icon: FileText },
      ],
    },
    { label: 'Audience', sections: [{ id: 'contacts', label: 'Contacts', icon: Users }] },
    { label: 'Insights', sections: [{ id: 'analytics', label: 'Analytics', icon: BarChart2 }] },
  ],
}

export const TRAINING_SECTIONS: ModuleSections = {
  moduleKey: 'training',
  basePath: '/training',
  title: 'Training & L&D',
  icon: GraduationCap,
  defaultSection: 'dashboard',
  groups: [
    { label: 'Overview', sections: [{ id: 'dashboard', label: 'Dashboard', icon: LayoutDashboard }] },
    {
      label: 'Learning',
      sections: [
        { id: 'courses', label: 'Courses', icon: GraduationCap },
        { id: 'sessions', label: 'Sessions', icon: CalendarDays },
        { id: 'certificates', label: 'Certificates', icon: Award },
      ],
    },
  ],
}

export const WAREHOUSING_SECTIONS: ModuleSections = {
  moduleKey: 'warehousing',
  // /warehousing/portal/* is the client portal, routed separately outside the shell.
  basePath: '/warehousing',
  title: 'Warehousing',
  icon: Warehouse,
  defaultSection: 'dashboard',
  groups: [
    { label: 'Overview', sections: [{ id: 'dashboard', label: 'Dashboard', icon: LayoutDashboard }] },
    {
      label: 'Clients & locations',
      sections: [
        { id: 'clients', label: 'Clients', icon: Building2 },
        { id: 'locations', label: 'Locations', icon: PinIcon },
      ],
    },
  ],
}

export const COLLECTIONS_AGENCY_SECTIONS: ModuleSections = {
  moduleKey: 'collectionsagency',
  // /collections-agency/portal/* is the creditor-client portal, routed separately.
  basePath: '/collections-agency',
  title: 'Collections Agency',
  icon: Handshake,
  defaultSection: 'dashboard',
  groups: [
    { label: 'Overview', sections: [{ id: 'dashboard', label: 'Dashboard', icon: LayoutDashboard }] },
    {
      label: 'Portfolio',
      sections: [
        { id: 'clients', label: 'Clients', icon: Users },
        { id: 'collectors', label: 'Collectors', icon: UserCog },
      ],
    },
    { label: 'Setup', sections: [{ id: 'profile', label: 'Agency Profile', icon: Gavel }] },
  ],
}

export const INTERNAL_AUDIT_SECTIONS: ModuleSections = {
  moduleKey: 'internal-audit',
  basePath: '/internal-audit',
  title: 'Internal Audit',
  icon: ShieldCheck,
  defaultSection: 'universe',
  groups: [
    {
      label: 'Audit programme',
      sections: [
        { id: 'universe', label: 'Audit Universe', icon: Database },
        { id: 'plans', label: 'Annual Plans', icon: CalendarRange },
        { id: 'engagements', label: 'Engagements', icon: ClipboardList },
      ],
    },
  ],
}

// Modules that used to keep their own tab bar (or a single screen) inside the page. They now use the
// same sidebar context mode as the rest: "All modules", the module name, then its sections.

export const RECRUITER_SECTIONS: ModuleSections = {
  moduleKey: 'recruiter',
  basePath: '/recruiter',
  title: 'Recruiter',
  icon: UserCheck,
  defaultSection: 'jobs',
  groups: [
    {
      label: 'Hiring',
      sections: [
        { id: 'jobs', label: 'Job Postings', icon: Briefcase },
        { id: 'pipeline', label: 'Pipeline', icon: BarChart2 },
        { id: 'applications', label: 'Applications', icon: Users },
      ],
    },
  ],
}

export const CREATIVE_SECTIONS: ModuleSections = {
  moduleKey: 'creative',
  basePath: '/creative',
  title: 'Creative',
  icon: Palette,
  defaultSection: 'jobs',
  groups: [{ label: 'Overview', sections: [{ id: 'jobs', label: 'Jobs', icon: Palette }] }],
}

export const CATALOGUE_SECTIONS: ModuleSections = {
  moduleKey: 'catalogue',
  basePath: '/catalogue',
  title: 'Catalogue',
  icon: Package,
  defaultSection: 'items',
  groups: [{ label: 'Overview', sections: [{ id: 'items', label: 'Products & services', icon: Package }] }],
}

export const COMPLIANCE_SERVICES_SECTIONS: ModuleSections = {
  moduleKey: 'complianceservices',
  basePath: '/complianceservices',
  title: 'Compliance Services',
  icon: Building2,
  defaultSection: 'clients',
  groups: [{ label: 'Overview', sections: [{ id: 'clients', label: 'Clients', icon: Users }] }],
}

export const DESK_SECTIONS: ModuleSections = {
  moduleKey: 'desk',
  basePath: '/desk',
  title: 'Desk Support',
  icon: Headphones,
  defaultSection: 'tickets',
  groups: [{ label: 'Overview', sections: [{ id: 'tickets', label: 'Tickets', icon: Headphones }] }],
}

export const EXPENSES_SECTIONS: ModuleSections = {
  moduleKey: 'expenses',
  basePath: '/expenses',
  title: 'Expenses',
  icon: Wallet,
  defaultSection: 'claims',
  groups: [{ label: 'Overview', sections: [{ id: 'claims', label: 'Claims', icon: Receipt }] }],
}

export const POS_SECTIONS: ModuleSections = {
  moduleKey: 'pos',
  basePath: '/pos',
  title: 'POS & Stock',
  icon: ShoppingCart,
  defaultSection: 'sell',
  groups: [
    {
      label: 'Point of sale',
      sections: [
        { id: 'sell', label: 'POS Terminal', icon: ShoppingCart },
        { id: 'transactions', label: 'Transactions', icon: Receipt },
      ],
    },
    {
      label: 'Inventory',
      sections: [
        { id: 'stock', label: 'Stock', icon: Package },
        { id: 'orders', label: 'Purchase Orders', icon: Truck },
      ],
    },
  ],
}

export const PROJECTS_SECTIONS: ModuleSections = {
  moduleKey: 'projects',
  basePath: '/projects',
  title: 'Projects',
  icon: HardHat,
  defaultSection: 'dashboard',
  groups: [
    {
      label: 'Overview',
      sections: [
        { id: 'dashboard', label: 'Dashboard', icon: LayoutDashboard },
        { id: 'projects', label: 'Projects', icon: FolderOpen },
      ],
    },
  ],
}

export const TASKS_SECTIONS: ModuleSections = {
  moduleKey: 'tasks',
  basePath: '/tasks',
  title: 'Tasks',
  icon: CheckSquare,
  defaultSection: 'boards',
  groups: [
    {
      label: 'Overview',
      sections: [
        { id: 'boards', label: 'Boards', icon: LayoutGrid },
        { id: 'my-tasks', label: 'My tasks', icon: ListChecks },
      ],
    },
  ],
}

const REGISTRY: ModuleSections[] = [
  SECURITY_SECTIONS, AGRICULTURE_SECTIONS, FUEL_SECTIONS, FLEET_SECTIONS, ACCOUNTING_SECTIONS, ACCOUNTANT_SECTIONS,
  HR_SECTIONS, BOOKINGS_SECTIONS, TRAINING_PROVIDER_SECTIONS, LEGAL_COMPLIANCE_SECTIONS, EARTHMOVING_SECTIONS,
  SUPPLY_CHAIN_SECTIONS, COMPLIANCE_TENDER_SECTIONS,
  EVENTS_SECTIONS, CONTRACTING_SECTIONS, AP_SECTIONS, PROPERTY_SECTIONS, CLINIC_SECTIONS,
  DEBT_COLLECTION_SECTIONS, MARKETING_SECTIONS, TRAINING_SECTIONS, WAREHOUSING_SECTIONS, COLLECTIONS_AGENCY_SECTIONS,
  INTERNAL_AUDIT_SECTIONS,
  RECRUITER_SECTIONS, CREATIVE_SECTIONS, CATALOGUE_SECTIONS, COMPLIANCE_SERVICES_SECTIONS, DESK_SECTIONS,
  EXPENSES_SECTIONS, POS_SECTIONS, PROJECTS_SECTIONS, TASKS_SECTIONS,
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
