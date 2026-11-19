-- Two more actions can be put behind maker-checker: accepting a counterparty's proposed change, and signing on behalf of a party.
-- Both are off unless a tenant switches them on, so existing behaviour does not change.
ALTER TABLE contract_approval_policies DROP CONSTRAINT chk_contract_approval_policy_action;
ALTER TABLE contract_approval_policies ADD CONSTRAINT chk_contract_approval_policy_action
    CHECK (action IN ('SEND_FOR_SIGNING','PUBLISH_CLAUSE','PUBLISH_TEMPLATE','TERMINATE','ACCEPT_COUNTERPARTY_REDLINE','SIGN_ON_BEHALF'));

ALTER TABLE contract_approvals DROP CONSTRAINT chk_contract_approval_action;
ALTER TABLE contract_approvals ADD CONSTRAINT chk_contract_approval_action
    CHECK (action IN ('SEND_FOR_SIGNING','PUBLISH_CLAUSE','PUBLISH_TEMPLATE','TERMINATE','ACCEPT_COUNTERPARTY_REDLINE','SIGN_ON_BEHALF'));
