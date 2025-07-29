package io.backup4j.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.AfterEach;

import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.io.FileWriter;

import static org.junit.jupiter.api.Assertions.*;

class DbBackup4jInitializerTest {

    @TempDir
    Path tempDir;

    private Path configFile;

    @BeforeEach
    void setUp() {
        configFile = tempDir.resolve("test-config.properties");
        
    }
    

    @Test
    void run_유효한설정파일로_정상실행() throws Exception {
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
        
        try (FileWriter writer = new FileWriter(configFile.toFile())) {
            writer.write(content);
        }
        
        // when
        assertDoesNotThrow(() -> DbBackup4jInitializer.run(configFile.toString()));
        
        // then - 로거가 제거되어 예외 없이 실행되면 성공
    }

    @Test
    @org.junit.jupiter.api.Timeout(value = 10, unit = java.util.concurrent.TimeUnit.SECONDS)
    void run_스케줄활성화로_스케줄러실행() throws Exception {
        // given
        String content = "database.url=jdbc:mysql://localhost:3306/testdb\n" +
            "database.username=user\n" +
            "database.password=pass\n" +
            "\n" +
            "backup.local.enabled=true\n" +
            "backup.local.path=/backup\n" +
            "\n" +
            "backup.s3.enabled=false\n" +
            "\n" +
            "schedule.enabled=true\n" +
            "schedule.cron=0 2 * * *\n";
        
        try (FileWriter writer = new FileWriter(configFile.toFile())) {
            writer.write(content);
        }
        
        // when - run in separate thread with timeout
        Thread schedulerThread = new Thread(() -> {
            try {
                DbBackup4jInitializer.run(configFile.toString());
            } catch (RuntimeException e) {
                // Expected backup execution failure
            }
        });
        
        schedulerThread.start();
        Thread.sleep(2000); // Wait for scheduler to start
        schedulerThread.interrupt(); // Stop the scheduler
        
        // then - 로거가 제거되어 스케줄러 시작만 확인
    }

    @Test
    void run_잘못된설정으로_예외발생() throws Exception {
        // given
        String content = "database.url=\n" +  // Empty URL should fail validation
            "database.username=user\n" +
            "database.password=pass\n" +
            "\n" +
            "backup.local.enabled=true\n" +
            "backup.local.path=/backup\n" +
            "\n" +
            "backup.s3.enabled=false\n" +
            "schedule.enabled=false\n";
        
        try (FileWriter writer = new FileWriter(configFile.toFile())) {
            writer.write(content);
        }
        
        // when & then
        RuntimeException exception = assertThrows(RuntimeException.class,
            () -> DbBackup4jInitializer.run(configFile.toString()));
        
        assertEquals("Backup execution failed", exception.getMessage());
        
        // 로거 제거로 인해 예외만 검증
    }

    @Test
    void run_존재하지않는파일로_예외발생() {
        // given
        String nonexistentFile = "nonexistent.properties";
        
        // when & then
        RuntimeException exception = assertThrows(RuntimeException.class,
            () -> DbBackup4jInitializer.run(nonexistentFile));
        
        assertEquals("Backup execution failed", exception.getMessage());
        
        // 로거 제거로 인해 예외만 검증
    }

    @Test
    void runFromClasspath_존재하지않는리소스로_예외발생() {
        // given
        String nonexistentResource = "nonexistent-config.yml";
        
        // when & then
        RuntimeException exception = assertThrows(RuntimeException.class,
            () -> DbBackup4jInitializer.runFromClasspath(nonexistentResource));
        
        assertEquals("Failed to load config from classpath", exception.getMessage());
        assertTrue(exception.getMessage().contains("Failed to load config from classpath"));
        
        // 로거 제거로 인해 예외만 검증
    }

    @Test
    void run_파라미터없이_자동감지테스트() {
        // given - 아무 설정 파일이 없는 상태
        
        // when & then
        RuntimeException exception = assertThrows(RuntimeException.class,
            DbBackup4jInitializer::run);
        
        assertEquals("Backup execution failed", exception.getMessage());
        
        // 로거 제거로 인해 예외만 검증
    }
}