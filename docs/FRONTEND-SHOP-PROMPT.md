# Промпт: кабинет магазина Green Eco Mall

> Скопируй всё, что ниже линии, в Claude Code / Cursor / AI Studio как задание для нового проекта.
> Контракт взят из бэкенда (`/Users/user/IdeaProjects/MarketPlace`) на 2026-09-21.
> Соседние проекты: `FRONTEND-CLIENT-PROMPT.md` (покупатель), `FRONTEND-ADMIN-PROMPT.md` (админка).

---

## 1. Что строим

Кабинет продавца маркетплейса **Green Eco Mall** (товары, не продукты питания; Кыргызстан, KGS).
Пользователь — **владелец магазина**: заводит товары, собирает заказы, следит за деньгами.
Не админка и не приложение покупателя.

Важно про доступ: роли `SHOP` бэкенд пока никому не выдаёт, права проверяются по владению
(магазин принадлежит пользователю из токена). Поэтому **не проверяй роль**: после входа запроси
`GET /api/shops/mine` — есть магазины, показывай кабинет; нет — экран «Зарегистрировать магазин».
У одного пользователя может быть несколько магазинов: нужен переключатель магазина в шапке,
выбранный `shopId` хранится в localStorage и подставляется во все запросы.

Язык — русский. Продавцы работают и с телефона (сборка заказов, фото товара), и с ноутбука
(массовая работа с товарами): mobile-first, но таблицы на десктопе должны быть плотными.

## 2. Стек

React 19 + TypeScript (strict) + Vite, Tailwind 4 (`@theme`), Radix + свои компоненты (cva +
tailwind-merge), Redux Toolkit + RTK Query, react-router 7, react-hook-form + zod, date-fns (ru),
lucide-react, шрифт Geist Variable, `tabular-nums` для цифр. MSW для моков (см. п.5).

## 3. Дизайн

Бренд общий: `--bg #F8F5F0`, `--surface #FFFFFF`, `--surface-muted #F0EBE0`, `--border #E5DDD0`,
`--text #1A1A1A`, `--text-muted #9B9589`, `--forest #1B2B20` (главные кнопки), `--leaf #4A7C5E`
(успех, «доставлено»), `--accent #E07840` (требует действия), `--danger #B3401F`.

**Запрещено:** фиолетовые градиенты, «стекло», эмодзи, тени `shadow-xl`, иконки в цветных кружках,
анимации на каждом элементе, тексты-заглушки вроде «Добро пожаловать в ваш магазин!».

**Нужно:**
- Главный экран — «сегодня»: новые заказы, что собрать, что зависло, сколько денег пришло.
  Продавец должен за 3 секунды понять, что от него требуется прямо сейчас.
- Деньги: показывай **свою цену** (`costPriceMinor` — сколько получит магазин). Наценку платформы
  и цену покупателя бэкенд магазину не отдаёт — не выдумывай эти поля.
- Списки: таблица на десктопе, карточки на телефоне. Статусы — текстовые бейджи со слабой заливкой.
- Каждое действие («Принять заказ», «Собрано») — одна заметная кнопка, подтверждение только там,
  где действие необратимо. После действия — тост и обновление списка.
- Скелетоны при загрузке, пустые состояния с подсказкой, что сделать.
- Даты `dd.MM.yyyy HH:mm`, деньги `1 250 сом` (значение API делить на 100).
- Доступность: тап-зоны от 44px, фокус-кольцо, контраст AA.

## 4. Экраны

1. **Регистрация магазина** (`/register-shop`) — название, реквизиты (свободный текст), адрес
   забора заказов, телефон, страна и город из справочника. После отправки — статус «На модерации»
   с пояснением, что делать дальше.
2. **Сегодня** (`/`) — плитки: новые заказы (`CREATED`/`PAID`), к сборке (`ACCEPTED`), передано
   курьеру, доставлено за сегодня; баланс кошелька и «в ожидании выплаты»; товары на модерации,
   отклонённые, с нулевым остатком; неотвеченные отзывы.
3. **Заказы** (`/orders`) — фильтр по статусу. Карточка заказа: состав, сумма к получению,
   статус доставки, курьер. Действия: «Принять» (→ `ACCEPTED`), «Собрано» (→ `ASSEMBLED`,
   после этого курьер сможет забрать). Дальнейшие статусы меняет курьер — показывай их только
   для информации.
4. **Товары** (`/products`) — список всех своих товаров с фильтром по статусу
   (черновик, на модерации, опубликован, отклонён, в архиве), остаток, своя цена, рейтинг.
   Массовых операций нет.
5. **Товар** (`/products/:id`) — форма: категория, название, описание, единица (`PCS`/`KG`/`L`),
   своя цена, валюта.
   - **Проверка текста ИИ:** перед сохранением дёрни `POST /api/products/proofread` и, если
     `changed = true`, покажи «Исправили ошибки» с подсветкой различий и кнопками «Применить» /
     «Оставить как есть». Те же правки бэкенд применяет и сам при сохранении.
   - **Остаток** — отдельным полем с кнопкой сохранения (`PATCH /products/{id}/stock`).
   - **Статусы:** черновик → «Отправить на модерацию» (`/submit`) → ждать решения админа.
     У отклонённого показывай причину (`rejectionReason`).
6. **Фото товара** (вкладка в карточке) — главный экран ИИ-функции:
   - Загрузка файла (до 10 МБ, JPEG/PNG/WebP) с камеры или диска.
   - Перед загрузкой — выбор стилей кадра из `GET /api/products/photo-styles` (чипы с подписями,
     у стилей с человеком пометка) и поле «Пожелание к кадру» (до 200 символов, например
     «девушка в бежевом пальто»). Студийная обложка делается всегда.
   - После загрузки варианты приходят в статусе `PENDING`: показывай плитки-скелетоны и опрашивай
     `GET /products/{id}/images` каждые 5 секунд, пока есть незавершённые (не дольше 3 минут).
   - Для каждого фото: бейдж типа (оригинал / стиль), «Показать покупателям» / «Скрыть»,
     «Сгенерировать заново», «Заказать ещё стиль» (из оригинала), удалить.
   - Объясни продавцу одной строкой: ИИ меняет только фон и окружение, сам товар остаётся как есть.
7. **Отзывы** (`/reviews`) — отзывы о товарах магазина, ответ магазина одним полем. Скрытые
   админом помечены. Отвечать можно один раз, повторный ответ перезаписывает предыдущий.
8. **Деньги** (`/finance`) — баланс, «в ожидании выплаты» (`heldMinor`), история проводок
   (зачисления по заказам, выплаты, возвраты), история выплат со статусами и причиной ошибки.
9. **Реквизиты** (`/finance/requisites`) — банк из `GET /api/wallet/banks` + телефон. Объясни:
   деньги уходят автоматически после оплаты заказа, но только на **одобренные админом** реквизиты;
   пока их нет, выплаты ждут в очереди.
10. **Профиль магазина** (`/settings`) — название, реквизиты, адрес забора, телефон; статус
    модерации; настройки уведомлений.

**Чего на бэкенде нет — не рисуй:** скидок и акций магазина, чата с покупателем, возвратов
глазами магазина, аналитики продаж по дням, экспорта, складов и нескольких точек.

## 5. Технические требования

- Всё через `VITE_API_URL` + `/api`. Авторизация `Bearer`, refresh с ротацией и общей очередью
  запросов; `auth.refresh_reused` или 401 на refresh — выход.
- Списки: `?page=0&size=20`, ответ `{ content, page, size, totalElements, totalPages }`.
- Деньги в минорных единицах (`...Minor`).
- **Бэкенд не развёрнут** — работай на MSW-моках (`src/mocks/`) строго по контракту п.6, состояние
  в памяти: 1 магазин, 12 товаров в разных статусах, 6 заказов, 3 отзыва, кошелёк с проводками,
  1 одобренный реквизит и 1 ожидающий. Генерация фото в моках: `PENDING` → через 6 секунд `READY`
  (подставляй заглушечную картинку). Переключатель `VITE_USE_MOCKS`.
- Ошибки — `application/problem+json` с `code`. Словарь: `catalog.product_forbidden` (это не ваш
  магазин), `catalog.shop_not_active` (магазин ещё не одобрен), `catalog.stock_insufficient`
  (остаток меньше уже зарезервированного покупателями), `catalog.image_invalid` (файл не картинка
  или превышен лимит), `order.suborder_status_invalid` (заказ уже в другом статусе),
  `finance.wallet_insufficient_funds`, `validation.failed`.

## 6. Контракт API (всё под `/api`, с токеном владельца)

### Магазин
| Метод и путь | |
|---|---|
| `POST /shops` | `{ name, legalInfo?, address?, phone?, countryId, cityId }` → `{ id }` |
| `GET /shops/mine?page=&size=` | страница `Shop` — мои магазины |
| `GET /shops/{id}` | полная карточка (владельцу и админу) |
| `PUT /shops/{id}` | `{ name, legalInfo?, address?, phone? }` → `Shop` |
| `GET /catalog/geo/countries` · `GET /catalog/geo/cities?countryId=` | справочники для формы |

`Shop = { id, ownerUserId, name, legalInfo, address, phone, countryId, cityId, status:
"DRAFT"|"MODERATION"|"ACTIVE"|"REJECTED"|"SUSPENDED", rejectionReason, rating, reviewsCount }`.

### Товары
| Метод и путь | |
|---|---|
| `GET /shops/{shopId}/products?status=&page=` | страница `Product` (все статусы) |
| `POST /shops/{shopId}/products` | `{ categoryId, name, description?, unit, costPriceMinor, currency }` → `{ id }` |
| `GET /products/{id}` · `PUT /products/{id}` | то же тело, что при создании |
| `POST /products/{id}/submit` · `POST /products/{id}/archive` | 204 |
| `PATCH /products/{id}/stock` | `{ quantity }` → 204 |
| `POST /products/proofread` | `{ name, description }` → `{ name, description, changed }` |
| `GET /catalog/categories` | дерево категорий |

`Product = { id, shopId, categoryId, name, description, unit: "PCS"|"KG"|"L", costPriceMinor,
currency, status: "DRAFT"|"MODERATION"|"PUBLISHED"|"REJECTED"|"ARCHIVED", rejectionReason, cityId,
rating, reviewsCount }`.

### Фото товара
| Метод и путь | |
|---|---|
| `GET /products/photo-styles` | `[{ code, label, description, withPeople }]` |
| `POST /products/{id}/images/upload` (multipart) | поле `file`; параметры `styles` (повторяемый, коды стилей) и `wish` → массив `Image` |
| `POST /products/{id}/images` | `{ url }` — фото по внешней ссылке, без ИИ |
| `GET /products/{id}/images` | `[Image]` — все, включая `PENDING` |
| `POST /products/{id}/images/{imageId}/generate` | `{ style, wish? }` → новый `Image` (только из оригинала) |
| `POST /products/{id}/images/{imageId}/publish` · `/unpublish` · `/regenerate` | → `Image` |
| `DELETE /products/{id}/images/{imageId}` | 204 |

`Image = { id, url, sort, kind: "ORIGINAL"|"AI", style, wish, status:
"PENDING"|"PROCESSING"|"READY"|"FAILED", published, sourceImageId, error }`.
Лимит: 30 ИИ-кадров на товар.

### Заказы магазина
| Метод и путь | |
|---|---|
| `GET /shop/suborders?shopId=&status=&page=` | страница `ShopSuborder` |
| `POST /shop/suborders/{id}/accept` · `/assemble` | 204 |

`ShopSuborder = { id, orderId, status, payoutAmountMinor, currency, courierId, createdAt,
items: [{ productId, name, qty, costPriceMinor }] }`.
Статусы: `CREATED, PAID, ACCEPTED, ASSEMBLED, HANDED_TO_COURIER, IN_TRANSIT, DELIVERED, COMPLETED,
CANCELLED, REFUNDED`. Магазин переводит только `PAID → ACCEPTED → ASSEMBLED`.

### Отзывы
| Метод и путь | |
|---|---|
| `GET /shops/{shopId}/reviews?page=` | страница `{ id, productId, shopId, rating, text, shopReply, hidden, createdAt }` |
| `POST /reviews/{reviewId}/reply` | `{ text }` → отзыв с ответом |

### Деньги
| Метод и путь | |
|---|---|
| `GET /wallet` | `[Wallet]` — кошельки моих магазинов |
| `GET /wallet/{walletId}/transactions?page=` | страница проводок |
| `GET /wallet/{walletId}/payouts?page=` | страница выплат |
| `POST /wallet/payouts` | `{ walletId, amountMinor, bankDetails: {…} }` → заявка на вывод вручную |
| `GET /wallet/banks` | `[{ code, name, minSom, maxSom }]` |
| `GET /wallet/{walletId}/requisites` · `POST /wallet/{walletId}/requisites` | `{ bank, phone }` → `Requisite` |

`Wallet = { id, ownerType, ownerRef, currency, balanceMinor, heldMinor, availableMinor }`
(`ownerRef` магазина = его `shopId`).
`Transaction = { id, direction: "CREDIT"|"DEBIT", amountMinor, currency, type: "ORDER_SETTLEMENT"|
"PAYOUT"|"REFUND"|"COMMISSION"|"COURIER_FEE", referenceType, referenceId, balanceAfterMinor, createdAt }`.
`Payout = { id, walletId, amountMinor, currency, status: "QUEUED"|"SENDING"|"PAID"|"FAILED"|
"REQUESTED"|"APPROVED"|"REJECTED", auto, attempts, lastError, createdAt, processedAt }`.
`Requisite = { id, ownerType, ownerRef, bank, bankName, phone, status: "PENDING"|"APPROVED"|"BLOCKED", createdAt }`.

### Уведомления
`GET /notifications?page=`, `GET /notifications/preferences`, `PUT /notifications/preferences`
`{ channels: { PUSH, SMS, TELEGRAM } }`.

## 7. Приёмка

- Страница `/ui` с компонентами во всех состояниях.
- На моках проходят сценарии: зарегистрировать магазин → завести товар (с проверкой текста) →
  загрузить фото и получить два ИИ-варианта → отправить на модерацию → принять и собрать заказ →
  ответить на отзыв → добавить реквизиты → посмотреть выплату.
- Скриншоты экранов «Сегодня», «Товар» с вкладкой фото и «Деньги» — на согласование до остального.
