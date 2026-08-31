---
schema: task-plan/v1
id: 20260829-2259-tv-home-focus-tmdb-metadata
status: partial
created_at: 2026-08-29T22:59:16Z
updated_at: 2026-08-30T10:12:47Z
base_commit: b7c581fd33ef0f21c5901cfd944e004e24b245af
---

# TV Ana Sayfa Odak, Akıcılık ve TMDB Metadata İyileştirmeleri

## Goal
TV ana sayfasındaki detaydan dönüş ve dikey kategori odak sıçramalarını gidermek, basılı D-pad kaydırmasını akıcı hâle getirmek, “İzlemeye devam et” kartlarında sağlayıcıyı göstermek ve isteğe bağlı TMDB İngilizce metadata fallback entegrasyonu eklemek.

## Repository Context
- Ana sayfa legacy XML/ViewBinding + MVVM yapısında; odak akışı `HomeFragment`, `ParentItemAdapter`, `HomeChildItemAdapter`, `HomeFocusRestore` ve `LinearListLayout` üzerinden ilerliyor.
- Mevcut restore döngüsü kart/row kimliğiyle odak geri yüklüyor; ancak geri dönüşte asenkron retry ile RecyclerView’ın kayıtlı `wasFocused` durumu yarışabiliyor. Recycle edilen child adapter/holder durumları da kategori sınırlarını aşabiliyor.
- `library/.../TmdbProvider.kt` plugin API’sine ait commonMain sınıfıdır ve private gömülü anahtar kullanır. App fallback bu anahtarı kopyalamak veya public ABI’yi değiştirmek yerine `TmdbProvider`dan türeyip `useMetaLoadResponse = true` kullanmalıdır.
- TMDB fallback ana sayfadaki her karta ağ çağrısı yapmayacak; yalnızca detay yüklenirken güvenli eşleşme ile başlık/açıklama tamamlayacaktır.

## Tasks

- [x] **T01 — TV odak durumunu tek sahipli ve kategoriye bağlı hâle getir**
  - `HomeScrollViewHolderState` yeniden bağlanırken geçici `wasFocused` değerini sıfırla; restore tamamlanınca eski true durumlarını temizle ve recycle edilmiş adapter durumunun başka kategoriye taşınmasını engelle.
  - Files/symbols: `HomeChildItemAdapter.kt`, `HomeParentItemAdapter.kt`, `BaseAdapter` state-key kullanımı
  - Invariants: Odak kaydı row adı + `apiName/url/name` kart anahtarıyla ayrışmalı; provider içeriği veya kart sırası değişse de başka row odak almamalı.

- [x] **T02 — Detaydan dönüş restore yarışını kaldır**
  - Note: Aynı pending hedef commit/onResume sonrası tekrar geldiğinde restore zinciri artık yeniden schedule edilir; unattached RecyclerView attach olunca generation kontrollü devam eder. Explicit restore tamamlandığında hem adapter cache’indeki hem de ekrandaki holder’lardaki legacy `wasFocused` bayrakları temizlenir; ilk sağ/sol hareketin eski karta geri odaklanması engellenir.
  - Çoklu `post`/sabit retry yerine parent ve child RecyclerView commit/layout tamamlanmasını izleyen generation-cancellable tek restore zinciri kur; hedef kart gerçekten focus aldıktan sonra pending state’i kapat.
  - Restore boyunca RecyclerView’ın otomatik child restore’unu ve rastgele descendant focus aramasını bloke et; hedef yoksa aynı kategorinin ilk kartına bir kez fallback yap.
  - Files/symbols: `HomeFragment.restorePendingHomeFocus`, `ParentItemAdapter.restoreFocus/attemptFocusRestore`, `HomeFocusRestorePlanner`

- [x] **T03 — Yukarı/aşağı gezinmeyi kategori anahtarıyla deterministik yap**
  - Note: Önceki hatalı RecyclerView key listener kaldırıldı; D-pad listener doğrudan focus alan kart view’larına bağlandı ve row holder/category key callback’ine yönlendirildi.
  - Geometrik Android `focusSearch` sonucuna güvenmek yerine mevcut category key’den bir önceki/sonraki dolu row’u çöz, parent row’u görünür yap ve beklenen davranış olarak ilk karta focus ver.
  - Header/ilk kategori ve son kategori sınırlarında mevcut nav-rail/FOCUS_SELF davranışını koru; pagination/list commit sırasında kategori indeksini key’den yeniden çöz.
  - Files/symbols: `HomeParentItemAdapter.kt`, `HomeChildItemAdapter.kt`, gerekirse home’a özel `LinearListLayout` callback’i

- [x] **T04 — Basılı D-pad kaydırma akıcılığını iyileştir**
  - TV home listelerinde üst üste biriken `smoothScrollBy` animasyonlarını coalesce et; devam eden scroll sırasında eski animasyonu durdurup yeni focus hizalamasını tek harekete indir, global player/search davranışını değiştirmemek için home’a özel seçenek kullan.
  - TV’de gereksiz RecyclerView change animasyonlarını kapat, aynı listeyi tekrar submit etmeme optimizasyonunu koru ve ölçülü parent/child cache-prefetch ayarlarını mevcut shared pool ile birlikte uygula.
  - Files/symbols: `LinearListLayout.requestChildRectangleOnScreen`, `HomeFragment.onBindingCreated`, `ParentItemAdapter.onCreateContent`

- [x] **T05 — “İzlemeye devam et” kartına sağlayıcı adını ekle**
  - Note: Resume kart başlığı, global poster-title ayarı kapalı olsa bile sağlayıcı bilgisinin gerçekten görülebilmesi için görünür tutulur.
  - Yalnızca `ResumeWatchingResult` sunumunda başlığı `Lost - (StreamPlay)` biçiminde formatla; saklanan gerçek adı, URL’yi, ID’yi ve oynatma davranışını değiştirme.
  - Files/symbols: `SearchResultBuilder.bind`, `DataStoreHelper.ResumeWatchingResult`, `app/src/main/res/values/strings.xml`

- [x] **T06 — TMDB fallback ayarını ekle**
  - Tek seçenek gerektiği için ayrı API-key ekranı oluşturma; Providers ayarlarına entegrasyonu açıp kapatan bir `SwitchPreference` ve TMDB attribution açıklaması ekle.
  - Gömülü anahtar kullanıcıya gösterilmez, kopyalanmaz veya tercihlere kaydedilmez; entegrasyon varsayılan olarak kapalı olur.
  - Files/symbols: `SettingsProviders`, `settings_providers.xml`, `donottranslate-strings.xml`, `strings.xml`

- [x] **T07 — Mevcut `TmdbProvider` üzerinden app metadata fallback’i oluştur**
  - Note: Önceki ASCII-only normalizasyon non-Latin sorguyu boşaltıyor ve İngilizce TMDB adını yerel adla exact karşılaştırarak tüm adayları eliyordu. Unicode normalizasyon, tür/yıl filtresi, TMDB ID önceliği ve varsa IMDb doğrulamasıyla düzeltildi.
  - App katmanında `TmdbProvider`dan türeyen, `useMetaLoadResponse = true` kullanan dar kapsamlı bir adapter/service oluştur; inherited `search/load` çağrıları mevcut private gömülü anahtarı kullansın.
  - Önce `LoadResponse.getTMDbId()` ile doğrudan TMDB URL’si kur; ID yoksa inherited search sonucunu exact-normalized title/original-title, medya türü ve mevcutsa yıl toleransıyla doğrula. `en-US` detayından başlık/açıklama al; 401/404/429/ağ hatalarında provider verisine fail-open dön.
  - Files/symbols: yeni app metadata adapter/service, `library/.../TmdbProvider.kt` yalnızca mevcut public davranışıyla tüketilir; anahtar veya ABI değiştirilmez.

- [x] **T08 — TMDB fallback’i sonuç metadata akışına bağla**
  - Enrichment’i provider load + mevcut `applyMeta` sonrasında, UI/post/cache öncesinde çalıştır: ad Latin-dışı harf içeriyorsa yalnızca Latin İngilizce başlıkla değiştir; açıklama boşsa English overview ile doldur.
  - Provider URL/apiName, bölüm listesi, link çıkarma, poster/header, senkronizasyon ve dolu provider açıklamasını asla ezme; ayar kapalı veya eşleşme düşük güvenli ise hiçbir değişiklik yapma.
  - Files/symbols: `ResultViewModel2.load/applyMeta/postSuccessful`, `LoadResponse` mutation helpers

- [x] **T09 — Kaynakları ve hata durumlarını tamamla**
  - Note: Detay yükleme job’u yeni yüklemede, `clear()` ve ViewModel kapanışında iptal edilerek gecikmiş TMDB/provider sonucunun yeni ekrana yazması engellendi.
  - Yeni kullanıcı metinlerini yalnızca ana `values/strings.xml` ve non-translatable key dosyasına ekle; Weblate-managed çevirileri elle değiştirme.
  - Provider değişimi, hızlı geri dönüş, art arda key-repeat ve iptal edilen TMDB isteklerinde stale callback’in yeni ekrana/odağa yazmasını generation/job cancellation ile engelle; gömülü anahtarı loglama veya UI’da gösterme.

## Validation

- [x] **V01 — Etkilenen Kotlin dosyalarında Serena/LSP diagnostics kontrolü yap.**
  - Note: Yeni değişikliklere ait hata bulunmadı; mevcut `PackageDirectoryMismatch` ve platform type inspection uyarıları görüldü.
- [x] **V02 — `./gradlew assemblePrereleaseDebug` ile compilation validation çalıştır.**
  - Note: `ANDROID_HOME=$HOME/Library/Android/sdk` ile çalıştırıldı; `BUILD SUCCESSFUL`.
- [ ] **V03 — Ayrı emulator/device izni verilirse TV’de detay aç-kapat-sonra sağ, 1→2→3→4 kategori inişi, basılı dört yön ve TMDB açık/kapalı/offline senaryolarını manuel doğrula; aksi hâlde runtime doğrulamasının yapılmadığını raporla.**
  - Status: PARTIAL — Kullanıcı gerçek kullanımda yukarı/aşağı kategori hareketinin kabul edilebilir olduğunu ve TMDB fallback’in çalıştığını doğruladı; açık/kapalı/offline matrisinin tamamı ayrıca doğrulanmadı.
- [x] **V04 — TMDB kapalıyken, servis hatasında veya eşleşme bulunamazken provider sonucunun değişmeden kaldığını; “İzlemeye devam et” tıklamasının aynı içeriği açtığını kontrol et.**
  - Note: Kod incelemesinde fallback kapalıyken aynı response döner; hata/eşleşmeme fail-open davranır ve yalnızca ad/açıklama değiştirir. Resume tıklama verisi korunur.

## Implementation Notes
- TMDB resmi dokümantasyonu search→details akışını, `language` kullanımını ve attribution zorunluluğunu destekliyor. İngilizce fallback mevcut `TmdbProvider`ın `en-US` meta load cevabı üzerinden uygulama tarafında yapılacak; tüm homepage kartlarını zenginleştirmek performans ve yanlış eşleşme riski nedeniyle kapsam dışıdır. Gömülü anahtar çalışmaz hâle gelirse özellik provider verisini bozmadan devre dışı kalmalıdır.
