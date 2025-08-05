package io.backup4j.core.scheduler;

import io.backup4j.core.config.BackupConfig;
import io.backup4j.core.config.DatabaseConfig;
import io.backup4j.core.config.LocalBackupConfig;
import io.backup4j.core.config.ScheduleConfig;
import io.backup4j.core.config.S3BackupConfig;
import io.backup4j.core.exception.SchedulerStartException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class SimpleBackupSchedulerTest {

    private BackupConfig config;
    private SimpleBackupScheduler scheduler;

    @BeforeEach
    void setUp() {
        // 기본 설정 구성
        DatabaseConfig databaseConfig = DatabaseConfig.builder()
                .url("jdbc:mysql://localhost:3306/test_db")
                .username("test")
                .password("test")
                .build();

        LocalBackupConfig localConfig = LocalBackupConfig.builder()
                .enabled(true)
                .path("./test-backup")
                .retention("7")
                .compress(true)
                .build();

        config = BackupConfig.builder()
                .database(databaseConfig)
                .local(localConfig)
                .s3(S3BackupConfig.builder().build())
                .build();
    }

    @AfterEach
    void tearDown() {
        if (scheduler != null && scheduler.isRunning()) {
            scheduler.stop();
        }
    }

    @Test
    void constructor_유효한설정으로_성공적생성() {
        // Given & When
        scheduler = new SimpleBackupScheduler(config);

        // Then
        assertNotNull(scheduler);
        assertFalse(scheduler.isRunning());
    }

    @Test
    void start_스케줄비활성화로_시작안함() throws SchedulerStartException {
        // Given
        ScheduleConfig scheduleConfig = ScheduleConfig.builder()
                .enabled(false)
                .build();
        
        BackupConfig configWithSchedule = BackupConfig.builder()
                .database(config.getDatabase())
                .local(config.getLocal())
                .s3(config.getS3())
                .schedule(scheduleConfig)
                .build();
        
        scheduler = new SimpleBackupScheduler(configWithSchedule);

        // When
        scheduler.start();

        // Then
        assertFalse(scheduler.isRunning());
    }

    @Test
    void start_null스케줄로_시작안함() throws SchedulerStartException {
        // Given
        scheduler = new SimpleBackupScheduler(config);

        // When
        scheduler.start();

        // Then
        assertFalse(scheduler.isRunning());
    }

    @Test
    void start_빈cron표현식으로_시작안함() throws SchedulerStartException {
        // Given
        ScheduleConfig scheduleConfig = ScheduleConfig.builder()
                .enabled(true)
                .cron("")
                .build();
        
        BackupConfig configWithSchedule = BackupConfig.builder()
                .database(config.getDatabase())
                .local(config.getLocal())
                .s3(config.getS3())
                .schedule(scheduleConfig)
                .build();
        
        scheduler = new SimpleBackupScheduler(configWithSchedule);

        // When
        scheduler.start();

        // Then
        assertFalse(scheduler.isRunning());
    }

    @Test
    void start_null_cron표현식으로_시작안함() throws SchedulerStartException {
        // Given
        ScheduleConfig scheduleConfig = ScheduleConfig.builder()
                .enabled(true)
                .cron(null)
                .build();
        
        BackupConfig configWithSchedule = BackupConfig.builder()
                .database(config.getDatabase())
                .local(config.getLocal())
                .s3(config.getS3())
                .schedule(scheduleConfig)
                .build();
        
        scheduler = new SimpleBackupScheduler(configWithSchedule);

        // When
        scheduler.start();

        // Then
        assertFalse(scheduler.isRunning());
    }

    @Test
    void start_잘못된cron표현식으로_예외발생() {
        // Given
        ScheduleConfig scheduleConfig = ScheduleConfig.builder()
                .enabled(true)
                .cron("invalid cron")
                .build();
        
        BackupConfig configWithSchedule = BackupConfig.builder()
                .database(config.getDatabase())
                .local(config.getLocal())
                .s3(config.getS3())
                .schedule(scheduleConfig)
                .build();
        
        scheduler = new SimpleBackupScheduler(configWithSchedule);

        // When & Then
        assertThrows(SchedulerStartException.class, () -> scheduler.start());
        assertFalse(scheduler.isRunning());
    }


    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void start_유효한cron표현식으로_스케줄러시작() throws SchedulerStartException {
        // Given
        ScheduleConfig scheduleConfig = ScheduleConfig.builder()
                .enabled(true)
                .cron("0 0 * * *") // 매일 자정
                .build();
        
        BackupConfig configWithSchedule = BackupConfig.builder()
                .database(config.getDatabase())
                .local(config.getLocal())
                .s3(config.getS3())
                .schedule(scheduleConfig)
                .build();
        
        scheduler = new SimpleBackupScheduler(configWithSchedule);

        // When
        scheduler.start();

        // Then
        assertTrue(scheduler.isRunning());
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void start_이미실행중인스케줄러로_중복시작방지() throws SchedulerStartException {
        // Given
        ScheduleConfig scheduleConfig = ScheduleConfig.builder()
                .enabled(true)
                .cron("0 0 * * *") // 매일 자정
                .build();
        
        BackupConfig configWithSchedule = BackupConfig.builder()
                .database(config.getDatabase())
                .local(config.getLocal())
                .s3(config.getS3())
                .schedule(scheduleConfig)
                .build();
        
        scheduler = new SimpleBackupScheduler(configWithSchedule);
        scheduler.start();

        // When
        scheduler.start(); // 두 번째 시작 호출

        // Then
        assertTrue(scheduler.isRunning());
    }

    @Test
    void stop_실행중이아닌스케줄러_안전한중지() {
        // Given
        scheduler = new SimpleBackupScheduler(config);
        assertFalse(scheduler.isRunning());

        // When & Then (예외 발생하지 않아야 함)
        assertDoesNotThrow(() -> scheduler.stop());
        assertFalse(scheduler.isRunning());
    }

    @Test
    void isRunning_초기상태_false반환() {
        // Given
        scheduler = new SimpleBackupScheduler(config);

        // When & Then
        assertFalse(scheduler.isRunning());
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void stop_실행중인스케줄러_정상중지() throws SchedulerStartException {
        // Given
        ScheduleConfig scheduleConfig = ScheduleConfig.builder()
                .enabled(true)
                .cron("0 0 * * *") // 매일 자정
                .build();
        
        BackupConfig configWithSchedule = BackupConfig.builder()
                .database(config.getDatabase())
                .local(config.getLocal())
                .s3(config.getS3())
                .schedule(scheduleConfig)
                .build();
        
        scheduler = new SimpleBackupScheduler(configWithSchedule);
        scheduler.start();
        assertTrue(scheduler.isRunning());

        // When
        scheduler.stop();

        // Then
        assertFalse(scheduler.isRunning());
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void isRunning_시작후_true반환() throws SchedulerStartException {
        // Given
        ScheduleConfig scheduleConfig = ScheduleConfig.builder()
                .enabled(true)
                .cron("0 0 * * *") // 매일 자정
                .build();
        
        BackupConfig configWithSchedule = BackupConfig.builder()
                .database(config.getDatabase())
                .local(config.getLocal())
                .s3(config.getS3())
                .schedule(scheduleConfig)
                .build();
        
        scheduler = new SimpleBackupScheduler(configWithSchedule);

        // When
        scheduler.start();

        // Then
        assertTrue(scheduler.isRunning());
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void isRunning_중지후_false반환() throws SchedulerStartException {
        // Given
        ScheduleConfig scheduleConfig = ScheduleConfig.builder()
                .enabled(true)
                .cron("0 0 * * *") // 매일 자정
                .build();
        
        BackupConfig configWithSchedule = BackupConfig.builder()
                .database(config.getDatabase())
                .local(config.getLocal())
                .s3(config.getS3())
                .schedule(scheduleConfig)
                .build();
        
        scheduler = new SimpleBackupScheduler(configWithSchedule);
        scheduler.start();

        // When
        scheduler.stop();

        // Then
        assertFalse(scheduler.isRunning());
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void start_매분실행cron_스케줄링동작확인() throws InterruptedException, SchedulerStartException {
        // Given - 매분 실행하는 cron (테스트용)
        ScheduleConfig scheduleConfig = ScheduleConfig.builder()
                .enabled(true)
                .cron("* * * * *") // 매분 실행
                .build();
        
        BackupConfig configWithSchedule = BackupConfig.builder()
                .database(config.getDatabase())
                .local(config.getLocal())
                .s3(config.getS3())
                .schedule(scheduleConfig)
                .build();
        
        scheduler = new SimpleBackupScheduler(configWithSchedule);

        // When
        scheduler.start();
        
        // 스케줄러가 시작되었는지 확인
        assertTrue(scheduler.isRunning());
        
        // 잠시 대기하여 스케줄링이 실제로 동작하는지 확인
        Thread.sleep(2000);
        
        // Then
        assertTrue(scheduler.isRunning());
    }
}