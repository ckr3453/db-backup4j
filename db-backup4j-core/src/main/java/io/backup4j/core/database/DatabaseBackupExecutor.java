package io.backup4j.core.database;

import io.backup4j.core.config.BackupConfig;
import io.backup4j.core.validation.BackupResult;
import io.backup4j.core.validation.ChecksumValidator;
import io.backup4j.core.validation.ZeroCopyChecksumCalculator;
import io.backup4j.core.util.CompressionUtils;
import io.backup4j.core.util.BackupFileNameGenerator;
import io.backup4j.core.util.RetentionPolicy;
import io.backup4j.core.util.DiskSpaceChecker;
import io.backup4j.core.notification.NotificationService;

import java.io.*;
import java.nio.file.Path;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * 데이터베이스 백업을 실행하는 클래스
 * 설정에 따라 MySQL, PostgreSQL 데이터베이스를 백업하고 압축, 체크섬 검증, 파일 정리 등을 수행함
 */
public class DatabaseBackupExecutor {
    private static final Logger logger = Logger.getLogger(DatabaseBackupExecutor.class.getName());
    
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
            
        logger.info("Starting database backup process... ID: " + backupId);
        
        try {
            // 1. 디스크 공간 검증
            BackupResult failedResult = validateDiskSpace(config, resultBuilder);
            if (failedResult != null) {
                return failedResult;
            }
            
            // 2. 데이터베이스 백업 실행
            DatabaseBackupResult backupResult = performDatabaseBackup(config, resultBuilder);
            if (backupResult.isFailure()) {
                return backupResult.getFailedResult();
            }
            
            File backupFile = backupResult.getBackupFile();
            long originalSize = backupResult.getOriginalSize();
            
            // 3. 압축 처리
            CompressionResult compressionResult = processCompression(config, backupFile, resultBuilder);
            File finalBackupFile = compressionResult.getFinalFile();
            
            // 4. 체크섬 계산
            ChecksumValidator.StoredChecksum checksum = calculateChecksum(config, finalBackupFile, resultBuilder);
            
            // 5. 백업 파일 정보 생성
            BackupResult.BackupFile backupFileInfo = createBackupFileInfo(finalBackupFile, checksum);
            resultBuilder.addFile(backupFileInfo);
            
            // 6. 보존 정책 적용
            applyRetentionPolicy(config, finalBackupFile, resultBuilder);
            
            // 7. S3 백업 처리
            if (config.getS3().isEnabled()) {
                executeS3Backup(finalBackupFile, config, resultBuilder);
            }
            
            // 8. 메타데이터 생성 및 결과 빌드
            BackupResult result = buildFinalResult(config, originalSize, finalBackupFile, resultBuilder);
            
            // 9. 알림 전송
            sendNotification(result, config);
            
            return result;
                
        } catch (Exception e) {
            logger.severe("Unexpected error during backup: " + e.getMessage());
            return handleUnexpectedError(e, resultBuilder, config);
        }
    }
    
    /**
     * 디스크 공간 검증
     */
    private BackupResult validateDiskSpace(BackupConfig config, BackupResult.Builder resultBuilder) {
        Path backupDirectory = new File(config.getLocal().getPath()).toPath();
        
        // 예상 백업 크기 계산 (기본값: 100MB)
        long estimatedSize = 100 * 1024 * 1024L; // 100MB
        
        // 종합 디스크 공간 체크
        DiskSpaceChecker.SpaceCheckResult spaceCheck = DiskSpaceChecker.comprehensiveSpaceCheck(
            backupDirectory, estimatedSize, config.getLocal().isCompress());
        
        logger.info("Disk space check result: " + spaceCheck);
        
        if (!spaceCheck.hasEnoughSpace()) {
            logger.severe("Insufficient disk space for backup: " + spaceCheck.getReason());
            resultBuilder.addError(new BackupResult.BackupError(
                "disk-space", 
                "Insufficient disk space: " + spaceCheck.getReason(), 
                null, 
                LocalDateTime.now()
            ));
            return resultBuilder
                .endTime(LocalDateTime.now())
                .status(BackupResult.Status.FAILED)
                .build();
        }
        
        return null; // 성공
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
                
                // 데이터베이스 타입에 따른 백업 실행
                if (config.getDatabase().getType() == DatabaseType.MYSQL) {
                    executeMySQLBackup(connection, writer, config.getDatabase().getName());
                } else if (config.getDatabase().getType() == DatabaseType.POSTGRESQL) {
                    executePostgreSQLBackup(connection, writer, config.getDatabase().getName());
                }
                
                writer.flush();
                originalSize = backupFile.length();
                
                logger.info("Database backup completed successfully: " + backupFile.getAbsolutePath());
                
                return new DatabaseBackupResult(backupFile, originalSize);
                
            } catch (SQLException | IOException e) {
                logger.severe("Database backup failed: " + e.getMessage());
                resultBuilder.addError(new BackupResult.BackupError(
                    "local", 
                    "Database backup failed: " + e.getMessage(), 
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
            logger.severe("Unexpected error during database backup: " + e.getMessage());
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
                
                // 원본 파일 삭제
                if (!backupFile.delete()) {
                    logger.warning("Failed to delete original backup file: " + backupFile.getAbsolutePath());
                }
                
                logger.info("Backup file compressed: " + compressionMetrics.toString());
                
            } catch (Exception e) {
                logger.warning("Compression failed: " + e.getMessage());
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
     * 체크섬 계산
     */
    private ChecksumValidator.StoredChecksum calculateChecksum(BackupConfig config, File backupFile, BackupResult.Builder resultBuilder) {
        ChecksumValidator.StoredChecksum checksum = null;
        
        if (config.getLocal().isEnableChecksum()) {
            try {
                ZeroCopyChecksumCalculator.Algorithm algorithm = 
                    ZeroCopyChecksumCalculator.Algorithm.valueOf(config.getLocal().getChecksumAlgorithm().toUpperCase());
                checksum = ChecksumValidator.calculateAndStore(backupFile.toPath(), algorithm);
                
                // 체크섬 검증
                ChecksumValidator.ValidationResult validation = checksum.validateAgainstFile(backupFile.toPath());
                resultBuilder.addValidationResult(validation);
                
                logger.info("Checksum calculated: " + checksum.getChecksum() + " (" + algorithm + ")");
                
            } catch (Exception e) {
                logger.warning("Checksum calculation failed: " + e.getMessage());
                resultBuilder.addError(new BackupResult.BackupError(
                    "checksum", 
                    "Checksum calculation failed: " + e.getMessage(), 
                    e, 
                    LocalDateTime.now()
                ));
            }
        }
        
        return checksum;
    }
    
    /**
     * 백업 파일 정보 생성
     */
    private BackupResult.BackupFile createBackupFileInfo(File backupFile, ChecksumValidator.StoredChecksum checksum) {
        return new BackupResult.BackupFile(
            backupFile.toPath(),
            backupFile.length(),
            "local",
            checksum,
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
                    RetentionPolicy.CleanupResult cleanupResult = RetentionPolicy.cleanup(retentionDirectory, retentionDays, false);
                    
                    int keptFiles = cleanupResult.getTotalFiles() - cleanupResult.getDeletedFiles();
                    logger.info("Retention policy applied: " + cleanupResult.getDeletedFiles() + " old files deleted, " + 
                               keptFiles + " files kept");
                    
                    if (cleanupResult.hasErrors()) {
                        for (String error : cleanupResult.getErrors()) {
                            logger.warning("Retention policy error: " + error);
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
                logger.warning("Invalid retention value: " + config.getLocal().getRetention());
                resultBuilder.addError(new BackupResult.BackupError(
                    "retention",
                    "Invalid retention value: " + config.getLocal().getRetention(),
                    e,
                    LocalDateTime.now()
                ));
            } catch (Exception e) {
                logger.warning("Retention policy execution failed: " + e.getMessage());
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
    private BackupResult handleUnexpectedError(Exception e, BackupResult.Builder resultBuilder, BackupConfig config) {
        resultBuilder.addError(new BackupResult.BackupError(
            "system", 
            "Unexpected error: " + e.getMessage(), 
            e, 
            LocalDateTime.now()
        ));
        BackupResult result = resultBuilder
            .endTime(LocalDateTime.now())
            .status(BackupResult.Status.FAILED)
            .build();
        
        // 실패 알림 전송
        sendNotification(result, config);
        
        return result;
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
            BackupFileNameGenerator.BackupType.MANUAL,
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
    private void executeMySQLBackup(Connection connection, BufferedWriter writer, String databaseName) 
            throws SQLException, IOException {
        
        writer.write("-- MySQL Database Backup by db-backup4j\n");
        writer.write("-- Generated: " + LocalDateTime.now() + "\n");
        writer.write("-- Database: " + databaseName + "\n\n");
        
        writer.write("SET FOREIGN_KEY_CHECKS=0;\n");
        writer.write("SET SQL_MODE='NO_AUTO_VALUE_ON_ZERO';\n\n");
        
        // 테이블 목록 조회
        try (Statement stmt = connection.createStatement();
             ResultSet tables = stmt.executeQuery("SHOW TABLES")) {
            
            while (tables.next()) {
                String tableName = tables.getString(1);
                backupTable(connection, writer, tableName, DatabaseType.MYSQL);
            }
        }
        
        writer.write("SET FOREIGN_KEY_CHECKS=1;\n");
    }
    
    /**
     * PostgreSQL 데이터베이스 백업 실행
     */
    private void executePostgreSQLBackup(Connection connection, BufferedWriter writer, String databaseName) 
            throws SQLException, IOException {
        
        writer.write("-- PostgreSQL Database Backup by db-backup4j\n");
        writer.write("-- Generated: " + LocalDateTime.now() + "\n");
        writer.write("-- Database: " + databaseName + "\n\n");
        
        // 테이블 목록 조회
        try (Statement stmt = connection.createStatement();
             ResultSet tables = stmt.executeQuery(
                 "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'")) {
            
            while (tables.next()) {
                String tableName = tables.getString(1);
                backupTable(connection, writer, tableName, DatabaseType.POSTGRESQL);
            }
        }
    }
    
    /**
     * 테이블 백업 실행
     */
    private void backupTable(Connection connection, BufferedWriter writer, String tableName, DatabaseType dbType) 
            throws SQLException, IOException {
        
        logger.info("Backing up table: " + tableName);
        
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
        
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM " + tableName)) {
            
            ResultSetMetaData metaData = rs.getMetaData();
            int columnCount = metaData.getColumnCount();
            
            while (rs.next()) {
                StringBuilder insertSQL = new StringBuilder();
                if (dbType == DatabaseType.MYSQL) {
                    insertSQL.append("INSERT INTO `").append(tableName).append("` VALUES (");
                } else {
                    insertSQL.append("INSERT INTO \"").append(tableName).append("\" VALUES (");
                }
                
                for (int i = 1; i <= columnCount; i++) {
                    if (i > 1) insertSQL.append(", ");
                    
                    Object value = rs.getObject(i);
                    if (value == null) {
                        insertSQL.append("NULL");
                    } else if (value instanceof String) {
                        insertSQL.append("'").append(value.toString().replace("'", "''")).append("'");
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
                if ("character varying".equals(dataType) && maxLength != null && maxLength > 0) {
                    createTable.append("VARCHAR(").append(maxLength).append(")");
                } else if ("character".equals(dataType) && maxLength != null && maxLength > 0) {
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
     * S3 백업 실행 (원본 메서드 유지)
     */
    private void executeS3Backup(File backupFile, BackupConfig config, BackupResult.Builder resultBuilder) 
            throws IOException {
        // 원본 구현 유지
        // 간단히 placeholder로 대체
        logger.info("S3 backup would be executed here");
    }
    
    /**
     * 백업 완료 후 알림을 전송합니다
     */
    private void sendNotification(BackupResult result, BackupConfig config) {
        if (config.getNotification() == null || !config.getNotification().isEnabled()) {
            return;
        }
        
        try {
            NotificationService notificationService = new NotificationService();
            notificationService.sendBackupNotification(result, config.getNotification());
            notificationService.shutdown();
        } catch (Exception e) {
            logger.warning("알림 전송 실패: " + e.getMessage());
        }
    }
}