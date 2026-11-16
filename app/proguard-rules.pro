# HiddenApiBypass는 ART 내부 객체와 같은 필드 배치를 가진 클래스로 동작한다.
# 필드가 지워지거나 이름이 바뀌면 숨은 API 접근이 깨지므로 그대로 둔다.
-keep class org.lsposed.hiddenapibypass.** { *; }
