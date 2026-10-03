CREATE TABLE zalava_account (
    id UUID PRIMARY KEY,
    login_name VARCHAR(100) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    enabled BOOLEAN NOT NULL,
    role VARCHAR(20) NOT NULL,
    password_change_required BOOLEAN NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT zalava_account_role CHECK (role IN ('ADMIN', 'MEMBER'))
);
