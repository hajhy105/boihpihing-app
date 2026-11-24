# 보이흐피힝 (안드로이드 앱)

3D 웹 버전 게임(`app/src/main/assets/index.html`)을 안드로이드 앱으로 감싼 프로젝트예요. 인터넷 없이도 실행되고, 기록은 앱 안에 저장돼요.

## APK 받기 (휴대폰)
1. 이 저장소의 **Actions** 탭에서 `Build APK`가 초록색 체크로 끝났는지 확인해요.
2. 저장소 첫 화면의 **Releases**에서 가장 최근 `보이흐피힝 빌드 N`을 열어요.
3. **Assets**의 `boihpihing-N.apk`를 눌러 받은 뒤 설치해요. 처음이면 "출처를 알 수 없는 앱 설치"를 허용해야 해요.

## 게임 고치기
`app/src/main/assets/index.html`을 바꿔서 `main` 브랜치에 올리면 새 APK가 자동으로 만들어져요.

## 참고
- 지금 APK는 테스트용(debug)이에요. 구글 플레이 출시용(서명된 AAB)은 따로 설정해야 해요.
- 패키지 이름: `com.boihpihing.app`
