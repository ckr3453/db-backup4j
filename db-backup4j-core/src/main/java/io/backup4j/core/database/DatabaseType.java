package io.backup4j.core.database;

/**
 * 지원하는 데이터베이스 타입 열거형
 * 현재 MySQL과 PostgreSQL을 지원합니다.
 */
public enum DatabaseType {
    MYSQL,
    POSTGRESQL;
    
    /**
     * 문자열 값을 DatabaseType으로 변환합니다.
     * 
     * @param value 변환할 문자열 (대소문자 구분 안함)
     * @return 해당하는 DatabaseType
     * @throws IllegalArgumentException 지원하지 않는 데이터베이스 타입인 경우
     */
    public static DatabaseType fromString(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Database type cannot be null or empty");
        }
        
        String upperValue = value.trim().toUpperCase();
        for (DatabaseType type : DatabaseType.values()) {
            if (type.name().equals(upperValue)) {
                return type;
            }
        }
        
        throw new IllegalArgumentException("Unsupported database type: " + value + 
            ". Supported types: " + String.join(", ", getSupportedTypes()));
    }
    
    private static String[] getSupportedTypes() {
        DatabaseType[] types = DatabaseType.values();
        String[] supportedTypes = new String[types.length];
        for (int i = 0; i < types.length; i++) {
            supportedTypes[i] = types[i].name();
        }
        return supportedTypes;
    }
}