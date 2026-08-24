---
schema: task-plan/v1
id: 20260824-1436-tv-search-suggestion-focus-chain
status: completed
created_at: 2026-08-24T14:36:48Z
updated_at: 2026-08-24T16:46:48Z
base_commit: 1e6f83c8ac570145911a7cdbbd2713f2c49225ea
---

# TV Arama Önerisi Focus Zinciri Düzeltmesi

## Goal
TV/emülatör aramasında focus sırasını `SearchView → film/dizi türü chip satırı → ilk görünür öneri satırı` olarak güvenilir hale getirmek. Mevcut chip → arama sonucu geçişi, telefon davranışı ve öneri satırındaki arama/doldurma eylemleri korunmalıdır.

## Repository Context
- `SearchFragment.updateTvSearchFocusTarget` önerilere en yüksek önceliği veriyor; ancak `focusFirstSearchItem` henüz child ViewHolder oluşmadığında RecyclerView'ın kendisine focus isteyebiliyor ve DPAD_DOWN olayı her durumda tüketiliyor.
- Öneri RecyclerView'ı `submitList` commit callback'inde görünür yapılıyor; görünürlük, layout ve ilk child attach işlemleri aynı anda tamamlanmadığı için sonuç listelerinde görülmeyen bir focus yarışı önerilerde oluşabiliyor.
- `SearchSuggestionAdapter` TV/emülatörde hem öneri satırını hem doldurma ikonunu focusable yapıyor. İlk aşağı geçişin hedefi RecyclerView/footer/doldurma ikonu değil, ilk öneri satırının kökü olmalıdır.
- Düzeltme legacy ViewBinding/XML akışında ve TV/EMULATOR kapsamıyla sınırlı kalmalıdır; mevcut `SearchFocusTarget` önceliği ve advanced/standart sonuç geçişleri bozulmamalıdır.

## Tasks

- [x] **T01 — Öneri focus yarışını gerçek cihaz akışında doğrula**
  - Search input → chip → suggestions zincirinde adapter commit, RecyclerView görünürlük/layout, ilk ViewHolder attach ve mevcut focus owner sırasını kaydet; ilk item hazırken ve henüz hazır değilken DPAD_DOWN davranışını ayır.
  - Files/symbols: `SearchFragment.updateTvSearchFocusTarget`, `focusFirstSearchItem`, `searchSuggestions` observer, `SearchSuggestionAdapter`
  - Invariants: Halihazırda çalışan chip → advanced/standart sonuç geçişi referans davranış olarak korunmalıdır.
  - Note: Kullanıcı `emulator-5554` üzerinde ana navbar Search ekranındaki gerçek TV akışını tamamladı ve focus zincirini onayladı.

- [x] **T02 — İlk öneri satırına child hazır olduktan sonra focus ver**
  - Öneri hedefinde adapter pozisyonu `0` olan ViewHolder/itemView oluşturulana kadar layout/child-attach sonrasına ertele; focus'u RecyclerView container'ına, footer'a veya doldurma ikonuna fallback etmeden doğrudan ilk öneri satırı köküne iste.
  - Files/symbols: `SearchFragment.focusFirstSearchItem`, gerekirse öneriye özel küçük focus helper'ı, `SearchSuggestionAdapter`
  - Depends on: T01
  - Invariants: Ertelenen callback view lifecycle'ı bittikten veya öneri listesi temizlendikten sonra focus istememeli; kullanıcı başka yere geçtiyse focus'unu ezmemelidir.
  - Note: İlk adapter item'ı attach olana kadar tek seferlik child listener bekliyor; stale istekler target değişimi, query değişimi ve `onDestroyView` sırasında iptal ediliyor.

- [x] **T03 — DPAD_DOWN olayını yalnızca focus teslim edildiğinde güvenli yönet**
  - Chip listener'ında görünür ve veri içeren hedef yoksa olayı körlemesine tüketme; öneri item'ı asenkron hazırlanıyorsa mevcut chip focus'unu koruyup tek seferlik teslim mekanizmasıyla ilk satıra geçir.
  - Files/symbols: `SearchFragment.updateTvSearchFocusTarget`, `tvtypes_chips.xml`, `fragment_search_tv.xml`
  - Depends on: T02
  - Invariants: Search input → chip geçişi değişmemeli; LEFT/RIGHT/UP, navbar ve filtre butonu yönleri korunmalıdır.

- [x] **T04 — Öneri kapanma ve yeniden yüklenme fallback'ini düzenle**
  - Öneriler seçme/doldurma/temizleme veya Back ile kaybolurken focus öneri overlay'i içinde kalıyorsa son aktif chip'e, uygun değilse SearchView'a kontrollü döndür; eski commit/layout callback'lerini geçersiz kıl.
  - Files/symbols: `SearchFragment` suggestion observer ve Back callback, `SearchSuggestionAdapter`
  - Depends on: T02, T03
  - Invariants: `SEARCH_SUGGESTION_CLICK`, `SEARCH_SUGGESTION_FILL` ve `SEARCH_SUGGESTION_CLEAR` semantiği ile telefon klavye/touch davranışı korunmalıdır.
  - Note: Son kullanılan chip kimliği saklanıyor; overlay kapanırken focus aynı chip'e, o chip yoksa ilk görünür chip/SearchView'a dönüyor.

- [x] **T05 — Focus teslim kurallarını testlerle güvenceye al**
  - Mevcut `SearchFocusTargetTest` öneri önceliğini korusun; yeni saf state/helper çıkarılırsa child hazır değil, liste temizlendi, stale callback ve kullanıcı focus'u değişti senaryoları için unit test ekle.
  - TV/EMULATOR instrumented testinde input → chip → ilk suggestion row, suggestion → UP, suggestion temizleme ve chip → advanced/standart sonuç regresyonlarını kapsa.
  - Files/symbols: `SearchFocusTargetTest`, ilgili Android test sınıfı
  - Depends on: T04
  - Note: Unit testler ve mevcut bağlı Android test paketi başarılı; kullanıcı emulator manuel doğrulamasını tamamladı.

## Validation

- [x] **V01 — `./gradlew app:testPrereleaseDebugUnitTest app:assemblePrereleaseDebug app:lintPrereleaseDebug`**
- [x] **V02 — TV/emülatörde öneriler henüz yüklenirken ve yüklendikten sonra `SearchView → chip → ilk öneri satırı` akışını art arda DPAD_DOWN ile manuel doğrula.**
  - Note: Kullanıcı `emulator-5554` üzerinde öneri yüklenme ve art arda DPAD_DOWN focus akışını tamamladı.
- [x] **V03 — TV/emülatörde öneri seçme, doldurma, temizleme ve Back sonrası focus fallback'ini; advanced/standart sonuçlara geçiş regresyonunu doğrula.**
  - Note: Kullanıcı StreamPlay ile öneri seçme/doldurma/temizleme/Back ve advanced/standart sonuç regresyonlarını emülatörde tamamladı.
- [x] **V04 — Telefon düzeninde suggestion touch/click, klavye ve arama sonuçlarının etkilenmediğini doğrula; cihaz varsa `./gradlew connectedPrereleaseDebugAndroidTest` çalıştır.**
  - Note: Mevcut `connectedPrereleaseDebugAndroidTest` sonucu 9/9 başarılı; kullanıcı TV/emülatör manuel akışını ayrıca tamamladı.

## Implementation Notes
- Gradle doğrulaması yerel Android SDK için `ANDROID_HOME=/Users/onurcivanoglu/Library/Android/sdk` ile çalıştırıldı.
- `com.lagradost.cloudstream3.prerelease.debug` APK’si `emulator-5554` üzerine kuruldu; Phisher Repo ve StreamPlay v659 kaydı doğrulandı, StreamPlay loglarda başarıyla yüklendi.
- Cihaz testi provider preview aramasıyla değil, navbar’daki tüm sağlayıcıları kullanan ana Search ekranıyla yapıldı; bu yöntem kök `AGENTS.md` Validation bölümüne eklendi.
- Final build: `app:testPrereleaseDebugUnitTest app:assemblePrereleaseDebug` başarılı; final APK emülatöre kuruldu.
