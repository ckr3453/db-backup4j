package io.backup4j.core.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Properties;
import java.io.File;

/**
 * 백업 설정 파일을 파싱하는 클래스입니다.
 * Properties 파일, YAML 파일, 환경 변수로부터 설정을 읽어와서 BackupConfig 객체로 변환합니다.
 */
public class ConfigParser {

    private ConfigParser() {
    }

    private static final String ENV_KEY_PREFIX = "DB_BACKUP4J_";

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
                // DB_BACKUP4J_DATABASE_TYPE -> database.type
                String configKey = key.substring(ENV_KEY_PREFIX.length())
                    .toLowerCase()
                    .replace("_", ".");
                props.setProperty(configKey, value);
            }
        });
        
        return mapPropertiesToConfig(props);
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
        
        // 2. 클래스패스에서 설정 파일 확인 (Spring Boot 스타일)
        String[] classpathPaths = {
            "db-backup4j.properties",
            "db-backup4j.yaml", 
            "db-backup4j.yml"
        };
        
        for (String resourcePath : classpathPaths) {
            try (InputStream is = ConfigParser.class.getClassLoader().getResourceAsStream(resourcePath)) {
                if (is != null) {
                    if (resourcePath.endsWith(".properties")) {
                        Properties props = new Properties();
                        props.load(is);
                        return mapPropertiesToConfig(props);
                    } else {
                        Properties props = parseYamlToProperties(is);
                        return mapPropertiesToConfig(props);
                    }
                }
            } catch (IOException e) {
                // 클래스패스에서 읽기 실패해도 계속 진행
            }
        }
        
        // 3. 프로젝트 루트에서 설정 파일 확인
        String[] defaultPaths = {
            "./db-backup4j.properties",
            "./db-backup4j.yaml",
            "./db-backup4j.yml"
        };
        
        for (String path : defaultPaths) {
            File file = new File(path);
            if (file.exists() && file.isFile()) {
                return parseConfigFile(path);
            }
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
    
    private static boolean hasEnvironmentVariables() {
        // DB_BACKUP4J_로 시작하는 환경변수가 하나라도 있는지 확인
        return System.getenv().keySet().stream()
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
        
        if (lowerPath.endsWith(".properties")) {
            return parsePropertiesFromFile(filePath);
        } else if (lowerPath.endsWith(".yaml") || lowerPath.endsWith(".yml")) {
            return parseYamlFromFile(filePath);
        } else {
            throw new IOException(
                "Unsupported file format: "
                + filePath
                + ". Supported formats: .properties, .yaml, .yml"
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
        return BackupConfig.builder()
            .database(mapDatabaseConfig(props))
            .local(mapLocalBackupConfig(props))
            .s3(mapS3Config(props))
            .schedule(mapScheduleConfig(props))
            .build();
    }
    

    private static DatabaseConfig mapDatabaseConfig(Properties props) {
        DatabaseConfig.Builder builder = DatabaseConfig.builder();
        
        // JDBC URL 방식으로 변경
        String url = props.getProperty("database.url");
        String username = props.getProperty("database.username");
        String password = props.getProperty("database.password");
        
        builder.url(url);
        builder.username(username);
        builder.password(password);
        
        return builder.build();
    }

    private static LocalBackupConfig mapLocalBackupConfig(Properties props) {
        return LocalBackupConfig.builder()
            .enabled(Boolean.parseBoolean(props.getProperty("backup.local.enabled", String.valueOf(ConfigDefaults.DEFAULT_LOCAL_BACKUP_ENABLED))))
            .path(props.getProperty("backup.local.path", ConfigDefaults.DEFAULT_LOCAL_BACKUP_PATH))
            .retention(props.getProperty("backup.local.retention", ConfigDefaults.DEFAULT_LOCAL_BACKUP_RETENTION_DAYS))
            .compress(Boolean.parseBoolean(props.getProperty("backup.local.compress", String.valueOf(ConfigDefaults.DEFAULT_LOCAL_BACKUP_COMPRESS))))
            .build();
    }


    private static S3BackupConfig mapS3Config(Properties props) {
        return S3BackupConfig.builder()
            .enabled(Boolean.parseBoolean(props.getProperty("backup.s3.enabled", String.valueOf(ConfigDefaults.DEFAULT_S3_BACKUP_ENABLED))))
            .bucket(props.getProperty("backup.s3.bucket"))
            .prefix(props.getProperty("backup.s3.prefix", ConfigDefaults.DEFAULT_S3_PREFIX))
            .region(props.getProperty("backup.s3.region", ConfigDefaults.DEFAULT_S3_REGION))
            .accessKey(props.getProperty("backup.s3.access-key"))
            .secretKey(props.getProperty("backup.s3.secret-key"))
            .build();
    }

    private static ScheduleConfig mapScheduleConfig(Properties props) {
        return ScheduleConfig.builder()
            .enabled(Boolean.parseBoolean(props.getProperty("schedule.enabled", String.valueOf(ConfigDefaults.DEFAULT_SCHEDULE_ENABLED))))
            .cron(props.getProperty("schedule.cron"))
            .build();
    }
    
    /**
     * YAML 파일 내용을 Properties 객체로 변환합니다.
     * 
     * @param inputStream YAML 파일 입력 스트림
     * @return 변환된 Properties 객체
     * @throws IOException YAML 파싱 실패 시
     */
    private static Properties parseYamlToProperties(InputStream inputStream) throws IOException {
        Properties properties = new Properties();
        
        try {
            Map<String, Object> data = SimpleYamlParser.parse(inputStream);
            
            if (data != null) {
                flattenMap(data, "", properties);
            }
            
        } catch (Exception e) {
            throw new IOException("Failed to parse YAML: " + e.getMessage(), e);
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
            } else if (value != null) {
                properties.setProperty(key, value.toString());
            }
        }
    }
}