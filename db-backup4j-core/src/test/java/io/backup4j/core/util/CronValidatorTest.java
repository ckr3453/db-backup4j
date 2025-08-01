package io.backup4j.core.config;

import io.backup4j.core.util.CronValidator;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

/**
 * CronValidator 테스트
 * cron-utils를 사용한 cron 검증 테스트
 */
class CronValidatorTest {

    @Test
    void isValid_유효한_기본_표현식() {
        // Given & When & Then
        assertThat(CronValidator.isValid("0 2 * * *")).isTrue();      // 매일 오전 2시
        assertThat(CronValidator.isValid("30 14 1 * *")).isTrue();    // 매월 1일 오후 2시 30분
        assertThat(CronValidator.isValid("0 9 * * 1")).isTrue();      // 매주 월요일 오전 9시
        assertThat(CronValidator.isValid("* * * * *")).isTrue();      // 매분마다
        assertThat(CronValidator.isValid("59 23 31 12 7")).isTrue();  // 경계값
    }
    @Test
    void isValid_잘못된_필드_개수() {
        // Given & When & Then
        assertThat(CronValidator.isValid("0 2 * *")).isFalse();       // 4필드
        assertThat(CronValidator.isValid("0 2 * * * *")).isFalse();   // 6필드
        assertThat(CronValidator.isValid("")).isFalse();              // 빈 문자열
        assertThat(CronValidator.isValid(null)).isFalse();            // null
    }
    @Test
    void isValid_범위_초과_값들() {
        // Given & When & Then
        // 분 범위 초과 (0-59)
        assertThat(CronValidator.isValid("60 2 * * *")).isFalse();
        assertThat(CronValidator.isValid("-1 2 * * *")).isFalse();

        // 시간 범위 초과 (0-23)
        assertThat(CronValidator.isValid("0 24 * * *")).isFalse();
        assertThat(CronValidator.isValid("0 -1 * * *")).isFalse();

        // 일 범위 초과 (1-31)
        assertThat(CronValidator.isValid("0 2 0 * *")).isFalse();
        assertThat(CronValidator.isValid("0 2 32 * *")).isFalse();

        // 월 범위 초과 (1-12)
        assertThat(CronValidator.isValid("0 2 1 0 *")).isFalse();
        assertThat(CronValidator.isValid("0 2 1 13 *")).isFalse();

        // 요일 범위 초과 (0-7)
        assertThat(CronValidator.isValid("0 2 * * -1")).isFalse();
        assertThat(CronValidator.isValid("0 2 * * 8")).isFalse();
    }
    @Test
    void isValid_복잡한_형식_지원() {
        // Given & When & Then - cron-utils는 복잡한 형식들을 지원
        assertThat(CronValidator.isValid("*/5 * * * *")).isTrue();        // 스텝
        assertThat(CronValidator.isValid("0 9-17 * * *")).isTrue();       // 범위
        assertThat(CronValidator.isValid("0,15,30,45 * * * *")).isTrue(); // 리스트
        assertThat(CronValidator.isValid("0 9-17 * * 1-5")).isTrue();     // 복잡한 조합
    }
    @Test
    void isValid_잘못된_문자() {
        // Given & When & Then
        assertThat(CronValidator.isValid("abc 2 * * *")).isFalse();       // 문자
        assertThat(CronValidator.isValid("0.5 2 * * *")).isFalse();       // 소수점
        assertThat(CronValidator.isValid("0 2 ? * *")).isFalse();         // ? 문자 (Unix cron에서 지원하지 않음)
        assertThat(CronValidator.isValid("0 2 L * *")).isFalse();         // L 문자 (Unix cron에서 지원하지 않음)  
        assertThat(CronValidator.isValid("0 2 # * *")).isFalse();         // 특수문자
    }
    @Test
    void isValid_경계값_테스트() {
        // Given & When & Then
        // 분 경계값 (0-59)
        assertThat(CronValidator.isValid("0 * * * *")).isTrue();
        assertThat(CronValidator.isValid("59 * * * *")).isTrue();

        // 시간 경계값 (0-23)
        assertThat(CronValidator.isValid("* 0 * * *")).isTrue();
        assertThat(CronValidator.isValid("* 23 * * *")).isTrue();

        // 일 경계값 (1-31)
        assertThat(CronValidator.isValid("* * 1 * *")).isTrue();
        assertThat(CronValidator.isValid("* * 31 * *")).isTrue();

        // 월 경계값 (1-12)
        assertThat(CronValidator.isValid("* * * 1 *")).isTrue();
        assertThat(CronValidator.isValid("* * * 12 *")).isTrue();

        // 요일 경계값 (0-7, 0과 7 모두 일요일)
        assertThat(CronValidator.isValid("* * * * 0")).isTrue();
        assertThat(CronValidator.isValid("* * * * 7")).isTrue();
    }
    @Test
    void validate_상세한_오류_메시지() {
        // Given & When & Then
        CronValidator.ValidationResult result1 = CronValidator.validate("0 2 * *");
        assertThat(result1.isValid()).isFalse();
        assertThat(result1.getErrorMessage()).contains("Invalid cron expression");

        CronValidator.ValidationResult result2 = CronValidator.validate("60 2 * * *");
        assertThat(result2.isValid()).isFalse();
        assertThat(result2.getErrorMessage()).contains("Invalid cron expression");

        CronValidator.ValidationResult result3 = CronValidator.validate("*/5 * * * *");
        assertThat(result3.isValid()).isTrue();  // cron-utils는 이것을 지원
        assertThat(result3.getErrorMessage()).isNull();

        CronValidator.ValidationResult result4 = CronValidator.validate("0 2 * * *");
        assertThat(result4.isValid()).isTrue();
        assertThat(result4.getErrorMessage()).isNull();
    }
    @Test
    void isValid_실제_사용_케이스들() {
        // Given & When & Then
        // 매일 오전 2시
        assertThat(CronValidator.isValid("0 2 * * *")).isTrue();

        // 매시간 정각
        assertThat(CronValidator.isValid("0 * * * *")).isTrue();

        // 매월 1일 자정
        assertThat(CronValidator.isValid("0 0 1 * *")).isTrue();

        // 매주 일요일 오전 3시 (0과 7 모두 테스트)
        assertThat(CronValidator.isValid("0 3 * * 0")).isTrue();
        assertThat(CronValidator.isValid("0 3 * * 7")).isTrue();

        // 평일 점심시간 (각각 따로 설정해야 함)
        assertThat(CronValidator.isValid("0 12 * * 1")).isTrue(); // 월요일
        assertThat(CronValidator.isValid("0 12 * * 2")).isTrue(); // 화요일
        assertThat(CronValidator.isValid("0 12 * * 3")).isTrue(); // 수요일
        assertThat(CronValidator.isValid("0 12 * * 4")).isTrue(); // 목요일
        assertThat(CronValidator.isValid("0 12 * * 5")).isTrue(); // 금요일
    }
    @Test
    void validate_null과_빈_문자열() {
        // Given & When & Then
        assertThat(CronValidator.validate(null).isValid()).isFalse();
        assertThat(CronValidator.validate("").isValid()).isFalse();
        assertThat(CronValidator.validate("   ").isValid()).isFalse();
    }
}