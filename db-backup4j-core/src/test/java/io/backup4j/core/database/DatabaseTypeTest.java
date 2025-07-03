package io.backup4j.core.database;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DatabaseTypeTest {

    @Test
    void fromString_대문자값으로_정상파싱() {
        // given
        String mysqlValue = "MYSQL";
        String postgresqlValue = "POSTGRESQL";
        
        // when
        DatabaseType mysqlResult = DatabaseType.fromString(mysqlValue);
        DatabaseType postgresqlResult = DatabaseType.fromString(postgresqlValue);
        
        // then
        assertEquals(DatabaseType.MYSQL, mysqlResult);
        assertEquals(DatabaseType.POSTGRESQL, postgresqlResult);
    }

    @Test
    void fromString_소문자값으로_정상파싱() {
        // given
        String mysqlValue = "mysql";
        String postgresqlValue = "postgresql";
        
        // when
        DatabaseType mysqlResult = DatabaseType.fromString(mysqlValue);
        DatabaseType postgresqlResult = DatabaseType.fromString(postgresqlValue);
        
        // then
        assertEquals(DatabaseType.MYSQL, mysqlResult);
        assertEquals(DatabaseType.POSTGRESQL, postgresqlResult);
    }

    @Test
    void fromString_대소문자혼합값으로_정상파싱() {
        // given
        String mysqlValue1 = "MySQL";
        String mysqlValue2 = "mYsQl";
        String postgresqlValue1 = "PostgreSQL";
        String postgresqlValue2 = "postgreSQL";
        
        // when & then
        assertEquals(DatabaseType.MYSQL, DatabaseType.fromString(mysqlValue1));
        assertEquals(DatabaseType.MYSQL, DatabaseType.fromString(mysqlValue2));
        assertEquals(DatabaseType.POSTGRESQL, DatabaseType.fromString(postgresqlValue1));
        assertEquals(DatabaseType.POSTGRESQL, DatabaseType.fromString(postgresqlValue2));
    }

    @Test
    void fromString_유효하지않은값으로_예외발생() {
        // given
        String invalidValue = "INVALID";
        
        // when & then
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> DatabaseType.fromString(invalidValue)
        );
        assertEquals("Unsupported database type: INVALID. Supported types: MYSQL, POSTGRESQL", exception.getMessage());
    }

    @Test
    void fromString_null값으로_예외발생() {
        // given
        String nullValue = null;
        
        // when & then
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> DatabaseType.fromString(nullValue)
        );
        assertEquals("Database type cannot be null or empty", exception.getMessage());
    }

    @Test
    void fromString_빈값으로_예외발생() {
        // given
        String emptyValue = "";
        
        // when & then
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> DatabaseType.fromString(emptyValue)
        );
        assertEquals("Database type cannot be null or empty", exception.getMessage());
    }

    @Test
    void fromString_공백값으로_예외발생() {
        // given
        String whitespaceValue = "   ";
        
        // when & then
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> DatabaseType.fromString(whitespaceValue)
        );
        assertEquals("Database type cannot be null or empty", exception.getMessage());
    }

    @Test
    void values_enum값들이_올바름() {
        // given & when
        DatabaseType[] types = DatabaseType.values();
        
        // then
        assertEquals(2, types.length);
        assertEquals(DatabaseType.MYSQL, types[0]);
        assertEquals(DatabaseType.POSTGRESQL, types[1]);
    }

    @Test
    void toString_enum문자열변환이_올바름() {
        // given & when & then
        assertEquals("MYSQL", DatabaseType.MYSQL.toString());
        assertEquals("POSTGRESQL", DatabaseType.POSTGRESQL.toString());
    }
}