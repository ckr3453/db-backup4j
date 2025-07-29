package io.backup4j.core.scheduler;

import io.backup4j.core.config.BackupConfig;
import io.backup4j.core.config.CronValidator;
import io.backup4j.core.database.DatabaseBackupExecutor;
import io.backup4j.core.validation.BackupResult;
import io.backup4j.core.exception.SchedulerStartException;
import io.backup4j.core.exception.BackupExecutionException;
import io.backup4j.core.util.Constants;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 백업 스케줄러 구현
 * 자체 구현한 cron 파서와 ScheduledExecutorService를 사용하여 주기적 백업 실행
 */
public class SimpleBackupScheduler {
    
    private final BackupConfig config;
    private final ScheduledExecutorService scheduler;
    
    private ScheduledFuture<?> currentTask;
    private volatile boolean running;
    
    public SimpleBackupScheduler(BackupConfig config) {
        this.config = config;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "backup-scheduler");
            t.setDaemon(true);
            return t;
        });
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
            if (!scheduler.awaitTermination(Constants.SCHEDULER_SHUTDOWN_TIMEOUT_SEC, Constants.SCHEDULER_SHUTDOWN_TIMEOUT_UNIT)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
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
                        executeBackup();
                    } finally {
                        // 백업 완료 후 다음 실행 시간 스케줄링
                        try {
                            scheduleNext();
                        } catch (Exception ex) {
                            running = false;
                        }
                    }
                }, delayMillis, TimeUnit.MILLISECONDS);
            } else {
                running = false;
            }
        } catch (Exception e) {
            running = false;
            throw new SchedulerStartException("Failed to schedule next execution", e);
        }
    }
    
    /**
     * cron 표현식을 파싱하여 다음 실행 시간을 계산합니다.
     * 
     * @param cronExpression cron 표현식 (분 시 일 월 요일)
     * @param from 계산 기준 시간
     * @return 다음 실행 시간, 계산할 수 없으면 null
     */
    private LocalDateTime calculateNextExecution(String cronExpression, LocalDateTime from) {
        String[] fields = cronExpression.trim().split("\\s+");
        if (fields.length != 5) {
            return null;
        }
        
        // 간단한 패턴들만 지원 (실제 환경에서 많이 사용되는 것들)
        // * * * * * (매분)
        if ("* * * * *".equals(cronExpression)) {
            return from.plusMinutes(1).withSecond(0).withNano(0);
        }
        
        // 0 * * * * (매시 정각)
        if ("0 * * * *".equals(cronExpression)) {
            return from.plusHours(1).withMinute(0).withSecond(0).withNano(0);
        }
        
        // 0 0 * * * (매일 자정)
        if ("0 0 * * *".equals(cronExpression)) {
            return from.plusDays(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
        }
        
        // */5 * * * * (5분마다)
        if ("*/5 * * * *".equals(cronExpression)) {
            int currentMinute = from.getMinute();
            int nextMinute = ((currentMinute / 5) + 1) * 5;
            if (nextMinute >= 60) {
                return from.plusHours(1).withMinute(0).withSecond(0).withNano(0);
            } else {
                return from.withMinute(nextMinute).withSecond(0).withNano(0);
            }
        }
        
        // 단순한 숫자 패턴 처리 (예: "30 2 * * *" - 매일 2시 30분)
        try {
            int minute = parseField(fields[0], from.getMinute(), 0, 59);
            int hour = parseField(fields[1], from.getHour(), 0, 23);
            
            LocalDateTime next = from.withMinute(minute).withSecond(0).withNano(0);
            
            if (hour >= 0) {
                next = next.withHour(hour);
                // 이미 지난 시간이면 다음 날로
                if (!next.isAfter(from)) {
                    next = next.plusDays(1);
                }
            } else {
                // 시간이 *이면 다음 시간으로
                if (!next.isAfter(from)) {
                    next = next.plusHours(1);
                }
            }
            
            return next;
        } catch (Exception e) {
            // 복잡한 패턴은 지원하지 않음. 1분 후로 설정
            return from.plusMinutes(1).withSecond(0).withNano(0);
        }
    }
    
    /**
     * cron 필드를 파싱합니다.
     * 
     * @param field cron 필드 문자열
     * @param current 현재 값
     * @param min 최소값
     * @param max 최대값
     * @return 파싱된 값, *이면 -1 반환
     */
    private int parseField(String field, int current, int min, int max) {
        if ("*".equals(field)) {
            return -1; // * 표시
        }
        
        try {
            int value = Integer.parseInt(field);
            if (value >= min && value <= max) {
                return value;
            }
        } catch (NumberFormatException e) {
            // 숫자가 아니면 현재값 사용
        }
        
        return current;
    }
    
    /**
     * 스케줄러에 의해 예약된 백업을 실행합니다.
     */
    private void executeBackup() {
        try {
            DatabaseBackupExecutor executor = new DatabaseBackupExecutor();
            BackupResult result = executor.executeBackup(config);
        } catch (Exception e) {
            // 백업 실패해도 스케줄러는 계속 실행
        }
    }
}