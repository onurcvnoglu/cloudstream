---
schema: task-plan/v1
id: 20260824-0004-episode-source-preference
status: partial
created_at: 2026-08-24T00:04:31+03:00
updated_at: 2026-08-24T01:08:00+03:00
base_commit: 2e95035f610c27a042acae7cb5a2c260ab753617
---

# Bölümler Arasında Kaynak ve Altyazı Tercihini Koruma

## Goal
Kullanıcının oynatıcı diyaloğunda açıkça seçtiği kaynak ve altyazı tercihini dizi oturumu boyunca hatırla; geçilen bölümde aynı seçenekler varsa varsayılan olarak bunları kullan. Seçilen kaynakla ilişkilendirilmiş ve otomatik dil ayarıyla uyumlu bir altyazı varsa onu kaynakla birlikte otomatik seç; eşleşme bulunamazsa mevcut kaynak ve altyazı seçim kurallarına geri dön.

## Repository Context
- `GeneratorPlayer.startPlayer()` bugün `VideoState.sortLinks(currentQualityProfile)` sonucundaki ilk `shouldUseLink` bağlantısını açıyor; `releasePlayer()` bölüm yüklenmeden önce hem `currentSelectedLink` hem `currentSelectedSubtitles` değerlerini siliyor.
- Bağlantı ve altyazılar `PlayerGeneratorViewModel.loadLinks()` içinde asenkron ve parça parça geliyor. Erken otomatik başlatma, tercih edilen kaynak veya onun altyazısı daha sonra gelirse yarış durumu oluşturabilir.
- Çevrimiçi kaynak kimliği `ExtractorLink.source`; görünen `name` kalite/varyant içerebilir. `getAutoSelectSubtitle()` ise bugün kaynak ilişkisini dikkate almadan sıralı altyazılardaki ilk dil eşleşmesini seçiyor.
- `SubtitleData`/`SubtitleFile` şu anda kaynak ilişkisi taşımıyor; `nameSuffix` yalnızca eş adlı altyazılara callback geliş sırasına göre verilen benzersizlik sayacı olduğundan `Türkçe > 1/2/3` değerleri kaynak eşleştirmesi için güvenilir değil.
- Bölüm geçişleri ileri, geri ve bölüm listesinden doğrudan seçim yollarını kapsıyor. İnceleme sırasında `PlayerGeneratorViewModel.loadLinksPrev()` içinde `episodeIndex += 1` kullanıldığı ve önceki bölüm yönüyle çeliştiği doğrulandı.

## Tasks

- [x] **T01 — Kaynak tercih ve seçim politikasını ayrıştır**
  - Boş/null tercihleri yok sayan, `ExtractorLink.source` ile tam kaynak eşleşmesi yapan ve aynı kaynağın birden fazla bağlantısı varsa mevcut sıralamadaki en uygun eşleşmeyi seçen test edilebilir bir yardımcı/politika oluştur.
  - Files/symbols: `app/src/main/java/com/lagradost/cloudstream3/ui/player/GeneratorPlayer.kt`, gerekirse aynı pakette küçük bir yardımcı dosya
  - Invariants: Tercih eşleşmezse sonuç mevcut `sortLinks` + ilk kullanılabilir bağlantı davranışıyla aynı kalmalı; yerel `ExtractorUri` akışı bozulmamalı.
  - Note: `PlayerSelection.kt` içinde null/blank tercihi yok sayan ve sıralı kullanılabilir bağlantılarda tam `ExtractorLink.source` eşleşmesini seçen yardımcı eklendi; `startPlayer()` ve asenkron gözlem akışına bağlandı.

- [x] **T02 — Açık kullanıcı kaynak ve altyazı seçimlerini oturum tercihi olarak kaydet**
  - `showMirrorsDialogue()` Apply akışında kullanıcının seçtiği kaynak kimliğini ve altyazının URL'den bağımsız kararlı kimliğini (dil etiketi, ad/origin ve varsa ilişkili kaynak) sakla.
  - Otomatik ilk seçim, kalite varyantı veya hata failover'ı kullanıcı tercihini değiştirmesin; daha sonraki açık seçim önceki tercihin yerini alsın.
  - Files/symbols: `GeneratorPlayer.showMirrorsDialogue`, `setSubtitles`, `loadLink`

- [x] **T03 — Kaynak ve altyazı tercihini bütün bölüm geçiş yollarında koru**
  - İleri, geri ve bölüm listesinden doğrudan seçim öncesinde oturum tercihlerinin `releasePlayer()` tarafından kaybedilmemesini sağla; yeni bölümde önceki altyazı kimliği bulunursa onu seç.
  - Önceki altyazı yeni bölümde yoksa mevcut otomatik dil, indirilmiş altyazı ve altyazısız davranışına geri dön; normal oynatıcı kapatma/recreation davranışını gereksiz yere değiştirme.
  - Files/symbols: `GeneratorPlayer.nextEpisode`, `prevEpisode`, `showEpisodesOverlay`, `releasePlayer`
  - Depends on: T02

- [x] **T04 — Asenkron bağlantı gelişinde tercih edilen kaynağı öncele**
  - `startPlayer()` ve `currentLinks`/`loadingLinks` gözlem akışını, tercih edilen kaynak gelirse hemen onu; yükleme terminal duruma ulaşır veya kullanıcı “yüklemeyi geç” derse kaynak bulunamadığında mevcut fallback'i başlatacak şekilde düzenle.
  - Files/symbols: `GeneratorPlayer.startPlayer`, `GeneratorPlayer.onBindingCreated`, `PlayerGeneratorViewModel.loadLinks`
  - Depends on: T01, T03
  - Invariants: Tercih beklenirken erken gelen yüksek öncelikli başka kaynak otomatik başlamamalı; tercih yokken mevcut hızlı başlatma ve `canSkipLoading` davranışı değişmemeli.

- [x] **T05 — Altyazının geldiği kaynak bilgisini güvenilir biçimde taşı**
  - `nameSuffix` veya callback sırasından eşleştirme yapma; extractor çağrısı sırasında üretilen altyazıya ilişkili extractor/kaynak kimliğini ekleyip `SubtitleFile` → `SubtitleData` → cache/ViewModel hattında koru.
  - Kaynak bilgisi üretilemeyen doğrudan provider, online picker, indirilen ve embedded altyazılarda alanı null bırakıp mevcut kurallara düş; public `library` alanı gerekiyorsa constructor imzasını bozmadan ve `@Prerelease`/ABI kurallarına uyarak ekle.
  - Files/symbols: `library/.../MainAPI.SubtitleFile`, `ExtractorApi.loadExtractor`, `PlayerSubtitleHelper.SubtitleData`, `RepoLinkGenerator.generateLinks`
  - Note: Public `library` constructoruna dokunulmadı. Extractor akışında callback sırasına güvenmemek için `LinkGenerator` altyazıları linkler bilinene kadar tamponlayıp tek kaynak varsa `SubtitleData.source` ile ilişkilendiriyor; doğrudan provider/online/downloaded/embedded akışları `null` kalıyor.

- [x] **T06 — Kaynakla uyumlu otomatik dil seçimini uygula**
  - Seçilen kaynak için `preferredAutoSelectSubtitles` diline uyan ilişkili altyazıları öncele; örnekte kaynak2 seçildiğinde `Türkçe > 2/3` adayları arasından mevcut deterministik altyazı sırasındaki ilk uygun seçeneği kullan, kaynak1'e ait `Türkçe > 1` seçeneğini alma.
  - Öncelik sırasını “aynı bölümde açıkça seçilen altyazı → yeni bölümde bulunan önceki altyazı tercihi → seçili kaynak + ayarlanan dil → mevcut global otomatik seçim/download fallback” olarak uygula.
  - Files/symbols: `GeneratorPlayer.getAutoSelectSubtitle`, `autoSelectFromSettings`, `autoSelectSubtitles`, `showMirrorsDialogue`
  - Depends on: T03, T05

- [x] **T07 — Kaynak ve altyazı yarış durumlarını birlikte yönet**
  - Kaynak oynatılmaya başlandığında ilişkili tercih edilen dil altyazısı henüz gelmediyse terminal yükleme durumuna kadar bekle veya altyazı sonradan geldiğinde yalnızca hâlâ aynı kaynak aktifse güvenli biçimde seç/reload et.
  - Eski episode `instance` callback'lerinin yeni bölümün kaynak ya da altyazı tercihini değiştirmesini engelle; kullanıcı yüklemeyi geçerse mevcut fallback sınırını koru.
  - Files/symbols: `PlayerGeneratorViewModel.loadLinks`, `GeneratorPlayer.onBindingCreated`, `loadLink`
  - Depends on: T04, T06

- [x] **T08 — Geri bölüm indeks hatasını düzelt**
  - `hasPrev()` koşuluyla uyumlu olarak geri geçişte indeksi azalt; ilk/son bölüm sınırlarını ve doğrudan bölüm seçiminden sonraki yön davranışını testle sabitle.
  - Files/symbols: `PlayerGeneratorViewModel.loadLinksPrev`, `VideoGenerator.hasPrev`
  - Note: `loadLinksPrev()` önceki bölüme geçerken hatalı biçimde `episodeIndex += 1` kullanıyordu; sınır koşulu korunarak `previousEpisodeIndex()` üzerinden `-= 1` yapıldı ve sınır testi eklendi.

- [x] **T09 — Tercihli bağlantı hata fallback'ını güvenceye al**
  - Tercih edilen bağlantı sıralı listenin ortasında/sonunda seçildiğinde `getNextLink()`/`nextMirror()` akışının kullanılabilir diğer bağlantıları kaçırmadığını doğrula; gerekiyorsa mevcut bağlantıyı dışlayıp kalan uygun bağlantıları deterministik sırayla tarayacak şekilde düzelt.
  - Kaynak failover'ı gerçekleşirse eski kaynağa bağlı altyazıyı taşımayıp yeni kaynak için T06 seçim zincirini yeniden çalıştır.
  - Files/symbols: `GeneratorPlayer.getNextLink`, `hasNextMirror`, `nextMirror`, `playerError`
  - Depends on: T04, T06

- [ ] **T10 — Kaynak, altyazı ve bölüm geçiş regresyon testlerini ekle**
  - Kaynak/altyazı tercihi mevcut-yok, aynı kaynaktan çoklu kalite ve aynı dilde çoklu altyazı, karışık callback sırası, kullanıcı skip'i, yerel URI, embedded/downloaded altyazı ve kaynak hata failover senaryolarını kapsa.
  - Türkçe ayarıyla `kaynak1 + Türkçe > 1`, `kaynak2 + Türkçe > 2/3` örneğini; kaynak2 seçildiğinde yalnızca kaynak2 ile ilişkili uygun altyazının seçildiğini ve ilişki yoksa mevcut global fallback'in çalıştığını doğrula.
  - İleri/geri/doğrudan bölüm indeks geçişlerini ve özellikle geri geçiş sınırını ayrıca test et.
  - Files/symbols: `app/src/test/java/com/lagradost/cloudstream3/`, T01/T06 seçim yardımcıları
  - Depends on: T01, T06, T07, T08, T09
  - Status: PARTIAL — Seçili kaynak, kaynak ilişkili altyazı, URL'den bağımsız tercih ve geri indeks sınırı için birim testleri eklendi; skip/URI/embedded/downloaded/failover ve gerçek cihaz geçiş senaryoları henüz otomatikleştirilmedi.

## Validation

- [x] **V01 — `./gradlew testPrereleaseDebugUnitTest` ile ilgili app birim testlerini çalıştır.**
- [x] **V02 — Public/protected `library` bildirimi değişirse `./gradlew library:checkKotlinAbi` çalıştır; ABI dosyasını yalnızca değişiklik bilinçli ve onaylıysa güncelle.**
  - Note: `library/` altında public/protected bildirim değişikliği yapılmadığı için ABI kontrolü uygulanabilir değildi.
- [x] **V03 — `./gradlew assemblePrereleaseDebug lintPrereleaseDebug` ile Kotlin/Android derleme ve lint kontrolünü çalıştır.**
- [ ] **V04 — Telefon ve TV oynatıcılarında ileri, geri ve bölüm listesinden geçişi; kaynak/altyazı var-yok, Türkçe kaynak eşleşmesi ve kaynak hata fallback senaryolarıyla manuel doğrula.**
  - Status: BLOCKED — Çalışma ortamında `adb`/bağlı Android cihaz veya emulator bulunmadığı için telefon/TV manuel doğrulaması yapılamadı.

## Implementation Notes
- Kaynak–altyazı ilişkisi mevcut modelde bulunmadığından `nameSuffix` sırasına güvenilmemeli. Mümkün olan en dar, nullable ve geriye uyumlu metadata hattı tercih edilmeli; kaynak bilgisi olmayan altyazılar mevcut seçim kurallarını kullanmaya devam etmeli.
- Gerçek cihaz doğrulaması ve kapsamlı failover/skip regresyonları ayrı bir takip adımı olarak kaldı.
