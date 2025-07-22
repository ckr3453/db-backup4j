package io.backup4j.core.util;

/**
 * SQL 관련 유틸리티 클래스
 * SQL injection 방지 및 안전한 문자열 처리를 제공합니다.
 */
public class SqlUtils {
    
    private SqlUtils() {
    }
    
    /**
     * SQL 문자열 값을 안전하게 이스케이프합니다.
     * SQL injection을 방지하기 위해 특수 문자들을 처리합니다.
     * 
     * @param value 이스케이프할 문자열 값
     * @return 이스케이프된 문자열
     */
    public static String escapeSqlString(String value) {
        if (value == null) {
            return "NULL";
        }
        
        // SQL injection 방지를 위한 이스케이프 처리
        return "'" + value
            .replace("\\", "\\\\")  // 백슬래시 이스케이프
            .replace("'", "''")     // 싱글 쿼트 이스케이프  
            .replace("\"", "\\\"")  // 더블 쿼트 이스케이프
            .replace("\r", "\\r")   // 캐리지 리턴 이스케이프
            .replace("\n", "\\n")   // 뉴라인 이스케이프
            .replace("\t", "\\t")   // 탭 이스케이프
            .replace("\0", "\\0")   // NULL 바이트 이스케이프
            + "'";
    }
    
    /**
     * 테이블명이나 컬럼명이 안전한지 검증합니다.
     * SQL injection을 방지하기 위해 허용되지 않는 문자가 있는지 확인합니다.
     * 
     * @param identifier 검증할 식별자 (테이블명, 컬럼명 등)
     * @return 안전한 식별자인 경우 true
     */
    public static boolean isValidSqlIdentifier(String identifier) {
        if (identifier == null || identifier.trim().isEmpty()) {
            return false;
        }
        
        // SQL 식별자는 영문자, 숫자, 언더스코어만 허용
        // 첫 글자는 영문자 또는 언더스코어여야 함
        return identifier.matches("^[a-zA-Z_][a-zA-Z0-9_]*$");
    }
    
    /**
     * MySQL용 식별자를 백틱으로 감쌉니다.
     * 
     * @param identifier 감쌀 식별자
     * @return 백틱으로 감싸진 식별자
     */
    public static String quoteMySqlIdentifier(String identifier) {
        if (!isValidSqlIdentifier(identifier)) {
            throw new IllegalArgumentException("Invalid SQL identifier: " + identifier);
        }
        return "`" + identifier + "`";
    }
    
    /**
     * PostgreSQL용 식별자를 더블 쿼트로 감쌉니다.
     * 
     * @param identifier 감쌀 식별자
     * @return 더블 쿼트로 감싸진 식별자
     */
    public static String quotePostgreSqlIdentifier(String identifier) {
        if (!isValidSqlIdentifier(identifier)) {
            throw new IllegalArgumentException("Invalid SQL identifier: " + identifier);
        }
        return "\"" + identifier + "\"";
    }
}