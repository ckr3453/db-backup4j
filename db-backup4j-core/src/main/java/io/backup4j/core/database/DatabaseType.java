package io.backup4j.core.database;

public enum DatabaseType {
    MYSQL,
    POSTGRESQL;
    
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