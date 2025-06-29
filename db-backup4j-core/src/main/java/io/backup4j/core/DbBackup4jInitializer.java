package io.backup4j.core;

import io.backup4j.core.config.BackupConfig;
import io.backup4j.core.config.ConfigParser;
import io.backup4j.core.config.ConfigValidator;

public class DbBackup4jInitializer {
    
    public static void run() {
        run(null);
    }
    
    public static void run(String configPath) {
        try {
            System.out.println("Starting DB Backup4j...");
            
            // 1. 설정 파일 로드
            BackupConfig config;
            if (configPath != null && !configPath.trim().isEmpty()) {
                config = ConfigParser.parseConfigFile(configPath);
                System.out.println("Using config file: " + configPath);
            } else {
                config = ConfigParser.autoDetectAndParse();
                System.out.println("Auto-detected configuration file");
            }
            
            // 2. 설정 검증
            ConfigValidator.ValidationResult result = ConfigValidator.validate(config);
            if (!result.isValid()) {
                System.err.println("Configuration validation failed:");
                for (String error : result.getErrors()) {
                    System.err.println("  - " + error);
                }
                throw new RuntimeException("Invalid configuration");
            }
            
            System.out.println("Configuration validated successfully");
            
            // 3. 스케줄 확인 후 실행 방식 결정
            if (config.getSchedule() != null && config.getSchedule().isEnabled()) {
                System.out.println("Schedule enabled - starting scheduler");
                startScheduler(config);
            } else {
                System.out.println("Schedule disabled - running one-time backup");
                executeBackup(config);
            }
            
            System.out.println("DB Backup4j started successfully");
            
        } catch (Exception e) {
            System.err.println("Backup failed: " + e.getMessage());
            throw new RuntimeException("Backup execution failed", e);
        }
    }
    
    
    private static void executeBackup(BackupConfig config) {
        System.out.println("Executing database backup...");
        
        // TODO: 실제 백업 로직 구현
        System.out.println("Database: " + config.getDatabase().getType() + " at " + config.getDatabase().getHost());
        System.out.println("Local backup enabled: " + config.getLocal().isEnabled());
        System.out.println("Email backup enabled: " + config.getEmail().isEnabled());
        System.out.println("S3 backup enabled: " + config.getS3().isEnabled());
        
        // 임시 구현
        System.out.println("Backup executed successfully (placeholder)");
    }
    
    private static void startScheduler(BackupConfig config) {
        System.out.println("Starting backup scheduler...");
        System.out.println("Daily schedule: " + config.getSchedule().getDaily());
        
        // TODO: 실제 스케줄러 구현
        System.out.println("Scheduler started successfully (placeholder)");
    }
}