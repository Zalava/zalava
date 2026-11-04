CREATE TABLE zalava_page_access_default (
 module_id VARCHAR(160) NOT NULL, page_id VARCHAR(160) NOT NULL, allowed BOOLEAN NOT NULL,
 changed_by UUID NOT NULL REFERENCES zalava_account(id), changed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
 PRIMARY KEY(module_id,page_id)
);
CREATE TABLE zalava_page_access_user (
 account_id UUID NOT NULL REFERENCES zalava_account(id), module_id VARCHAR(160) NOT NULL, page_id VARCHAR(160) NOT NULL, allowed BOOLEAN NOT NULL,
 changed_by UUID NOT NULL REFERENCES zalava_account(id), changed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
 PRIMARY KEY(account_id,module_id,page_id)
);
CREATE TABLE zalava_page_access_audit (
 id UUID PRIMARY KEY, account_id UUID REFERENCES zalava_account(id), module_id VARCHAR(160) NOT NULL, page_id VARCHAR(160) NOT NULL,
 decision VARCHAR(10) NOT NULL, changed_by UUID NOT NULL REFERENCES zalava_account(id), changed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
