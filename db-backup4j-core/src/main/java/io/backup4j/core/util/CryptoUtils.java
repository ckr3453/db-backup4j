package io.backup4j.core.util;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/**
 * 암호화, 해시, 인코딩 관련 유틸리티 클래스
 * 프로젝트 전반에서 사용되는 암호화 관련 공통 기능을 제공합니다.
 */
public class CryptoUtils {
    
    private static final int DEFAULT_BUFFER_SIZE = 8192;
    
    private CryptoUtils() {
    }
    
    /**
     * 바이트 배열을 16진수 문자열로 변환합니다.
     * 
     * @param bytes 변환할 바이트 배열
     * @return 16진수 문자열
     */
    public static String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }
    
    /**
     * 파일의 SHA-256 해시를 계산합니다.
     * 
     * @param file 해시를 계산할 파일
     * @return SHA-256 해시 문자열
     * @throws IOException 파일 읽기 실패 시
     */
    public static String calculateSha256(File file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            
            try (FileInputStream fis = new FileInputStream(file);
                 BufferedInputStream bis = new BufferedInputStream(fis)) {
                
                byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];
                int bytesRead;
                
                while ((bytesRead = bis.read(buffer)) != -1) {
                    digest.update(buffer, 0, bytesRead);
                }
            }
            
            return bytesToHex(digest.digest());
            
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("SHA-256 algorithm not available", e);
        }
    }
    
    /**
     * 문자열의 SHA-256 해시를 계산합니다.
     * 
     * @param data 해시를 계산할 문자열
     * @return SHA-256 해시 문자열
     * @throws IOException 해시 계산 실패 시
     */
    public static String calculateSha256(String data) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("SHA-256 algorithm not available", e);
        }
    }
    
    /**
     * HMAC-SHA256을 계산합니다.
     * 
     * @param key HMAC 키
     * @param data 서명할 데이터
     * @return HMAC-SHA256 바이트 배열
     * @throws Exception HMAC 계산 실패 시
     */
    public static byte[] hmacSha256(byte[] key, String data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
    }
    
    /**
     * HMAC-SHA256을 16진수 문자열로 계산합니다.
     * 
     * @param key HMAC 키
     * @param data 서명할 데이터
     * @return HMAC-SHA256 16진수 문자열
     * @throws Exception HMAC 계산 실패 시
     */
    public static String hmacSha256Hex(byte[] key, String data) throws Exception {
        return bytesToHex(hmacSha256(key, data));
    }
    
    /**
     * Base64로 문자열을 인코딩합니다.
     * 
     * @param text 인코딩할 문자열
     * @return Base64 인코딩된 문자열
     */
    public static String base64Encode(String text) {
        return Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }
    
    /**
     * Base64 문자열을 디코딩합니다.
     * 
     * @param encodedText Base64 인코딩된 문자열
     * @return 디코딩된 문자열
     */
    public static String base64Decode(String encodedText) {
        return new String(Base64.getDecoder().decode(encodedText), StandardCharsets.UTF_8);
    }
    
    /**
     * AWS Signature Version 4 서명 키를 생성합니다.
     * 
     * @param key AWS Secret Key
     * @param dateStamp 날짜 스탬프 (YYYYMMDD)
     * @param regionName AWS 리전명
     * @param serviceName AWS 서비스명
     * @return AWS 서명 키
     * @throws Exception 서명 키 생성 실패 시
     */
    public static byte[] getAwsSignatureKey(String key, String dateStamp, String regionName, String serviceName) throws Exception {
        byte[] kDate = hmacSha256(("AWS4" + key).getBytes(StandardCharsets.UTF_8), dateStamp);
        byte[] kRegion = hmacSha256(kDate, regionName);
        byte[] kService = hmacSha256(kRegion, serviceName);
        return hmacSha256(kService, "aws4_request");
    }
}