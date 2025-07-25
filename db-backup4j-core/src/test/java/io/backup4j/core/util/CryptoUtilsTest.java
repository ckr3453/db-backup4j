package io.backup4j.core.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.*;

/**
 * CryptoUtils 클래스의 단위 테스트
 */
class CryptoUtilsTest {

    @TempDir
    Path tempDir;

    @Test
    void bytesToHex_정상적인바이트배열_16진수문자열반환() {
        // Given
        byte[] bytes = {0x00, 0x01, 0x0F, (byte) 0xFF, (byte) 0xAB};
        
        // When
        String result = CryptoUtils.bytesToHex(bytes);
        
        // Then
        assertThat(result).isEqualTo("00010fffab");
    }

    @Test
    void bytesToHex_빈배열_빈문자열반환() {
        // Given
        byte[] bytes = {};
        
        // When
        String result = CryptoUtils.bytesToHex(bytes);
        
        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void calculateSha256_파일_정확한해시반환() throws IOException {
        // Given
        Path testFile = tempDir.resolve("test.txt");
        String content = "Hello, World!";
        Files.write(testFile, content.getBytes(StandardCharsets.UTF_8));
        
        // When
        String result = CryptoUtils.calculateSha256(testFile.toFile());
        
        // Then
        // "Hello, World!"의 SHA-256 해시
        assertThat(result).isEqualTo("dffd6021bb2bd5b0af676290809ec3a53191dd81c7f70a4b28688a362182986f");
    }

    @Test
    void calculateSha256_문자열_정확한해시반환() throws IOException {
        // Given
        String data = "Hello, World!";
        
        // When
        String result = CryptoUtils.calculateSha256(data);
        
        // Then
        assertThat(result).isEqualTo("dffd6021bb2bd5b0af676290809ec3a53191dd81c7f70a4b28688a362182986f");
    }

    @Test
    void calculateSha256_존재하지않는파일_IOException발생() {
        // Given
        File nonExistentFile = new File("non-existent-file.txt");
        
        // When & Then
        assertThatThrownBy(() -> CryptoUtils.calculateSha256(nonExistentFile))
                .isInstanceOf(IOException.class);
    }

    @Test
    void calculateSha256_빈문자열_정확한해시반환() throws IOException {
        // Given
        String data = "";
        
        // When
        String result = CryptoUtils.calculateSha256(data);
        
        // Then
        // 빈 문자열의 SHA-256 해시
        assertThat(result).isEqualTo("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
    }

    @Test
    void hmacSha256_정상적인키와데이터_HMAC바이트배열반환() throws Exception {
        // Given
        String key = "secret-key";
        String data = "test-data";
        
        // When
        byte[] result = CryptoUtils.hmacSha256(key.getBytes(StandardCharsets.UTF_8), data);
        
        // Then
        assertThat(result).isNotNull();
        assertThat(result).hasSize(32); // SHA-256은 32바이트 출력
    }

    @Test
    void hmacSha256Hex_정상적인키와데이터_16진수문자열반환() throws Exception {
        // Given
        String key = "secret-key";
        String data = "test-data";
        
        // When
        String result = CryptoUtils.hmacSha256Hex(key.getBytes(StandardCharsets.UTF_8), data);
        
        // Then
        assertThat(result).isNotNull();
        assertThat(result).hasSize(64); // 32바이트 * 2 = 64자리 16진수
        assertThat(result).matches("[0-9a-f]{64}"); // 16진수 패턴 확인
    }

    @Test
    void hmacSha256_동일한입력_동일한결과반환() throws Exception {
        // Given
        String key = "test-key";
        String data = "test-message";
        
        // When
        byte[] result1 = CryptoUtils.hmacSha256(key.getBytes(StandardCharsets.UTF_8), data);
        byte[] result2 = CryptoUtils.hmacSha256(key.getBytes(StandardCharsets.UTF_8), data);
        
        // Then
        assertThat(result1).isEqualTo(result2);
    }

    @Test
    void base64Encode_정상적인문자열_Base64문자열반환() {
        // Given
        String text = "Hello, World!";
        
        // When
        String result = CryptoUtils.base64Encode(text);
        
        // Then
        assertThat(result).isEqualTo("SGVsbG8sIFdvcmxkIQ==");
    }

    @Test
    void base64Encode_빈문자열_빈Base64문자열반환() {
        // Given
        String text = "";
        
        // When
        String result = CryptoUtils.base64Encode(text);
        
        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void base64Encode_한글문자열_정상인코딩() {
        // Given
        String text = "안녕하세요";
        
        // When
        String result = CryptoUtils.base64Encode(text);
        
        // Then
        assertThat(result).isNotEmpty();
        // 디코딩해서 원본과 같은지 확인
        String decoded = CryptoUtils.base64Decode(result);
        assertThat(decoded).isEqualTo(text);
    }

    @Test
    void base64Decode_정상적인Base64문자열_원본문자열반환() {
        // Given
        String encodedText = "SGVsbG8sIFdvcmxkIQ==";
        
        // When
        String result = CryptoUtils.base64Decode(encodedText);
        
        // Then
        assertThat(result).isEqualTo("Hello, World!");
    }

    @Test
    void base64EncodeDecode_왕복변환_원본과동일() {
        // Given
        String originalText = "Test message with special chars: @#$%^&*()";
        
        // When
        String encoded = CryptoUtils.base64Encode(originalText);
        String decoded = CryptoUtils.base64Decode(encoded);
        
        // Then
        assertThat(decoded).isEqualTo(originalText);
    }

    @Test
    void getAwsSignatureKey_정상적인파라미터_서명키반환() throws Exception {
        // Given
        String key = "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY";
        String dateStamp = "20220101";
        String regionName = "us-east-1";
        String serviceName = "s3";
        
        // When
        byte[] result = CryptoUtils.getAwsSignatureKey(key, dateStamp, regionName, serviceName);
        
        // Then
        assertThat(result).isNotNull();
        assertThat(result).hasSize(32); // HMAC-SHA256은 32바이트
    }

    @Test
    void getAwsSignatureKey_동일한파라미터_동일한키반환() throws Exception {
        // Given
        String key = "test-key";
        String dateStamp = "20220101";
        String regionName = "ap-northeast-2";
        String serviceName = "s3";
        
        // When
        byte[] result1 = CryptoUtils.getAwsSignatureKey(key, dateStamp, regionName, serviceName);
        byte[] result2 = CryptoUtils.getAwsSignatureKey(key, dateStamp, regionName, serviceName);
        
        // Then
        assertThat(result1).isEqualTo(result2);
    }

    @Test
    void getAwsSignatureKey_다른날짜_다른키반환() throws Exception {
        // Given
        String key = "test-key";
        String regionName = "ap-northeast-2";
        String serviceName = "s3";
        
        // When
        byte[] result1 = CryptoUtils.getAwsSignatureKey(key, "20220101", regionName, serviceName);
        byte[] result2 = CryptoUtils.getAwsSignatureKey(key, "20220102", regionName, serviceName);
        
        // Then
        assertThat(result1).isNotEqualTo(result2);
    }

    @Test
    void calculateSha256_큰파일_정상처리() throws IOException {
        // Given
        Path largeFile = tempDir.resolve("large-file.txt");
        StringBuilder content = new StringBuilder();
        for (int i = 0; i < 10000; i++) {
            content.append("This is line ").append(i).append("\n");
        }
        Files.write(largeFile, content.toString().getBytes(StandardCharsets.UTF_8));
        
        // When
        String result = CryptoUtils.calculateSha256(largeFile.toFile());
        
        // Then
        assertThat(result).isNotNull();
        assertThat(result).hasSize(64); // SHA-256은 64자리 16진수
        assertThat(result).matches("[0-9a-f]{64}");
    }
}