package io.backup4j.core.config;

import io.backup4j.core.database.DatabaseType;
import io.backup4j.core.notification.WebhookChannel;
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
            .notification(mapNotificationConfig(props))
            .s3(mapS3Config(props))
            .schedule(mapScheduleConfig(props))
            .build();
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
            .enableChecksum(Boolean.parseBoolean(props.getProperty("backup.local.checksum.enabled", String.valueOf(ConfigDefaults.DEFAULT_LOCAL_BACKUP_ENABLE_CHECKSUM))))
            .checksumAlgorithm(props.getProperty("backup.local.checksum.algorithm", ConfigDefaults.DEFAULT_LOCAL_BACKUP_CHECKSUM_ALGORITHM))
            .build();
    }

    private static NotificationConfig mapNotificationConfig(Properties props) {
        boolean enabled = Boolean.parseBoolean(props.getProperty("backup.notification.enabled", String.valueOf(ConfigDefaults.DEFAULT_NOTIFICATION_ENABLED)));
        
        // 이메일 설정
        NotificationConfig.EmailConfig.Builder emailBuilder = NotificationConfig.EmailConfig.builder()
            .enabled(Boolean.parseBoolean(props.getProperty("backup.notification.email.enabled", String.valueOf(ConfigDefaults.DEFAULT_EMAIL_ENABLED))))
            .username(props.getProperty("backup.notification.email.username"))
            .password(props.getProperty("backup.notification.email.password"))
            .subject(props.getProperty("backup.notification.email.subject", ConfigDefaults.DEFAULT_EMAIL_SUBJECT));
        
        String recipients = props.getProperty("backup.notification.email.recipients");
        if (recipients != null) {
            List<String> recipientList = Arrays.asList(recipients.split(","));
            emailBuilder.recipients(recipientList);
        }
        
        // SMTP 설정
        NotificationConfig.SmtpConfig.Builder smtpBuilder = NotificationConfig.SmtpConfig.builder()
            .host(props.getProperty("backup.notification.email.smtp.host"))
            .useTls(Boolean.parseBoolean(props.getProperty("backup.notification.email.smtp.tls", String.valueOf(ConfigDefaults.DEFAULT_SMTP_TLS))))
            .useAuth(Boolean.parseBoolean(props.getProperty("backup.notification.email.smtp.auth", String.valueOf(ConfigDefaults.DEFAULT_SMTP_AUTH))));
        
        String smtpPortStr = props.getProperty("backup.notification.email.smtp.port", String.valueOf(ConfigDefaults.DEFAULT_SMTP_PORT));
        try {
            smtpBuilder.port(Integer.parseInt(smtpPortStr));
        } catch (NumberFormatException e) {
            smtpBuilder.port(0); // 잘못된 포트는 0으로 설정
        }
        
        emailBuilder.smtp(smtpBuilder.build());
        
        // 웹훅 설정
        NotificationConfig.WebhookConfig.Builder webhookBuilder = NotificationConfig.WebhookConfig.builder()
            .enabled(Boolean.parseBoolean(props.getProperty("backup.notification.webhook.enabled", String.valueOf(ConfigDefaults.DEFAULT_WEBHOOK_ENABLED))))
            .url(props.getProperty("backup.notification.webhook.url"))
            .useRichFormat(Boolean.parseBoolean(props.getProperty("backup.notification.webhook.rich-format", String.valueOf(ConfigDefaults.DEFAULT_WEBHOOK_RICH_FORMAT))))
            .timeout(Integer.parseInt(props.getProperty("backup.notification.webhook.timeout", String.valueOf(ConfigDefaults.DEFAULT_WEBHOOK_TIMEOUT))))
            .retryCount(Integer.parseInt(props.getProperty("backup.notification.webhook.retry-count", String.valueOf(ConfigDefaults.DEFAULT_WEBHOOK_RETRY_COUNT))));
        
        // 웹훅 채널 설정
        String channelStr = props.getProperty("backup.notification.webhook.channel");
        if (channelStr != null && !channelStr.trim().isEmpty()) {
            try {
                webhookBuilder.channel(WebhookChannel.valueOf(channelStr.toUpperCase()));
            } catch (IllegalArgumentException e) {
                // 잘못된 채널은 null로 설정 (자동 감지)
                webhookBuilder.channel(null);
            }
        }
        
        return NotificationConfig.builder()
            .enabled(enabled)
            .email(emailBuilder.build())
            .webhook(webhookBuilder.build())
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
            .enableChecksum(Boolean.parseBoolean(props.getProperty("backup.s3.checksum.enabled", String.valueOf(ConfigDefaults.DEFAULT_S3_BACKUP_ENABLE_CHECKSUM))))
            .checksumAlgorithm(props.getProperty("backup.s3.checksum.algorithm", ConfigDefaults.DEFAULT_S3_BACKUP_CHECKSUM_ALGORITHM))
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