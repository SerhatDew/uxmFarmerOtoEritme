# uxmFarmerOtoEritme

Farmer/UXMFarmer'ın ana farmer menüsüne tıklamalı ve süreli bir oto eritme düğmesi ekleyen Paper eklentisidir. Hedef eklentinin sınıfları derleme zamanında kullanılmaz; event ve API sınıfları çalışma anında bulunur.

## Çalışma şekli

Varsayılan `trigger: click` ayarında toplama olayı otomatik eritme başlatmaz. Oyuncu farmer ana menüsünde `gui.slot` düğmesine, sonra eritilecek ürüne tıklar. Her işlem için `gui.duration-seconds` kadar boss bar görünür; menüyü kapatmak işlemi durdurmaz, oyuncu çıkarsa işlem iptal edilir. Aynı farmer veya oyuncu üzerinde ikinci işlem başlatılamaz.

`uxmfarmerotosmelter.use` ve `gui.permission` izinleri zorunludur. Farmer sahibi veya UXMFarmer üyesi işlem yapabilir. Eklenti, her işlem başlangıcında ve tamamlanmadan önce yetkiyi tekrar kontrol eder.

## Yapılandırma

`items` altındaki anahtarlar Bukkit `Material` adlarıdır. `mode: plugin` kaynak miktarını düşürür ve `output-material` (bilinmiyorsa demir, altın, bakır gibi varsayılan eşleşme) aynı farmer deposuna ekler. `mode: commands` kaynak miktarını düşürür ve `commands` listesini konsoldan çalıştırır. Komutlardan biri başarısız olursa daha önce çalışmış komutların yan etkisi geri alınamayacağı için kaynak iade edilmez ve yönetici loguna yazılır.

`min-amount`, `max-amount` (`0` sınırsız), `duration-seconds` ve `batch-size` her item için ayrı verilebilir. `permission` item bazında ek izin ister. Komut yer tutucuları `%player%`, `%uuid%`, `%owner%`, `%item%`, `%amount%`, `%farmer_uuid%` ve süslü parantezli karşılıklarıdır.

`trigger: automatic` yalnız geriye dönük event hook'unu açar; yeni kurulumlarda önerilen ve varsayılan kullanım `click` modudur. `hook.event-class` ve `hook.plugin-name` değiştirilebilir. Farmer sunucu açılışında geç gelirse hook ve GUI bağlantısı otomatik olarak yeniden denenir.

Farmer v6 için GUI bağlantısı `Farmer` eklenti adı ve `xyz.geik.farmer.api.handlers.FarmerMainGuiOpenEvent` sözleşmesini otomatik bulur. Başka bir UXMFarmer fork'u kullanıyorsanız `hook.plugin-name` ve `hook.event-class` alanlarını kendi public event sınıfınıza göre değiştirin.

`/uxmfarmerotosmelter reload` config'i yeniden yükler. Reload veya kapatma sırasında açık oto eritme menüleri kapanır; devam eden işlemler kaynak tüketmeden iptal edilir.
