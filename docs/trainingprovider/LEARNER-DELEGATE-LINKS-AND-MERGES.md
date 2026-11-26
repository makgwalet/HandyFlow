# Learner and delegate links, duplicates and merges (patch 0168)

## The model
A **learner** and a **delegate** are two different records and stay that way. The old client, delegate and enrolment model is untouched. A learner can optionally point at one delegate (`delegate_id`) to say "this is the same person". Most learners have no delegate, and a delegate does not have to be a learner.

## Delegate link
- One delegate can be linked to one live learner. The service checks this on every link; V934 adds a unique index for it, but only when existing data already agrees. If it does not, V934 raises a warning, creates nothing, and `db/audit/trainprov_learner_delegate_links.sql` lists the offenders. Nothing is rewritten.
- Linking a delegate whose identity number differs from the learner's needs a written reason (10+ characters). Moving or removing a link always needs one.
- Every link and unlink is a line in `trainprov_learner_events` (who, when, why). Written once.
- `PUT /learners/{id}/delegate` changes the link on its own. The learner edit form now keeps the link as it is. (Before this patch the form sent no delegate, so an edit would have cleared it.)
- Sponsoring organisation stays on each enrolment (`client_id`) and on the learner. Merging does not touch either.

## Finding duplicates
Pure rules in `LearnerMatchRules`. They compare identity number, name (any order), date of birth, email and phone (last nine digits). STRONG means an identity number matches, or a name plus a birth date, email or phone, or an email plus a phone. Everything else that shares something is POSSIBLE. If identity numbers or birth dates disagree, a match is never better than POSSIBLE and the disagreement is shown. Archived learners are included, so a returning person is not added a second time; merged records are left out.
- `POST /learners/check-duplicates` for a learner being typed in
- `GET /learners/{id}/possible-duplicates`
- `GET /learners/duplicate-pairs` (live learners, grouped first so a long list is not compared pair by pair)
- `GET /learners/unmatched-delegates` (delegates with no live learner, with look-alike learners)

A match never changes anything. A person decides.

## Merging
1. A person proposes keeping one learner and merging the other into it, with a reason. If the records disagree, they have to say they checked. Both records are copied into the merge as they stood.
2. A second person (an administrator) approves or rejects it. The proposer can approve only by saying why no second person is deciding (10+ characters). The proposer can cancel their own proposal.
3. On approval, in one transaction: the merged learner's enrolments, applications and documents move to the kept learner; the merged learner is archived with `merged_into_id`; a history line is written on both; the merge records how many of each moved.
4. Refused when both learners are live or open on the same programme, so nobody is enrolled twice. Finish or withdraw one first.

Not changed by a merge: each enrolment's programme version, site, sponsoring organisation, status and history; the name printed on any document already issued; the kept learner's own details (nothing is copied across or overwritten, and the merged learner's details are kept in the merge record).
Delegate link: if the kept learner has none and the merged one did, the link moves; if they differed, the merged learner's delegate becomes unmatched and the merge says so.
A merged learner cannot be restored.

## Screens
Learners → **Duplicates**: possible pairs (propose a merge), delegates with no learner (link a look-alike), merges (approve, reject, cancel). On a learner: the delegate link with change/remove and a history.

## Not done here
- The delegate link does not change the old enrolment model's records.
- Merging does not rewrite attendance or marks, which hang off the enrolment and so move with it.
- No automatic merging, ever.
