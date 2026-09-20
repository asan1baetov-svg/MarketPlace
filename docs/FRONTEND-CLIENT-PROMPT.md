# Промпт: клиентское приложение Green Eco Mall

> Скопируй всё, что ниже линии, в Claude Code / Cursor / AI Studio как задание для нового проекта.
> Контракт API в разделе 6 взят из бэкенда (`/Users/user/Downloads/MarketPlace`) и совпадает с ним
> на 2026-09-19. Меняется бэкенд — обнови раздел 6.

---

## 1. Что строим

Клиентское веб-приложение (mobile-first, PWA-ready) маркетплейса **Green Eco Mall** — товары
(не продукты питания) от магазинов Кыргызстана с доставкой курьером. Это **не** админка
и **не** кабинет магазина — только покупатель:

- **Внешний клиент** — зарегистрировался сам (email или телефон).
- **Участник MLM** — пришёл из приложения GreenEcoMall (MLM) по SSO-ссылке, у него есть
  дополнительный раздел «Моя программа» (окно активации, бонусы, рефералы).

Язык интерфейса — русский и кыргызский (`ru`, `ky`), валюта — сом (KGS), всё в целых сомах.

## 2. Стек (обязательно, совпадает с MLM-клиентом — это один бренд)

- React 19 + TypeScript (strict) + Vite
- Tailwind CSS 4 (`@theme` в CSS, без tailwind.config.js)
- Примитивы Radix + свои компоненты в духе shadcn (cva + tailwind-merge) — **не** ставить готовую
  тему shadcn как есть, стилизовать под бренд (раздел 3)
- Redux Toolkit + **RTK Query** для всего API (кэш, инвалидация тегов, опрос статуса оплаты)
- react-router 7, i18next (`ru`, `ky`), motion (framer-motion) — дозированно
- Шрифт: Geist Variable (уже используется в MLM-клиенте) + Geist Mono для цифр в чеке/номерах заказа
- Иконки: lucide-react, stroke 1.75, размер 18–20 px

## 3. Дизайн: дорого, спокойно, по-человечески

### Бренд (взят из существующего MLM-клиента — не менять)

| Токен | Цвет | Где |
|---|---|---|
| `--bg` | `#F8F5F0` | фон приложения (тёплый кремовый, не белый) |
| `--surface` | `#FFFFFF` | карточки, шиты, модалки |
| `--surface-muted` | `#F0EBE0` | плашки, фон инпутов, скелетоны |
| `--border` | `#E5DDD0` | все разделители и обводки (1px) |
| `--border-strong` | `#C5BDB3` | обводка инпута в фокусе без акцента, чекбоксы |
| `--text` | `#1A1A1A` | основной текст |
| `--text-muted` | `#9B9589` | вторичный текст, подписи |
| `--forest` | `#1B2B20` | главные кнопки, шапка, акцентные заголовки |
| `--forest-2` | `#2C4A3E` | hover главных кнопок |
| `--leaf` | `#4A7C5E` | успех, «в наличии», «доставлено», звёзды рейтинга |
| `--mint` | `#EDF5F1` | фон success-плашек, выбранные фильтры |
| `--accent` | `#E07840` | цена со скидкой, бейджи «−15%», счётчик корзины — **экономно** |

Добавь `--danger` (`#C2410C`-ish, приглушённый) и `--warning` из той же тёплой гаммы.
Всё — через CSS-переменные в `@theme`, никаких «сырых» hex в компонентах.

### Как сделать, чтобы не выглядело дёшево и «сгенерировано ИИ»

Это главный критерий приёмки. **Запрещено:**
- фиолетово-синие градиенты, неоновые градиенты, «glassmorphism», blur-фоны, блобы-пятна;
- эмодзи в интерфейсе; иконки в цветных кружочках над каждым заголовком;
- «Welcome to…»/«Discover amazing products» и прочий маркетинговый текст-заглушка;
- `rounded-3xl` на всём подряд, тени `shadow-xl/2xl`, карточки, парящие над фоном;
- кнопки-«таблетки» с градиентом, текст всех заголовков по центру, одинаковые секции-карточки
  с иконкой + заголовком + двумя строчками;
- lorem ipsum и фейковые товары со стоковыми фото людей — только реальные данные API;
- анимации на каждом элементе, parallax, bounce.

**Нужно:**
- Ориентир по ощущению: Kaspi/Ozon по плотности и понятности + Aesop/Airbnb по сдержанности.
  Витрина — это сетка товаров с хорошими фото, а не лендинг.
- Скругления: 12px карточки, 10px кнопки и инпуты, 999px только чипы-фильтры. Тени почти нет:
  карточки отделяются фоном (`--surface` на `--bg`) и 1px `--border`.
- Типографика с иерархией: цена товара `text-lg font-semibold tabular-nums`, название 2 строки
  с обрезкой, подписи 12–13px `--text-muted`. Заголовки страниц 22–24px, выровнены влево.
  Числа (цены, количество, номера) — `tabular-nums`.
- Сетка 4/8 px, отступы щедрые, но экран плотный по информации. Мобильная сетка товаров — 2
  колонки, планшет — 3, десктоп — 4–5 с максимальной шириной контента 1280px.
- Фото товара — квадрат 1:1, `object-cover`, фон `--surface-muted`, lazy + blur-up из цвета.
  Нет фото — аккуратный плейсхолдер (иконка категории, не «No image»).
- Микровзаимодействия короткие (150–200 мс, ease-out): добавление в корзину — счётчик в
  таб-баре мягко «прыгает», кнопка превращается в степпер `− 2 +`. Больше анимаций не надо.
- Скелетоны вместо спиннеров для списков и карточки товара; спиннер только в кнопке.
- Пустые состояния с одной строкой по делу и действием («Корзина пуста» → «Перейти в каталог»).
- Ошибки — человеческим языком по коду ошибки (раздел 6.1), без «Something went wrong».
- Доступность: контраст AA, фокус-кольцо `--forest` 2px с offset, все кнопки-иконки с
  `aria-label`, тап-зоны от 44px, работа с клавиатуры.

### Навигация

- Мобайл: нижний таб-бар — Каталог · Корзина (счётчик) · Заказы · Профиль. Шапка: выбор
  города (чип с названием города).
- Десктоп: шапка с логотипом, городом, иконками корзины/заказов/профиля.
- Текстового поиска на бэкенде пока нет — не рисуй строку поиска; навигация по категориям и
  магазинам. Заложи место в шапке под поиск на будущее.
- Город выбирается при первом входе (bottom sheet со списком городов страны) и хранится в
  localStorage. Корзина привязана к одному городу: при смене города с непустой корзиной —
  понятное подтверждение.

## 4. Экраны и сценарии

1. **Витрина** (`/`) — категории горизонтальными чипами, сетка товаров города, бесконечная
   подгрузка по страницам. Карточка: фото, цена, название, магазин (мелко), рейтинг ★ 4.8 (132),
   кнопка «В корзину» → степпер.
2. **Категория** (`/c/:categoryId`) — то же с фильтром.
3. **Товар** (`/p/:productId`) — галерея (свайп, точки), цена, магазин со
   ссылкой, описание, липкая нижняя панель «В корзину · 1 250 сом». Блок отзывов: средняя оценка,
   распределение по звёздам (горизонтальные полосы), список с пагинацией, ответ магазина
   под отзывом (отступ, фон `--surface-muted`, подпись «Ответ магазина»), бейдж «Покупатель».
4. **Магазин** (`/s/:shopId`) — шапка магазина (название, рейтинг), его товары в городе.
5. **Корзина** (`/cart`) — позиции, сгруппированные по магазинам («Доставка от Home Decor»),
   степперы, удаление свайпом/иконкой, промокод, итог; кнопка «Оформить».
6. **Оформление** (`/checkout`) — адрес (улица, дом, квартира, подъезд, этаж, комментарий
   курьеру, телефон), промокод, итог с разбивкой (товары, скидка, к оплате). Кнопка «Оплатить
   1 250 сом» → создаёт заказ → экран оплаты.
7. **Оплата** (`/pay/:orderId`) — платёж создаётся на бэкенде асинхронно: опрашивай
   `GET /payments/by-order/{orderId}` каждые 2 с (до 30 с), пока не появится `redirectUrl`, затем
   кнопка «Оплатить через Finik» (на мобайле — переход по ссылке, на десктопе — QR по ссылке).
   После возврата с оплаты — опрашивай статус до `SUCCEEDED` (экран «Заказ оплачен», галочка,
   номер заказа) или `FAILED`/`CANCELLED` (понятное сообщение + «Попробовать снова»).
   Помни: неоплаченный заказ отменяется бэкендом через некоторое время — покажи это.
8. **Заказы** (`/orders`) и **заказ** (`/orders/:id`) — у заказа несколько посылок (по магазинам),
   у каждой свой статус. Таймлайн статуса посылки: Оплачен → Магазин собирает → Передан курьеру →
   В пути → Доставлен. Для доставленной посылки — «Оценить товары».
9. **Написать отзыв** — bottom sheet: 5 крупных звёзд (обязательно), текст (необязательно, до
   2000 символов), «Отправить». Повторная отправка правит свой отзыв; свой отзыв помечен и
   можно удалить. Если бэкенд ответил `catalog.review_not_allowed` — «Отзыв можно оставить после
   получения товара».
10. **Вход / регистрация** (`/auth`) — один экран: телефон или email → «Получить код» → 6
    ячеек кода (автофокус, вставка из буфера, таймер повторной отправки 60 с). Вход по паролю —
    вторичной ссылкой. Регистрация: контакт + пароль → код подтверждения.
11. **SSO из MLM** (`/sso?token=...`) — обменять токен и сразу на витрину, без экранов.
12. **Профиль** (`/profile`) — контакты, язык, настройки уведомлений (переключатели по каналам),
    история уведомлений, выход.
13. **Моя программа** (`/mlm`, только если в `/auth/me` есть `mlmUserId`) — статус доступа,
    прогресс-бар «куплено на X из Y сом», обратный отсчёт до дедлайна, бонусы, рефералы по
    уровням, оплата доступа по тарифу.

Гость видит витрину, товары и отзывы без входа; вход требуется при добавлении в корзину.

## 5. Технические требования

- **Токены:** access-токен — только в памяти (Redux), refresh — в `localStorage`. Один
  `baseQuery` с автоматическим refresh при 401 и **очередью** параллельных запросов (один refresh
  на всех). Refresh одноразовый (ротация): при ответе `auth.refresh_reused` или 401 на refresh —
  выход и экран входа.
- Все запросы — к `VITE_API_URL` (api-gateway), префикс `/api`.
- **Бэкенд ещё не развёрнут — приложение должно полностью работать на моках.** Подключи MSW
  (Mock Service Worker): обработчики в `src/mocks/` строго по контракту раздела 6 (те же пути,
  поля, коды ошибок и формат `{ content, page, size, totalElements, totalPages }`). Моки хранят
  состояние в памяти: корзина, заказы, отзывы меняются по-настоящему. Данные — правдоподобные
  на русском: 2 города (Бишкек, Ош), 6 категорий, 4 магазина, ~40 товаров с фото (Unsplash:
  товары для дома, аксессуары, текстиль, техника — предметы без людей, на однотонном фоне), отзывы, заказы в разных статусах. Код входа в моках — `123456`.
  Оплата в моках: `redirectUrl` появляется через 3 с, ссылка ведёт на внутреннюю страницу
  `/mock-pay/:orderId` с кнопками «Оплатить» / «Отклонить», статус платежа меняется
  соответственно. Смоделируй и ошибки: `order.stock_insufficient` для одного товара,
  `catalog.review_not_allowed` для недоставленного.
- Переключение: `VITE_USE_MOCKS=true` — моки (по умолчанию в dev), `false` — реальный
  `VITE_API_URL`. Никакого кода, завязанного на моки, вне `src/mocks/` — при переключении на
  настоящий бэкенд менять ничего не нужно.
- Деньги приходят в минорных единицах (`...Minor`, тыйыны): показывать `amountMinor / 100`
  с пробелами-разделителями тысяч и « сом»: `1 250 сом`. Никаких копеек в интерфейсе.
- Типы ответов API — вручную в `src/api/types.ts` строго по разделу 6.
- Оптимистичные обновления корзины (степпер) с откатом при ошибке.
- Код-сплиттинг по маршрутам, изображения lazy, Lighthouse mobile ≥ 90 на витрине.
- Структура: `src/app` (store, router, providers), `src/api` (RTK Query slices по сервисам),
  `src/features/*` (catalog, cart, checkout, orders, reviews, auth, profile, mlm),
  `src/ui` (дизайн-система: Button, Input, Sheet, Stepper, Rating, Price, Skeleton, EmptyState…).
- Сначала сделай `src/ui` и страницу `/ui` со всеми компонентами во всех состояниях —
  по ней принимаем дизайн, потом собираем экраны.

## 6. Контракт API (через gateway, всё под `/api`)

Авторизация: `Authorization: Bearer <accessToken>`. Списки — формат
`{ content: T[], page, size, totalElements, totalPages }`, параметры `?page=0&size=20`.

### 6.1 Ошибки

Любая ошибка — `application/problem+json`:
`{ "title", "status", "detail", "code" }`. Показывай текст по `code` из i18n, `detail` — только
в консоль. Минимальный словарь:

| code | Текст |
|---|---|
| `security.unauthorized` | Войдите, чтобы продолжить |
| `auth.credentials_invalid` | Неверный логин или пароль |
| `auth.otp_invalid`, `auth.otp_expired` | Неверный или устаревший код |
| `auth.otp_attempts_exceeded` | Слишком много попыток, запросите новый код |
| `auth.otp_too_frequent` | Код уже отправлен, подождите минуту |
| `auth.contact_taken` | Этот телефон или email уже зарегистрирован |
| `auth.user_not_active` | Подтвердите регистрацию кодом |
| `order.cart_city_mismatch` | В корзине товары другого города |
| `order.stock_insufficient` | Товара не хватает на складе |
| `order.product_unavailable` | Товар больше не продаётся |
| `order.promocode_invalid` / `order.promocode_not_found` | Промокод не действует |
| `catalog.review_not_allowed` | Отзыв можно оставить после получения товара |
| `finance.acquiring_unavailable` | Оплата временно недоступна, попробуйте через минуту |
| `validation.failed` | Проверьте поля формы |
| прочее / 5xx | Не получилось. Попробуйте ещё раз |

### 6.2 Вход

| Метод и путь | Тело → ответ |
|---|---|
| `POST /api/auth/register` | `{ email?, phone?, password, locale: "ru" }` → 202, код уже отправлен |
| `POST /api/auth/otp/request` | `{ target: "+996…"/email, purpose: "login"\|"registration" }` → 204 |
| `POST /api/auth/otp/verify` | `{ target, purpose, code }` → `TokenResponse` |
| `POST /api/auth/login` | `{ login, password }` → `TokenResponse` |
| `POST /api/auth/token/refresh` | `{ refreshToken }` → `TokenResponse` (старый refresh больше не действует) |
| `POST /api/auth/logout` | `{ refreshToken }` → 204 |
| `POST /api/auth/sso/mlm` | `{ token }` → `TokenResponse & { created: boolean }` |
| `GET /api/auth/me` | → `{ userId, email, phone, roles: string[], clientType, mlmUserId }` |

`TokenResponse = { accessToken, refreshToken, expiresIn }` (секунды).

### 6.3 Каталог (без входа)

| Метод и путь | Ответ |
|---|---|
| `GET /api/catalog/geo/countries` | `[{ id, name, isoCode }]` |
| `GET /api/catalog/geo/cities?countryId=` | `[{ id, countryId, name, lat, lon, timezone }]` |
| `GET /api/catalog/categories` | `[{ id, parentId, name, slug, sort }]` |
| `GET /api/catalog/products?cityId=&categoryId=&shopId=&page=&size=` | страница `StorefrontProduct` |
| `GET /api/catalog/products/{id}?cityId=` | `StorefrontProduct` (404, если не продаётся в городе) |
| `GET /api/catalog/shops/{id}` | `{ id, name, countryId, cityId, rating, reviewsCount }` |
| `GET /api/catalog/products/{id}/reviews?page=` | страница `Review` (с токеном — поле `mine` для своего) |
| `GET /api/catalog/products/{id}/reviews/summary` | `{ average, count, distribution: { "1": n, …, "5": n } }` |

`StorefrontProduct = { id, shopId, categoryId, name, description, unit: "PCS"|"KG"|"L",
salePriceMinor, currency, rating, reviewsCount, shopName, images: string[] }` — `images[0]`
обложка.

`Review = { id, productId, rating, text, verifiedPurchase, shopReply, repliedAt, createdAt,
updatedAt, mine }`.

### 6.4 Отзывы (с входом)

| Метод и путь | Тело → ответ |
|---|---|
| `PUT /api/products/{id}/reviews/mine` | `{ rating: 1..5, text? }` → `Review` (создать или исправить свой) |
| `DELETE /api/products/{id}/reviews/mine` | → 204 |

### 6.5 Корзина и заказы (с входом)

| Метод и путь | Тело → ответ |
|---|---|
| `GET /api/cart` | → `Cart` |
| `POST /api/cart/items` | `{ productId, cityId, qty }` → `Cart` |
| `PUT /api/cart/items/{itemId}` | `{ qty }` (0 — удалить) → `Cart` |
| `DELETE /api/cart/items/{itemId}` | → `Cart` |
| `POST /api/cart/checkout` | `{ deliveryAddress: {…любой JSON адреса}, promoCode? }` → `{ orderId, totalAmountMinor, currency }` |
| `GET /api/orders?page=` | страница `Order` |
| `GET /api/orders/{id}` | `Order` |

`Cart = { cartId, cityId, items: [{ id, productId, shopId, productName, qty, unitSalePriceMinor,
lineTotalMinor, currency }], subtotalMinor, currency }`.

`Order = { id, cityId, status, itemsAmountMinor, discountAmountMinor, totalAmountMinor, currency,
paymentId, createdAt, parcels: [{ id, shopId, status, amountMinor, items: [{ productId, name, qty,
unitPriceMinor, lineTotalMinor }] }] }`.

Статус заказа: `CREATED` (ждёт оплаты), `PAID`, `PARTIALLY_DELIVERED`, `COMPLETED`, `CANCELLED`,
`PAYMENT_FAILED`. Статус посылки: `CREATED`, `PAID`, `ACCEPTED`, `ASSEMBLED`,
`HANDED_TO_COURIER`, `IN_TRANSIT`, `DELIVERED`, `COMPLETED`, `CANCELLED`, `REFUNDED`.
Отзыв доступен, когда посылка `DELIVERED` или `COMPLETED`.

### 6.6 Оплата (с входом)

| Метод и путь | Ответ |
|---|---|
| `GET /api/payments/by-order/{orderId}` | `Payment` (404 — ещё не создан, повторить через 2 с) |
| `GET /api/payments/{id}` | `Payment` |
| `POST /api/payments/mlm-access` | `{ tariffId }` → `Payment` (только участник MLM) |

`Payment = { id, type: "ORDER"|"MLM_ACCESS", status: "PENDING"|"SUCCEEDED"|"FAILED"|"REFUNDED"|
"CANCELLED", orderId, amountMinor, currency, redirectUrl, createdAt }`. `redirectUrl` может быть
`null` несколько секунд — продолжай опрос.

### 6.7 Уведомления и профиль (с входом)

| Метод и путь | Тело → ответ |
|---|---|
| `GET /api/notifications?page=` | страница `{ id, channel, templateCode, text, status, createdAt, sentAt }` |
| `GET /api/notifications/preferences` | `{ channels: { PUSH: bool, SMS: bool, TELEGRAM: bool } }` |
| `PUT /api/notifications/preferences` | то же тело → 204 |
| `POST /api/notifications/devices` | `{ platform: "WEB"\|"IOS"\|"ANDROID", token }` → 204 |

### 6.8 MLM (с входом, только при `mlmUserId`)

| Метод и путь | Ответ |
|---|---|
| `GET /api/mlm/me` | `{ accessStatus: "NONE"\|"MUST_PURCHASE"\|"ACTIVE"\|"EXPIRED", activationDeadline, secondsToDeadline, requiredPurchaseAmountMinor, achievedPurchaseAmountMinor, remainingMinor, activatedAt, bonusBalanceMinor, currency, referralCode, … }` |
| `GET /api/mlm/me/orders` | `[{ orderId, amountMinor, currency, status, paidAt }]` |
| `GET /api/mlm/me/bonuses` | `[{ id, sourceType, level, percent, amountMinor, currency, status, createdAt }]` |
| `GET /api/mlm/me/referrals?depth=3` | `[{ level, mlmUserId, accessStatus, joinedAt }]` |

## 7. Приёмка

- Страница `/ui` со всеми компонентами во всех состояниях (default, hover, focus, disabled,
  loading, error) в светлой теме.
- Сценарий «гость → витрина → товар → вход по коду → корзина → оформление → оплата → заказ →
  отзыв» проходит на мобильной ширине 375px без горизонтального скролла.
- Скриншоты витрины, карточки товара, корзины и заказа — сначала мне на согласование, до того
  как делать остальные экраны.
- Нет ни одного запрещённого приёма из раздела 3.
