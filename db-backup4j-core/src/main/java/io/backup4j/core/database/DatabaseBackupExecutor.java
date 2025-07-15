package io.backup4j.core.database;

import io.backup4j.core.config.BackupConfig;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.logging.Logger;

public class DatabaseBackupExecutor {
    private static final Logger logger = Logger.getLogger(DatabaseBackupExecutor.class.getName());
    
    public void executeBackup(BackupConfig config) throws SQLException, IOException {
        logger.info("Starting database backup process...");
        
        // 백업 파일 생성
        File backupFile = createBackupFile(config);
        
        // 데이터베이스 연결 및 백업 실행
        try (Connection connection = DatabaseConnection.getConnection(config.getDatabase());
             BufferedWriter writer = new BufferedWriter(new FileWriter(backupFile))) {
            
            // 데이터베이스 타입에 따른 백업 실행
            if (config.getDatabase().getType() == DatabaseType.MYSQL) {
                executeMySQLBackup(connection, writer, config.getDatabase().getName());
            } else if (config.getDatabase().getType() == DatabaseType.POSTGRESQL) {
                executePostgreSQLBackup(connection, writer, config.getDatabase().getName());
            }
            
            logger.info("Database backup completed successfully: " + backupFile.getAbsolutePath());
            
        } catch (SQLException | IOException e) {
            logger.severe("Database backup failed: " + e.getMessage());
            throw e;
        }
    }
    
    private File createBackupFile(BackupConfig config) throws IOException {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String fileName = String.format("%s_%s_backup.sql", 
            config.getDatabase().getName(), timestamp);
        
        File backupDir = new File(config.getLocal().getPath());
        if (!backupDir.exists()) {
            boolean created = backupDir.mkdirs();
            if (!created) {
                throw new IOException("Failed to create backup directory: " + backupDir.getAbsolutePath());
            }
        }
        
        return new File(backupDir, fileName);
    }
    
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
    
    private void backupTable(Connection connection, BufferedWriter writer, String tableName, DatabaseType dbType) 
            throws SQLException, IOException {
        
        logger.info("Backing up table: " + tableName);
        
        // 테이블 구조 백업
        backupTableStructure(connection, writer, tableName, dbType);
        
        // 테이블 데이터 백업
        backupTableData(connection, writer, tableName, dbType);
        
        writer.write("\n");
    }
    
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
}