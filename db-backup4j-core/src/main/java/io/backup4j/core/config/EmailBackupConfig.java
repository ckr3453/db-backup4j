package io.backup4j.core.config;

import java.util.List;

public class EmailBackupConfig {
    private boolean enabled;
    private SmtpConfig smtp;
    private String username;
    private String password;
    private List<String> recipients;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public SmtpConfig getSmtp() {
        return smtp;
    }

    public void setSmtp(SmtpConfig smtp) {
        this.smtp = smtp;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public List<String> getRecipients() {
        return recipients;
    }

    public void setRecipients(List<String> recipients) {
        this.recipients = recipients;
    }

    public static class SmtpConfig {
        private String host;
        private int port;

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }
    }
}