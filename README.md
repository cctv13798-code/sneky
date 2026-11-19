# Ykens (Fabric, Minecraft 1.21.1)

Görünmeyen bir psikolojik korku varlığı. Gerçek bir mob yok; Ykens oyuncunun etrafında sunucu tarafında "musallat" olaylarıyla var olur.

## Ne yapar?

| Aşama | Olaylar |
|-------|---------|
| 1 | Arkandan sesler ve yaklaşan ayak sesleri, yakındaki sandıkların içeriği karışır |
| 2 | + Yakındaki köylüler donup sana bakar, sonra kovalayıp vurur. Jumpscare (ekran kararır, kanlı köylü resmi ve çığlık). Sana özel garip gökyüzü (gün ortasında dolunay gecesi). Ani yağmur |
| 3 | + Olaylar daha sık. Demir golemler sana saldırır. Fısıltı tabelaları (bazen ters yazılı). Evinden uzaklaşıp dönünce "Bana güven, 1000 blok yürü" tabelası |

Tabelayı bıraktıktan sonra sesler ve jumpscare'ler sıklaşır. Tabeladan 1000 blok uzaklaşırsan önünde 3x3 bir lav çukuru ve "lava atla" tabelası belirir. Seni zorlayan bir şey yok, atlayıp atlamamak sana kalmış.

Mod kendiliğinden, oturumun 5. dakikasından sonra başlar; her 6 dakikada bir aşama yükselir (en fazla 3).

## Komutlar (op, seviye 2)

- `/ykens start` Ykens'i uyandırır (aşama 1), ev konumu = bulunduğun yer
- `/ykens stop` susturur, bu oturumda otomatik başlamaz, donmuş köylüleri serbest bırakır
- `/ykens stage <1-3>` aşamayı ayarlar
- `/ykens villagers` yakındaki köylüleri hemen ele geçirir (bakarlar, sonra saldırırlar)
- `/ykens scare` jumpscare'i hemen atar
- `/ykens sky` garip gökyüzünü hemen başlatır (sadece sana görünür)
- `/ykens sethome` ev konumunu bulunduğun yere alır
- `/ykens sign` "Bana güven" tabelasını hemen bırakır ve 1000 blok sayacını başlatır
- `/ykens trap` lav tuzağını hemen kurar (test için)
- `/ykens status` durumu gösterir

Hızlı deneme: yaratıcı bir dünyada köylü köyünün yanında `/ykens stage 3`, sonra `/ykens villagers`, `/ykens scare`, `/ykens sky`.

## Derleme

GitHub Actions ile (bilgisayara bir şey kurmadan): dosyaları bir GitHub reposuna yükle, `.github/workflows/build.yml` dosyası kökte olsun. Eylemler (Actions) sekmesindeki çalışmanın Artifacts bölümünden `ykens-mod` zip'ini indir, içindeki `ykens-1.0.0.jar` dosyasını `mods/` klasörüne at. Fabric Loader 0.16+ ve Fabric API gerekir.

Kendi bilgisayarında: Java 21 ve Gradle ile `gradle build`. Çıktı: `build/libs/ykens-1.0.0.jar`.

## Ayarlar

`HauntManager.java` başındaki sabitler: otomatik başlama süresi, aşama süresi, yürüme mesafesi, köylü vuruş hasarı (`VILLAGER_DAMAGE`) ve her olayı açıp kapatan `true/false` bayrakları (köylüler, jumpscare, gökyüzü, lav tuzağı, hava, golem, sandık).

## Notlar

- Garip gökyüzü sadece oyuncunun ekranında değişir, dünya saati değişmez. Ay'ın görünümünü (kırmızı, dev vb.) değiştirmek için istemci tarafı bir kaynak paketi gerekir; bu mod sunucu tarafında kaldığı için gökyüzünü dolunay gecesine çeviriyor.
- Jumpscare'de ekran kararır (körlük + karanlık efekti) ve ekranın ortasında kanlı köylü resmi belirir. Resim mod jar'ının içindeki özel bir font karakteri olarak (`assets/ykens/font/scare.json`, `assets/ykens/textures/font/scare.png`) büyük başlık şeklinde gösterilir. Bu yüzden mod oyuncunun kendi oyununda da yüklü olmalı (tek kişilik dünyada zaten öyle). Mod yüklü olmayan bir oyuncuda resim yerine boş kutu görünür.
- Resmi değiştirmek için `scare.png` dosyasını aynı adla değiştir. Boyutu `scare.json` içindeki `height` (60) ayarıyla büyütüp küçültebilirsin.
- Lav tuzağı dünyada kalıcı değişiklik yapar. Önemli bir dünyada denemeden önce yedek al.
- Ykens'in durumu bellekte tutulur; sunucu yeniden başlayınca aşama sıfırlanır.
- Köylüler donup kovalarken yapay zekâları geçici kapatılır, süre bitince veya `/ykens stop` ile geri açılır.
- Bu kod hiç derlenmeden önce yazıldı; ilk derleme GitHub'da başarıyla geçti ama yeni eklemeler oyunda denenmedi. Hata alırsan `logs/latest.log` ya da GitHub'daki hata satırlarını yapıştır.
