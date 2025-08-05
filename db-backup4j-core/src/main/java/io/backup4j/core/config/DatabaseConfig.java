package io.backup4j.core.config;

import io.backup4j.core.database.DatabaseType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 데이터베이스 연결 설정을 관리하는 클래스
 * JDBC URL 방식으로 연결 정보를 관리함
 */
public class DatabaseConfig {
    private final String url;
    private final String username;
    private final String password;
    private final DatabaseType type; // URL에서 자동 추론
    
    // 테이블 필터링 설정
    private final boolean excludeSystemTables;
    private final List<String> excludeTablePatterns;
    private final List<String> includeTablePatterns;
    
    // 기본값 상수
    public static final boolean DEFAULT_EXCLUDE_SYSTEM_TABLES = true;

    private DatabaseConfig(Builder builder) {
        this.url = builder.url;
        this.username = builder.username;
        this.password = builder.password;
        this.type = inferTypeFromUrl(builder.url);
        this.excludeSystemTables = builder.excludeSystemTables;
        this.excludeTablePatterns = builder.excludeTablePatterns != null ? 
            Collections.unmodifiableList(new ArrayList<>(builder.excludeTablePatterns)) : 
            Collections.emptyList();
        this.includeTablePatterns = builder.includeTablePatterns != null ? 
            Collections.unmodifiableList(new ArrayList<>(builder.includeTablePatterns)) : 
            Collections.emptyList();
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
    
    public boolean isExcludeSystemTables() {
        return excludeSystemTables;
    }
    
    public List<String> getExcludeTablePatterns() {
        return excludeTablePatterns;
    }
    
    public List<String> getIncludeTablePatterns() {
        return includeTablePatterns;
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
     * JDBC URL에서 스키마를 추출합니다.
     * PostgreSQL의 currentSchema 파라미터를 우선 확인하고,
     * 없으면 기본값을 반환합니다.
     * 
     * @return 스키마명 (PostgreSQL: currentSchema 또는 'public', MySQL: 데이터베이스명)
     */
    public String getSchema() {
        return extractSchema(this.url, this.type);
    }

    /**
     * JDBC URL에서 데이터베이스 이름을 추출하는 유틸리티 메서드
     * 
     * @param url JDBC URL
     * @return 데이터베이스 이름
     */
    private static String extractDatabaseName(String url) {
        if (url == null) return null;
        
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
    }
    
    /**
     * JDBC URL에서 스키마를 추출하는 유틸리티 메서드
     * 
     * @param url JDBC URL
     * @param type 데이터베이스 타입
     * @return 스키마명
     */
    private static String extractSchema(String url, DatabaseType type) {
        if (url == null || type == null) {
            return getDefaultSchema(type);
        }
        
        switch (type) {
            case MYSQL:
                // MySQL에서는 데이터베이스명이 스키마 역할
                return extractDatabaseName(url);
                
            case POSTGRESQL:
                // PostgreSQL URL에서 currentSchema 파라미터 추출
                String currentSchema = extractPostgreSQLCurrentSchema(url);
                return currentSchema != null ? currentSchema : "public";
                
            default:
                return getDefaultSchema(type);
        }
    }
    
    /**
     * PostgreSQL JDBC URL에서 currentSchema 파라미터를 추출합니다.
     * 
     * @param url JDBC URL
     * @return currentSchema 값, 없으면 null
     */
    private static String extractPostgreSQLCurrentSchema(String url) {
        if (url == null) return null;
        
        // 쿼리 파라미터 부분 추출
        int queryStart = url.indexOf('?');
        if (queryStart == -1) return null;
        
        String queryString = url.substring(queryStart + 1);
        String[] params = queryString.split("&");
        
        for (String param : params) {
            String[] keyValue = param.split("=", 2);
            if (keyValue.length == 2 && "currentSchema".equalsIgnoreCase(keyValue[0])) {
                return keyValue[1];
            }
            // searchPath도 확인 (첫 번째 스키마 사용)
            if (keyValue.length == 2 && "searchPath".equalsIgnoreCase(keyValue[0])) {
                String[] schemas = keyValue[1].split(",");
                return schemas.length > 0 ? schemas[0].trim() : null;
            }
        }
        
        return null;
    }
    
    /**
     * 데이터베이스 타입별 기본 스키마를 반환합니다.
     * 
     * @param type 데이터베이스 타입
     * @return 기본 스키마명
     */
    private static String getDefaultSchema(DatabaseType type) {
        if (type == null) return "public";
        
        switch (type) {
            case MYSQL:
                return null; // MySQL은 데이터베이스명이 스키마
            case POSTGRESQL:
                return "public"; // PostgreSQL 기본 스키마
            default:
                return "public";
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
        private boolean excludeSystemTables = DEFAULT_EXCLUDE_SYSTEM_TABLES;
        private List<String> excludeTablePatterns;
        private List<String> includeTablePatterns;

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
        
        public Builder excludeSystemTables(boolean excludeSystemTables) {
            this.excludeSystemTables = excludeSystemTables;
            return this;
        }
        
        public Builder excludeTablePatterns(List<String> excludeTablePatterns) {
            this.excludeTablePatterns = excludeTablePatterns;
            return this;
        }
        
        public Builder includeTablePatterns(List<String> includeTablePatterns) {
            this.includeTablePatterns = includeTablePatterns;
            return this;
        }

        public DatabaseConfig build() {
            return new DatabaseConfig(this);
        }
    }
}