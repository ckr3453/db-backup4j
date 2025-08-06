# db-backup4j

[![CI](https://github.com/ckr3453/db-backup4j/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/ckr3453/db-backup4j/actions/workflows/ci.yml)
[![JitPack](https://jitpack.io/v/ckr3453/db-backup4j.svg)](https://jitpack.io/#ckr3453/db-backup4j)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

**db-backup4j**는 Java 8+ 환경을 위한 경량 데이터베이스 백업 라이브러리입니다. 설정 파일 기반으로 간단하게 데이터베이스 백업을 자동화할 수 있습니다.

## 주요 특징

- **JDK version**: JDK 8+
- **설정 파일 기반**: Properties/YAML 파일로 모든 설정 관리
- **환경 변수 지원**: 환경변수 직접 설정 및 `${ENV_VAR}` 참조 문법 지원
- **보안 강화**: 민감정보를 환경변수로 분리하여 안전한 설정 관리
- **다중 DB 지원**: MySQL, PostgreSQL 지원 
- **다중 저장소**: 로컬 파일, AWS S3 백업 지원
- **자동 스케줄링**: 고급 cron 표현식 지원 (스텝, 범위, 리스트 형식)
- **프레임워크 독립적**: Spring Boot, 일반 Java 애플리케이션 모두 지원
- **한 줄 실행**: `DbBackup4jInitializer.run()` 한 줄로 모든 기능 사용

## 설치

### Gradle
```gradle
repositories {
    mavenCentral()
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'com.github.ckr3453:db-backup4j:v1.0.3'
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
    <version>v1.0.3</version>
</dependency>
```

## 빠른 시작

### 1. 설정 파일 생성

**db-backup4j.properties** (프로젝트 루트에 생성)
```properties
# 데이터베이스 설정 (필수) - JDBC URL 방식
database.url=jdbc:mysql://localhost:3306/myapp_db?serverTimezone=UTC
database.username=backup_user
database.password=backup_password

# 로컬 백업 (기본값)
backup.local.enabled=true
backup.local.path=./db-backup4j
backup.local.retention=30
backup.local.compress=true

# 스케줄링 (선택사항)
schedule.enabled=true
schedule.cron=0 0 * * *         # 매일 자정 (*/15 * * * *, 0 9-17 * * * 등 고급 표현식 지원)
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

### 4. Spring Boot에서 사용

Spring Boot 애플리케이션에서는 ApplicationRunner를 사용하여 백업을 실행할 수 있습니다:

```java
@Component
public class BackupRunner implements ApplicationRunner {
    
    @Override
    public void run(ApplicationArguments args) throws Exception {
        // 자동 설정 파일 감지
        DbBackup4jInitializer.run();
        
        // 또는 클래스패스의 설정 파일 사용
        // DbBackup4jInitializer.runFromClasspath("db-backup4j.yml");
    }
}
```

**Kotlin 버전**
```kotlin
@Component
class BackupRunner : ApplicationRunner {
    override fun run(args: ApplicationArguments) {
        DbBackup4jInitializer.run()
    }
}
```

## 설정 파일 상세

### YAML 설정 예시 (db-backup4j.yaml)

```yaml
database:
  # JDBC URL 방식 (권장) - 모든 JDBC 옵션 사용 가능
  url: ${DATABASE_URL}
  username: ${DB_USER}
  password: ${DB_PASSWORD}
  
  # PostgreSQL 예시:
  # url: jdbc:postgresql://localhost:5432/myapp_db?currentSchema=backup_schema
  
  # 테이블 필터링 설정 (선택사항)
  exclude-system-tables: true            # 시스템 테이블 자동 제외 (기본값: true)
  exclude-table-patterns:                # 제외할 테이블 패턴 (와일드카드 지원)
    - "temp_*"                         # temp_로 시작하는 모든 테이블
    - "*backup*"                       # backup이 포함된 모든 테이블 
    - "log_*"                          # 로그 테이블들
  # includeTablePatterns:              # 포함할 테이블 패턴 (설정시 이것만 백업)
  #   - "app_*"                        # app_로 시작하는 테이블만

backup:
  local:
    enabled: true
    path: ./db-backup4j
    retention: 30        # 일 단위
    compress: true
    
  s3:
    enabled: false
    bucket: my-backup-bucket
    prefix: backups
    region: us-east-1
    access-key: ${AWS_ACCESS_KEY}
    secret-key: ${AWS_SECRET_KEY}

schedule:
  enabled: false         # true면 스케줄러 시작, false면 1회 실행
  cron: "0 0 * * *"      # Unix cron 형식 (매일 자정)
                         # 고급 형식 지원: */15 * * * * (15분마다), 0 9-17 * * * (9-17시), 0,30 * * * * (정시/30분) 등
```

### 테이블 필터링 기능

시스템 테이블이나 임시 테이블 등 불필요한 테이블을 백업에서 제외할 수 있습니다.

#### 기본 시스템 테이블 제외
**자동으로 제외되는 시스템 테이블들:**
- **PostGIS**: `geometry_columns`, `spatial_ref_sys`, `geography_columns`
- **MySQL**: `information_schema.*`, `performance_schema.*`, `mysql.*`, `sys.*`
- **PostgreSQL**: `information_schema.*`, `pg_*`
- **기타**: `flyway_*`, `liquibase*`, `__*` (이중 언더스코어로 시작)

#### 패턴 기반 필터링
**와일드카드 지원:**
- `*`: 모든 문자 매칭 (예: `temp_*` → `temp_users`, `temp_orders`)
- `?`: 단일 문자 매칭 (예: `test?` → `test1`, `test2`)

**설정 예시:**
```yaml
database:
  exclude-system-tables: true          # 시스템 테이블 자동 제외
  exclude-table-patterns:              # 추가로 제외할 패턴들
    - "temp_*"                       # 임시 테이블
    - "*_backup"                     # 백업 테이블
    - "log_*"                        # 로그 테이블
  include-table-patterns:              # 특정 테이블만 포함 (설정시 이것만 백업)
    - "app_*"                        # 앱 관련 테이블만
    - "user_*"                       # 사용자 관련 테이블만
```

**필터링 우선순위:**
1. `include-table-patterns`가 설정된 경우 → 해당 패턴과 일치하는 테이블만 선택
2. `exclude-system-tables=true` → 시스템 테이블 제외  
3. `exclude-table-patterns` → 추가 제외 패턴 적용

### 보안 기능 (민감정보 보호)

#### 환경 변수 참조 (${ENV_VAR} 문법)
설정 파일에서 `${ENV_VAR}` 또는 `${ENV_VAR:기본값}` 형식으로 환경 변수를 참조할 수 있습니다:

```yaml
database:
  url: ${DATABASE_URL}
  username: ${DB_USER}
  password: ${DB_PASSWORD}

backup:
  local:
    enabled: ${BACKUP_LOCAL_ENABLED:true}  # 기본값 true
    path: ${BACKUP_PATH:./db-backup4j}     # 기본값 ./db-backup4j
  s3:
    enabled: ${S3_ENABLED:false}           # 기본값 false
    access-key: ${AWS_ACCESS_KEY}
    secret-key: ${AWS_SECRET_KEY}
```

**Properties 파일 예시:**
```properties
database.url=${DATABASE_URL}
database.username=${DB_USER}
database.password=${DB_PASSWORD}

# 테이블 필터링 설정
database.exclude-system-tables=${EXCLUDE_SYSTEM_TABLES:true}
database.exclude-table-patterns=temp_*,*backup*,log_*
# database.includeTablePatterns=app_*,user_*

# 기본값 지원
backup.local.enabled=${BACKUP_LOCAL_ENABLED:true}
backup.local.path=${BACKUP_PATH:./db-backup4j}

backup.s3.access-key=${AWS_ACCESS_KEY}
backup.s3.secret-key=${AWS_SECRET_KEY}
```

**고급 기능:**
- **기본값 지원**: `${VAR:기본값}` 형식으로 환경변수가 없을 때 사용할 기본값 설정
- **중첩 참조**: 환경변수 값 안에 다른 환경변수 참조 가능
- **순환 참조 방지**: 무한 루프를 방지하는 안전장치 내장
- **최대 치환 깊이**: 복잡한 중첩을 제한하여 성능 보장

#### 환경 변수 직접 설정
설정 파일 없이 환경 변수만으로도 모든 설정이 가능합니다:

```bash
# 데이터베이스 설정 - JDBC URL 방식
export DB_BACKUP4J_DATABASE_URL=jdbc:mysql://localhost:3306/mydb
export DB_BACKUP4J_DATABASE_USERNAME=dbuser
export DB_BACKUP4J_DATABASE_PASSWORD=dbpass

# 테이블 필터링 설정
export DB_BACKUP4J_DATABASE_EXCLUDESYSTEMTABLES=true
export DB_BACKUP4J_DATABASE_EXCLUDETABLEPATTERNS="temp_*,*backup*,log_*"
# export DB_BACKUP4J_DATABASE_INCLUDETABLEPATTERNS="app_*,user_*"

# 로컬 백업 설정
export DB_BACKUP4J_BACKUP_LOCAL_ENABLED=true
export DB_BACKUP4J_BACKUP_LOCAL_PATH=/backup/myapp
export DB_BACKUP4J_BACKUP_LOCAL_RETENTION=30
export DB_BACKUP4J_BACKUP_LOCAL_COMPRESS=true

# S3 백업 설정
export DB_BACKUP4J_BACKUP_S3_ENABLED=true
export DB_BACKUP4J_BACKUP_S3_BUCKET=my-backup-bucket
export DB_BACKUP4J_BACKUP_S3_ACCESS_KEY=AKIA123456789
export DB_BACKUP4J_BACKUP_S3_SECRET_KEY=secret123456789
export DB_BACKUP4J_BACKUP_S3_REGION=ap-northeast-2

# 스케줄 설정
export DB_BACKUP4J_SCHEDULE_ENABLED=true
export DB_BACKUP4J_SCHEDULE_CRON="0 2 * * *"

# 실행
java -cp db-backup4j-core/build/libs/* io.backup4j.core.DbBackup4jInitializer
```

**설정 우선순위:**
1. 환경 변수 (최고 우선순위)
2. 설정 파일 내 `${ENV_VAR}` 참조
3. 설정 파일 일반 값
4. 기본값 (최하위)

### 설정 파일 자동 탐지 순서

1. `./db-backup4j.properties`
2. `./db-backup4j.yaml`
3. `./db-backup4j.yml`
4. 혹은 DbBackup4jInitializer.run(configPath)에 지정한 경로

## 지원 기능

### 백업 저장소
- **로컬 파일**: 압축 및 보존 기간 관리
- **AWS S3**: S3 버킷에 백업 파일 업로드

### 스케줄링
- **최초 1회 실행**: schedule.enabled=false
- **주기적 실행**: schedule.enabled=true

#### Cron 표현식 예시
```properties
# 기본 형식
schedule.cron=0 2 * * *        # 매일 오전 2시
schedule.cron=0 */6 * * *      # 6시간마다
schedule.cron=0 0 * * 0        # 매주 일요일 자정
schedule.cron=0 0 1 * *        # 매월 1일 자정

# 고급 형식 (스텝, 범위, 리스트)
schedule.cron=*/15 * * * *     # 15분마다
schedule.cron=0 9-17 * * *     # 오전 9시부터 오후 5시까지 매시간
schedule.cron=0,30 * * * *     # 매시간 정각과 30분
schedule.cron=0 0 * * 1-5      # 평일 자정
schedule.cron=0 0 1,15 * *     # 매월 1일과 15일 자정
```

## 개발 환경 설정

### 요구사항
- **Java**: JDK 8 이상

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

## 라이선스

이 프로젝트는 [MIT License](LICENSE) 하에 배포됩니다.

## 지원

- 이메일: ckr3453@gmail.com
- 이슈 리포트: [GitHub Issues](https://github.com/ckr3453/db-backup4j/issues)
- 토론: [GitHub Discussions](https://github.com/ckr3453/db-backup4j/discussions)
