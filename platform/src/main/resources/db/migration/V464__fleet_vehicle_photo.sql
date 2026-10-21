-- A vehicle's photo is a singleton document type (PHOTO): one current picture per vehicle, older ones kept as history.
-- The partial unique index must list every singleton type, so recreate it with PHOTO added.
DROP INDEX IF EXISTS uq_fleet_vehicle_documents_current_singleton;

CREATE UNIQUE INDEX uq_fleet_vehicle_documents_current_singleton
    ON fleet_vehicle_documents (vehicle_id, doc_type)
    WHERE status = 'CURRENT'
      AND deleted_at IS NULL
      AND doc_type IN ('LICENCE_DISC', 'ROADWORTHY', 'INSURANCE', 'REGISTRATION_CERTIFICATE',
                       'PURCHASE_AGREEMENT', 'FINANCE_AGREEMENT', 'TRACKING_CERTIFICATE', 'PHOTO');
