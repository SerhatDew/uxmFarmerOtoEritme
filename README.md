# uxmFarmerOtoEritme

UXMFarmer’ın `FarmerCollectProductEvent` olayını çalışma anında bulup dinleyen bağımsız bir Paper eklentisidir. UXMFarmer API sınıfları derleme içine alınmadığı için UXMFarmer güncellendiğinde eklentinin yeniden derlenmesi gerekmez.

## Kullanım

`plugins/uxmFarmerOtoEritme/config.yml` içindeki `items` bölümüne Bukkit `Material` adını yazın.

- `mode: plugin`: ürünü UXMFarmer’ın kendi deposuna bırakır.
- `mode: commands`: `commands` listesindeki komutları konsol olarak çalıştırır.
- `consume: true`: komutlar çalıştığında toplama miktarını UXMFarmer’a `0` olarak iletir; böylece ürün depoya eklenmez.
- `min-amount` ve `max-amount`: olay miktarı aralığını sınırlar (`max-amount: 0` sınırsızdır).
- `permission`: çiftlik sahibinde aranacak yetki; boş bırakılırsa kontrol edilmez.

`permission` kontrolü için çiftlik sahibinin çevrimiçi olması gerekir. Sahip çözülemezse `%player%`, `%owner%` veya `%uuid%` kullanan komutlar güvenli şekilde atlanır.

Komut yer tutucuları: `%player%`, `%uuid%`, `%owner%`, `%item%`, `%amount%`, `%farmer_uuid%`. `{player}` gibi süslü parantezli biçim de desteklenir. Sahip çevrimdışıysa `%player%` adı yerine UUID kullanılamaz; bu durumda sahip adı `OfflinePlayer` üzerinden çözülür.

`/otoeritme reload` komutu ile dosya yeniden yüklenir. Hook sınıfı bulunamazsa eklenti kapanmaz; yalnızca uyarı verip bekler. `/otoeritme reload` sonrasında UXMFarmer sonradan açılmışsa hook yeniden denenir.
