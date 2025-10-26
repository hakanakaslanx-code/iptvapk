# IPTV Smarters Clone Sample

Bu proje, IPTV Smarters uygulamasına benzer temel özellikleri barındıran örnek bir Android (Jetpack Compose) uygulamasını göstermektedir. Kod, ExoPlayer kullanarak M3U playlistlerinden canlı yayın oynatmayı, basit bir kanal listesi göstermeyi ve kullanıcıya kendi yayın URL'lerini girme imkanı sunar.

## Özellikler

- M3U playlist URL'si girerek kanal listesini yükleme
- Kanal adı, grup ve logo gibi meta bilgileri gösterme
- ExoPlayer ile seçilen kanalı oynatma
- Material 3, dinamik renk desteği ve Jetpack Compose ile modern arayüz
- ViewModel tabanlı durum yönetimi ve hata bildirimi için Snackbar kullanımı
- HTTP tabanlı yayınlar için Android ağ güvenliği yapılandırması

## Yapı

- `MainActivity.kt`: Compose arayüz katmanı, ExoPlayer ön yüzü ve ViewModel ile etkileşim
- `IPTVViewModel.kt`: Playlist yükleme, hata yönetimi ve durum saklama mantığı
- `AndroidManifest.xml`: Gerekli izinler ve başlangıç aktivitesi
- `ui/theme/Theme.kt`: Material 3 teması ve dinamik renk desteği
- `strings.xml`: UI metinleri

## Derleme Talimatları

1. Android Studio'nun en güncel sürümünü kurun.
2. Yeni bir "Empty Compose Activity" projesi oluşturun.
3. Oluşturulan proje içinde `app/src/main` altındaki ilgili dosyaları bu depo ile değiştirin.
4. `build.gradle` dosyasında aşağıdaki bağımlılıkların bulunduğundan emin olun:

```kotlin
dependencies {
    implementation("androidx.activity:activity-compose:1.8.0")
    implementation(platform("androidx.compose:compose-bom:2023.08.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.6.2")
    implementation("com.google.android.exoplayer:exoplayer:2.19.1")
}
```

5. Uygulamayı bir Android cihazda veya emülatörde çalıştırın.

## Playlist Formatı

Kod, standart M3U playlistlerini destekler. Örnek bir satır:

```
#EXTINF:-1 tvg-id="" tvg-name="Channel" tvg-logo="https://example.com/logo.png" group-title="News",Channel Name
https://example.com/stream.m3u8
```

## Geliştirme Önerileri

- EPG (Elektronik Program Rehberi) entegrasyonu için `epgUrl` alanı kullanılabilir.
- Kullanıcı kimlik doğrulaması ve abonelik yönetimi eklenebilir.
- TV cihazları için Android TV arayüzü tasarlanabilir.

Bu örnek, IPTV Smarters benzeri bir uygulamanın temelini oluşturur ve projeyi ihtiyaçlara göre genişletmek mümkündür.
