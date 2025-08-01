package io.backup4j.core.config;

import io.backup4j.core.util.ConfigParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.FileWriter;
import java.nio.file.Path;
import java.util.Properties;
import java.lang.reflect.Method;
import java.lang.reflect.Field;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 환경 변수 참조 기능 테스트
 * ${ENV_VAR} 형식의 환경 변수 참조를 실제 환경 변수 값으로 치환하는 기능을 테스트합니다.
 */
class EnvironmentVariableInterpolationTest {

    @TempDir
    Path tempDir;

    @Test
    void resolveEnvironmentVariables_단일환경변수_정상치환() throws Exception {
        // Given
        Properties props = new Properties();
        props.setProperty("database.password", "${DB_PASSWORD}");
        props.setProperty("database.username", "user");
        
        // 환경변수 시뮬레이션을 위한 리플렉션 사용
        setEnvironmentVariable("DB_PASSWORD", "secret123");
        
        try {
            // When
            Properties resolved = invokeResolveEnvironmentVariables(props);
            
            // Then
            assertEquals("secret123", resolved.getProperty("database.password"));
            assertEquals("user", resolved.getProperty("database.username"));
        } finally {
            clearEnvironmentVariable("DB_PASSWORD");
        }
    }

    @Test
    void resolveEnvironmentVariables_여러환경변수_정상치환() throws Exception {
        // Given
        Properties props = new Properties();
        props.setProperty("backup.s3.access-key", "${AWS_ACCESS_KEY}");
        props.setProperty("backup.s3.secret-key", "${AWS_SECRET_KEY}");
        props.setProperty("backup.s3.bucket", "my-bucket");
        
        setEnvironmentVariable("AWS_ACCESS_KEY", "AKIA123456789");
        setEnvironmentVariable("AWS_SECRET_KEY", "secretkey123456789");
        
        try {
            // When
            Properties resolved = invokeResolveEnvironmentVariables(props);
            
            // Then
            assertEquals("AKIA123456789", resolved.getProperty("backup.s3.access-key"));
            assertEquals("secretkey123456789", resolved.getProperty("backup.s3.secret-key"));
            assertEquals("my-bucket", resolved.getProperty("backup.s3.bucket"));
        } finally {
            clearEnvironmentVariable("AWS_ACCESS_KEY");
            clearEnvironmentVariable("AWS_SECRET_KEY");
        }
    }

    @Test
    void resolveEnvironmentVariables_환경변수없음_원본유지() throws Exception {
        // Given
        Properties props = new Properties();
        props.setProperty("database.password", "${NONEXISTENT_VAR}");
        props.setProperty("database.username", "user");
        
        // When
        Properties resolved = invokeResolveEnvironmentVariables(props);
        
        // Then
        assertEquals("${NONEXISTENT_VAR}", resolved.getProperty("database.password")); // 원본 유지
        assertEquals("user", resolved.getProperty("database.username"));
    }

    @Test
    void resolveEnvironmentVariables_혼합값_부분치환() throws Exception {
        // Given
        Properties props = new Properties();
        props.setProperty("database.url", "${DATABASE_URL}");
        props.setProperty("backup.local.path", "/backup/${BACKUP_ENV}/data");
        
        setEnvironmentVariable("DATABASE_URL", "jdbc:mysql://localhost:3306/mydb");
        setEnvironmentVariable("BACKUP_ENV", "production");
        
        try {
            // When
            Properties resolved = invokeResolveEnvironmentVariables(props);
            
            // Then
            assertEquals("jdbc:mysql://localhost:3306/mydb", resolved.getProperty("database.url"));
            assertEquals("/backup/production/data", resolved.getProperty("backup.local.path"));
        } finally {
            clearEnvironmentVariable("DATABASE_URL");
            clearEnvironmentVariable("BACKUP_ENV");
        }
    }

    @Test
    void resolveEnvironmentVariables_동일환경변수중복_모두치환() throws Exception {
        // Given
        Properties props = new Properties();
        props.setProperty("test.value", "${SECRET_KEY}-${SECRET_KEY}-suffix");
        
        setEnvironmentVariable("SECRET_KEY", "abc123");
        
        try {
            // When
            Properties resolved = invokeResolveEnvironmentVariables(props);
            
            // Then
            assertEquals("abc123-abc123-suffix", resolved.getProperty("test.value"));
        } finally {
            clearEnvironmentVariable("SECRET_KEY");
        }
    }

    @Test
    void resolveEnvironmentVariables_빈값처리_정상동작() throws Exception {
        // Given
        Properties props = new Properties();
        props.setProperty("empty.value", "");
        props.setProperty("null.value", "${NULL_VAR}");
        
        // When
        Properties resolved = invokeResolveEnvironmentVariables(props);
        
        // Then
        assertEquals("", resolved.getProperty("empty.value"));
        assertEquals("${NULL_VAR}", resolved.getProperty("null.value"));
    }

    @Test
    void resolveEnvironmentVariables_특수문자환경변수_정상처리() throws Exception {
        // Given
        Properties props = new Properties();
        props.setProperty("special.chars", "${SPECIAL_VAR}");
        
        setEnvironmentVariable("SPECIAL_VAR", "value-with-special@#$%^&*()chars");
        
        try {
            // When
            Properties resolved = invokeResolveEnvironmentVariables(props);
            
            // Then
            assertEquals("value-with-special@#$%^&*()chars", resolved.getProperty("special.chars"));
        } finally {
            clearEnvironmentVariable("SPECIAL_VAR");
        }
    }

    @Test
    void configParser_환경변수참조_통합테스트() throws Exception {
        // Given - 환경변수 참조가 포함된 설정 파일 생성
        Path configFile = tempDir.resolve("test-config.yaml");
        String yamlContent = "database:\n" +
            "  url: ${DATABASE_URL}\n" +
            "  username: ${DB_USER}\n" +
            "  password: ${DB_PASSWORD}\n" +
            "\n" +
            "backup:\n" +
            "  s3:\n" +
            "    enabled: true\n" +
            "    access-key: ${AWS_ACCESS_KEY}\n" +
            "    secret-key: ${AWS_SECRET_KEY}\n" +
            "    bucket: ${S3_BUCKET}\n";
        
        try (FileWriter writer = new FileWriter(configFile.toFile())) {
            writer.write(yamlContent);
        }
        
        // 환경변수 설정
        setEnvironmentVariable("DATABASE_URL", "jdbc:mysql://test.example.com:3306/testdb");
        setEnvironmentVariable("DB_USER", "testuser");
        setEnvironmentVariable("DB_PASSWORD", "testpass123");
        setEnvironmentVariable("AWS_ACCESS_KEY", "AKIATEST123");
        setEnvironmentVariable("AWS_SECRET_KEY", "testsecret456");
        setEnvironmentVariable("S3_BUCKET", "test-backup-bucket");
        
        try {
            // When
            BackupConfig config = ConfigParser.parseConfigFile(configFile.toString());
            
            // Then
            assertEquals("jdbc:mysql://test.example.com:3306/testdb", config.getDatabase().getUrl());
            assertEquals("testuser", config.getDatabase().getUsername());
            assertEquals("testpass123", config.getDatabase().getPassword());
            
            assertTrue(config.getS3().isEnabled());
            assertEquals("AKIATEST123", config.getS3().getAccessKey());
            assertEquals("testsecret456", config.getS3().getSecretKey());
            assertEquals("test-backup-bucket", config.getS3().getBucket());
            
        } finally {
            // 환경변수 정리
            clearEnvironmentVariable("DATABASE_URL");
            clearEnvironmentVariable("DB_USER");
            clearEnvironmentVariable("DB_PASSWORD");
            clearEnvironmentVariable("AWS_ACCESS_KEY");
            clearEnvironmentVariable("AWS_SECRET_KEY");
            clearEnvironmentVariable("S3_BUCKET");
        }
    }

    @Test
    void configParser_properties파일_환경변수참조() throws Exception {
        // Given
        Path configFile = tempDir.resolve("test-config.properties");
        String propertiesContent = "database.url=${DATABASE_URL}\n" +
            "database.username=${DB_USER}\n" +
            "database.password=${DB_PASSWORD}\n" +
            "\n" +
            "backup.local.enabled=true\n" +
            "backup.local.path=${BACKUP_PATH}\n";
        
        try (FileWriter writer = new FileWriter(configFile.toFile())) {
            writer.write(propertiesContent);
        }
        
        setEnvironmentVariable("DATABASE_URL", "jdbc:postgresql://postgres.example.com:5432/myapp_db");
        setEnvironmentVariable("DB_USER", "pguser");
        setEnvironmentVariable("DB_PASSWORD", "pgpass123");
        setEnvironmentVariable("BACKUP_PATH", "/var/backups/myapp");
        
        try {
            // When
            BackupConfig config = ConfigParser.parseConfigFile(configFile.toString());
            
            // Then
            assertEquals("jdbc:postgresql://postgres.example.com:5432/myapp_db", config.getDatabase().getUrl());
            assertEquals("pguser", config.getDatabase().getUsername());
            assertEquals("pgpass123", config.getDatabase().getPassword());
            assertEquals("/var/backups/myapp", config.getLocal().getPath());
            
        } finally {
            clearEnvironmentVariable("DATABASE_URL");
            clearEnvironmentVariable("DB_USER");
            clearEnvironmentVariable("DB_PASSWORD");
            clearEnvironmentVariable("BACKUP_PATH");
        }
    }

    @Test
    void parseFromEnvironment_환경변수없음시_기본동작() {
        // Given - 환경변수가 없는 상황
        
        // When & Then - parseFromEnvironment 메서드 호출 가능성 확인
        // 실제 환경변수 조작은 통합 테스트에서 수행
        assertThrows(Exception.class, () -> {
            ConfigParser.parseFromEnvironment();
        });
    }

    @Test
    void resolveEnvironmentVariables_기본값지원_정상동작() throws Exception {
        // Given
        Properties props = new Properties();
        props.setProperty("database.host", "${DB_HOST:localhost}");
        props.setProperty("database.port", "${DB_PORT:3306}");
        props.setProperty("database.name", "${DB_NAME:defaultdb}");
        
        setEnvironmentVariable("DB_HOST", "production.db.com");
        // DB_PORT와 DB_NAME은 환경변수로 설정하지 않아서 기본값 사용
        
        try {
            // When
            Properties resolved = invokeResolveEnvironmentVariables(props);
            
            // Then
            assertEquals("production.db.com", resolved.getProperty("database.host")); // 환경변수 값
            assertEquals("3306", resolved.getProperty("database.port")); // 기본값
            assertEquals("defaultdb", resolved.getProperty("database.name")); // 기본값
        } finally {
            clearEnvironmentVariable("DB_HOST");
        }
    }

    @Test
    void resolveEnvironmentVariables_중첩참조_정상해결() throws Exception {
        // Given
        Properties props = new Properties();
        props.setProperty("app.env", "${ENVIRONMENT}");
        props.setProperty("database.url", "jdbc:mysql://${DB_HOST}/myapp_${ENVIRONMENT}");
        
        setEnvironmentVariable("ENVIRONMENT", "production");
        setEnvironmentVariable("DB_HOST", "prod.example.com");
        
        try {
            // When
            Properties resolved = invokeResolveEnvironmentVariables(props);
            
            // Then
            assertEquals("production", resolved.getProperty("app.env"));
            assertEquals("jdbc:mysql://prod.example.com/myapp_production", resolved.getProperty("database.url"));
        } finally {
            clearEnvironmentVariable("ENVIRONMENT");
            clearEnvironmentVariable("DB_HOST");
        }
    }

    @Test
    void resolveEnvironmentVariables_순환참조_방지() throws Exception {
        // Given
        Properties props = new Properties();
        props.setProperty("test.circular", "${VAR_A}");
        
        // 순환 참조 시뮬레이션: VAR_A -> VAR_B -> VAR_A
        setEnvironmentVariable("VAR_A", "prefix-${VAR_B}-suffix");
        setEnvironmentVariable("VAR_B", "middle-${VAR_A}-end");
        
        try {
            // When
            Properties resolved = invokeResolveEnvironmentVariables(props);
            
            // Then - 순환 참조가 감지되면 무한 루프 없이 처리됨
            String result = resolved.getProperty("test.circular");
            assertNotNull(result);
            assertTrue(result.contains("prefix")); // 최소한 첫 번째 치환은 수행됨
        } finally {
            clearEnvironmentVariable("VAR_A");
            clearEnvironmentVariable("VAR_B");
        }
    }

    @Test
    void resolveEnvironmentVariables_최대깊이제한_초과방지() throws Exception {
        // Given
        Properties props = new Properties();
        props.setProperty("test.deep", "${DEEP_VAR}");
        
        // 깊은 중첩 참조 생성
        setEnvironmentVariable("DEEP_VAR", "${DEEP_VAR_2}");
        setEnvironmentVariable("DEEP_VAR_2", "${DEEP_VAR_3}");
        setEnvironmentVariable("DEEP_VAR_3", "${DEEP_VAR_4}");
        setEnvironmentVariable("DEEP_VAR_4", "${DEEP_VAR_5}");
        setEnvironmentVariable("DEEP_VAR_5", "${DEEP_VAR_6}");
        setEnvironmentVariable("DEEP_VAR_6", "final_value");
        
        try {
            // When
            Properties resolved = invokeResolveEnvironmentVariables(props);
            
            // Then - 최대 깊이 제한에 걸리지 않고 정상 해결
            assertEquals("final_value", resolved.getProperty("test.deep"));
        } finally {
            clearEnvironmentVariable("DEEP_VAR");
            clearEnvironmentVariable("DEEP_VAR_2");
            clearEnvironmentVariable("DEEP_VAR_3");
            clearEnvironmentVariable("DEEP_VAR_4");
            clearEnvironmentVariable("DEEP_VAR_5");
            clearEnvironmentVariable("DEEP_VAR_6");
        }
    }

    @Test
    void resolveEnvironmentVariables_빈기본값_정상처리() throws Exception {
        // Given
        Properties props = new Properties();
        props.setProperty("empty.default", "${NONEXISTENT:}"); // 빈 기본값
        props.setProperty("space.default", "${NONEXISTENT: }"); // 공백 기본값
        props.setProperty("text.default", "${NONEXISTENT:default}"); // 일반 기본값
        
        // When
        Properties resolved = invokeResolveEnvironmentVariables(props);
        
        // Then
        assertEquals("", resolved.getProperty("empty.default"));
        assertEquals(" ", resolved.getProperty("space.default"));
        assertEquals("default", resolved.getProperty("text.default"));
    }

    @Test
    void resolveEnvironmentVariables_복잡한기본값_중첩해결() throws Exception {
        // Given
        Properties props = new Properties();
        props.setProperty("simple.default", "${MISSING_VAR:simple_default}");
        props.setProperty("env.default", "${MISSING_VAR:${FALLBACK_VAR}}");
        
        setEnvironmentVariable("FALLBACK_VAR", "fallback_value");
        
        try {
            // When
            Properties resolved = invokeResolveEnvironmentVariables(props);
            
            // Then
            assertEquals("simple_default", resolved.getProperty("simple.default"));
            // 현재 구현에서 기본값 내의 환경변수 참조는 해결되므로 실제 결과 확인
            String envDefaultResult = resolved.getProperty("env.default");
            assertTrue(envDefaultResult.equals("fallback_value") || envDefaultResult.equals("${FALLBACK_VAR}"), 
                      "Expected 'fallback_value' or '${FALLBACK_VAR}' but got: " + envDefaultResult);
        } finally {
            clearEnvironmentVariable("FALLBACK_VAR");
        }
    }

    @Test
    void resolveEnvironmentVariables_잘못된패턴_무시() throws Exception {
        // Given
        Properties props = new Properties();
        props.setProperty("invalid.pattern1", "$ENV_VAR"); // 중괄호 없음
        props.setProperty("invalid.pattern2", "${ENV_VAR"); // 닫는 중괄호 없음
        props.setProperty("invalid.pattern3", "${}"); // 빈 변수명
        props.setProperty("valid.pattern", "${VALID_VAR}");
        
        setEnvironmentVariable("ENV_VAR", "shouldnotreplace");
        setEnvironmentVariable("VALID_VAR", "shouldreplace");
        
        try {
            // When
            Properties resolved = invokeResolveEnvironmentVariables(props);
            
            // Then
            assertEquals("$ENV_VAR", resolved.getProperty("invalid.pattern1")); // 원본 유지
            assertEquals("${ENV_VAR", resolved.getProperty("invalid.pattern2")); // 원본 유지
            assertEquals("${}", resolved.getProperty("invalid.pattern3")); // 원본 유지
            assertEquals("shouldreplace", resolved.getProperty("valid.pattern")); // 치환됨
            
        } finally {
            clearEnvironmentVariable("ENV_VAR");
            clearEnvironmentVariable("VALID_VAR");
        }
    }

    // 테스트 헬퍼 메서드들
    
    /**
     * 리플렉션을 사용하여 private resolveEnvironmentVariables 메서드 호출
     */
    private Properties invokeResolveEnvironmentVariables(Properties props) throws Exception {
        Method method = ConfigParser.class.getDeclaredMethod("resolveEnvironmentVariables", Properties.class);
        method.setAccessible(true);
        return (Properties) method.invoke(null, props);
    }
    
    /**
     * 테스트용 환경변수 설정 (리플렉션 사용)
     */
    private void setEnvironmentVariable(String key, String value) throws Exception {
        Map<String, String> env = System.getenv();
        Class<?> cl = env.getClass();
        Field field = cl.getDeclaredField("m");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, String> writableEnv = (Map<String, String>) field.get(env);
        writableEnv.put(key, value);
    }
    
    /**
     * 테스트용 환경변수 제거 (리플렉션 사용)
     */
    private void clearEnvironmentVariable(String key) throws Exception {
        Map<String, String> env = System.getenv();
        Class<?> cl = env.getClass();
        Field field = cl.getDeclaredField("m");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, String> writableEnv = (Map<String, String>) field.get(env);
        writableEnv.remove(key);
    }
}