-- Ссылка на оплату от провайдера (Finik QR / mock-страница). null — провайдер ещё не ответил,
-- ссылка создаётся повторно при запросе платежа клиентом.
alter table payments add column hosted_page_url varchar(1000);
