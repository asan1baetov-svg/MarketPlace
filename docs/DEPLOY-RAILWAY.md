# Деплой на Railway

Монорепозиторий: один сервис Railway = один модуль из `services/*`. Корневой `pom.xml` — агрегатор,
в нём нет jar, поэтому команда вида `java -jar target/*jar` из корня всегда падает
(`Unable to access jarfile`). Вместо неё — Dockerfile на каждый сервис.

## 1. Что создать в проекте Railway

Базы и очередь:
- **PostgreSQL** — можно один инстанс, но **своя база на сервис**: `auth`, `catalog`, `orders`,
  `finance`, `courier`, `mlm`, `notification` (каждый сервис держит свои таблицы и миграции).
- **Kafka** — управляемой Kafka у Railway нет. Варианты: контейнер `bitnami/kafka` отдельным
  сервисом, внешний Redpanda Cloud / Confluent Cloud. Без Kafka сервисы **стартуют**, но события
  между ними не ходят: заказ не станет оплаченным, уведомления не уйдут.

Сервисы приложения (минимум для входа и витрины): `api-gateway`, `auth-service`, `catalog-service`.
Полный набор: плюс `order-service`, `finance-service`, `courier-service`, `mlm-service`,
`notification-service`.

## 2. Настройка каждого сервиса Railway

1. Источник — этот GitHub-репозиторий, **Root Directory оставить пустым** (сборке нужен весь
   репозиторий: родительский pom и модули `common/*`).
2. Переменная `RAILWAY_DOCKERFILE_PATH` = `services/<имя-сервиса>/Dockerfile`.
3. Start command не задавать — он в Dockerfile.
4. Healthcheck path: `/actuator/health`.
5. Порт: ничего не указывать, приложение слушает `PORT`, который задаёт Railway.
6. Для общения по внутренней сети Railway (`*.railway.internal`) задать `SERVER_ADDRESS=::` —
   она работает по IPv6, а Spring по умолчанию слушает только IPv4.

## 3. Переменные окружения

Общие для всех сервисов приложения:

```
SPRING_DATASOURCE_URL=jdbc:postgresql://<host>:<port>/<db>
SPRING_DATASOURCE_USERNAME=...
SPRING_DATASOURCE_PASSWORD=...
SPRING_KAFKA_BOOTSTRAP_SERVERS=<host>:<port>
GEM_JWT_JWK_SET_URI=https://<auth-service>/oauth2/jwks
GEM_INTERNAL_TOKEN=<длинная случайная строка, одна на весь кластер>
AUTH_JWT_ISSUER=https://<auth-service>
```

`AUTH_JWT_ISSUER` должен совпадать у auth-service и у всех остальных, иначе токены не примут.

**auth-service** дополнительно:
```
ADMIN_EMAIL=<почта первого админа>
ADMIN_PASSWORD=<пароль первого админа>   # сменить после первого входа
AUTH_INTERNAL_TOKEN=<тот же GEM_INTERNAL_TOKEN>
MLM_SSO_SHARED_SECRET=<общий секрет с MLM-системой>
```

**api-gateway**:
```
GATEWAY_AUTH_URL=https://<auth-service>
GATEWAY_CATALOG_URL=https://<catalog-service>
GATEWAY_ORDER_URL=https://<order-service>
GATEWAY_FINANCE_URL=https://<finance-service>
GATEWAY_COURIER_URL=https://<courier-service>
GATEWAY_MLM_URL=https://<mlm-service>
GATEWAY_NOTIFICATION_URL=https://<notification-service>
GATEWAY_JWT_JWK_SET_URI=https://<auth-service>/oauth2/jwks
GATEWAY_CORS_ORIGINS=https://<домен фронта>,https://<домен админки>
```

**catalog-service**: `ORDER_BASE_URL`, `CATALOG_MEDIA_DIR=/data/media` (+ том), при включении ИИ —
`PHOTO_AI_PROVIDER=gemini`, `PROOFREAD_PROVIDER=gemini`, `GEMINI_API_KEY`.
**order-service**: `CATALOG_BASE_URL`.
**finance-service**: `MLM_BASE_URL`, `FINANCE_MOCK_SIMULATOR_ENABLED=false`, при подключении Finik —
`FINANCE_ACQUIRING_PROVIDER=finik`, `FINANCE_PAYOUT_PROVIDER=finik` и ключи `FINIK_*`.
**courier-service**: `ORDER_BASE_URL`, `CATALOG_BASE_URL`.
**mlm-service**: `MLM_BACKEND_BASE_URL`, `MLM_SYNC_SHARED_SECRET`.

Наружу открывают **только api-gateway**. Остальные сервисы должны ходить между собой по внутренним
адресам Railway; публичные домены им не нужны.

## 4. Проверка после деплоя

```
curl -i https://<gateway>/actuator/health
curl -i -X POST https://<gateway>/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"login":"<ADMIN_EMAIL>","password":"<ADMIN_PASSWORD>"}'
```
Ожидаем `200` и пару токенов. `502` — приложение не поднялось, смотреть Deploy Logs;
`401` — неверные данные; `404` — путь не проброшен (проверить `GATEWAY_*_URL`).

## 5. Локальная проверка образа

```
docker build -f services/auth-service/Dockerfile -t gem-auth .
docker run --rm -p 8081:8081 -e PORT=8081 \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://host.docker.internal:5432/auth \
  -e SPRING_DATASOURCE_USERNAME=gem -e SPRING_DATASOURCE_PASSWORD=gem gem-auth
```
