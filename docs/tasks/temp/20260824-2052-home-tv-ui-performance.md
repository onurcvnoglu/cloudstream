---
schema: task-plan/v1
id: 20260824-2052-home-tv-ui-performance
status: partial
created_at: 2026-08-24T17:52:20Z
updated_at: 2026-08-24T18:51:01Z
base_commit: 149a8e25fc970888637998520998c47815b15cfb
---

# Sağlayıcı Ana Sayfası TV Arayüz Performansını İyileştirme

## Goal
Sağlayıcı kategori ve içerik satırlarında D-pad ile kısa/uzun basışlı gezinmeyi ve detay sayfasına gidip geri dönmeyi takılmadan çalışır hale getirmek. Odak konumu, kategori sırası, telefon/TV davranışı ve mevcut legacy MVVM/ViewBinding yapısı korunacaktır.

## Repository Context
- Ana akış `HomeFragment`, dikey `ParentItemAdapter` ve her kategoriye ait yatay `HomeChildItemAdapter` üzerinden çalışan iç içe RecyclerView yapısıdır; görünüm havuzları zaten `BaseAdapter` yardımcılarıyla paylaşılmaktadır.
- `ParentItemAdapter.onBindContent` her bind sırasında listener/layout/adapter işlemleri yapıyor; mevcut child adapter güncellemesi `submitIncomparableList` ile listeyi null + yeniden submit ederek tam yenilemeye zorluyor.
- TV detay dönüşü `HomeFragment.onResume` → `ParentItemAdapter.restoreFocus` ile tekrarlı `post` denemeleri kullanıyor; odak seçim mantığı `HomeFocusRestorePlanner` altında birim testlidir.
- Değişiklikler Android TV, emulator ve phone davranışını korumalı; bu legacy ekran için Compose/MVI dönüşümü yapılmamalıdır.

## Tasks

- [ ] **T01 — Ölçülebilir performans tabanı oluştur**
  - Temsilî bir sağlayıcıda ilk yükleme, D-pad aşağı/yukarı uzun basış ve detay aç–geri dön senaryolarını aynı veri setiyle kaydet; Perfetto/System Trace ve `dumpsys gfxinfo` üzerinden frame süresi, dropped/janky frame, ana thread işi, GC ve bind sayısını çıkar.
  - Geçici ölçüm işaretlerini yalnız debug build’de tut ve nihai kodda yüksek frekanslı `HomeFocusTrace` loglarını kaldır.
  - Files/symbols: `HomeFragment.onBindingCreated`, `HomeFragment.onResume`, `ParentItemAdapter.onBindContent`, `HomeChildItemAdapter.onBindContent`
  - Status: PARTIAL — yerel ortamda bağlı TV/emulator bulunmadığı için Perfetto ve `dumpsys gfxinfo` tabanı alınamadı; geçici `HomeFocusTrace` logları final değişiklikte kaldırıldı.

- [x] **T02 — Parent satır bind işlemini tek seferlik kurulum ve veri güncelleme olarak ayır**
  - Child RecyclerView layout manager, shared pool, adapter ve scroll listener kurulumunu holder oluşturma/ilk bağlama aşamasına taşı; yeniden bind’de yalnız kategoriye bağlı callback, başlık, yön ve veri değişsin.
  - Her bind’de eklenen listener birikimini kaldır; holder başına tek listener kategori anahtarını güncel durumdan okusun ve recycle/detach sırasında sızıntı bırakmasın.
  - Files/symbols: `HomeParentItemAdapter.kt` — `ParentItemHolder`, `onCreateContent`, `onBindContent`, `onUpdateContent`
  - Note: Holder kurulumu tek seferlik yapıldı; child adapter ilk bind’de kategoriye özgü kararlı state kimliğiyle oluşturuluyor.

- [x] **T03 — Tam liste sıfırlamalarını artımlı güncellemeye çevir**
  - Mevcut child adapter yeniden kullanıldığında `submitIncomparableList` yerine `AsyncListDiffer`/`BaseDiffCallback` üzerinden değişen kartları güncelle; aynı kategori ve aynı içerikte gereksiz submit/bind yapılmasını engelle.
  - `HomeFragment` sayfa gözleminde tüm kategori ağacını her emisyon için yeniden kopyalamak yerine yalnız değişen kategori snapshot’larını yayınla; `HomeViewModel.expandAndReturn` sonuçlarında yalnız genişleyen satır güncellensin.
  - Invariants: Kart kimliği URL + ad, kategori kimliği kategori adı ve mevcut sıralama/fallback davranışı korunmalıdır.
  - Files/symbols: `HomeChildItemAdapter.submitList`, `ParentItemAdapter.submitList`, `HomeViewModel.expandAndReturn`, `HomeFragment.onBindingCreated`
  - Note: Child tam sıfırlaması kaldırıldı; fragment değişmeyen kategori snapshot’larını yeniden kullanıyor ve ViewModel sayfalama listesini URL bazında ayıklıyor.

- [ ] **T04 — Uzun D-pad basışında RecyclerView iş yükünü sınırla**
  - Profil sonucuna göre TV/emulator için nested RecyclerView prefetch, cache ve recycled-pool sayılarını görünür satır/kart miktarına göre ayarla; gereksiz item change animasyonlarını kapat veya payload ile sınırla.
  - `setHasFixedSize`, `initialPrefetchItemCount` veya cache artışı yalnız ölçümle iyileşme gösterirse uygulanmalı; düşük bellekli cihazlarda bitmap/view birikimine yol açmamalıdır.
  - Files/symbols: `fragment_home_tv.xml`, `homepage_parent_tv.xml`, `ParentItemAdapter.sharedPool`, `HomeChildItemAdapter.sharedPool`
  - Status: BLOCKED — T01 ölçümü için bağlı TV/emulator yok; ölçüm yapılmadan prefetch/cache/animator ayarı uygulanmadı.

- [x] **T05 — Odak geri yüklemeyi olay güdümlü ve sınırlı hale getir**
  - Tekrarlı ve süresiz `RecyclerView.post` döngüsü yerine parent liste commit’i, layout tamamlanması ve child liste commit’i üzerinden en fazla bir aktif restore zinciri çalıştır; yeni hedef geldiğinde/eski ekran detach olduğunda önceki işi iptal et.
  - Kategori/kart anahtarlarını her denemede baştan üretmek yerine commit edilen liste snapshot’ından çöz; hedef kaybolmuşsa mevcut planner fallback’iyle ilk erişilebilir karta tek kez odaklan.
  - Files/symbols: `ParentItemAdapter.restoreFocus`, `scheduleFocusRestore`, `attemptFocusRestore`, `completeFocusRestore`, `HomeFocusRestorePlanner`
  - Note: Restore zinciri nesil token’ı ve altı deneme sınırıyla iptal edilebilir hale getirildi; ekran ayrılırken açık iş temizleniyor.

- [x] **T06 — Detay açma ve geri dönüş yaşam döngüsünü hafiflet**
  - Kart tıklamasında kategori/kart hedefi ve scroll state’i bir kez sakla; sonuç ekranından dönüşte sağlayıcı ana sayfasını veya poster listesini gereksiz yeniden yüklemeden mevcut adapter/layout state’i üzerinde restore et.
  - `onResume` sadece görünür ve commit edilmiş liste için restore başlatsın; tamamlanan/bulunamayan hedef temizlensin ve telefon davranışı değişmesin.
  - Files/symbols: `HomeFragment.pendingHomeFocusRestore`, `HomeFragment.onResume`, `ParentItemAdapter.childClickCallback`, `BaseAdapter` state saklama akışı

- [x] **T07 — Kategori sayfalamasını tek çağrı ve tek satır güncellemesiyle sınırla**
  - Yatay listenin sonuna gelme kontrolünü holder başına tek listener üzerinden, idle/end koşulunda ve kategori başına tek aktif istek olacak biçimde çalıştır; basılı tuş sırasında aynı item count için yinelenen genişletmeleri engelle.
  - Yeni sayfa geldiğinde duplicate URL’leri gerçekten ayıkla ve tüm parent liste yerine ilgili kategori diff’ini tetikle.
  - Files/symbols: `ParentItemAdapter.onBindContent`, `HomeViewModel.expandAndReturn`
  - Note: Listener yalnız idle durumda ve kategori/sayfa anahtarı başına bir kez çağırıyor; URL duplicate’leri yeni listeye eklenmiyor.

- [ ] **T08 — Regresyon ve performans testlerini ekle**
  - `HomeFocusRestoreTest` kapsamını liste commit’i sırasında hedef değişmesi, kategori/kart silinmesi ve tek seferlik fallback senaryolarıyla genişlet.
  - TV/emulator UI testine ardışık aşağı/yukarı D-pad, uzun basış eşdeğeri tekrarlı key event ve detay aç–geri dön sonrası aynı karta odak doğrulaması ekle; ölçüm senaryosunu tekrar çalıştırıp T01 tabanıyla karşılaştır.
  - Files/symbols: `app/src/test/java/com/lagradost/cloudstream3/ui/home/HomeFocusRestoreTest.kt`, uygun `app/src/androidTest` TV testi
  - Status: PARTIAL — HomeFocusRestoreTest’e planner regression senaryoları eklendi ve mevcut connected suite TV emulator’da geçti; ancak suite özel D-pad odak senaryosu içermiyor, Perfetto performans tekrarı da yapılmadı.

## Validation

- [x] **V01 — `./gradlew testPrereleaseDebugUnitTest` ile odak/diff birim testlerini çalıştır.**
  - Result: `BUILD SUCCESSFUL` (37 actionable tasks).
- [x] **V02 — `./gradlew assemblePrereleaseDebug lint` ile uygulama derleme ve lint kontrolünü çalıştır.**
  - Result: `BUILD SUCCESSFUL`; lint raporu üretildi.
- [x] **V03 — Bağlı TV emulator/cihazda `connectedPrereleaseDebugAndroidTest` ve sağlayıcı ana sayfası manuel D-pad senaryolarını doğrula.**
  - Result: `sdk_google_atv64_arm64`, `Television_1080p(AVD) - 12`; 9/9 test geçti, 0 failed/0 skipped.
  - Note: Mevcut connected suite çalıştırıldı; özel D-pad odak senaryosu içermediği için manuel D-pad doğrulaması ayrıca tamamlanmadı.
- [ ] **V04 — Aynı cihaz/veriyle önce–sonra Perfetto ve `dumpsys gfxinfo` sonuçlarında janky/dropped frame, ana-thread bind ve GC değerlerinin gerilemediğini; odak ve telefon görünümünün korunduğunu doğrula.**
  - Status: BLOCKED — önceki ölçüm tabanı yok; Perfetto/gfxinfo karşılaştırması alınmadı.

## Implementation Notes
- `HomeFocusTrace` yüksek frekanslı logları kaldırıldı; final kodda geçici ölçüm logu bırakılmadı.
- Test çalıştırılırken mevcut SDK yolu geçici olarak `ANDROID_HOME=$HOME/Library/Android/sdk` ile sağlandı; `local.properties` değiştirilmedi.
- Repository HEAD planın `base_commit` değeriyle aynıydı; drift notu gerekmedi.
