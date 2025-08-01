package io.backup4j.core.database;

import io.backup4j.core.config.BackupConfig;
import io.backup4j.core.validation.BackupResult;
import io.backup4j.core.validation.BackupValidator;
import io.backup4j.core.util.CompressionUtils;
import io.backup4j.core.util.BackupFileNameGenerator;
import io.backup4j.core.util.RetentionPolicy;
import io.backup4j.core.util.SqlUtils;
import io.backup4j.core.util.S3Utils;
import io.backup4j.core.config.S3BackupConfig;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.FileSystemException;
import java.nio.file.Path;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;

/**
 * 데이터베이스 백업을 실행하는 클래스
 * 설정에 따라 MySQL, PostgreSQL 데이터베이스를 백업하고 압축, 체크섬 검증, 파일 정리 등을 수행함
 */
public class DatabaseBackupExecutor {
    
    /**
     * 백업 실행 메서드
     * 
     * @param config 백업 설정
     * @return 백업 결과
     */
    public BackupResult executeBackup(BackupConfig config) {
        String backupId = UUID.randomUUID().toString();
        LocalDateTime startTime = LocalDateTime.now();
        BackupResult.Builder resultBuilder = BackupResult.builder()
            .backupId(backupId)
            .startTime(startTime);

        try {
            // 1. 데이터베이스 백업 실행
            DatabaseBackupResult backupResult = performDatabaseBackup(config, resultBuilder);
            if (backupResult.isFailure()) {
                return backupResult.getFailedResult();
            }
            
            File backupFile = backupResult.getBackupFile();
            long originalSize = backupResult.getOriginalSize();
            
            // 3. 압축 처리
            CompressionResult compressionResult = processCompression(config, backupFile, resultBuilder);
            File finalBackupFile = compressionResult.getFinalFile();
            
            // 4. 백업 파일 검증
            BackupValidator.ValidationResult validation = validateBackupFile(finalBackupFile, resultBuilder);
            
            // 5. 백업 파일 정보 생성
            BackupResult.BackupFile backupFileInfo = createBackupFileInfo(finalBackupFile, validation);
            resultBuilder.addFile(backupFileInfo);
            
            // 6. 보존 정책 적용
            applyRetentionPolicy(config, finalBackupFile, resultBuilder);
            
            // 7. S3 백업 처리
            if (config.getS3().isEnabled()) {
                executeS3Backup(finalBackupFile, config, resultBuilder);
            }
            
            // 8. 메타데이터 생성 및 결과 빌드
            return buildFinalResult(config, originalSize, finalBackupFile, resultBuilder);
                
        } catch (Exception e) {
            return handleUnexpectedError(e, resultBuilder);
        }
    }
    
    
    /**
     * 데이터베이스 백업 실행
     */
    private DatabaseBackupResult performDatabaseBackup(BackupConfig config, BackupResult.Builder resultBuilder) {
        try {
            File backupFile = createBackupFile(config);
            long originalSize;
            
            // 데이터베이스 연결 및 백업 실행
            try (Connection connection = DatabaseConnection.getConnection(config.getDatabase());
                 BufferedWriter writer = new BufferedWriter(new FileWriter(backupFile))) {
                
                // 스키마 해결 및 검증
                SchemaResolver.SchemaResolutionResult schemaResult = 
                    SchemaResolver.resolveAndValidateSchema(connection, config.getDatabase());
                    
                String resolvedSchema = schemaResult.getResolvedSchema();
                
                // 스키마 정보를 백업 파일에 기록
                writer.write("-- Schema Resolution: " + schemaResult.getResolutionMethod() + "\n");
                writer.write("-- Target Schema: " + resolvedSchema + "\n");
                if (!schemaResult.isSchemaExists()) {
                    writer.write("-- WARNING: Schema '" + resolvedSchema + "' does not exist!\n");
                }
                writer.write("\n");
                
                // 데이터베이스 타입에 따른 백업 실행
                if (config.getDatabase().getType() == DatabaseType.MYSQL) {
                    executeMySQLBackup(connection, writer, resolvedSchema, config);
                } else if (config.getDatabase().getType() == DatabaseType.POSTGRESQL) {
                    executePostgreSQLBackup(connection, writer, resolvedSchema, config);
                }
                
                writer.flush();
                originalSize = backupFile.length();
                
                
                return new DatabaseBackupResult(backupFile, originalSize);
                
            } catch (SQLException | IOException e) {
                String errorMessage = "Database backup failed: " + e.getMessage();
                
                // 디스크 공간 부족 감지
                if (isDiskSpaceError(e)) {
                    errorMessage = "Insufficient disk space for backup: " + e.getMessage();
                }
                
                resultBuilder.addError(new BackupResult.BackupError(
                    "local", 
                    errorMessage, 
                    e, 
                    LocalDateTime.now()
                ));
                BackupResult failedResult = resultBuilder
                    .endTime(LocalDateTime.now())
                    .status(BackupResult.Status.FAILED)
                    .build();
                return new DatabaseBackupResult(failedResult);
            }
            
        } catch (Exception e) {
            resultBuilder.addError(new BackupResult.BackupError(
                "database", 
                "Unexpected database backup error: " + e.getMessage(), 
                e, 
                LocalDateTime.now()
            ));
            BackupResult failedResult = resultBuilder
                .endTime(LocalDateTime.now())
                .status(BackupResult.Status.FAILED)
                .build();
            return new DatabaseBackupResult(failedResult);
        }
    }
    
    /**
     * 압축 처리
     */
    private CompressionResult processCompression(BackupConfig config, File backupFile, BackupResult.Builder resultBuilder) {
        File finalBackupFile = backupFile;
        CompressionUtils.CompressionResult compressionMetrics = null;
        
        if (config.getLocal().isCompress()) {
            try {
                Path compressedPath = CompressionUtils.getCompressedFileName(backupFile.toPath());
                compressionMetrics = CompressionUtils.compressWithMetrics(backupFile.toPath(), compressedPath);
                finalBackupFile = compressedPath.toFile();
                
                // 원본 파일 삭제 (압축 후 원본은 불필요)
                if (!backupFile.delete()) {
                    System.err.println("Warning: Failed to delete original backup file: " + backupFile.getPath());
                }
                
                
            } catch (Exception e) {
                resultBuilder.addError(new BackupResult.BackupError(
                    "compression", 
                    "Compression failed: " + e.getMessage(), 
                    e, 
                    LocalDateTime.now()
                ));
            }
        }
        
        return new CompressionResult(finalBackupFile, compressionMetrics);
    }
    
    /**
     * 백업 파일 검증
     */
    private BackupValidator.ValidationResult validateBackupFile(File backupFile, BackupResult.Builder resultBuilder) {
        try {
            BackupValidator.ValidationResult validation = BackupValidator.validateBackupFile(backupFile);
            resultBuilder.addValidationResult(validation);
            
            if (!validation.isValid()) {
                resultBuilder.addError(new BackupResult.BackupError(
                    "validation", 
                    "Backup file validation failed: " + String.join(", ", validation.getErrors()), 
                    null, 
                    LocalDateTime.now()
                ));
            }
            
            return validation;
            
        } catch (Exception e) {
            BackupValidator.ValidationResult failedValidation = BackupValidator.ValidationResult.builder()
                .filePath(backupFile.getAbsolutePath())
                .valid(false)
                .addError("Validation process failed: " + e.getMessage())
                .build();
                
            resultBuilder.addValidationResult(failedValidation);
            resultBuilder.addError(new BackupResult.BackupError(
                "validation", 
                "Backup file validation process failed: " + e.getMessage(), 
                e, 
                LocalDateTime.now()
            ));
            
            return failedValidation;
        }
    }
    
    /**
     * 백업 파일 정보 생성
     */
    private BackupResult.BackupFile createBackupFileInfo(File backupFile, BackupValidator.ValidationResult validation) {
        return new BackupResult.BackupFile(
            backupFile.toPath(),
            backupFile.length(),
            "local",
            validation,
            LocalDateTime.now()
        );
    }
    
    /**
     * 보존 정책 적용
     */
    private void applyRetentionPolicy(BackupConfig config, File backupFile, BackupResult.Builder resultBuilder) {
        if (config.getLocal().getRetention() != null && !config.getLocal().getRetention().isEmpty()) {
            try {
                int retentionDays = Integer.parseInt(config.getLocal().getRetention());
                if (retentionDays > 0) {
                    Path retentionDirectory = backupFile.getParentFile().toPath();
                    RetentionPolicy retentionPolicy = new RetentionPolicy();
                    RetentionPolicy.CleanupResult cleanupResult = retentionPolicy.cleanup(retentionDirectory, retentionDays, false);

                    if (cleanupResult.hasErrors()) {
                        for (String error : cleanupResult.getErrors()) {
                            resultBuilder.addError(new BackupResult.BackupError(
                                "retention",
                                "Retention policy error: " + error,
                                null,
                                LocalDateTime.now()
                            ));
                        }
                    }
                }
            } catch (NumberFormatException e) {
                resultBuilder.addError(new BackupResult.BackupError(
                    "retention",
                    "Invalid retention value: " + config.getLocal().getRetention(),
                    e,
                    LocalDateTime.now()
                ));
            } catch (Exception e) {
                resultBuilder.addError(new BackupResult.BackupError(
                    "retention",
                    "Retention policy execution failed: " + e.getMessage(),
                    e,
                    LocalDateTime.now()
                ));
            }
        }
    }
    
    /**
     * 최종 결과 빌드
     */
    private BackupResult buildFinalResult(BackupConfig config, long originalSize, File finalBackupFile, BackupResult.Builder resultBuilder) {
        // 메타데이터 생성
        BackupResult.BackupMetadata metadata = new BackupResult.BackupMetadata(
            config.getDatabase().getType().toString(),
            config.getDatabase().getHost(),
            config.getDatabase().getName(),
            config.getLocal().isCompress(),
            originalSize,
            finalBackupFile.length(),
            "SQL"
        );
        resultBuilder.metadata(metadata);
        
        // 백업 결과 생성
        return resultBuilder
            .endTime(LocalDateTime.now())
            .status(BackupResult.Status.SUCCESS)
            .build();
    }
    
    /**
     * 예기치 않은 오류 처리
     */
    private BackupResult handleUnexpectedError(Exception e, BackupResult.Builder resultBuilder) {
        resultBuilder.addError(new BackupResult.BackupError(
            "system", 
            "Unexpected error: " + e.getMessage(), 
            e, 
            LocalDateTime.now()
        ));
        return resultBuilder
            .endTime(LocalDateTime.now())
            .status(BackupResult.Status.FAILED)
            .build();
    }
    
    /**
     * 데이터베이스 백업 결과 래퍼 클래스
     */
    private static class DatabaseBackupResult {
        private final File backupFile;
        private final long originalSize;
        private final BackupResult failedResult;
        private final boolean isFailure;
        
        public DatabaseBackupResult(File backupFile, long originalSize) {
            this.backupFile = backupFile;
            this.originalSize = originalSize;
            this.failedResult = null;
            this.isFailure = false;
        }
        
        public DatabaseBackupResult(BackupResult failedResult) {
            this.backupFile = null;
            this.originalSize = 0;
            this.failedResult = failedResult;
            this.isFailure = true;
        }
        
        public File getBackupFile() { return backupFile; }
        public long getOriginalSize() { return originalSize; }
        public BackupResult getFailedResult() { return failedResult; }
        public boolean isFailure() { return isFailure; }
    }
    
    /**
     * 압축 결과 래퍼 클래스
     */
    private static class CompressionResult {
        private final File finalFile;
        private final CompressionUtils.CompressionResult metrics;
        
        public CompressionResult(File finalFile, CompressionUtils.CompressionResult metrics) {
            this.finalFile = finalFile;
            this.metrics = metrics;
        }
        
        public File getFinalFile() { return finalFile; }
        public CompressionUtils.CompressionResult getMetrics() { return metrics; }
    }
    
    // 기존 메서드들 유지 (createBackupFile, executeMySQLBackup, executePostgreSQLBackup 등)
    // 이 부분은 원본 파일에서 복사해야 함
    
    /**
     * 백업 파일 생성
     */
    private File createBackupFile(BackupConfig config) throws IOException {
        String fileName = BackupFileNameGenerator.generateFileName(
            config.getDatabase().getName(),
            false  // 압축은 나중에 처리
        );
        
        File backupDir = new File(config.getLocal().getPath());
        if (!backupDir.exists()) {
            boolean created = backupDir.mkdirs();
            if (!created) {
                throw new IOException("Failed to create backup directory: " + backupDir.getAbsolutePath());
            }
        }
        
        return new File(backupDir, fileName);
    }
    
    /**
     * MySQL 데이터베이스 백업 실행
     */
    private void executeMySQLBackup(Connection connection, BufferedWriter writer, String databaseName, BackupConfig config) 
            throws SQLException, IOException {
        
        writer.write("-- MySQL Database Backup by db-backup4j\n");
        writer.write("-- Generated: " + LocalDateTime.now() + "\n");
        writer.write("-- Database: " + databaseName + "\n\n");
        
        writer.write("SET FOREIGN_KEY_CHECKS=0;\n");
        writer.write("SET SQL_MODE='NO_AUTO_VALUE_ON_ZERO';\n\n");
        
        // 테이블 목록 조회
        List<String> allTables = new ArrayList<>();
        try (Statement stmt = connection.createStatement();
             ResultSet tables = stmt.executeQuery("SHOW TABLES")) {
            
            while (tables.next()) {
                allTables.add(tables.getString(1));
            }
        }
        
        // 테이블 필터링 적용
        TableFilter.FilterResult filterResult = TableFilter.filterTablesWithResult(
            allTables,
            DatabaseType.MYSQL,
            config.getDatabase().isExcludeSystemTables(),
            config.getDatabase().getExcludeTablePatterns(),
            config.getDatabase().getIncludeTablePatterns()
        );
        
        List<String> tablesToBackup = filterResult.getIncludedTables();
        
        // 필터링 결과 로그 출력
        if (filterResult.hasExcludedTables()) {
            writer.write("-- Filtered out " + filterResult.getExcludedCount() + " tables\n");
            writer.write("-- Backing up " + filterResult.getIncludedCount() + " tables\n\n");
        }
        
        // 의존성 분석 및 올바른 순서로 백업
        try {
            TableDependencyAnalyzer.DependencyAnalysisResult dependencyResult = 
                TableDependencyAnalyzer.analyzeDependencies(connection, DatabaseType.MYSQL, tablesToBackup, databaseName);
                
            List<String> orderedTables = dependencyResult.getOrderedTables();
            List<TableDependencyAnalyzer.TableDependency> dependencies = 
                dependencyResult.getDependenciesForTables(tablesToBackup);
            
            // 순환 참조 경고
            if (dependencyResult.hasCircularReferences()) {
                Set<String> circularTables = dependencyResult.getCircularReferenceTables();
                writer.write("-- WARNING: Circular reference detected in tables: " + 
                    String.join(", ", circularTables) + "\n");
                writer.write("-- These tables will be backed up with FOREIGN_KEY_CHECKS=0\n\n");
            }
            
            writer.write("-- Backing up " + orderedTables.size() + " tables in dependency order\n\n");
            
            // 3단계 백업 프로세스
            // 1단계: 외래 키 제약 조건 없이 테이블 구조 생성
            writer.write("-- Phase 1: Table Structures (without Foreign Key constraints)\n");
            for (String tableName : orderedTables) {
                backupTableStructureOnly(connection, writer, tableName, DatabaseType.MYSQL);
            }
            writer.write("\n");
            
            // 2단계: 의존성 순서대로 데이터 삽입
            writer.write("-- Phase 2: Data Insertion (in dependency order)\n");
            for (String tableName : orderedTables) {
                backupTableDataOnly(connection, writer, tableName, DatabaseType.MYSQL);
            }
            writer.write("\n");
            
            // 3단계: 외래 키 제약 조건 추가
            if (!dependencies.isEmpty()) {
                writer.write("-- Phase 3: Foreign Key Constraints\n");
                for (TableDependencyAnalyzer.TableDependency dependency : dependencies) {
                    addForeignKeyConstraint(writer, dependency, DatabaseType.MYSQL);
                }
                writer.write("\n");
            }
            
        } catch (SQLException e) {
            // 의존성 분석 실패 시 기존 방식으로 백업
            writer.write("-- WARNING: Dependency analysis failed, using original backup method\n");
            writer.write("-- Error: " + e.getMessage() + "\n\n");
            
            for (String tableName : tablesToBackup) {
                backupTable(connection, writer, tableName, DatabaseType.MYSQL);
            }
        }
        
        writer.write("SET FOREIGN_KEY_CHECKS=1;\n");
    }
    
    /**
     * PostgreSQL 데이터베이스 백업 실행
     */
    private void executePostgreSQLBackup(Connection connection, BufferedWriter writer, String schemaName, BackupConfig config) 
            throws SQLException, IOException {
        
        writer.write("-- PostgreSQL Database Backup by db-backup4j\n");
        writer.write("-- Generated: " + LocalDateTime.now() + "\n");
        writer.write("-- Schema: " + schemaName + "\n\n");
        
        // 테이블 목록 조회
        List<String> allTables = new ArrayList<>();
        try (Statement stmt = connection.createStatement();
             PreparedStatement pstmt = connection.prepareStatement(
                 "SELECT table_name FROM information_schema.tables WHERE table_schema = ?")) {
            
            pstmt.setString(1, schemaName);
            try (ResultSet tables = pstmt.executeQuery()) {
                while (tables.next()) {
                    allTables.add(tables.getString(1));
                }
            }
        }
        
        // 테이블 필터링 적용
        TableFilter.FilterResult filterResult = TableFilter.filterTablesWithResult(
            allTables,
            DatabaseType.POSTGRESQL,
            config.getDatabase().isExcludeSystemTables(),
            config.getDatabase().getExcludeTablePatterns(),
            config.getDatabase().getIncludeTablePatterns()
        );
        
        List<String> tablesToBackup = filterResult.getIncludedTables();
        
        // 필터링 결과 로그 출력
        if (filterResult.hasExcludedTables()) {
            writer.write("-- Filtered out " + filterResult.getExcludedCount() + " tables\n");
            writer.write("-- Backing up " + filterResult.getIncludedCount() + " tables\n\n");
        }
        
        // 의존성 분석 및 올바른 순서로 백업  
        try {
            TableDependencyAnalyzer.DependencyAnalysisResult dependencyResult = 
                TableDependencyAnalyzer.analyzeDependencies(connection, DatabaseType.POSTGRESQL, tablesToBackup, schemaName);
                
            List<String> orderedTables = dependencyResult.getOrderedTables();
            List<TableDependencyAnalyzer.TableDependency> dependencies = 
                dependencyResult.getDependenciesForTables(tablesToBackup);
            
            // 순환 참조 경고
            if (dependencyResult.hasCircularReferences()) {
                Set<String> circularTables = dependencyResult.getCircularReferenceTables();
                writer.write("-- WARNING: Circular reference detected in tables: " + 
                    String.join(", ", circularTables) + "\n");
                writer.write("-- These tables will require manual constraint handling\n\n");
            }
            
            writer.write("-- Backing up " + orderedTables.size() + " tables in dependency order\n\n");
            
            // 3단계 백업 프로세스
            // 1단계: 외래 키 제약 조건 없이 테이블 구조 생성
            writer.write("-- Phase 1: Table Structures (without Foreign Key constraints)\n");
            for (String tableName : orderedTables) {
                backupTableStructureOnly(connection, writer, tableName, DatabaseType.POSTGRESQL);
            }
            writer.write("\n");
            
            // 2단계: 의존성 순서대로 데이터 삽입
            writer.write("-- Phase 2: Data Insertion (in dependency order)\n");  
            for (String tableName : orderedTables) {
                backupTableDataOnly(connection, writer, tableName, DatabaseType.POSTGRESQL);
            }
            writer.write("\n");
            
            // 3단계: 외래 키 제약 조건 추가
            if (!dependencies.isEmpty()) {
                writer.write("-- Phase 3: Foreign Key Constraints\n");
                for (TableDependencyAnalyzer.TableDependency dependency : dependencies) {
                    addForeignKeyConstraint(writer, dependency, DatabaseType.POSTGRESQL);
                }
                writer.write("\n");
            }
            
        } catch (SQLException e) {
            // 의존성 분석 실패 시 기존 방식으로 백업
            writer.write("-- WARNING: Dependency analysis failed, using original backup method\n");
            writer.write("-- Error: " + e.getMessage() + "\n\n");
            
            for (String tableName : tablesToBackup) {
                backupTable(connection, writer, tableName, DatabaseType.POSTGRESQL);
            }
        }
    }
    
    /**
     * 테이블 백업 실행
     */
    private void backupTable(Connection connection, BufferedWriter writer, String tableName, DatabaseType dbType) 
            throws SQLException, IOException {
        // 테이블 구조 백업
        backupTableStructure(connection, writer, tableName, dbType);
        
        // 테이블 데이터 백업
        backupTableData(connection, writer, tableName, dbType);
        
        writer.write("\n");
    }
    
    /**
     * 테이블 구조 백업
     */
    private void backupTableStructure(Connection connection, BufferedWriter writer, String tableName, DatabaseType dbType) 
            throws SQLException, IOException {
        
        if (dbType == DatabaseType.MYSQL) {
            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery("SHOW CREATE TABLE " + tableName)) {
                
                if (rs.next()) {
                    writer.write("DROP TABLE IF EXISTS `" + tableName + "`;\n");
                    writer.write(rs.getString(2) + ";\n\n");
                }
            }
        } else if (dbType == DatabaseType.POSTGRESQL) {
            // PostgreSQL의 경우 정보 스키마를 이용한 CREATE TABLE 구문 생성
            writer.write("DROP TABLE IF EXISTS \"" + tableName + "\";\n");
            generatePostgreSQLCreateTable(connection, writer, tableName);
        }
    }
    
    /**
     * 테이블 데이터 백업
     */
    private void backupTableData(Connection connection, BufferedWriter writer, String tableName, DatabaseType dbType) 
            throws SQLException, IOException {
        
        // 테이블명 유효성 검사
        if (!SqlUtils.isValidSqlIdentifier(tableName)) {
            throw new IllegalArgumentException("Invalid table name: " + tableName);
        }
        
        String quotedTableName = dbType == DatabaseType.MYSQL ? 
            SqlUtils.quoteMySqlIdentifier(tableName) : 
            SqlUtils.quotePostgreSqlIdentifier(tableName);
        
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM " + quotedTableName)) {
            
            ResultSetMetaData metaData = rs.getMetaData();
            int columnCount = metaData.getColumnCount();
            
            while (rs.next()) {
                StringBuilder insertSQL = new StringBuilder();
                if (dbType == DatabaseType.MYSQL) {
                    insertSQL.append("INSERT INTO ").append(SqlUtils.quoteMySqlIdentifier(tableName)).append(" VALUES (");
                } else {
                    insertSQL.append("INSERT INTO ").append(SqlUtils.quotePostgreSqlIdentifier(tableName)).append(" VALUES (");
                }
                
                for (int i = 1; i <= columnCount; i++) {
                    if (i > 1) insertSQL.append(", ");
                    
                    Object value = rs.getObject(i);
                    if (value == null) {
                        insertSQL.append("NULL");
                    } else if (value instanceof String) {
                        insertSQL.append(SqlUtils.escapeSqlString(value.toString()));
                    } else {
                        insertSQL.append(value);
                    }
                }
                
                insertSQL.append(");\n");
                writer.write(insertSQL.toString());
            }
        }
    }
    
    /**
     * PostgreSQL CREATE TABLE 구문 생성
     */
    private void generatePostgreSQLCreateTable(Connection connection, BufferedWriter writer, String tableName) 
            throws SQLException, IOException {
        
        StringBuilder createTable = new StringBuilder();
        createTable.append("CREATE TABLE \"").append(tableName).append("\" (\n");
        
        // 컬럼 정보 조회
        String columnQuery = "SELECT column_name, data_type, character_maximum_length, " +
            "is_nullable, column_default " +
            "FROM information_schema.columns " +
            "WHERE table_name = ? AND table_schema = 'public' " +
            "ORDER BY ordinal_position";
            
        try (PreparedStatement stmt = connection.prepareStatement(columnQuery)) {
            stmt.setString(1, tableName);
            ResultSet rs = stmt.executeQuery();
            
            boolean first = true;
            while (rs.next()) {
                if (!first) {
                    createTable.append(",\n");
                }
                first = false;
                
                String columnName = rs.getString("column_name");
                String dataType = rs.getString("data_type");
                Integer maxLength = rs.getInt("character_maximum_length");
                String isNullable = rs.getString("is_nullable");
                String columnDefault = rs.getString("column_default");
                
                createTable.append("  \"").append(columnName).append("\" ");
                
                // 데이터 타입 처리
                if ("character varying".equals(dataType) && maxLength > 0) {
                    createTable.append("VARCHAR(").append(maxLength).append(")");
                } else if ("character".equals(dataType)  && maxLength > 0) {
                    createTable.append("CHAR(").append(maxLength).append(")");
                } else {
                    createTable.append(dataType.toUpperCase());
                }
                
                // NULL 제약 조건
                if ("NO".equals(isNullable)) {
                    createTable.append(" NOT NULL");
                }
                
                // 기본값
                if (columnDefault != null && !columnDefault.trim().isEmpty()) {
                    createTable.append(" DEFAULT ").append(columnDefault);
                }
            }
        }
        
        // 기본 키 정보 조회
        String pkQuery = "SELECT a.attname " +
            "FROM pg_index i " +
            "JOIN pg_attribute a ON a.attrelid = i.indrelid AND a.attnum = ANY(i.indkey) " +
            "WHERE i.indrelid = ?::regclass AND i.indisprimary";
            
        try (PreparedStatement stmt = connection.prepareStatement(pkQuery)) {
            stmt.setString(1, tableName);
            ResultSet rs = stmt.executeQuery();
            
            StringBuilder pkColumns = new StringBuilder();
            while (rs.next()) {
                if (pkColumns.length() > 0) {
                    pkColumns.append(", ");
                }
                pkColumns.append("\"").append(rs.getString("attname")).append("\"");
            }
            
            if (pkColumns.length() > 0) {
                createTable.append(",\n  PRIMARY KEY (").append(pkColumns).append(")");
            }
        } catch (SQLException e) {
            // 기본 키 조회 실패시 무시하고 계속 진행
        }
        
        createTable.append("\n);\n\n");
        writer.write(createTable.toString());
    }
    
    /**
     * 테이블 구조만 백업 (외래 키 제약 조건 제외)
     */
    private void backupTableStructureOnly(Connection connection, BufferedWriter writer, String tableName, DatabaseType dbType) 
            throws SQLException, IOException {
        
        if (dbType == DatabaseType.MYSQL) {
            // MySQL: SHOW CREATE TABLE에서 외래 키 제약 조건 제거
            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery("SHOW CREATE TABLE `" + tableName + "`")) {
                
                if (rs.next()) {
                    writer.write("DROP TABLE IF EXISTS `" + tableName + "`;\n");
                    String createTableSql = rs.getString(2);
                    
                    // 외래 키 제약 조건 제거
                    String cleanedSql = removeForeignKeyConstraints(createTableSql);
                    writer.write(cleanedSql + ";\n\n");
                }
            }
        } else if (dbType == DatabaseType.POSTGRESQL) {
            // PostgreSQL: 기존 방식 사용 (이미 외래 키 제약 조건이 제외됨)
            writer.write("DROP TABLE IF EXISTS \"" + tableName + "\";\n");
            generatePostgreSQLCreateTable(connection, writer, tableName);
        }
    }
    
    /**
     * 테이블 데이터만 백업
     */
    private void backupTableDataOnly(Connection connection, BufferedWriter writer, String tableName, DatabaseType dbType) 
            throws SQLException, IOException {
        
        // 테이블명 유효성 검사
        if (!SqlUtils.isValidSqlIdentifier(tableName)) {
            throw new IllegalArgumentException("Invalid table name: " + tableName);
        }
        
        String quotedTableName = dbType == DatabaseType.MYSQL ? 
            SqlUtils.quoteMySqlIdentifier(tableName) : 
            SqlUtils.quotePostgreSqlIdentifier(tableName);
        
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM " + quotedTableName)) {
            
            ResultSetMetaData metaData = rs.getMetaData();
            int columnCount = metaData.getColumnCount();
            
            while (rs.next()) {
                StringBuilder insertSQL = new StringBuilder();
                if (dbType == DatabaseType.MYSQL) {
                    insertSQL.append("INSERT INTO ").append(SqlUtils.quoteMySqlIdentifier(tableName)).append(" VALUES (");
                } else {
                    insertSQL.append("INSERT INTO ").append(SqlUtils.quotePostgreSqlIdentifier(tableName)).append(" VALUES (");
                }
                
                for (int i = 1; i <= columnCount; i++) {
                    if (i > 1) insertSQL.append(", ");
                    
                    Object value = rs.getObject(i);
                    if (value == null) {
                        insertSQL.append("NULL");
                    } else if (value instanceof String) {
                        insertSQL.append(SqlUtils.escapeSqlString(value.toString()));
                    } else {
                        insertSQL.append(value);
                    }
                }
                
                insertSQL.append(");\n");
                writer.write(insertSQL.toString());
            }
        }
        
        writer.write("\n");
    }
    
    /**
     * 외래 키 제약 조건 추가
     */
    private void addForeignKeyConstraint(BufferedWriter writer, TableDependencyAnalyzer.TableDependency dependency, DatabaseType dbType) 
            throws IOException {
        
        if (dbType == DatabaseType.MYSQL) {
            writer.write(String.format("ALTER TABLE `%s` ADD CONSTRAINT `%s` FOREIGN KEY (`%s`) REFERENCES `%s` (`%s`);\n",
                dependency.getChildTable(),
                dependency.getConstraintName(),
                dependency.getChildColumn(),
                dependency.getParentTable(),
                dependency.getParentColumn()));
        } else if (dbType == DatabaseType.POSTGRESQL) {
            writer.write(String.format("ALTER TABLE \"%s\" ADD CONSTRAINT \"%s\" FOREIGN KEY (\"%s\") REFERENCES \"%s\" (\"%s\");\n",
                dependency.getChildTable(),
                dependency.getConstraintName(),
                dependency.getChildColumn(),
                dependency.getParentTable(),
                dependency.getParentColumn()));
        }
    }
    
    /**
     * CREATE TABLE 문에서 외래 키 제약 조건을 제거합니다.
     */
    private String removeForeignKeyConstraints(String createTableSql) {
        // MySQL의 외래 키 제약 조건 패턴을 제거
        // CONSTRAINT `fk_name` FOREIGN KEY (`column`) REFERENCES `table` (`column`)
        String result = createTableSql.replaceAll(",\\s*CONSTRAINT\\s+`[^`]+`\\s+FOREIGN\\s+KEY\\s+\\([^)]+\\)\\s+REFERENCES\\s+`[^`]+`\\s+\\([^)]+\\)(?:\\s+ON\\s+DELETE\\s+\\w+)?(?:\\s+ON\\s+UPDATE\\s+\\w+)?", "");
        
        // KEY 정의 뒤에 남아있는 외래 키 참조도 제거
        result = result.replaceAll(",\\s*FOREIGN\\s+KEY\\s+\\([^)]+\\)\\s+REFERENCES\\s+`[^`]+`\\s+\\([^)]+\\)(?:\\s+ON\\s+DELETE\\s+\\w+)?(?:\\s+ON\\s+UPDATE\\s+\\w+)?", "");
        
        return result;
    }
    
    /**
     * S3 백업 실행
     */
    private void executeS3Backup(File backupFile, BackupConfig config, BackupResult.Builder resultBuilder) {
        S3BackupConfig s3Config = config.getS3();
        if (!s3Config.isEnabled()) {
            return;
        }
        
        try {
            // S3 객체 키 생성 (접두어 + 파일명)
            String objectKey = s3Config.getPrefix() != null ? 
                s3Config.getPrefix() + "/" + backupFile.getName() : 
                backupFile.getName();
            
            // S3 업로드 실행
            S3Utils s3Utils = new S3Utils(s3Config);
            s3Utils.uploadFile(backupFile, objectKey);
            
        } catch (IOException e) {
            String errorMessage = "S3 backup failed: " + e.getMessage();
            
            resultBuilder.addError(new BackupResult.BackupError(
                "s3", 
                errorMessage, 
                e, 
                LocalDateTime.now()
            ));
            
            // S3 백업 실패는 전체 백업을 실패로 처리하지 않음
            // 로컬 백업은 성공했을 수 있음
        }
    }
    
    
    /**
     * 디스크 공간 부족 오류인지 확인
     */
    private boolean isDiskSpaceError(Exception e) {
        String message = e.getMessage();
        if (message == null) {
            return false;
        }
        
        String lowerMessage = message.toLowerCase();
        return lowerMessage.contains("no space left on device") ||
               lowerMessage.contains("not enough space") ||
               lowerMessage.contains("disk full") ||
               lowerMessage.contains("insufficient disk space") ||
               e instanceof FileSystemException;
    }
}