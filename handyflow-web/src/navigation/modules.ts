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
} from 'lucide-react'
import type { ElementType } from 'react'
import { apiClient } from '../api/client'

export interface ModuleNavItem {
  key: string
  icon: ElementType
  label: string
  route: string
}

export const MODULE_REGISTRY: Record<string, Omit<ModuleNavItem, 'key'>> = {
  crm:          { icon: Users,         label: 'Customers',    route: '/customers'    },
  invoicing:    { icon: FileText,      label: 'Invoices',     route: '/invoices'     },
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
}

/** Always reachable regardless of subscription. */
export const WORKSPACE_NAV: Omit<ModuleNavItem, 'key'>[] = [
  { icon: FileText,   label: 'Quotes',   route: '/quotes'   },
  { icon: CreditCard, label: 'Billing',  route: '/billing'  },
  { icon: Settings,   label: 'Settings', route: '/settings' },
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
  return modules.find(m => pathname === m.route || pathname.startsWith(m.route + '/'))
}
