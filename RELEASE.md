# 배포 및 자동 업데이트 가이드

이 앱은 **GitHub Releases** 기반 자동 업데이트를 지원합니다.
사용자 폰에서 앱을 실행하면 `github.com/leeseyoung77/my-secretary/releases/latest`를 조회해
새 버전이 있으면 다이얼로그를 띄우고, 사용자가 동의하면 APK를 받아 시스템 설치자를 호출합니다.

## 1. GitHub 저장소 준비 (최초 1회)

1. GitHub에 `my-secretary`라는 이름의 **공개** 저장소를 생성 (계정: `leeseyoung77`)
2. 로컬 코드 푸시:
   ```bash
   cd d:\test_lsy\my-secretary
   git init
   git add .
   git commit -m "Initial commit"
   git branch -M main
   git remote add origin https://github.com/leeseyoung77/my-secretary.git
   git push -u origin main
   ```
3. 저장소 이름을 다르게 쓰고 싶다면 `app/build.gradle.kts`의
   `buildConfigField("String", "GITHUB_REPO", ...)`를 수정

## 2. 서명 키스토어 (중요)

자동 업데이트가 동작하려면 **모든 릴리즈가 동일한 키로 서명**되어 있어야 합니다.
서명이 다르면 Android가 "다른 앱"으로 간주해 업데이트를 거부합니다.

### 옵션 A: 디버그 키 그대로 사용 (개인 용도, 가장 간단)

- GitHub Actions의 우분투 러너에서 매번 빌드되는 디버그 키는 매번 달라집니다.
- 따라서 **디버그 빌드를 그대로 자동 배포하면 업데이트가 안 됩니다.**

### 옵션 B: 전용 디버그 키스토어를 저장소에 포함 (권장 - 개인용)

```bash
# 1회만 생성
keytool -genkey -v -keystore release.keystore \
  -alias my-secretary -keyalg RSA -keysize 2048 -validity 36500 \
  -storepass mysecretary -keypass mysecretary \
  -dname "CN=Lee Seyoung, OU=Personal, O=Personal, L=Seoul, ST=Seoul, C=KR"
```

생성된 `release.keystore`를 프로젝트 루트에 두고 `app/build.gradle.kts`에 서명 설정 추가:

```kotlin
android {
    signingConfigs {
        create("release") {
            storeFile = file("../release.keystore")
            storePassword = "mysecretary"
            keyAlias = "my-secretary"
            keyPassword = "mysecretary"
        }
    }
    buildTypes {
        debug { signingConfig = signingConfigs.getByName("release") }
        release { signingConfig = signingConfigs.getByName("release") }
    }
}
```

→ 항상 같은 키로 서명됨. 개인 프로젝트에는 충분.

> **주의**: 키스토어 비밀번호가 코드에 들어가므로 저장소가 공개라면 키 노출 위험이 있습니다.
> 공개 배포가 아니라면 패스워드를 강력하게 설정하고 신경 쓰지 않아도 됩니다.

### 옵션 C: GitHub Secrets로 키 관리 (정식)

키스토어를 base64로 인코딩해 secret에 저장하고 워크플로에서 복원:

```yaml
- name: Decode keystore
  run: echo "${{ secrets.KEYSTORE_BASE64 }}" | base64 -d > release.keystore
```

이 가이드에서는 옵션 B를 가정합니다.

## 3. 첫 번째 릴리즈

1. 키스토어 세팅이 끝나면 **로컬에서** 한 번 빌드해서 폰에 설치 (이게 v0.1.0 기준점)
   ```bash
   ./gradlew assembleDebug
   ```
   생성된 `app/build/outputs/apk/debug/app-debug.apk`를 폰에 설치
2. 폰 설정 → 앱 → My Secretary → "알 수 없는 앱 설치" 허용
3. (이후 다른 릴리즈가 자동 업데이트로 처리됨)

## 4. 새 버전 릴리즈하기

1. `app/build.gradle.kts`에서 버전 올림:
   ```kotlin
   versionCode = 2
   versionName = "0.2.0"
   ```
2. 커밋 + 태그 푸시:
   ```bash
   git add app/build.gradle.kts
   git commit -m "Bump version to 0.2.0"
   git tag v0.2.0
   git push origin main
   git push origin v0.2.0
   ```
3. GitHub Actions가 자동으로:
   - APK를 빌드
   - GitHub Release를 생성 (`v0.2.0` 태그 기준)
   - 릴리즈 노트는 커밋 메시지에서 자동 생성
   - APK를 release asset으로 첨부
4. 폰에서 앱을 실행하면 "새 버전이 있습니다" 다이얼로그가 자동으로 뜸 → "지금 업데이트" 탭

## 5. 수동 릴리즈 (워크플로 안 쓸 때)

GitHub Actions를 안 쓰려면 직접:

1. 로컬에서 APK 빌드 (`./gradlew assembleDebug`)
2. GitHub 저장소 페이지 → Releases → "Draft a new release"
3. Tag: `v0.2.0`
4. APK 파일을 첨부
5. Publish

## 6. 업데이트 흐름 (사용자 입장)

```
앱 실행
  ↓
GitHub API 조회 (백그라운드)
  ↓ 새 버전 있음?
  ├─ 없음 → 평소대로 캘린더 표시
  └─ 있음 → "새 버전이 있습니다 v0.2.0" 다이얼로그
              ├─ [나중에] → 닫고 캘린더로
              └─ [지금 업데이트]
                    ↓
                  설치 권한 있나?
                    ├─ 없음 → 설정 화면 안내
                    └─ 있음 → DownloadManager로 APK 받기
                              (시스템 알림에 진행률 표시)
                              ↓
                         다운로드 완료 → 시스템 설치자 자동 호출
                              ↓
                         사용자가 "설치" 탭 → 새 버전으로 교체 후 실행
```

## 7. 트러블슈팅

| 증상 | 원인 / 해결 |
|------|-----------|
| 업데이트 다이얼로그가 안 뜸 | (a) GitHub release가 아직 없음 (b) `tag_name`이 `v` prefix 없거나 형식이 다름 (c) `draft`/`prerelease` 상태 |
| 다운로드는 되는데 설치 화면이 안 뜸 | "알 수 없는 앱 설치" 권한 없음 → 설정에서 허용 |
| 설치 화면에서 "앱이 설치되지 않음" 오류 | 서명 키가 다름 → 옵션 B로 키스토어 통일 |
| GitHub Actions 실패 | Actions 탭에서 로그 확인. Gradle wrapper 없으면 ./gradlew 실행 안 됨 → 로컬에서 `gradle wrapper` 실행 후 커밋 |
| 첫 출시 후 사용자가 자동 업데이트 못 받음 | 사용자가 받은 첫 APK도 동일 키로 서명되어야 함 |

## 8. 보안 주의사항

- HTTPS만 사용 (GitHub API는 HTTPS 강제)
- APK 무결성: 동일 서명 키 비교로 안드로이드가 자동 검증
- 키스토어를 공개 저장소에 평문으로 두면 누구나 가짜 업데이트를 만들 수 있음 → 공개 배포라면 Secrets 사용
