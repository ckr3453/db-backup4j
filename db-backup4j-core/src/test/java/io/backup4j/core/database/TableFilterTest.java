package io.backup4j.core.database;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class TableFilterTest {

    @Test
    void isSystemTable_PostGIS_시스템_테이블_감지() {
        // Given
        String[] postgisSystemTables = {"geometry_columns", "spatial_ref_sys", "geography_columns"};
        
        // When & Then
        for (String table : postgisSystemTables) {
            assertThat(TableFilter.isSystemTable(table, DatabaseType.MYSQL))
                .as("PostGIS table '%s' should be detected as system table", table)
                .isTrue();
            assertThat(TableFilter.isSystemTable(table, DatabaseType.POSTGRESQL))
                .as("PostGIS table '%s' should be detected as system table", table)
                .isTrue();
        }
    }

    @Test
    void isSystemTable_MySQL_시스템_테이블_감지() {
        // Given
        String[] mysqlSystemTables = {"information_schema.tables", "performance_schema.events", "mysql.user", "sys.version"};
        
        // When & Then
        for (String table : mysqlSystemTables) {
            assertThat(TableFilter.isSystemTable(table, DatabaseType.MYSQL))
                .as("MySQL system table '%s' should be detected", table)
                .isTrue();
        }
    }

    @Test
    void isSystemTable_PostgreSQL_시스템_테이블_감지() {
        // Given
        String[] postgresSystemTables = {"information_schema.tables", "pg_class", "pg_namespace"};
        
        // When & Then
        for (String table : postgresSystemTables) {
            assertThat(TableFilter.isSystemTable(table, DatabaseType.POSTGRESQL))
                .as("PostgreSQL system table '%s' should be detected", table)
                .isTrue();
        }
    }

    @Test
    void isSystemTable_사용자_테이블은_시스템_테이블이_아님() {
        // Given
        String[] userTables = {"users", "orders", "products", "my_custom_table"};
        
        // When & Then
        for (String table : userTables) {
            assertThat(TableFilter.isSystemTable(table, DatabaseType.MYSQL))
                .as("User table '%s' should not be detected as system table", table)
                .isFalse();
            assertThat(TableFilter.isSystemTable(table, DatabaseType.POSTGRESQL))
                .as("User table '%s' should not be detected as system table", table)
                .isFalse();
        }
    }

    @Test
    void matchesPattern_와일드카드_패턴_매칭() {
        // Given & When & Then
        assertThat(TableFilter.matchesPattern("temp_users", "temp_*")).isTrue();
        assertThat(TableFilter.matchesPattern("temp_orders", "temp_*")).isTrue();
        assertThat(TableFilter.matchesPattern("users_temp", "temp_*")).isFalse();
        
        assertThat(TableFilter.matchesPattern("test1", "test?")).isTrue();
        assertThat(TableFilter.matchesPattern("test12", "test?")).isFalse();
        
        assertThat(TableFilter.matchesPattern("backup_users_old", "*_old")).isTrue();
        assertThat(TableFilter.matchesPattern("users_backup", "*_old")).isFalse();
    }

    @Test
    void matchesPattern_정확한_매치() {
        // Given & When & Then
        assertThat(TableFilter.matchesPattern("users", "users")).isTrue();
        assertThat(TableFilter.matchesPattern("Users", "users")).isTrue(); // 대소문자 구분 없음
        assertThat(TableFilter.matchesPattern("user", "users")).isFalse();
    }

    @Test
    void filterTables_시스템_테이블_제외() {
        // Given
        List<String> tables = Arrays.asList(
            "users", "orders", "geometry_columns", "spatial_ref_sys", "products"
        );
        
        // When
        List<String> filtered = TableFilter.filterTables(
            tables, DatabaseType.POSTGRESQL, true, null, null
        );
        
        // Then
        assertThat(filtered).containsExactly("users", "orders", "products");
        assertThat(filtered).doesNotContain("geometry_columns", "spatial_ref_sys");
    }

    @Test
    void filterTables_exclude_패턴_적용() {
        // Given
        List<String> tables = Arrays.asList(
            "users", "temp_users", "orders", "temp_orders", "products"
        );
        List<String> excludePatterns = Arrays.asList("temp_*");
        
        // When
        List<String> filtered = TableFilter.filterTables(
            tables, DatabaseType.MYSQL, false, excludePatterns, null
        );
        
        // Then
        assertThat(filtered).containsExactly("users", "orders", "products");
        assertThat(filtered).doesNotContain("temp_users", "temp_orders");
    }

    @Test
    void filterTables_include_패턴_적용() {
        // Given
        List<String> tables = Arrays.asList(
            "users", "temp_users", "orders", "temp_orders", "products"
        );
        List<String> includePatterns = Arrays.asList("temp_*");
        
        // When
        List<String> filtered = TableFilter.filterTables(
            tables, DatabaseType.MYSQL, false, null, includePatterns
        );
        
        // Then
        assertThat(filtered).containsExactly("temp_users", "temp_orders");
        assertThat(filtered).doesNotContain("users", "orders", "products");
    }

    @Test
    void filterTables_include와_exclude_함께_적용() {
        // Given
        List<String> tables = Arrays.asList(
            "app_users", "app_orders", "temp_users", "temp_orders", "geometry_columns"
        );
        List<String> includePatterns = Arrays.asList("app_*", "temp_*");
        List<String> excludePatterns = Arrays.asList("temp_*");
        
        // When
        List<String> filtered = TableFilter.filterTables(
            tables, DatabaseType.MYSQL, true, excludePatterns, includePatterns
        );
        
        // Then - include가 먼저 적용되고, 그 중에서 exclude 패턴이 제외됨
        assertThat(filtered).containsExactly("app_users", "app_orders");
        assertThat(filtered).doesNotContain("temp_users", "temp_orders", "geometry_columns");
    }

    @Test
    void filterTablesWithResult_상세한_결과_반환() {
        // Given
        List<String> tables = Arrays.asList(
            "users", "orders", "geometry_columns", "spatial_ref_sys", "temp_backup"
        );
        List<String> excludePatterns = Arrays.asList("*_backup");
        
        // When
        TableFilter.FilterResult result = TableFilter.filterTablesWithResult(
            tables, DatabaseType.POSTGRESQL, true, excludePatterns, null
        );
        
        // Then
        assertThat(result.getOriginalCount()).isEqualTo(5);
        assertThat(result.getIncludedCount()).isEqualTo(2);
        assertThat(result.getExcludedCount()).isEqualTo(3);
        assertThat(result.getIncludedTables()).containsExactly("users", "orders");
        assertThat(result.getExcludedTables()).containsExactly("geometry_columns", "spatial_ref_sys", "temp_backup");
        assertThat(result.hasExcludedTables()).isTrue();
    }

    @Test
    void filterTables_빈_목록_처리() {
        // Given
        List<String> emptyTables = Collections.emptyList();
        
        // When
        List<String> filtered = TableFilter.filterTables(
            emptyTables, DatabaseType.MYSQL, true, null, null
        );
        
        // Then
        assertThat(filtered).isEmpty();
    }

    @Test
    void filterTables_null_목록_처리() {
        // When
        List<String> filtered = TableFilter.filterTables(
            null, DatabaseType.MYSQL, true, null, null
        );
        
        // Then
        assertThat(filtered).isEmpty();
    }

    @Test
    void isSystemTable_null과_빈_문자열_처리() {
        // When & Then
        assertThat(TableFilter.isSystemTable(null, DatabaseType.MYSQL)).isFalse();
        assertThat(TableFilter.isSystemTable("", DatabaseType.MYSQL)).isFalse();
        assertThat(TableFilter.isSystemTable("   ", DatabaseType.MYSQL)).isFalse();
    }

    @Test
    void matchesPattern_null_처리() {
        // When & Then
        assertThat(TableFilter.matchesPattern(null, "pattern")).isFalse();
        assertThat(TableFilter.matchesPattern("text", null)).isFalse();
        assertThat(TableFilter.matchesPattern(null, null)).isFalse();
    }
}