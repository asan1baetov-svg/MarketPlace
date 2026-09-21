# Промпт: админка Green Eco Mall

> Скопируй всё, что ниже линии, в Claude Code / Cursor / AI Studio как задание для нового проекта.
> Контракт API взят из бэкенда (`/Users/user/IdeaProjects/MarketPlace`) на 2026-09-20.
> Клиентское приложение покупателя — отдельный проект, см. `FRONTEND-CLIENT-PROMPT.md`.

---

## 1. Что строим

Внутреннюю панель управления маркетплейсом **Green Eco Mall** (товары, не продукты питания;
Кыргызстан, валюта KGS). Пользователь один — **сотрудник платформы с ролью ADMIN или SUPER_ADMIN**.
Это рабочий инструмент: модерация, деньги, справочники. Не витрина и не кабинет магазина.

Язык интерфейса — русский. Десктоп в приоритете (работают с ноутбука), но таблицы и формы должны
оставаться пригодными на планшете.

## 2. Стек

- React 19 + TypeScript (strict) + Vite
- Tailwind CSS 4 (`@theme` в CSS), примитивы Radix + свои компоненты (cva + tailwind-merge)
- Redux Toolkit + **RTK Query**, react-router 7
- TanStack Table для таблиц (сортировка, пагинация с сервера), react-hook-form + zod для форм
- Recharts для графиков, date-fns (локаль ru), lucide-react
- Шрифт Geist Variable (как в остальных продуктах), цифры `tabular-nums`

## 3. Дизайн: рабочий инструмент, а не витрина

Бренд общий с клиентским приложением, но плотнее и строже.

| Токен | Цвет | Где |
|---|---|---|
| `--bg` | `#F8F5F0` | фон |
| `--surface` | `#FFFFFF` | таблицы, карточки, модалки |
| `--surface-muted` | `#F0EBE0` | шапка таблицы, полосы, фон инпутов |
| `--border` | `#E5DDD0` | границы (1px везде) |
| `--text` | `#1A1A1A` / `--text-muted` `#9B9589` | текст и подписи |
| `--forest` | `#1B2B20` | боковое меню, главные кнопки |
| `--leaf` | `#4A7C5E` | «одобрено», «оплачено», положительные суммы |
| `--accent` | `#E07840` | «ждёт решения», счётчики в меню |
| `--danger` | `#B3401F` | «отклонено», «ошибка», опасные действия |

**Запрещено:** фиолетовые градиенты, «стекло», эмодзи, тени `shadow-xl`, иконки в цветных кружках,
карточки-плитки вместо таблиц, анимации переходов между страницами, «Welcome back, Admin!».

**Нужно:**
- Постоянное боковое меню слева (свёрнутое до иконок на узком экране) с разделами из п.4 и
  счётчиками ожидающих задач (магазины, реквизиты, выплаты, возвраты, отзывы).
- Таблицы — основа интерфейса: плотные строки (высота 44px), 13–14px, шапка `--surface-muted`,
  зебры нет, разделители 1px, числа и суммы выровнены вправо, статусы — текстовые бейджи со
  слабой заливкой (без ярких пилюль). Сортировка и пагинация с сервера, размер страницы 20/50/100.
- Строка таблицы открывает боковую панель (drawer) с деталями и действиями — без ухода со страницы.
- Любое действие, меняющее деньги или статус, подтверждается модалкой с явным текстом
  («Вернуть клиенту 1 250 сом по заказу №… ?») и полем причины, когда бэкенд его принимает.
- После действия — тост с результатом и точечное обновление данных (инвалидация тега RTK Query).
- Фильтры в строке над таблицей, выбранные значения видны чипами, состояние фильтров — в URL
  (можно скинуть ссылку коллеге).
- Пустые состояния делового тона: «Нет заявок на модерацию».
- Скелетоны строк при загрузке, спиннер только в кнопках.
- Даты — `dd.MM.yyyy HH:mm`, деньги — `1 250 сом` (значение из API делить на 100).

## 4. Разделы

1. **Дашборд** (`/`) — плитки с числами: заказы за сегодня и за месяц, выручка платформы
   (комиссия), ожидают решения (магазины, товары, реквизиты, выплаты, возвраты, отзывы),
   MLM-сводка. График заказов и выручки за 30 дней.
   *Важно: сводных эндпоинтов на бэкенде пока нет, кроме MLM (`/admin/mlm/reports/summary`).
   Числа для плиток собирай из списков (`totalElements` при `size=1`), а график пока строй по
   выборке `/admin/orders`. Пометь эти места `TODO: нужен отчётный эндпоинт`.*
2. **Модерация** (`/moderation`) — единая очередь задач, вкладки со счётчиками:
   магазины (`/admin/shops?status=MODERATION`), товары (`/admin/products?status=MODERATION`),
   отзывы (`/admin/reviews?hidden=false`), реквизиты выплат (`/admin/requisites?status=PENDING`),
   курьеры (`/admin/couriers?moderation=PENDING`).
3. **Магазины** (`/shops`) — фильтр по статусу, карточка магазина: реквизиты, рейтинг, отзывы,
   кнопки одобрить / отклонить (причина) / приостановить (причина).
4. **Товары** (`/products`) — публикация и отклонение товара (причина). Карточка товара:
   фото (включая сгенерированные ИИ), цена себестоимости, рассчитанная цена продажи, остаток.
5. **Наценка** (`/markup`) — правила: уровень (товар, магазин, категория+регион, категория,
   регион, вся платформа), процент, приоритет, включено. Создание, правка, удаление. Показывай
   пример расчёта: себестоимость 1 000 сом → цена продажи с этим правилом.
6. **Категории и гео** (`/catalog`) — дерево категорий, страны, города, зоны. Осторожно с
   удалением: бэкенд отвечает 409, если есть зависимые записи, — покажи понятный текст.
7. **Заказы** (`/orders`) — фильтры: статус, город, клиент. Карточка заказа: посылки по магазинам,
   позиции, себестоимость, наценка, комиссия платформы, платёж; действия «Отменить» и «Вернуть
   деньги» с причиной.
8. **Промокоды** (`/promocodes`) — список с использованием (`usedCount` / `usageLimit`), создание
   и правка: тип (процент / фиксированная сумма), значение, кто платит за скидку (платформа или
   магазин), срок действия, лимит, включён.
9. **Финансы** (`/finance`) — вкладки:
   - **Выплаты**: фильтр по статусу (`REQUESTED` — ручные заявки, `FAILED` — неудавшиеся
     автовыплаты, `SENDING` — зависшие, `PAID`, `QUEUED`). Действия: подтвердить, отклонить,
     повторить. В строке — магазин, сумма, попытки, текст ошибки.
   - **Реквизиты**: одобрить или заблокировать реквизиты магазина (банк и телефон).
   - **Возвраты**: список ожидающих ручного возврата в кабинете Finik, кнопка «Подтвердить
     возврат» с номером операции.
   - **Кошельки**: поиск кошелька по владельцу (тип, идентификатор, валюта), включая кошельки
     платформы `PLATFORM / platform.commission` и `platform.incoming`.
10. **Курьеры** (`/couriers`) — фильтр по городу и статусу модерации, одобрение и отклонение,
    свободные курьеры города, ручное назначение доставки на курьера.
11. **MLM** (`/mlm`) — сводка, тарифы (создание, правка, реферальные проценты по уровням),
    список аккаунтов с фильтром по статусу доступа, очередь неудавшейся синхронизации с
    MLM-системой и кнопка «Повторить».
12. **Уведомления** (`/notifications`) — шаблоны (код, канал, локаль, тема, текст) с правкой,
    и лента системных уведомлений.

**Чего в админке нет и не выдумывай:** управления пользователями и ролями (этих эндпоинтов нет
наружу), редактирования чужих отзывов, ручного изменения остатков, экспорта в Excel.
Для отсутствующего ставь заглушку с пометкой `TODO` и не рисуй нерабочих кнопок.

## 5. Технические требования

- Вход: `POST /api/auth/login` (email/телефон + пароль); в dev-окружении первый админ —
  `admin@greenecomall.kg` / `admin12345` (заводится бэкендом при старте). После входа `GET /api/auth/me`:
  если в `roles` нет `ADMIN` или `SUPER_ADMIN` — выход и сообщение «Доступ только для сотрудников».
- Access-токен в памяти, refresh в `localStorage`, автоматическое обновление по 401 с одной общей
  очередью запросов; `auth.refresh_reused` или 401 на refresh — выход.
- Все запросы к `VITE_API_URL` с префиксом `/api`. Списки: `?page=0&size=20`, ответ
  `{ content, page, size, totalElements, totalPages }`.
- **Бэкенд ещё не развёрнут**: подключи MSW (`src/mocks/`) строго по контракту п.6, состояние в
  памяти, правдоподобные данные на русском (3 магазина на модерации, 12 заказов в разных статусах,
  5 выплат, 2 неудачные, 4 отзыва, 8 товаров, тарифы MLM). Переключатель `VITE_USE_MOCKS`.
- Ошибки: `application/problem+json` c полем `code` — показывай текст по коду, `detail` в консоль.
  Частые: `security.forbidden` (нет прав), `catalog.geo_has_dependents` (сначала удалите вложенные),
  `finance.payout_status_invalid` (выплата уже обработана), `validation.failed`.
- Деньги везде в минорных единицах (`...Minor`), проценты — строкой-десятичной дробью.

## 6. Контракт API (всё под `/api`, требует токен админа)

### Магазины и товары (catalog-service)
| Метод и путь | Комментарий |
|---|---|
| `GET /admin/shops?status=MODERATION\|ACTIVE\|SUSPENDED\|REJECTED\|DRAFT&page=&size=` | страница `Shop` |
| `POST /admin/shops/{id}/approve` | 204 |
| `POST /admin/shops/{id}/reject` `{reason}` | 204 |
| `POST /admin/shops/{id}/suspend` `{reason}` | 204 |
| `GET /admin/products?status=MODERATION\|DRAFT\|PUBLISHED\|REJECTED\|ARCHIVED&shopId=&cityId=&page=` | страница `Product` (очередь модерации) |
| `POST /admin/products/{id}/publish` | 204 |
| `POST /admin/products/{id}/reject` `{reason}` | 204 |
| `GET /products/{id}` | карточка товара с себестоимостью |
| `GET /products/{id}/images` | фото, включая ИИ-варианты |
| `GET /catalog/products?cityId=&shopId=&categoryId=` | витринный вид (цена продажи) |

`Shop = { id, ownerUserId, name, legalInfo, countryId, cityId, status, rejectionReason, rating, reviewsCount }`.

### Наценка, категории, гео
| Метод и путь | Тело |
|---|---|
| `GET /admin/markup-rules` | → `[MarkupRule]` |
| `POST /admin/markup-rules` | `{ scope: "PRODUCT"\|"SHOP"\|"CATEGORY_REGION"\|"CATEGORY"\|"REGION"\|"GLOBAL", categoryId?, shopId?, productId?, countryId?, cityId?, markupPercent: "20.00", priority, active }` |
| `PUT /admin/markup-rules/{id}` | `{ markupPercent, priority, active }` |
| `DELETE /admin/markup-rules/{id}` | 204 |
| `GET /catalog/categories` · `POST /admin/categories` · `PUT /admin/categories/{id}` · `DELETE /admin/categories/{id}` | `{ parentId?, name, slug, sort }` |
| `GET /catalog/geo/countries` · `POST /admin/geo/countries` · `PUT` · `DELETE` | `{ name, isoCode }` |
| `GET /catalog/geo/cities?countryId=` · `POST /admin/geo/cities` · `PUT /admin/geo/cities/{id}` · `DELETE` | `{ countryId, name, lat?, lon?, timezone? }` (в PUT без `countryId`) |
| `POST /admin/geo/zones` · `PUT /admin/geo/zones/{id}` · `DELETE` | `{ cityId, name, radiusM? }` |

Приоритет наценки при пересечении правил: PRODUCT → SHOP → CATEGORY_REGION → CATEGORY → REGION → GLOBAL.

### Отзывы
| Метод и путь | |
|---|---|
| `GET /admin/reviews?hidden=false&page=` | страница `{ id, productId, shopId, rating, text, shopReply, hidden, createdAt }` |
| `POST /admin/reviews/{id}/hide` · `POST /admin/reviews/{id}/unhide` | → сам отзыв |

### Заказы и промокоды (order-service)
| Метод и путь | |
|---|---|
| `GET /admin/orders?status=&cityId=&clientId=&page=` | страница `AdminOrder` (с себестоимостью и комиссией) |
| `POST /admin/orders/{id}/cancel` `{reason}` | 204 |
| `POST /admin/orders/{id}/refund` `{reason}` | 204, деньги возвращает finance |
| `GET /admin/promocodes` | `[Promocode]` |
| `POST /admin/promocodes` | `{ code, type: "PERCENT"\|"FIXED", value, fundedBy: "PLATFORM"\|"SHOP", validFrom, validTo, usageLimit? }` |
| `PUT /admin/promocodes/{id}` | `{ value, validFrom, validTo, usageLimit?, active }` |

`AdminOrder = { id, clientUserId, cityId, status, itemsAmountMinor, discountAmountMinor,
totalAmountMinor, currency, paymentId, createdAt, suborders: [{ id, shopId, status,
goodsAmountMinor, costAmountMinor, platformCommissionMinor, courierId, items: [{ productId, name,
qty, costPriceMinor, salePriceMinor, markupPercentSnapshot }] }] }`.
Статусы заказа: `CREATED, PAID, PARTIALLY_DELIVERED, COMPLETED, CANCELLED, PAYMENT_FAILED`.

### Финансы (finance-service)
| Метод и путь | |
|---|---|
| `GET /admin/payouts?status=REQUESTED\|QUEUED\|SENDING\|PAID\|FAILED\|APPROVED\|REJECTED&page=` | страница `Payout` |
| `POST /admin/payouts/{id}/approve` `{reason?}` | 204 — списать с кошелька (перевод сделан вручную) |
| `POST /admin/payouts/{id}/reject` `{reason?}` | 204 — вернуть сумму в доступный остаток |
| `POST /admin/payouts/{id}/retry` | 204 — повторить неудавшуюся автовыплату |
| `GET /admin/requisites?status=PENDING\|APPROVED\|BLOCKED&page=` | страница `Requisite` |
| `POST /admin/requisites/{id}/approve` · `POST /admin/requisites/{id}/block` | → сам реквизит |
| `GET /admin/refunds?status=REQUESTED\|SUCCEEDED\|FAILED&page=` | страница `Refund` |
| `POST /admin/refunds/{id}/complete` `{ reference }` | подтвердить ручной возврат |
| `GET /admin/wallets?ownerType=SHOP\|COURIER\|PLATFORM&ownerRef=&currency=KGS` | `Wallet` |

`Payout = { id, walletId, amountMinor, currency, status, requestedBy, approvedBy, createdAt,
processedAt, auto, attempts, lastError }` — `auto: true` значит автовыплата магазину после оплаты.
`Requisite = { id, ownerType, ownerRef, bank, bankName, phone, status, createdAt }`.
`Refund = { id, paymentId, amountMinor, reason, status, providerRefundId, createdAt }`.
`Wallet = { id, ownerType, ownerRef, currency, balanceMinor, heldMinor, updatedAt }`.

### Курьеры (courier-service)
| Метод и путь | |
|---|---|
| `GET /admin/couriers?cityId=&moderation=PENDING\|APPROVED\|REJECTED&page=` | страница `Courier` |
| `POST /admin/couriers/{id}/approve` · `POST /admin/couriers/{id}/reject` `{reason?}` | 204 |
| `GET /admin/couriers/available?cityId=` | `[Courier]` — свободные сейчас |
| `POST /admin/assignments` `{ suborderId, courierId }` | 204 — назначить вручную |

`Courier = { id, userId, countryId, cityId, zoneIds, status: "ACTIVE"|"BUSY"|"OFFLINE",
moderationStatus, rejectionReason, rating }`.

### MLM (mlm-service)
| Метод и путь | |
|---|---|
| `GET /admin/mlm/reports/summary` | `{ total, none, mustPurchase, active, expired, mlmRevenueMinor }` |
| `GET /admin/mlm/tariffs` · `POST` · `PUT /admin/mlm/tariffs/{id}` | `{ name, countryId?, accessPriceMinor, currency, requiredPurchaseAmountMinor, purchaseWindowDays, isDefault, active }` |
| `GET /admin/mlm/tariffs/{id}/referral-rates` · `PUT` | `[{ level, percent }]` |
| `GET /admin/mlm/accounts?status=NONE\|MUST_PURCHASE\|ACTIVE\|EXPIRED&page=` | страница аккаунтов |
| `GET /admin/mlm/sync/failed?page=` | `[{ id, type, status, attempts, nextAttemptAt, lastError, createdAt }]` |
| `POST /admin/mlm/sync/{id}/retry` | 204 |

### Уведомления (notification-service)
| Метод и путь | |
|---|---|
| `GET /admin/notification-templates` | `[{ id, code, channel: "PUSH"\|"SMS"\|"TELEGRAM", locale, subject, body }]` |
| `PUT /admin/notification-templates` | то же тело — создаёт или обновляет по коду и каналу |
| `GET /admin/notifications?page=` | системная лента |

## 7. Приёмка

- Страница `/ui` с компонентами: таблица, фильтры, drawer, модалка подтверждения, бейджи статусов,
  формы, пустые состояния, скелетоны.
- Сценарии проходят целиком на моках: одобрить магазин, опубликовать товар, создать правило
  наценки, вернуть деньги по заказу, подтвердить реквизиты и повторить упавшую выплату,
  скрыть отзыв, назначить курьера вручную.
- Скриншоты дашборда, списка заказов с открытым drawer и раздела «Финансы» — мне на согласование
  до того, как делать остальные разделы.
- Нет ни одного запрещённого приёма из раздела 3 и ни одной кнопки без рабочего эндпоинта.
