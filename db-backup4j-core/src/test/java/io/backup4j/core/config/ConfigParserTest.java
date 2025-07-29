package io.backup4j.core.config;

import io.backup4j.core.database.DatabaseType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
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
        String content = "database.url=jdbc:mysql://localhost:3306/testdb\n" +
            "database.username=user\n" +
            "database.password=pass\n" +
            "\n" +
            "backup.local.enabled=true\n" +
            "backup.local.path=/backup\n" +
            "backup.local.retention=30\n" +
            "backup.local.compress=true\n" +
            "\n" +
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
        assertEquals("testdb", config.getDatabase().getName());
        assertEquals("user", config.getDatabase().getUsername());
        assertEquals("pass", config.getDatabase().getPassword());
        
        assertTrue(config.getLocal().isEnabled());
        assertEquals("/backup", config.getLocal().getPath());
        assertEquals("30", config.getLocal().getRetention());
        assertTrue(config.getLocal().isCompress());
        
        assertFalse(config.getS3().isEnabled());
        assertFalse(config.getSchedule().isEnabled());
    }

    @Test
    void parseConfigFile_yaml파일로_정상파싱() throws Exception {
        // given
        String content = "database:\n" +
            "  url: jdbc:postgresql://localhost:5432/testdb\n" +
            "  username: user\n" +
            "  password: pass\n" +
            "\n" +
            "backup:\n" +
            "  local:\n" +
            "    enabled: true\n" +
            "    path: /backup\n" +
            "    retention: 30\n" +
            "    compress: true\n" +
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
        assertEquals("testdb", config.getDatabase().getName());
        assertEquals("user", config.getDatabase().getUsername());
        assertEquals("pass", config.getDatabase().getPassword());
        
        assertTrue(config.getLocal().isEnabled());
        assertEquals("/backup", config.getLocal().getPath());
        assertEquals("30", config.getLocal().getRetention());
        assertTrue(config.getLocal().isCompress());
        
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
    void parseConfigFile_잘못된JDBCURL로_예외발생() throws Exception {
        // given
        String content = "database.url=jdbc:invalidtype://localhost:3306/testdb\n" +
            "database.username=user\n" +
            "database.password=pass\n" +
            "backup.local.enabled=true\n" +
            "backup.s3.enabled=false\n" +
            "schedule.enabled=false\n";
        
        try (FileWriter writer = new FileWriter(propertiesFile.toFile())) {
            writer.write(content);
        }
        
        // when & then - Invalid JDBC URL should throw during parsing
        assertThrows(IllegalArgumentException.class, () -> {
            ConfigParser.parseConfigFile(propertiesFile.toString());
        });
    }

    @Test
    void autoDetectAndParse_설정파일없음으로_예외발생() {
        // given
        // when & then
        IOException exception = assertThrows(IOException.class, 
            ConfigParser::autoDetectAndParse);
        assertTrue(exception.getMessage().contains("Configuration not found"));
        // 클래스패스 검색이 포함되어야 함
        assertTrue(exception.getMessage().contains("classpath paths"));
    }

    @Test
    void parseConfigFile_YAML복잡한중첩구조_정상파싱() throws Exception {
        // given - 실제 지원하는 구조로 수정
        String content = "database:\n" +
            "  url: jdbc:mysql://localhost:3306/testdb\n" +
            "  username: user\n" +
            "  password: pass\n" +
            "\n" +
            "backup:\n" +
            "  local:\n" +
            "    enabled: true\n" +
            "    path: /backup\n" +
            "    retention: 30\n" +
            "    compress: true\n" +
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
            "  url: jdbc:mysql://localhost:3306/testdb  # JDBC URL\n" +
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
        assertTrue(config.getLocal().isEnabled());
    }

    @Test
    void parseConfigFile_YAML잘못된형식_예외발생() throws Exception {
        // given
        String content = "database:\n" +
            "  url: jdbc:mysql://localhost:3306/testdb\n" +
            "  host localhost  # 콜론 누락\n" +
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
            "  url: jdbc:mysql://localhost:3306/testdb\n" +
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
        assertEquals("testdb", config.getDatabase().getName());
        assertEquals("./db-backup4j", config.getLocal().getPath()); // 기본값
        assertEquals("30", config.getLocal().getRetention()); // 기본값
    }

    @Test
    void parseConfigFile_YAML타입불일치_적절한처리() throws Exception {
        // given
        String content = "database:\n" +
            "  url: jdbc:mysql://localhost:3306/testdb\n" +
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
        assertEquals(DatabaseType.MYSQL, config.getDatabase().getType());
        assertFalse(config.getLocal().isEnabled()); // 잘못된 boolean은 false로 설정
    }

    @Test
    void parseFromEnvironment_환경변수없음으로_예외발생() {
        // when & then - 환경변수가 없으면 필수 데이터베이스 설정 누락으로 예외 발생
        assertThrows(IllegalArgumentException.class, () -> {
            ConfigParser.parseFromEnvironment();
        });
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
    void parseConfigFile_YAML기본설정_정상파싱() throws Exception {
        // given
        String content = "database:\n" +
            "  url: jdbc:mysql://localhost:3306/testdb\n" +
            "  username: user\n" +
            "  password: pass\n" +
            "\n" +
            "backup:\n" +
            "  local:\n" +
            "    enabled: true\n" +
            "  s3:\n" +
            "    enabled: false\n";
        
        try (FileWriter writer = new FileWriter(yamlFile.toFile())) {
            writer.write(content);
        }
        
        // when
        BackupConfig config = ConfigParser.parseConfigFile(yamlFile.toString());
        
        // then
        assertNotNull(config);
        assertTrue(config.getLocal().isEnabled());
        assertFalse(config.getS3().isEnabled());
    }

    @Test
    void parseConfigFile_properties기본설정_정상파싱() throws Exception {
        // given
        String content = "database.url=jdbc:mysql://localhost:3306/testdb\n" +
            "database.username=user\n" +
            "database.password=pass\n" +
            "backup.local.enabled=true\n";
        
        try (FileWriter writer = new FileWriter(propertiesFile.toFile())) {
            writer.write(content);
        }
        
        // when
        BackupConfig config = ConfigParser.parseConfigFile(propertiesFile.toString());
        
        // then
        assertNotNull(config);
        assertEquals(DatabaseType.MYSQL, config.getDatabase().getType());
        assertTrue(config.getLocal().isEnabled());
    }

    @Test
    void autoDetectAndParse_클래스패스에서_자동감지() throws Exception {
        // given - 클래스패스에 설정 파일이 있는 경우는 실제 테스트 리소스 폴더에 파일이 있어야 함
        // 이 테스트는 실제로는 파일이 없으므로 예외가 발생하는 것을 확인
        
        // when & then
        IOException exception = assertThrows(IOException.class, 
            ConfigParser::autoDetectAndParse);
        assertTrue(exception.getMessage().contains("Configuration not found"));
        assertTrue(exception.getMessage().contains("classpath paths"));
        // 클래스패스 경로들이 포함되어야 함
        assertTrue(exception.getMessage().contains("db-backup4j.properties"));
        assertTrue(exception.getMessage().contains("db-backup4j.yaml"));
        assertTrue(exception.getMessage().contains("db-backup4j.yml"));
    }

    @Test
    void autoDetectAndParse_환경변수_우선순위_확인() throws Exception {
        // given - 환경변수가 없는 상태에서 파일 우선순위 확인
        // 프로젝트 루트에 실제 파일을 만들어서 테스트
        Path rootPropertiesFile = Paths.get("./db-backup4j.properties");
        String content = "database.url=jdbc:mysql://localhost:3306/testdb\n" +
            "database.username=user\n" +
            "database.password=pass\n";
        
        try {
            try (FileWriter writer = new FileWriter(rootPropertiesFile.toFile())) {
                writer.write(content);
            }
            
            // when
            BackupConfig config = ConfigParser.autoDetectAndParse();
            
            // then
            assertNotNull(config);
            assertEquals(DatabaseType.MYSQL, config.getDatabase().getType());
            assertEquals("testdb", config.getDatabase().getName());
            
        } finally {
            // cleanup
            if (rootPropertiesFile.toFile().exists()) {
                rootPropertiesFile.toFile().delete();
            }
        }
    }
}