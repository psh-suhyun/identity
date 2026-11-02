package identity.config;

public final class CacheKeys {
    public static final String ASSESS = "identity:assess";
    public static final String REFRASH = "identity:refrash";
    public static final String TOKEN_ORDER = "identity:token:order";
    public static final String SESSION_PREFIX = "identity:session:";
    public static final String USER_SESSIONS_PREFIX = "identity:user:sessions:";
    public static String session(String token) { return SESSION_PREFIX + token; }
    public static String userSessions(java.util.UUID id) { return USER_SESSIONS_PREFIX + id; }
    public static String rate(String purpose, String value) { return "identity:rate:" + purpose + ":" + identity.util.Digests.sha256(value); }
    private CacheKeys() {}
    public static String assess(String token) { return ASSESS + ":" + token; }
    public static String refrash(String token) { return REFRASH + ":" + token; }
}
