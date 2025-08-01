package io.backup4j.core;

import io.backup4j.core.database.DatabaseBackupExecutor;
import io.backup4j.core.config.BackupConfig;
import io.backup4j.core.util.ConfigParser;
import io.backup4j.core.validation.ConfigValidator;
import io.backup4j.core.scheduler.SimpleBackupScheduler;
import io.backup4j.core.exception.SchedulerStartException;

import java.net.URL;


/**
 * db-backup4j 라이브러리의 메인 진입점
 * 설정 파일을 자동으로 감지하고 백업을 실행하는 통합 초기화 클래스입니다.
 */
public class DbBackup4jInitializer {

    private DbBackup4jInitializer() {
    }
    
    /**
     * 기본 설정 파일을 자동으로 감지하여 백업을 실행합니다.
     */
    public static void run() {
        run(null);
    }
    
    /**
     * 클래스패스에서 설정 파일을 찾아 백업을 실행합니다.
     * Spring Boot의 application.yml처럼 resources/ 폴더에서 찾습니다.
     * 
     * @param resourcePath 클래스패스 내 설정 파일 경로 (예: "db-backup4j.yml")
     */
    public static void runFromClasspath(String resourcePath) {
        URL resource = DbBackup4jInitializer.class.getClassLoader().getResource(resourcePath);
        if (resource == null) {
            throw new RuntimeException("Configuration file not found in classpath: " + resourcePath);
        }
        run(resource.getPath());
    }
    
    /**
     * 지정된 설정 파일로 백업을 실행합니다.
     * 
     * @param configPath 설정 파일 경로 (null인 경우 자동 감지)
     */
    public static void run(String configPath) {
        try {
            // 1. 설정 파일 로드
            BackupConfig config;
            if (configPath != null && !configPath.trim().isEmpty()) {
                config = ConfigParser.parseConfigFile(configPath);
            } else {
                config = ConfigParser.autoDetectAndParse();
            }
            
            // 2. 설정 검증
            ConfigValidator.ValidationResult result = ConfigValidator.validate(config);
            if (!result.isValid()) {
                throw new RuntimeException("Invalid configuration");
            }
            
            // 3. 스케줄 확인 후 실행 방식 결정
            if (config.getSchedule() != null && config.getSchedule().isEnabled()) {
                startScheduler(config);
            } else {
                executeBackup(config);
            }
            
        } catch (Exception e) {
            throw new RuntimeException("Backup execution failed", e);
        }
    }

    /**
     * 일회성 백업을 실행합니다.
     * 
     * @param config 백업 설정
     */
    private static void executeBackup(BackupConfig config) {
        
        try {
            // 실제 백업 실행
            DatabaseBackupExecutor executor = new DatabaseBackupExecutor();
            executor.executeBackup(config);
        } catch (Exception e) {
            throw new RuntimeException("Backup execution failed", e);
        }
    }
    
    /**
     * 스케줄러를 시작하여 주기적인 백업을 실행합니다.
     * 
     * @param config 백업 설정
     */
    private static void startScheduler(BackupConfig config) {
        
        try {
            SimpleBackupScheduler scheduler = new SimpleBackupScheduler(config);
            scheduler.start();
            
            // JVM 종료 시 스케줄러 정리
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                if (scheduler.isRunning()) {
                    scheduler.stop();
                }
            }));
            
            // 스케줄러가 종료될 때까지 메인 스레드 대기
            scheduler.awaitTermination();
            
        } catch (SchedulerStartException e) {
            throw new RuntimeException("Scheduler start failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Scheduler interrupted", e);
        }
    }
    
}