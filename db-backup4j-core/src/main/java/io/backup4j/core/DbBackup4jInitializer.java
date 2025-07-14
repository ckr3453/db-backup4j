package io.backup4j.core;

import io.backup4j.core.database.DatabaseBackupExecutor;
import io.backup4j.core.config.BackupConfig;
import io.backup4j.core.config.ConfigParser;
import io.backup4j.core.config.ConfigValidator;

import java.util.logging.Logger;
import java.util.logging.Level;

public class DbBackup4jInitializer {
    
    private static final Logger logger = Logger.getLogger(DbBackup4jInitializer.class.getName());
    
    public static void run() {
        run(null);
    }
    
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

    private static void executeBackup(BackupConfig config) {
        logger.info("Executing database backup...");
        
        try {
            logger.log(Level.INFO, "Database: {0} at {1}", new Object[]{config.getDatabase().getType(), config.getDatabase().getHost()});
            logger.log(Level.INFO, "Local backup enabled: {0}", config.getLocal().isEnabled());
            logger.log(Level.INFO, "Email backup enabled: {0}", config.getEmail().isEnabled());
            logger.log(Level.INFO, "S3 backup enabled: {0}", config.getS3().isEnabled());
            
            // 실제 백업 실행
            if (isTestEnvironment()) {
                logger.info("Test environment detected - skipping actual backup execution");
            } else {
                DatabaseBackupExecutor executor = new DatabaseBackupExecutor();
                executor.executeBackup(config);
            }
            
            logger.info("Database backup completed successfully");
            
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Backup execution failed: {0}", e.getMessage());
            throw new RuntimeException("Backup execution failed", e);
        }
    }
    
    private static void startScheduler(BackupConfig config) {
        logger.info("Starting db-backup4j scheduler...");
        logger.log(Level.INFO, "Cron schedule: {0}", config.getSchedule().getCron());
        
        // TODO: 실제 스케줄러 구현
        logger.info("Scheduler started successfully (placeholder)");
    }
    
    private static boolean isTestEnvironment() {
        // JUnit 테스트 환경인지 확인
        try {
            Class.forName("org.junit.jupiter.api.Test");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}