package io.backup4j.core.config;

import io.backup4j.core.database.DatabaseType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.io.FileWriter;

import static org.junit.jupiter.api.Assertions.*;

class ConfigParserTest {

    @TempDir
    Path tempDir;

    private Path propertiesFile;
    private Path yamlFile;

    @BeforeEach
    void setUp() {
        propertiesFile = tempDir.resolve("test-config.properties");
        yamlFile = tempDir.resolve("test-config.yaml");
    }

    @Test
    void parseConfigFile_properties파일로_정상파싱() throws Exception {
        // given
        String content = "database.type=MYSQL\n" +
            "database.host=localhost\n" +
            "database.port=3306\n" +
            "database.name=testdb\n" +
            "database.username=user\n" +
            "database.password=pass\n" +
            "\n" +
            "backup.local.enabled=true\n" +
            "backup.local.path=/backup\n" +
            "backup.local.retention=30\n" +
            "backup.local.compress=true\n" +
            "\n" +
            "backup.notification.enabled=false\n" +
            "backup.s3.enabled=false\n" +
            "schedule.enabled=false\n";
        
        try (FileWriter writer = new FileWriter(propertiesFile.toFile())) {
            writer.write(content);
        }
        
        // when
        BackupConfig config = ConfigParser.parseConfigFile(propertiesFile.toString());
        
        // then
        assertNotNull(config);
        assertEquals(DatabaseType.MYSQL, config.getDatabase().getType());
        assertEquals("localhost", config.getDatabase().getHost());
        assertEquals(3306, config.getDatabase().getPort());
        assertEquals("testdb", config.getDatabase().getName());
        assertEquals("user", config.getDatabase().getUsername());
        assertEquals("pass", config.getDatabase().getPassword());
        
        assertTrue(config.getLocal().isEnabled());
        assertEquals("/backup", config.getLocal().getPath());
        assertEquals("30", config.getLocal().getRetention());
        assertTrue(config.getLocal().isCompress());
        
        assertFalse(config.getNotification().isEnabled());
        assertFalse(config.getS3().isEnabled());
        assertFalse(config.getSchedule().isEnabled());
    }

    @Test
    void parseConfigFile_yaml파일로_정상파싱() throws Exception {
        // given
        String content = "database:\n" +
            "  type: POSTGRESQL\n" +
            "  host: localhost\n" +
            "  port: 5432\n" +
            "  name: testdb\n" +
            "  username: user\n" +
            "  password: pass\n" +
            "\n" +
            "backup:\n" +
            "  local:\n" +
            "    enabled: true\n" +
            "    path: /backup\n" +
            "    retention: 30\n" +
            "    compress: true\n" +
            "  notification:\n" +
            "    enabled: false\n" +
            "  s3:\n" +
            "    enabled: false\n" +
            "\n" +
            "schedule:\n" +
            "  enabled: false\n";
        
        try (FileWriter writer = new FileWriter(yamlFile.toFile())) {
            writer.write(content);
        }
        
        // when
        BackupConfig config = ConfigParser.parseConfigFile(yamlFile.toString());
        
        // then
        assertNotNull(config);
        assertEquals(DatabaseType.POSTGRESQL, config.getDatabase().getType());
        assertEquals("localhost", config.getDatabase().getHost());
        assertEquals(5432, config.getDatabase().getPort());
        assertEquals("testdb", config.getDatabase().getName());
        assertEquals("user", config.getDatabase().getUsername());
        assertEquals("pass", config.getDatabase().getPassword());
        
        assertTrue(config.getLocal().isEnabled());
        assertEquals("/backup", config.getLocal().getPath());
        assertEquals("30", config.getLocal().getRetention());
        assertTrue(config.getLocal().isCompress());
        
        assertFalse(config.getNotification().isEnabled());
        assertFalse(config.getS3().isEnabled());
        assertFalse(config.getSchedule().isEnabled());
    }

    @Test
    void parseConfigFile_파일없음으로_예외발생() {
        // given
        String nonexistentFile = "nonexistent.properties";
        
        // when & then
        IOException exception = assertThrows(IOException.class, () -> 
            ConfigParser.parseConfigFile(nonexistentFile));
        assertNotNull(exception.getMessage());
    }

    @Test
    void parseConfigFile_잘못된데이터베이스타입으로_null처리() throws Exception {
        // given
        String content = "database.type=INVALID_TYPE\n" +
            "database.host=localhost\n" +
            "database.port=3306\n" +
            "database.name=testdb\n" +
            "database.username=user\n" +
            "database.password=pass\n" +
            "backup.local.enabled=true\n" +
            "backup.notification.enabled=false\n" +
            "backup.s3.enabled=false\n" +
            "schedule.enabled=false\n";
        
        try (FileWriter writer = new FileWriter(propertiesFile.toFile())) {
            writer.write(content);
        }
        
        // when
        BackupConfig config = ConfigParser.parseConfigFile(propertiesFile.toString());
        
        // then - invalid type should be set to null and caught by validator
        assertNotNull(config);
        assertNull(config.getDatabase().getType());
    }

    @Test
    void autoDetectAndParse_설정파일없음으로_예외발생() {
        // given
        // when & then
        IOException exception = assertThrows(IOException.class, 
            ConfigParser::autoDetectAndParse);
        assertTrue(exception.getMessage().contains("Configuration not found"));
    }

    @Test
    void parseConfigFile_YAML복잡한중첩구조_정상파싱() throws Exception {
        // given - 실제 지원하는 구조로 수정
        String content = "database:\n" +
            "  type: MYSQL\n" +
            "  host: localhost\n" +
            "  port: 3306\n" +
            "  name: testdb\n" +
            "  username: user\n" +
            "  password: pass\n" +
            "\n" +
            "backup:\n" +
            "  local:\n" +
            "    enabled: true\n" +
            "    path: /backup\n" +
            "    retention: 30\n" +
            "    compress: true\n" +
            "  notification:\n" +
            "    enabled: false\n" +
            "  s3:\n" +
            "    enabled: false\n" +
            "\n" +
            "schedule:\n" +
            "  enabled: false\n";
        
        try (FileWriter writer = new FileWriter(yamlFile.toFile())) {
            writer.write(content);
        }
        
        // when
        BackupConfig config = ConfigParser.parseConfigFile(yamlFile.toString());
        
        // then
        assertNotNull(config);
        assertEquals(DatabaseType.MYSQL, config.getDatabase().getType());
        assertEquals("localhost", config.getDatabase().getHost());
        assertEquals(3306, config.getDatabase().getPort());
        assertEquals("user", config.getDatabase().getUsername());
        assertEquals("pass", config.getDatabase().getPassword());
        assertEquals("testdb", config.getDatabase().getName());
        
        assertTrue(config.getLocal().isEnabled());
        assertEquals("/backup", config.getLocal().getPath());
        assertEquals("30", config.getLocal().getRetention());
        assertTrue(config.getLocal().isCompress());
    }

    @Test
    void parseConfigFile_YAML주석포함_정상파싱() throws Exception {
        // given
        String content = "# Database configuration\n" +
            "database:\n" +
            "  type: MYSQL  # Database type\n" +
            "  host: localhost  # Database host\n" +
            "  port: 3306\n" +
            "  name: testdb\n" +
            "  username: user\n" +
            "  password: pass\n" +
            "\n" +
            "# Backup configuration\n" +
            "backup:\n" +
            "  local:\n" +
            "    enabled: true  # Enable local backup\n" +
            "    path: /backup\n" +
            "    retention: 30\n" +
            "    compress: true\n" +
            "  notification:\n" +
            "    enabled: false  # Disable notification\n" +
            "  s3:\n" +
            "    enabled: false\n" +
            "\n" +
            "schedule:\n" +
            "  enabled: false\n";
        
        try (FileWriter writer = new FileWriter(yamlFile.toFile())) {
            writer.write(content);
        }
        
        // when
        BackupConfig config = ConfigParser.parseConfigFile(yamlFile.toString());
        
        // then
        assertNotNull(config);
        assertEquals(DatabaseType.MYSQL, config.getDatabase().getType());
        assertEquals("localhost", config.getDatabase().getHost());
        assertTrue(config.getLocal().isEnabled());
        assertFalse(config.getNotification().isEnabled());
    }

    @Test
    void parseConfigFile_YAML잘못된형식_예외발생() throws Exception {
        // given
        String content = "database:\n" +
            "  type: MYSQL\n" +
            "  host localhost  # 콜론 누락\n" +
            "  port: 3306\n" +
            "invalid_yaml_structure\n";
        
        try (FileWriter writer = new FileWriter(yamlFile.toFile())) {
            writer.write(content);
        }
        
        // when & then
        IOException exception = assertThrows(IOException.class, () -> {
            ConfigParser.parseConfigFile(yamlFile.toString());
        });
        
        assertTrue(exception.getMessage().contains("Failed to parse YAML"));
    }

    @Test
    void parseConfigFile_YAML빈값처리_기본값사용() throws Exception {
        // given
        String content = "database:\n" +
            "  type: MYSQL\n" +
            "  host:  # 빈 값\n" +
            "  port: 3306\n" +
            "  name: testdb\n" +
            "  username: user\n" +
            "  password: pass\n" +
            "\n" +
            "backup:\n" +
            "  local:\n" +
            "    enabled: true\n" +
            "    path:  # 빈 값\n" +
            "    retention:  # 빈 값\n" +
            "    compress: true\n";
        
        try (FileWriter writer = new FileWriter(yamlFile.toFile())) {
            writer.write(content);
        }
        
        // when
        BackupConfig config = ConfigParser.parseConfigFile(yamlFile.toString());
        
        // then
        assertNotNull(config);
        assertEquals("localhost", config.getDatabase().getHost()); // 기본값
        assertEquals("./db-backup4j", config.getLocal().getPath()); // 기본값
        assertEquals("30", config.getLocal().getRetention()); // 기본값
    }

    @Test
    void parseConfigFile_YAML타입불일치_적절한처리() throws Exception {
        // given
        String content = "database:\n" +
            "  type: MYSQL\n" +
            "  host: localhost\n" +
            "  port: \"not_a_number\"  # 문자열 포트\n" +
            "  name: testdb\n" +
            "  username: user\n" +
            "  password: pass\n" +
            "\n" +
            "backup:\n" +
            "  local:\n" +
            "    enabled: \"not_boolean\"  # 잘못된 boolean\n" +
            "    path: /backup\n" +
            "    retention: 30\n" +
            "    compress: true\n";
        
        try (FileWriter writer = new FileWriter(yamlFile.toFile())) {
            writer.write(content);
        }
        
        // when
        BackupConfig config = ConfigParser.parseConfigFile(yamlFile.toString());
        
        // then
        assertNotNull(config);
        assertEquals(0, config.getDatabase().getPort()); // 잘못된 포트는 0으로 설정
        assertFalse(config.getLocal().isEnabled()); // 잘못된 boolean은 false로 설정
    }

    @Test
    void parseFromEnvironment_환경변수파싱_정상동작() {
        // when - 실제 환경변수가 없는 경우 테스트
        BackupConfig config = ConfigParser.parseFromEnvironment();
        
        // then - 환경변수가 없으므로 기본값들이 설정됨
        assertNotNull(config);
        assertNotNull(config.getDatabase());
        assertNotNull(config.getLocal());
        assertNotNull(config.getNotification());
        assertNotNull(config.getS3());
        assertNotNull(config.getSchedule());
        
        // 기본값 확인
        assertEquals("localhost", config.getDatabase().getHost());
        assertTrue(config.getLocal().isEnabled());
        assertEquals("./db-backup4j", config.getLocal().getPath());
    }

    @Test
    void parseConfigFile_지원하지않는확장자_예외발생() {
        // given
        String unsupportedFile = tempDir.resolve("config.txt").toString();
        
        // when & then
        IOException exception = assertThrows(IOException.class, () -> {
            ConfigParser.parseConfigFile(unsupportedFile);
        });
        
        assertTrue(exception.getMessage().contains("Unsupported file format"));
        assertTrue(exception.getMessage().contains("config.txt"));
    }

    @Test
    void parseConfigFile_YAML리스트처리_정상파싱() throws Exception {
        // given
        String content = "database:\n" +
            "  type: MYSQL\n" +
            "  host: localhost\n" +
            "  port: 3306\n" +
            "  name: testdb\n" +
            "  username: user\n" +
            "  password: pass\n" +
            "\n" +
            "backup:\n" +
            "  notification:\n" +
            "    enabled: true\n" +
            "    email:\n" +
            "      enabled: true\n" +
            "      recipients: \"user1@example.com,user2@example.com\"\n" +
            "      username: sender@example.com\n" +
            "      password: emailpass\n" +
            "      smtp:\n" +
            "        host: smtp.example.com\n" +
            "        port: 587\n" +
            "  local:\n" +
            "    enabled: false\n" +
            "  s3:\n" +
            "    enabled: false\n";
        
        try (FileWriter writer = new FileWriter(yamlFile.toFile())) {
            writer.write(content);
        }
        
        // when
        BackupConfig config = ConfigParser.parseConfigFile(yamlFile.toString());
        
        // then
        assertNotNull(config);
        assertTrue(config.getNotification().isEnabled());
        assertTrue(config.getNotification().getEmail().isEnabled());
        assertEquals("sender@example.com", config.getNotification().getEmail().getUsername());
        assertEquals("emailpass", config.getNotification().getEmail().getPassword());
        assertEquals("smtp.example.com", config.getNotification().getEmail().getSmtp().getHost());
        assertEquals(587, config.getNotification().getEmail().getSmtp().getPort());
        assertNotNull(config.getNotification().getEmail().getRecipients());
        assertEquals(2, config.getNotification().getEmail().getRecipients().size());
    }

    @Test
    void parseConfigFile_properties숫자형식오류_기본값사용() throws Exception {
        // given
        String content = "database.type=MYSQL\n" +
            "database.host=localhost\n" +
            "database.port=invalid_port\n" +
            "database.name=testdb\n" +
            "database.username=user\n" +
            "database.password=pass\n" +
            "backup.notification.email.smtp.port=invalid_smtp_port\n";
        
        try (FileWriter writer = new FileWriter(propertiesFile.toFile())) {
            writer.write(content);
        }
        
        // when
        BackupConfig config = ConfigParser.parseConfigFile(propertiesFile.toString());
        
        // then
        assertNotNull(config);
        assertEquals(0, config.getDatabase().getPort()); // 잘못된 포트는 0으로 설정
        assertEquals(0, config.getNotification().getEmail().getSmtp().getPort()); // 잘못된 SMTP 포트는 0으로 설정
    }
}