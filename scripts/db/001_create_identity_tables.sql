-- identity DB에 적용한다. UUIDv7은 애플리케이션에서 생성해 전달한다.
-- PostgreSQL 16에는 기본 UUIDv7 생성 함수가 없으므로 DB 기본값을 지정하지 않는다.
BEGIN;

CREATE SCHEMA identity;

CREATE TABLE identity."user" (
    id UUID PRIMARY KEY,
    name TEXT NOT NULL,
    phone TEXT,
    email TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT user_id_uuidv7 CHECK (
        substring(id::TEXT FROM 15 FOR 1) = '7'
        AND substring(id::TEXT FROM 20 FOR 1) IN ('8', '9', 'a', 'b')
    )
);

CREATE TABLE identity.user_sign (
    id UUID PRIMARY KEY REFERENCES identity."user" (id),
    username TEXT NOT NULL,
    password_hash TEXT NOT NULL,

    CONSTRAINT user_sign_username_unique UNIQUE (username),
    CONSTRAINT user_sign_username_not_blank CHECK (length(btrim(username)) > 0),
    CONSTRAINT user_sign_password_hash_not_blank CHECK (length(btrim(password_hash)) > 0)
);

CREATE TABLE identity.user_profile (
    id UUID PRIMARY KEY REFERENCES identity."user" (id),
    profile_url TEXT,
    nick_name TEXT,
    metadata TEXT NOT NULL DEFAULT '{}',

    CONSTRAINT user_profile_metadata_json CHECK (metadata IS JSON)
);

COMMENT ON SCHEMA identity IS '회원 및 인증';

COMMENT ON TABLE identity."user" IS '회원 기본 정보';
COMMENT ON COLUMN identity."user".id IS '회원 고유 ID: 애플리케이션에서 생성한 UUIDv7';
COMMENT ON COLUMN identity."user".name IS '이름';
COMMENT ON COLUMN identity."user".phone IS '전화번호';
COMMENT ON COLUMN identity."user".email IS '이메일';
COMMENT ON COLUMN identity."user".created_at IS '가입 시각';
COMMENT ON COLUMN identity."user".updated_at IS '최종 수정 시각: 수정 시 애플리케이션에서 갱신';

COMMENT ON TABLE identity.user_sign IS '아이디 및 비밀번호 로그인 정보';
COMMENT ON COLUMN identity.user_sign.id IS '회원 ID: user.id 참조, 회원당 최대 한 행';
COMMENT ON COLUMN identity.user_sign.username IS '로그인 아이디: 대소문자를 구분하며 중복을 허용하지 않음';
COMMENT ON COLUMN identity.user_sign.password_hash IS '비밀번호 해시: 평문 비밀번호 저장 금지';

COMMENT ON TABLE identity.user_profile IS '회원 프로필';
COMMENT ON COLUMN identity.user_profile.id IS '회원 ID: user.id 참조, 회원당 최대 한 행';
COMMENT ON COLUMN identity.user_profile.profile_url IS '프로필 이미지 URL';
COMMENT ON COLUMN identity.user_profile.nick_name IS '닉네임';
COMMENT ON COLUMN identity.user_profile.metadata IS '유효한 JSON을 담는 문자열';

COMMIT;
