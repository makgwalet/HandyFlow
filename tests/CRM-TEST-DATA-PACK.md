# CRM test data pack: enter through the screens

Dates assume today is **Wed 21 Oct 2026**. Shift them if you test later (due dates cannot be in the past).
Section 0 first (setup), then A to K in order. Each block has the **valid data to type** and the **bad data to try**, with the message you should see.

All phone numbers and emails are made up. Use `@example.co.za` so nothing real gets mailed.

---

## 0. Setup (do this BEFORE adding customers, so the rules fire on them)

**Customers > Setup > Custom fields** (create 3)

| Name                   | Kind               | Options                              |
| ---------------------- | ------------------ | ------------------------------------ |
| Account manager region | Choice from a list | Gauteng, Western Cape, KwaZulu-Natal |
| Annual spend band      | Choice from a list | Under R100k, R100k-R500k, Over R500k |
| Credit approved        | Yes / No           | n/a                                  |

Bad: a field with no name, "Give the field a name."; a Choice field with one option only, expect "A choice field needs at least two options." A "Several choices" option containing `|`, expect "Options cannot contain the | character."

**Automation > Scoring & routing**
- Scoring rule 1: Lead source **is** Referral, points **20**
- Scoring rule 2: Deal value **is at least** 100000, points **15**
- Scoring rule 3: Has an email **is** No, points **-10**
- Bad points: `0` / `150` / `-101` / `5.5` / empty. Expect "Points must be between -100 and 100, and not zero.", "Points must be a whole number.", "Enter the points."
- Routing rule: Lead source **is** Website, assign to the users you have (needs at least one person).
- Bad: routing rule with nobody chosen, expect "Choose at least one person to receive the leads."

**Automation > Automation** (workflows)
- WF1: starts when **A lead is added**, does **Add a task**, text `Call new lead within 24h`, due in **1** day
- WF2: starts when **An opportunity is won**, does **Add a tag**, text `Won-2026`
- Bad: nothing chosen as trigger, "Choose what starts the workflow."; due days `400`, `-1`, `2.5`, expect "Due in days must be a whole number from 0 to 365."

---

## A. Customers and leads (Customers > Customers > Add)

Type = **Customer** or **Lead**, "Where did they come from?" = source.

| #   | Name                          | Type     | Source       | Email                                   | Phone           | VAT        | Street            | Suburb      | City         | Province      | Postal |
| --- | ----------------------------- | -------- | ------------ | --------------------------------------- | --------------- | ---------- | ----------------- | ----------- | ------------ | ------------- | ------ |
| 1   | Karoo Fresh Produce (Pty) Ltd | Customer | Referral     | orders@karoofresh.example.co.za         | +27 23 555 0101 | 4120987654 | 14 Church St      | Worcester   | Worcester    | Western Cape  | 6850   |
| 2   | Mthembu Logistics             | Customer | Website      | accounts@mthembulogistics.example.co.za | 011 555 0142    | 4551230987 | 88 Main Reef Rd   | Industria   | Johannesburg | Gauteng       | 2093   |
| 3   | Bay Hardware & Building       | Customer | Walk-in      | info@bayhardware.example.co.za          | 041 555 0177    | 4330012345 | 9 Govan Mbeki Ave | Central     | Gqeberha     | Eastern Cape  | 6001   |
| 4   | Sunrise Dental Practice       | Lead     | Website      | reception@sunrisedental.example.co.za   | 031 555 0188    | (blank)    | 22 Florida Rd     | Morningside | Durban       | KwaZulu-Natal | 4001   |
| 5   | Ndlovu Construction           | Lead     | Referral     | thabo@ndlovucon.example.co.za           | +27 82 555 0123 | (blank)    |                   |             | Pretoria     | Gauteng       | 0002   |
| 6   | Cape Brew Collective          | Lead     | Social media | hello@capebrew.example.co.za            | 021 555 0166    | (blank)    |                   |             | Cape Town    | Western Cape  | 8001   |
| 7   | Highveld Agri Supplies        | Lead     | Event        | sales@highveldagri.example.co.za        | 017 555 0133    | (blank)    |                   |             | Ermelo       | Mpumalanga    | 2350   |
| 8   | Lakeside Guest House          | Lead     | Phone call   | stay@lakesideguest.example.co.za        | 044 555 0199    | (blank)    |                   |             | Knysna       | Western Cape  | 6570   |
| 9   | Prime Auto Parts              | Lead     | Advert       | (blank)                                 | 012 555 0150    | (blank)    |                   |             | Pretoria     | Gauteng       | 0181   |

Leads 4 to 9 should fire WF1 (a task appears under Tasks) and get scored. Lead 5 (Referral) should score higher than lead 9 (no email, so a penalty). Lead 4, 6 and 7 (Website) should be routed to the assignee you chose. **Check this**, it is the main test of scoring/routing/automation.

Fill the custom fields on 1, 2, 3 (any choice).

**Bad data (each should be blocked with the message shown, nothing saved)**

| Field | Type this | Expect |
|---|---|---|
| Name | (empty) | Company name is required |
| Email | `karoo.fresh` / `a@b` / `a b@c.co.za` | Enter a valid email address |
| Phone | `555 0101` / `27235550101` / `0123` | Phone must start with + or 0 |
| VAT | `412098765` (9 digits) / `41209876543` (11) / `4120A87654` | SA VAT number must be exactly 10 digits |
| Postal | `600` / `60011` / `ABCD` | SA postal code must be exactly 4 digits |

---

## B. Contacts (open a customer > Contacts)

On **Karoo Fresh Produce**:
1. Add `Annelie` / `van Wyk`, annelie@karoofresh.example.co.za (first contact becomes the **main** contact)
2. Add `Pieter` / `Marais`, pieter@karoofresh.example.co.za

Bad: empty first name, "Enter a first name."; empty last name, "Enter a last name."; Pieter's email changed to Annelie's, "Another contact here already has that email."

On **Mthembu Logistics**: `Sipho` / `Mthembu`, sipho@mthembulogistics.example.co.za.

---

## C. Pipeline (Customers > Pipeline, leads 4 to 9)

| Lead                 | Do this                                               | Expect                                               |
| -------------------- | ----------------------------------------------------- | ---------------------------------------------------- |
| Sunrise Dental       | New -> Contacted -> Qualified                         | moves; WF "stage changed" fires if you built one     |
| Ndlovu Construction  | New -> Contacted -> Qualified -> Won                  | becomes a won deal; try **Undo conversion** if shown |
| Cape Brew Collective | Mark **Lost**, reason "Price too high"                | saved                                                |
| Highveld Agri        | Mark **Lost**, reason **Other**, leave the note empty | blocked: "Add a note saying why."                    |
| Highveld Agri        | Retry with note `Went with local co-op`               | saved                                                |
| Lakeside Guest House | Stay in New                                           | stays                                                |
| Prime Auto Parts     | Contacted                                             | moves                                                |

Check the **Funnel report** and **Dashboard** counts afterwards: 6 leads, 1 won, 2 lost.

---

## D. Opportunities (Customers > Opportunities > New)

| Company                 | Name                        | Amount | Expected close | Stage to move to                                       |
| ----------------------- | --------------------------- | ------ | -------------- | ------------------------------------------------------ |
| Karoo Fresh Produce     | Cold-room expansion         | 420000 | 2026-12-15     | Proposal                                               |
| Karoo Fresh Produce     | Annual supply contract 2027 | 180000 | 2027-01-31     | Negotiation                                            |
| Mthembu Logistics       | Fleet tracking, 25 trucks   | 95000  | 2026-11-30     | Qualified                                              |
| Bay Hardware & Building | Point-of-sale rollout       | 60000  | 2026-11-10     | Won (should fire WF2 tag)                              |
| Sunrise Dental          | Patient booking system      | 38500  | 2026-10-10     | leave Prospecting (**past close date**, shows as late) |
| Bay Hardware & Building | Loyalty programme           | 22000  | 2026-12-01     | Lost, reason Competitor                                |

Check **Forecast**: weighted values use 10 / 25 / 50 / 75 / 100 / 0 percent by stage. Proposal 420000 -> 210000, Negotiation 180000 -> 135000, Qualified 95000 -> 23750.

**Bad data**

| Field | Type this | Expect |
|---|---|---|
| Company | (none chosen, on the all-company tab) | Choose the company. |
| Name | empty / 256+ characters | Give the opportunity a name. / at most 255 characters |
| Amount | `abc` / `-500` / `99999999999999` | must be a number / cannot be negative / too large |
| Close date | `2026-02-30` | Use a real date. |
| Move Won -> Lost directly | | not offered; reopen first |

**Company groups**: Company groups > create `Karoo Group`, add Karoo Fresh Produce as parent, and Highveld Agri as a subsidiary (or whichever two you want). Check the tree shows the hierarchy and a company cannot be its own parent or loop back.

---

## E. Tasks (Customers > Tasks > Add)

| Customer            | Task                    | Due                |
| ------------------- | ----------------------- | ------------------ |
| Karoo Fresh Produce | Send cold-room quote    | 2026-10-23         |
| Mthembu Logistics   | Demo tracking dashboard | 2026-10-28         |
| Sunrise Dental      | Follow up on proposal   | today (2026-10-21) |

Bad: no customer, "Choose a customer."; empty note, "Say what needs doing."; no date, "Choose a due date."; date `2026-10-01`, "The due date cannot be in the past."

Complete one task and check it leaves the queue and the Dashboard "Late tasks" count updates.

---

## F. Segments (Customers > Segments > New)

1. `Referral leads`: Lead source is Referral
2. `Big deals`: Deal value is at least 100000
3. `Gone quiet`: no activity for 30 days (use the "days" condition)

Bad: save with no condition, "Add at least one condition."; deal value `abc` / `-1`, "Deal value must be a number." / "A deal value cannot be negative."; days `0` / `5000` / `2.5`, "Days must be a whole number from 1 to 3650."

---

## G. Campaigns (Customers > Campaigns > New)

**Campaign 1: "Spring Referral Push"**
- Goal: `Win 3 new accounts from referrals before year end`
- Starts 2026-11-01, ends 2026-12-15
- Journey steps (day, action):
  1. Day 0, Add a task: `Intro call`
  2. Day 3, Add a tag: `spring-2026`
  3. Day 7, Add a task: `Send case study`
- Add people: search and add Ndlovu Construction, Sunrise Dental, Karoo Fresh Produce. Add one twice to see "already in".
- Start it (status Active). Then try to edit the steps, which should be **locked** while active.

**Campaign 2: "Year-end Win-back"** — starts 2027-01-05, ends 2027-02-28, one step, left as draft.

**Bad data**

| Field | Type this | Expect |
|---|---|---|
| Name | empty / 121+ chars | Name the campaign. / at most 120 characters |
| Goal | 501+ chars | at most 500 characters |
| Dates | start 2026-12-15, end 2026-11-01 | The end date cannot be before the start date. |
| Step day | `-1` / `400` / `1.5` / empty | the day must be a whole number from 0 to 365 |
| Step order | day 5 under a day 7 step | it cannot come before the step above it |
| Step text | empty on a task / tag over 50 chars | say what the task is / tag at most 50 characters |
| Owner step | no user chosen | choose who becomes the owner |
| Steps | 11 steps | A journey can have at most 10 steps. |

---

## H. Duplicates and merge (Customers > Duplicates)

1. Add a **deliberate duplicate**: `Karoo Fresh Produce` again, Type Lead, same email orders@karoofresh.example.co.za.
2. Open Duplicates. The pair should be listed.
3. Merge: keep the original, remove the new one. The preview shows what moves and which fields conflict.
4. Pick one field value from the removed record. Confirm.
5. Result: one Karoo record, activity shows a "Merged" entry, the removed record is gone from the register.

Bad: pick the same record on both sides, "Choose two different records." Try merging a customer that has invoices (if you have any) and expect a refusal that names the other module.

---

## I. Integrations (Customers > Integrations)

- Webhook: `https://webhook.site/your-test-id`, events: pick two. Expect a test delivery in the log.
- Bad: `http://example.com/hook`, "The address must start with https://."; `not a url`, "That address is not valid."; `https://user:pass@example.com`, "cannot contain a user name or password."; no events ticked, "Choose at least one event."
- API key: create one named `Website form`. The full key is shown **once**; copy it, then confirm it is not shown again.

---

## J. Scheduled reports (Customers > Scheduled reports)

- Funnel report, weekly, to your own email.
- Bad: no report type, "Choose which report to send."; no frequency, "Choose how often to send it."

---

## K. Cross-checks (end-to-end sanity)

| Check | Where | Expect |
|---|---|---|
| Customer count | Dashboard | 9 on the register (10 before merging the duplicate, 9 after) |
| Open deals / expected to win | Dashboard, Forecast | matches section D numbers |
| Late tasks | Dashboard | 0 until a task passes its due date |
| Audit log | Setup > Audit log | entries for creates, stage moves, merge, key creation |
| Permissions | Log in as a user **without** CUSTOMER_UPDATE | can view, no Add/Edit/Merge buttons |
| Deep links | open `/customers/pipeline` directly, and an unknown `/customers/xyz` | pipeline loads; the unknown one falls back to Dashboard |
| Refresh | F5 on any section | stays on that section |

**If something does not match**, note the screen, what you typed, and what you saw, and send it to me.
