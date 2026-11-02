-- psql -v user_id='<회원 UUID>' -v roles='super,admin' -f scripts/db/003_set_admin.sql
BEGIN;
INSERT INTO identity.admin (id, roles)
VALUES (:'user_id'::UUID, string_to_array(:'roles', ','))
ON CONFLICT (id) DO UPDATE SET roles = EXCLUDED.roles;
COMMIT;
