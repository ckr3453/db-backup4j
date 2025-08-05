package io.backup4j.core.database;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 데이터베이스 테이블 필터링을 담당하는 유틸리티 클래스
 * 시스템 테이블 제외, 패턴 기반 필터링 등을 지원합니다.
 */
public class TableFilter {
    
    // MySQL 시스템 테이블 패턴
    private static final Set<String> MYSQL_SYSTEM_TABLE_PATTERNS = new HashSet<String>() {{
        add("information_schema.*");
        add("performance_schema.*");
        add("mysql.*");
        add("sys.*");
    }};
    
    // PostgreSQL 시스템 테이블 패턴  
    private static final Set<String> POSTGRESQL_SYSTEM_TABLE_PATTERNS = new HashSet<String>() {{
        add("information_schema.*");
        add("pg_*");
    }};
    
    // PostGIS 확장 테이블 (MySQL, PostgreSQL 공통)
    private static final Set<String> POSTGIS_SYSTEM_TABLES = new HashSet<String>() {{
        add("geometry_columns");
        add("spatial_ref_sys");
        add("geography_columns");
        add("raster_*");
    }};
    
    // 기타 일반적인 시스템 테이블들
    private static final Set<String> COMMON_SYSTEM_TABLE_PATTERNS = new HashSet<String>() {{
        add("flyway_*");
        add("liquibase*");
        add("__*"); // 이중 언더스코어로 시작하는 테이블들
    }};
    
    private TableFilter() {
        // 유틸리티 클래스
    }
    
    /**
     * 테이블 목록을 필터링합니다.
     * 
     * @param tables 원본 테이블 목록
     * @param databaseType 데이터베이스 타입
     * @param excludeSystemTables 시스템 테이블 제외 여부
     * @param excludePatterns 제외할 테이블 패턴 목록
     * @param includePatterns 포함할 테이블 패턴 목록 (null이면 모든 테이블 포함)
     * @return 필터링된 테이블 목록
     */
    public static List<String> filterTables(List<String> tables, 
                                           DatabaseType databaseType,
                                           boolean excludeSystemTables,
                                           List<String> excludePatterns, 
                                           List<String> includePatterns) {
        
        if (tables == null || tables.isEmpty()) {
            return new ArrayList<>();
        }
        
        return tables.stream()
            .filter(table -> shouldIncludeTable(table, databaseType, excludeSystemTables, excludePatterns, includePatterns))
            .collect(Collectors.toList());
    }
    
    /**
     * 테이블이 포함되어야 하는지 판단합니다.
     * 
     * @param tableName 테이블명
     * @param databaseType 데이터베이스 타입
     * @param excludeSystemTables 시스템 테이블 제외 여부
     * @param excludePatterns 제외 패턴 목록
     * @param includePatterns 포함 패턴 목록
     * @return 포함 여부
     */
    private static boolean shouldIncludeTable(String tableName, 
                                            DatabaseType databaseType,
                                            boolean excludeSystemTables,
                                            List<String> excludePatterns, 
                                            List<String> includePatterns) {
        
        // 1. include 패턴이 있는 경우, 먼저 확인
        if (includePatterns != null && !includePatterns.isEmpty()) {
            boolean matchesInclude = includePatterns.stream()
                .anyMatch(pattern -> matchesPattern(tableName, pattern));
            if (!matchesInclude) {
                return false;
            }
        }
        
        // 2. 시스템 테이블 제외 확인
        if (excludeSystemTables && isSystemTable(tableName, databaseType)) {
            return false;
        }
        
        // 3. exclude 패턴 확인
        if (excludePatterns != null && !excludePatterns.isEmpty()) {
            boolean matchesExclude = excludePatterns.stream()
                .anyMatch(pattern -> matchesPattern(tableName, pattern));
            if (matchesExclude) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * 시스템 테이블인지 확인합니다.
     * 
     * @param tableName 테이블명
     * @param databaseType 데이터베이스 타입
     * @return 시스템 테이블 여부
     */
    public static boolean isSystemTable(String tableName, DatabaseType databaseType) {
        if (tableName == null || tableName.trim().isEmpty()) {
            return false;
        }
        
        String lowerTableName = tableName.toLowerCase();
        
        // PostGIS 테이블 확인 (모든 DB 공통)
        if (POSTGIS_SYSTEM_TABLES.stream().anyMatch(pattern -> matchesPattern(lowerTableName, pattern))) {
            return true;
        }
        
        // 공통 시스템 테이블 패턴 확인
        if (COMMON_SYSTEM_TABLE_PATTERNS.stream().anyMatch(pattern -> matchesPattern(lowerTableName, pattern))) {
            return true;
        }
        
        // 데이터베이스별 시스템 테이블 확인
        Set<String> systemPatterns = getSystemTablePatterns(databaseType);
        return systemPatterns.stream().anyMatch(pattern -> matchesPattern(lowerTableName, pattern));
    }
    
    /**
     * 데이터베이스 타입별 시스템 테이블 패턴을 반환합니다.
     * 
     * @param databaseType 데이터베이스 타입
     * @return 시스템 테이블 패턴 목록
     */
    private static Set<String> getSystemTablePatterns(DatabaseType databaseType) {
        switch (databaseType) {
            case MYSQL:
                return MYSQL_SYSTEM_TABLE_PATTERNS;
            case POSTGRESQL:
                return POSTGRESQL_SYSTEM_TABLE_PATTERNS;
            default:
                return Collections.emptySet();
        }
    }
    
    /**
     * 문자열이 패턴과 일치하는지 확인합니다.
     * 와일드카드 패턴을 지원합니다: * (모든 문자), ? (단일 문자)
     * 
     * @param text 확인할 문자열
     * @param pattern 패턴 (와일드카드 지원)
     * @return 패턴 일치 여부
     */
    public static boolean matchesPattern(String text, String pattern) {
        if (text == null || pattern == null) {
            return false;
        }
        
        // 정확한 매치
        if (text.equals(pattern)) {
            return true;
        }
        
        // 와일드카드가 없으면 단순 비교
        if (!pattern.contains("*") && !pattern.contains("?")) {
            return text.equalsIgnoreCase(pattern);
        }
        
        // 와일드카드 패턴을 정규식으로 변환
        String regexPattern = convertWildcardToRegex(pattern);
        return Pattern.compile(regexPattern, Pattern.CASE_INSENSITIVE).matcher(text).matches();
    }
    
    /**
     * 와일드카드 패턴을 정규식으로 변환합니다.
     * 
     * @param wildcardPattern 와일드카드 패턴
     * @return 정규식 패턴
     */
    private static String convertWildcardToRegex(String wildcardPattern) {
        StringBuilder regex = new StringBuilder();
        
        for (int i = 0; i < wildcardPattern.length(); i++) {
            char c = wildcardPattern.charAt(i);
            switch (c) {
                case '*':
                    regex.append(".*");
                    break;
                case '?':
                    regex.append(".");
                    break;
                case '.':
                case '\\':
                case '+':
                case '^':
                case '$':
                case '(':
                case ')':
                case '[':
                case ']':
                case '{':
                case '}':
                case '|':
                    // 정규식 특수문자 이스케이프
                    regex.append("\\").append(c);
                    break;
                default:
                    regex.append(c);
                    break;
            }
        }
        
        return regex.toString();
    }
    
    /**
     * 필터링 결과 정보를 담는 클래스
     */
    public static class FilterResult {
        private final List<String> includedTables;
        private final List<String> excludedTables;
        private final int originalCount;
        
        public FilterResult(List<String> includedTables, List<String> excludedTables, int originalCount) {
            this.includedTables = new ArrayList<>(includedTables);
            this.excludedTables = new ArrayList<>(excludedTables);
            this.originalCount = originalCount;
        }
        
        public List<String> getIncludedTables() {
            return new ArrayList<>(includedTables);
        }
        
        public List<String> getExcludedTables() {
            return new ArrayList<>(excludedTables);
        }
        
        public int getOriginalCount() {
            return originalCount;
        }
        
        public int getIncludedCount() {
            return includedTables.size();
        }
        
        public int getExcludedCount() {
            return excludedTables.size();
        }
        
        public boolean hasExcludedTables() {
            return !excludedTables.isEmpty();
        }
        
        @Override
        public String toString() {
            return String.format("FilterResult{original=%d, included=%d, excluded=%d}", 
                originalCount, getIncludedCount(), getExcludedCount());
        }
    }
    
    /**
     * 상세한 필터링 결과와 함께 테이블을 필터링합니다.
     * 
     * @param tables 원본 테이블 목록
     * @param databaseType 데이터베이스 타입
     * @param excludeSystemTables 시스템 테이블 제외 여부
     * @param excludePatterns 제외 패턴 목록
     * @param includePatterns 포함 패턴 목록
     * @return 필터링 결과
     */
    public static FilterResult filterTablesWithResult(List<String> tables, 
                                                     DatabaseType databaseType,
                                                     boolean excludeSystemTables,
                                                     List<String> excludePatterns, 
                                                     List<String> includePatterns) {
        
        if (tables == null || tables.isEmpty()) {
            return new FilterResult(Collections.emptyList(), Collections.emptyList(), 0);
        }
        
        List<String> includedTables = new ArrayList<>();
        List<String> excludedTables = new ArrayList<>();
        
        for (String table : tables) {
            boolean shouldInclude = shouldIncludeTable(table, databaseType, excludeSystemTables, excludePatterns, includePatterns);
            if (shouldInclude) {
                includedTables.add(table);
            } else {
                excludedTables.add(table);
            }
        }
        
        return new FilterResult(includedTables, excludedTables, tables.size());
    }
}