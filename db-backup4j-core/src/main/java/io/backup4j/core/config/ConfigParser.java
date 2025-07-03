package io.backup4j.core.config;

import io.backup4j.core.database.DatabaseType;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.io.File;

public class ConfigParser {

    public static BackupConfig parseProperties(String resourcePath) throws IOException {
        try (InputStream is = ConfigParser.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IOException("Configuration file not found: " + resourcePath);
            }
            
            Properties props = new Properties();
            props.load(is);
            
            return mapPropertiesToConfig(props);
        }
    }

    public static BackupConfig parsePropertiesFromFile(String filePath) throws IOException {
        Properties props = new Properties();
        try (InputStream is = Files.newInputStream(Paths.get(filePath))) {
            props.load(is);
            return mapPropertiesToConfig(props);
        }
    }
    
    public static BackupConfig parseYaml(String resourcePath) throws IOException {
        try (InputStream is = ConfigParser.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IOException("Configuration file not found: " + resourcePath);
            }
            
            Properties props = SimpleYamlParser.parseYamlToProperties(is);
            return mapPropertiesToConfig(props);
        }
    }
    
    public static BackupConfig parseYamlFromFile(String filePath) throws IOException {
        try (InputStream is = Files.newInputStream(Paths.get(filePath))) {
            Properties props = SimpleYamlParser.parseYamlToProperties(is);
            return mapPropertiesToConfig(props);
        }
    }
    
    public static BackupConfig autoDetectAndParse() throws IOException {
        return autoDetectAndParse(null);
    }
    
    public static BackupConfig autoDetectAndParse(String configLocation) throws IOException {
        // 1. 프로젝트 루트에서 db-backup4j.properties 확인
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
        
        // 2. annotation의 configLocation 존재여부 확인
        if (configLocation != null && !configLocation.trim().isEmpty()) {
            File configFile = new File(configLocation);
            if (configFile.exists() && configFile.isFile()) {
                return parseConfigFile(configLocation);
            }
        }
        
        // 3. 모든 경로에서 파일을 찾지 못한 경우
        throw new IOException(
            "Configuration file not found. Searched paths: "
                + String.join(", ", defaultPaths)
                + (configLocation != null ? ", " + configLocation : "")
        );
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
        BackupConfig config = new BackupConfig();
        
        config.setDatabase(mapDatabaseConfig(props));
        config.setLocal(mapLocalBackupConfig(props));
        config.setEmail(mapEmailConfig(props));
        config.setS3(mapS3Config(props));
        config.setSchedule(mapScheduleConfig(props));
        
        return config;
    }

    private static DatabaseConfig mapDatabaseConfig(Properties props) {
        DatabaseConfig db = new DatabaseConfig();
        
        String typeStr = props.getProperty("database.type");
        if (typeStr != null) {
            try {
                DatabaseType type = DatabaseType.fromString(typeStr);
                db.setType(type);
            } catch (IllegalArgumentException e) {
                // 잘못된 타입은 null로 설정하고 나중에 validator에서 검증
                db.setType(null);
            }
        }
        
        db.setHost(props.getProperty("database.host", "localhost"));
        
        String portStr = props.getProperty("database.port", "3306");
        try {
            db.setPort(Integer.parseInt(portStr));
        } catch (NumberFormatException e) {
            db.setPort(0); // 잘못된 포트는 0으로 설정
        }
        
        db.setName(props.getProperty("database.name"));
        db.setUsername(props.getProperty("database.username"));
        db.setPassword(props.getProperty("database.password"));
        
        return db;
    }

    private static LocalBackupConfig mapLocalBackupConfig(Properties props) {
        LocalBackupConfig local = new LocalBackupConfig();
        local.setEnabled(Boolean.parseBoolean(props.getProperty("backup.local.enabled", "true")));
        local.setPath(props.getProperty("backup.local.path", "./db-backup4j"));
        local.setRetention(props.getProperty("backup.local.retention", "30"));
        local.setCompress(Boolean.parseBoolean(props.getProperty("backup.local.compress", "true")));
        return local;
    }

    private static EmailBackupConfig mapEmailConfig(Properties props) {
        EmailBackupConfig email = new EmailBackupConfig();
        email.setEnabled(Boolean.parseBoolean(props.getProperty("backup.email.enabled", "false")));
        email.setUsername(props.getProperty("backup.email.username"));
        email.setPassword(props.getProperty("backup.email.password"));
        
        EmailBackupConfig.SmtpConfig smtp = new EmailBackupConfig.SmtpConfig();
        smtp.setHost(props.getProperty("backup.email.smtp.host"));
        
        String smtpPortStr = props.getProperty("backup.email.smtp.port", "587");
        try {
            smtp.setPort(Integer.parseInt(smtpPortStr));
        } catch (NumberFormatException e) {
            smtp.setPort(0); // 잘못된 포트는 0으로 설정
        }
        email.setSmtp(smtp);
        
        String recipients = props.getProperty("backup.email.recipients");
        if (recipients != null) {
            List<String> recipientList = Arrays.asList(recipients.split(","));
            email.setRecipients(recipientList);
        }
        
        return email;
    }

    private static S3BackupConfig mapS3Config(Properties props) {
        S3BackupConfig s3 = new S3BackupConfig();
        s3.setEnabled(Boolean.parseBoolean(props.getProperty("backup.s3.enabled", "false")));
        s3.setBucket(props.getProperty("backup.s3.bucket"));
        s3.setPrefix(props.getProperty("backup.s3.prefix", "backups"));
        s3.setRegion(props.getProperty("backup.s3.region", "us-east-1"));
        s3.setAccessKey(props.getProperty("backup.s3.access-key"));
        s3.setSecretKey(props.getProperty("backup.s3.secret-key"));
        return s3;
    }

    private static ScheduleConfig mapScheduleConfig(Properties props) {
        ScheduleConfig schedule = new ScheduleConfig();
        schedule.setEnabled(Boolean.parseBoolean(props.getProperty("schedule.enabled", "false")));
        schedule.setDaily(props.getProperty("schedule.daily"));
        schedule.setWeekly(props.getProperty("schedule.weekly"));
        schedule.setMonthly(props.getProperty("schedule.monthly"));
        return schedule;
    }
}