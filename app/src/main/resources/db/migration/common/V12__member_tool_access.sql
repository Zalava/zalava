CREATE TABLE zalava_member_tool_access (
    member_id UUID NOT NULL REFERENCES zalava_account(id),
    module_id VARCHAR(160) NOT NULL,
    provider_id VARCHAR(160) NOT NULL,
    tool_name VARCHAR(160) NOT NULL,
    allowed BOOLEAN NOT NULL,
    changed_by UUID NOT NULL REFERENCES zalava_account(id),
    changed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (member_id, module_id, provider_id, tool_name)
);
CREATE TABLE zalava_member_tool_access_audit (
    id UUID PRIMARY KEY,
    member_id UUID NOT NULL REFERENCES zalava_account(id),
    module_id VARCHAR(160) NOT NULL,
    provider_id VARCHAR(160) NOT NULL,
    tool_name VARCHAR(160) NOT NULL,
    allowed BOOLEAN NOT NULL,
    changed_by UUID NOT NULL REFERENCES zalava_account(id),
    changed_at TIMESTAMP WITH TIME ZONE NOT NULL
);
