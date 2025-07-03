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
            "backup.email.enabled=false\n" +
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
        
        assertFalse(config.getEmail().isEnabled());
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
            "  email:\n" +
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
        
        assertFalse(config.getEmail().isEnabled());
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
            "backup.email.enabled=false\n" +
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
        assertTrue(exception.getMessage().contains("Configuration file not found"));
    }
}