package io.backup4j.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.io.FileWriter;

import static org.junit.jupiter.api.Assertions.*;

class DbBackup4jInitializerTest {

    @TempDir
    Path tempDir;

    private Path configFile;
    private ByteArrayOutputStream outputStream;

    @BeforeEach
    void setUp() {
        configFile = tempDir.resolve("test-config.properties");
        
        // Capture console output
        outputStream = new ByteArrayOutputStream();
        System.setOut(new PrintStream(outputStream));
        System.setErr(new PrintStream(outputStream));
    }

    @Test
    void run_유효한설정파일로_정상실행() throws Exception {
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
        
        try (FileWriter writer = new FileWriter(configFile.toFile())) {
            writer.write(content);
        }
        
        // when
        assertDoesNotThrow(() -> DbBackup4jInitializer.run(configFile.toString()));
        
        // then
        String output = outputStream.toString();
        assertTrue(output.contains("Starting DB Backup4j..."));
        assertTrue(output.contains("Using config file: " + configFile.toString()));
        assertTrue(output.contains("Configuration validated successfully"));
        assertTrue(output.contains("Schedule disabled - running one-time backup"));
        assertTrue(output.contains("Database: MYSQL at localhost"));
        assertTrue(output.contains("DB Backup4j started successfully"));
    }

    @Test
    void run_스케줄활성화로_스케줄러실행() throws Exception {
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
            "\n" +
            "backup.email.enabled=false\n" +
            "backup.s3.enabled=false\n" +
            "\n" +
            "schedule.enabled=true\n" +
            "schedule.daily=02:00\n";
        
        try (FileWriter writer = new FileWriter(configFile.toFile())) {
            writer.write(content);
        }
        
        // when
        assertDoesNotThrow(() -> DbBackup4jInitializer.run(configFile.toString()));
        
        // then
        String output = outputStream.toString();
        assertTrue(output.contains("Schedule enabled - starting scheduler"));
        assertTrue(output.contains("Starting backup scheduler..."));
        assertTrue(output.contains("Daily schedule: 02:00"));
        assertTrue(output.contains("Scheduler started successfully"));
    }

    @Test
    void run_잘못된설정으로_예외발생() throws Exception {
        // given
        String content = "database.type=MYSQL\n" +
            "database.host=\n" +  // Empty host should fail validation
            "database.port=3306\n" +
            "database.name=testdb\n" +
            "database.username=user\n" +
            "database.password=pass\n" +
            "\n" +
            "backup.local.enabled=true\n" +
            "backup.local.path=/backup\n" +
            "\n" +
            "backup.email.enabled=false\n" +
            "backup.s3.enabled=false\n" +
            "schedule.enabled=false\n";
        
        try (FileWriter writer = new FileWriter(configFile.toFile())) {
            writer.write(content);
        }
        
        // when & then
        RuntimeException exception = assertThrows(RuntimeException.class,
            () -> DbBackup4jInitializer.run(configFile.toString()));
        
        assertEquals("Backup execution failed", exception.getMessage());
        
        String output = outputStream.toString();
        assertTrue(output.contains("Configuration validation failed:"));
        assertTrue(output.contains("Database host is required"));
    }

    @Test
    void run_존재하지않는파일로_예외발생() {
        // given
        String nonexistentFile = "nonexistent.properties";
        
        // when & then
        RuntimeException exception = assertThrows(RuntimeException.class,
            () -> DbBackup4jInitializer.run(nonexistentFile));
        
        assertEquals("Backup execution failed", exception.getMessage());
        
        String output = outputStream.toString();
        assertTrue(output.contains("Backup failed:"));
    }
}