# uxmFarmerOtoEritme

[English](README.en.md)

UXMFarmer ana farmer menüsüne tıklamalı ve süreli otomatik eritme düğmesi ekleyen Paper eklentisi. Farmer sınıfları derleme zamanında kullanılmaz; event ve API bağlantıları çalışma anında, yansıma (reflection) ile kurulur.

## İçindekiler

- [Gereksinimler ve kurulum](#gereksinimler-ve-kurulum)
- [Çalışma şekli](#çalışma-şekli)
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

Varsayılan `trigger: click` ayarında toplama olayı otomatik eritme başlatmaz. Oyuncu farmer ana menüsünde `gui.slot` düğmesine, ardından eritilecek ürüne tıklar. Her işlem `gui.duration-seconds` kadar boss bar gösterir. Menü kapanınca işlem devam eder; oyuncu çıkarsa işlem iptal edilir. Aynı farmer veya oyuncu üzerinde ikinci işlem başlatılamaz.

Başlangıçta ve tamamlanmadan hemen önce farmer sahibi/üye ilişkisi ve izinler yeniden kontrol edilir. `uxmfarmerotosmelter.use` ile `gui.permission` zorunludur; yönetim işlemleri `uxmfarmerotosmelter.admin` ister. Her item kuralı ayrıca `permission` belirleyebilir.

Varsayılan tarif ham demirden (`RAW_IRON`) demir külçesi üretir. Örneğin depoda 3 ham demir varsa, 10 saniye sonunda farmer deposundan 3 ham demir düşülür ve oyuncuya 3 `IRON_INGOT` verilir. Bekleme sırasında kaynak tüketilmez. `RAW_IRON` ve `IRON_ORE` stokları birbirine karıştırılmaz. Komut modunda bir komut başarısız olursa önceki komutların etkisi geri alınamaz; kaynak iade edilmez ve yönetici loguna yazılır.

## Yapılandırma

Ayar dosyası `plugins/uxmFarmerOtoEritme/config.yml` konumunda oluşturulur; paketlenen varsayılanlar [config.yml](src/main/resources/config.yml) dosyasındadır.

- `items` anahtarları Bukkit `Material` adlarıdır (büyük/küçük harf fark etmez).
- `mode: plugin`, kaynağı düşürüp `output-material` ürününü aynı farmer deposuna ekler. Alan verilmezse tanınan malzemelerin varsayılan eritme eşleşmesi kullanılır; geçersiz tarifler loglanıp atlanır. `mode: commands`, kaynağı düşürüp `commands` listesini konsoldan çalıştırır.
- `min-amount`, `max-amount` (`0` sınırsız), `duration-seconds` ve `batch-size` item başına ayarlanabilir. `batch-size: 0`, uygun stoğun tamamıdır.
- Komut yer tutucuları `%player%`, `%uuid%`, `%owner%`, `%item%`, `%amount%`, `%farmer_uuid%` ve süslü parantezli karşılıklarıdır.
- `gui.rows` eritme menüsünün boyutudur; farmer ana menüsünün aynı boyutta olması gerekmez. `gui.slot` farmer ana menüsündeki düğme yeridir; dolu aktif bir düğmenin üzerine yazılmaz. Varsayılan slot `7`, UXMFarmer 3.18 ana menüsünde dolgu slotudur.
- GUI boyutu 1–6 satırla sınırlıdır; slot ve dolu/işgal edilmiş menü güvenliği kontrol edilir.

Eski config açılışta veya reload sırasında `config-version: 1`'e göç ettirilir. Eksik `RAW_IRON` tarifi eklenir, mevcut özel tarifler korunur. `enabled`, `trigger`, `hook.plugin-name`, `hook.event-class` ve event önceliği de ayarlanabilir.

`trigger: automatic` yalnız geriye dönük toplama event hook'unu açar; yeni kurulumlarda önerilen ve varsayılan kullanım `click`tir. Farmer sunucu açılışında geç gelirse bağlantı ve GUI otomatik olarak yeniden denenir.

Varsayılan ham demir tarifinden bir kesit:

```yaml
trigger: click
gui:
  slot: 7
  duration-seconds: 10
  batch-size: 64
items:
  RAW_IRON:
    mode: commands
    min-amount: 1
    consume: true
    commands:
      - 'give %player% iron_ingot %amount%'
```

## Uyumluluk ve event sözleşmeleri

GUI bağlantısı UXMFarmer 3.18 için `com.uxplima.uxmfarmer.api.event.impl.FarmerOpenMainMenuEvent`, legacy Farmer v6 için `xyz.geik.farmer.api.handlers.FarmerMainGuiOpenEvent` sınıfını otomatik arar. UXMFarmer 3.18'in eklenti adı `uxmFarmer`'dır; eski config'lerdeki `Farmer` adı da desteklenir.

`hook.plugin-name` bağımlı eklentiyi, `hook.event-class` ise **automatic modunun toplama olayını** seçer; GUI açılış olayı bu ayarla değiştirilmez. Başka bir fork kullanırken event ve depolama API'sinin uyumunu ayrıca kontrol edin.

Folia desteklenmez. Paper 26.2 ve Java 25 ile canlı sunucu çalışması doğrulanmamıştır; Java 21 derleme hedefi tek başına sunucu uyumluluğu garantisi değildir.

## Komutlar ve izinler

- `/uxmfarmerotosmelter reload` — config'i yeniden yükler; `/otoeritme` ve `farmerotosmelter` alias'ları vardır.
- `/otoeritme status` — farmer GUI bağlantısının aktif, kapalı veya bağlantı bekliyor durumunu gösterir.
- `uxmfarmerotosmelter.use` — GUI'den eritme başlatma (varsayılan: op).
- `uxmfarmerotosmelter.admin` — reload ve yönetim (varsayılan: op).

Reload veya eklentinin kapanması sırasında açık eritme menüleri kapanır; devam eden işlemler kaynak tüketmeden iptal edilir. Normal menü kapatma işlemi iptal etmez. Farmer'ın geç açılması durumunda bağlantı yeniden denenir; oyuncunun bağlantısı koparsa işlem iptal edilir.

## Sorun giderme

| Belirti | Kontrol |
| --- | --- |
| Oto eritme düğmesi görünmüyor | `/otoeritme status`, `gui.enabled` ve başlangıç logundaki `Farmer GUI oto eritme düğmesi aktif` satırını kontrol edin. |
| Menü bağlantısı aktif ama düğme yok | `gui.slot` farmer ana menüsünde boş veya dolgu slotu olmalıdır; aktif düğmelerin üzerine yazılmaz. |
| Oyuncu işlem başlatamıyor | Farmer sahibi/üye ilişkisini, `uxmfarmerotosmelter.use`, `gui.permission` ve tarifin ek iznini kontrol edin. |
| Bir tarif menüde görünmüyor | Material adını, `output-material` veya komut listesini ve logdaki atlanan tarif uyarısını kontrol edin. |
| Eritme tamamlanmıyor | Kaynak miktarını, plugin modunda hedef depo kapasitesini ve tamamlanma anındaki erişim izinlerini kontrol edin. |
| Komut başarısız ama kaynak düşmüş | Komutların yan etkileri geri alınamaz; kaynak bilerek iade edilmez. Konsol komutunu ve hata logunu kontrol edin. |

Farmer lisans, veritabanı veya başka bir başlangıç hatası nedeniyle devre dışıysa önce Farmer'ın kendi başlangıç hatasını giderin. Sorun bildirirken sunucu, Java ve Farmer sürümleriyle `/otoeritme status` çıktısını ve ilgili konsol hatasını ekleyin.
