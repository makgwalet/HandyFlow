// src/navigation/modules.ts
//
// Staff-app navigation data, shared by the sidebar and the module switcher.
// Moved out of ModuleLayout unchanged so there is one source of truth.
//
// Note: DashboardPage keeps its own richer registry (descriptions, tile
// colours). Merging the two is tracked in UI-MODERNIZATION-PROGRESS.md.
import { useQuery } from '@tanstack/react-query'
import {
  Building2, Users, FileText, Package, CreditCard,
  Shield, Fuel, HardHat, Car, Settings, Calculator, CalendarCheck,
  HeartPulse, PartyPopper, FilePen, Wallet, Briefcase,
  Palette, Headphones, CheckSquare, Megaphone, UserCheck, ShoppingCart,
  Truck, Receipt, UserCog, ShieldCheck, Wheat,
  Handshake, Warehouse, Scale, ClipboardCheck, Landmark, GraduationCap, BookOpen,
  CalendarClock, Banknote, UserSearch, ShieldAlert,
} from 'lucide-react'
import type { ElementType } from 'react'
import { apiClient } from '../api/client'

export interface ModuleNavItem {
  key: string
  icon: ElementType
  label: string
  route: string
  /**
   * Extra path prefixes this module owns when they live outside its own route. Invoicing's
   * Recurring tab is at /recurring, so without this no sidebar item is highlighted there.
   */
  aliases?: string[]
}

/** True when `pathname` is `route` or anything beneath it. */
export const routeMatches = (pathname: string, route: string) =>
  pathname === route || pathname.startsWith(route + '/')

/** True when the path is inside the item's route or any of its aliases. */
export const isNavItemActive = (pathname: string, item: Pick<ModuleNavItem, 'route' | 'aliases'>) =>
  [item.route, ...(item.aliases ?? [])].some(r => routeMatches(pathname, r))

export const MODULE_REGISTRY: Record<string, Omit<ModuleNavItem, 'key'>> = {
  crm:          { icon: Users,         label: 'Customers',    route: '/customers'    },
  invoicing:    { icon: FileText,      label: 'Invoices',     route: '/invoices',    aliases: ['/recurring'] },
  catalogue:    { icon: Package,       label: 'Catalogue',    route: '/catalogue'    },
  security:     { icon: Shield,        label: 'Security',     route: '/security'     },
  fuel:         { icon: Fuel,          label: 'Fuel',         route: '/fuel'         },
  earthmoving:  { icon: HardHat,       label: 'Earthmoving',  route: '/earthmoving'  },
  property:     { icon: Building2,     label: 'Property',     route: '/property'     },
  fleet:        { icon: Car,           label: 'Fleet',        route: '/fleet'        },
  hr:           { icon: Briefcase,     label: 'HR & Payroll', route: '/hr'           },
  accounting:   { icon: Calculator,    label: 'Accounting',   route: '/accounting'   },
  bookings:     { icon: CalendarCheck, label: 'Bookings',     route: '/bookings'     },
  clinic:       { icon: HeartPulse,    label: 'Clinic',       route: '/clinic'       },
  events:       { icon: PartyPopper,   label: 'Events',       route: '/events'       },
  contracting:  { icon: FilePen,       label: 'Contracts',    route: '/contracts'    },
  expenses:     { icon: Wallet,        label: 'Expenses',     route: '/expenses'     },
  creative:     { icon: Palette,       label: 'Creative',     route: '/creative'     },
  desk:         { icon: Headphones,    label: 'Desk',         route: '/desk'         },
  tasks:        { icon: CheckSquare,   label: 'Tasks',        route: '/tasks'        },
  marketing:    { icon: Megaphone,     label: 'Marketing',    route: '/marketing'    },
  recruiter:    { icon: UserCheck,     label: 'Recruiter',    route: '/recruiter'    },
  pos:          { icon: ShoppingCart,  label: 'POS & Stock',  route: '/pos'          },
  supply_chain: { icon: Truck,         label: 'Supply Chain', route: '/supply-chain' },
  ap:           { icon: Receipt,       label: 'Payables',     route: '/ap'           },
  accountant:   { icon: UserCog,       label: 'Accountant',   route: '/accountant'   },
  'internal-audit': { icon: ShieldCheck, label: 'Internal Audit', route: '/internal-audit' },
  agriculture:  { icon: Wheat,         label: 'Agriculture',  route: '/agriculture'  },
  // Present on the dashboard but missing here until now, so they were absent
  // from the sidebar and the Ctrl+K switcher.
  collectionsagency:  { icon: Handshake,      label: 'Collections Agency',           route: '/collections-agency' },
  warehousing:        { icon: Warehouse,      label: 'Warehousing',                  route: '/warehousing'        },
  legalcompliance:    { icon: Scale,          label: 'Legal & Compliance',           route: '/legalcompliance'    },
  compliancetender:   { icon: ClipboardCheck, label: 'Business Compliance & Tender', route: '/compliancetender'   },
  complianceservices: { icon: Building2,      label: 'Compliance Services',          route: '/complianceservices' },
  debtcollection:     { icon: Landmark,       label: 'Debt Collection',              route: '/debtcollection'     },
  projects:           { icon: HardHat,        label: 'Projects',                     route: '/projects'           },
  // Active catalogue modules that were on neither the dashboard nor the sidebar.
  training:           { icon: BookOpen,       label: 'Training & L&D',               route: '/training'           },
  trainingprovider:   { icon: GraduationCap,  label: 'Training Provider',            route: '/training-provider'  },
  // Routed in App.tsx and enforced by the backend (requireModule) but never registered here, so a
  // subscribed tenant could not reach them from the sidebar or Ctrl+K.
  bookingagency:      { icon: CalendarClock,  label: 'Booking Agency',               route: '/booking-agency'     },
  payrollbureau:      { icon: Banknote,       label: 'Payroll Bureau',               route: '/payroll-bureau'     },
  recruitmentagency:  { icon: UserSearch,     label: 'Recruitment Agency',           route: '/recruitment-agency' },
}

/** Always reachable regardless of subscription. */
export const WORKSPACE_NAV: Omit<ModuleNavItem, 'key'>[] = [
  { icon: FileText,   label: 'Quotes',   route: '/quotes'   },
  { icon: CreditCard, label: 'Billing',  route: '/billing'  },
  { icon: Settings,   label: 'Settings', route: '/settings' },
  // A cross-module "needs attention" board, open to any signed-in user (the API only requires
  // authentication). It is not a subscribed module, so it lives here, not in MODULE_REGISTRY.
  { icon: ShieldAlert, label: 'Control Exceptions', route: '/control-exceptions' },
]

/** Core modules every tenant has, even if the billing API omits them. */
const CORE_ALWAYS = ['crm', 'catalogue']

interface TenantModule { moduleKey: string; accessible: boolean }

/**
 * Modules the tenant can open right now, in registry-key order with core
 * modules first. Unknown keys from the API are ignored.
 */
export function useSubscribedModules(): { modules: ModuleNavItem[]; isLoading: boolean } {
  const { data = [], isLoading } = useQuery<TenantModule[]>({
    queryKey: ['tenant-modules-nav'],
    queryFn: async () => (await apiClient.get('/api/v1/billing/modules/mine')).data || [],
    staleTime: 5 * 60 * 1000,
  })
  const accessible = data.filter(m => m.accessible).map(m => m.moduleKey)
  const keys = [...CORE_ALWAYS, ...accessible].filter((k, i, arr) => MODULE_REGISTRY[k] && arr.indexOf(k) === i)
  return { modules: keys.map(key => ({ key, ...MODULE_REGISTRY[key] })), isLoading }
}

/** The module whose route the given path is inside, if any. */
export function activeModuleFor(pathname: string, modules: ModuleNavItem[]): ModuleNavItem | undefined {
  return modules.find(m => isNavItemActive(pathname, m))
}
