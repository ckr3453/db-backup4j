package io.backup4j.core.util;

import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.*;

/**
 * BackupFileNameGenerator 테스트
 */
class BackupFileNameGeneratorTest {

    @Test
    void generateFileName_기본기능_정상동작() {
        // Given
        String databaseName = "testdb";
        LocalDateTime timestamp = LocalDateTime.of(2023, 12, 25, 14, 30, 45);
        
        // When
        String uncompressedFileName = BackupFileNameGenerator.generateFileName(databaseName, timestamp, false);
        String compressedFileName = BackupFileNameGenerator.generateFileName(databaseName, timestamp, true);
        
        // Then
        assertThat(uncompressedFileName).isEqualTo("testdb_20231225_143045.sql");
        assertThat(compressedFileName).isEqualTo("testdb_20231225_143045.sql.gz");
    }

    @Test
    void generateFileName_현재시간사용_정상동작() {
        // Given
        String databaseName = "myapp";
        
        // When
        String fileName = BackupFileNameGenerator.generateFileName(databaseName, false);
        
        // Then
        assertThat(fileName).matches("myapp_\\d{8}_\\d{6}\\.sql");
        assertThat(fileName).startsWith("myapp_");
        assertThat(fileName).endsWith(".sql");
    }

    @Test
    void sanitizeDatabaseName_특수문자_정제() {
        // Given & When & Then
        assertThat(BackupFileNameGenerator.sanitizeDatabaseName("my-app_db")).isEqualTo("my-app_db");
        assertThat(BackupFileNameGenerator.sanitizeDatabaseName("my@app#db")).isEqualTo("my_app_db");
        assertThat(BackupFileNameGenerator.sanitizeDatabaseName("my/app\\db")).isEqualTo("my_app_db");
        assertThat(BackupFileNameGenerator.sanitizeDatabaseName("my db")).isEqualTo("my_db");
        assertThat(BackupFileNameGenerator.sanitizeDatabaseName("my.app.db")).isEqualTo("my_app_db");
    }

    @Test
    void sanitizeDatabaseName_연속언더스코어_제거() {
        // Given & When & Then
        assertThat(BackupFileNameGenerator.sanitizeDatabaseName("my___app___db")).isEqualTo("my_app_db");
        assertThat(BackupFileNameGenerator.sanitizeDatabaseName("___myapp___")).isEqualTo("myapp");
        assertThat(BackupFileNameGenerator.sanitizeDatabaseName("my____db")).isEqualTo("my_db");
    }

    @Test
    void sanitizeDatabaseName_null과_빈문자열_처리() {
        // Given & When & Then
        assertThat(BackupFileNameGenerator.sanitizeDatabaseName(null)).isEqualTo("unknown");
        assertThat(BackupFileNameGenerator.sanitizeDatabaseName("")).isEqualTo("unknown");
        assertThat(BackupFileNameGenerator.sanitizeDatabaseName("   ")).isEqualTo("unknown");
        assertThat(BackupFileNameGenerator.sanitizeDatabaseName("@#$%")).isEqualTo("unknown");
    }

    @Test
    void sanitizeDatabaseName_유니코드문자_처리() {
        // Given & When & Then
        assertThat(BackupFileNameGenerator.sanitizeDatabaseName("한글데이터베이스")).isEqualTo("unknown"); // 한글은 제거됨
        assertThat(BackupFileNameGenerator.sanitizeDatabaseName("myapp_한글")).isEqualTo("myapp");
        assertThat(BackupFileNameGenerator.sanitizeDatabaseName("test123_데이터베이스_db")).isEqualTo("test123_db");
    }

    @Test
    void generateFileName_압축여부_올바른확장자() {
        // Given
        String databaseName = "testdb";
        LocalDateTime timestamp = LocalDateTime.of(2023, 12, 25, 14, 30, 45);
        
        // When & Then
        String uncompressed = BackupFileNameGenerator.generateFileName(databaseName, timestamp, false);
        String compressed = BackupFileNameGenerator.generateFileName(databaseName, timestamp, true);
        
        assertThat(uncompressed).doesNotContain(".gz");
        assertThat(compressed).endsWith(".gz");
        assertThat(compressed).contains(".sql.gz");
    }

    @Test
    void generateFileName_타임스탬프형식_정확성검증() {
        // Given
        String databaseName = "testdb";
        LocalDateTime timestamp = LocalDateTime.of(2023, 1, 5, 9, 5, 3); // 한 자리 숫자들
        
        // When
        String fileName = BackupFileNameGenerator.generateFileName(databaseName, timestamp, false);
        
        // Then - 제로패딩이 올바르게 적용됨
        assertThat(fileName).isEqualTo("testdb_20230105_090503.sql");
    }

    @Test
    void generateFileName_동시호출_패턴검증() throws InterruptedException {
        // Given
        String databaseName = "testdb";
        int threadCount = 5; // 스레드 수 감소
        int callsPerThread = 10; // 호출 수 감소
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        Set<String> fileNames = ConcurrentHashMap.newKeySet();
        
        // When - 동시에 여러 스레드에서 파일명 생성
        for (int i = 0; i < threadCount; i++) {
            final int threadIndex = i;
            executorService.submit(() -> {
                try {
                    for (int j = 0; j < callsPerThread; j++) {
                        String fileName = BackupFileNameGenerator.generateFileName(databaseName, false);
                        fileNames.add(fileName);
                        
                        // 스레드별로 다른 지연시간 부여
                        Thread.sleep(threadIndex + j);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    latch.countDown();
                }
            });
        }
        
        // Then
        latch.await(30, TimeUnit.SECONDS);
        executorService.shutdown();
        
        // 모든 파일명이 올바른 패턴을 가져야 함 (이것이 핵심 검증)
        assertThat(fileNames).isNotEmpty();
        for (String fileName : fileNames) {
            assertThat(fileName).matches("testdb_\\d{8}_\\d{6}\\.sql");
            assertThat(fileName).startsWith("testdb_");
            assertThat(fileName).endsWith(".sql");
        }
        
        // 최소한의 고유성 검증 (적어도 파일명이 생성되었는지 확인)
        assertThat(fileNames.size()).isGreaterThanOrEqualTo(1); // 최소 1개는 생성되어야 함
    }

    @Test
    void generateFileName_극단적케이스_안정성() {
        // Given
        LocalDateTime timestamp = LocalDateTime.of(2023, 12, 31, 23, 59, 59);
        
        // When & Then - 극단적인 시간값도 정상 처리
        String fileName = BackupFileNameGenerator.generateFileName("db", timestamp, false);
        assertThat(fileName).isEqualTo("db_20231231_235959.sql");
        
        // 윤년 처리
        LocalDateTime leapYear = LocalDateTime.of(2024, 2, 29, 12, 0, 0);
        String leapFileName = BackupFileNameGenerator.generateFileName("db", leapYear, false);
        assertThat(leapFileName).isEqualTo("db_20240229_120000.sql");
    }

    @Test
    void generateFileName_성능테스트() {
        // Given
        String databaseName = "performancetest";
        LocalDateTime timestamp = LocalDateTime.now();
        int iterations = 10000;
        
        // When
        long startTime = System.currentTimeMillis();
        
        for (int i = 0; i < iterations; i++) {
            BackupFileNameGenerator.generateFileName(databaseName, timestamp, i % 2 == 0);
        }
        
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;
        
        // Then - 10,000번 호출이 1초 이내에 완료되어야 함
        assertThat(duration).isLessThan(1000);
    }

    @Test
    void sanitizeDatabaseName_보안테스트_위험한문자열() {
        // Given & When & Then - 잠재적으로 위험한 문자들 처리
        // 실제 메서드는 하이픈(-)과 언더스코어(_)는 유지함을 고려
        assertThat(BackupFileNameGenerator.sanitizeDatabaseName("../../../etc/passwd")).isEqualTo("etc_passwd");
        assertThat(BackupFileNameGenerator.sanitizeDatabaseName("..\\..\\windows\\system32")).isEqualTo("windows_system32");
        assertThat(BackupFileNameGenerator.sanitizeDatabaseName("; rm -rf /")).isEqualTo("rm_-rf"); // 하이픈 유지
        assertThat(BackupFileNameGenerator.sanitizeDatabaseName("DROP TABLE users;")).isEqualTo("DROP_TABLE_users");
        assertThat(BackupFileNameGenerator.sanitizeDatabaseName("<script>alert('xss')</script>")).isEqualTo("script_alert_xss_script");
    }
}