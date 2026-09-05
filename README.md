# Грин Эко Молл

Мультивендорный B2B2C маркетплейс: магазины-партнёры, внешние и MLM-клиенты, курьеры,
онлайн-оплата с распределением по кошелькам, гео-фильтрация каталога.

**Стек:** Java 21, Spring Boot 4.1.1, Spring Data JPA, Kafka, PostgreSQL, Redis, Flyway,
Docker, GitLab CI. Монорепозиторий, Maven multi-module.

## Структура

```
common/            общий код: домен, контракты Kafka, безопасность, веб-слой
services/          7 сервисов + api-gateway, каждый — самостоятельное Spring Boot приложение
infra/             docker-compose (инфра и полный прогон), mock-mlm, init БД
docs/              архитектура и задания
```

| Сервис | Порт | БД | Ответственность |
|---|---|---|---|
| api-gateway | 8080 | — | маршрутизация, валидация JWT, проброс identity |
| auth-service | 8081 | auth | учётки, роли, JWT+refresh, OTP, SSO из MLM |
| catalog-service | 8082 | catalog | магазины, товары, категории, гео, наценка, витрина |
| order-service | 8083 | orders | корзина, заказы, suborders, статусы, промокоды |
| finance-service | 8084 | finance | эквайринг, кошельки, ledger, выводы |
| courier-service | 8085 | courier | курьеры, подбор, статусы доставки |
| mlm-service | 8086 | mlm | MLM-аккаунты, активация, рефералы, интеграция с MLM-бэком |
| notification-service | 8087 | notification | push/SMS/Telegram уведомления |

## Локальный запуск

```bash
# 1. инфраструктура
docker compose -f infra/docker-compose.infra.yml up -d
#    postgres:5432  kafka:9092  kafka-ui:8085  redis:6379  mailhog:8025  mock-mlm:9100

# 2. сборка
./mvnw -T1C -DskipTests clean package

# 3. сервис из IDE или:
./mvnw -pl services/auth-service spring-boot:run

# всё в контейнерах:
./mvnw -DskipTests spring-boot:build-image
docker compose -f infra/docker-compose.yml up -d
```

## Документы

- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) — декомпозиция, схема БД, Kafka-события,
  SSO-контракт с MLM, риски и решения по открытым вопросам, порядок реализации.
- [docs/TASK-01-auth-service.md](docs/TASK-01-auth-service.md) — первое задание (auth-service).

## Статус

Скелет монорепозитория собран (`./mvnw compile` — зелёный). Бизнес-логика — по этапам из
раздела 9 архитектуры, начиная с `auth-service`.
