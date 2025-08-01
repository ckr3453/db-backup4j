package io.backup4j.core.util;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * 백업 파일 압축/해제 유틸리티 클래스
 * GZIP 형식으로 파일을 압축하고 해제하는 기능을 제공합니다.
 */
public class CompressionUtils {

    private CompressionUtils() {
    }
    
    private static final int BUFFER_SIZE = 8192;
    
    /**
     * 파일을 GZIP으로 압축
     * 
     * @param sourceFile 원본 파일
     * @param targetFile 압축된 파일
     * @throws IOException 압축 중 오류 발생 시
     */
    public static void compressFile(Path sourceFile, Path targetFile) throws IOException {
        try (FileInputStream fis = new FileInputStream(sourceFile.toFile());
             FileOutputStream fos = new FileOutputStream(targetFile.toFile());
             GZIPOutputStream gzipOut = new GZIPOutputStream(fos);
             BufferedInputStream bis = new BufferedInputStream(fis);
             BufferedOutputStream bos = new BufferedOutputStream(gzipOut)) {
            
            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;
            
            while ((bytesRead = bis.read(buffer)) != -1) {
                bos.write(buffer, 0, bytesRead);
            }
            
            bos.flush();
        }
    }
    
    /**
     * GZIP 파일을 해제
     * 
     * @param compressedFile 압축된 파일
     * @param targetFile 해제할 파일
     * @throws IOException 해제 중 오류 발생 시
     */
    public static void decompressFile(Path compressedFile, Path targetFile) throws IOException {
        try (FileInputStream fis = new FileInputStream(compressedFile.toFile());
             GZIPInputStream gzipIn = new GZIPInputStream(fis);
             FileOutputStream fos = new FileOutputStream(targetFile.toFile());
             BufferedInputStream bis = new BufferedInputStream(gzipIn);
             BufferedOutputStream bos = new BufferedOutputStream(fos)) {
            
            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;
            
            while ((bytesRead = bis.read(buffer)) != -1) {
                bos.write(buffer, 0, bytesRead);
            }
            
            bos.flush();
        }
    }
    
    /**
     * 파일의 압축률 계산
     * 
     * @param originalFile 원본 파일
     * @param compressedFile 압축된 파일
     * @return 압축률 (0.0 ~ 1.0, 1.0이 압축되지 않음)
     * @throws IOException 파일 크기 확인 중 오류 발생 시
     */
    public static double calculateCompressionRatio(Path originalFile, Path compressedFile) throws IOException {
        long originalSize = Files.size(originalFile);
        long compressedSize = Files.size(compressedFile);
        
        if (originalSize == 0) {
            return 1.0;
        }
        
        return (double) compressedSize / originalSize;
    }
    
    /**
     * 압축 파일 여부 확인
     * 
     * @param file 확인할 파일
     * @return 압축 파일이면 true
     */
    public static boolean isCompressed(Path file) {
        String fileName = file.getFileName().toString().toLowerCase();
        return fileName.endsWith(".gz") || fileName.endsWith(".gzip");
    }
    
    /**
     * 압축 파일명 생성
     * 
     * @param originalFile 원본 파일
     * @return 압축 파일명
     */
    public static Path getCompressedFileName(Path originalFile) {
        String fileName = originalFile.getFileName().toString();
        return originalFile.getParent().resolve(fileName + ".gz");
    }
    
    /**
     * 압축 해제 파일명 생성
     * 
     * @param compressedFile 압축된 파일
     * @return 해제 파일명
     */
    public static Path getDecompressedFileName(Path compressedFile) {
        String fileName = compressedFile.getFileName().toString();
        
        if (fileName.endsWith(".gz")) {
            fileName = fileName.substring(0, fileName.length() - 3);
        } else if (fileName.endsWith(".gzip")) {
            fileName = fileName.substring(0, fileName.length() - 5);
        }
        
        return compressedFile.getParent().resolve(fileName);
    }
    
    /**
     * 압축 결과 정보
     */
    public static class CompressionResult {
        private final Path originalFile;
        private final Path compressedFile;
        private final long originalSize;
        private final long compressedSize;
        private final double compressionRatio;
        private final long compressionTimeMs;
        
        public CompressionResult(Path originalFile, Path compressedFile, 
                               long originalSize, long compressedSize, 
                               double compressionRatio, long compressionTimeMs) {
            this.originalFile = originalFile;
            this.compressedFile = compressedFile;
            this.originalSize = originalSize;
            this.compressedSize = compressedSize;
            this.compressionRatio = compressionRatio;
            this.compressionTimeMs = compressionTimeMs;
        }
        
        public Path getOriginalFile() {
            return originalFile;
        }
        
        public Path getCompressedFile() {
            return compressedFile;
        }
        
        public long getOriginalSize() {
            return originalSize;
        }
        
        public long getCompressedSize() {
            return compressedSize;
        }
        
        public double getCompressionRatio() {
            return compressionRatio;
        }
        
        public long getCompressionTimeMs() {
            return compressionTimeMs;
        }
        
        public long getSavedBytes() {
            return originalSize - compressedSize;
        }
        
        public double getSavedPercentage() {
            return (1.0 - compressionRatio) * 100.0;
        }
        
        @Override
        public String toString() {
            return String.format("CompressionResult{originalSize=%d, compressedSize=%d, " +
                    "compressionRatio=%.2f, savedPercentage=%.1f%%, timeMs=%d}",
                    originalSize, compressedSize, compressionRatio, 
                    getSavedPercentage(), compressionTimeMs);
        }
    }
    
    /**
     * 압축과 함께 성능 측정
     * 
     * @param sourceFile 원본 파일
     * @param targetFile 압축 파일
     * @return 압축 결과 정보
     * @throws IOException 압축 중 오류 발생 시
     */
    public static CompressionResult compressWithMetrics(Path sourceFile, Path targetFile) throws IOException {
        long startTime = System.currentTimeMillis();
        long originalSize = Files.size(sourceFile);
        
        compressFile(sourceFile, targetFile);
        
        long endTime = System.currentTimeMillis();
        long compressedSize = Files.size(targetFile);
        double compressionRatio = calculateCompressionRatio(sourceFile, targetFile);
        
        return new CompressionResult(
            sourceFile, targetFile, originalSize, compressedSize, 
            compressionRatio, endTime - startTime
        );
    }
}