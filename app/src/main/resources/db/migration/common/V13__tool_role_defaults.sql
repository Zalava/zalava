CREATE TABLE zalava_tool_role_access (
 module_id VARCHAR(160) NOT NULL,
 provider_id VARCHAR(160) NOT NULL,
 tool_name VARCHAR(160) NOT NULL,
 allowed BOOLEAN NOT NULL,
 changed_by UUID NOT NULL REFERENCES zalava_account(id),
 changed_at TIMESTAMP WITH TIME ZONE NOT NULL,
 PRIMARY KEY(module_id, provider_id, tool_name)
);
CREATE TABLE zalava_tool_role_access_audit (
 id UUID PRIMARY KEY,
 module_id VARCHAR(160) NOT NULL,
 provider_id VARCHAR(160) NOT NULL,
 tool_name VARCHAR(160) NOT NULL,
 allowed BOOLEAN NOT NULL,
 changed_by UUID NOT NULL REFERENCES zalava_account(id),
 changed_at TIMESTAMP WITH TIME ZONE NOT NULL
);
ALTER TABLE zalava_member_tool_access_audit ALTER COLUMN allowed DROP NOT NULL;
