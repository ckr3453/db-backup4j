package io.backup4j.core.notification;

import io.backup4j.core.config.NotificationConfig;
import io.backup4j.core.validation.BackupResult;
import io.backup4j.core.exception.WebhookNotificationException;
import io.backup4j.core.util.Constants;
import io.backup4j.core.util.HttpResponseReader;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.logging.Logger;

/**
 * 웹훅 알림을 전송하는 구현체
 * 다양한 플랫폼의 웹훅을 지원하며 재시도 및 타임아웃 기능을 제공
 */
public class WebhookNotifier {
    
    private static final Logger logger = Logger.getLogger(WebhookNotifier.class.getName());
    
    /**
     * 웹훅 알림을 전송합니다
     *
     * @param title 알림 제목
     * @param message 알림 메시지
     * @param result 백업 결과
     * @param config 웹훅 설정
     * @throws Exception 전송 실패 시
     */
    public void sendNotification(String title, String message, BackupResult result, 
                               NotificationConfig.WebhookConfig config) throws Exception {
        
        if (config.getUrl() == null || config.getUrl().trim().isEmpty()) {
            throw new IllegalArgumentException("Webhook URL is not configured");
        }
        
        logger.info("Webhook notification sending started: " + config.getUrl());
        
        WebhookChannel channel = config.getEffectiveChannel();
        logger.info("Detected webhook channel: " + channel.getName());
        
        // 페이로드 생성
        String payload = createPayload(title, message, result, config, channel);
        
        // 재시도 로직으로 전송
        Exception lastException = null;
        int maxRetries = Math.max(1, config.getRetryCount());
        
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                sendHttpRequest(config.getUrl(), payload, config, channel);
                logger.info("Webhook notification sending completed (attempt " + attempt + "/" + maxRetries + ")");
                return;
                
            } catch (Exception e) {
                lastException = e;
                logger.warning("Webhook sending failed (attempt " + attempt + "/" + maxRetries + "): " + e.getMessage());
                
                if (attempt < maxRetries) {
                    // 재시도 전 대기 (지수 백오프)
                    try {
                        Thread.sleep(Constants.DEFAULT_WEBHOOK_RETRY_DELAY_MS * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new Exception("Thread interrupted during webhook transmission", ie);
                    }
                }
            }
        }
        
        throw new Exception("Webhook transmission failed after maximum retry attempts", lastException);
    }
    
    /**
     * 웹훅 페이로드를 생성합니다
     * 
     * @param title 제목
     * @param message 메시지
     * @param result 백업 결과
     * @param config 웹훅 설정
     * @param channel 웹훅 채널
     * @return JSON 페이로드
     */
    private String createPayload(String title, String message, BackupResult result, 
                               NotificationConfig.WebhookConfig config, WebhookChannel channel) {
        
        if (config.isUseRichFormat()) {
            boolean isSuccess = result.getStatus() == BackupResult.Status.SUCCESS;
            return channel.createRichPayload(title, message, isSuccess);
        } else {
            // 간단한 텍스트 메시지
            String combinedMessage = title + "\\n\\n" + message;
            return channel.createPayload(combinedMessage);
        }
    }
    
    /**
     * HTTP 요청을 전송합니다
     * 
     * @param webhookUrl 웹훅 URL
     * @param payload JSON 페이로드
     * @param config 웹훅 설정
     * @param channel 웹훅 채널
     * @throws Exception 전송 실패 시
     */
    private void sendHttpRequest(String webhookUrl, String payload, 
                               NotificationConfig.WebhookConfig config, WebhookChannel channel) throws Exception {
        
        URL url = new URL(webhookUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        
        try {
            // 연결 설정
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", channel.getContentType());
            connection.setRequestProperty("User-Agent", Constants.USER_AGENT);
            connection.setConnectTimeout(config.getTimeout() * 1000);
            connection.setReadTimeout(config.getTimeout() * 1000);
            connection.setDoOutput(true);
            
            // 커스텀 헤더 설정
            if (config.getHeaders() != null) {
                for (Map.Entry<String, String> header : config.getHeaders().entrySet()) {
                    connection.setRequestProperty(header.getKey(), header.getValue());
                }
            }
            
            // 페이로드 전송
            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = payload.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }
            
            // 응답 확인
            int responseCode = connection.getResponseCode();
            String responseMessage = connection.getResponseMessage();
            
            logger.info("Webhook response: " + responseCode + " " + responseMessage);
            
            if (responseCode < 200 || responseCode >= 300) {
                String errorBody = readErrorResponse(connection);
                throw new Exception("Webhook transmission failed: " + responseCode + " " + responseMessage + 
                                  (errorBody != null ? " - " + errorBody : ""));
            }
            
            // 성공 응답 로깅
            String successBody = readSuccessResponse(connection);
            if (successBody != null && !successBody.trim().isEmpty()) {
                logger.info("Webhook response body: " + successBody);
            }
            
        } catch (WebhookNotificationException e) {
            throw e;
        } catch (Exception e) {
            throw new WebhookNotificationException("Failed to send webhook request", e);
        } finally {
            connection.disconnect();
        }
    }
    
    /**
     * 오류 응답 본문을 읽습니다
     * 
     * @param connection HTTP 연결
     * @return 오류 응답 본문
     */
    private String readErrorResponse(HttpURLConnection connection) {
        try {
            InputStream errorStream = connection.getErrorStream();
            return HttpResponseReader.readResponse(errorStream, Constants.MAX_ERROR_RESPONSE_SIZE);
        } catch (Exception e) {
            logger.warning("Failed to read error response: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * 성공 응답 본문을 읽습니다
     * 
     * @param connection HTTP 연결
     * @return 성공 응답 본문
     */
    private String readSuccessResponse(HttpURLConnection connection) {
        try {
            InputStream inputStream = connection.getInputStream();
            return HttpResponseReader.readResponse(inputStream, Constants.MAX_SUCCESS_RESPONSE_SIZE);
        } catch (Exception e) {
            logger.warning("Failed to read success response: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * 웹훅 연결을 테스트합니다
     * 
     * @param config 웹훅 설정
     * @return 연결 성공 여부
     */
    public boolean testConnection(NotificationConfig.WebhookConfig config) {
        try {
            WebhookChannel channel = config.getEffectiveChannel();
            String testPayload = channel.createPayload("db-backup4j connection test");
            
            sendHttpRequest(config.getUrl(), testPayload, config, channel);
            return true;
            
        } catch (Exception e) {
            logger.warning("Webhook connection test failed: " + e.getMessage());
            return false;
        }
    }
    
}