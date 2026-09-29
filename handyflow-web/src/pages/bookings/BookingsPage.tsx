// src/pages/bookings/BookingsPage.tsx
//
// Sections are routes (/bookings/:section) with navigation in the sidebar.
// The pending-bookings count that used to sit on the "Bookings" tab is now a
// live badge on that sidebar item (navigation/sectionBadges.ts, same query
// key as before), so it is visible from every section of the module.
import BookingsDashboard  from "./BookingsDashboard"
import BookingsTab        from "./BookingsTab"
import CalendarTab        from "./CalendarTab"
import ServicesTab        from "./ServicesTab"
import StaffTab           from "./StaffTab"
import AvailabilityTab    from "./AvailabilityTab"
import { SectionedModulePage } from "../../components/shell/SectionedModulePage"
import { BOOKINGS_SECTIONS } from "../../navigation/moduleSections"

export function BookingsPage() {
  return (
    <SectionedModulePage config={BOOKINGS_SECTIONS}
      subtitle="Schedule appointments, manage staff and track your calendar"
      render={(id, goTo) => {
        switch (id) {
          case "dashboard":    return <BookingsDashboard onNavigate={goTo} />
          case "calendar":     return <CalendarTab />
          case "bookings":     return <BookingsTab />
          case "services":     return <ServicesTab />
          case "staff":        return <StaffTab />
          case "availability": return <AvailabilityTab />
          default:             return null
        }
      }} />
  )
}
