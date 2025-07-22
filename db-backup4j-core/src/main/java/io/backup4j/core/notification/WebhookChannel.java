package io.backup4j.core.notification;

/**
 * 웹훅 채널별 포맷을 정의하는 열거형
 * 각 플랫폼에 맞는 JSON 메시지 포맷을 제공함
 */
public enum WebhookChannel {
    /**
     * Slack 웹훅 채널
     */
    SLACK("slack", "text", "application/json"),
    
    /**
     * Discord 웹훅 채널
     */
    DISCORD("discord", "content", "application/json"),
    
    /**
     * Microsoft Teams 웹훅 채널
     */
    TEAMS("teams", "text", "application/json"),
    
    /**
     * Mattermost 웹훅 채널
     */
    MATTERMOST("mattermost", "text", "application/json"),
    
    /**
     * 범용 웹훅 채널 (기본값)
     */
    GENERIC("generic", "message", "application/json");
    
    private final String name;
    private final String messageKey;
    private final String contentType;
    
    /**
     * WebhookChannel 생성자
     * 
     * @param name 채널 이름
     * @param messageKey JSON 메시지 키
     * @param contentType HTTP Content-Type
     */
    WebhookChannel(String name, String messageKey, String contentType) {
        this.name = name;
        this.messageKey = messageKey;
        this.contentType = contentType;
    }
    
    /**
     * 채널 이름을 반환합니다
     * 
     * @return 채널 이름
     */
    public String getName() {
        return name;
    }
    
    /**
     * 메시지 키를 반환합니다
     * 
     * @return JSON 메시지 키
     */
    public String getMessageKey() {
        return messageKey;
    }
    
    /**
     * Content-Type을 반환합니다
     * 
     * @return HTTP Content-Type
     */
    public String getContentType() {
        return contentType;
    }
    
    /**
     * 메시지를 해당 채널 포맷에 맞는 JSON 페이로드로 변환합니다
     * 
     * @param message 전송할 메시지
     * @return JSON 형태의 페이로드
     */
    public String createPayload(String message) {
        if (message == null) {
            message = "";
        }
        
        String escapedMessage = escapeJsonString(message);
        return String.format("{\"%s\": \"%s\"}", messageKey, escapedMessage);
    }
    
    /**
     * 백업 결과를 기반으로 리치 메시지 페이로드를 생성합니다
     * 
     * @param title 메시지 제목
     * @param message 메시지 내용
     * @param isSuccess 성공 여부
     * @return 리치 메시지 JSON 페이로드
     */
    public String createRichPayload(String title, String message, boolean isSuccess) {
        String escapedTitle = escapeJsonString(title);
        String escapedMessage = escapeJsonString(message);
        
        switch (this) {
            case SLACK:
                return createSlackRichPayload(escapedTitle, escapedMessage, isSuccess);
            case DISCORD:
                return createDiscordRichPayload(escapedTitle, escapedMessage, isSuccess);
            case TEAMS:
                return createTeamsRichPayload(escapedTitle, escapedMessage, isSuccess);
            default:
                return createPayload(title + "\\n" + message);
        }
    }
    
    /**
     * URL 패턴을 기반으로 웹훅 채널을 자동으로 감지합니다
     * 
     * @param url 웹훅 URL
     * @return 감지된 웹훅 채널
     */
    public static WebhookChannel fromUrl(String url) {
        if (url == null || url.trim().isEmpty()) {
            return GENERIC;
        }
        
        String lowerUrl = url.toLowerCase();
        
        if (lowerUrl.contains("hooks.slack.com")) {
            return SLACK;
        } else if (lowerUrl.contains("discord.com/api/webhooks") || lowerUrl.contains("discordapp.com/api/webhooks")) {
            return DISCORD;
        } else if (lowerUrl.contains("teams.microsoft.com") || lowerUrl.contains("outlook.office.com")) {
            return TEAMS;
        } else if (lowerUrl.contains("mattermost")) {
            return MATTERMOST;
        }
        
        return GENERIC;
    }
    
    /**
     * 문자열에서 JSON 특수 문자를 이스케이프 처리합니다
     * 
     * @param str 이스케이프할 문자열
     * @return 이스케이프된 문자열
     */
    private String escapeJsonString(String str) {
        if (str == null) {
            return "";
        }
        
        return str.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }
    
    /**
     * Slack 리치 메시지 페이로드를 생성합니다
     */
    private String createSlackRichPayload(String title, String message, boolean isSuccess) {
        String color = isSuccess ? "good" : "danger";
        String emoji = isSuccess ? ":white_check_mark:" : ":x:";
        
        return String.format("{\n" +
            "  \"text\": \"%s %s\",\n" +
            "  \"attachments\": [\n" +
            "    {\n" +
            "      \"color\": \"%s\",\n" +
            "      \"text\": \"%s\",\n" +
            "      \"ts\": %d\n" +
            "    }\n" +
            "  ]\n" +
            "}", emoji, title, color, message, System.currentTimeMillis() / 1000);
    }
    
    /**
     * Discord 리치 메시지 페이로드를 생성합니다
     */
    private String createDiscordRichPayload(String title, String message, boolean isSuccess) {
        int color = isSuccess ? 0x00ff00 : 0xff0000; // 녹색 또는 빨간색
        
        return String.format("{\n" +
            "  \"embeds\": [\n" +
            "    {\n" +
            "      \"title\": \"%s\",\n" +
            "      \"description\": \"%s\",\n" +
            "      \"color\": %d,\n" +
            "      \"timestamp\": \"%s\"\n" +
            "    }\n" +
            "  ]\n" +
            "}", title, message, color, java.time.Instant.now().toString());
    }
    
    /**
     * Teams 리치 메시지 페이로드를 생성합니다
     */
    private String createTeamsRichPayload(String title, String message, boolean isSuccess) {
        String color = isSuccess ? "good" : "attention";
        
        return String.format("{\n" +
            "  \"@type\": \"MessageCard\",\n" +
            "  \"@context\": \"https://schema.org/extensions\",\n" +
            "  \"summary\": \"%s\",\n" +
            "  \"themeColor\": \"%s\",\n" +
            "  \"title\": \"%s\",\n" +
            "  \"text\": \"%s\"\n" +
            "}", title, color, title, message);
    }
}