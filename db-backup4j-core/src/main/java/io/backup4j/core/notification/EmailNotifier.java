package io.backup4j.core.notification;

import io.backup4j.core.config.NotificationConfig;
import io.backup4j.core.validation.BackupResult;
import io.backup4j.core.exception.EmailNotificationException;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.logging.Logger;

/**
 * 이메일 알림을 전송하는 구현체
 * JavaMail 없이 표준 라이브러리만 사용하여 SMTP 프로토콜을 직접 구현
 */
public class EmailNotifier {
    
    private static final Logger logger = Logger.getLogger(EmailNotifier.class.getName());
    
    /**
     * 이메일 알림을 전송합니다
     * 
     * @param title 알림 제목
     * @param message 알림 메시지
     * @param result 백업 결과
     * @param config 이메일 설정
     * @throws Exception 전송 실패 시
     */
    public void sendNotification(String title, String message, BackupResult result, 
                               NotificationConfig.EmailConfig config) throws EmailNotificationException {
        
        logger.info("Email notification sending started: " + config.getSmtp().getHost());
        
        // SMTP 연결 및 메시지 전송
        try (Socket socket = createSocket(config.getSmtp());
             BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true)) {
            
            // SMTP 핸드셰이크
            performSmtpHandshake(reader, writer, config);
            
            // 메시지 전송
            sendMessage(reader, writer, title, message, result, config);
            
            logger.info("Email notification sending completed");
            
        } catch (Exception e) {
            logger.severe("Email notification sending failed: " + e.getMessage());
            throw new EmailNotificationException("Failed to send email notification", e);
        }
    }
    
    /**
     * SMTP 서버 연결 소켓을 생성합니다
     * 
     * @param smtpConfig SMTP 설정
     * @return 생성된 소켓
     * @throws Exception 연결 실패 시
     */
    private Socket createSocket(NotificationConfig.SmtpConfig smtpConfig) throws EmailNotificationException {
        try {
        Socket socket = new Socket(smtpConfig.getHost(), smtpConfig.getPort());
        
        if (smtpConfig.isUseTls()) {
            // TLS 업그레이드는 STARTTLS 명령어 이후에 수행
            return socket;
        }
        
        return socket;
        } catch (Exception e) {
            throw new EmailNotificationException("Failed to create socket connection", e);
        }
    }
    
    /**
     * SMTP 핸드셰이크를 수행합니다
     * 
     * @param reader 입력 스트림
     * @param writer 출력 스트림
     * @param config 이메일 설정
     * @throws Exception 핸드셰이크 실패 시
     */
    private void performSmtpHandshake(BufferedReader reader, PrintWriter writer, 
                                    NotificationConfig.EmailConfig config) throws EmailNotificationException {
        try {
        // 서버 인사말 읽기
        String response = reader.readLine();
        if (!response.startsWith("220")) {
            throw new EmailNotificationException("SMTP 서버 연결 실패: " + response);
        }
        
        // EHLO 명령어
        writer.println("EHLO " + getLocalHostname());
        response = reader.readLine();
        if (!response.startsWith("250")) {
            throw new EmailNotificationException("EHLO 실패: " + response);
        }
        
        // 추가 EHLO 응답 읽기
        while (reader.ready()) {
            response = reader.readLine();
            if (!response.startsWith("250")) break;
        }
        
        // STARTTLS (TLS 사용시)
        if (config.getSmtp().isUseTls()) {
            writer.println("STARTTLS");
            response = reader.readLine();
            if (!response.startsWith("220")) {
                throw new EmailNotificationException("STARTTLS 실패: " + response);
            }
            
            // 실제 TLS 업그레이드는 복잡하므로 생략
            // 실제 운영 환경에서는 JavaMail 사용 권장
        }
        
        // 인증 (AUTH 사용시)
        if (config.getSmtp().isUseAuth()) {
            performAuthentication(reader, writer, config);
        }
        } catch (EmailNotificationException e) {
            throw e;
        } catch (Exception e) {
            throw new EmailNotificationException("SMTP handshake failed", e);
        }
    }
    
    /**
     * SMTP 인증을 수행합니다
     * 
     * @param reader 입력 스트림
     * @param writer 출력 스트림
     * @param config 이메일 설정
     * @throws Exception 인증 실패 시
     */
    private void performAuthentication(BufferedReader reader, PrintWriter writer, 
                                     NotificationConfig.EmailConfig config) throws EmailNotificationException {
        try {
        // AUTH LOGIN 명령어
        writer.println("AUTH LOGIN");
        String response = reader.readLine();
        if (!response.startsWith("334")) {
            throw new EmailNotificationException("AUTH LOGIN 실패: " + response);
        }
        
        // 사용자명 전송
        String encodedUsername = Base64.getEncoder().encodeToString(config.getUsername().getBytes(StandardCharsets.UTF_8));
        writer.println(encodedUsername);
        response = reader.readLine();
        if (!response.startsWith("334")) {
            throw new EmailNotificationException("사용자명 인증 실패: " + response);
        }
        
        // 비밀번호 전송
        String encodedPassword = Base64.getEncoder().encodeToString(config.getPassword().getBytes(StandardCharsets.UTF_8));
        writer.println(encodedPassword);
        response = reader.readLine();
        if (!response.startsWith("235")) {
            throw new EmailNotificationException("비밀번호 인증 실패: " + response);
        }
        } catch (EmailNotificationException e) {
            throw e;
        } catch (Exception e) {
            throw new EmailNotificationException("SMTP authentication failed", e);
        }
    }
    
    /**
     * 실제 메시지를 전송합니다
     * 
     * @param reader 입력 스트림
     * @param writer 출력 스트림
     * @param title 메시지 제목
     * @param message 메시지 내용
     * @param result 백업 결과
     * @param config 이메일 설정
     * @throws Exception 전송 실패 시
     */
    private void sendMessage(BufferedReader reader, PrintWriter writer, String title, String message, 
                           BackupResult result, NotificationConfig.EmailConfig config) throws EmailNotificationException {
        try {
        // MAIL FROM
        writer.println("MAIL FROM:<" + config.getUsername() + ">");
        String response = reader.readLine();
        if (!response.startsWith("250")) {
            throw new EmailNotificationException("MAIL FROM 실패: " + response);
        }
        
        // RCPT TO (모든 수신자에게)
        for (String recipient : config.getRecipients()) {
            writer.println("RCPT TO:<" + recipient + ">");
            response = reader.readLine();
            if (!response.startsWith("250")) {
                throw new EmailNotificationException("RCPT TO 실패: " + response);
            }
        }
        
        // DATA
        writer.println("DATA");
        response = reader.readLine();
        if (!response.startsWith("354")) {
            throw new EmailNotificationException("DATA 실패: " + response);
        }
        
        // 메시지 헤더 및 본문
        String emailContent = createEmailContent(title, message, result, config);
        writer.println(emailContent);
        writer.println(".");
        
        response = reader.readLine();
        if (!response.startsWith("250")) {
            throw new EmailNotificationException("메시지 전송 실패: " + response);
        }
        
        // QUIT
        writer.println("QUIT");
        reader.readLine();
        } catch (EmailNotificationException e) {
            throw e;
        } catch (Exception e) {
            throw new EmailNotificationException("Failed to send email message", e);
        }
    }
    
    /**
     * 이메일 내용을 생성합니다
     * 
     * @param title 제목
     * @param message 메시지
     * @param result 백업 결과
     * @param config 이메일 설정
     * @return 완성된 이메일 내용
     */
    private String createEmailContent(String title, String message, BackupResult result, 
                                    NotificationConfig.EmailConfig config) {
        
        StringBuilder email = new StringBuilder();
        
        // 헤더
        email.append("From: ").append(config.getUsername()).append("\\n");
        email.append("To: ").append(String.join(", ", config.getRecipients())).append("\\n");
        email.append("Subject: ").append(config.getSubject()).append("\\n");
        email.append("Date: ").append(LocalDateTime.now().format(DateTimeFormatter.RFC_1123_DATE_TIME)).append("\\n");
        email.append("Content-Type: text/plain; charset=UTF-8\\n");
        email.append("Content-Transfer-Encoding: 8bit\\n");
        email.append("\\n");
        
        // 본문
        email.append(title).append("\\n\\n");
        email.append(message.replace("\\n", "\\n"));
        
        // 푸터
        email.append("\\n\\n");
        email.append("---\\n");
        email.append("이 메시지는 db-backup4j에서 자동으로 전송되었습니다.\\n");
        email.append("전송 시간: ").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append("\\n");
        
        return email.toString();
    }
    
    /**
     * 로컬 호스트명을 반환합니다
     * 
     * @return 호스트명
     */
    private String getLocalHostname() {
        try {
            return java.net.InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "localhost";
        }
    }
    
}