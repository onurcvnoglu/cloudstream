## 2026-08-24 - Bölüm Kaynak ve Altyazı Tercihi

- Ana Odak: Oynatıcıda açıkça seçilen kaynak ve altyazı tercihlerini bölüm oturumu boyunca korumak.

### Yapılan İşlemler (Sırasıyla)

1. **Kaynak seçimi:** `ExtractorLink.source` ile tam eşleşen, aynı kaynak içindeki mevcut sıralamayı koruyan ve eşleşme yoksa eski ilk kullanılabilir bağlantıya dönen seçim yardımcısı eklendi.
2. **Oturum tercihi:** Kaynak seçimi ile altyazının dil/ad/origin/kaynak kimliği `GeneratorPlayer` içinde kullanıcı seçimi olarak tutuldu. Otomatik seçim ve failover bu tercihi değiştirmiyor.
3. **Bölüm geçişleri:** İleri, geri ve bölüm listesinden seçim sonrasında tercihler korunuyor; `releasePlayer()` yalnızca aktif oynatıcı seçimini temizliyor.
4. **Asenkron yükleme:** Tercih edilen kaynak gelmeden erken gelen başka bir kaynak otomatik başlatılmıyor. Yükleme tamamlanır veya kullanıcı yüklemeyi geçerse mevcut fallback davranışı devreye giriyor.
5. **Altyazı-kaynak ilişkisi:** Extractor callback sırasına veya `nameSuffix` değerine güvenmek yerine `LinkGenerator` altyazıları linkler bilinene kadar tamponluyor. Tek bir kaynak güvenilir biçimde belirlenirse `SubtitleData.source` alanına yazılıyor; doğrudan provider, online, downloaded ve embedded altyazılarda alan `null` kalıyor.
6. **Altyazı otomatik seçimi:** Aynı bölümdeki açık seçim, önceki bölümdeki kararlı tercih, seçili kaynakla ilişkili dil ve mevcut global/download fallback sırası uygulandı.
7. **Failover:** Tercihli bağlantı hata verdiğinde sıralamadaki diğer kullanılabilir bağlantılar, mevcut bağlantı dışlanarak ve listenin başına sarılabilecek şekilde taranıyor. Yeni kaynakta eski kaynağa bağlı altyazı korunmuyor.
8. **Geri geçiş:** `loadLinksPrev()` içindeki indeks artışı azaltma olarak düzeltildi; ilk bölüm sınırı korunuyor.
9. **Test kapsamı:** Kaynak seçimi, kaynak ilişkili altyazı, URL'den bağımsız altyazı tercihi ve geri bölüm indeks sınırı için birim testleri eklendi.

### Yeni ve Güncellenen API Endpointleri

- Endpoint değişikliği yok.
- `library/` içindeki public/protected API bildirimi değiştirilmedi.

### Frontend İçin Notlar

- Telefon ve TV oynatıcı akışında kullanıcı bir kaynak seçtiğinde sonraki bölümde aynı `source` kimliği aranır.
- Kaynak bulunamazsa kalite profili sıralaması ve mevcut fallback davranışı kullanılır.
- Kaynak ilişkisi olmayan altyazılar kaynak filtresine zorlanmaz; mevcut otomatik dil/download kurallarına düşer.
- Gerçek telefon ve TV manuel doğrulaması, ortamda `adb` veya bağlı emulator/cihaz bulunmadığı için yapılamadı.

### Teknik Değişim Detayı

- Değişen dosyalar:
  - `app/src/main/java/com/lagradost/cloudstream3/ui/player/GeneratorPlayer.kt`
  - `app/src/main/java/com/lagradost/cloudstream3/ui/player/PlayerGeneratorViewModel.kt`
  - `app/src/main/java/com/lagradost/cloudstream3/ui/player/PlayerSubtitleHelper.kt`
  - `app/src/main/java/com/lagradost/cloudstream3/ui/player/PlayerSelection.kt`
  - `app/src/main/java/com/lagradost/cloudstream3/ui/player/LinkGenerator.kt`
  - `app/src/main/java/com/lagradost/cloudstream3/ui/player/RepoLinkGenerator.kt`
  - `app/src/test/java/com/lagradost/cloudstream3/PlayerSelectionTest.kt`
- Kritik fonksiyonlar: `startPlayer`, `loadLink`, `getAutoSelectSubtitle`, `autoSelectFromSettings`, `getNextLink`, `showMirrorsDialogue`, `loadLinksPrev`.
- Kalıcı/public API değişikliği yapılmadı; tercihler oynatıcı oturumu kapsamındadır.

### Doğrulama

- `ANDROID_HOME="$HOME/Library/Android/sdk" ./gradlew testPrereleaseDebugUnitTest` — başarılı.
- `ANDROID_HOME="$HOME/Library/Android/sdk" ./gradlew assemblePrereleaseDebug lintPrereleaseDebug` — başarılı.
- `library:checkKotlinAbi` — `library/` public API’si değişmediği için çalıştırılmadı.

### Yapılan İşler Durumu (Todo Status)

- [x] Kaynak tercih yardımcısı ve erken otomatik başlatma kontrolü
- [x] Kullanıcı kaynak/altyazı tercihinin oturum boyunca korunması
- [x] Kaynakla uyumlu altyazı otomatik seçimi
- [x] Extractor callback yarışının tamponlanması
- [x] Geri bölüm indeks hatasının düzeltilmesi
- [x] Kaynak hata fallback’ının genişletilmesi
- [x] İlgili birim testlerinin eklenmesi
- [ ] Skip, yerel URI, embedded/downloaded, kapsamlı failover ve gerçek cihaz regresyonlarının otomatik/manuel doğrulaması
