package io.backup4j.core.database;

import io.backup4j.core.config.DatabaseConfig;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * 데이터베이스 연결로부터 실제 스키마를 확인하는 유틸리티 클래스
 * JDBC URL에서 추출한 스키마와 실제 연결된 스키마를 검증합니다.
 */
public class SchemaResolver {
    
    private SchemaResolver() {
        // 유틸리티 클래스
    }
    
    /**
     * 데이터베이스 연결로부터 현재 활성 스키마를 확인합니다.
     * 
     * @param connection 데이터베이스 연결
     * @param databaseConfig 데이터베이스 설정
     * @return 현재 활성 스키마명
     * @throws SQLException 스키마 조회 실패 시
     */
    public static String resolveCurrentSchema(Connection connection, DatabaseConfig databaseConfig) throws SQLException {
        DatabaseType dbType = databaseConfig.getType();
        
        switch (dbType) {
            case MYSQL:
                return resolveMySQLSchema(connection, databaseConfig);
            case POSTGRESQL:
                return resolvePostgreSQLSchema(connection, databaseConfig);
            default:
                throw new IllegalArgumentException("Unsupported database type: " + dbType);
        }
    }
    
    /**
     * MySQL에서 현재 활성 데이터베이스(스키마)를 확인합니다.
     * 
     * @param connection MySQL 데이터베이스 연결
     * @param databaseConfig 데이터베이스 설정
     * @return 현재 데이터베이스명
     * @throws SQLException 조회 실패 시
     */
    private static String resolveMySQLSchema(Connection connection, DatabaseConfig databaseConfig) throws SQLException {
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT DATABASE()")) {
            
            if (rs.next()) {
                String currentDatabase = rs.getString(1);
                if (currentDatabase != null) {
                    return currentDatabase;
                }
            }
        }
        
        // 연결된 데이터베이스가 없으면 설정에서 추출한 이름 사용
        return databaseConfig.getName();
    }
    
    /**
     * PostgreSQL에서 현재 활성 스키마를 확인합니다.
     * 
     * @param connection PostgreSQL 데이터베이스 연결
     * @param databaseConfig 데이터베이스 설정
     * @return 현재 스키마명
     * @throws SQLException 조회 실패 시
     */
    private static String resolvePostgreSQLSchema(Connection connection, DatabaseConfig databaseConfig) throws SQLException {
        // 1. 현재 스키마 확인
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT current_schema()")) {
            
            if (rs.next()) {
                String currentSchema = rs.getString(1);
                if (currentSchema != null && !currentSchema.trim().isEmpty()) {
                    return currentSchema;
                }
            }
        }
        
        // 2. search_path에서 첫 번째 스키마 확인
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SHOW search_path")) {
            
            if (rs.next()) {
                String searchPath = rs.getString(1);
                if (searchPath != null) {
                    String[] schemas = searchPath.split(",");
                    for (String schema : schemas) {
                        schema = schema.trim();
                        // "$user" 스키마는 제외하고 실제 스키마명만 반환
                        if (!schema.startsWith("$") && !schema.isEmpty()) {
                            return schema;
                        }
                    }
                }
            }
        }
        
        // 3. 설정에서 추출한 스키마 사용
        String configSchema = databaseConfig.getSchema();
        if (configSchema != null && !configSchema.trim().isEmpty()) {
            return configSchema;
        }
        
        // 4. 기본값 사용
        return "public";
    }
    
    /**
     * 스키마가 실제로 존재하는지 확인합니다.
     * 
     * @param connection 데이터베이스 연결
     * @param schemaName 확인할 스키마명
     * @param dbType 데이터베이스 타입
     * @return 스키마 존재 여부
     * @throws SQLException 조회 실패 시
     */
    public static boolean schemaExists(Connection connection, String schemaName, DatabaseType dbType) throws SQLException {
        if (schemaName == null || schemaName.trim().isEmpty()) {
            return false;
        }
        
        switch (dbType) {
            case MYSQL:
                return mysqlDatabaseExists(connection, schemaName);
            case POSTGRESQL:
                return postgresqlSchemaExists(connection, schemaName);
            default:
                return false;
        }
    }
    
    /**
     * MySQL에서 데이터베이스 존재 여부를 확인합니다.
     */
    private static boolean mysqlDatabaseExists(Connection connection, String databaseName) throws SQLException {
        String query = "SELECT SCHEMA_NAME FROM information_schema.SCHEMATA WHERE SCHEMA_NAME = ?";
        try (java.sql.PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, databaseName);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        }
    }
    
    /**
     * PostgreSQL에서 스키마 존재 여부를 확인합니다.
     */
    private static boolean postgresqlSchemaExists(Connection connection, String schemaName) throws SQLException {
        String query = "SELECT schema_name FROM information_schema.schemata WHERE schema_name = ?";
        try (java.sql.PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, schemaName);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        }
    }
    
    /**
     * 스키마 해결 결과를 담는 클래스
     */
    public static class SchemaResolutionResult {
        private final String resolvedSchema;
        private final String originalSchema;
        private final boolean schemaExists;
        private final String resolutionMethod;
        
        public SchemaResolutionResult(String resolvedSchema, String originalSchema, 
                                    boolean schemaExists, String resolutionMethod) {
            this.resolvedSchema = resolvedSchema;
            this.originalSchema = originalSchema;
            this.schemaExists = schemaExists;
            this.resolutionMethod = resolutionMethod;
        }
        
        public String getResolvedSchema() { return resolvedSchema; }
        public String getOriginalSchema() { return originalSchema; }
        public boolean isSchemaExists() { return schemaExists; }
        public String getResolutionMethod() { return resolutionMethod; }
        
        @Override
        public String toString() {
            return String.format("SchemaResolutionResult{resolved='%s', original='%s', exists=%s, method='%s'}", 
                resolvedSchema, originalSchema, schemaExists, resolutionMethod);
        }
    }
    
    /**
     * 스키마를 완전히 해결하고 검증합니다.
     * 
     * @param connection 데이터베이스 연결
     * @param databaseConfig 데이터베이스 설정
     * @return 스키마 해결 결과
     * @throws SQLException 조회 실패 시
     */
    public static SchemaResolutionResult resolveAndValidateSchema(Connection connection, DatabaseConfig databaseConfig) throws SQLException {
        String originalSchema = databaseConfig.getSchema();
        String resolvedSchema = resolveCurrentSchema(connection, databaseConfig);
        String resolutionMethod;
        
        // 해결 방법 결정
        if (resolvedSchema.equals(originalSchema)) {
            resolutionMethod = "URL_PARAMETER";
        } else if (databaseConfig.getType() == DatabaseType.POSTGRESQL) {
            resolutionMethod = "CURRENT_SCHEMA_QUERY";
        } else {
            resolutionMethod = "DATABASE_QUERY";
        }
        
        // 스키마 존재 여부 확인
        boolean exists = schemaExists(connection, resolvedSchema, databaseConfig.getType());
        
        return new SchemaResolutionResult(resolvedSchema, originalSchema, exists, resolutionMethod);
    }
}