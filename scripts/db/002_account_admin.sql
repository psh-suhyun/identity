BEGIN;
ALTER TABLE identity."user"
    ADD COLUMN status TEXT NOT NULL DEFAULT 'ACTIVE',
    ADD CONSTRAINT user_status_valid CHECK (status IN ('ACTIVE', 'SUSPENDED', 'DELETED'));
CREATE TABLE identity.admin (
    id UUID PRIMARY KEY REFERENCES identity."user"(id),
    roles TEXT[] NOT NULL,
    CONSTRAINT admin_roles_valid CHECK (cardinality(roles) > 0 AND roles <@ ARRAY['super','admin']::TEXT[])
);
COMMENT ON TABLE identity.admin IS '회원별 관리자 권한';
COMMENT ON COLUMN identity.admin.roles IS 'super, admin 권한 목록';
COMMIT;
