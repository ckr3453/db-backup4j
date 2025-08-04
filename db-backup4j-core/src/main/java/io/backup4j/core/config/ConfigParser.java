package io.backup4j.core.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Properties;
import java.util.List;
import java.io.File;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.Set;
import java.util.HashSet;
import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.Collections;

import org.yaml.snakeyaml.Yaml;

/**
 * 백업 설정 파일을 파싱하는 클래스입니다.
 * Properties 파일, YAML 파일, 환경 변수로부터 설정을 읽어와서 BackupConfig 객체로 변환합니다.
 */
public class ConfigParser {

    private ConfigParser() {
    }

    // 환경 변수 접두사
    public static final String ENV_KEY_PREFIX = "DB_BACKUP4J_";
    
    // 환경 변수 참조 패턴 (${VAR} 또는 ${VAR:default})
    private static final Pattern ENV_VAR_PATTERN = Pattern.compile("\\$\\{([^}:]+)(?::([^}]*))?}");
    
    // 최대 치환 깊이 (순환 참조 방지)
    private static final int MAX_SUBSTITUTION_DEPTH = 10;

    // 기본 설정 파일명
    public static final String DEFAULT_PROPERTIES_FILE = "db-backup4j.properties";
    public static final String DEFAULT_YAML_FILE = "db-backup4j.yaml";
    public static final String DEFAULT_YML_FILE = "db-backup4j.yml";

    // 데이터베이스 설정 키
    public static final String DATABASE_URL = "database.url";
    public static final String DATABASE_USERNAME = "database.username";
    public static final String DATABASE_PASSWORD = "database.password";
    public static final String DATABASE_EXCLUDE_SYSTEM_TABLES = "database.exclude-system-tables";
    public static final String DATABASE_EXCLUDE_TABLE_PATTERNS = "database.exclude-table-patterns";
    public static final String DATABASE_INCLUDE_TABLE_PATTERNS = "database.include-table-patterns";

    // 로컬 백업 설정 키
    public static final String BACKUP_LOCAL_ENABLED = "backup.local.enabled";
    public static final String BACKUP_LOCAL_PATH = "backup.local.path";
    public static final String BACKUP_LOCAL_RETENTION = "backup.local.retention";
    public static final String BACKUP_LOCAL_COMPRESS = "backup.local.compress";

    // S3 백업 설정 키
    public static final String BACKUP_S3_ENABLED = "backup.s3.enabled";
    public static final String BACKUP_S3_BUCKET = "backup.s3.bucket";
    public static final String BACKUP_S3_PREFIX = "backup.s3.prefix";
    public static final String BACKUP_S3_REGION = "backup.s3.region";
    public static final String BACKUP_S3_ACCESS_KEY = "backup.s3.access-key";
    public static final String BACKUP_S3_SECRET_KEY = "backup.s3.secret-key";

    // 스케줄 설정 키
    public static final String SCHEDULE_ENABLED = "schedule.enabled";
    public static final String SCHEDULE_CRON = "schedule.cron";

    // 파일 확장자
    public static final String PROPERTIES_EXTENSION = ".properties";
    public static final String YAML_EXTENSION = ".yaml";
    public static final String YML_EXTENSION = ".yml";

    private static final String[] classpathPaths = {
        DEFAULT_PROPERTIES_FILE,
        DEFAULT_YAML_FILE,
        DEFAULT_YML_FILE
    };

    private static final String[] defaultPaths = {
        "./" + DEFAULT_PROPERTIES_FILE,
        "./" + DEFAULT_YAML_FILE,
        "./" + DEFAULT_YML_FILE
    };

    /**
     * Properties 파일에서 설정을 읽어와서 BackupConfig 객체로 변환합니다.
     * 
     * @param filePath Properties 파일 경로
     * @return 파싱된 백업 설정 객체
     * @throws IOException 파일 읽기 실패 시
     */
    public static BackupConfig parsePropertiesFromFile(String filePath) throws IOException {
        Properties props = new Properties();
        try (InputStream is = Files.newInputStream(Paths.get(filePath))) {
            props.load(is);
            return mapPropertiesToConfig(props);
        }
    }
    
    /**
     * YAML 파일에서 설정을 읽어와서 BackupConfig 객체로 변환합니다.
     * 
     * @param filePath YAML 파일 경로
     * @return 파싱된 백업 설정 객체
     * @throws IOException 파일 읽기 실패 시
     */
    public static BackupConfig parseYamlFromFile(String filePath) throws IOException {
        try (InputStream is = Files.newInputStream(Paths.get(filePath))) {
            Properties props = parseYamlToProperties(is);
            return mapPropertiesToConfig(props);
        }
    }
    
    public static BackupConfig autoDetectAndParse() throws IOException {
        return autoDetectAndParse(null);
    }
    
    /**
     * 환경 변수에서 설정을 읽어와서 BackupConfig 객체로 변환합니다.
     * DB_BACKUP4J_로 시작하는 환경 변수를 찾아서 변환합니다.
     * 
     * @return 파싱된 백업 설정 객체
     */
    public static BackupConfig parseFromEnvironment() {
        Properties props = new Properties();
        
        // 환경변수에서 DB_BACKUP4J_로 시작하는 모든 값을 찾아서 Properties로 변환
        System.getenv().forEach((key, value) -> {
            if (key.startsWith(ENV_KEY_PREFIX)) {
                // DB_BACKUP4J_DATABASE_URL -> database.url
                // DB_BACKUP4J_BACKUP_LOCAL_PATH -> backup.local.path
                String configKey = key.substring(ENV_KEY_PREFIX.length())
                    .toLowerCase()
                    .replace("_", ".");
                props.setProperty(configKey, value);
            }
        });
        
        return mapPropertiesToConfig(props);
    }
    
    /**
     * Properties 객체에서 환경 변수 참조(${ENV_VAR} 또는 ${ENV_VAR:default} 형식)를 실제 환경 변수 값으로 치환합니다.
     * 순환 참조를 방지하고 기본값을 지원합니다.
     * 
     * @param props 원본 Properties 객체
     * @return 환경 변수가 치환된 새로운 Properties 객체
     */
    private static Properties resolveEnvironmentVariables(Properties props) {
        Properties resolvedProps = new Properties();
        
        for (String key : props.stringPropertyNames()) {
            String value = props.getProperty(key);
            if (value != null) {
                String resolvedValue = resolveEnvironmentVariablesInValue(value, new HashSet<>(), 0);
                resolvedProps.setProperty(key, resolvedValue);
            }
        }
        
        return resolvedProps;
    }
    
    /**
     * 단일 값에서 환경 변수 참조를 재귀적으로 해결합니다.
     * 
     * @param value 해결할 값
     * @param resolving 현재 해결 중인 환경 변수 세트 (순환 참조 방지)
     * @param depth 현재 치환 깊이
     * @return 환경 변수가 치환된 값
     */
    private static String resolveEnvironmentVariablesInValue(String value, Set<String> resolving, int depth) {
        if (value == null || depth >= MAX_SUBSTITUTION_DEPTH) {
            return value;
        }
        
        Matcher matcher = ENV_VAR_PATTERN.matcher(value);
        StringBuffer result = new StringBuffer();
        
        while (matcher.find()) {
            String envVarName = matcher.group(1);
            String defaultValue = matcher.group(2); // null이면 기본값 없음
            
            // 순환 참조 검사
            if (resolving.contains(envVarName)) {
                // 순환 참조 발견 시 원본 참조 유지
                matcher.appendReplacement(result, Matcher.quoteReplacement(matcher.group(0)));
                continue;
            }
            
            String envVarValue = System.getenv(envVarName);
            String replacementValue;
            
            if (envVarValue != null) {
                // 환경 변수가 존재하는 경우, 재귀적으로 해결
                Set<String> newResolving = new HashSet<>(resolving);
                newResolving.add(envVarName);
                replacementValue = resolveEnvironmentVariablesInValue(envVarValue, newResolving, depth + 1);
            } else if (defaultValue != null) {
                // 환경 변수가 없고 기본값이 있는 경우
                Set<String> newResolving = new HashSet<>(resolving);
                newResolving.add(envVarName);
                replacementValue = resolveEnvironmentVariablesInValue(defaultValue, newResolving, depth + 1);
            } else {
                // 환경 변수도 없고 기본값도 없는 경우 원본 유지
                replacementValue = matcher.group(0);
            }
            
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacementValue));
        }
        
        matcher.appendTail(result);
        return result.toString();
    }
    
    /**
     * 설정 파일을 자동으로 감지하고 파싱합니다.
     * 환경 변수 > 기본 설정 파일 > 지정된 설정 파일 순으로 확인합니다.
     * 
     * @param configLocation 추가로 확인할 설정 파일 경로 (선택사항)
     * @return 파싱된 백업 설정 객체
     * @throws IOException 설정 파일을 찾지 못하거나 파싱 실패 시
     */
    public static BackupConfig autoDetectAndParse(String configLocation) throws IOException {
        // 1. 환경변수 확인 (최우선)
        if (hasEnvironmentVariables()) {
            return parseFromEnvironment();
        }
        
        // 2. 클래스패스에서 설정 파일 확인
        BackupConfig classpathConfig = tryParseFromClasspath();
        if (classpathConfig != null) {
            return classpathConfig;
        }
        
        // 3. 프로젝트 루트에서 설정 파일 확인
        BackupConfig fileSystemConfig = tryParseFromFileSystem();
        if (fileSystemConfig != null) {
            return fileSystemConfig;
        }
        
        // 4. configLocation 파라미터 확인
        if (configLocation != null && !configLocation.trim().isEmpty()) {
            File configFile = new File(configLocation);
            if (configFile.exists() && configFile.isFile()) {
                return parseConfigFile(configLocation);
            }
        }
        
        // 5. 모든 경로에서 파일을 찾지 못한 경우
        throw new IOException(
            "Configuration not found. Searched: environment variables, classpath paths: "
                + String.join(", ", classpathPaths)
                + ", file paths: " + String.join(", ", defaultPaths)
                + (configLocation != null ? ", " + configLocation : "")
        );
    }
    
    /**
     * 클래스패스에서 설정 파일을 찾아 파싱을 시도합니다.
     * 
     * @return 파싱된 설정 객체, 찾지 못하면 null
     */
    private static BackupConfig tryParseFromClasspath() {
        for (String resourcePath : classpathPaths) {
            try (InputStream is = ConfigParser.class.getClassLoader().getResourceAsStream(resourcePath)) {
                if (is != null) {
                    return parseFromInputStream(is, resourcePath);
                }
            } catch (IOException e) {
                // 클래스패스에서 읽기 실패해도 계속 진행
            }
        }
        return null;
    }
    
    /**
     * 파일 시스템에서 설정 파일을 찾아 파싱을 시도합니다.
     * 
     * @return 파싱된 설정 객체, 찾지 못하면 null
     */
    private static BackupConfig tryParseFromFileSystem() {
        for (String path : defaultPaths) {
            File file = new File(path);
            if (file.exists() && file.isFile()) {
                try {
                    return parseConfigFile(path);
                } catch (IOException e) {
                    // 파일 읽기 실패해도 계속 진행
                }
            }
        }
        return null;
    }
    
    /**
     * InputStream에서 설정을 파싱합니다.
     * 
     * @param inputStream 입력 스트림
     * @param resourcePath 리소스 경로 (확장자 판단용)
     * @return 파싱된 설정 객체
     * @throws IOException 파싱 실패 시
     */
    private static BackupConfig parseFromInputStream(InputStream inputStream, String resourcePath) throws IOException {
        Properties props;
        if (resourcePath.endsWith(PROPERTIES_EXTENSION)) {
            props = new Properties();
            props.load(inputStream);
        } else {
            props = parseYamlToProperties(inputStream);
        }
        return mapPropertiesToConfig(props);
    }
    
    private static boolean hasEnvironmentVariables() {
        // DB_BACKUP4J_로 시작하는 환경변수가 하나라도 있는지 확인
        return System.getenv()
            .keySet()
            .stream()
            .anyMatch(key -> key.startsWith(ENV_KEY_PREFIX));
    }
    
    /**
     * 파일 확장자에 따라 적절한 파싱 방법을 선택하여 설정 파일을 파싱합니다.
     * 
     * @param filePath 설정 파일 경로
     * @return 파싱된 백업 설정 객체
     * @throws IOException 지원하지 않는 파일 형식이거나 파싱 실패 시
     */
    public static BackupConfig parseConfigFile(String filePath) throws IOException {
        String lowerPath = filePath.toLowerCase();
        
        if (lowerPath.endsWith(PROPERTIES_EXTENSION)) {
            return parsePropertiesFromFile(filePath);
        } else if (lowerPath.endsWith(YAML_EXTENSION) || lowerPath.endsWith(YML_EXTENSION)) {
            return parseYamlFromFile(filePath);
        } else {
            throw new IOException(
                "Unsupported file format: "
                + filePath
                + ". Supported formats: " + PROPERTIES_EXTENSION + ", " + YAML_EXTENSION + ", " + YML_EXTENSION
            );
        }
    }

    /**
     * Properties 객체를 BackupConfig 객체로 변환합니다.
     * 
     * @param props Properties 객체
     * @return 변환된 백업 설정 객체
     */
    private static BackupConfig mapPropertiesToConfig(Properties props) {
        // 환경 변수 참조 해결
        Properties resolvedProps = resolveEnvironmentVariables(props);
        
        return BackupConfig.builder()
            .database(mapDatabaseConfig(resolvedProps))
            .local(mapLocalBackupConfig(resolvedProps))
            .s3(mapS3Config(resolvedProps))
            .schedule(mapScheduleConfig(resolvedProps))
            .build();
    }
    

    private static DatabaseConfig mapDatabaseConfig(Properties props) {
        DatabaseConfig.Builder builder = DatabaseConfig.builder();
        
        // JDBC URL 방식으로 변경
        String url = props.getProperty(DATABASE_URL);
        String username = props.getProperty(DATABASE_USERNAME);
        String password = props.getProperty(DATABASE_PASSWORD);
        
        builder.url(url);
        builder.username(username);
        builder.password(password);
        
        // 테이블 필터링 설정
        boolean excludeSystemTables = Boolean.parseBoolean(
            props.getProperty(DATABASE_EXCLUDE_SYSTEM_TABLES, String.valueOf(DatabaseConfig.DEFAULT_EXCLUDE_SYSTEM_TABLES))
        );
        builder.excludeSystemTables(excludeSystemTables);
        
        // 제외 테이블 패턴 처리
        String excludePatternsStr = props.getProperty(DATABASE_EXCLUDE_TABLE_PATTERNS);
        if (excludePatternsStr != null && !excludePatternsStr.trim().isEmpty()) {
            List<String> excludePatterns = parseCommaSeparatedList(excludePatternsStr);
            builder.excludeTablePatterns(excludePatterns);
        }
        
        // 포함 테이블 패턴 처리
        String includePatternsStr = props.getProperty(DATABASE_INCLUDE_TABLE_PATTERNS);
        if (includePatternsStr != null && !includePatternsStr.trim().isEmpty()) {
            List<String> includePatterns = parseCommaSeparatedList(includePatternsStr);
            builder.includeTablePatterns(includePatterns);
        }
        
        return builder.build();
    }
    
    /**
     * 쉼표로 구분된 문자열을 List로 변환합니다.
     * 
     * @param commaSeparatedStr 쉼표로 구분된 문자열
     * @return 문자열 리스트
     */
    private static List<String> parseCommaSeparatedList(String commaSeparatedStr) {
        if (commaSeparatedStr == null || commaSeparatedStr.trim().isEmpty()) {
            return Collections.emptyList();
        }
        
        return Arrays.stream(commaSeparatedStr.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .collect(Collectors.toList());
    }

    private static LocalBackupConfig mapLocalBackupConfig(Properties props) {
        return LocalBackupConfig.builder()
            .enabled(Boolean.parseBoolean(props.getProperty(BACKUP_LOCAL_ENABLED, String.valueOf(LocalBackupConfig.DEFAULT_LOCAL_BACKUP_ENABLED))))
            .path(props.getProperty(BACKUP_LOCAL_PATH, LocalBackupConfig.DEFAULT_LOCAL_BACKUP_PATH))
            .retention(props.getProperty(BACKUP_LOCAL_RETENTION, LocalBackupConfig.DEFAULT_LOCAL_BACKUP_RETENTION_DAYS))
            .compress(Boolean.parseBoolean(props.getProperty(BACKUP_LOCAL_COMPRESS, String.valueOf(LocalBackupConfig.DEFAULT_LOCAL_BACKUP_COMPRESS))))
            .build();
    }


    private static S3BackupConfig mapS3Config(Properties props) {
        return S3BackupConfig.builder()
            .enabled(Boolean.parseBoolean(props.getProperty(BACKUP_S3_ENABLED, String.valueOf(S3BackupConfig.DEFAULT_S3_BACKUP_ENABLED))))
            .bucket(props.getProperty(BACKUP_S3_BUCKET))
            .prefix(props.getProperty(BACKUP_S3_PREFIX, S3BackupConfig.DEFAULT_S3_PREFIX))
            .region(props.getProperty(BACKUP_S3_REGION, S3BackupConfig.DEFAULT_S3_REGION))
            .accessKey(props.getProperty(BACKUP_S3_ACCESS_KEY))
            .secretKey(props.getProperty(BACKUP_S3_SECRET_KEY))
            .build();
    }

    private static ScheduleConfig mapScheduleConfig(Properties props) {
        return ScheduleConfig.builder()
            .enabled(Boolean.parseBoolean(props.getProperty(SCHEDULE_ENABLED, String.valueOf(ScheduleConfig.DEFAULT_SCHEDULE_ENABLED))))
            .cron(props.getProperty(SCHEDULE_CRON))
            .build();
    }
    
    /**
     * YAML 파일 내용을 Properties 객체로 변환합니다.
     * 
     * @param inputStream YAML 파일 입력 스트림
     * @return 변환된 Properties 객체
     */
    private static Properties parseYamlToProperties(InputStream inputStream) {
        Yaml yaml = new Yaml();
        Map<String, Object> data = yaml.load(inputStream);

        Properties properties = new Properties();
        if (data != null) {
            flattenMap(data, "", properties);
        }
        
        return properties;
    }
    
    /**
     * 중첩된 Map 구조를 평탄화하여 Properties로 변환합니다.
     * 
     * @param map 평탄화할 Map 객체
     * @param prefix 키의 접두사
     * @param properties 결과를 저장할 Properties 객체
     */
    @SuppressWarnings("unchecked")
    private static void flattenMap(Map<String, Object> map, String prefix, Properties properties) {
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String key = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
            Object value = entry.getValue();
            
            if (value instanceof Map) {
                flattenMap((Map<String, Object>) value, key, properties);
            } else if (value instanceof List) {
                // 리스트를 쉼표로 구분된 문자열로 변환
                List<?> list = (List<?>) value;
                StringBuilder listValue = new StringBuilder();
                for (int i = 0; i < list.size(); i++) {
                    if (i > 0) listValue.append(",");
                    listValue.append(list.get(i).toString());
                }
                properties.setProperty(key, listValue.toString());
            } else if (value != null) {
                properties.setProperty(key, value.toString());
            }
        }
    }
}