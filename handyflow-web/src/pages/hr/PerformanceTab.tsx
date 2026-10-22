// src/pages/hr/PerformanceTab.tsx
//
// Performance: reviews, goals, 360 feedback and improvement plans. Everyone sees their own; managers see their team's; HR sees all.
// The server decides who may see and do what; this screen shows that and offers only what is allowed.
import { useState } from "react"
import { useAuth } from "../../hooks/useAuth"
import CycleScreen from "./PerfCycles"
import FeedbackTasks from "./PerfFeedback"
import GoalsScreen from "./PerfGoals"
import { useMe, usePerfList } from "./perf.hooks"
import { perfRights } from "./perf.logic"
import type { PlanRow } from "./perf.shared"
import PlanScreen from "./PerfPlans"
import ReviewScreen, { ReviewList } from "./PerfReviews"
import { Tabs } from "./PerfParts"

type Section = "reviews" | "goals" | "feedback" | "plan" | "team" | "cycles" | "allgoals" | "plans"

export default function PerformanceTab() {
  const { user } = useAuth() as { user?: { permissions?: string[] } }
  const rights = perfRights(user?.permissions ?? [])
  const me = useMe()
  const mine = usePerfList<PlanRow>(["plans", "mine"], "/plans?scope=mine")
  const [section, setSection] = useState<Section>("reviews")
  const [openId, setOpenId] = useState<string | null>(null)
  const [teamView, setTeamView] = useState<"reviews" | "goals" | "plans">("reviews")

  const isManager = (me.data?.directReports ?? 0) > 0
  const tabs: { id: Section; label: string; show: boolean }[] = [
    { id: "reviews", label: "My reviews", show: true },
    { id: "goals", label: "My goals", show: true },
    { id: "feedback", label: "Feedback for others", show: true },
    { id: "plan", label: "My plan", show: (mine.data ?? []).length > 0 },
    { id: "team", label: "My team", show: isManager },
    { id: "cycles", label: "Review cycles", show: rights.read },
    { id: "allgoals", label: "All goals", show: rights.read },
    { id: "plans", label: "Improvement plans", show: rights.read },
  ]
  const pick = (s: Section) => { setSection(s); setOpenId(null) }

  if (openId) return <ReviewScreen id={openId} back={() => setOpenId(null)} />
  return (
    <div>
      <Tabs tabs={tabs.filter(t => t.show).map(t => ({ id: t.id, label: t.label }))} value={section} onChange={pick} />
      {section === "reviews" && <ReviewList scope="mine" open={setOpenId} empty="You have no performance reviews yet." />}
      {section === "goals" && <GoalsScreen scope="mine" />}
      {section === "feedback" && <FeedbackTasks />}
      {section === "plan" && <PlanScreen scope="mine" canCreate={false} />}
      {section === "team" && (
        <div>
          <Tabs tabs={[{ id: "reviews", label: "Reviews" }, { id: "goals", label: "Goals" }, { id: "plans", label: "Plans" }]} value={teamView} onChange={setTeamView} />
          {teamView === "reviews" && <ReviewList scope="team" open={setOpenId} empty="Nobody in your team has a review yet." />}
          {teamView === "goals" && <GoalsScreen scope="team" />}
          {teamView === "plans" && <PlanScreen scope="team" canCreate={false} />}
        </div>
      )}
      {section === "cycles" && <CycleScreen openReview={setOpenId} />}
      {section === "allgoals" && <GoalsScreen scope="all" />}
      {section === "plans" && <PlanScreen scope="all" canCreate={rights.manage} />}
    </div>
  )
}
