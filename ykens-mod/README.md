# Ykens (Fabric, Minecraft 1.21.1)

Görünmeyen bir psikolojik korku varlığı. Gerçek bir mob yok; Ykens oyuncunun etrafında sunucu tarafında "musallat" olaylarıyla var olur.

## Ne yapar?

| Aşama | Olaylar |
|-------|---------|
| 1 | Arkandan sesler ve yaklaşan ayak sesleri, yakındaki sandıkların içeriği karışır |
| 2 | + Yakındaki köylüler tek tek kaybolur, ani yağmur başlar |
| 3 | + Yakındaki demir golemler sana saldırır, ara sıra fısıltı tabelaları (bazen ters yazılı), evinden uzaklaşıp dönünce "Bana güven, 1000 blok yürü" tabelası |

Tabelayı bıraktıktan sonra sesler sıklaşır. Tabeladan 1000 blok uzaklaşırsan önünde 3x3 bir lav çukuru ve "lava atla" tabelası belirir. Seni zorlayan bir şey yok, atlayıp atlamamak sana kalmış.

Mod kendiliğinden, oturumun 5. dakikasından sonra başlar; her 6 dakikada bir aşama yükselir (en fazla 3).

## Komutlar (op, seviye 2)

- `/ykens start` Ykens'i uyandırır (aşama 1), ev konumu = bulunduğun yer
- `/ykens stop` susturur, bu oturumda otomatik başlamaz
- `/ykens stage <1-3>` aşamayı ayarlar
- `/ykens sethome` ev konumunu bulunduğun yere alır
- `/ykens sign` "Bana güven" tabelasını hemen bırakır ve 1000 blok sayacını başlatır
- `/ykens trap` lav tuzağını hemen kurar (test için)
- `/ykens status` durumu gösterir

## Derleme

Bu klasörde Gradle wrapper yok. En kolay yol:

1. https://fabricmc.net/develop/template adresinden Minecraft 1.21.1 için bir şablon indir (Gradle wrapper'ı onunla gelir).
2. Bu klasördeki `src/` klasörünü şablondaki `src/` ile değiştir; `build.gradle`, `gradle.properties`, `settings.gradle` dosyalarını da kopyala.
3. `gradle.properties` içindeki sürümleri (`yarn_mappings`, `loader_version`, `fabric_version`) şablondakilerle eşleştir.
4. Java 21 ile `./gradlew build` çalıştır. Çıktı: `build/libs/ykens-1.0.0.jar`
5. Jar'ı `mods/` klasörüne at. Fabric Loader 0.16+ ve Fabric API gerekir.

Test için: yaratıcı bir dünya aç, `/ykens stage 3`, sonra `/ykens sign`.

## Ayarlar

`HauntManager.java` başındaki sabitler: otomatik başlama süresi, aşama süresi, yürüme mesafesi, ve her olayı açıp kapatan `true/false` bayrakları (köylü silme, lav tuzağı, hava, golem, sandık).

## Dikkat

- Köylü silme ve lav tuzağı dünyada kalıcı değişiklik yapar. Önemli bir dünyada denemeden önce yedek al.
- Ykens'in durumu bellekte tutulur; sunucu yeniden başlayınca aşama sıfırlanır.
- Bu kod henüz derlenip denenmedi. Yarn eşlemelerindeki bir isim farklıysa derleme hatası verir; hata mesajını bana yapıştır, düzelteyim.
