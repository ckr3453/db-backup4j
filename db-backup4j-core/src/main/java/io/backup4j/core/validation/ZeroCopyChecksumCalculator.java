package io.backup4j.core.validation;

import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.logging.Logger;

import static java.nio.channels.FileChannel.MapMode.READ_ONLY;
import static java.nio.file.StandardOpenOption.READ;

/**
 * 제로카피 기법을 사용한 파일 체크섬 계산기
 * 메모리 효율적으로 대용량 파일의 MD5, SHA256 체크섬을 계산합니다.
 */
public class ZeroCopyChecksumCalculator {
    
    private static final Logger logger = Logger.getLogger(ZeroCopyChecksumCalculator.class.getName());
    private static final int DEFAULT_CHUNK_SIZE = 16 * 1024 * 1024; // 16MB
    private static final int MAX_CHUNK_SIZE = 64 * 1024 * 1024;     // 64MB
    
    public enum Algorithm {
        MD5("MD5"),
        SHA256("SHA-256");
        
        private final String algorithmName;
        
        Algorithm(String algorithmName) {
            this.algorithmName = algorithmName;
        }
        
        public String getAlgorithmName() {
            return algorithmName;
        }
    }
    
    /**
     * 기본 청크 크기로 MD5 체크섬 계산
     */
    public static String calculateMD5(Path filePath) throws IOException {
        return calculate(filePath, Algorithm.MD5, DEFAULT_CHUNK_SIZE);
    }
    
    /**
     * 기본 청크 크기로 SHA256 체크섬 계산
     */
    public static String calculateSHA256(Path filePath) throws IOException {
        return calculate(filePath, Algorithm.SHA256, DEFAULT_CHUNK_SIZE);
    }
    
    /**
     * 지정된 알고리즘과 청크 크기로 체크섬 계산
     */
    public static String calculate(Path filePath, Algorithm algorithm, int chunkSize) throws IOException {
        validateInputs(filePath, chunkSize);
        
        try {
            MessageDigest digest = MessageDigest.getInstance(algorithm.getAlgorithmName());
            
            try (FileChannel channel = FileChannel.open(filePath, READ)) {
                long fileSize = channel.size();
                
                if (fileSize == 0) {
                    return bytesToHex(digest.digest());
                }
                
                long position = 0;
                
                while (position < fileSize) {
                    long remaining = fileSize - position;
                    int mapSize = (int) Math.min(chunkSize, remaining);
                    
                    MappedByteBuffer buffer = channel.map(READ_ONLY, position, mapSize);
                    digest.update(buffer);
                    
                    position += mapSize;
                    
                    // MappedByteBuffer는 JVM이 자동으로 정리하므로 명시적 GC 호출 불필요
                    // 대용량 파일 처리 시 메모리 사용량 로깅
                    if (position % (chunkSize * 8) == 0) {
                        long processedMB = position / (1024 * 1024);
                        long totalMB = fileSize / (1024 * 1024);
                        logger.fine(String.format("체크섬 계산 진행률: %d MB / %d MB (%.1f%%)", 
                            processedMB, totalMB, (double) position / fileSize * 100));
                    }
                }
            }
            
            return bytesToHex(digest.digest());
            
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("지원하지 않는 해시 알고리즘: " + algorithm.getAlgorithmName(), e);
        }
    }
    
    /**
     * 파일 크기에 따른 최적 청크 크기 계산
     */
    public static int calculateOptimalChunkSize(long fileSize) {
        if (fileSize < 100 * 1024 * 1024) {      // 100MB 미만
            return 4 * 1024 * 1024;              // 4MB
        } else if (fileSize < 1024 * 1024 * 1024) { // 1GB 미만  
            return 16 * 1024 * 1024;             // 16MB
        } else {                                  // 1GB 이상
            return 32 * 1024 * 1024;             // 32MB
        }
    }
    
    /**
     * 최적화된 체크섬 계산 (파일 크기에 따라 청크 크기 자동 조정)
     */
    public static String calculateOptimized(Path filePath, Algorithm algorithm) throws IOException {
        validateInputs(filePath, DEFAULT_CHUNK_SIZE);
        
        try (FileChannel channel = FileChannel.open(filePath, READ)) {
            long fileSize = channel.size();
            int optimalChunkSize = calculateOptimalChunkSize(fileSize);
            return calculate(filePath, algorithm, optimalChunkSize);
        }
    }
    
    /**
     * 체크섬 계산 결과 클래스
     */
    public static class ChecksumResult {
        private final String checksum;
        private final Algorithm algorithm;
        private final long fileSize;
        private final long calculationTimeMs;
        
        public ChecksumResult(String checksum, Algorithm algorithm, long fileSize, long calculationTimeMs) {
            this.checksum = checksum;
            this.algorithm = algorithm;
            this.fileSize = fileSize;
            this.calculationTimeMs = calculationTimeMs;
        }
        
        public String getChecksum() {
            return checksum;
        }
        
        public Algorithm getAlgorithm() {
            return algorithm;
        }
        
        public long getFileSize() {
            return fileSize;
        }
        
        public long getCalculationTimeMs() {
            return calculationTimeMs;
        }
        
        @Override
        public String toString() {
            return String.format("ChecksumResult{checksum='%s', algorithm=%s, fileSize=%d, calculationTimeMs=%d}", 
                checksum, algorithm, fileSize, calculationTimeMs);
        }
    }
    
    /**
     * 체크섬 계산과 함께 성능 측정
     */
    public static ChecksumResult calculateWithMetrics(Path filePath, Algorithm algorithm) throws IOException {
        long startTime = System.currentTimeMillis();
        
        try (FileChannel channel = FileChannel.open(filePath, READ)) {
            long fileSize = channel.size();
            String checksum = calculateOptimized(filePath, algorithm);
            long calculationTime = System.currentTimeMillis() - startTime;
            
            return new ChecksumResult(checksum, algorithm, fileSize, calculationTime);
        }
    }
    
    private static void validateInputs(Path filePath, int chunkSize) {
        if (filePath == null) {
            throw new IllegalArgumentException("파일 경로는 null일 수 없습니다");
        }
        
        if (!filePath.toFile().exists()) {
            throw new IllegalArgumentException("파일이 존재하지 않습니다: " + filePath);
        }
        
        if (!filePath.toFile().isFile()) {
            throw new IllegalArgumentException("디렉토리가 아닌 파일이어야 합니다: " + filePath);
        }
        
        if (chunkSize <= 0 || chunkSize > MAX_CHUNK_SIZE) {
            throw new IllegalArgumentException(
                String.format("청크 크기는 1 ~ %d 바이트 사이여야 합니다: %d", MAX_CHUNK_SIZE, chunkSize));
        }
    }
    
    /**
     * 바이트 배열을 16진수 문자열로 변환 (Java 8 호환)
     */
    private static String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }
}