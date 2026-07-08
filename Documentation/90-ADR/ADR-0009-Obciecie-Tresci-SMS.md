# ADR-0009 — Obcięcie treści przekraczającej limit Multipart SMS

**Status:** Accepted
**Data:** 2026-07-05

## Kontekst

20-Adapters/21-Adapter-GSM.md wymaga korzystania z Multipart SMS dla długich wiadomości (segmentacja zamiast pojedynczego, obcinanego SMS-a — bezpośrednia lekcja z wersji 1.x, gdzie `sendTextMessage()` po cichu gubiło treść przekraczającą limit). Multipart ma jednak praktyczny limit liczby segmentów (zależny od operatora/urządzenia) — treść pochodząca np. z długiego wątku e-mailowego (z cytowaną historią) może przekroczyć nawet ten limit. Żaden dokument nie rozstrzygał, co dzieje się z nadmiarową częścią: obcięcie, odrzucenie komunikatu, czy coś innego — i **kto** o tym decyduje.

## Decyzja

Odpowiedzialność za obcięcie treści przekraczającej maksymalną skonfigurowaną liczbę segmentów Multipart SMS spoczywa na **Adapterze GSM**, nie na osobnym, dedykowanym komponencie transformacji ani na Gateway Engine — zgodnie z ogólną zasadą, że mapowanie i transkodowanie treści na wymogi konkretnego transportu jest odpowiedzialnością adaptera (10-Core/12-GatewayMessage.md, §7 Payload; 91-Specification/SPEC-0006-Adapter-Contract.md).

Polityka: treść przekraczająca limit jest **obcinana z jawnym wskaźnikiem obcięcia** (np. dopisanym „…") na końcu ostatniego segmentu, a nie odrzucana w całości — dostarczenie skróconej, ale użytecznej treści jest cenniejsze niż brak dostarczenia czegokolwiek. Maksymalna liczba segmentów jest parametrem konfiguracyjnym adaptera (91-Specification/SPEC-0005-Configuration-Model.md), nie wartością zaszytą na stałe.

Zdarzenie obcięcia jest jawnie raportowane (zdarzenie domenowe, widoczne w Diagnostyce — 60-User-Interface/67-Diagnostyka.md) — obcięcie treści nigdy nie zachodzi cicho.

## Konsekwencje

- Adapter GSM implementuje dwuetapowe ograniczenie długości: segmentacja (Multipart SMS) do skonfigurowanego maksimum segmentów, a dopiero po przekroczeniu tego maksimum — obcięcie ze wskaźnikiem.
- Inne adaptery (Email, REST, WebSocket) nie mają analogicznego ograniczenia długości i nie implementują tej logiki — jest specyficzna dla transportu GSM/SMS.
- Administrator może dostroić maksymalną liczbę segmentów zgodnie z ograniczeniami konkretnego operatora (SPEC-0005-Configuration-Model.md).

## Dokumenty powiązane

- 10-Core/12-GatewayMessage.md
- 20-Adapters/21-Adapter-GSM.md
- 60-User-Interface/67-Diagnostyka.md
- 91-Specification/SPEC-0005-Configuration-Model.md
- 91-Specification/SPEC-0006-Adapter-Contract.md
