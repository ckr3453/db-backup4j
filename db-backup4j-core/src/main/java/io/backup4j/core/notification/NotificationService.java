package io.backup4j.core.notification;

import io.backup4j.core.config.NotificationConfig;
import io.backup4j.core.validation.BackupResult;

import java.time.format.DateTimeFormatter;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * 통합 알림 서비스 클래스
 * 백업 완료 후 이메일과 웹훅을 통해 알림을 전송합니다
 */
public class NotificationService {
    
    private static final Logger logger = Logger.getLogger(NotificationService.class.getName());
    
    private final ExecutorService executorService;
    private final EmailNotifier emailNotifier;
    private final WebhookNotifier webhookNotifier;
    
    /**
     * NotificationService 생성자
     */
    public NotificationService() {
        this.executorService = Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "notification-service");
            t.setDaemon(true);
            return t;
        });
        this.emailNotifier = new EmailNotifier();
        this.webhookNotifier = new WebhookNotifier();
    }
    
    /**
     * 백업 완료 알림을 전송합니다
     * 
     * @param result 백업 결과
     * @param config 알림 설정
     */
    public void sendBackupNotification(BackupResult result, NotificationConfig config) {
        if (config == null || !config.isEnabled() || !config.hasEnabledNotifiers()) {
            logger.info("알림이 비활성화되어 있습니다.");
            return;
        }
        
        logger.info("백업 완료 알림 전송 시작...");
        
        // 알림 메시지 생성
        String title = createNotificationTitle(result);
        String message = createNotificationMessage(result);
        
        // 비동기로 알림 전송
        CompletableFuture<Void> emailFuture = CompletableFuture.runAsync(() -> {
            if (config.getEmail() != null && config.getEmail().isEnabled()) {
                try {
                    emailNotifier.sendNotification(title, message, result, config.getEmail());
                    logger.info("이메일 알림 전송 완료");
                } catch (Exception e) {
                    logger.warning("이메일 알림 전송 실패: " + e.getMessage());
                }
            }
        }, executorService);
        
        CompletableFuture<Void> webhookFuture = CompletableFuture.runAsync(() -> {
            if (config.getWebhook() != null && config.getWebhook().isEnabled()) {
                try {
                    webhookNotifier.sendNotification(title, message, result, config.getWebhook());
                    logger.info("웹훅 알림 전송 완료");
                } catch (Exception e) {
                    logger.warning("웹훅 알림 전송 실패: " + e.getMessage());
                }
            }
        }, executorService);
        
        // 모든 알림 완료 대기 (최대 30초)
        try {
            CompletableFuture.allOf(emailFuture, webhookFuture)
                .get(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            logger.warning("알림 전송 중 타임아웃 또는 오류 발생: " + e.getMessage());
        }
    }
    
    /**
     * 알림 제목을 생성합니다
     * 
     * @param result 백업 결과
     * @return 알림 제목
     */
    private String createNotificationTitle(BackupResult result) {
        boolean isSuccess = result.getStatus() == BackupResult.Status.SUCCESS;
        String status = isSuccess ? "성공" : "실패";
        String database = result.getMetadata() != null ? result.getMetadata().getDatabaseName() : "Unknown";
        
        return String.format("📊 DB 백업 %s - %s", status, database);
    }
    
    /**
     * 알림 메시지를 생성합니다
     * 
     * @param result 백업 결과
     * @return 알림 메시지
     */
    private String createNotificationMessage(BackupResult result) {
        StringBuilder message = new StringBuilder();
        
        // 기본 정보
        if (result.getMetadata() != null) {
            message.append("🗄️ 데이터베이스: ").append(result.getMetadata().getDatabaseName()).append("\\n");
            message.append("🖥️ 호스트: ").append(result.getMetadata().getDatabaseHost()).append("\\n");
            message.append("📊 타입: ").append(result.getMetadata().getDatabaseType()).append("\\n");
        }
        
        // 상태 정보
        message.append("✅ 상태: ").append(result.getStatus()).append("\\n");
        message.append("🕒 시작: ").append(formatDateTime(result.getStartTime())).append("\\n");
        message.append("🕒 완료: ").append(formatDateTime(result.getEndTime())).append("\\n");
        message.append("⏱️ 소요시간: ").append(formatDuration(result.getDuration())).append("\\n");
        
        // 백업 파일 정보
        if (!result.getFiles().isEmpty()) {
            message.append("\\n📁 백업 파일:\\n");
            for (BackupResult.BackupFile file : result.getFiles()) {
                message.append("• ").append(file.getFilePath().getFileName()).append(" (")
                       .append(formatFileSize(file.getFileSize())).append(") - ")
                       .append(file.getDestination()).append("\\n");
            }
        }
        
        // 압축 정보
        if (result.getMetadata() != null && result.getMetadata().isCompressed()) {
            double ratio = result.getMetadata().getCompressionRatio();
            message.append("\\n🗜️ 압축률: ").append(String.format("%.1f%%", (1.0 - ratio) * 100)).append("\\n");
        }
        
        // 체크섬 정보
        if (!result.getValidationResults().isEmpty()) {
            message.append("\\n🔍 체크섬 검증:\\n");
            for (io.backup4j.core.validation.ChecksumValidator.ValidationResult validation : result.getValidationResults()) {
                String status = validation.isValid() ? "✅ 성공" : "❌ 실패";
                message.append("• ").append(validation.getAlgorithm()).append(": ").append(status).append("\\n");
            }
        }
        
        // 오류 정보
        if (result.hasErrors()) {
            message.append("\\n⚠️ 오류 정보:\\n");
            for (BackupResult.BackupError error : result.getErrors()) {
                message.append("• ").append(error.getDestination()).append(": ").append(error.getMessage()).append("\\n");
            }
        }
        
        return message.toString();
    }
    
    /**
     * 날짜/시간을 포맷팅합니다
     * 
     * @param dateTime 날짜/시간
     * @return 포맷된 문자열
     */
    private String formatDateTime(java.time.LocalDateTime dateTime) {
        if (dateTime == null) return "N/A";
        return dateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }
    
    /**
     * 지속시간을 포맷팅합니다
     * 
     * @param duration 지속시간
     * @return 포맷된 문자열
     */
    private String formatDuration(java.time.Duration duration) {
        if (duration == null) return "N/A";
        
        long minutes = duration.toMinutes();
        long seconds = duration.getSeconds() % 60;
        
        if (minutes > 0) {
            return String.format("%d분 %d초", minutes, seconds);
        } else {
            return String.format("%d초", seconds);
        }
    }
    
    /**
     * 파일 크기를 포맷팅합니다
     * 
     * @param bytes 바이트 크기
     * @return 포맷된 문자열
     */
    private String formatFileSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        
        String[] units = {"B", "KB", "MB", "GB", "TB"};
        int unitIndex = 0;
        double size = bytes;
        
        while (size >= 1024 && unitIndex < units.length - 1) {
            size /= 1024;
            unitIndex++;
        }
        
        return String.format("%.1f %s", size, units[unitIndex]);
    }
    
    /**
     * 서비스를 종료합니다
     */
    public void shutdown() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        logger.info("알림 서비스가 종료되었습니다.");
    }
}