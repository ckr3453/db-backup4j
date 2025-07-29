package io.backup4j.core.config;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Stack;

/**
 * 최소한의 YAML 파싱 기능을 제공하는 커스텀 파서
 * SnakeYAML 의존성 없이 기본 YAML 구조를 파싱합니다.
 * 
 * 지원 기능:
 * - 기본 key-value 파싱
 * - 중첩 구조 (들여쓰기 기반)
 * - 주석 처리 (#)
 * - 문자열, 숫자, 불린 타입
 */
public class SimpleYamlParser {
    
    /**
     * InputStream에서 YAML을 파싱하여 Map으로 반환
     * 
     * @param inputStream YAML 데이터를 포함한 InputStream
     * @return 파싱된 YAML 데이터를 담은 Map
     * @throws IOException 파싱 중 오류 발생 시
     */
    public static Map<String, Object> parse(InputStream inputStream) throws IOException {
        Map<String, Object> result = new HashMap<>();
        
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            
            Stack<YamlContext> contextStack = new Stack<>();
            contextStack.push(new YamlContext(result, 0));
            
            String line;
            int lineNumber = 0;
            
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                parseLine(line, contextStack, lineNumber);
            }
        }
        
        // 빈 맵들을 제거 (주석만 있고 실제 값이 없는 경우)
        cleanEmptyMaps(result);
        
        return result;
    }
    
    /**
     * 한 줄씩 YAML을 파싱하는 메서드
     */
    private static void parseLine(String line, Stack<YamlContext> contextStack, int lineNumber) throws IOException {
        // 빈 줄이나 주석 라인 건너뛰기
        String trimmed = line.trim();
        if (trimmed.isEmpty() || trimmed.startsWith("#")) {
            return;
        }
        
        // 들여쓰기 레벨 계산
        int indentLevel = getIndentLevel(line);
        
        // 현재 들여쓰기에 맞는 컨텍스트 찾기
        adjustContextStack(contextStack, indentLevel);
        
        // key-value 파싱
        parseKeyValue(trimmed, contextStack, indentLevel, lineNumber);
    }
    
    /**
     * 들여쓰기 레벨 계산 (공백 2개 = 1레벨)
     */
    private static int getIndentLevel(String line) {
        int spaces = 0;
        for (char c : line.toCharArray()) {
            if (c == ' ') {
                spaces++;
            } else if (c == '\t') {
                spaces += 2; // 탭은 2칸으로 계산
            } else {
                break;
            }
        }
        return spaces / 2; // 공백 2개를 1레벨로 계산
    }
    
    /**
     * 컨텍스트 스택을 현재 들여쓰기 레벨에 맞게 조정
     */
    private static void adjustContextStack(Stack<YamlContext> contextStack, int indentLevel) {
        // 현재 들여쓰기보다 깊은 컨텍스트들 제거
        while (contextStack.size() > 1 && contextStack.peek().getIndentLevel() > indentLevel) {
            contextStack.pop();
        }
    }
    
    /**
     * key-value 쌍을 파싱하여 맵에 추가
     */
    private static void parseKeyValue(String line, Stack<YamlContext> contextStack, 
                                    int indentLevel, int lineNumber) throws IOException {
        
        int colonIndex = line.indexOf(':');
        if (colonIndex == -1) {
            throw new IOException("Invalid YAML syntax at line " + lineNumber + ": " + line);
        }
        
        String key = line.substring(0, colonIndex).trim();
        String valueStr = line.substring(colonIndex + 1).trim();
        
        if (key.isEmpty()) {
            throw new IOException("Empty key at line " + lineNumber + ": " + line);
        }
        
        YamlContext currentContext = contextStack.peek();
        
        // 주석 제거 후 값 확인
        String cleanValueStr = valueStr;
        int commentIndex = valueStr.indexOf('#');
        if (commentIndex != -1) {
            cleanValueStr = valueStr.substring(0, commentIndex).trim();
        }
        
        if (cleanValueStr.isEmpty()) {
            // 값이 비어있는지 확인 - 다음 줄이 더 들여쓰기되어 있으면 중첩 객체
            // 그렇지 않으면 빈 값이므로 키를 추가하지 않음 (기본값 사용을 위해)
            // 일단 중첩 객체로 가정하고 다음 줄에서 판단
            Map<String, Object> nestedMap = new HashMap<>();
            currentContext.getMap().put(key, nestedMap);
            contextStack.push(new YamlContext(nestedMap, indentLevel + 1));
        } else {
            // 값이 있는 경우
            Object value = parseValue(valueStr);
            currentContext.getMap().put(key, value);
        }
    }
    
    /**
     * 값 문자열을 적절한 타입으로 변환
     */
    private static Object parseValue(String valueStr) {
        // 주석 제거
        int commentIndex = valueStr.indexOf('#');
        if (commentIndex != -1) {
            valueStr = valueStr.substring(0, commentIndex).trim();
        }
        
        if (valueStr.isEmpty()) {
            return "";
        }
        
        // 불린 값
        if ("true".equalsIgnoreCase(valueStr)) {
            return true;
        }
        if ("false".equalsIgnoreCase(valueStr)) {
            return false;
        }
        
        // null 값
        if ("null".equalsIgnoreCase(valueStr) || "~".equals(valueStr)) {
            return null;
        }
        
        // 숫자 값
        try {
            if (valueStr.contains(".")) {
                return Double.parseDouble(valueStr);
            } else {
                return Integer.parseInt(valueStr);
            }
        } catch (NumberFormatException e) {
            // 숫자가 아니면 문자열로 처리
        }
        
        // 따옴표 제거
        if ((valueStr.startsWith("\"") && valueStr.endsWith("\"")) ||
            (valueStr.startsWith("'") && valueStr.endsWith("'"))) {
            return valueStr.substring(1, valueStr.length() - 1);
        }
        
        return valueStr;
    }
    
    /**
     * 빈 맵들을 재귀적으로 제거하는 메서드
     * 주석만 있고 실제 값이 없는 키들을 제거하여 기본값이 적용되도록 함
     */
    @SuppressWarnings("unchecked")
    private static void cleanEmptyMaps(Map<String, Object> map) {
        map.entrySet().removeIf(entry -> {
            Object value = entry.getValue();
            if (value instanceof Map) {
                Map<String, Object> nestedMap = (Map<String, Object>) value;
                cleanEmptyMaps(nestedMap);
                return nestedMap.isEmpty();
            }
            return false;
        });
    }
    
    /**
     * YAML 파싱 컨텍스트를 담는 내부 클래스
     */
    private static class YamlContext {
        private final Map<String, Object> map;
        private final int indentLevel;
        
        public YamlContext(Map<String, Object> map, int indentLevel) {
            this.map = map;
            this.indentLevel = indentLevel;
        }
        
        public Map<String, Object> getMap() {
            return map;
        }
        
        public int getIndentLevel() {
            return indentLevel;
        }
    }
}