# uxmFarmerOtoEritme

[English](README.en.md)

![Java 21+](https://img.shields.io/badge/Java-21%2B-f59e0b)
![Paper API 1.21.1](https://img.shields.io/badge/Paper_API-1.21.1-38bdf8)
[![GPL-3.0](https://img.shields.io/badge/License-GPL--3.0-22c55e)](LICENSE)

Farmer deposundaki pişmemiş metal cevherlerini tıklama gerektirmeden eritip aynı depoya yazan Paper eklentisi. uxmFarmer düzenine uygun bir menüden stok, kapasite ve devam eden işlemler görüntülenir; manuel eritme de kullanılabilir. Farmer sınıfları derleme zamanında kullanılmaz; event ve API bağlantıları çalışma anında, yansıma (reflection) ile kurulur.

![Süreli ham demir eritme akışı](assets/smelting-flow.gif)

*Temsili işleyiş animasyonu; oyun içi kayıt değildir. Animasyon hızlandırılmıştır; varsayılan işlem süresi 10 saniyedir.*

## İçindekiler

- [Gereksinimler ve kurulum](#gereksinimler-ve-kurulum)
- [Çalışma şekli](#çalışma-şekli)
- [Farmer tarzı menü](#farmer-tarzı-menü)
- [Yapılandırma](#yapılandırma)
- [Uyumluluk ve event sözleşmeleri](#uyumluluk-ve-event-sözleşmeleri)
- [Komutlar ve izinler](#komutlar-ve-izinler)
- [Sorun giderme](#sorun-giderme)

## Gereksinimler ve kurulum

- Sunucuda Java 21 veya üzeri gerekir; kaynaktan derleme için ayrıca Maven 3 kullanılır.
- Derleme hedefi Paper API 1.21.1'dir; bu, diğer Paper sürümlerinde çalışma garantisi değildir.
- `mvn clean verify` çalıştırın ve `target/uxmFarmerOtoEritme.jar` dosyasını sunucunuzun `plugins/` klasörüne kopyalayın. İlk açılışta config oluşturulur.
- Derleme için ücretli veya özel Farmer JAR'ı gerekmez. Üçüncü taraf Farmer dosyaları bu projeye dahil değildir; çalışan sunucuda ilgili Farmer eklentisi bulunmalıdır.
- GPL-3.0-only lisansı için [LICENSE](LICENSE) dosyasına bakın.

## Çalışma şekli

Varsayılan `automatic-smelting.enabled: true` ayarında Farmer depoları düzenli taranır. Uygun miktarda pişmemiş cevher bulunduğunda süreli eritme otomatik başlar; menüye tıklamak veya menüyü açık tutmak gerekmez. GUI kapalıyken de otomatik işlemler çalışır. Sınırsız demir üretimi diğer cevherlerin işlenmesini engellemesin diye tarifler sırayla değerlendirilir.

Demir, altın ve bakırın ham, normal cevher ve derin kayrak varyantları ile Nether altın cevheri desteklenir. Yalnızca `items` altında tanımlı metal cevheri `plugin` tarifleri otomatik işlenir; özel komut tarifleri otomatik komut çalıştırmaz.

Farmer ana menüsündeki `gui.slot` düğmesinden ürün menüsüne girip manuel işlem başlatmak da mümkündür. Aynı Farmer'da otomatik ve manuel işlemler çakışamaz; aynı oyuncu iki manuel işlem başlatamaz. Manuel işlemler boss bar gösterir. Menü kapatılınca işlem devam eder; oyuncu çıkarsa yalnızca onun manuel işlemi iptal edilir.

Manuel kullanımda başlangıçta ve tamamlanmadan hemen önce sahip/üye ilişkisi, `uxmfarmerotosmelter.use`, `gui.permission` ve tarif izni kontrol edilir. Otomatik kullanım sunucu ayarıyla açılır; varsayılan tarifler sahibi çevrimdışıyken de çalışır. Ek izin isteyen bir tarif, sahibinin çevrimiçi olması ve ilgili izne sahip olması koşuluyla otomatik işlenir.

Varsayılan tarif ham demirden (`RAW_IRON`) demir külçesi üretir. Örneğin depoda 3 ham demir varsa, 10 saniye sonunda aynı Farmer deposundan 3 ham demir düşülür ve depodaki `IRON_INGOT` miktarı 3 artırılır. **Oyuncu envanterine eşya verilmez.** Bekleme sırasında kaynak tüketilmez. `RAW_IRON` ve `IRON_ORE` stokları birbirine karıştırılmaz.

Demir cevherleri `IRON_INGOT`, altın cevherleri `GOLD_INGOT`, bakır cevherleri `COPPER_INGOT` olarak aynı depoya eklenir. Kaynak zaten Farmer deposunda bulunmalıdır; bu eklenti yere düşen eşyaları toplamaz. Çıktı malzemesi Farmer'ın `collected-materials.yml` dosyasında tanımlı olmalıdır. Hedef ürün kapasitesi yetersizse işlem kaynak tüketmeden durur.

## Farmer tarzı menü

Varsayılan menü 6 satırdır: gri cam çerçeve, ortada 28 ürün slotu ve ayrı kontrol alanları. Ürün açıklamaları kaynak stoğunu, çıktı deposunu/kapasitesini, işlem miktarını ve süresini gösterir. Otomatik eritme durumu ve aktif işlem ilerlemesi menüden görülebilir. Geri düğmesi Farmer ana menüsüne döner; sayfalama ve yenileme düğmeleri ürün slotlarıyla çakışmaz.

Stok ve ilerleme değerleri menü açıldığında veya **Yenile** düğmesine basıldığında güncellenir. Ürün adları Minecraft istemcisinin dilinde gösterilir.

Başlık ve dolgu malzemesi `gui.title` / `gui.filler-material` ile ayarlanır. Yeni varsayılanlar uxmFarmer'ın MiniMessage renklerini kullanır; mevcut özel `&` renk kodlu metinler de okunur.

## Yapılandırma

Ayar dosyası `plugins/uxmFarmerOtoEritme/config.yml` konumunda oluşturulur; paketlenen varsayılanlar [config.yml](src/main/resources/config.yml) dosyasındadır.

- `items` anahtarları Bukkit `Material` adlarıdır (büyük/küçük harf fark etmez).
- Varsayılan `mode: plugin`, kaynağı düşürüp `output-material` ürününü **aynı Farmer deposuna** ekler; oyuncuya ödül komutu çalıştırmaz. Alan verilmezse tanınan malzemelerin varsayılan eritme eşleşmesi kullanılır; geçersiz tarifler loglanıp atlanır. İsteğe bağlı `mode: commands`, kaynağı düşürüp özel `commands` listesini konsoldan çalıştırır.
- `min-amount`, `max-amount` (`0` sınırsız), `duration-seconds` ve `batch-size` item başına ayarlanabilir. `batch-size: 0`, uygun stoğun tamamıdır.
- `automatic-smelting.enabled` depodan otomatik eritmenin anahtarıdır; `interval-ticks` tarama aralığıdır (varsayılan: 20 tick).
- Komut yer tutucuları `%player%`, `%uuid%`, `%owner%`, `%item%`, `%amount%`, `%farmer_uuid%` ve süslü parantezli karşılıklarıdır.
- `gui.rows` eritme menüsünün boyutudur; farmer ana menüsünün aynı boyutta olması gerekmez. `gui.slot` farmer ana menüsündeki düğme yeridir; dolu aktif bir düğmenin üzerine yazılmaz. Varsayılan slot `7`, UXMFarmer 3.18 ana menüsünde dolgu slotudur.
- GUI boyutu 3–6 satırla sınırlıdır; varsayılan 6 satırda 28 ürün gösterilir. Slot ve dolu/işgal edilmiş menü güvenliği kontrol edilir.

Eski config açılışta veya reload sırasında `config-version: 3`'e güncellenir. Önceki varsayılan `RAW_IRON`, `IRON_ORE` ve `GOLD_ORE` envantere verme tarifleri depoya yazan `plugin` tariflerine dönüştürülür; miktar, süre ve izin ayarları korunur. Yeni cevher varyantları yalnızca eksikse eklenir. Özel tarifler/metinler korunur; önceki sürümlerde bilerek kaldırılmış eski varsayılan tarifler yeniden eklenmez.

`trigger: automatic` eski toplama-event/komut hook'unu açar; **depodan otomatik eritme ayarı değildir**. Yeni depodan eritme `automatic-smelting.enabled` ile kontrol edilir. Farmer sunucu açılışında geç gelirse işlem ve GUI bağlantısı otomatik olarak yeniden denenir.

Varsayılan ham demir tarifinden bir kesit:

```yaml
trigger: click
automatic-smelting:
  enabled: true
  interval-ticks: 20
gui:
  title: '<black><bold>Oto Eritme'
  filler-material: GRAY_STAINED_GLASS_PANE
  rows: 6
  slot: 7
  duration-seconds: 10
  batch-size: 64
items:
  RAW_IRON:
    mode: plugin
    output-material: IRON_INGOT
    min-amount: 1
```

## Uyumluluk ve event sözleşmeleri

GUI bağlantısı UXMFarmer 3.18 için `com.uxplima.uxmfarmer.api.event.impl.FarmerOpenMainMenuEvent`, legacy Farmer v6 için `xyz.geik.farmer.api.handlers.FarmerMainGuiOpenEvent` sınıfını otomatik arar. UXMFarmer 3.18'in eklenti adı `uxmFarmer`'dır; eski config'lerdeki `Farmer` adı da desteklenir.

Depodan otomatik eritme modern UXMFarmer 3.18'in depo/listeleme API'sini kullanır. Bu API'si bulunmayan legacy Farmer sürümlerinde otomatik çalışma açıkça kullanılamaz olarak bildirilir; manuel menü bağlantısı korunur.

`hook.plugin-name` bağımlı eklentiyi, `hook.event-class` ise **automatic modunun toplama olayını** seçer; GUI açılış olayı bu ayarla değiştirilmez. Başka bir fork kullanırken event ve depolama API'sinin uyumunu ayrıca kontrol edin.

Folia desteklenmez. Paper 26.2 ve Java 25 ile canlı sunucu çalışması doğrulanmamıştır; Java 21 derleme hedefi tek başına sunucu uyumluluğu garantisi değildir.

## Komutlar ve izinler

- `/uxmfarmerotosmelter reload` — config'i yeniden yükler; `/otoeritme` ve `farmerotosmelter` alias'ları vardır.
- `/otoeritme status` — Farmer bağlantısını, menü durumunu ve otomatik eritmenin durumunu gösterir.
- `uxmfarmerotosmelter.use` — GUI'den eritme başlatma (varsayılan: op).
- `uxmfarmerotosmelter.admin` — reload ve yönetim (varsayılan: op).

Reload veya eklentinin kapanması sırasında açık eritme menüleri kapanır; devam eden tüm işlemler kaynak tüketmeden iptal edilir. Normal menü kapatma işlemi iptal etmez. Farmer'ın geç açılması durumunda bağlantı yeniden denenir; oyuncunun bağlantısı koparsa yalnızca manuel işlemi iptal edilir.

## Sorun giderme

| Belirti | Kontrol |
| --- | --- |
| Oto eritme düğmesi görünmüyor | `/otoeritme status`, `gui.enabled` ve başlangıç logundaki `Farmer GUI oto eritme düğmesi aktif` satırını kontrol edin. |
| Menü bağlantısı aktif ama düğme yok | `gui.slot` farmer ana menüsünde boş veya dolgu slotu olmalıdır; aktif düğmelerin üzerine yazılmaz. |
| Oyuncu işlem başlatamıyor | Farmer sahibi/üye ilişkisini, `uxmfarmerotosmelter.use`, `gui.permission` ve tarifin ek iznini kontrol edin. |
| Bir tarif menüde görünmüyor | Material adını, `output-material` veya komut listesini ve logdaki atlanan tarif uyarısını kontrol edin. |
| Eritme tamamlanmıyor | Kaynak miktarını, çıktı malzemesinin Farmer'da tanımlı olmasını, hedef depo kapasitesini ve tamamlanma anındaki erişim izinlerini kontrol edin. |
| Depodaki cevherler otomatik işlenmiyor | `automatic-smelting.enabled`, `/otoeritme status`, tarifin `plugin` modu ve çıktı deposunun kapasitesini kontrol edin. Ek izinli tarifte sahibi çevrimiçi olmalıdır. |
| Özel komut tarifinde komut başarısız ama kaynak düşmüş | Komutların yan etkileri geri alınamaz; kaynak bilerek iade edilmez. Konsol komutunu ve hata logunu kontrol edin. Varsayılan depoya eritme tarifleri komut çalıştırmaz. |

Farmer lisans, veritabanı veya başka bir başlangıç hatası nedeniyle devre dışıysa önce Farmer'ın kendi başlangıç hatasını giderin. Sorun bildirirken sunucu, Java ve Farmer sürümleriyle `/otoeritme status` çıktısını ve ilgili konsol hatasını ekleyin.
