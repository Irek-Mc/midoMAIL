midoMAIL — paczka brandingowa dla Androida
============================================

ZAWARTOŚĆ:

1) mipmap-mdpi / hdpi / xhdpi / xxhdpi / xxxhdpi
   - ic_launcher.png          -> klasyczna ikona (kwadrat)
   - ic_launcher_round.png    -> ikona okrągła
   - ic_launcher_foreground.png -> warstwa foreground do adaptive icon

   Skopiuj każdy folder mipmap-* do:
   app/src/main/res/

2) ic_launcher.xml
   Adaptive icon (Android 8+). Wklej do:
   app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml
   (skopiuj też jako ic_launcher_round.xml)

   Musisz dodać w res/values/colors.xml:
   <color name="ic_launcher_background">#1565C0</color>
   (kolor tła pod ikoną — dopasowany do niebieskiego z logo, możesz zmienić)

3) play-store/play_store_icon_512.png
   Ikona 512x512 do wgrania w Google Play Console (wymaga białego tła — już przygotowane).

4) logo/
   Transparentne PNG logo w rozmiarach 1024 / 512 / 256 / 128 px
   — do użycia w splash screenie, materiałach marketingowych, stronie www itd.

UWAGA: białe tło zostało całkowicie usunięte (przezroczystość),
zachowana została biała koperta jako element grafiki.
