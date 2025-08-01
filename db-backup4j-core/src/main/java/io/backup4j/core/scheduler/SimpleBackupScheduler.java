package io.backup4j.core.scheduler;

import com.cronutils.model.Cron;
import com.cronutils.model.CronType;
import com.cronutils.model.definition.CronDefinition;
import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.model.time.ExecutionTime;
import com.cronutils.parser.CronParser;
import io.backup4j.core.config.BackupConfig;
import io.backup4j.core.validation.CronValidator;
import io.backup4j.core.database.DatabaseBackupExecutor;
import io.backup4j.core.exception.SchedulerStartException;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CountDownLatch;

/**
 * 백업 스케줄러 구현
 * 자체 구현한 cron 파서와 ScheduledExecutorService를 사용하여 주기적 백업 실행
 */
public class SimpleBackupScheduler {
    public static final long SCHEDULER_SHUTDOWN_TIMEOUT_SEC = 5;

    private final BackupConfig config;
    private final CountDownLatch terminationLatch;
    private final ScheduledExecutorService scheduler;

    private volatile boolean running;
    private ScheduledFuture<?> currentTask;

    public SimpleBackupScheduler(BackupConfig config) {
        this.config = config;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "backup-scheduler");
            t.setDaemon(true);
            return t;
        });
        this.terminationLatch = new CountDownLatch(1);
        this.running = false;
    }
    
    /**
     * 스케줄러 시작
     */
    public void start() throws SchedulerStartException {
        if (running) {
            return;
        }
        
        if (config.getSchedule() == null || !config.getSchedule().isEnabled()) {
            return;
        }
        
        String cronExpression = config.getSchedule().getCron();
        if (cronExpression == null || cronExpression.trim().isEmpty()) {
            return;
        }
        
        try {
            running = true;
            scheduleNext();
        } catch (Exception e) {
            running = false;
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
            if (!scheduler.awaitTermination(SimpleBackupScheduler.SCHEDULER_SHUTDOWN_TIMEOUT_SEC, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        } finally {
            terminationLatch.countDown();
        }
        
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
            
            // cron 표현식 검증
            if (!CronValidator.isValid(cronExpression)) {
                throw new SchedulerStartException("Invalid cron expression: " + cronExpression);
            }
            
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime nextExecution = calculateNextExecution(cronExpression, now);
            
            if (nextExecution != null) {
                long delayMillis = ChronoUnit.MILLIS.between(now, nextExecution);
                
                currentTask = scheduler.schedule(() -> {
                    try {
                        DatabaseBackupExecutor executor = new DatabaseBackupExecutor();
                        executor.executeBackup(config);
                    } finally {
                        // 백업 완료 후 다음 실행 시간 스케줄링
                        try {
                            scheduleNext();
                        } catch (Exception ex) {
                            running = false;
                            terminationLatch.countDown();
                        }
                    }
                }, delayMillis, TimeUnit.MILLISECONDS);
            } else {
                running = false;
                terminationLatch.countDown();
            }
        } catch (Exception e) {
            running = false;
            terminationLatch.countDown();
            throw new SchedulerStartException("Failed to schedule next execution", e);
        }
    }
    
    /**
     * 스케줄러 종료까지 대기합니다.
     */
    public void awaitTermination() throws InterruptedException {
        terminationLatch.await();
    }

    /**
     * cron 표현식을 파싱하여 다음 실행 시간을 계산합니다.
     *
     * @param cronExpression cron 표현식 (분 시 일 월 요일)
     * @param from 계산 기준 시간
     * @return 다음 실행 시간, 계산할 수 없으면 null
     */
    private LocalDateTime calculateNextExecution(String cronExpression, LocalDateTime from) {
        if (cronExpression == null || cronExpression.trim().isEmpty()) {
            return null;
        }

        CronDefinition cronDefinition = CronDefinitionBuilder.instanceDefinitionFor(CronType.UNIX);
        CronParser cronParser = new CronParser(cronDefinition);

        Cron cron = cronParser.parse(cronExpression.trim());
        ExecutionTime executionTime = ExecutionTime.forCron(cron);

        ZonedDateTime zonedFrom = from.atZone(java.time.ZoneId.systemDefault());
        Optional<ZonedDateTime> nextExecution = executionTime.nextExecution(zonedFrom);

        return nextExecution.map(ZonedDateTime::toLocalDateTime).orElse(null);
    }
}