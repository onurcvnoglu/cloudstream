# 2026-08-24 - Altyazı Diline Göre Otomatik Kaynak Seçimi

- Ana Odak: İlk oynatıcı açılışında, otomatik altyazı dilini taşıyan kaynak bağlı altyazıya sahip kullanılabilir kaynağın seçilmesi.

### Yapılan İşlemler

1. **Kaynak seçim politikası:** Açık `preferredSource` önceliği korunarak, tercih yoksa ayarlanan altyazı diline ait `SubtitleData.source` ile eşleşen ilk kullanılabilir bağlantı seçiliyor. Eşleşme yoksa mevcut kalite sıralı fallback kullanılıyor.
2. **Asenkron callback yarışı:** Link veya altyazı callback'i önce gelse bile ilk yükleme, kaynak bağlı dil eşleşmesi görülene kadar erken fallback başlatmıyor. Yükleme tamamlandığında veya kullanıcı skip yaptığında mevcut fallback davranışı devreye giriyor.
3. **Oturum tercihi:** Altyazı dili nedeniyle yapılan otomatik kaynak seçimi `preferredSource` değerini değiştirmiyor. Kaynak tercihi yalnızca kaynak diyaloğundaki Apply işlemiyle kaydediliyor.
4. **Altyazı otomatik seçimi:** Seçilen kaynakla ilişkili dil eşleşmesi, global veya indirilen altyazı fallback'ından önce uygulanıyor. Altyazı sonradan geldiğinde seçim yalnızca aktif kaynakla uyumluysa yenileniyor.
5. **Bölüm ve failover davranışı:** Açık kaynak tercihi bölüm geçişlerinde korunuyor; skip ve kaynak hatası sonrası `getNextLink()` sırası ile otomatik altyazı zinciri değişmeden çalışıyor.
6. **Testler:** Dil eşleşmeli kaynak seçimi, aynı dilde sıralı kaynaklar, açık kaynak önceliği, kullanılmayan bağlantılar ve kaynaksız/indirilen altyazı fallback senaryoları için `PlayerSelectionTest` genişletildi.

### Değişen Dosyalar

- `app/src/main/java/com/lagradost/cloudstream3/ui/player/GeneratorPlayer.kt`
- `app/src/main/java/com/lagradost/cloudstream3/ui/player/PlayerSelection.kt`
- `app/src/test/java/com/lagradost/cloudstream3/PlayerSelectionTest.kt`

### Doğrulama

- `ANDROID_HOME="$HOME/Library/Android/sdk" ./gradlew testPrereleaseDebugUnitTest` — başarılı.
- `ANDROID_HOME="$HOME/Library/Android/sdk" ./gradlew assemblePrereleaseDebug lintPrereleaseDebug` — başarılı.
- Telefon ve TV manuel regresyonu — çalıştırılamadı; ortamda `adb` kurulu değil.
- `library:checkKotlinAbi` — `library/` public/protected API'si değişmediği için çalıştırılmadı.
