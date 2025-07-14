package io.backup4j.core.config;

import io.backup4j.core.database.DatabaseType;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.io.File;

public class ConfigParser {

    private ConfigParser() {
    }

    private static final String ENV_KEY_PREFIX = "DB_BACKUP4J_";

    public static BackupConfig parsePropertiesFromFile(String filePath) throws IOException {
        Properties props = new Properties();
        try (InputStream is = Files.newInputStream(Paths.get(filePath))) {
            props.load(is);
            return mapPropertiesToConfig(props);
        }
    }
    
    public static BackupConfig parseYamlFromFile(String filePath) throws IOException {
        try (InputStream is = Files.newInputStream(Paths.get(filePath))) {
            Properties props = parseYamlToProperties(is);
            return mapPropertiesToConfig(props);
        }
    }
    
    public static BackupConfig autoDetectAndParse() throws IOException {
        return autoDetectAndParse(null);
    }
    
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
    
    public static BackupConfig autoDetectAndParse(String configLocation) throws IOException {
        // 1. 환경변수 확인 (최우선)
        if (hasEnvironmentVariables()) {
            return parseFromEnvironment();
        }
        
        // 2. 프로젝트 루트에서 설정 파일 확인
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
        
        // 3. configLocation 파라미터 확인
        if (configLocation != null && !configLocation.trim().isEmpty()) {
            File configFile = new File(configLocation);
            if (configFile.exists() && configFile.isFile()) {
                return parseConfigFile(configLocation);
            }
        }
        
        // 4. 모든 경로에서 파일을 찾지 못한 경우
        throw new IOException(
            "Configuration not found. Searched: environment variables, file paths: "
                + String.join(", ", defaultPaths)
                + (configLocation != null ? ", " + configLocation : "")
        );
    }
    
    private static boolean hasEnvironmentVariables() {
        // DB_BACKUP4J_로 시작하는 환경변수가 하나라도 있는지 확인
        return System.getenv().keySet().stream()
            .anyMatch(key -> key.startsWith(ENV_KEY_PREFIX));
    }
    
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

    private static BackupConfig mapPropertiesToConfig(Properties props) {
        return BackupConfig.builder()
            .database(mapDatabaseConfig(props))
            .local(mapLocalBackupConfig(props))
            .email(mapEmailConfig(props))
            .s3(mapS3Config(props))
            .schedule(mapScheduleConfig(props))
            .build();
    }
    
    // 테스트 목적으로만 사용 - 환경변수와 파일 설정 병합 (레거시)
    static Properties mergeWithEnvironmentVariables(Properties fileProps) {
        Properties mergedProps = new Properties();
        
        // 1. 파일에서 읽은 설정을 먼저 추가
        mergedProps.putAll(fileProps);
        
        // 2. 환경변수에서 DB_BACKUP4J_로 시작하는 값들을 덮어쓰기
        System.getenv().forEach((key, value) -> {
            if (key.startsWith(ENV_KEY_PREFIX)) {
                // DB_BACKUP4J_DATABASE_TYPE -> database.type
                String configKey = key.substring(ENV_KEY_PREFIX.length())
                    .toLowerCase()
                    .replace("_", ".");
                mergedProps.setProperty(configKey, value);
            }
        });
        
        return mergedProps;
    }

    private static DatabaseConfig mapDatabaseConfig(Properties props) {
        DatabaseConfig.Builder builder = DatabaseConfig.builder();
        
        String typeStr = props.getProperty("database.type");
        if (typeStr != null) {
            try {
                DatabaseType type = DatabaseType.fromString(typeStr);
                builder.type(type);
            } catch (IllegalArgumentException e) {
                // 잘못된 타입은 null로 설정하고 나중에 validator에서 검증
                builder.type(null);
            }
        }
        
        builder.host(props.getProperty("database.host", ConfigDefaults.DEFAULT_DATABASE_HOST));
        
        String portStr = props.getProperty("database.port", String.valueOf(ConfigDefaults.DEFAULT_MYSQL_PORT));
        try {
            builder.port(Integer.parseInt(portStr));
        } catch (NumberFormatException e) {
            builder.port(0); // 잘못된 포트는 0으로 설정
        }
        
        builder.name(props.getProperty("database.name"));
        builder.username(props.getProperty("database.username"));
        builder.password(props.getProperty("database.password"));
        
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

    private static EmailBackupConfig mapEmailConfig(Properties props) {
        EmailBackupConfig.SmtpConfig.Builder smtpBuilder = EmailBackupConfig.SmtpConfig.builder()
            .host(props.getProperty("backup.email.smtp.host"));
        
        String smtpPortStr = props.getProperty("backup.email.smtp.port", String.valueOf(ConfigDefaults.DEFAULT_SMTP_PORT));
        try {
            smtpBuilder.port(Integer.parseInt(smtpPortStr));
        } catch (NumberFormatException e) {
            smtpBuilder.port(0); // 잘못된 포트는 0으로 설정
        }
        
        EmailBackupConfig.Builder builder = EmailBackupConfig.builder()
            .enabled(Boolean.parseBoolean(props.getProperty("backup.email.enabled", String.valueOf(ConfigDefaults.DEFAULT_EMAIL_BACKUP_ENABLED))))
            .username(props.getProperty("backup.email.username"))
            .password(props.getProperty("backup.email.password"))
            .smtp(smtpBuilder.build());
        
        String recipients = props.getProperty("backup.email.recipients");
        if (recipients != null) {
            List<String> recipientList = Arrays.asList(recipients.split(","));
            builder.recipients(recipientList);
        }
        
        return builder.build();
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
    
    private static Properties parseYamlToProperties(InputStream inputStream) throws IOException {
        Properties properties = new Properties();
        
        try {
            Yaml yaml = new Yaml();
            @SuppressWarnings("unchecked")
            Map<String, Object> data = yaml.load(inputStream);
            
            if (data != null) {
                flattenMap(data, "", properties);
            }
            
        } catch (Exception e) {
            throw new IOException("Failed to parse YAML: " + e.getMessage(), e);
        }
        
        return properties;
    }
    
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