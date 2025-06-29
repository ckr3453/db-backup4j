# db-backup4j

[![CI](https://github.com/ckr3453/db-backup4j/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/ckr3453/db-backup4j/actions/workflows/ci.yml)
[![JitPack](https://jitpack.io/v/ckr3453/db-backup4j.svg)](https://jitpack.io/#ckr3453/db-backup4j)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

**db-backup4j**는 Java 8+ 환경을 위한 경량 데이터베이스 백업 라이브러리입니다. 설정 파일 기반으로 간단하게 데이터베이스 백업을 자동화할 수 있습니다.

## ✨ 주요 특징

- **순수 Java**: JDK 8+ 표준 라이브러리만 사용, 외부 의존성 없음
- **설정 파일 기반**: Properties/YAML 파일로 모든 설정 관리
- **다중 DB 지원**: MySQL, PostgreSQL 지원 
- **다중 저장소**: 로컬 파일, 이메일, AWS S3 백업 지원
- **자동 스케줄링**: 일/주/월 단위 백업 스케줄링
- **프레임워크 독립적**: Spring Boot, 일반 Java 애플리케이션 모두 지원
- **한 줄 실행**: `DbBackup4jInitializer.run()` 한 줄로 모든 기능 사용

## 📦 설치

### Gradle
```gradle
repositories {
    mavenCentral()
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'com.github.ckr3453:db-backup4j:v1.0.0'
}
```

### Maven
```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependency>
    <groupId>com.github.ckr3453</groupId>
    <artifactId>db-backup4j</artifactId>
    <version>v1.0.0</version>
</dependency>
```

## 🚀 빠른 시작

### 1. 설정 파일 생성

**db-backup4j.properties** (프로젝트 루트에 생성)
```properties
# 데이터베이스 설정 (필수)
database.type=MYSQL
database.host=localhost
database.port=3306
database.name=myapp_db
database.username=backup_user
database.password=backup_password

# 로컬 백업 (기본값)
backup.local.enabled=true
backup.local.path=./db-backup4j
backup.local.retention=30
backup.local.compress=true

# 스케줄링 (선택사항)
schedule.enabled=true
schedule.daily=02:00
```

### 2. Java 코드 실행

```java
import io.backup4j.core.DbBackup4jInitializer;

public class MyApplication {
    public static void main(String[] args) {
        // 한 줄로 백업 실행!
        // 설정 파일을 자동으로 찾아서 백업 실행
        DbBackup4jInitializer.run();
    }
}
```

### 3. 커스텀 설정 파일 사용

```java
public class MyApplication {
    public static void main(String[] args) {
        // 특정 설정 파일 사용
        DbBackup4jInitializer.run("config/my-backup.yaml");
    }
}
```

## ⚙️ 설정 파일 상세

### YAML 설정 예시 (db-backup4j.yaml)

```yaml
database:
  type: MYSQL          # MYSQL 또는 POSTGRESQL
  host: localhost
  port: 3306
  name: myapp_db
  username: backup_user
  password: backup_password

backup:
  local:
    enabled: true
    path: ./db-backup4j
    retention: 30        # 일 단위
    compress: true
    
  email:
    enabled: false
    smtp:
      host: smtp.gmail.com
      port: 587
    username: backup@company.com
    password: app-password
    recipients: admin@company.com,dev@company.com
    
  s3:
    enabled: false
    bucket: my-backup-bucket
    prefix: backups
    region: us-east-1
    access-key: ${AWS_ACCESS_KEY}
    secret-key: ${AWS_SECRET_KEY}

schedule:
  enabled: false         # true면 스케줄러 시작, false면 1회 실행
  daily: "02:00"        # HH:MM 형식
```

### 설정 파일 자동 탐지 순서

1. `./db-backup4j.properties`
2. `./db-backup4j.yaml`
3. `./db-backup4j.yml`
4. 혹은 DbBackup4jInitializer.run(configPath)에 지정한 경로

## 📋 지원 기능

### 데이터베이스
- **MYSQL**: mysqldump 사용
- **POSTGRESQL**: pg_dump 사용

### 백업 저장소
- **로컬 파일**: 압축 및 보존 기간 관리
- **이메일**: SMTP를 통한 백업 파일 전송
- **AWS S3**: S3 버킷에 백업 파일 업로드

### 스케줄링
- **최초 1회 실행**: schedule.enabled=false
- **주기적 실행**: schedule.enabled=true

## 🔧 개발 환경 설정

### 요구사항
- **Java**: JDK 8 이상
- **데이터베이스 도구**: 
  - MySQL: `mysqldump` 명령어 사용 가능
  - PostgreSQL: `pg_dump` 명령어 사용 가능

### 빌드 및 테스트

```bash
# 프로젝트 클론
git clone https://github.com/ckr3453/db-backup4j.git
cd db-backup4j

# 빌드
./gradlew build

# 테스트 실행
./gradlew test
```

## 🎯 사용 사례

### Spring Boot 애플리케이션
```java
@SpringBootApplication
public class MySpringBootApp {
    public static void main(String[] args) {
        SpringApplication.run(MySpringBootApp.class, args);
        
        // 애플리케이션 시작 후 db-backup4j 실행
        DbBackup4jInitializer.run();
    }
}
```

### 배치 작업
```java
public class BackupBatchJob {
    public static void main(String[] args) {
        try {
            DbBackup4jInitializer.run("config/prod-backup.properties");
            System.out.println("Backup completed successfully");
        } catch (Exception e) {
            System.err.println("Backup failed: " + e.getMessage());
            System.exit(1);
        }
    }
}
```

## 📄 라이선스

이 프로젝트는 [MIT License](LICENSE) 하에 배포됩니다.

## 🙋‍♂️ 지원

- 이메일: ckr3453@gmail.com
- 이슈 리포트: [GitHub Issues](https://github.com/ckr3453/db-backup4j/issues)
- 토론: [GitHub Discussions](https://github.com/ckr3453/db-backup4j/discussions)
