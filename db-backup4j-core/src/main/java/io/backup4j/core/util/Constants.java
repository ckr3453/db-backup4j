package io.backup4j.core.util;

import java.util.concurrent.TimeUnit;

/**
 * 시스템 전반에서 사용되는 상수들을 정의하는 클래스
 */
public final class Constants {
    
    private Constants() {
    }
    // Scheduler Constants  
    public static final long SCHEDULER_SHUTDOWN_TIMEOUT_SEC = 5;   // 5 seconds
    public static final TimeUnit SCHEDULER_SHUTDOWN_TIMEOUT_UNIT = TimeUnit.SECONDS;

    // File and Response Size Limits
    public static final int MAX_ERROR_RESPONSE_SIZE = 1000;        // characters

    // SQL Constants
    public static final String SQL_NULL = "NULL";
}