-- Read-only. Lists delegates linked to more than one live learner (V934 does not create uq_trainprov_learner_delegate while any exist).
-- Fix each by unlinking or merging the learners in the application, with a reason, then run the CREATE INDEX at the bottom.
SELECT l.tenant_id, l.delegate_id, d.full_name AS delegate, count(*) AS live_learners, string_agg(l.learner_no, ', ' ORDER BY l.learner_no) AS learner_nos
FROM trainprov_learners l JOIN trainprov_delegates d ON d.id = l.delegate_id
WHERE l.status = 'ACTIVE' AND l.delegate_id IS NOT NULL
GROUP BY l.tenant_id, l.delegate_id, d.full_name HAVING count(*) > 1 ORDER BY 1, 2;

-- Run only once the query above returns no rows:
-- CREATE UNIQUE INDEX uq_trainprov_learner_delegate ON trainprov_learners (tenant_id, delegate_id) WHERE delegate_id IS NOT NULL AND status = 'ACTIVE';
