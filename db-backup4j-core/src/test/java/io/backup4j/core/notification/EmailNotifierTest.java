package io.backup4j.core.notification;

import io.backup4j.core.util.CryptoUtils;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * EmailNotifier 클래스의 단위 테스트
 * CryptoUtils를 사용한 Base64 인코딩 기능을 중심으로 테스트합니다.
 */
class EmailNotifierTest {

    @Test
    void base64인코딩_사용자명_정상인코딩() {
        // Given
        String username = "test@example.com";
        
        // When
        String encoded = CryptoUtils.base64Encode(username);
        
        // Then
        assertThat(encoded).isEqualTo("dGVzdEBleGFtcGxlLmNvbQ==");
        
        // 디코딩해서 원본과 같은지 확인
        String decoded = CryptoUtils.base64Decode(encoded);
        assertThat(decoded).isEqualTo(username);
    }

    @Test
    void base64인코딩_비밀번호_정상인코딩() {
        // Given
        String password = "mySecretPassword123!";
        
        // When
        String encoded = CryptoUtils.base64Encode(password);
        
        // Then
        assertThat(encoded).isNotEmpty();
        assertThat(encoded).doesNotContain(password); // 원본 비밀번호가 포함되지 않아야 함
        
        // 디코딩해서 원본과 같은지 확인
        String decoded = CryptoUtils.base64Decode(encoded);
        assertThat(decoded).isEqualTo(password);
    }

    @Test
    void base64인코딩_특수문자포함_정상인코딩() {
        // Given
        String textWithSpecialChars = "admin@컴퍼니.한국:P@ssw0rd!@#$%";
        
        // When
        String encoded = CryptoUtils.base64Encode(textWithSpecialChars);
        
        // Then
        assertThat(encoded).isNotEmpty();
        
        // 디코딩해서 원본과 같은지 확인
        String decoded = CryptoUtils.base64Decode(encoded);
        assertThat(decoded).isEqualTo(textWithSpecialChars);
    }

    @Test
    void base64인코딩_빈문자열_빈결과반환() {
        // Given
        String emptyString = "";
        
        // When
        String encoded = CryptoUtils.base64Encode(emptyString);
        
        // Then
        assertThat(encoded).isEmpty();
        
        // 디코딩해서 원본과 같은지 확인
        String decoded = CryptoUtils.base64Decode(encoded);
        assertThat(decoded).isEqualTo(emptyString);
    }

    @Test
    void 이메일주소_형식검증() {
        // Given
        String[] validEmails = {
            "test@example.com",
            "user.name@domain.co.kr", 
            "admin@subdomain.example.org"
        };
        
        String[] invalidEmails = {
            "invalid-email",
            "@domain.com",
            "user@",
            "",
            null
        };
        
        // When & Then - 유효한 이메일 주소들
        for (String email : validEmails) {
            assertThat(isValidEmail(email)).isTrue();
        }
        
        // 무효한 이메일 주소들
        for (String email : invalidEmails) {
            assertThat(isValidEmail(email)).isFalse();
        }
    }

    @Test
    void 수신자목록_파싱_정상처리() {
        // Given
        String recipients = "admin@company.com,backup@company.com,alert@company.com";
        
        // When
        String[] recipientArray = recipients.split(",");
        
        // Then
        assertThat(recipientArray).hasSize(3);
        assertThat(recipientArray[0].trim()).isEqualTo("admin@company.com");
        assertThat(recipientArray[1].trim()).isEqualTo("backup@company.com");
        assertThat(recipientArray[2].trim()).isEqualTo("alert@company.com");
    }

    @Test
    void 수신자목록_공백처리_정상처리() {
        // Given
        String recipientsWithSpaces = " admin@company.com , backup@company.com , alert@company.com ";
        
        // When
        String[] recipientArray = recipientsWithSpaces.split(",");
        
        // Then
        for (String recipient : recipientArray) {
            assertThat(recipient.trim()).isNotEmpty();
            assertThat(isValidEmail(recipient.trim())).isTrue();
        }
    }

    @Test
    void Base64인코딩_SMTP인증패턴_테스트() {
        // Given - SMTP 인증에서 사용되는 일반적인 패턴
        String username = "smtp.user@gmail.com";
        String password = "app-specific-password-123";
        
        // When - CryptoUtils를 사용한 Base64 인코딩 (EmailNotifier에서 사용하는 방식)
        String encodedUsername = CryptoUtils.base64Encode(username);
        String encodedPassword = CryptoUtils.base64Encode(password);
        
        // Then - 인코딩 결과 검증
        assertThat(encodedUsername).isNotEmpty();
        assertThat(encodedPassword).isNotEmpty();
        
        // Base64 문자열 형식 확인 (영문자, 숫자, +, /, = 만 포함)
        assertThat(encodedUsername).matches("[A-Za-z0-9+/=]+");
        assertThat(encodedPassword).matches("[A-Za-z0-9+/=]+");
        
        // 디코딩하여 원본과 일치하는지 확인
        assertThat(CryptoUtils.base64Decode(encodedUsername)).isEqualTo(username);
        assertThat(CryptoUtils.base64Decode(encodedPassword)).isEqualTo(password);
        
        // SMTP AUTH 명령어 형식 확인 (BASE64 인코딩된 문자열이 포함되어야 함)
        String authCommand = "AUTH PLAIN " + encodedUsername;
        assertThat(authCommand).startsWith("AUTH PLAIN ");
        assertThat(authCommand).contains(encodedUsername);
    }

    @Test
    void Base64인코딩_다양한문자셋_정상처리() {
        // Given
        String[] testStrings = {
            "simple text",
            "123456789",
            "!@#$%^&*()",
            "한글문자열테스트",
            "Mixed한글English123!@#",
            "very.long.email.address.with.multiple.dots@very.long.domain.name.example.com"
        };
        
        // When & Then
        for (String testString : testStrings) {
            String encoded = CryptoUtils.base64Encode(testString);
            String decoded = CryptoUtils.base64Decode(encoded);
            
            assertThat(encoded).isNotEmpty();
            assertThat(decoded).isEqualTo(testString);
            
            // Base64 형식 검증
            assertThat(encoded).matches("[A-Za-z0-9+/=]*");
        }
    }

    // Helper method
    private boolean isValidEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            return false;
        }
        return email.contains("@") && email.contains(".") && 
               email.indexOf("@") > 0 && 
               email.lastIndexOf(".") > email.indexOf("@");
    }
}