-- What a package was built from (tender details, requirements, pricing, key personnel), as one short hash per part.
-- Comparing it with the tender today tells the person the package has gone stale. Null for packages built before this existed.
ALTER TABLE tender_packages ADD COLUMN inputs_fingerprint VARCHAR(400);
