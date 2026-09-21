-- Адрес и телефон магазина: курьер забирает заказ по этому адресу, поддержка звонит по телефону.
alter table shops
    add column address varchar(300),
    add column phone   varchar(20);
