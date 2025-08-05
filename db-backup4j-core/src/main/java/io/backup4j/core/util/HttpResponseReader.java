package io.backup4j.core.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * HTTP 응답을 읽기 위한 유틸리티 클래스
 * InputStream에서 문자열을 읽어오는 공통 기능을 제공합니다.
 */
public class HttpResponseReader {
    
    private HttpResponseReader() {
    }
    
    /**
     * InputStream에서 문자열을 읽어옵니다.
     * 
     * @param stream 읽을 InputStream
     * @param maxSize 최대 응답 크기 (바이트)
     * @return 읽어온 문자열, stream이 null이면 null 반환
     */
    public static String readResponse(InputStream stream, int maxSize) {
        if (stream == null) {
            return null;
        }
        
        StringBuilder response = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
                if (response.length() > maxSize) {
                    response.append("...");
                    break;
                }
            }
            return response.toString();
            
        } catch (IOException e) {
            return "Failed to read response: " + e.getMessage();
        }
    }
    
    /**
     * InputStream에서 문자열을 읽어오며, 실패 시 기본 메시지를 반환합니다.
     * 
     * @param stream 읽을 InputStream
     * @param maxSize 최대 응답 크기 (바이트)
     * @param fallbackMessage stream이 null이거나 읽기 실패 시 반환할 메시지
     * @return 읽어온 문자열 또는 fallback 메시지
     */
    public static String readResponse(InputStream stream, int maxSize, String fallbackMessage) {
        String result = readResponse(stream, maxSize);
        return result != null ? result : fallbackMessage;
    }
}