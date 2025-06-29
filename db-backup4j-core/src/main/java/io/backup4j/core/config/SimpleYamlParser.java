package io.backup4j.core.config;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Properties;
import java.util.Stack;

public class SimpleYamlParser {
    
    public static Properties parseYamlToProperties(InputStream inputStream) throws IOException {
        Properties properties = new Properties();
        
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            String line;
            Stack<String> keyStack = new Stack<>();
            int currentIndentLevel = 0;
            
            while ((line = reader.readLine()) != null) {
                String trimmedLine = line.trim();
                
                // 빈 줄이나 주석 무시
                if (trimmedLine.isEmpty() || trimmedLine.startsWith("#")) {
                    continue;
                }
                
                // 들여쓰기 레벨 계산
                int indentLevel = getIndentLevel(line);
                
                // 들여쓰기가 줄어들면 스택에서 키 제거
                while (keyStack.size() > indentLevel / 2) {
                    keyStack.pop();
                }
                
                // key: value 파싱
                if (trimmedLine.contains(":")) {
                    String[] parts = trimmedLine.split(":", 2);
                    String key = parts[0].trim();
                    String value = parts.length > 1 ? parts[1].trim() : "";
                    
                    // 현재 키의 full path 생성
                    String fullKey = buildFullKey(keyStack, key);
                    
                    if (value.isEmpty()) {
                        // value가 없으면 중첩 객체의 시작
                        keyStack.push(key);
                    } else {
                        // value가 있으면 properties에 추가
                        properties.setProperty(fullKey, value);
                    }
                }
                
                currentIndentLevel = indentLevel;
            }
        }
        
        return properties;
    }
    
    private static int getIndentLevel(String line) {
        int spaces = 0;
        for (char c : line.toCharArray()) {
            if (c == ' ') {
                spaces++;
            } else {
                break;
            }
        }
        return spaces;
    }
    
    private static String buildFullKey(Stack<String> keyStack, String currentKey) {
        if (keyStack.isEmpty()) {
            return currentKey;
        }
        
        StringBuilder fullKey = new StringBuilder();
        for (String key : keyStack) {
            if (fullKey.length() > 0) {
                fullKey.append(".");
            }
            fullKey.append(key);
        }
        
        if (fullKey.length() > 0) {
            fullKey.append(".");
        }
        fullKey.append(currentKey);
        
        return fullKey.toString();
    }
}