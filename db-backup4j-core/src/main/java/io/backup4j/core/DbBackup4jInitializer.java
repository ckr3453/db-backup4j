package io.backup4j.core;

import io.backup4j.core.database.DatabaseBackupExecutor;
import io.backup4j.core.config.BackupConfig;
import io.backup4j.core.config.ConfigParser;
import io.backup4j.core.config.ConfigValidator;
import io.backup4j.core.scheduler.SimpleBackupScheduler;

import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * db-backup4j 라이브러리의 메인 진입점
 * 설정 파일을 자동으로 감지하고 백업을 실행하는 통합 초기화 클래스입니다.
 */
public class DbBackup4jInitializer {
    
    private static final Logger logger = Logger.getLogger(DbBackup4jInitializer.class.getName());
    private static SimpleBackupScheduler scheduler;
    
    /**
     * 기본 설정 파일을 자동으로 감지하여 백업을 실행합니다.
     */
    public static void run() {
        run(null);
    }
    
    /**
     * 지정된 설정 파일로 백업을 실행합니다.
     * 
     * @param configPath 설정 파일 경로 (null인 경우 자동 감지)
     */
    public static void run(String configPath) {
        try {
            logger.info("Starting db-backup4j...");
            
            // 1. 설정 파일 로드
            BackupConfig config;
            if (configPath != null && !configPath.trim().isEmpty()) {
                config = ConfigParser.parseConfigFile(configPath);
                logger.log(Level.INFO, "Using config file: {0}", configPath);
            } else {
                config = ConfigParser.autoDetectAndParse();
                logger.info("Auto-detected configuration file");
            }
            
            // 2. 설정 검증
            ConfigValidator.ValidationResult result = ConfigValidator.validate(config);
            if (!result.isValid()) {
                logger.severe("Configuration validation failed:");
                for (String error : result.getErrors()) {
                    logger.log(Level.SEVERE, "  - {0}", error);
                }
                throw new RuntimeException("Invalid configuration");
            }
            
            logger.info("Configuration validated successfully");
            
            // 3. 스케줄 확인 후 실행 방식 결정
            if (config.getSchedule() != null && config.getSchedule().isEnabled()) {
                logger.info("Schedule enabled - starting scheduler");
                startScheduler(config);
            } else {
                logger.info("Schedule disabled - running one-time backup");
                executeBackup(config);
            }
            
            logger.info("db-backup4j started successfully");
            
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Backup failed: {0}", e.getMessage());
            throw new RuntimeException("Backup execution failed", e);
        }
    }

    /**
     * 일회성 백업을 실행합니다.
     * 
     * @param config 백업 설정
     */
    private static void executeBackup(BackupConfig config) {
        logger.info("Executing database backup...");
        
        try {
            logger.log(Level.INFO, "Database: {0} at {1}", new Object[]{config.getDatabase().getType(), config.getDatabase().getHost()});
            logger.log(Level.INFO, "Local backup enabled: {0}", config.getLocal().isEnabled());
            logger.log(Level.INFO, "Notification enabled: {0}", config.getNotification().isEnabled());
            logger.log(Level.INFO, "S3 backup enabled: {0}", config.getS3().isEnabled());
            
            // 실제 백업 실행
            DatabaseBackupExecutor executor = new DatabaseBackupExecutor();
            executor.executeBackup(config);
            
            logger.info("Database backup completed successfully");
            
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Backup execution failed: {0}", e.getMessage());
            throw new RuntimeException("Backup execution failed", e);
        }
    }
    
    /**
     * 스케줄러를 시작하여 주기적인 백업을 실행합니다.
     * 
     * @param config 백업 설정
     */
    private static void startScheduler(BackupConfig config) {
        logger.info("Starting db-backup4j scheduler...");
        logger.log(Level.INFO, "Cron schedule: {0}", config.getSchedule().getCron());
        
        try {
            scheduler = new SimpleBackupScheduler(config);
            scheduler.start();
            
            // JVM 종료 시 스케줄러 정리
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                if (scheduler != null && scheduler.isRunning()) {
                    logger.info("Shutting down scheduler...");
                    scheduler.stop();
                }
            }));
            
            logger.info("Scheduler started successfully");
            
            // 스케줄러가 실행 중일 때 메인 스레드가 종료되지 않도록 대기
            while (scheduler.isRunning()) {
                Thread.sleep(1000);
            }
            
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to start scheduler: {0}", e.getMessage());
            throw new RuntimeException("Scheduler start failed", e);
        }
    }
    
}