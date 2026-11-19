-- Every stored client address now says where it came from: the connection itself (DIRECT) or a forwarded header sent by a configured
-- trusted proxy (TRUSTED_PROXY). Older rows stay NULL: their source was not recorded and is not guessed.
ALTER TABLE contract_signatures ADD COLUMN ip_source VARCHAR(16) CHECK (ip_source IN ('DIRECT','TRUSTED_PROXY'));
ALTER TABLE contract_parties ADD COLUMN sign_ip_source VARCHAR(16) CHECK (sign_ip_source IN ('DIRECT','TRUSTED_PROXY'));
ALTER TABLE contract_negotiation_events ADD COLUMN ip_source VARCHAR(16) CHECK (ip_source IN ('DIRECT','TRUSTED_PROXY'));
ALTER TABLE contract_negotiation_sessions ADD COLUMN ip_source VARCHAR(16) CHECK (ip_source IN ('DIRECT','TRUSTED_PROXY'));
