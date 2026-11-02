# Identity

Java 17 / Spring Boot 4.1.2 기반 회원·인증 서버의 초기 실행 구성입니다.
PostgreSQL은 Spring Data JPA로, Redis assessToken은 JSON 문자열로 저장·조회합니다.
refrashToken은 Spring Data Redis Repository로 조회합니다.
토큰 발급·교체와 순번 예약은 원자성을 위해 Redis Lua로 처리합니다.

## 실행

Java 17 이상과 Node.js/npm이 필요합니다. Maven은 Wrapper가 다운로드하므로 별도 설치하지 않아도 됩니다.
첫 실행에는 Maven 및 의존성 다운로드를 위한 인터넷 연결이 필요합니다.
npm 패키지 의존성이 없어 `npm install`은 필요하지 않습니다.

1. 실행 중인 PostgreSQL에 사용할 DB와 접속 계정을 준비하고 Redis를 실행합니다.
2. `cp .env.example .env` 후 실제 접속 정보를 입력합니다.
3. `npm run dev` 또는 `npm start`로 실행합니다.

`.env`는 Spring properties 형식으로 읽습니다. `KEY=value` 형식으로 작성하고 값에 셸 따옴표나 `export`를 넣지 마세요.
실제 비밀번호는 Git에 포함하지 않습니다. 셸 환경변수가 `.env`보다 우선합니다.

```sh
PORT=8081 npm run dev
```

`PORT` 미설정 시 기본 포트는 8080입니다. `.env.example`에 모든 기본 접속 항목이 있습니다.
PostgreSQL 접속은 `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`로 설정합니다.
`ROOT_PASSWORD`는 로컬 PostgreSQL 관리자 `root` 계정용이며 앱은 `identity` 일반 계정을 사용합니다.
Redis는 `REDIS_URL` 하나로 설정합니다. 예: `redis://localhost:6379/0`, 인증이 있으면 `redis://username:password@localhost:6379/0`입니다. URL의 사용자명·비밀번호에 특수문자가 있으면 percent-encoding해야 합니다.

로컬 PostgreSQL은 `postgres` 컨테이너와 `identity-postgres-data` 볼륨을 사용하며 `127.0.0.1:5432`에서 접근합니다.
`.env`의 비밀번호 변경만으로 DB 계정 비밀번호가 변경되지는 않습니다.

## 시작 조건과 상태 확인

HTTP 요청을 받기 전에 PostgreSQL `SELECT 1`, Redis `PING`을 순서대로 실행합니다.
둘 중 하나라도 실패하거나 응답이 예상과 다르면 시작을 중단하고 프로세스를 비정상 종료합니다.
자동 DB 생성, 테이블 생성, migration 및 데이터 쓰기는 수행하지 않습니다.

```sh
curl -i http://localhost:8080/actuator/health
```

- 정상: HTTP 200, `{"status":"success","statusCode":200,"message":"OK","data":{"status":"UP"}}` (data에 그룹 정보가 포함될 수 있습니다.)
- 실행 중 DB/Redis 연결 장애: HTTP 503, 공통 응답의 `data.status`가 `DOWN`
- `/actuator/health/liveness`: 프로세스 생존 상태
- `/actuator/health/readiness`: PostgreSQL·Redis를 포함한 요청 처리 준비 상태

상태 상세 정보와 인증 정보는 응답에 노출하지 않습니다.
상태 확인 및 아래 인증 API만 Spring Security에서 허용합니다. 미등록 경로는 404를 반환합니다. 기본 로그인 계정은 생성하지 않습니다.
실행 후 연결 장애는 health에 반영하며 프로세스를 자동 종료하지는 않습니다.

## 검증 및 빌드

```sh
npm test
npm run build
java -jar target/identity-0.0.1-SNAPSHOT.jar
```

`build`는 테스트를 포함한 Maven `clean verify`를 실행합니다.
자동 테스트는 연결 성공/실패 제어와 상태 확인·보안 구성을 mock으로 검증하므로 실제 DB가 없어도 실행할 수 있습니다.

## 주요 파일

- `src/main/resources/application.properties`: 환경변수, DB/Redis, 캐시 TTL, 상태 확인 설정
- `src/main/java/identity/config/StartupConnectionVerifier.java`: 시작 시 연결 확인
- `src/main/java/identity/config/SecurityConfig.java`: 초기 접근 제한
- `src/main/java/identity/config/ApplicationConfig.java`: Redis 캐시 활성화

회원가입·로그인·인증 확인·토큰 갱신 API를 제공합니다.

## 패키지 구조와 구현 원칙

```text
identity/
├── IdentityApplication.java
├── config/
├── controller/
├── service/
├── repository/
├── security/
└── util/
```

- `config`: Spring Bean, DB·Redis·캐시 및 보안 설정
- `controller`: HTTP 입력 검증, Service 호출, 응답 처리
- `service`: 회원·인증 비즈니스 규칙과 트랜잭션 경계
- `repository`: PostgreSQL JPA Entity/Repository 및 Redis Entity/Repository
- `security`: 인증·인가 처리와 인증 주체 관리
- `util`: 도메인에 종속되지 않는 공통 기능

기본 호출 방향은 `controller → service → repository`입니다.
요청·응답 및 조회 결과 타입은 사용하는 계층의 기능과 함께 배치하고,
별도의 최상위 `dto`, `entity`, `exception` 폴더는 두지 않습니다.
JPA는 `ddl-auto=validate`로 기존 테이블을 검증하며 자동 생성·변경하지 않습니다.

## 실행 도구 위치

Maven Wrapper 관련 파일은 `src`와 같은 위치의 `scripts/`에서 관리합니다.

```text
scripts/
├── mvnw
├── mvnw.cmd
└── .mvn/
    └── wrapper/
        └── maven-wrapper.properties
```

프로젝트 루트에서 `npm run dev`, `npm start`, `npm test`, `npm run build`를 실행합니다.
Maven을 직접 호출하려면 루트에서 `./scripts/mvnw -B test`를 사용합니다.
Windows에서 직접 호출할 때는 `scripts\mvnw.cmd -B test`를 사용합니다.
`pom.xml`은 Maven 프로젝트 기준 파일이므로 루트에 유지합니다.

## 공통 HTTP 응답과 오류 처리

JSON 응답은 `identity.util.Http`를 사용합니다. Controller가 일반 객체나 문자열을 반환해도
`HttpResponseAdvice`가 같은 형태로 변환합니다. `Http.Body`는 중복 포장하지 않습니다.

```json
{
  "status": "success",
  "statusCode": 200,
  "message": "OK",
  "data": {"id": 1}
}
```

`status`는 400 미만이면 `success`, 오류이면 `error`입니다.
데이터 없는 오류도 `data: null`을 포함하며 `count`는 지정한 경우에만 포함합니다.
`204 No Content`, `304 Not Modified`, HEAD 응답 등 HTTP 규칙상 본문이 없는 응답은 JSON 본문을 보내지 않습니다.

```java
return Http.ok(data);                    // 200
return Http.ok(items, totalCount);       // 200 + count
return Http.created(data);               // 201
return Http.noContent();                 // 204, 본문 없음
return Http.notModified();               // 304, 본문 없음
return Http.badRequest("잘못된 요청입니다.");
return Http.notFound("회원을 찾을 수 없습니다.");
```

오류 메서드는 기본 메시지로 호출하거나 문자열 메시지를 전달할 수 있습니다.
지원 메서드: `badRequest`(400), `unauthorized`(401), `forbidden`(403), `notFound`(404),
`methodNotAllowed`(405), `tooManyRequests`(429), `payloadTooLarge`(413),
`internalServerError`(500), `badGateway`(502), `serviceUnavailable`(503), `gatewayTimeout`(504).

- `controller/NotFoundHandler`: 미등록 API·리소스를 공통 404 응답으로 변환
- `controller/ErrorHandler`: 잘못된 JSON·입력 검증·지원하지 않는 메서드 등은 기존 HTTP 상태를 유지하고, 예상하지 못한 예외는 공통 500 응답으로 변환
- `security/SecurityErrorHandler`: 등록된 API의 접근 제한을 유지하며 인증·인가 실패도 공통 응답으로 연결
- `controller/FallbackErrorController`: Servlet 오류 dispatch도 같은 응답 형식으로 처리

예외 메시지와 stack trace는 오류 응답에 포함하지 않습니다.

## 개발 중 자동 재시작

`npm run dev`는 `scripts/dev.mjs`로 실행합니다.
`src/main`의 Java·설정·리소스 파일 추가/수정/삭제, `pom.xml`, `.env` 변경을 감지합니다.
변경 감지 후 약 0.5초 동안 저장을 모아 기존 Maven/JVM을 종료하고 다시 컴파일·기동합니다.
실제 재시작 시간에는 Maven 컴파일과 Spring Boot 기동 시간이 추가됩니다.
DB·Redis 시작 연결 확인과 시작 로고·주소 출력도 매번 실행됩니다.

컴파일 오류나 시작 실패 시 오류를 출력하고 변경 감지를 계속합니다.
문제를 고친 뒤 저장하면 다시 실행합니다. `Ctrl+C`로 감시와 서버를 함께 종료합니다.
`npm start`는 변경 감지 없이 한 번 실행하는 기존 명령입니다.

## 회원 DB 구조

`scripts/db/001_create_identity_tables.sql`은 `identity` DB의 `identity` 스키마에 다음 테이블을 생성합니다.
이미 생성된 환경에 재실행하는 초기화 스크립트가 아니므로 변경은 별도 SQL로 관리합니다.

- `identity."user"`: `id`(UUIDv7 PK), `name`(필수), `phone`, `email`, `created_at`, `updated_at`
- `identity.user_sign`: `id`(PK 및 user.id FK), `username`(필수·고유), `password_hash`(필수)
- `identity.user_profile`: `id`(PK 및 user.id FK), `profile_url`, `nick_name`, `metadata`

`user`는 PostgreSQL 키워드이므로 쿼리에서 `identity."user"`로 표기합니다.
PostgreSQL 16은 UUIDv7 생성 함수를 기본 제공하지 않습니다. 앱에서 UUIDv7을 생성해 전달하며,
DB는 UUID 타입과 CHECK 제약조건으로 UUIDv7을 검증합니다.

`phone`, `email`, `profile_url`, `nick_name`은 선택 항목입니다.
로그인 아이디는 대소문자를 구분하며, 비밀번호는 해시만 저장합니다.
프로필의 `metadata`는 DB에서 기본값 `{}`인 TEXT이며 유효한 JSON만 허용합니다.
JPA converter가 JSON 타입(`JsonNode`)으로 변환하므로 API 응답은 문자열이 아닌 JSON 값으로 반환합니다.
FK의 기본 삭제 제한을 유지하며 자동 연쇄 삭제는 설정하지 않았습니다.
`updated_at`은 수정 시 앱에서 갱신합니다.

## 인증 API 계약

모든 응답은 위 공통 `Http` 형식입니다. `assessToken`, `refrashToken`은 요청한 이름을 유지합니다.

| Method | URL | 입력 | 성공 data |
| --- | --- | --- | --- |
| POST | `/v1/signup` | JSON: `username`, `password`, `name`, 선택 `phone`, `email` | 회원 정보, 201 |
| POST | `/v1/login` | JSON: `username`, `password` | 토큰 2개, 만료까지 초, `user`, 200 |
| GET | `/v1/sign` | `assessToken` 쿠키 | 회원 정보, 200 |
| POST | `/v1/logout` | 쿠키의 assessToken·refrashToken, body 없음 | null, 200 |
| PATCH | `/v1/password` | assessToken 쿠키 + JSON: currentPassword, newPassword | null, 200 |
| POST | `/v1/refresh` | `refrashToken` 쿠키 | 새 assessToken, 기존 refrashToken, 만료까지 초, `user`, 200 |

회원 정보는 `id`, `username`, `name`, `phone`, `email`, `createdAt`, `updatedAt`,
`profile: {profileUrl, nickName, metadata}`입니다. 비밀번호와 해시는 반환하지 않습니다.
가입은 자동 로그인하지 않습니다. 동일 username은 409, 잘못된 로그인이나 만료 토큰은 401,
저장소 연결 장애는 503입니다. 토큰은 쿠키에서만 읽으며 인증 헤더는 사용하지 않습니다.

로그인 응답은 `assessToken`, `refrashToken`, `assessExpiresIn`, `refrashExpiresIn`, `user`를 포함하며
두 토큰을 `HttpOnly; SameSite=Lax; Path=/` 쿠키에도 저장합니다.
HTTPS 요청에서는 Secure 쿠키를 사용합니다. TLS 종료 프록시 환경에서는
`IDENTITY_AUTH_SECURE_COOKIES=true`로 Secure를 명시하세요.

CSRF 검증은 비활성화되어 있으며 `/v1/csrf`와 `X-XSRF-TOKEN` 헤더는 사용하지 않습니다.
로그인 후 브라우저가 보내는 `assessToken` 쿠키로 인증하고,
갱신 요청에서는 `refrashToken` 쿠키를 사용합니다.

### Body 검증

Controller의 `@Valid @RequestBody`와 요청 record의 `@NotBlank`, `@Size`, `@Email`이
body를 검증합니다. 누락·빈 필수값·잘못된 JSON·이메일 형식 오류는 service 호출 전에
공통 `ErrorHandler`에서 400으로 처리하므로 각 route에서 검사 코드를 반복하지 않습니다.
username은 공백 없이 최대 100자, name은 최대 100자, phone은 최대 30자,
email은 최대 254자입니다. 가입 비밀번호는 8자 이상, BCrypt 제한인 UTF-8 72바이트 이하입니다.

```java
public ResponseEntity<Http.Body<AuthService.Member>> signup(
        @Valid @RequestBody AuthService.SignupRequest input) {
    return Http.created(auth.signup(input));
}
```

### Redis 인증 데이터

- `identity:assess:at_<UUIDv7 32자리 hex>`: `/v1/sign`의 `data` 전체를 담은 JSON 문자열(String), TTL 6시간
- `identity:refrash:rt_<UUIDv7 32자리 hex>`: 회원 ID + 연결된 assessToken, TTL 3일
- `identity:token:order`: 발급 순서 상태, 만료 없음

key는 `config/CacheKeys.java`, TTL은 `config/AuthConfig.java`에서 관리합니다.
토큰은 UUIDv7의 하이픈을 제거한 소문자 hex입니다. 같은 종류의 토큰은 문자열로 정렬하면
Redis에서 예약된 발급 순서대로 정렬됩니다. 서로 다른 종류는 `at_`/`rt_` 접두사를 제외하고 비교합니다.
동시 요청의 HTTP 응답 도착 순서까지 보장하는 것은 아닙니다.
Redis 순번 상태를 유지해야 재시작 이후에도 순서가 이어지므로 해당 key를 삭제하지 않아야 합니다.

assessToken 만료 후에도 유효한 refrashToken으로 갱신할 수 있습니다.
갱신 시 이전 assessToken은 삭제하고 새 토큰에 6시간을 부여합니다.
refrashToken의 최초 3일 만료 시간은 늘리지 않습니다. 같은 이전 상태로 동시에 갱신하면
한 요청만 성공합니다. 만료된 refrashToken은 401이므로 다시 로그인해야 합니다.

### Redis 통합 테스트

기본 `npm test`는 외부 Redis 통합 테스트를 건너뜁니다.
별도 테스트 전용 Redis의 포트를 지정하면 실제 Lua, TTL, Repository 변환 및 동시 갱신을 검증합니다.
테스트가 데이터를 쓰므로 운영/개발용 Redis 포트를 지정하지 마세요.

```sh
IDENTITY_TEST_REDIS_PORT=16379 npm run build
```

`GET /v1/sign`은 assessToken의 회원 JSON을 읽고 현재 DB 관리자 권한으로 `admin`을 보정해 `data`로 반환합니다.
외부 응답의 `status`, `statusCode`, `message`는 저장하지 않습니다. `profile.metadata`도 JSON 타입을 유지합니다.
회원 정보는 로그인·refresh 때 DB에서 읽고, 회원·프로필 수정 시 활성 세션의 회원 JSON도 갱신합니다.
회원 수정·삭제 기능을 추가할 때 해당 회원의 토큰을 갱신하거나 무효화해야 합니다.
기존 Hash 타입 assessToken은 인증되지 않으며 다시 로그인하거나 유효한 refrashToken으로 갱신해야 합니다.

## 로그아웃과 비밀번호 변경

`POST /v1/logout`은 현재 세션의 assessToken과 refrashToken을 Redis에서 함께 삭제하고,
`Path=/; Max-Age=0`으로 두 쿠키를 만료시킵니다. 토큰이 이미 만료되었거나 쿠키가 없어도 200입니다.
연결 정보가 있는 세션은 쿠키 하나만 전달되어도 연결된 토큰 쌍을 삭제합니다.
Redis 장애 시 삭제 성공으로 응답하지 않고 503을 반환합니다.

`PATCH /v1/password`는 assessToken 쿠키로 인증한 회원의 현재 비밀번호를 확인합니다.

```json
{"currentPassword":"현재 비밀번호","newPassword":"새 비밀번호"}
```

현재 비밀번호 불일치는 401, 새 비밀번호 형식 오류 또는 현재 비밀번호와 동일하면 400입니다.
가입과 같은 8자 이상·UTF-8 72바이트 이하 제한을 적용하며 BCrypt 해시만 DB에 저장합니다.
성공하면 해당 회원의 모든 Redis 로그인 세션을 해제하고 현재 브라우저의 쿠키를 지웁니다.
다시 로그인해야 합니다. 기존 다른 브라우저의 쿠키는 다음 요청부터 401입니다.
Redis 해제 실패 시 DB 트랜잭션도 rollback됩니다.
로그인·refresh·비밀번호 변경은 회원의 로그인 행에 잠금을 사용해 동시에 발급 중인 세션도 처리합니다.

세션 추적에는 `identity:session:<assessToken>`, `identity:session:<refrashToken>`,
`identity:user:sessions:<회원 UUID>`를 사용합니다. 추적 정보는 연결된 두 토큰이 모두 만료될 때까지 유지합니다.
assessToken의 회원 JSON 저장 형식은 유지하며, 추적 정보가 없는 이전 발급 토큰은 다시 로그인해야 합니다.
비밀번호 찾기는 아직 구현하지 않았습니다. 이메일·휴대폰 소유 확인과 발송 수단을 정한 뒤 추가합니다.

## 회원·세션·관리자 확장

`002_account_admin.sql`을 기존 001 적용 이후 실행합니다. 로컬에는 002까지 적용했습니다.
회원 `status`는 ACTIVE / SUSPENDED / DELETED이며 기존 회원은 ACTIVE로 유지됩니다.
`identity.admin`은 user.id를 참조하는 id와 TEXT[] roles를 저장합니다.
권한 값은 super / admin만 허용하고 회원가입이나 사용자 수정으로 권한을 부여할 수 없습니다.

관리자 지정은 별도 SQL `003_set_admin.sql`을 psql 변수 user_id, roles로 실행합니다.
예: `psql ... -v user_id='<회원 UUID>' -v roles='super,admin' -f scripts/db/003_set_admin.sql`.
이 SQL은 자동 실행하지 않았으며 현재 관리자 계정은 지정하지 않았습니다.
권한 제거는 해당 회원의 admin 행을 삭제합니다. `super`와 `admin` 모두 `/docs` 접근 권한을 가집니다.
관리자 부여·상태 변경 HTTP API는 제공하지 않습니다.

`GET /v1/sign`의 data에 `admin: []` 또는 `admin: ["super", "admin"]`이 포함됩니다.
보안 판단에 쓰는 회원 상태와 권한은 매 인증 시 DB에서 확인하므로,
Redis 회원 JSON의 오래된 관리자 권한으로 접근할 수 없습니다.
ACTIVE 이외의 계정은 로그인·인증·refresh가 거절됩니다.
SQL로 일시 정지 후 ACTIVE로 복원하면 만료되지 않은 기존 세션은 다시 사용될 수 있습니다.
앱에서 탈퇴하면 DELETED로 변경하고 모든 세션을 삭제합니다. 물리적인 개인정보 삭제는 수행하지 않습니다.
탈퇴한 username은 재사용되지 않으며 super 계정은 권한을 SQL로 제거한 후 탈퇴할 수 있습니다.

추가 API:

- `PATCH /v1/user`: name, phone, email 중 전달한 필드 수정. 빈 객체·공백 name은 400.
- `PATCH /v1/profile`: profileUrl, nickName, metadata 수정. metadata는 최대 16KiB JSON 객체.
  이미지 파일 업로드 대신 URL을 받으며 http/https만 허용합니다.
- `DELETE /v1/user`: `{"password":"현재 비밀번호"}`로 탈퇴. 전체 세션 해제 및 쿠키 삭제.
- `GET /v1/sessions`: 세션 id, IP, userAgent, 생성·최근 활동 시간, current 반환.
  외부 세션 id는 토큰의 SHA-256이며 원본 토큰은 노출하지 않습니다.
- `DELETE /v1/sessions/{sessionId}`: 자신의 특정 세션 종료. 다른 회원 세션은 404.
- `DELETE /v1/sessions`: 모든 로그인 세션 종료 및 현재 쿠키 삭제.

수정 요청의 null 또는 생략 필드는 유지합니다. phone/email/nickName/profileUrl은 빈 문자열로 비울 수 있습니다.
회원·프로필 수정은 회원 JSON을 활성 assessToken 전체에 갱신하며 원래 TTL을 유지합니다.
이메일·전화번호 변경은 현재 소유 인증을 포함하지 않으며 계정 복구 수단으로 사용할 수 없습니다.
이메일 인증·비밀번호 찾기는 발송 수단이 정해질 때까지 보류했습니다.

로그인은 15분당 username 10회 / IP 100회까지 허용합니다. 성공 시 username 카운터는 초기화하고
IP 요청 카운터는 유지합니다. 초과하면 429와 Retry-After(초)를 반환합니다.
IP는 서버가 받은 remoteAddr를 사용하며 임의의 X-Forwarded-For는 신뢰하지 않습니다.

`/docs`는 관리자 전용 API 문서 및 요청 실행 화면이며 `/v1/docs/openapi`에서 OpenAPI JSON을 제공합니다.
문서의 실행 버튼은 실제 서버에 요청합니다. JSON 응답 envelope는 API에 적용하며 HTML/OpenAPI 문서는 원문을 반환합니다.
쿠키 Path는 `/`로 변경했습니다. 로그인·로그아웃 때 이전 `/v1` 쿠키도 만료시킵니다.
