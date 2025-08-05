package io.backup4j.core.database;

import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.*;

class TableDependencyAnalyzerTest {

    @Test
    void tableDependency_생성자와_접근자_정상동작() {
        // Given
        String childTable = "orders";
        String parentTable = "users";
        String constraintName = "fk_orders_user_id";
        String childColumn = "user_id";
        String parentColumn = "id";
        
        // When
        TableDependencyAnalyzer.TableDependency dependency = new TableDependencyAnalyzer.TableDependency(
            childTable, parentTable, constraintName, childColumn, parentColumn);
        
        // Then
        assertThat(dependency.getChildTable()).isEqualTo(childTable);
        assertThat(dependency.getParentTable()).isEqualTo(parentTable);
        assertThat(dependency.getConstraintName()).isEqualTo(constraintName);
        assertThat(dependency.getChildColumn()).isEqualTo(childColumn);
        assertThat(dependency.getParentColumn()).isEqualTo(parentColumn);
    }

    @Test
    void tableDependency_toString_올바른_형식() {
        // Given
        TableDependencyAnalyzer.TableDependency dependency = new TableDependencyAnalyzer.TableDependency(
            "orders", "users", "fk_orders_user_id", "user_id", "id");
        
        // When
        String result = dependency.toString();
        
        // Then
        assertThat(result).isEqualTo("orders.user_id -> users.id (fk_orders_user_id)");
    }

    @Test
    void tableDependency_equals와_hashCode_정상동작() {
        // Given
        TableDependencyAnalyzer.TableDependency dep1 = new TableDependencyAnalyzer.TableDependency(
            "orders", "users", "fk_orders_user_id", "user_id", "id");
        TableDependencyAnalyzer.TableDependency dep2 = new TableDependencyAnalyzer.TableDependency(
            "orders", "users", "fk_orders_user_id", "user_id", "id");
        TableDependencyAnalyzer.TableDependency dep3 = new TableDependencyAnalyzer.TableDependency(
            "orders", "products", "fk_orders_product_id", "product_id", "id");
        
        // When & Then
        assertThat(dep1).isEqualTo(dep2);
        assertThat(dep1).isNotEqualTo(dep3);
        assertThat(dep1.hashCode()).isEqualTo(dep2.hashCode());
        assertThat(dep1.hashCode()).isNotEqualTo(dep3.hashCode());
    }

    @Test
    void dependencyAnalysisResult_기본_기능_테스트() {
        // Given
        List<String> orderedTables = Arrays.asList("users", "products", "orders");
        List<TableDependencyAnalyzer.TableDependency> dependencies = Arrays.asList(
            new TableDependencyAnalyzer.TableDependency("orders", "users", "fk1", "user_id", "id"),
            new TableDependencyAnalyzer.TableDependency("orders", "products", "fk2", "product_id", "id")
        );
        Set<String> circularTables = Collections.emptySet();
        
        // When
        TableDependencyAnalyzer.DependencyAnalysisResult result = 
            new TableDependencyAnalyzer.DependencyAnalysisResult(orderedTables, dependencies, circularTables);
        
        // Then
        assertThat(result.getOrderedTables()).containsExactly("users", "products", "orders");
        assertThat(result.getDependencies()).hasSize(2);
        assertThat(result.getCircularReferenceTables()).isEmpty();
        assertThat(result.hasCircularReferences()).isFalse();
    }

    @Test
    void dependencyAnalysisResult_순환참조_감지() {
        // Given
        List<String> orderedTables = Arrays.asList("table_a", "table_b", "table_c");
        List<TableDependencyAnalyzer.TableDependency> dependencies = Arrays.asList(
            new TableDependencyAnalyzer.TableDependency("table_b", "table_a", "fk1", "a_id", "id"),
            new TableDependencyAnalyzer.TableDependency("table_c", "table_b", "fk2", "b_id", "id"),
            new TableDependencyAnalyzer.TableDependency("table_a", "table_c", "fk3", "c_id", "id")  // 순환 참조
        );
        Set<String> circularTables = new HashSet<String>() {{
            add("table_a");
            add("table_b");
            add("table_c");
        }};
        
        // When
        TableDependencyAnalyzer.DependencyAnalysisResult result = 
            new TableDependencyAnalyzer.DependencyAnalysisResult(orderedTables, dependencies, circularTables);
        
        // Then
        assertThat(result.hasCircularReferences()).isTrue();
        assertThat(result.getCircularReferenceTables()).containsExactlyInAnyOrder("table_a", "table_b", "table_c");
    }

    @Test
    void dependencyAnalysisResult_특정테이블_의존성_필터링() {
        // Given
        List<String> orderedTables = Arrays.asList("users", "products", "orders", "order_items");
        List<TableDependencyAnalyzer.TableDependency> dependencies = Arrays.asList(
            new TableDependencyAnalyzer.TableDependency("orders", "users", "fk1", "user_id", "id"),
            new TableDependencyAnalyzer.TableDependency("order_items", "orders", "fk2", "order_id", "id"),
            new TableDependencyAnalyzer.TableDependency("order_items", "products", "fk3", "product_id", "id")
        );
        Set<String> circularTables = Collections.emptySet();
        
        TableDependencyAnalyzer.DependencyAnalysisResult result = 
            new TableDependencyAnalyzer.DependencyAnalysisResult(orderedTables, dependencies, circularTables);
        
        // When
        List<TableDependencyAnalyzer.TableDependency> filteredDeps = 
            result.getDependenciesForTables(Arrays.asList("orders", "order_items"));
        
        // Then
        assertThat(filteredDeps).hasSize(1);
        assertThat(filteredDeps.get(0).getChildTable()).isEqualTo("order_items");
        assertThat(filteredDeps.get(0).getParentTable()).isEqualTo("orders");
    }

    @Test
    void dependencyAnalysisResult_부분집합_순서_반환() {
        // Given
        List<String> orderedTables = Arrays.asList("users", "products", "orders", "order_items");
        List<TableDependencyAnalyzer.TableDependency> dependencies = Collections.emptyList();
        Set<String> circularTables = Collections.emptySet();
        
        TableDependencyAnalyzer.DependencyAnalysisResult result = 
            new TableDependencyAnalyzer.DependencyAnalysisResult(orderedTables, dependencies, circularTables);
        
        // When
        List<String> subsetOrder = result.getOrderedTablesForSubset(Arrays.asList("products", "users", "orders"));
        
        // Then
        assertThat(subsetOrder).containsExactly("users", "products", "orders");
    }

    @Test
    void dependencyAnalysisResult_toString_정상동작() {
        // Given
        List<String> orderedTables = Arrays.asList("users", "orders");
        List<TableDependencyAnalyzer.TableDependency> dependencies = Arrays.asList(
            new TableDependencyAnalyzer.TableDependency("orders", "users", "fk1", "user_id", "id")
        );
        Set<String> circularTables = Collections.emptySet();
        
        TableDependencyAnalyzer.DependencyAnalysisResult result = 
            new TableDependencyAnalyzer.DependencyAnalysisResult(orderedTables, dependencies, circularTables);
        
        // When
        String str = result.toString();
        
        // Then
        assertThat(str).contains("tables=2");
        assertThat(str).contains("dependencies=1");
        assertThat(str).contains("circularRefs=false");
    }

    @Test
    void dependencyAnalysisResult_불변성_보장() {
        // Given
        List<String> orderedTables = new ArrayList<>(Arrays.asList("users", "orders"));
        List<TableDependencyAnalyzer.TableDependency> dependencies = new ArrayList<>(Arrays.asList(
            new TableDependencyAnalyzer.TableDependency("orders", "users", "fk1", "user_id", "id")
        ));
        Set<String> circularTables = new HashSet<>();
        
        TableDependencyAnalyzer.DependencyAnalysisResult result = 
            new TableDependencyAnalyzer.DependencyAnalysisResult(orderedTables, dependencies, circularTables);
        
        // When - 원본 컬렉션 수정 시도
        orderedTables.add("products");
        dependencies.clear();
        circularTables.add("test");
        
        // Then - 결과 객체는 영향받지 않음
        assertThat(result.getOrderedTables()).containsExactly("users", "orders");
        assertThat(result.getDependencies()).hasSize(1);
        assertThat(result.getCircularReferenceTables()).isEmpty();
    }

    @Test
    void dependencyAnalysisResult_반환_컬렉션_불변성() {
        // Given
        List<String> orderedTables = Arrays.asList("users", "orders");
        List<TableDependencyAnalyzer.TableDependency> dependencies = Arrays.asList(
            new TableDependencyAnalyzer.TableDependency("orders", "users", "fk1", "user_id", "id")
        );
        Set<String> circularTables = Collections.emptySet();
        
        TableDependencyAnalyzer.DependencyAnalysisResult result = 
            new TableDependencyAnalyzer.DependencyAnalysisResult(orderedTables, dependencies, circularTables);
        
        // When & Then - 반환된 컬렉션 수정 시도시 예외 발생
        assertThatThrownBy(() -> result.getOrderedTables().add("products"))
            .isInstanceOf(UnsupportedOperationException.class);
        
        assertThatThrownBy(() -> result.getDependencies().clear())
            .isInstanceOf(UnsupportedOperationException.class);
        
        assertThatThrownBy(() -> result.getCircularReferenceTables().add("test"))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    // 실제 데이터베이스 연결이 필요한 테스트는 통합 테스트에서 수행
    // 여기서는 단위 테스트로 로직만 검증

    @Test
    void topologicalSort_단순한_의존성_체인() {
        // 실제 위상 정렬 로직은 private 메서드이므로 
        // 전체 analyzeDependencies 메서드를 통해 간접적으로 테스트해야 함
        // 이는 통합 테스트에서 데이터베이스와 함께 테스트됨
        
        // 여기서는 결과 객체의 동작만 테스트
        List<String> simpleChain = Arrays.asList("parent", "child", "grandchild");
        List<TableDependencyAnalyzer.TableDependency> dependencies = Arrays.asList(
            new TableDependencyAnalyzer.TableDependency("child", "parent", "fk1", "parent_id", "id"),
            new TableDependencyAnalyzer.TableDependency("grandchild", "child", "fk2", "child_id", "id")
        );
        
        TableDependencyAnalyzer.DependencyAnalysisResult result = 
            new TableDependencyAnalyzer.DependencyAnalysisResult(simpleChain, dependencies, Collections.emptySet());
        
        assertThat(result.getOrderedTables()).hasSize(3);
        assertThat(result.getDependencies()).hasSize(2);
        assertThat(result.hasCircularReferences()).isFalse();
    }

    @Test
    void 빈_입력_처리() {
        // Given
        List<String> emptyTables = Collections.emptyList();
        List<TableDependencyAnalyzer.TableDependency> emptyDependencies = Collections.emptyList();
        Set<String> emptyCircular = Collections.emptySet();
        
        // When
        TableDependencyAnalyzer.DependencyAnalysisResult result = 
            new TableDependencyAnalyzer.DependencyAnalysisResult(emptyTables, emptyDependencies, emptyCircular);
        
        // Then
        assertThat(result.getOrderedTables()).isEmpty();
        assertThat(result.getDependencies()).isEmpty();
        assertThat(result.getCircularReferenceTables()).isEmpty();
        assertThat(result.hasCircularReferences()).isFalse();
    }
}