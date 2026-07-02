# ForwardSMS

## 프로젝트 개요
안드로이드 OS에서 실행되는 고성능 SMS/RCS 메시지 자동 포워딩 애플리케이션입니다.
SMS와 RCS 메시지를 실시간으로 감지하여 키워드 기반 필터링을 통해 웹훅 URL을 자동 호출하는 기능을 제공합니다.
메시지 처리 최적화, 보안 강화, 중복 방지 등 프로덕션 환경에 적합한 고급 기능들이 구현되어 있습니다.

---

## 주요 기능

### 1. 메시지 감지 및 처리
- **SMS 수신**: BroadcastReceiver를 통한 실시간 SMS 감지, 여러 PDU로 분할되는 장문 메시지(LMS)는 전체 조각을 합쳐 온전한 본문으로 처리
- **RCS 수신**: NotificationListenerService를 통한 RCS 메시지 감지, 알림의 `android.bigText`(전체 본문)를 `android.text`(축약 미리보기)보다 우선 사용해 긴 메시지 잘림 방지
- **MMS 수신**: `WAP_PUSH_RECEIVED`는 다운로드 위치 정보만 담고 있어 본문이 없으므로, `MmsContentObserver`가 `content://mms` 변화를 감지해 다운로드 완료된 실제 본문(발신자/텍스트)을 읽어와 처리
- **알림 기반 감지 토글**: 설정에서 알림 기반(RCS) 감지를 껐다 켤 수 있음 — 끄면 RCS는 감지되지 않지만 SMS/LMS는 항상 전체 내용으로 안정적으로 감지됨
- **중복 방지**: 발신자+본문 기반 해시로 5초 윈도우 내 중복 메시지 필터링(`ConcurrentHashMap.compute`로 확인·기록을 원자적으로 처리해 동시성 경합 방지), MMS는 별도로 메시지 ID 기반 중복 전달 방지
- **비동기 처리**: 큐 기반 백그라운드 메시지 처리 (최대 1000개)
- **참고**: 긴급재난문자(Cell Broadcast, `SMS_CB_RECEIVED`)는 본문이 비공개(hidden) 시스템 API로만 노출되어 일반 앱에서는 공개 SDK로 내용을 읽을 수 없음(처리 생략)

### 2. 고성능 필터 시스템
- **실시간 매칭**: 최적화된 키워드 매칭 알고리즘
- **필터 인덱싱**: 빠른 검색을 위한 자동 인덱스 갱신
- **병렬 처리**: 최대 5개 웹훅 동시 호출
- **CRUD 지원**: 필터 생성/수정/삭제/활성화 토글

### 3. 웹훅 호출 시스템
- **다중 방식**: GET/POST 방식 지원
- **URL 템플릿**: 동적 파라미터 치환 ({message}, {sender}, {timestamp})
- **재시도 로직**: 네트워크 오류 시 자동 재시도
- **오프라인 큐**: 네트워크 불가 시 큐잉 후 재전송

### 4. 데이터 관리
- **Room 데이터베이스**: 필터 및 히스토리 영구 저장
- **히스토리 추적**: 모든 메시지 처리 결과 기록
- **페이징 처리**: 대용량 데이터 효율적 처리
- **자동 정리**: 1주일 이상 된 히스토리 자동 삭제

### 5. 보안 및 최적화
- **ProGuard**: 릴리즈 빌드 시 코드 난독화 및 최적화
- **보안 로깅**: 민감한 데이터 마스킹 처리
- **메모리 관리**: 캐시 크기 제한 (최대 1000개) 및 자동 정리
- **네트워크 보안**: HTTPS 강제, 인증서 핀닝 지원

### 6. 시스템 진단 및 설정 화면
- **시스템 최적화 화면**: 필터 목록 화면 상단의 설정 아이콘에서 진입, 배터리 최적화/자동 시작/네트워크 상태를 진단하고 안내
- **네트워크 상태 진단**: `NET_CAPABILITY_INTERNET` 기준으로 판단(시스템 자체 연결성 점검용 `NET_CAPABILITY_VALIDATED`는 제외 — 사내망/캡티브 포털 등에서 그 점검만 실패해도 웹훅 호출 자체는 정상 동작할 수 있으므로)
- **배터리 최적화 제외 안내**: `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` 권한을 선언해 예외 요청 다이얼로그를 직접 띄우고, 실패 시 일반 배터리 설정으로 폴백, 화면 재진입(`ON_RESUME`) 시 최신 상태로 자동 갱신
- **알림 기반 감지 토글**: `AppSettings`(SharedPreferences)로 영속화되는 RCS 알림 모니터링 on/off 스위치 제공

---

## 기술 스택
- **언어**: Kotlin
- **플랫폼**: Android OS (minSdk 26, targetSdk 34)
- **네트워킹**: Retrofit2 / OkHttp3 (재시도, 로깅 인터셉터)
- **데이터베이스**: Room DB (Flow 기반 반응형)
- **UI**: Jetpack Compose (Navigation, Material3)
- **아키텍처**: MVVM + Repository Pattern
- **비동기**: Kotlin Coroutines + Flow
- **보안**: ProGuard, 네트워크 보안 설정

---

## 권한 및 설정
```xml
<!-- 기본 SMS 권한 -->
<uses-permission android:name="android.permission.RECEIVE_SMS" />
<uses-permission android:name="android.permission.READ_SMS" />
<uses-permission android:name="android.permission.RECEIVE_MMS" />

<!-- 네트워크 및 서비스 권한 -->
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />

<!-- 알림 및 RCS 지원 -->
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE" />

<!-- 배터리 최적화 제외 요청(ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)에 필요 -->
<uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" />

<!-- 하드웨어 요구사항 -->
<uses-feature android:name="android.hardware.telephony" android:required="false" />
```

## 아키텍처 구조

### 핵심 컴포넌트

1. **메시지 수신 계층**
   - `SmsReceiver`: SMS/MMS/WAP Push 브로드캐스트 수신 (다중 PDU 장문 SMS 병합 처리 포함)
   - `MessageNotificationListener`: RCS 알림 감지(`AppSettings` 토글로 on/off), bigText 우선 파싱
   - `MmsContentObserver`: `content://mms` 관찰을 통한 실제 MMS 본문 확보(`MessageProcessingService`에 등록되어 서비스 생명주기와 함께 관리, Mutex로 중복 전달 방지)
   - `MessageProcessor`: 비동기 메시지 처리 및 큐 관리, 발신자 포함 해시 기반 원자적 중복 방지

2. **필터링 계층**
   - `FilterMatcher`: 최적화된 키워드 매칭 엔진
   - `FilterRepository`: 필터 데이터 관리 및 비즈니스 로직

3. **네트워킹 계층**
   - `RetrofitClient`: HTTP 클라이언트 싱글톤
   - `RetryInterceptor`: 자동 재시도 로직
   - `OfflineQueueService`: 오프라인 메시지 큐잉

4. **UI 계층**
   - `FilterViewModel`: 상태 관리 및 비즈니스 로직
   - Compose Screens: 선언적 UI 구성 (필터 목록/이력/로그/시스템 최적화 등)

5. **설정/유틸리티 계층**
   - `AppSettings`: SharedPreferences 기반 사용자 설정(알림 기반 감지 토글 등) 저장
   - `SystemStateManager`: 배터리 최적화/네트워크/자동 시작 등 시스템 상태 진단

### 데이터 흐름

```
SMS/RCS 수신 → MessageProcessor → FilterMatcher → 웹훅 호출 → 히스토리 저장
```

## 주요 구현 예시

### 1. 고성능 메시지 처리
```kotlin
class MessageProcessor {
    private val messageQueue = Channel<MessageTask>(capacity = 1000)
    private val recentMessageHashes = ConcurrentHashMap<String, Long>()
    private val MAX_CACHE_SIZE = 1000

    suspend fun processMessage(messageBody: String, sender: String?, timestamp: Long) {
        // 발신자를 포함한 해시 + compute()로 확인·기록을 원자적으로 수행해
        // 동시 호출 시에도 중복 판정 경합(TOCTOU)이 발생하지 않도록 함
        val messageHash = generateMessageHash(messageBody, sender)
        if (isDuplicateAndRecord(messageHash, System.currentTimeMillis())) return

        messageQueue.trySend(MessageTask(messageBody, sender, timestamp))
    }
}
```

### 2. 필터 데이터 모델
```kotlin
@Entity(tableName = "filters")
data class Filter(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val keyword: String,
    val url: String,
    val method: String, // GET / POST
    val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "filter_history")
data class FilterHistory(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val filterId: Int,
    val filterKeyword: String,
    val smsMessage: String,
    val sender: String,
    val webhookUrl: String,
    val httpMethod: String,
    val timestamp: Long,
    val success: Boolean,
    val responseCode: Int?,
    val errorMessage: String?
)
```

### 3. 웹훅 API 인터페이스
```kotlin
interface ApiService {
    @FormUrlEncoded
    @POST
    suspend fun sendMessagePost(
        @Url url: String,
        @Field("message") message: String,
        @Field("sender") sender: String?,
        @Field("timestamp") timestamp: Long
    ): Response<Unit>

    @GET
    suspend fun sendGetRequest(@Url url: String): Response<Unit>
}
```

## 실행 시나리오

### 기본 시나리오
1. **필터 등록**: 키워드 "인증번호", URL "https://api.example.com/webhook", 방식 "POST"
2. **SMS 수신**: "KT 인증번호는 [123456] 입니다."
3. **메시지 처리**:
   - 중복 검사 → 통과
   - 키워드 매칭 → "인증번호" 발견
   - 웹훅 호출 실행

### POST 요청 예시
```json
{
  "message": "KT 인증번호는 [123456] 입니다.",
  "sender": "0802580303",
  "timestamp": 1704067200000
}
```

### GET 요청 예시
```
https://api.example.com/webhook?message=KT%20%EC%9D%B8%EC%A6%9D%EB%B2%88%ED%98%B8%EB%8A%94%20%5B123456%5D%20%EC%9E%85%EB%8B%88%EB%8B%A4.&sender=0802580303&timestamp=1704067200000
```

### 히스토리 기록
모든 처리 결과가 데이터베이스에 저장되어 성공/실패 추적 가능

---

## 배포 및 빌드

### ProGuard 설정
```gradle
buildTypes {
    release {
        isMinifyEnabled = true
        proguardFiles(
            getDefaultProguardFile("proguard-android-optimize.txt"),
            "proguard-rules.pro"
        )
    }
}
```

### 빌드 명령어
```bash
# 디버그 빌드
./gradlew assembleDebug

# 릴리즈 빌드 (ProGuard 적용)
./gradlew assembleRelease

# 테스트 실행
./gradlew test
```

---

## 성능 및 안정성

### 메모리 최적화
- 메시지 큐: 최대 1000개 제한
- 캐시 관리: 자동 정리 및 크기 제한
- 히스토리: 1주일 단위 자동 삭제

### 보안 기능
- 민감한 데이터 마스킹
- HTTPS 강제 적용
- ProGuard 코드 난독화

### 안정성
- 예외 처리 및 복구 로직
- 오프라인 모드 지원
- 자동 재시도 메커니즘

---

## 프로젝트 상태

✅ **완성도**: 95% (프로덕션 배포 준비 완료)
✅ **핵심 기능**: 100% 구현
✅ **고급 기능**: 성능 최적화, 보안 강화, 안정성 확보
✅ **테스트**: 기본 테스트 프레임워크 설정
✅ **문서화**: 상세한 구현 문서 및 가이드
