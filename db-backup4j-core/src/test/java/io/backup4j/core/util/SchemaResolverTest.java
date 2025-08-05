package io.backup4j.core.util;

import io.backup4j.core.config.DatabaseConfig;
import io.backup4j.core.database.DatabaseType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SchemaResolver 테스트 클래스
 */
class SchemaResolverTest {

    @Test
    void extractSchema_PostgreSQL_currentSchema파라미터_정상추출() {
        // given
        String url = "jdbc:postgresql://localhost:5432/testdb?currentSchema=myschema";
        DatabaseConfig config = DatabaseConfig.builder()
            .url(url)
            .username("user")
            .password("pass")
            .build();

        // when
        String schema = config.getSchema();

        // then
        assertEquals("myschema", schema);
    }

    @Test
    void extractSchema_PostgreSQL_currentSchema파라미터없음_기본값public() {
        // given
        String url = "jdbc:postgresql://localhost:5432/testdb";
        DatabaseConfig config = DatabaseConfig.builder()
            .url(url)
            .username("user")
            .password("pass")
            .build();

        // when
        String schema = config.getSchema();

        // then
        assertEquals("public", schema);
    }

    @Test
    void extractSchema_PostgreSQL_searchPath파라미터_첫번째스키마추출() {
        // given
        String url = "jdbc:postgresql://localhost:5432/testdb?searchPath=myschema,public";
        DatabaseConfig config = DatabaseConfig.builder()
            .url(url)
            .username("user")
            .password("pass")
            .build();

        // when
        String schema = config.getSchema();

        // then
        assertEquals("myschema", schema);
    }

    @Test
    void extractSchema_MySQL_데이터베이스명이_스키마역할() {
        // given
        String url = "jdbc:mysql://localhost:3306/mydb";
        DatabaseConfig config = DatabaseConfig.builder()
            .url(url)
            .username("user")
            .password("pass")
            .build();

        // when
        String schema = config.getSchema();

        // then
        assertEquals("mydb", schema);
    }

    @Test
    void extractSchema_PostgreSQL_currentSchema우선순위_확인() {
        // given - currentSchema와 searchPath가 모두 있을 때
        String url = "jdbc:postgresql://localhost:5432/testdb?currentSchema=priority&searchPath=secondary,public";
        DatabaseConfig config = DatabaseConfig.builder()
            .url(url)
            .username("user")
            .password("pass")
            .build();

        // when
        String schema = config.getSchema();

        // then
        assertEquals("priority", schema);
    }

    @Test
    void extractSchema_PostgreSQL_복잡한파라미터_정상처리() {
        // given
        String url = "jdbc:postgresql://localhost:5432/testdb?useSSL=true&currentSchema=testschema&connectTimeout=30";
        DatabaseConfig config = DatabaseConfig.builder()
            .url(url)
            .username("user")
            .password("pass")
            .build();

        // when
        String schema = config.getSchema();

        // then
        assertEquals("testschema", schema);
    }

    @Test
    void getName_JDBC_URL에서_데이터베이스명_정상추출() {
        // given
        String postgresUrl = "jdbc:postgresql://localhost:5432/testdb?currentSchema=myschema";
        String mysqlUrl = "jdbc:mysql://localhost:3306/mydb?useSSL=false";

        DatabaseConfig postgresConfig = DatabaseConfig.builder()
            .url(postgresUrl)
            .username("user")
            .password("pass")
            .build();

        DatabaseConfig mysqlConfig = DatabaseConfig.builder()
            .url(mysqlUrl)
            .username("user")
            .password("pass")
            .build();

        // when & then
        assertEquals("testdb", postgresConfig.getName());
        assertEquals("mydb", mysqlConfig.getName());
    }

    @Test
    void getType_JDBC_URL에서_데이터베이스타입_정상추론() {
        // given
        String postgresUrl = "jdbc:postgresql://localhost:5432/testdb";
        String mysqlUrl = "jdbc:mysql://localhost:3306/mydb";

        DatabaseConfig postgresConfig = DatabaseConfig.builder()
            .url(postgresUrl)
            .username("user")
            .password("pass")
            .build();

        DatabaseConfig mysqlConfig = DatabaseConfig.builder()
            .url(mysqlUrl)
            .username("user")
            .password("pass")
            .build();

        // when & then
        assertEquals(DatabaseType.POSTGRESQL, postgresConfig.getType());
        assertEquals(DatabaseType.MYSQL, mysqlConfig.getType());
    }
}