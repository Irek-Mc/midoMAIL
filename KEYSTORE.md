# midoMAIL — Klucz podpisywania Android

## TL;DR

Nic nie musisz robić ręcznie. `./release.sh` przy pierwszym uruchomieniu **sam
generuje** klucz podpisywania w `keys/midomail-release.jks` (jeśli go tam nie
znajdzie) i używa go do zbudowania podpisanego, instalowalnego APK
(`:platform-android`). Katalog `keys/` jest w `.gitignore` — klucz zostaje
wyłącznie na Twoim dysku, nigdy nie trafia do repozytorium.

## Dlaczego auto-generowany, a nie jeden wspólny klucz w repo?

Android wymaga, żeby APK był podpisany, żeby dało się go zainstalować, ale
sam podpis w tym projekcie (aplikacja instalowana poza Google Play, sideload)
nie pełni roli realnego zabezpieczenia — nikt go nie weryfikuje wobec żadnej
zaufanej strony trzeciej. Jego jedyna funkcja to spójność między buildami tej
samej osoby. Dlatego każdy, kto buduje midoMAIL u siebie, dostaje **własny**
klucz zamiast współdzielić jeden publiczny sekret w repo.

**Konsekwencja praktyczna:** jeśli usuniesz `keys/` i zbudujesz APK ponownie,
dostaniesz APK podpisany INNYM kluczem. Android nie pozwoli zainstalować go
jako aktualizacji istniejącej instalacji — trzeba najpierw odinstalować starą
wersję. Dopóki `keys/midomail-release.jks` istnieje, kolejne buildy używają
tego samego klucza i aktualizują się normalnie.

## Nadpisanie własnym kluczem

Jeśli chcesz użyć własnego, ręcznie wygenerowanego klucza (np. żeby wpisać
swoje dane w certyfikacie i dystrybuować APK dalej pod własną tożsamością):

```bash
keytool -genkeypair -v \
  -keystore keys/midomail-release.jks \
  -alias midomail \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -storetype PKCS12 \
  -dname "CN=Twoje Imię i Nazwisko, OU=midoMAIL, O=midoMAIL, L=Miasto, S=, C=PL"
```

`keytool` zapyta o hasło do keystore'a — zapamiętaj je, będzie potrzebne przy
każdym `release.sh`. Następnie ustaw zmienne środowiskowe przed uruchomieniem
skryptu (zamiast pozwolić mu wygenerować własny klucz):

```bash
export MIDOMAIL_KEYSTORE_FILE=keys/midomail-release.jks   # domyślna wartość, można pominąć
export MIDOMAIL_KEYSTORE_PASSWORD="hasło-które-podałeś-w-keytool"
export MIDOMAIL_KEY_ALIAS=midomail                        # domyślna wartość, można pominąć
./release.sh
```

`release.sh` wykrywa istniejący plik `keys/midomail-release.jks` i **nie**
generuje nowego — użyje Twojego. Bez ustawienia `MIDOMAIL_KEYSTORE_PASSWORD`
w tym scenariuszu build APK zakończy się błędem (Gradle nie zna hasła do
Twojego pliku) — JVM (`:platform-jvm`) i tak zbuduje się poprawnie, ta część
pipeline'u nie zależy od Androida.

## Brak Android SDK

Jeśli na maszynie nie ma zainstalowanego Android SDK (`ANDROID_SDK_ROOT`/
`local.properties`), `release.sh` pomija cały etap Androida (nie generuje
nawet klucza) i buduje wyłącznie dystrybucję `:platform-jvm`. Communication
Gateway w pełni działa bez Androida (40-Platforms/41-JVM.md,
43-Przenosnosc.md) — Android to jedna z kilku platform, nie wymóg.

## Powiązane dokumenty
- Documentation/40-Platforms/40-Android.md
- Documentation/50-Quality/52-Deployment.md
