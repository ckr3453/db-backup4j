package io.backup4j.core.config;

import io.backup4j.core.database.DatabaseType;

/**
 * 데이터베이스 연결 설정을 관리하는 클래스
 * 데이터베이스 타입, 호스트, 포트, 사용자 정보 등을 포함함
 */
public class DatabaseConfig {
    private final DatabaseType type;
    private final String host;
    private final int port;
    private final String name;
    private final String username;
    private final String password;

    private DatabaseConfig(Builder builder) {
        this.type = builder.type;
        this.host = builder.host;
        this.port = builder.port;
        this.name = builder.name;
        this.username = builder.username;
        this.password = builder.password;
    }

    public static Builder builder() {
        return new Builder();
    }

    public DatabaseType getType() {
        return type;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public String getName() {
        return name;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public static class Builder {
        private DatabaseType type;
        private String host = ConfigDefaults.DEFAULT_DATABASE_HOST;
        private int port = ConfigDefaults.DEFAULT_MYSQL_PORT;
        private String name;
        private String username;
        private String password;

        public Builder type(DatabaseType type) {
            this.type = type;
            return this;
        }

        public Builder host(String host) {
            this.host = host;
            return this;
        }

        public Builder port(int port) {
            this.port = port;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
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

        public DatabaseConfig build() {
            return new DatabaseConfig(this);
        }
    }
}