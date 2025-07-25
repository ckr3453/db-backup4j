package io.backup4j.core.util;

import io.backup4j.core.config.S3BackupConfig;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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
            String contentSha256 = CryptoUtils.calculateSha256(file);
            
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
                CryptoUtils.calculateSha256(canonicalRequest);
            
            // 3. Signing Key 생성
            byte[] signingKey = CryptoUtils.getAwsSignatureKey(config.getSecretKey(), dateStamp, config.getRegion(), AWS_SERVICE);
            
            // 4. Signature 계산
            String signature = CryptoUtils.hmacSha256Hex(signingKey, stringToSign);
            
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
     * HTTP 에러 응답을 읽습니다.
     */
    private static String readErrorResponse(HttpURLConnection connection) {
        InputStream errorStream = null;
        try {
            errorStream = connection.getErrorStream();
            return HttpResponseReader.readResponse(errorStream, Constants.MAX_ERROR_RESPONSE_SIZE, "No error details available");
            
        } catch (Exception e) {
            return "Failed to read error response: " + e.getMessage();
        } finally {
            if (errorStream != null) {
                try {
                    errorStream.close();
                } catch (IOException ignored) {
                    // 무시함 - 리소스 정리 실패는 치명적이지 않음
                }
            }
        }
    }
}