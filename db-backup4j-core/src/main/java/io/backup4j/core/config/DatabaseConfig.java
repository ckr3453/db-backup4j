package io.backup4j.core.config;

import io.backup4j.core.database.DatabaseType;

/**
 * 데이터베이스 연결 설정을 관리하는 클래스
 * JDBC URL 방식으로 연결 정보를 관리함
 */
public class DatabaseConfig {
    private final String url;
    private final String username;
    private final String password;
    private final DatabaseType type; // URL에서 자동 추론

    private DatabaseConfig(Builder builder) {
        this.url = builder.url;
        this.username = builder.username;
        this.password = builder.password;
        this.type = inferTypeFromUrl(builder.url);
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getUrl() {
        return url;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public DatabaseType getType() {
        return type;
    }

    /**
     * JDBC URL에서 데이터베이스 이름을 추출
     * 
     * @return 데이터베이스 이름
     */
    public String getName() {
        return extractDatabaseName(this.url);
    }

    /**
     * JDBC URL에서 호스트를 추출 (호환성을 위해 유지)
     * 
     * @return 호스트명
     */
    public String getHost() {
        return extractHost(this.url);
    }

    /**
     * JDBC URL에서 데이터베이스 이름을 추출하는 유틸리티 메서드
     * 
     * @param url JDBC URL
     * @return 데이터베이스 이름
     */
    private static String extractDatabaseName(String url) {
        if (url == null) return null;
        
        try {
            // MySQL: jdbc:mysql://host:port/dbname?params
            // PostgreSQL: jdbc:postgresql://host:port/dbname?params
            
            // "://" 이후 부분 찾기
            int schemeEnd = url.indexOf("://");
            if (schemeEnd == -1) return null;
            
            String remaining = url.substring(schemeEnd + 3);
            
            // 호스트:포트 부분 건너뛰기
            int pathStart = remaining.indexOf('/');
            if (pathStart == -1) return null;
            
            String pathPart = remaining.substring(pathStart + 1);
            
            // 쿼리 파라미터 제거
            int queryStart = pathPart.indexOf('?');
            if (queryStart != -1) {
                pathPart = pathPart.substring(0, queryStart);
            }
            
            return pathPart.isEmpty() ? null : pathPart;
            
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * JDBC URL에서 호스트를 추출하는 유틸리티 메서드
     * 
     * @param url JDBC URL
     * @return 호스트명
     */
    private static String extractHost(String url) {
        if (url == null) return null;
        
        try {
            // "://" 이후 부분 찾기
            int schemeEnd = url.indexOf("://");
            if (schemeEnd == -1) return null;
            
            String remaining = url.substring(schemeEnd + 3);
            
            // 호스트 부분 추출 (포트나 경로 전까지)
            int portStart = remaining.indexOf(':');
            int pathStart = remaining.indexOf('/');
            
            int end = -1;
            if (portStart != -1 && pathStart != -1) {
                end = Math.min(portStart, pathStart);
            } else if (portStart != -1) {
                end = portStart;
            } else if (pathStart != -1) {
                end = pathStart;
            }
            
            if (end != -1) {
                return remaining.substring(0, end);
            } else {
                return remaining;
            }
            
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * JDBC URL에서 데이터베이스 타입을 추론
     * 
     * @param url JDBC URL
     * @return 추론된 데이터베이스 타입
     * @throws IllegalArgumentException 지원하지 않는 URL 형식인 경우
     */
    private static DatabaseType inferTypeFromUrl(String url) {
        if (url == null || url.trim().isEmpty()) {
            throw new IllegalArgumentException("Database URL cannot be null or empty");
        }
        
        String lowerUrl = url.toLowerCase();
        if (lowerUrl.startsWith("jdbc:mysql:")) {
            return DatabaseType.MYSQL;
        } else if (lowerUrl.startsWith("jdbc:postgresql:")) {
            return DatabaseType.POSTGRESQL;
        } else {
            throw new IllegalArgumentException("Unsupported database URL: " + url + 
                ". Supported prefixes: jdbc:mysql:, jdbc:postgresql:");
        }
    }

    public static class Builder {
        private String url;
        private String username;
        private String password;

        public Builder url(String url) {
            this.url = url;
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