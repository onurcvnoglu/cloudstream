---
schema: task-plan/v1
id: 20260901-0053-persist-playback-selection-priority
status: partial
created_at: 2026-09-01T00:53:25+03:00
updated_at: 2026-09-01T01:31:00+03:00
base_commit: f1f113856df8ba7fba1ed4f0546d9de992ebe188
---

# Kalıcı Oynatma Tercihi ve Profil Sıralaması Düzeltmesi

## Goal
Kullanıcının bir dizi/içerik için açıkça seçtiği kaynak ve altyazıyı yeni oyuncu oturumlarında da geri yükle. Açık tercih yoksa ilk oynatmayı aktif Wi‑Fi/Mobil profilinin kaynak + çözünürlük puanına göre, tüm link yüklemesi tamamlandıktan sonra başlat.

## Repository Context
- `GeneratorPlayer.preferredSource` ve `preferredSubtitle` yalnızca fragment belleğinde yaşar; kaynak hiç kalıcılaştırılmaz, altyazıdan yalnızca global `SUBTITLE_AUTO_SELECT_KEY` dil ayarı kalıcıdır.
- Aktif profil ağ türüyle seçilir; `QualityDataHelper.getLinkPriority` kaynak ve en yakın çözünürlük puanını toplar, `VideoState.sortLinks` toplam puana göre sıralar.
- `currentLinks` gözlemcisi linkler akarken `AUTO_SKIP_PRIORITY` veya kaynak-bağlı altyazı eşleşmesiyle oyuncuyu erken başlatabilir; sonradan gelen daha yüksek puanlı link artık seçilemez.
- Dizi bölümlerinde kalıcı anahtar için `ResultEpisode.parentId`; yerel oynatma için `ExtractorUri.parentId` kullanılabilir. DataStore anahtarları `currentAccount` altında tutulur.

## Review Scope
- Owned surfaces: `DataStoreHelper` oynatma-tercihi depolaması; `GeneratorPlayer` tercih yükleme/kaydetme ve başlangıç akışı; `PlayerSelection` saf seçim politikası; ilgili birim testleri.
- Direct relations: `ResultEpisode.parentId`, `ExtractorUri.parentId`, `SubtitlePreference`, `QualityDataHelper.getLinkPriority`, `VideoState.sortLinks`, `loadingLinks` ve `currentLinks` gözlemcileri.
- Out of scope: `library/` public API, eklenti/extractor sözleşmeleri, indirme sıralaması, harici oynatıcılar, profil düzenleme ekranının puan arayüzü ve mevcut ilgisiz çalışma ağacı değişiklikleri.

## Tasks

- [x] **T01 — İçerik bazlı kalıcı seçim kaydını ekle**
  - Hesap + `parentId` altında kaynak kimliği ve tam altyazı tercih kimliğini (dil, ad, origin, source ve “altyazı yok” durumu) taşıyan serializable bir kayıt ile okuma/yazma/silme yardımcılarını ekle.
  - Files/symbols: `app/src/main/java/com/lagradost/cloudstream3/utils/DataStoreHelper.kt`
  - Invariants: Ham video/subtitle URL’si veya header saklama; olmayan ya da eski tercih güvenli fallback’e düşmeli.

- [x] **T02 — Oynatıcı açılışında kalıcı tercihi başlangıçtan önce geri yükle**
  - `ResultEpisode.parentId` ve `ExtractorUri.parentId` üzerinden tercih kapsamını çöz; anahtar yoksa mevcut oturum/global altyazı dili davranışını koru.
  - Tercihi link callback’leri otomatik oynatmayı tetiklemeden önce `preferredSource` ve `preferredSubtitle` alanlarına yükle; bölüm geçişinde aynı içerik kapsamını yeniden kullan.
  - Files/symbols: `GeneratorPlayer.onBindingCreated`, `PlayerGeneratorViewModel.loadLinks`, `GeneratorState.meta`

- [x] **T03 — Yalnızca açık kullanıcı seçimlerini kalıcılaştır**
  - Kaynak diyaloğunda kaynak gerçekten değiştiğinde seçili `sourceId` değerini kaydet; yalnız altyazı değiştirmek mevcut kaynağı yanlışlıkla açık kaynak tercihi yapmamalı.
  - Kullanıcının seçtiği altyazı veya “altyazı yok” tercihini aynı içerik kaydına yaz; otomatik seçim ve hata failover bu kaydı değiştirmesin.
  - Files/symbols: `GeneratorPlayer.setSubtitles`, `rememberSourcePreference`, kaynak diyaloğunun `applyBtt` akışı

- [x] **T04 — Kalıcı kaynak ve altyazı için belirli fallback sırasını uygula**
  - Seçim sırasını `kalıcı açık kaynak → kalıcı altyazının kaynak ilişkisi → aktif profilin sıralı ilk kullanılabilir linki` olarak saf ve test edilebilir tut.
  - Tercih edilen kaynak/altyazı yükleme sürerken beklenmeli; yükleme terminal duruma geçtiğinde bulunamayan tercih profil sıralı fallback’i engellememeli.
  - Files/symbols: `PlayerSelection.selectPreferredLink`, `hasSourceLinkedSubtitle`, `GeneratorPlayer.startPlayer`, `getAutoSelectSubtitle`

- [x] **T05 — Varsayılan ilk oynatmayı kısmi link listesine göre kilitlemeyi kaldır**
  - Açık kalıcı tercih yokken `currentLinks` üzerinden yapılan `AUTO_SKIP_PRIORITY` ve altyazı-eşleşmesi erken başlatmalarını, tamamlanmamış yüklemede geri döndürülemez seçim yapmayacak şekilde sınırla; varsayılan karar `Resource.Success`/`Failure` sonrasında sıralı listenin ilk öğesi olmalı.
  - Kullanıcının Skip eylemi ile açık kaynak/altyazı tercihini bekleme davranışı korunmalı; aktif oynatma başladıktan sonra link değiştirilmemeli.
  - Files/symbols: `GeneratorPlayer.currentLinks`, `loadingLinks`, `startPlayer`, `QualityDataHelper.AUTO_SKIP_PRIORITY`

- [x] **T06 — Altyazı geri yükleme ve kaynak bağını doğrula**
  - Kalıcı tam altyazı tercihi varsa aynı kimliği ve kaynak bağını önce seç; bulunmazsa global otomatik dil ayarına ve mevcut downloaded/global fallback zincirine düş.
  - Kaynak değişiminde eski kaynağa bağlı altyazıyı taşımama ve “altyazı yok” tercihini koruma kurallarını sürdür.
  - Files/symbols: `GeneratorPlayer.loadLink`, `getAutoSelectSubtitle`, `autoSelectFromSettings`, `SubtitlePreference.matches`

- [x] **T07 — Saf politika için regresyon kapsamı ekle**
  - Yeni oturumda kalıcı kaynak + tam altyazı geri yükleme, yalnız altyazı değişiminde kaynak kaydetmeme, bulunamayan tercihte fallback ve ağ profilinin sıralı varsayılan seçimi senaryolarını kapsa.
  - Kısmi link yüklemesinde daha düşük puanlı linkin varsayılan oynatmayı kilitlemediğini; açık tercih ve Skip/failover davranışlarının korunmasını doğrula.
  - Files/symbols: `app/src/test/java/com/lagradost/cloudstream3/PlayerSelectionTest.kt`, gerekirse yeni dar kapsamlı DataStore/oynatıcı politika testi

- [ ] **T08 — [Critical Review/medium] Kalıcı tercih geri yükleme sözleşmesini doğrudan test et**
  - Problem: Yeni oturumda kalıcı kaynak ve tam altyazı tercihinin geri yüklenmesi T07'nin zorunlu senaryosudur; mevcut saf seçim testleri depolanan kaydın `sourceId`, altyazı kimliği, `isNone` ve geçersiz `origin` davranışının `GeneratorPlayer` geri yükleme zincirine doğru taşındığını kanıtlamaz. Bu sözleşmedeki serializasyon veya güvenli fallback regresyonu fark edilmeden kullanıcı tercihlerini etkileyebilir.
  - Evidence: `app/src/test/java/com/lagradost/cloudstream3/PlayerSelectionTest.kt:42`–`125` yalnızca `selectPreferredLink`/`selectPreferredSubtitle` çağırır → `app/src/main/java/com/lagradost/cloudstream3/utils/DataStoreHelper.kt:248`–`267` kalıcı kayıt şeması → `app/src/main/java/com/lagradost/cloudstream3/ui/player/GeneratorPlayer.kt:253`–`305` geri yükleme ve güvenli dönüşüm.
  - Required fix: Yeni oturum için kalıcı kaynak ve tam altyazı kimliğinin geri yüklenmesini, “altyazı yok” seçimini ve bilinmeyen altyazı origin'inin güvenli fallback'ini doğrudan kapsayan dar kapsamlı regresyon testi ekle.
  - Review finding: `CR01`

## Validation

- [x] **V01 — `./gradlew assemblePrereleaseDebug` ile derleme doğrulamasını çalıştır.**
  - `ANDROID_HOME=/Users/onurcivanoglu/Library/Android/sdk` ile başarılı.
- [x] **V02 — T07 kapsamındaki ilgili birim testleri yalnızca plan uygulaması sırasında açıkça onaylandığında çalıştır.**
  - `ANDROID_HOME=/Users/onurcivanoglu/Library/Android/sdk ./gradlew :app:testPrereleaseDebugUnitTest --tests com.lagradost.cloudstream3.PlayerSelectionTest` başarılı.

- [ ] **V03 — T08 kapsamındaki kalıcı tercih geri yükleme regresyon testini çalıştır.**
  - `ANDROID_HOME=/Users/onurcivanoglu/Library/Android/sdk ./gradlew :app:testPrereleaseDebugUnitTest --tests com.lagradost.cloudstream3.PlayerSelectionTest`

Optional or permission-gated checks:

- Telefon/TV veya emülatör üzerinde devam et izleme, uygulamayı kapatıp yeniden açma, kaynak bulunamama ve profil sırası manuel doğrulaması repository politikası gereği ayrıca istenmedikçe çalıştırılmayacak.

## Implementation Notes
- Önceki altyazı-diline göre otomatik kaynak geliştirmesi açık tercihleri yalnızca oturumda korumak üzere tasarlanmıştı; bu plan kullanıcı tarafından istenen kalıcı içerik tercihini buna ekler.
- Kaynak/altyazı önceliği için gerekli bağlam yorumları yalnızca kalıcılık ve asenkron başlangıç kararının nedenini açıklayan noktalarda eklenmeli.

## Critical Review Scope
- Original tasks: `T01`–`T07`
- Owned surfaces: `DataStoreHelper` oynatma-tercihi kaydı; `GeneratorPlayer` tercih yükleme/kaydetme ve başlangıç akışı; `PlayerSelection` seçim politikası; `PlayerSelectionTest`.
- Direct relations: `ResultEpisode.parentId` ve `ExtractorUri.parentId`; `VideoGenerator.videos`/`PlayerGeneratorViewModel` bölüm geçişi; `SubtitlePreference`; `VideoState.sortLinks`; `loadingLinks` ve `currentLinks` gözlemcileri.
- Excluded: `library/` public API, eklenti/extractor sözleşmeleri, indirme sıralaması, harici oynatıcılar, profil düzenleme ekranı, emülatör doğrulaması ve diğer task-plan değişiklikleri.

## Critical Review
- Reviewed at: `2026-09-01T01:31:00+03:00`
- Cycle: `INITIAL`
- Baseline: `f1f113856df8ba7fba1ed4f0546d9de992ebe188` (`HEAD`) + working tree
- Scope: Dondurulmuş owned surfaces ve yalnız yukarıdaki doğrudan ilişkiler.
- Result: `0 blocker, 0 high, 1 medium`.
- Finding changes: `CR01` yeni.
- Verdict: `CHANGES_REQUESTED`
