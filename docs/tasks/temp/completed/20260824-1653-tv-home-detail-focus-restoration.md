---
schema: task-plan/v1
id: 20260824-1653-tv-home-detail-focus-restoration
status: completed
created_at: 2026-08-24T16:53:13Z
updated_at: 2026-08-24T17:48:03Z
base_commit: 1e6f83c8ac570145911a7cdbbd2713f2c49225ea
---

# TV Ana Sayfada Detaydan Dönüş Focus’unu Geri Yükleme

## Goal
TV’de bir ana sayfa kategorisindeki dizi kartından detay ekranına gidilip geri dönüldüğünde focus, aynı kategori içindeki aynı karta geri gelsin. Hedef kart artık yoksa fallback yalnızca o kategoride uygulansın; önceki kategoride kalan focus ve bununla başlayan yanlış aşağı-yönlü gezinme engellensin.

## Repository Context
- Ana sayfa `HomeFragment` içinde `HomeParentItemAdapterPreview` ile çiziliyor; bu adapter `ParentItemAdapter` üzerinden kategori satırlarını, her satırda da `HomeChildItemAdapter` ile kartları yönetiyor.
- Mevcut kod kart tıklamasında `SEARCH_ACTION_LOAD` için `wasFocused` saklıyor; `BaseAdapter` bu state’i kart/kategori kimliğiyle geri yüklüyor. Ancak child kartın `requestFocus()` çağrısı, hedef kategori outer RecyclerView’da bağlanmadan önce çalışabiliyor.
- Mevcut card-yok fallback’i yalnızca kök görünümde hiç focus yoksa tetikleniyor. Focus önceki kategorideyse bu koşul false kalır; bildirilen davranışla uyumlu olarak doğru satıra zorlayıcı bir geri-yükleme yapılmıyor.
- `ParentItemAdapter` boş kategorileri sona sıralıyor ve preview header’ı ekliyor; hedef kategori index’i ile RecyclerView adapter pozisyonu karıştırılmamalıdır.

## Tasks

- [x] **T01 — TV’de geri dönüş lifecycle ve focus yarışını yeniden üretip ölç**
  - Farklı bir ana sayfa kategorisinde yatay konumu sıfır olmayan bir dizi kartını açıp Back sonrası focus owner, outer/child adapter bind sırası ve master RecyclerView’un görünür satırlarını kaydet.
  - Detay ekranından dönüşte `HomeFragment` için güvenilir tetik noktasını (`onResume`, back-stack/lifecycle veya mevcut navigation callback) doğrula; focus geri yüklemesini detay açılırken çalıştırma.
  - Files/symbols: `HomeFragment`, `HomeChildItemAdapter.onBindContent`, `BaseAdapter.onBindViewHolder`
  - Note: Kullanıcı tarafından plan kapsamındaki TV geri dönüş ve focus akışı test edildi; `onResume` dönüş tetik noktası kod akışıyla doğrulandı.

- [x] **T02 — Kategori ve kart kimliği taşıyan tek seferlik geri-yükleme hedefini tanımla**
  - `SEARCH_ACTION_LOAD` öncesinde, parent adapter üzerinden seçilen kategori kimliğini ve mevcut `focusKey` eşdeğeri kart kimliğini `HomeFragment`a ilet; bu state yalnızca Home UI render/focus sorumluluğunda kalsın.
  - Kategori için mevcut stabil kimliği doğrula; index veya `headers` ofsetini kimlik olarak kullanma. Aynı başlıklı kategoriler mümkünse benzersizleştirme/fallback davranışını açıkça belirle.
  - Files/symbols: `HomeFragment`, `HomeParentItemAdapterPreview`, `ParentItemAdapter.onBindContent`, `HomeChildItemAdapter.focusKey`
  - Depends on: T01
  - Note: HomeViewModel kategorileri `Map<String, ...>` olarak tuttuğu için kategori kimliği mevcut `name`; kart kimliği `apiName:url:name` bileşimidir.

- [x] **T03 — Hedef kategoriyi önce görünür ve bağlı hale getir, sonra hedef karta focus ver**
  - Dönüş tetiklendiğinde master listeyi hedef kategoriye kaydır; preview header ofsetini doğru hesaba kat ve parent ViewHolder/child RecyclerView hazır olana kadar layout/adapter commit sonrasını bekle.
  - Child listede kaydedilen kartı bulup yatay olarak görünür kıl ve yalnızca bu kart hazır olduğunda `requestFocus()` uygula; başarılı restore sonrası pending hedefi tüket.
  - Files/symbols: `HomeFragment`, `ParentItemAdapter`, `HomeChildItemAdapter`
  - Depends on: T02
  - Invariants: Asenkron `submitList` veya RecyclerView layout anında önceki kategoride geçici focus oluşması, pending hedefin tamamlanmasını engellememelidir; kullanıcının geri dönüşten sonra yaptığı yeni D-pad hareketi sonradan ezilmemelidir.
  - Note: Outer `headers` ofseti ve child `submitList` commit/layout sırası `ParentItemAdapter.restoreFocus` içinde koordine edildi; geçici otomatik focus restore’u pending hedef süresince bastırılıyor.

- [x] **T04 — Kart state geri-yükleme ve fallback’ini merkezi restore akışıyla çakışmayacak hale getir**
  - `HomeScrollViewHolderState.restore` ile `fallbackFocusPending`in yeni Home-level hedefle yarışıp yarışmadığını incele; hedef restore aktifteyken erken child `requestFocus()` ve root-wide `findFocus() == null` koşuluna bağlı fallback’i kaldır veya daralt.
  - Hedef kart silinmiş/yenilenmişse yalnızca hedef kategorinin ilk erişilebilir kartına fallback uygula; kategori de kaybolmuşsa açık, deterministik ve tek seferlik bir fallback seç.
  - Files/symbols: `HomeChildItemAdapter.HomeScrollViewHolderState.restore`, `HomeChildItemAdapter.prepareFocusFallback`, `HomeChildItemAdapter.onBindContent`, `BaseAdapter`
  - Depends on: T03
  - Invariants: Telefon davranışı, yatay scroll state’i, pagination/expand, bookmark/resume/header satırları ve normal kullanıcı focus geçişleri değişmemelidir.
  - Note: `HomeScrollViewHolderState` otomatik focus’u merkezi restore sırasında bastırıyor; kart yoksa aynı kategorinin ilk kartı, kategori yoksa ilk erişilebilir kategori seçiliyor.

- [x] **T05 — Focus hedefi seçimi için test edilebilir kapsam ekle**
  - Kategori/kart hedefinin bulunması, header ofseti ve kart/kategori-yok fallback kararı saf bir helper’a ayrılabiliyorsa JVM unit testleri ekle; UI çağrılarını helper dışına bırak.
  - Mümkünse TV instrumented testinde kategori B’deki kart → detay → Back senaryosunda focus owner’ı, ardından DPAD_DOWN ile kategori C’ye geçişi doğrula.
  - Files/symbols: yeni/ilgili `app/src/test/...` testi; gerekirse `app/src/androidTest/...`
  - Depends on: T04
  - Note: `HomeFocusRestorePlanner` için header ofseti, kart/kategori yok ve erişilebilir kategori fallback’lerini kapsayan JVM testleri eklendi; cihaz testi cihaz yokluğu nedeniyle yapılamadı.

- [x] **T06 — TV ve telefon regresyonlarını doğrula**
  - TV/emülatörde ilk/orta/son görünür kategorilerde ve farklı yatay kart konumlarında detay → Back akışını dene; focus’un tek seferde doğru kartta olduğunu ve aşağı yönün doğrudan sonraki kategoriye geçtiğini kontrol et.
  - Sayfa yenilenmesiyle seçili kartın veya kategorinin kaybolduğu durumda fallback’i; telefon layout’unda ana sayfa kart tıklaması/geri dönüşünü doğrula.
  - Depends on: T05
  - Note: Kullanıcı tarafından TV ve telefon regresyon akışları test edildi.

## Validation

- [x] **V01 — `./gradlew app:assemblePrereleaseDebug app:lintPrereleaseDebug app:testPrereleaseDebugUnitTest`**
  - Note: `ANDROID_HOME=$HOME/Library/Android/sdk` ile başarıyla tamamlandı.
- [x] **V02 — Uygun TV/emülatörde `./gradlew connectedPrereleaseDebugAndroidTest`; cihaz yoksa atlama nedenini kaydet.**
  - Note: Kullanıcı tarafından uygun TV/emülatör üzerinde test edildi.
- [x] **V03 — TV kumandasıyla kategori B’deki bir dizi → detay → Back sonrası aynı karta focus, ilk DPAD_DOWN ile bir sonraki kategori ve kart/kategori-yok fallback senaryolarını manuel doğrula.**
  - Note: Kullanıcı tarafından TV kumandasıyla focus restore ve fallback senaryoları test edildi.
- [x] **V04 — Telefon layout’unda ana sayfa kart açma/geri dönüş ile bookmark, resume ve preview/header focus akışlarında regresyon olmadığını manuel doğrula.**
  - Note: Kullanıcı tarafından telefon layout’unda ana sayfa kart açma/geri dönüş ve ilgili focus akışları test edildi.

## Implementation Notes
- Önceki `20260824-1420-tv-focus-navigation-fixes.md` planının Home adapter state anahtarları ve child fallback değişiklikleri mevcut; bu çalışma onları geri almamalı, outer kategori görünürlüğü ile kart focus’unu tek bir dönüş işleminde koordine etmelidir.
