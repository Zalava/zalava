CREATE TABLE zalava_chat_confirmation_role (
  role_name VARCHAR(20) NOT NULL,
  module_id VARCHAR(160) NOT NULL,
  provider_id VARCHAR(160) NOT NULL,
  tool_name VARCHAR(160) NOT NULL,
  require_confirmation BOOLEAN NOT NULL,
  changed_by UUID NOT NULL REFERENCES zalava_account(id),
  changed_at TIMESTAMP WITH TIME ZONE NOT NULL,
  PRIMARY KEY(role_name, module_id, provider_id, tool_name)
);
CREATE TABLE zalava_chat_confirmation_user (
  account_id UUID NOT NULL REFERENCES zalava_account(id),
  module_id VARCHAR(160) NOT NULL,
  provider_id VARCHAR(160) NOT NULL,
  tool_name VARCHAR(160) NOT NULL,
  require_confirmation BOOLEAN NOT NULL,
  changed_by UUID NOT NULL REFERENCES zalava_account(id),
  changed_at TIMESTAMP WITH TIME ZONE NOT NULL,
  PRIMARY KEY(account_id, module_id, provider_id, tool_name)
);
CREATE TABLE zalava_chat_confirmation_audit (
  id UUID PRIMARY KEY,
  role_name VARCHAR(20),
  account_id UUID REFERENCES zalava_account(id),
  module_id VARCHAR(160) NOT NULL,
  provider_id VARCHAR(160) NOT NULL,
  tool_name VARCHAR(160) NOT NULL,
  require_confirmation BOOLEAN,
  changed_by UUID NOT NULL REFERENCES zalava_account(id),
  changed_at TIMESTAMP WITH TIME ZONE NOT NULL
);
