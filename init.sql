create table if not exists players
(
    id integer primary key,
    xp integer default 0,
    'level' integer default 0,
    needle integer default 0,
    name text unique,
    balance integer default 0,
    emojiStatus integer default null
);


create table if not exists cooldowns
(
    player_id integer primary key references players (id) on update cascade on delete cascade,
    find_expiration text default null,
    pockets_expiration text default null
);

create index if not exists find_ex_id on cooldowns (find_expiration);
create index if not exists pockets_ex_id on cooldowns (pockets_expiration);


create table if not exists items
(
    id integer primary key,
    name text unique not null,
    rarity text not null,
    cost integer,
    emoji text default null
);


create table if not exists shop
(
    id integer primary key,
    item_id,
    cost integer not null,
    seller_id,

    foreign key (item_id) references items (id) on update cascade,
    foreign key (seller_id) references players (id) on delete cascade
);


create table if not exists container
(
    id integer primary key,
    player_id,
    item_id,

    foreign key (player_id) references players (id) on delete cascade,
    foreign key (item_id) references items (id) on update cascade on delete cascade
);


create table if not exists shop_expiration
(
    shop_id integer references shop (id) on delete cascade on update cascade,
    exp_date text
);

create index if not exists exp_index on shop_expiration (exp_date);


insert or ignore into items (name, rarity, cost, emoji) values
Certainly! Here's the provided text in English:

- ('Shovel','Cheap',200, ''),
- ('Searchlight','Rare',7000, '🔦'),
- ('Pendant ''Nosebleed''','Rare',5000,''),
- ('Strings','Cheap',300, ''),
- ('T-shirt ''Drain''','Cheap',300, ''),
- ('Banana','Cheap',100,'🍌'),
- ('Mug ''Egypt''','Rare',5000,'☕'),
- ('Socks','Cheap',100,''),
- ('Pen','Cheap',100,''),
- ('Paint Spray Can','Common',750,''),
- ('Handkerchief','Common',150,''),
- ('Cigarette Pack','Cheap',50,''),
- ('Blue Bracelet','Common',300,''),
- ('Red Bracelet','Common',300,''),
- ('Yellow Bracelet','Common',300,''),
- ('Green Bracelet','Common',300,''),
- ('Bracelet ''Orion''','Common',1000,''),
- ('Bracelet ''Sirius''','Common',900,''),
- ('Toothbrush','Cheap',50,''),
- ('Chocolate Bar','Cheap',200,''),
- ('Backpack','Rare',7500,'🎒'),
- ('SIM Card 777','Rare',6000,''),
- ('Steel Knife','Common',600,''),
- ('Laundry Detergent','Cheap',100,''),
- ('Plush Ayanami Rei','Gift',50000,'💎'),
- ('Colored Hair Tie','Gift',17000,''),
- ('Screwdriver','Cheap',150,''),
- ('Keychain','Cheap',250,''),
- ('USB Cable','Cheap',100,''),
- ('Wine Bottle ''Cabernet Sauvignon''','Rare',5000,''),
- ('Vintage Magazine','Gift',15000,''),
- ('Necklace','Rare',3500,''),
- ('Popcorn','Rare',5000,''),
- ('Popcorn','Cheap',700,''),
- ('Lyrics ''FF''','Rare',2500,''),
- ('Evangelion Magazine','Rare',6700,''),
- ('Fishing Rod','Gift',10000,'🐟'),
- ('Crucian Carp','Common',100,''),
- ('Herring','Common',100,''),
- ('Bullhead','Common',100,''),
- ('Bottle','Cheap',5,''),
- ('Tag','Rare',5000,'📝'),
- ('Gift Case','Common',500,'📦'),
- ('Key from the Case','Rare',7000,'🔑'),
- ('Butterfly Keychain','Gift',25000,'🦋'),
- ('Seedling','Cheap',150,''),
- ('Bearing','Cheap',50,''),
- ('Watch','Gift',10000,'⌚'),
- ('Fairy','Status',7500000,'🧚‍♀'),
- ('Space Magazine','Rare',7000,''),
- ('Playboy Magazine 1/2','Gift',7000,'🍓'),
- ('Playboy Magazine 2/2','Gift',11000,'🍓'),
- ('Magazine ''Fullmetal Alchemist''','Common',1500,''),
- ('Vogue Magazine 1/5','Gift',10500,'🔮'),
- ('Vogue Magazine 2/5','Gift',3000,'🔮'),
- ('Vogue Magazine 3/5','Gift',5000,'🔮'),
- ('Vogue Magazine 4/5','Gift',9000,'🔮'),
- ('Vogue Magazine 5/5','Gift',5000,'🔮'),
- ('The Male Point Of View Magazine 1/3','Gift',15000,'🍓'),
- ('The Male Point Of View Magazine 2/3','Gift',20000,'🍓'),
- ('The Male Point Of View Magazine 3/3','Gift',23000,'🍓'),
- ('Car Magazine','Common',1500,''),
- ('Jeans','Common',1000,''),
- ('Sombrero','Common',700,''),
- ('Guitar Pick','Cheap',50,''),
- ('Bag','Cheap',35,''),
- ('Jacket','Common',850,''),
- ('Firecracker','Cheap',15,''),
- ('Notebook','Cheap',10,''),
- ('Rope','Common',320,''),
- ('Zeus's Tear','Status',150000,'⚡'),
- ('Shining Rigel','Status',100000,'💫'),
- ('Status Ayanami','Status',150000,'💎'),
- ('Recipe for Status Ayanami','Rare',9500,''),
- ('Saturn','Status',1000000,'🪐'),
- ('Soap Bubbles','Status',5000000,'🫧'),
- ('Scarlett','Status',250000,'❤'),
- ('Angel Dust','Status',450000,'🤍'),
- ('Crescent Moon','Status',250000,'🌙'),
- ('Plantera','Status',250000,'💚'),
- ('Dispersion','Status',250000,'🌈'),
- ('Voyager-1','Status',300000,'🛰'),
- ('Passion','Status',450000,'🖤'),
- ('Teddy Fred','Status',7500000,'🧸'),
- ('Forrest Gump','Status',9999,'🌳'),
- ('John Coffey','Status',9999,'☕'),
- ('Greenfield','Status',9999,'🌿'),
- ('Citizen of the World','Status',9999,'🌏'),
- ('Paws','Status',111000,'🐾'),
- ('Bee','Pet',5000000,'🐝'),
- ('Cow of God','Pet',5500000,'🐞'),
- ('Death God','Pet',7500000,'🍎'),
- ('Vamp','Pet',6666666,'🦇'),
- ('Stella','Pet',6666666,'🕷'),
- ('Pony','Pet',7500000,'🦄'),
- ('Kibo','Pet',8000000,'🦕'),
- ('Whale','Pet',8000000,'🐳'),
- ('Butterfly Day','Pet',8000000,'🦋'),
- ('UFO','Status',75000,'👽'),
- ('Nosebleed','Status',45000,'🩸'),
- ('C4','Status',14000,'🧨'),
- ('Tire','Status',12000,'🛞'),
- ('Raincoat','Status',15000,'⛱'),
- ('Hello Kitty Magazine 1/3','Rare',16100,'🔮'),
- ('Hello Kitty Magazine 2/3','Rare',15000,'🔮'),
- ('Hello Kitty Magazine 3/3','Rare',5900,'🔮'),
- ('Graffiti','Rare',9500,'🛢'),
- ('Support Status Ukraine','Status',10000,'🇺🇦'),
- ('Developer Note #101122','Status',

10000,'📄'),
- ('Blanket','Common',275,''),
- ('Mitten','Cheap',150,''),
- ('Mittens','Common',300,''),
- ('Uranium Rod','Cheap',235,''),
- ('White Monster Energy','Case',3999,'🥤'),
- ('Lithium','Cheap',85,''),
- ('Microchip','Craft',1500,'💾'),
- ('Battery','Craft',2000,'🔋'),
- ('Pink Phone','Craft',5000,'📱'),
- ('Antenna','Rare',1500,'📡'),
- ('Communication','Common',500,''),
- ('Fender Guitar','Gift',9100,'🎸'),
- ('Deck','Common',175,''),
- ('Skateboard','Craft',8500,'🛹'),
- ('Rare Trophy','Gift',19000,'🏆'),
- ('Heart in a Golden Shell','Case',19000,'💛'),
- ('Gold','Craft',8560,'🥇'),
- ('Coal','Craft',450,'🪨'),
- ('Bone','Cheap',80,''),
- ('Clover','Common',450,'☘'),
- ('Notes','Cheap',300,''),
- ('Bolt','Cheap',7,''),
- ('Wrapper','Cheap',64,''),
- ('Rare Vase','Case',85000,'🪘'),
- ('Four-Leaf Clover','Gift',16250,'🍀'),
- ('Coal','Common',450,''),
- ('Christmas Tree 2023','Status',1000000,'🎄'),
- ('XJ9','Pet',1000000,'🤖'),
- ('Bee The Warrior Game Box','Limited',0,'📟'),
- ('Cloud in a Bottle','Gift',25600,'☁'),
- ('Regular Toy Car','Limited',99000000,'🚗'),
- ('Legendary Sports Car','Limited',0,'🏎'),
- ('Rare Ring','Case',713317,'💍'),
- ('Pickaxe','Gift',11550,'⛏'),
- ('Helmet','Gift',5000,'🪖'),
- ('Anchor','Rare',14350,'⚓'),
- ('Lucky Pendant','Craft',25000,'🏵'),
- ('Deftones - Adrenaline','Gift',55000,'💽'),
- ('Titanium','Craft',1500,''),
- ('Container Key','Craft',100000,'🗝'),
- ('Vertigo','Gift',17000,'🌀'),
- ('Cipher','Craft',2000,'✉');


create table if not exists inventory
(
    id integer primary key,
    player_id,
    item_id,

    foreign key (player_id) references players (id) on delete cascade,
    foreign key (item_id) references items (id) on update cascade on delete cascade
);


create table if not exists stats
(
    player_id integer primary key references players (id) on delete cascade on update cascade,
    bonus integer not null default 0,
    coinWins integer default 0,
    coinLosses integer default 0,
    coffee integer default 0,
    tea integer default 0,
    trees integer default 0,
    capitals integer default 0,
    hideInv integer not null default 0,
    magazines integer default 0,
    totalWonMoney integer default 0,
    totalLostMoney integer default 0,
    findCounter integer default 0,
    mudCounter integer default 0,
    totalMud integer default 0,
    craftCounter integer default 0,
    duelWin integer default 0,
    duelLose integer default 0
);


drop view if exists player;
create view if not exists player as
    select
        id, name, xp, level, balance, needle, emojiStatus,
        find_expiration as FIND, pockets_expiration as POCKETS,
        coinWins as W, coinLosses as L, coffee, tea, bonus, trees, capitals,
        hideInv, magazines, totalWonMoney, totalLostMoney, findCounter, mudCounter, totalMud,
        craftCounter, duelWin, duelLose
    from
    (
        players
            left join
        cooldowns
            on id = cooldowns.player_id
    )
        left join
    stats
        on id = stats.player_id;
