# ADR-0008 — Multi-tenancy: nie teraz

**Status:** Accepted
**Data:** 2026-07-05

## Kontekst

Podczas projektowania Rate Limitingu (ADR-0006-Rate-Limiting.md) pojawiło się pytanie o ograniczanie przepustowości „per tenant" — co ujawniło, że model domenowy midoMAIL 2.0 nie zawiera w ogóle pojęcia „Tenant" (wielodzierżawność). Bez jawnej decyzji temat wracałby przy każdej kolejnej funkcji (rate limiting, RBAC, Message Store, statystyki), za każdym razem od nowa.

## Decyzja

midoMAIL 2.0 **nie wspiera wielodzierżawności (multi-tenancy)**. Jedna instancja Communication Gateway odpowiada dokładnie jednemu wdrożeniu/właścicielowi (00-Foundation/03-Model-domenowy.md) — zarówno na Androidzie (jeden telefon, jeden właściciel), jak i na JVM/Linux (jedna organizacja na instancję). Model wieloużytkownikowy (60-User-Interface/70-Uzytkownicy-i-uprawnienia.md) dotyczy wielu **operatorów jednej instancji**, nie wielu **niezależnych dzierżawców** współdzielących tę samą instancję z izolacją danych.

To jest decyzja świadoma, nie przeoczenie — nie jest to zamknięcie tematu na zawsze, jest to jawne odłożenie go poza zakres wersji 2.0.

## Konsekwencje

- Rate Limiting (SPEC-0011), Message Store (SPEC-0009), Statystyki (37-Statystyki) i RBAC (70-Uzytkownicy-i-uprawnienia) operują w zakresie jednej instancji, bez wymiaru izolacji danych między dzierżawcami.
- Wdrożenie obsługujące wielu niezależnych klientów wymaga dziś **osobnych instancji Gateway** (jedna na klienta), nie jednej instancji z logicznym podziałem.
- Dodanie wielodzierżawności w przyszłości wymaga: nowego pojęcia domenowego `Tenant`, rozszerzenia GatewayMessage o `TenantId`, izolacji Message Store per tenant oraz rewizji niniejszego ADR — jest to możliwe bez przebudowy architektury (Ports & Adapters, Exactly Once i Routing nie zakładają nic sprzecznego z tym rozszerzeniem), ale nie jest planowane w obecnym zakresie.

## Dokumenty powiązane

- 00-Foundation/03-Model-domenowy.md
- 60-User-Interface/70-Uzytkownicy-i-uprawnienia.md
- 90-ADR/ADR-0006-Rate-Limiting.md
- 91-Specification/SPEC-0009-Message-Store-Contract.md
- 91-Specification/SPEC-0011-Rate-Limiting-Contract.md
