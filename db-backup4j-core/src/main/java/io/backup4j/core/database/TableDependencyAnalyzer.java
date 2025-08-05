package io.backup4j.core.database;

import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 테이블 간 외래 키 의존성을 분석하고 올바른 백업/복원 순서를 결정하는 유틸리티 클래스
 * 위상 정렬(Topological Sort) 알고리즘을 사용하여 의존성 순서를 계산합니다.
 */
public class TableDependencyAnalyzer {
    
    private TableDependencyAnalyzer() {
    }
    
    /**
     * 테이블 간 의존성을 나타내는 관계 클래스
     */
    public static class TableDependency {
        private final String childTable;      // 외래 키를 가지는 테이블 (참조하는 테이블)
        private final String parentTable;     // 참조되는 테이블 (피참조 테이블)
        private final String constraintName;  // 제약 조건 이름
        private final String childColumn;     // 외래 키 컬럼
        private final String parentColumn;    // 참조되는 컬럼
        
        public TableDependency(String childTable, String parentTable, String constraintName, 
                             String childColumn, String parentColumn) {
            this.childTable = childTable;
            this.parentTable = parentTable;
            this.constraintName = constraintName;
            this.childColumn = childColumn;
            this.parentColumn = parentColumn;
        }
        
        public String getChildTable() { return childTable; }
        public String getParentTable() { return parentTable; }
        public String getConstraintName() { return constraintName; }
        public String getChildColumn() { return childColumn; }
        public String getParentColumn() { return parentColumn; }
        
        @Override
        public String toString() {
            return String.format("%s.%s -> %s.%s (%s)", 
                childTable, childColumn, parentTable, parentColumn, constraintName);
        }
        
        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            TableDependency that = (TableDependency) obj;
            return Objects.equals(childTable, that.childTable) &&
                   Objects.equals(parentTable, that.parentTable) &&
                   Objects.equals(constraintName, that.constraintName);
        }
        
        @Override
        public int hashCode() {
            return Objects.hash(childTable, parentTable, constraintName);
        }
    }
    
    /**
     * 의존성 분석 결과를 담는 클래스
     */
    public static class DependencyAnalysisResult {
        private final List<String> orderedTables;           // 의존성 순서대로 정렬된 테이블 목록
        private final List<TableDependency> dependencies;   // 모든 의존성 관계
        private final Set<String> circularReferenceTables;  // 순환 참조에 포함된 테이블들
        private final boolean hasCircularReferences;        // 순환 참조 존재 여부
        
        public DependencyAnalysisResult(List<String> orderedTables, 
                                      List<TableDependency> dependencies,
                                      Set<String> circularReferenceTables) {
            this.orderedTables = Collections.unmodifiableList(new ArrayList<>(orderedTables));
            this.dependencies = Collections.unmodifiableList(new ArrayList<>(dependencies));
            this.circularReferenceTables = Collections.unmodifiableSet(new HashSet<>(circularReferenceTables));
            this.hasCircularReferences = !circularReferenceTables.isEmpty();
        }
        
        public List<String> getOrderedTables() { return orderedTables; }
        public List<TableDependency> getDependencies() { return dependencies; }
        public Set<String> getCircularReferenceTables() { return circularReferenceTables; }
        public boolean hasCircularReferences() { return hasCircularReferences; }
        
        /**
         * 특정 테이블의 의존성 관계만 필터링합니다.
         */
        public List<TableDependency> getDependenciesForTables(Collection<String> tableNames) {
            Set<String> tableSet = new HashSet<>(tableNames);
            return dependencies.stream()
                .filter(dep -> tableSet.contains(dep.getChildTable()) && tableSet.contains(dep.getParentTable()))
                .collect(Collectors.toList());
        }
        
        /**
         * 지정된 테이블들만의 순서를 반환합니다.
         */
        public List<String> getOrderedTablesForSubset(Collection<String> tableNames) {
            Set<String> tableSet = new HashSet<>(tableNames);
            return orderedTables.stream()
                .filter(tableSet::contains)
                .collect(Collectors.toList());
        }
        
        @Override
        public String toString() {
            return String.format("DependencyAnalysisResult{tables=%d, dependencies=%d, circularRefs=%s}", 
                orderedTables.size(), dependencies.size(), hasCircularReferences);
        }
    }
    
    /**
     * 테이블 목록의 의존성을 분석하여 올바른 순서를 결정합니다.
     * 
     * @param connection 데이터베이스 연결
     * @param databaseType 데이터베이스 타입
     * @param tableNames 분석할 테이블 목록
     * @param databaseName 데이터베이스 스키마명
     * @return 의존성 분석 결과
     * @throws SQLException 의존성 분석 실패 시
     */
    public static DependencyAnalysisResult analyzeDependencies(Connection connection, 
                                                             DatabaseType databaseType,
                                                             List<String> tableNames, 
                                                             String databaseName) throws SQLException {
        
        // 1. 외래 키 의존성 관계 추출
        List<TableDependency> dependencies = extractForeignKeyDependencies(
            connection, databaseType, tableNames, databaseName);
        
        // 2. 위상 정렬을 통한 순서 결정
        TopologicalSortResult sortResult = performTopologicalSort(tableNames, dependencies);
        
        return new DependencyAnalysisResult(
            sortResult.orderedTables, 
            dependencies, 
            sortResult.circularReferenceTables
        );
    }
    
    /**
     * 데이터베이스에서 외래 키 의존성 관계를 추출합니다.
     */
    private static List<TableDependency> extractForeignKeyDependencies(Connection connection, 
                                                                      DatabaseType databaseType,
                                                                      List<String> tableNames, 
                                                                      String databaseName) throws SQLException {
        
        Set<String> tableSet = new HashSet<>(tableNames);
        List<TableDependency> dependencies;
        
        switch (databaseType) {
            case MYSQL:
                dependencies = extractMySQLDependencies(connection, tableSet, databaseName);
                break;
            case POSTGRESQL:
                dependencies = extractPostgreSQLDependencies(connection, tableSet, databaseName);
                break;
            default:
                throw new IllegalArgumentException("Unsupported database type: " + databaseType);
        }
        
        return dependencies;
    }
    
    /**
     * MySQL에서 외래 키 의존성을 추출합니다.
     */
    private static List<TableDependency> extractMySQLDependencies(Connection connection, 
                                                                 Set<String> tableNames, 
                                                                 String databaseName) throws SQLException {
        
        List<TableDependency> dependencies = new ArrayList<>();
        
        // 빈 테이블 목록인 경우 빈 결과 반환
        if (tableNames.isEmpty()) {
            return dependencies;
        }
        
        String inClausePlaceholders = tableNames.stream().map(t -> "?").collect(Collectors.joining(","));
        
        String query = "SELECT " +
                "kcu.TABLE_NAME as child_table, " +
                "kcu.REFERENCED_TABLE_NAME as parent_table, " +
                "kcu.CONSTRAINT_NAME as constraint_name, " +
                "kcu.COLUMN_NAME as child_column, " +
                "kcu.REFERENCED_COLUMN_NAME as parent_column " +
                "FROM information_schema.KEY_COLUMN_USAGE kcu " +
                "WHERE kcu.REFERENCED_TABLE_SCHEMA = ? " +
                "AND kcu.REFERENCED_TABLE_NAME IS NOT NULL " +
                "AND kcu.TABLE_NAME IN (" + inClausePlaceholders + ") " +
                "AND kcu.REFERENCED_TABLE_NAME IN (" + inClausePlaceholders + ")";
        
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            int paramIndex = 1;
            stmt.setString(paramIndex++, databaseName);
            
            // child_table 조건을 위한 파라미터
            for (String tableName : tableNames) {
                stmt.setString(paramIndex++, tableName);
            }
            
            // parent_table 조건을 위한 파라미터
            for (String tableName : tableNames) {
                stmt.setString(paramIndex++, tableName);
            }
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String childTable = rs.getString("child_table");
                    String parentTable = rs.getString("parent_table");
                    String constraintName = rs.getString("constraint_name");
                    String childColumn = rs.getString("child_column");
                    String parentColumn = rs.getString("parent_column");
                    
                    // 자기 자신을 참조하는 경우는 제외 (순환 참조 단순화)
                    if (!childTable.equals(parentTable)) {
                        dependencies.add(new TableDependency(
                            childTable, parentTable, constraintName, childColumn, parentColumn));
                    }
                }
            }
        }
        
        return dependencies;
    }
    
    /**
     * PostgreSQL에서 외래 키 의존성을 추출합니다.
     */
    private static List<TableDependency> extractPostgreSQLDependencies(Connection connection, 
                                                                       Set<String> tableNames, 
                                                                       String databaseName) throws SQLException {
        
        List<TableDependency> dependencies = new ArrayList<>();
        
        // 빈 테이블 목록인 경우 빈 결과 반환
        if (tableNames.isEmpty()) {
            return dependencies;
        }
        
        String inClausePlaceholders = tableNames.stream().map(t -> "?").collect(Collectors.joining(","));
        
        String query = "SELECT " +
                "tc.table_name as child_table, " +
                "ccu.table_name as parent_table, " +
                "tc.constraint_name as constraint_name, " +
                "kcu.column_name as child_column, " +
                "ccu.column_name as parent_column " +
                "FROM information_schema.table_constraints tc " +
                "JOIN information_schema.key_column_usage kcu " +
                "ON tc.constraint_name = kcu.constraint_name " +
                "AND tc.table_schema = kcu.table_schema " +
                "JOIN information_schema.constraint_column_usage ccu " +
                "ON tc.constraint_name = ccu.constraint_name " +
                "AND tc.table_schema = ccu.table_schema " +
                "WHERE tc.constraint_type = 'FOREIGN KEY' " +
                "AND tc.table_schema = ? " +
                "AND tc.table_name IN (" + inClausePlaceholders + ") " +
                "AND ccu.table_name IN (" + inClausePlaceholders + ")";
        
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            int paramIndex = 1;
            
            // 스키마 파라미터 설정
            stmt.setString(paramIndex++, databaseName);
            
            // child_table 조건을 위한 파라미터
            for (String tableName : tableNames) {
                stmt.setString(paramIndex++, tableName);
            }
            
            // parent_table 조건을 위한 파라미터
            for (String tableName : tableNames) {
                stmt.setString(paramIndex++, tableName);
            }
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String childTable = rs.getString("child_table");
                    String parentTable = rs.getString("parent_table");
                    String constraintName = rs.getString("constraint_name");
                    String childColumn = rs.getString("child_column");
                    String parentColumn = rs.getString("parent_column");
                    
                    // 자기 자신을 참조하는 경우는 제외
                    if (!childTable.equals(parentTable)) {
                        dependencies.add(new TableDependency(
                            childTable, parentTable, constraintName, childColumn, parentColumn));
                    }
                }
            }
        }
        
        return dependencies;
    }
    
    /**
     * 위상 정렬 결과를 담는 내부 클래스
     */
    private static class TopologicalSortResult {
        final List<String> orderedTables;
        final Set<String> circularReferenceTables;
        
        TopologicalSortResult(List<String> orderedTables, Set<String> circularReferenceTables) {
            this.orderedTables = orderedTables;
            this.circularReferenceTables = circularReferenceTables;
        }
    }
    
    /**
     * 위상 정렬을 수행하여 테이블의 의존성 순서를 결정합니다.
     * Kahn's Algorithm을 사용합니다.
     */
    private static TopologicalSortResult performTopologicalSort(List<String> tableNames, 
                                                               List<TableDependency> dependencies) {
        
        // 그래프 구성: 인접 리스트와 진입 차수
        Map<String, List<String>> adjacencyList = new HashMap<>();
        Map<String, Integer> inDegree = new HashMap<>();
        
        // 모든 테이블 초기화
        for (String table : tableNames) {
            adjacencyList.put(table, new ArrayList<>());
            inDegree.put(table, 0);
        }
        
        // 의존성 관계를 그래프에 추가
        for (TableDependency dep : dependencies) {
            String parent = dep.getParentTable();
            String child = dep.getChildTable();
            
            if (adjacencyList.containsKey(parent) && adjacencyList.containsKey(child)) {
                adjacencyList.get(parent).add(child);
                inDegree.put(child, inDegree.get(child) + 1);
            }
        }
        
        // Kahn's Algorithm 실행
        Queue<String> queue = new LinkedList<>();
        List<String> orderedTables = new ArrayList<>();
        
        // 진입 차수가 0인 노드들을 큐에 추가
        for (Map.Entry<String, Integer> entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) {
                queue.offer(entry.getKey());
            }
        }
        
        // 위상 정렬 수행
        while (!queue.isEmpty()) {
            String current = queue.poll();
            orderedTables.add(current);
            
            // 현재 노드에서 나가는 간선들을 제거
            for (String neighbor : adjacencyList.get(current)) {
                int newInDegree = inDegree.get(neighbor) - 1;
                inDegree.put(neighbor, newInDegree);
                
                if (newInDegree == 0) {
                    queue.offer(neighbor);
                }
            }
        }
        
        // 순환 참조 감지: 정렬되지 않은 노드들이 있으면 순환 참조 존재
        Set<String> circularReferenceTables = new HashSet<>();
        for (Map.Entry<String, Integer> entry : inDegree.entrySet()) {
            if (entry.getValue() > 0) {
                circularReferenceTables.add(entry.getKey());
            }
        }
        
        // 순환 참조가 있는 테이블들을 순서에 추가 (알파벳 순)
        List<String> remainingTables = new ArrayList<>(circularReferenceTables);
        remainingTables.sort(String::compareToIgnoreCase);
        orderedTables.addAll(remainingTables);
        
        return new TopologicalSortResult(orderedTables, circularReferenceTables);
    }
}