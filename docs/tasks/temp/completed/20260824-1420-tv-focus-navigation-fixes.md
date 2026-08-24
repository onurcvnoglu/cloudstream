---
schema: task-plan/v1
id: 20260824-1420-tv-focus-navigation-fixes
status: partial
created_at: 2026-08-24T11:20:13Z
updated_at: 2026-08-24T11:48:37Z
base_commit: 8f4160d337e5d32f6ed2d8b207caa100e052caf1
---

# TV Arama ve Ana Sayfa Focus Düzeltmeleri

## Goal
TV/kumanda kullanımında arama filtrelerinden sonuçlara ve açık önerilere kesintisiz aşağı yönlü focus geçişi sağlamak; sağlayıcı ana sayfasında detaydan dönünce aynı kategori ve içerik kartına focus'u geri yüklemek. Değişiklikler telefon, emülatör, arama geçmişi, gelişmiş/standart arama ve ortak RecyclerView state akışlarını bozmamalıdır.

## Repository Context
- Arama görünürlüğü ve focus yönlendirmesi `SearchFragment.onBindingCreated` ile `fragment_search_tv.xml` içinde yönetiliyor; mevcut öneri observer'ı chip kökünün aşağı hedefini yalnızca öneri/geçmiş arasında değiştiriyor ve görünür sonuç RecyclerView'ını hesaba katmıyor.
- Öneriler `SearchSuggestionAdapter` ile gösteriliyor; satır kökü açıkça focusable değil ve asenkron suggestion güncellemesi sırasında RecyclerView görünürlüğü/focus hedefi yarışabiliyor.
- Ana sayfa kategori/kart state'i `ParentItemAdapter`, `HomeChildItemAdapter.HomeScrollViewHolderState` ve pozisyon anahtarlı `BaseAdapter` state mekanizmasına dayanıyor; aynı adapter ailesi arama provider satırlarında, bookmark/resume alanlarında ve telefon/TV düzenlerinde de kullanılıyor.
- Legacy ViewBinding/XML + MVVM akışı korunmalı; düzeltme TV/EMULATOR davranışına daraltılmalı ve ortak adapter davranışı değiştirilirse tüm tüketicileri review edilmelidir.

## Tasks

- [x] **T01 — Üç focus akışının mevcut state geçişlerini doğrula**
  - Search input → medya tipi chipleri → öneri/geçmiş/advanced provider sonuçları/standart grid sonuçları ile Home kategori → detay → geri dönüş akışlarında görünür view, adapter commit, focus owner ve lifecycle sırasını kaydet.
  - Files/symbols: `SearchFragment.onBindingCreated`, `fragment_search_tv.xml`, `SearchSuggestionAdapter`, `HomeFragment.onBindingCreated`, `ParentItemAdapter`, `HomeChildItemAdapter`
  - Invariants: Boş/gone RecyclerView focus hedefi olamaz; telefonun klavye ve touch davranışı değişmemelidir.

- [x] **T02 — Arama chip satırından aktif içeriğe deterministik aşağı geçiş ekle**
  - Chip satırındaki gerçek focuslanan çocuklardan DPAD_DOWN geldiğinde önceliği açık önerilere, aksi halde görünür ve veri içeren advanced `searchMasterRecycler`, standart `searchAutofitResults` veya boş sorguda geçmiş listesine ver; ilk focusable öğeye layout/adapter commit sonrasında geç.
  - Files/symbols: `SearchFragment.onBindingCreated`, `fragment_search_tv.xml`, `tvtypes_chips_scroll.xml`
  - Depends on: T01
  - Invariants: Sol navbar yalnızca açıkça sola gidildiğinde erişilmeli; up/right/left yönleri ve provider filtre butonu korunmalıdır.

- [x] **T03 — Asenkron öneri focus yarışını gider**
  - Öneri RecyclerView'ı yalnızca gerçek liste commit edildiğinde focus hedefi yap; SearchView veya chip satırından aşağı basıldığında ilk öneri satırına güvenilir şekilde focus ver ve öneri seç/doldur/temizle ile Back callback akışlarını koru.
  - Files/symbols: `SearchFragment.onBindingCreated`, `SearchSuggestionAdapter`, `search_suggestion_item.xml`, `fragment_search_tv.xml`
  - Depends on: T01
  - Invariants: Suggestion satırı ile doldurma ikonu arasındaki click semantiği korunmalı; boşaltılan öneriler focus'u görünmez overlay içinde bırakmamalıdır.

- [x] **T04 — Arama focus hedefi seçimini tek ve testlenebilir kurala indir**
  - Suggestions, query boşluğu, advanced-search ayarı, görünür adapter ve item sayısını kullanan hedef çözümünü tek helper/akışta merkezileştir; query değişimi, suggestion observer'ı ve sonuç observer'larının çelişkili `nextFocusDownId` yazmasını kaldır.
  - Files/symbols: `SearchFragment`, gerekirse `SearchViewModel` dışına taşmayan küçük bir focus-target helper
  - Depends on: T02, T03
  - Invariants: Arama/veri çağrıları ViewModel'de kalmalı; UI helper yalnızca focus/render state'i belirlemelidir.

- [x] **T05 — Detaydan dönüşte kategori ve kart kimliğini güvenli biçimde restore et**
  - Detay açılırken focuslanan kategori ve kartı pozisyona körü körüne bağlanmadan kaydet; geri dönüşte kategori listesi ve child adapter commit/layout tamamlandıktan sonra aynı kategori satırındaki aynı kartı restore et, öğe artık yoksa aynı kategoride kontrollü fallback uygula.
  - Files/symbols: `HomeChildItemAdapter.onBindContent`, `HomeScrollViewHolderState`, `ParentItemAdapter.onBindContent`, `ParentItemHolder`, `HomeFragment.onBindingCreated`
  - Depends on: T01
  - Invariants: Provider refresh/reorder durumunda yanlış üst kategoriye focus verilmemeli; restore tek seferlik olmalı ve kullanıcı yeni focus hareketi yaptıysa onu ezmemelidir.
  - Note: Parent ve child state anahtarları kategori/kart kimliğine göre sabitlendi; kaybolan kart için aynı satırın ilk kartına yalnızca focus boşsa fallback uygulanır.

- [x] **T06 — Ortak adapter ve navigation etkilerini review edip kapsamı daralt**
  - `BaseAdapter`ın pozisyon anahtarlı state saklamasını, parent/child adapter ID üretimini ve fragment detach/attach sırasını kontrol et; mümkünse düzeltmeyi Home provider akışına lokal tut, ortak mekanizma değişecekse Search provider rows, bookmark, resume ve popup/homepage list tüketicilerini uyumlu hale getir.
  - Files/symbols: `BaseAdapter.save/getState/setState`, `ParentItemAdapter`, `HomeParentItemAdapterPreview`, `HomeChildItemAdapter`, `SearchHelper.handleSearchClickCallback`, `HomeViewModel.click`
  - Depends on: T05
  - Invariants: Yatay scroll konumu, pagination/expand, kart click/long-click ve telefon davranışı korunmalıdır.

- [x] **T07 — Focus regresyon testlerini ekle**
  - Saf hedef çözümü/state eşlemesi çıkarılabiliyorsa unit test ekle; Android focus davranışı için TV/EMULATOR layout'unda Espresso veya mevcut test altyapısına uygun instrumented senaryoları kapsa.
  - Senaryolar: öneri gelmeden/gelirken/geldikten sonra DPAD_DOWN, öneri seçme-doldurma-temizleme, advanced ve standart sonuç listeleri, boş sonuç/geçmiş, farklı kategori ve yatay kart konumlarından detay→Back, provider refresh/reorder fallback.
  - Depends on: T04, T06
  - Note: Saf hedef çözümü için unit test eklendi; cihaz bağımlı senaryolar V02–V04 kapsamında manuel/instrumented doğrulama bekliyor.

## Validation

- [x] **V01 — `./gradlew app:assemblePrereleaseDebug app:lintPrereleaseDebug app:testPrereleaseDebugUnitTest`**
- [ ] **V02 — TV/emülatörde kumanda ile Search → chip → suggestions/results ve sonuç → detay → Back focus senaryolarını manuel doğrula.**
  - Status: PARTIAL — Bağlı TV/emülatör olmadığı için manuel kumanda doğrulaması yapılmadı.
- [ ] **V03 — Telefon düzeninde arama klavyesi, suggestion touch/click, history, advanced-search açık/kapalı ve navbar gezinmesinde regresyon olmadığını doğrula.**
  - Status: PARTIAL — Bağlı telefon/emülatör olmadığı için manuel doğrulama yapılmadı.
- [ ] **V04 — Uygun cihaz/emülatör varsa `./gradlew connectedPrereleaseDebugAndroidTest`; yoksa komutun neden atlandığını kaydet.**
  - Status: PARTIAL — `adb devices` bağlı cihaz/emülatör göstermedi; instrumented test çalıştırılmadı.

## Implementation Notes
- None.
