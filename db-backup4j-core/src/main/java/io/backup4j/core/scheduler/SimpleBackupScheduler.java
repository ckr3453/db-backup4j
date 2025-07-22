package io.backup4j.core.scheduler;

import io.backup4j.core.config.BackupConfig;
import io.backup4j.core.database.DatabaseBackupExecutor;
import io.backup4j.core.validation.BackupResult;
import io.backup4j.core.exception.SchedulerStartException;
import io.backup4j.core.exception.BackupExecutionException;
import io.backup4j.core.util.Constants;
import com.cronutils.model.Cron;
import com.cronutils.model.CronType;
import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.model.time.ExecutionTime;
import com.cronutils.parser.CronParser;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * 백업 스케줄러 구현
 * cron-utils와 ScheduledExecutorService를 사용하여 주기적 백업 실행
 */
public class SimpleBackupScheduler {
    
    private static final Logger logger = Logger.getLogger(SimpleBackupScheduler.class.getName());
    
    private final BackupConfig config;
    private final ScheduledExecutorService scheduler;
    private final CronParser cronParser;
    
    private ScheduledFuture<?> currentTask;
    private volatile boolean running;
    
    public SimpleBackupScheduler(BackupConfig config) {
        this.config = config;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "backup-scheduler");
            t.setDaemon(true);
            return t;
        });
        this.cronParser = new CronParser(CronDefinitionBuilder.instanceDefinitionFor(CronType.UNIX));
        this.running = false;
    }
    
    /**
     * 스케줄러 시작
     */
    public void start() throws SchedulerStartException {
        if (running) {
            logger.info("Scheduler is already running.");
            return;
        }
        
        if (config.getSchedule() == null || !config.getSchedule().isEnabled()) {
            logger.info("Schedule is disabled.");
            return;
        }
        
        String cronExpression = config.getSchedule().getCron();
        if (cronExpression == null || cronExpression.trim().isEmpty()) {
            logger.warning("Cron expression is not configured.");
            return;
        }
        
        try {
            running = true;
            scheduleNext();
            logger.info("Scheduler has started. cron: " + cronExpression);
        } catch (Exception e) {
            running = false;
            logger.severe("Scheduler start failed: " + e.getMessage());
            throw new SchedulerStartException("Scheduler start failed", e);
        }
    }
    
    /**
     * 스케줄러 중지
     */
    public void stop() {
        if (!running) {
            return;
        }
        
        running = false;
        
        if (currentTask != null) {
            currentTask.cancel(false);
            currentTask = null;
        }
        
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(Constants.SCHEDULER_SHUTDOWN_TIMEOUT_SEC, Constants.SCHEDULER_SHUTDOWN_TIMEOUT_UNIT)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        logger.info("Scheduler has been stopped.");
    }
    
    /**
     * 스케줄러 실행 상태 확인
     */
    public boolean isRunning() {
        return running && !scheduler.isShutdown();
    }
    
    /**
     * cron 표현식을 이용해 다음 실행 시간을 계산하고 스케줄링합니다.
     */
    private void scheduleNext() throws SchedulerStartException {
        if (!running) {
            return;
        }
        
        try {
            String cronExpression = config.getSchedule().getCron();
            Cron cron = cronParser.parse(cronExpression);
            ExecutionTime executionTime = ExecutionTime.forCron(cron);
            
            ZonedDateTime now = ZonedDateTime.now();
            Optional<ZonedDateTime> nextExecution = executionTime.nextExecution(now);
            
            if (nextExecution.isPresent()) {
                ZonedDateTime nextTime = nextExecution.get();
                Duration delay = Duration.between(now, nextTime);
                
                logger.info("Next backup execution time: " + nextTime);
                
                currentTask = scheduler.schedule(() -> {
                    try {
                        executeBackup();
                    } finally {
                        // 백업 완료 후 다음 실행 시간 스케줄링
                        try {
                            scheduleNext();
                        } catch (Exception ex) {
                            logger.severe("Next backup scheduling failed: " + ex.getMessage());
                            running = false;
                        }
                    }
                }, delay.toMillis(), TimeUnit.MILLISECONDS);
            } else {
                logger.warning("Cannot calculate next execution time.");
                running = false;
            }
        } catch (Exception e) {
            logger.severe("Next execution time scheduling failed: " + e.getMessage());
            running = false;
            throw new SchedulerStartException("Failed to schedule next execution", e);
        }
    }
    
    /**
     * 스케줄러에 의해 예약된 백업을 실행합니다.
     */
    private void executeBackup() {
        try {
            logger.info("Scheduled backup execution started: " + ZonedDateTime.now());
            DatabaseBackupExecutor executor = new DatabaseBackupExecutor();
            BackupResult result = executor.executeBackup(config);
            
            if (result.isSuccess()) {
                logger.info("Scheduled backup execution completed: " + ZonedDateTime.now());
                logger.info("Number of backup files: " + result.getFiles().size());
            } else {
                logger.warning("Backup partially failed. Error count: " + result.getErrors().size());
                for (BackupResult.BackupError error : result.getErrors()) {
                    logger.warning("Backup error: " + error.getMessage());
                }
            }
        } catch (Exception e) {
            logger.severe("Error occurred during backup execution: " + e.getMessage());
            logger.severe("Stack trace: " + java.util.Arrays.toString(e.getStackTrace()));
            // 백업 실패해도 스케줄러는 계속 실행
        }
    }
}