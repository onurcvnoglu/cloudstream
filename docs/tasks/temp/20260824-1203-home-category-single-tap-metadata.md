---
schema: task-plan/v1
id: 20260824-1203-home-category-single-tap-metadata
status: completed
created_at: 2026-08-24T12:03:10+03:00
updated_at: 2026-08-24T17:31:30+03:00
base_commit: 617f5d91731100b51f45a3386fba56f155ab79b0
---

# Ana sayfa kategori kartlarında tek dokunuşla bilgi penceresi

## Goal
Ana sayfadaki sağlayıcı kategori satırlarında film/dizi kartına tek dokunuş, mevcut basılı tutma davranışı olan bilgi/önizleme penceresini açsın; sonuç detayına doğrudan gitmesin. İzlemeye devam et kartları tek dokunuşta mevcut kaldığı yerden oynatma davranışını korusun.

## Repository Context
- `SearchResultBuilder.bind` normal kart tek dokunuşunda `SEARCH_ACTION_LOAD`, basılı tutmada `SEARCH_ACTION_SHOW_METADATA`; `ResumeWatchingResult` tek dokunuşunda `SEARCH_ACTION_PLAY_FILE` yayımlar.
- Ana sayfa `HomeParentItemAdapterPreview` → `ParentItemAdapter` → `HomeChildItemAdapter` zincirini kullanır; `HomeViewModel.click` bu olayı `SearchHelper.handleSearchClickCallback` üzerinden `MainActivity.loadPopup` akışına iletir.
- `ParentItemAdapter` ve `SearchAdapter`, arama/quick search gibi başka ekranlarda da kullanılır. Davranış sadece ana sayfa sağlayıcı kategorilerine opt-in olarak uygulanmalıdır.

## Tasks

- [x] **T01 — Kart bağlayıcısına yapılandırılabilir tek dokunuş eylemi ekle**
  - `SearchResultBuilder.bind` için varsayılanı `SEARCH_ACTION_LOAD` olan, çağıranın değiştirebildiği birincil eylem parametresi tanımla.
  - Normal kartlarda bu eylemi yayımla; basılı tutmayı her zaman `SEARCH_ACTION_SHOW_METADATA`, `ResumeWatchingResult` tek dokunuşunu her zaman `SEARCH_ACTION_PLAY_FILE` olarak bırak.
  - Files/symbols: `app/src/main/java/com/lagradost/cloudstream3/ui/search/SearchResultBuilder.kt`
  - Invariants: Mevcut çağıranlar parametre vermediğinde detay açma davranışı değişmemeli.

- [x] **T02 — Ana sayfa kategori satırlarına eylem ayarını aktar**
  - Note: Yeniden kullanılan `HomeChildItemAdapter` örneğinde de `primaryAction` güncelleniyor; mevcut çağrılar varsayılan yükleme davranışını koruyor.
  - `HomeChildItemAdapter` ve onu oluşturan `ParentItemAdapter` üzerinden T01’deki ayarı geçir; yeniden kullanılan child adapter’da da güncel ayarın korunduğundan emin ol.
  - Yeni ayarın varsayılanını yükleme/detay eylemi yaparak `SearchFragment` ve `QuickSearchFragment` çağıranlarını geriye dönük uyumlu tut.
  - Files/symbols: `ui/home/HomeChildItemAdapter.kt`, `ui/home/HomeParentItemAdapter.kt`
  - Depends on: `T01`

- [x] **T03 — Sağlayıcı ana sayfa kategorilerinde bilgi penceresini tek dokunuşa bağla**
  - Yalnızca `HomeParentItemAdapterPreview` ile oluşturulan sağlayıcı kategori satırları için birincil eylemi `SEARCH_ACTION_SHOW_METADATA` olarak seç.
  - Başlık içindeki yer imleri ve diğer özel listeleri bu ayara dahil etme; bunların mevcut davranışını koru.
  - Files/symbols: `ui/home/HomeParentItemAdapterPreview.kt`, `HomeParentItemAdapterPreview`
  - Depends on: `T02`

- [x] **T04 — Genişletilmiş ana sayfa kategori listesini aynı kurala dahil et**
  - Note: Yalnızca `HomeFragment` genişletilmiş listesi metadata eylemini seçiyor; Search/Quick Search/sonuç önerileri varsayılan yükleme eyleminde kaldı.
  - `SearchAdapter`a varsayılanı yükleme olan aynı yapılandırmayı geçir ve `HomeFragment`in “daha fazla/genişletilmiş kategori” listesinde bilgi eylemini seç.
  - Arama, quick search, öneriler ve sonuç ekranındaki diğer `SearchAdapter` kullanımlarını varsayılan davranışta bırak; ana sayfa bottom sheet’inin mevcut gizleme koşulu sadece yükleme/oynatma eylemlerinde çalışmaya devam etsin.
  - Files/symbols: `ui/search/SearchAdaptor.kt`, `ui/home/HomeFragment.kt`
  - Depends on: `T01`

- [x] **T05 — İzlemeye devam et istisnasını ve etki alanını doğrula**
  - Note: `ResumeItemAdapter` varsayılan ayarda bırakıldı; `HomeViewModel.click` ve `SearchHelper` akışları olay dönüşümü olmadan gözden geçirildi.
  - `ResumeItemAdapter`ın varsayılan yapılandırmayla kaldığını ve `SEARCH_ACTION_PLAY_FILE` olayını dönüştürmeden `HomeViewModel.click`e ilettiğini doğrula.
  - Basılı tutmadaki izlemeye devam et seçenek menüsünü ve `SEARCH_ACTION_SHOW_METADATA` yolunu değiştirme; sağlayıcı kategori dışındaki tek dokunuşların detay yolunda kaldığını kod gözden geçirmesiyle kontrol et.
  - Files/symbols: `ui/home/HomeChildItemAdapter.kt`, `ui/home/HomeParentItemAdapterPreview.kt`, `ui/home/HomeViewModel.kt`, `ui/search/SearchHelper.kt`
  - Depends on: `T01`, `T02`, `T03`, `T04`

## Validation

- [x] **V01 —** `./gradlew assemblePrereleaseDebug testPrereleaseDebugUnitTest` çalıştır.
  - Note: Yerel Android SDK için `ANDROID_HOME`/`ANDROID_SDK_ROOT` verilerek çalıştırıldı; derleme ve birim testleri başarılı.
- [x] **V02 —** Telefon ve TV/emülatöründe sağlayıcı ana sayfa satırı ile genişletilmiş kategori listesindeki normal kart tek dokunuşunun bilgi penceresini açtığını, basılı tutmanın aynı davranışı koruduğunu doğrula.
- [x] **V03 —** İzlemeye devam et kartında tek dokunuşun kaldığı yerden oynatmayı başlattığını; basılı tutmanın mevcut seçenek menüsünü koruduğunu doğrula.
- [x] **V04 —** Arama, quick search, kütüphane ve sonuç önerilerinde normal kart tek dokunuşunun sonuç detayına gitmeye devam ettiğini doğrula.

## Implementation Notes
- Yeni metin, kaynak veya public library API değişikliği gerekmiyor.
- Mevcut testler UI adapter etkileşimini kapsamıyor; bu listener davranışı için cihaz/emülatör doğrulaması zorunlu tutulmalıdır.
