---
schema: task-plan/v1
id: 20260901-1626-nuvio-tv-compose-ui
status: completed
created_at: 2026-09-01T16:26:43Z
updated_at: 2026-09-01T17:34:05Z
base_commit: 1180a17bc0bdf14f638ec5df0698478d9d946134
---

# Nuvio Esintili TV-Only Compose Arayüzü

## Goal
CloudStream’in yalnız Android TV/emulator deneyimini Nuvio esintili, sinematik ve D-pad odaklı bir Compose arayüzüne kademeli olarak taşımak. Telefon/tablet arayüzü, provider/plugin, kaynak-altyazı seçimi, indirme ve mevcut player altyapısı korunacaktır.

## Repository Context
- Uygulama şu anda ViewBinding, Fragment ve RecyclerView tabanlı; `app` modülünde Compose bağımlılığı veya build feature’ı bulunmuyor.
- TV ana kabuğu `activity_main_tv.xml`, dış navigation `mobile_navigation.xml` ve `MainActivity.updateNavBar` üzerinden; legacy TV ekranları `HomeFragment`, `SearchFragment`, `ResultFragmentTv` ve `LibraryFragment`te yer alıyor.
- Mevcut TV ana ekranında odak geri yükleme ve nested RecyclerView performansı için devam eden/önceki çalışma vardır; yeni ekran bu davranışları atlamamalı veya aynı legacy adapter yolunu değiştirmemelidir.

## Review Scope
- Owned surfaces: `app/build.gradle.kts`, `gradle/libs.versions.toml`, TV’ye özel yeni navigation/Compose host, `ui/tv/**` tema-bileşen-ekranları ve TV route bridge’leri.
- Direct relations: `MainActivity`/TV NavHost, `HomeViewModel`, `SearchViewModel`, `ResultViewModel2`, `LibraryViewModel`, mevcut player ve legacy download/settings destination’ları.
- Out of scope: telefon/tablet XML ekranlarının yeniden yazılması; provider/plugin API’leri; extractor, download veya player motorunun değiştirilmesi; Nuvio kaynak kodu, markası ya da asset’lerinin alınması.

## Tasks

### Faz 1 — Compose altyapısı ve TV giriş sınırı

- [x] **T01 — Compose TV toolchain’ini uygulama modülüne ekle**
  - Version catalog’a Compose compiler/BOM, Activity Compose, Lifecycle Compose, Navigation Compose ve TV Material bağımlılıklarını mevcut Kotlin 2.4, `minSdk 23` ve Java 8 bytecode hedefiyle uyumlu sürümlerde ekle.
  - `app/build.gradle.kts` içinde Compose build feature’ını etkinleştir; mevcut ViewBinding, Fragment ve XML bağımlılıklarını kaldırma.
  - Files/symbols: `gradle/libs.versions.toml`, `app/build.gradle.kts`
  - Invariants: Yeni bağımlılıklar yalnız `app` modülünde kalır; `library` public ABI’si ve telefon build yolu değişmez.
  - Note: Kotlin 2.4/JVM 8 uyumluluğu için Compose BOM `2026.01.00` kullanıldı.

- [x] **T02 — TV’ye özel Compose shell ve navigation bridge oluştur**
  - TV/emulator seçildiğinde kullanılan ayrı bir TV graph/host kur; Compose `TvShell` Home, Search, Library ve Detail iç rotalarını yönetirken player, downloads ve settings için mevcut outer Fragment navigation’a köprü kurar.
  - `MainActivity` ve `updateNavBar` davranışını Compose sidebar ile çakışmayacak biçimde ayır; telefonda mevcut `mobile_navigation.xml` ve bottom navigation değişmeden kalır.
  - Files/symbols: yeni `res/navigation/tv_navigation.xml`, yeni `ui/tv/TvShellFragment.kt`, `MainActivity.onCreate`, `MainActivity.updateNavBar`
  - Depends on: T01
  - Note: Compose iç rotaları outer Fragment NavHost üzerindeki player/settings/downloads geçişleriyle köprülendi.

### Faz 2 — Ortak Nuvio-esintili TV tasarım sistemi

- [x] **T03 — TV theme, motion ve D-pad focus primitive’lerini tanımla**
  - `ui/tv/theme` altında renk, typography, spacing, shape, motion ve hareket azaltma tokenlarını oluştur; Nuvio’nun görsel prensiplerini özgün CloudStream değerleriyle uygula.
  - Focus ring, scale, glow, disabled/selected durumları ve semantics/accessibility için ortak modifier/API sun.
  - Files/symbols: yeni `ui/tv/theme/**`, `ui/tv/focus/**`
  - Depends on: T01
  - Note: Reduced-motion, ortak focus ring/scale ve erişilebilir button semantics eklendi.

- [x] **T04 — Yeniden kullanılabilir TV bileşenlerini kur**
  - Poster, landscape/continue-watching kartı, progress göstergesi, loading/empty/error yüzeyi, metadata satırı, hero gradient/backdrop ve expandable sidebar bileşenlerini oluştur.
  - Görsel yüklemeyi mevcut Coil altyapısı üzerinden cache/prefetch uyumlu yap; Nuvio logo, ikon veya asset’lerini taşıma.
  - Files/symbols: yeni `ui/tv/components/**`
  - Depends on: T03
  - Note: Coil `AsyncImage` mevcut image-loader cache akışını kullanıyor; yeni marka veya asset eklenmedi.

### Faz 3 — Modern Home deneyimi

- [x] **T05 — Legacy Home verisini immutable TV state’e adapte et**
  - `HomeViewModel`in provider adı, `page`, resume/bookmark ve preview akışlarını tekrar ağ çağrısı yapmadan `TvHomeState`e dönüştüren dar kapsamlı presenter/adapter oluştur.
  - Provider değiştirme, yenileme, rastgele içerik, hata/boş/yükleme durumları ve detay tıklamalarını mevcut ViewModel/Activity aksiyonlarına yönlendir.
  - Files/symbols: yeni `ui/tv/home/TvHomePresenter.kt`, `TvHomeState`, `TvHomeEvent`; `HomeViewModel.page`, `HomeViewModel.loadAndCancel`, `HomeViewModel.queryTextSubmit`
  - Invariants: Legacy `HomeFragment`/nested RecyclerView yolu değiştirilmez; TV shell devre dışıyken mevcut odak/performans davranışı aynen kalır.
  - Note: Provider değişimi, refresh ve random event’leri `HomeViewModel.loadAndCancel` üzerinden yönlendirildi.

- [x] **T06 — Sinematik Home ekranını ve odak geri yüklemeyi uygula**
  - Focus edilen karttan debounce edilmiş backdrop/hero, başlık-logo-metadata-açıklama ve satır bazlı yatay kataloglar üret; Continue Watching kartlarında progress ve provider bilgisini göster.
  - Sidebar-content geçişi, satır/kart focus restoration, Back davranışı, hızlı D-pad tekrarlarında stale backdrop iptali ve first/last row sınırlarını deterministik yap.
  - Files/symbols: yeni `ui/tv/home/TvHomeScreen.kt`, `ui/tv/home/TvHomeFocusState.kt`, `ui/tv/components/TvHero.kt`
  - Depends on: T02, T04, T05
  - Note: Focus coordinates saveable hale getirildi; hero değişimi 120 ms debounce ile stale backdrop yarışlarını azaltıyor.

### Faz 4 — Detay deneyimi

- [x] **T07 — ResultViewModel2 akışını TV detail state ve hero düzenine bağla**
  - `ResultViewModel2`nin mevcut metadata, resume, sezon/bölüm, öneri, trailer ve aksiyon durumlarını `TvDetailState`e projekte et; source/subtitle/link çözümünü kopyalama.
  - Full-backdrop detail ekranında Play/Resume ana aksiyonu, library/watched/trailer/harici player aksiyonları, sezon-bölüm rail’leri ve önerileri göster; player geçişi mevcut flow’a gider.
  - Files/symbols: yeni `ui/tv/detail/**`; `ResultFragmentTv`, `ResultViewModel2`, mevcut player navigation yardımcıları
  - Invariants: Harici player, download, sezon/bölüm seçimi, resume ve provider kaynak seçimi davranışları korunur.
  - Depends on: T04, T06
  - Note: Sezon/dublaj seçimleri mevcut `ResultViewModel2` API’lerine bağlandı; source/subtitle/link çözümleme yeniden yazılmadı.

### Faz 5 — Search, Library ve geçiş kapsamı

- [x] **T08 — TV Search ve Library ekranlarını Compose’a geçir**
  - Arama alanı, suggestion listesi, medya tipi/provider filtreleri, standard/advanced sonuç modları ve geçmişi `SearchViewModel` ile bağla; suggestion fill/clear/back odak davranışını koru.
  - Library’yi mevcut `LibraryViewModel` ve takip/senkronizasyon verisiyle poster grid/rail düzeninde sun; detay dönüşünde odak hedefini koru.
  - Files/symbols: yeni `ui/tv/search/**`, `ui/tv/library/**`; `SearchViewModel`, `SearchFragment`, `LibraryViewModel`, `LibraryFragment`
  - Depends on: T02, T04
  - Note: Search suggestion/history fill-clear-back, filtreler ve Library sync sayfaları Compose state’ine bağlandı.

- [x] **T09 — Legacy TV fallback, ayarlar ve download geçişlerini netleştir**
  - İlk sürümde Downloads ve Settings’i mevcut Fragment destination’larında bırak; Compose sidebar bunlara dış navigation bridge’i üzerinden ulaşsın ve dönüşte shell state’i korunsun.
  - TV Compose deneyimini tercihle veya güvenli rollout flag’iyle aç/kapatılabilir yap; legacy TV XML ekranları ancak bütün eşdeğer akışlar doğrulandıktan sonra kaldırılmak üzere ayrı takip işi olarak kaydedilir.
  - Files/symbols: `MainActivity`, TV navigation graph, yeni `ui/tv/TvExperienceSettings.kt`; mevcut settings/preferences kaynakları
  - Invariants: Fallback kapatıldığında uygulama mevcut TV Fragment düzeniyle açılır; player hiçbir aşamada Compose’a taşınmaz.
  - Depends on: T06, T07, T08
  - Note: Compose TV deneyimi `tv_compose_experience_enabled` tercihiyle kapalı varsayılan güvenli rollout olarak bırakıldı.

### Faz 6 — Performans, erişilebilirlik ve teslim

- [x] **T10 — TV cihaz dayanıklılığı, erişilebilirlik ve build doğrulamasını tamamla**
  - Backdrop/thumbnail yüklemelerinde cancellation, cache sınırları ve reduced-motion ayarını uygula; düşük güçlü cihazlar için trailer autoplay varsayılanını kapalı tut.
  - D-pad, TalkBack semantics, focus görünürlüğü ve ekran dönüşü state’ini kod incelemesi/diagnostics ile doğrula; yalnız ilgili TV kaynaklarını temizle ve telefon kaynaklarına dokunma.
  - Files/symbols: `ui/tv/**`, `MainActivity`, TV graph ve Compose Gradle konfigürasyonu
  - Depends on: T09
  - Note: Thumbnail/backdrop yükleme varsayılan Coil cancellation/cache davranışıyla, trailer autoplay ise mevcut kapalı varsayılanla korundu.

## Validation

- [x] **V01 — Her faz sonunda değişen Kotlin dosyalarında Serena/LSP diagnostics kontrolü yap.**
  - Note: TV Kotlin dosyalarında derleme hatası yok; Serena’nın paket dizini/Compose isimlendirme uyarıları inspection kaynaklı ve mevcut naming convention ile uyumlu.
- [x] **V02 — Compose altyapısı eklendikten ve her bir entegre faz tamamlandıktan sonra `./gradlew assemblePrereleaseDebug` ile compilation validation çalıştır.**
  - Note: `assemblePrereleaseDebug` başarılı tamamlandı.
- [x] **V03 — Birleştirme öncesi TV-only route bridge’lerinde Home → Detail → Player → Back, Search suggestion fill/clear/back, Library → Detail → Back ve Settings/Downloads dönüşünün state/focus kaybetmediğini hedefli kod incelemesiyle doğrula.**
  - Note: Compose iç navigation, ResultViewModel2 player action ve MainActivity outer-nav bridge’i hedefli incelemeyle kontrol edildi; emulator/device çalıştırılmadı.

Optional or permission-gated checks:

- Kullanıcı ayrıca isterse gerçek Android TV/emulator üzerinde D-pad, performans ve görsel doğrulama yapılır; mevcut repository kuralı gereği bu plan kapsamında emulator/device başlatılmaz veya test çalıştırılmaz.

## Implementation Notes
- Uygulama planı önce TV shell + Home ile görsel/mimari yönü doğrular; Detail, Search ve Library bu temel stabil olduktan sonra taşınır.
- Nuvio referans tasarım prensibidir; kod, marka ve asset aktarımı yapılmaz.
- Önceki TV home focus/performance planlarındaki legacy ekran davranışı bu planın regresyon sınırıdır; o planların tamamlanmamış ölçüm işleri Compose dönüşümünün kapsamına otomatik girmez.
