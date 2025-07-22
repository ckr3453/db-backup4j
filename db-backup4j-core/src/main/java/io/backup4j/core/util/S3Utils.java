package io.backup4j.core.util;

import io.backup4j.core.config.S3BackupConfig;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Logger;

/**
 * AWS S3 백업을 위한 유틸리티 클래스
 * AWS SDK 없이 표준 라이브러리만 사용하여 S3 API를 직접 구현
 */
public class S3Utils {
    
    private static final Logger logger = Logger.getLogger(S3Utils.class.getName());
    
    private static final String AWS_ALGORITHM = "AWS4-HMAC-SHA256";
    private static final String AWS_REQUEST = "aws4_request";
    private static final String AWS_SERVICE = "s3";
    
    private S3Utils() {
    }
    
    /**
     * S3에 파일을 업로드합니다.
     * 
     * @param file 업로드할 파일
     * @param config S3 설정
     * @param objectKey S3 객체 키
     * @throws IOException 업로드 실패 시
     */
    public static void uploadFile(File file, S3BackupConfig config, String objectKey) throws IOException {
        if (!file.exists() || !file.isFile()) {
            throw new FileNotFoundException("Backup file not found: " + file.getAbsolutePath());
        }
        
        logger.info("Starting S3 upload: " + objectKey + " to bucket " + config.getBucket());
        
        String endpoint = String.format("https://%s.s3.%s.amazonaws.com/%s", 
            config.getBucket(), config.getRegion(), objectKey);
        
        URL url = new URL(endpoint);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        
        try {
            // 파일 해시 계산
            String contentSha256 = calculateSha256(file);
            
            // HTTP 요청 설정
            connection.setRequestMethod("PUT");
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/octet-stream");
            connection.setRequestProperty("Content-Length", String.valueOf(file.length()));
            connection.setRequestProperty("x-amz-content-sha256", contentSha256);
            
            // AWS 서명 생성 및 설정
            String timestamp = ZonedDateTime.now(ZoneOffset.UTC)
                .format(DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'"));
            String dateStamp = timestamp.substring(0, 8);
            
            Map<String, String> headers = new TreeMap<>();
            headers.put("host", connection.getURL().getHost());
            headers.put("x-amz-content-sha256", contentSha256);
            headers.put("x-amz-date", timestamp);
            
            String authorization = createAuthorizationHeader(
                config, "PUT", objectKey, headers, contentSha256, timestamp, dateStamp);
            
            connection.setRequestProperty("Authorization", authorization);
            connection.setRequestProperty("x-amz-date", timestamp);
            
            // 파일 업로드
            long uploadedBytes = 0;
            try (FileInputStream fis = new FileInputStream(file);
                 OutputStream os = connection.getOutputStream();
                 BufferedInputStream bis = new BufferedInputStream(fis);
                 BufferedOutputStream bos = new BufferedOutputStream(os)) {
                
                byte[] buffer = new byte[8192];
                int bytesRead;
                
                while ((bytesRead = bis.read(buffer)) != -1) {
                    bos.write(buffer, 0, bytesRead);
                    uploadedBytes += bytesRead;
                    
                    // 진행률 로깅 (10MB마다)
                    if (uploadedBytes % (10 * 1024 * 1024) == 0) {
                        double progress = (double) uploadedBytes / file.length() * 100;
                        logger.info(String.format("Upload progress: %.1f%% (%d/%d bytes)", 
                            progress, uploadedBytes, file.length()));
                    }
                }
                
                bos.flush();
            }
            
            // 응답 확인
            int responseCode = connection.getResponseCode();
            if (responseCode >= 200 && responseCode < 300) {
                logger.info("S3 upload completed successfully. Response code: " + responseCode);
            } else {
                String errorResponse = readErrorResponse(connection);
                throw new IOException("S3 upload failed. Response code: " + responseCode + 
                    ", Error: " + errorResponse);
            }
            
        } finally {
            connection.disconnect();
        }
    }
    
    /**
     * AWS Signature Version 4 인증 헤더를 생성합니다.
     */
    private static String createAuthorizationHeader(S3BackupConfig config, String method, 
            String objectKey, Map<String, String> headers, String payloadHash, 
            String timestamp, String dateStamp) throws IOException {
        
        try {
            // 1. Canonical Request 생성
            String canonicalRequest = createCanonicalRequest(method, "/" + objectKey, "", headers, payloadHash);
            
            // 2. String to Sign 생성
            String credentialScope = dateStamp + "/" + config.getRegion() + "/" + AWS_SERVICE + "/" + AWS_REQUEST;
            String stringToSign = AWS_ALGORITHM + "\n" + timestamp + "\n" + credentialScope + "\n" + 
                sha256Hex(canonicalRequest);
            
            // 3. Signing Key 생성
            byte[] signingKey = getSignatureKey(config.getSecretKey(), dateStamp, config.getRegion(), AWS_SERVICE);
            
            // 4. Signature 계산
            String signature = hmacSha256Hex(signingKey, stringToSign);
            
            // 5. Authorization 헤더 생성
            return AWS_ALGORITHM + " " +
                "Credential=" + config.getAccessKey() + "/" + credentialScope + ", " +
                "SignedHeaders=" + String.join(";", headers.keySet()) + ", " +
                "Signature=" + signature;
                
        } catch (Exception e) {
            throw new IOException("Failed to create AWS authorization header", e);
        }
    }
    
    /**
     * Canonical Request를 생성합니다.
     */
    private static String createCanonicalRequest(String method, String uri, String queryString, 
            Map<String, String> headers, String payloadHash) {
        
        StringBuilder canonicalHeaders = new StringBuilder();
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            canonicalHeaders.append(entry.getKey().toLowerCase()).append(":")
                .append(entry.getValue().trim()).append("\n");
        }
        
        String signedHeaders = String.join(";", headers.keySet());
        
        return method + "\n" + uri + "\n" + queryString + "\n" +
            canonicalHeaders + "\n" + signedHeaders + "\n" + payloadHash;
    }
    
    /**
     * AWS Signature Version 4 서명 키를 생성합니다.
     */
    private static byte[] getSignatureKey(String key, String dateStamp, String regionName, String serviceName) 
            throws Exception {
        byte[] kDate = hmacSha256(("AWS4" + key).getBytes(StandardCharsets.UTF_8), dateStamp);
        byte[] kRegion = hmacSha256(kDate, regionName);
        byte[] kService = hmacSha256(kRegion, serviceName);
        return hmacSha256(kService, AWS_REQUEST);
    }
    
    /**
     * HMAC-SHA256을 계산합니다.
     */
    private static byte[] hmacSha256(byte[] key, String data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
    }
    
    /**
     * HMAC-SHA256을 hex 문자열로 계산합니다.
     */
    private static String hmacSha256Hex(byte[] key, String data) throws Exception {
        return bytesToHex(hmacSha256(key, data));
    }
    
    /**
     * 파일의 SHA-256 해시를 계산합니다.
     */
    private static String calculateSha256(File file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            
            try (FileInputStream fis = new FileInputStream(file);
                 BufferedInputStream bis = new BufferedInputStream(fis)) {
                
                byte[] buffer = new byte[8192];
                int bytesRead;
                
                while ((bytesRead = bis.read(buffer)) != -1) {
                    digest.update(buffer, 0, bytesRead);
                }
            }
            
            return bytesToHex(digest.digest());
            
        } catch (Exception e) {
            throw new IOException("Failed to calculate SHA-256 hash", e);
        }
    }
    
    /**
     * 문자열의 SHA-256 해시를 계산합니다.
     */
    private static String sha256Hex(String data) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hash);
        } catch (Exception e) {
            throw new IOException("Failed to calculate SHA-256 hash", e);
        }
    }
    
    /**
     * 바이트 배열을 hex 문자열로 변환합니다.
     */
    private static String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }
    
    /**
     * HTTP 에러 응답을 읽습니다.
     */
    private static String readErrorResponse(HttpURLConnection connection) {
        try (InputStream errorStream = connection.getErrorStream()) {
            if (errorStream == null) {
                return "No error details available";
            }
            
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(errorStream, StandardCharsets.UTF_8))) {
                
                StringBuilder response = new StringBuilder();
                String line;
                
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                    if (response.length() > Constants.MAX_ERROR_RESPONSE_SIZE) {
                        response.append("...");
                        break;
                    }
                }
                
                return response.toString();
            }
            
        } catch (Exception e) {
            return "Failed to read error response: " + e.getMessage();
        }
    }
}