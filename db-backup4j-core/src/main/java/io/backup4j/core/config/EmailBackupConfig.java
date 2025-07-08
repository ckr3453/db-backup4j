package io.backup4j.core.config;

import java.util.List;

public class EmailBackupConfig {
    private final boolean enabled;
    private final SmtpConfig smtp;
    private final String username;
    private final String password;
    private final List<String> recipients;

    private EmailBackupConfig(Builder builder) {
        this.enabled = builder.enabled;
        this.smtp = builder.smtp;
        this.username = builder.username;
        this.password = builder.password;
        this.recipients = builder.recipients;
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

    public static class Builder {
        private boolean enabled = ConfigDefaults.DEFAULT_EMAIL_BACKUP_ENABLED;
        private SmtpConfig smtp;
        private String username;
        private String password;
        private List<String> recipients;

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

        public EmailBackupConfig build() {
            return new EmailBackupConfig(this);
        }
    }

    public static class SmtpConfig {
        private final String host;
        private final int port;

        private SmtpConfig(Builder builder) {
            this.host = builder.host;
            this.port = builder.port;
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

        public static class Builder {
            private String host;
            private int port = ConfigDefaults.DEFAULT_SMTP_PORT;

            public Builder host(String host) {
                this.host = host;
                return this;
            }

            public Builder port(int port) {
                this.port = port;
                return this;
            }

            public SmtpConfig build() {
                return new SmtpConfig(this);
            }
        }
    }
}