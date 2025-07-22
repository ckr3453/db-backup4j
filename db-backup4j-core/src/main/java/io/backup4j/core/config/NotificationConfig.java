package io.backup4j.core.config;

import io.backup4j.core.notification.WebhookChannel;

import java.util.List;
import java.util.Map;

/**
 * 백업 완료 알림 설정을 관리하는 클래스
 * 이메일 알림과 웹훅 알림을 통합하여 관리합니다
 */
public class NotificationConfig {
    private final boolean enabled;
    private final EmailConfig email;
    private final WebhookConfig webhook;
    
    private NotificationConfig(Builder builder) {
        this.enabled = builder.enabled;
        this.email = builder.email;
        this.webhook = builder.webhook;
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * 알림 기능 활성화 여부를 반환합니다
     * 
     * @return 알림 활성화 여부
     */
    public boolean isEnabled() {
        return enabled;
    }
    
    /**
     * 이메일 알림 설정을 반환합니다
     * 
     * @return 이메일 알림 설정
     */
    public EmailConfig getEmail() {
        return email;
    }
    
    /**
     * 웹훅 알림 설정을 반환합니다
     * 
     * @return 웹훅 알림 설정
     */
    public WebhookConfig getWebhook() {
        return webhook;
    }
    
    /**
     * 활성화된 알림 방법이 있는지 확인합니다
     * 
     * @return 활성화된 알림 방법이 있으면 true
     */
    public boolean hasEnabledNotifiers() {
        return enabled && ((email != null && email.isEnabled()) || (webhook != null && webhook.isEnabled()));
    }
    
    public static class Builder {
        private boolean enabled = ConfigDefaults.DEFAULT_NOTIFICATION_ENABLED;
        private EmailConfig email;
        private WebhookConfig webhook;
        
        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }
        
        public Builder email(EmailConfig email) {
            this.email = email;
            return this;
        }
        
        public Builder webhook(WebhookConfig webhook) {
            this.webhook = webhook;
            return this;
        }
        
        public NotificationConfig build() {
            return new NotificationConfig(this);
        }
    }
    
    /**
     * 이메일 알림 설정을 관리하는 내부 클래스
     */
    public static class EmailConfig {
        private final boolean enabled;
        private final SmtpConfig smtp;
        private final String username;
        private final String password;
        private final List<String> recipients;
        private final String subject;
        private final String template;
        
        private EmailConfig(Builder builder) {
            this.enabled = builder.enabled;
            this.smtp = builder.smtp;
            this.username = builder.username;
            this.password = builder.password;
            this.recipients = builder.recipients;
            this.subject = builder.subject;
            this.template = builder.template;
        }
        
        public static Builder builder() {
            return new Builder();
        }
        
        public boolean isEnabled() {
            return enabled;
        }
        
        public SmtpConfig getSmtp() {
            return smtp;
        }
        
        public String getUsername() {
            return username;
        }
        
        public String getPassword() {
            return password;
        }
        
        public List<String> getRecipients() {
            return recipients;
        }
        
        public String getSubject() {
            return subject;
        }
        
        public String getTemplate() {
            return template;
        }
        
        public static class Builder {
            private boolean enabled = ConfigDefaults.DEFAULT_EMAIL_NOTIFICATION_ENABLED;
            private SmtpConfig smtp;
            private String username;
            private String password;
            private List<String> recipients;
            private String subject = ConfigDefaults.DEFAULT_EMAIL_SUBJECT;
            private String template = ConfigDefaults.DEFAULT_EMAIL_TEMPLATE;
            
            public Builder enabled(boolean enabled) {
                this.enabled = enabled;
                return this;
            }
            
            public Builder smtp(SmtpConfig smtp) {
                this.smtp = smtp;
                return this;
            }
            
            public Builder username(String username) {
                this.username = username;
                return this;
            }
            
            public Builder password(String password) {
                this.password = password;
                return this;
            }
            
            public Builder recipients(List<String> recipients) {
                this.recipients = recipients;
                return this;
            }
            
            public Builder subject(String subject) {
                this.subject = subject;
                return this;
            }
            
            public Builder template(String template) {
                this.template = template;
                return this;
            }
            
            public EmailConfig build() {
                return new EmailConfig(this);
            }
        }
    }
    
    /**
     * 웹훅 알림 설정을 관리하는 내부 클래스
     */
    public static class WebhookConfig {
        private final boolean enabled;
        private final String url;
        private final WebhookChannel channel;
        private final int timeout;
        private final int retryCount;
        private final Map<String, String> headers;
        private final boolean useRichFormat;
        
        private WebhookConfig(Builder builder) {
            this.enabled = builder.enabled;
            this.url = builder.url;
            this.channel = builder.channel;
            this.timeout = builder.timeout;
            this.retryCount = builder.retryCount;
            this.headers = builder.headers;
            this.useRichFormat = builder.useRichFormat;
        }
        
        public static Builder builder() {
            return new Builder();
        }
        
        public boolean isEnabled() {
            return enabled;
        }
        
        public String getUrl() {
            return url;
        }
        
        public WebhookChannel getChannel() {
            return channel;
        }
        
        public int getTimeout() {
            return timeout;
        }
        
        public int getRetryCount() {
            return retryCount;
        }
        
        public Map<String, String> getHeaders() {
            return headers;
        }
        
        public boolean isUseRichFormat() {
            return useRichFormat;
        }
        
        /**
         * 웹훅 채널을 자동으로 감지하여 반환합니다
         * 
         * @return 감지된 웹훅 채널
         */
        public WebhookChannel getEffectiveChannel() {
            if (channel != null) {
                return channel;
            }
            return WebhookChannel.fromUrl(url);
        }
        
        public static class Builder {
            private boolean enabled = ConfigDefaults.DEFAULT_WEBHOOK_NOTIFICATION_ENABLED;
            private String url;
            private WebhookChannel channel;
            private int timeout = ConfigDefaults.DEFAULT_WEBHOOK_TIMEOUT;
            private int retryCount = ConfigDefaults.DEFAULT_WEBHOOK_RETRY_COUNT;
            private Map<String, String> headers;
            private boolean useRichFormat = ConfigDefaults.DEFAULT_WEBHOOK_USE_RICH_FORMAT;
            
            public Builder enabled(boolean enabled) {
                this.enabled = enabled;
                return this;
            }
            
            public Builder url(String url) {
                this.url = url;
                return this;
            }
            
            public Builder channel(WebhookChannel channel) {
                this.channel = channel;
                return this;
            }
            
            public Builder timeout(int timeout) {
                this.timeout = timeout;
                return this;
            }
            
            public Builder retryCount(int retryCount) {
                this.retryCount = retryCount;
                return this;
            }
            
            public Builder headers(Map<String, String> headers) {
                this.headers = headers;
                return this;
            }
            
            public Builder useRichFormat(boolean useRichFormat) {
                this.useRichFormat = useRichFormat;
                return this;
            }
            
            public WebhookConfig build() {
                return new WebhookConfig(this);
            }
        }
    }
    
    /**
     * SMTP 서버 연결 정보를 담는 클래스
     */
    public static class SmtpConfig {
        private final String host;
        private final int port;
        private final boolean useTls;
        private final boolean useAuth;
        
        private SmtpConfig(Builder builder) {
            this.host = builder.host;
            this.port = builder.port;
            this.useTls = builder.useTls;
            this.useAuth = builder.useAuth;
        }
        
        public static Builder builder() {
            return new Builder();
        }
        
        public String getHost() {
            return host;
        }
        
        public int getPort() {
            return port;
        }
        
        public boolean isUseTls() {
            return useTls;
        }
        
        public boolean isUseAuth() {
            return useAuth;
        }
        
        public static class Builder {
            private String host;
            private int port = ConfigDefaults.DEFAULT_SMTP_PORT;
            private boolean useTls = ConfigDefaults.DEFAULT_SMTP_USE_TLS;
            private boolean useAuth = ConfigDefaults.DEFAULT_SMTP_USE_AUTH;
            
            public Builder host(String host) {
                this.host = host;
                return this;
            }
            
            public Builder port(int port) {
                this.port = port;
                return this;
            }
            
            public Builder useTls(boolean useTls) {
                this.useTls = useTls;
                return this;
            }
            
            public Builder useAuth(boolean useAuth) {
                this.useAuth = useAuth;
                return this;
            }
            
            public SmtpConfig build() {
                return new SmtpConfig(this);
            }
        }
    }
}