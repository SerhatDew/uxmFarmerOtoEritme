# uxmFarmerOtoEritme

Farmer/UXMFarmer'ın ana farmer menüsüne tıklamalı ve süreli bir oto eritme düğmesi ekleyen Paper eklentisidir. Hedef eklentinin sınıfları derleme zamanında kullanılmaz; event ve API sınıfları çalışma anında bulunur.

## Çalışma şekli

Varsayılan `trigger: click` ayarında toplama olayı otomatik eritme başlatmaz. Oyuncu farmer ana menüsünde `gui.slot` düğmesine, sonra eritilecek ürüne tıklar. Her işlem için `gui.duration-seconds` kadar boss bar görünür; menüyü kapatmak işlemi durdurmaz, oyuncu çıkarsa işlem iptal edilir. Aynı farmer veya oyuncu üzerinde ikinci işlem başlatılamaz.

`uxmfarmerotosmelter.use` ve `gui.permission` izinleri zorunludur. Farmer sahibi veya UXMFarmer üyesi işlem yapabilir. Eklenti, her işlem başlangıcında ve tamamlanmadan önce yetkiyi tekrar kontrol eder.

## Yapılandırma

`items` altındaki anahtarlar Bukkit `Material` adlarıdır. `mode: plugin` kaynak miktarını düşürür ve `output-material` (bilinmiyorsa demir, altın, bakır gibi varsayılan eşleşme) aynı farmer deposuna ekler. `mode: commands` kaynak miktarını düşürür ve `commands` listesini konsoldan çalıştırır. Komutlardan biri başarısız olursa daha önce çalışmış komutların yan etkisi geri alınamayacağı için kaynak iade edilmez ve yönetici loguna yazılır.

`min-amount`, `max-amount` (`0` sınırsız), `duration-seconds` ve `batch-size` her item için ayrı verilebilir. `permission` item bazında ek izin ister. Komut yer tutucuları `%player%`, `%uuid%`, `%owner%`, `%item%`, `%amount%`, `%farmer_uuid%` ve süslü parantezli karşılıklarıdır.

Ham demir (`RAW_IRON`) ayrı bir tariftir; demir cevheri (`IRON_ORE`) stokuyla birleştirilmez. Varsayılan ham demir tarifi farmer deposundan ham demir tüketip aynı miktarda demir külçesini oyuncuya verir. Örneğin 3 ham demir, 10 saniye sonunda 3 külçe verir. Eski config dosyası açılışta veya reload sırasında bir kez güncellenir; eksik ham demir tarifi eklenir, mevcut özel tarifler korunur. Güncelleme sonrası `config-version: 1` tutulur.

`trigger: automatic` yalnız geriye dönük event hook'unu açar; yeni kurulumlarda önerilen ve varsayılan kullanım `click` modudur. `hook.event-class` ve `hook.plugin-name` değiştirilebilir. Farmer sunucu açılışında geç gelirse hook ve GUI bağlantısı otomatik olarak yeniden denenir.

Farmer v6 için GUI bağlantısı `Farmer` eklenti adı ve `xyz.geik.farmer.api.handlers.FarmerMainGuiOpenEvent` sözleşmesini otomatik bulur. Başka bir UXMFarmer fork'u kullanıyorsanız `hook.plugin-name` ve `hook.event-class` alanlarını kendi public event sınıfınıza göre değiştirin.

`/uxmfarmerotosmelter reload` config'i yeniden yükler. `/otoeritme status` menü bağlantısının aktif, kapalı veya bağlantı bekliyor olduğunu gösterir. Reload veya kapatma sırasında açık oto eritme menüleri kapanır; devam eden işlemler kaynak tüketmeden iptal edilir.

UXMFarmer 3.18 için eklenti adı `uxmFarmer`, ana menü olayı `com.uxplima.uxmfarmer.api.event.impl.FarmerOpenMainMenuEvent` sınıfıdır. Eski config dosyalarındaki `hook.plugin-name: Farmer` değeri de desteklenir. Bağlantı her iki eklenti adını kontrol eder. `gui.rows` eritme menüsünün ayarıdır; farmer ana menüsünün aynı boyutta olması gerekmez. `gui.slot` ana menüde boş veya dolgu olarak kullanılan bir slot olmalıdır; başka bir aktif düğmenin üzerine yazılmaz. Varsayılan slot `7`, UXMFarmer 3.18'in ana menüsünde dolgu slotudur.

Düğme görünmüyorsa `/otoeritme status` çıktısını ve başlangıç logundaki `Farmer GUI oto eritme düğmesi aktif` satırını kontrol edin. Farmer lisans, veritabanı veya başka bir başlangıç hatası yüzünden devre dışı kalmışsa önce Farmer'ın başlangıç hatası giderilmelidir.
