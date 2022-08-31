package main;

import ability.Cooldown;
import database.dao.*;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.io.File;
import java.io.FileNotFoundException;
import java.sql.Connection;
import java.text.SimpleDateFormat;
import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;


public class Bot extends TelegramLongPollingBot
{
	private final PlayerDAO playerDAO;
	private final InventoryDAO inventoryDAO;
	private final ItemDAO itemDAO;
	private final ShopDAO shopDAO;
	private final StatsDAO statsDAO;
	private final AbilityDAO abilityDAO;

	private static final Roller<Item> mudRoller = RollerFactory.getMudRoller(new Random());
	private static final Roller<Integer> moneyRoller = RollerFactory.getMoneyRoller(new Random());
	private static final Roller<Item> findRoller = RollerFactory.getFindRoller(new Random());
	private static final Roller<Item> fishRoller = RollerFactory.getFishRoller(new Random());
	List<Player> playersInGame;

	ScheduledFuture<?> sf_timers;
	ScheduledFuture<?> sf_find;
	private final long expStepS = 5L;
	ScheduledFuture<?> sf_pockets;
	ScheduledFuture<?> sf_dump;

	public final long findCooldown = 20L * 60L * 1000L;
	public final long pocketsCooldown = 30L * 1000L;

	private final String token;

	KeyboardPaginator paginator;

	Map<Long, Player> active_players;

	Map<Player.State, BiConsumer<Player, Message>> state_processor;
	Map<String, Consumer<Player>> command_processor;

	public Bot(Connection connection) throws FileNotFoundException
	{
		playerDAO = new PlayerDAO(connection, this);
		inventoryDAO = new InventoryDAO(connection);
		itemDAO = new ItemDAO(connection);
		shopDAO = new ShopDAO(connection, this);
		statsDAO = new StatsDAO(connection);
		abilityDAO = new AbilityDAO(connection, this);
		token = init_token();
		state_processor = BotStateProcessor.get_map(this);
		command_processor = BotCommandProcessor.get_map(this);
		active_players = new HashMap<>();
		sf_timers = STPE.stpe.scheduleAtFixedRate(this::cleanShopFromExpired, 0L, 5L, TimeUnit.SECONDS);
		sf_find = STPE.stpe.scheduleAtFixedRate(this::sendFindCooldownNotification, 0L, expStepS, TimeUnit.SECONDS);
		sf_pockets = STPE.stpe.scheduleAtFixedRate(abilityDAO::expirePockets, 0L, expStepS, TimeUnit.SECONDS);  // remove this shit
		sf_dump = STPE.stpe.scheduleAtFixedRate(this::dump_database, 1L, 1L, TimeUnit.MINUTES);
		playersInGame = new ArrayList<>();
		paginator = new KeyboardPaginator()
				.first("/find", "/pockets", "/mud", "/fish")
				.then("/me", "/inv", "/top", "/stats")
				.then("/pay", "/sell", "/shopshow", "/drop", "/coin")
				.last("/help", "/info");
	}

	public void sendMsg(Long chatId, String text)
	{
		SendMessage sendMessage = new SendMessage(chatId.toString(), text);
		sendMessage.enableMarkdown(true);

		//конкретно, на какое сообщение ответить
		//sendMessage.setReplyToMessageId(message.getMessageId());

		sendMessage.setText(text);
		try
		{
			//добавили кнопку и поместили в нее сообщение
			setButtons(sendMessage);
			execute(sendMessage);
		}
		catch (TelegramApiException e)
		{
			e.printStackTrace();
		}
	}

	public void setButtons(SendMessage sendMessage)
	{
		long id = Long.parseLong(sendMessage.getChatId());
		//Player player = playerDAO.get_by_id(id);
		Player player = active_players.get(id);
		if (player == null)
		{
			player = playerDAO.get_by_id(id);
		}
		//инициаллизация клавиатуры
		ReplyKeyboardMarkup replyKeyboardMarkup = new ReplyKeyboardMarkup();
		//установка разметки
		sendMessage.setReplyMarkup(replyKeyboardMarkup);
		//вывод клавиатуры (видел или нет)
		replyKeyboardMarkup.setSelective(true);
		replyKeyboardMarkup.setResizeKeyboard(true);
		//скрывать или не скрывать после использования
		replyKeyboardMarkup.setOneTimeKeyboard(false);

		if (player == null)
		{
			List<KeyboardRow> rows = new ArrayList<>();
			KeyboardRow row = new KeyboardRow();
			row.add(new KeyboardButton("⭐ Начать"));
			rows.add(row);
			replyKeyboardMarkup.setKeyboard(rows);
		}
		else
		{
			replyKeyboardMarkup.setKeyboard(paginator.get(player.page));
		}
		//switch (player.getState())
		//{
		//	case awaitingCommands:
		//	{
		//		keyboardFirstRow.add(new KeyboardButton("\uD83C\uDF92 Инвентарь"));
		//		keyboardSecondRow.add(new KeyboardButton("\uD83D\uDC8E Искать редкие предметы"));
		//		keyboardSecondRow.add(new KeyboardButton("\uD83D\uDD26 Рыться в грязи"));
		//		keyboardSecondRow.add(new KeyboardButton("\uD83E\uDDF6 Проверить карманы"));
		//
		//
		//		keyboardFirstRow.add(new KeyboardButton("\uD83C\uDF3A Помощь"));
		//		keyboardFirstRow.add(new KeyboardButton("⭐️ Персонаж"));
		//
		//
		//		keyboardThirdRow.add(new KeyboardButton("\uD83D\uDCB0 Монетка"));
		//		keyboardThirdRow.add(new KeyboardButton("\uD83D\uDED2 Магазин"));
		//		keyboardThirdRow.add(new KeyboardButton("\uD83D\uDCDE Скупщик"));
		//
		//		keyboardFourthRow.add(new KeyboardButton("\uD83C\uDF80 Топ 10"));
		//		keyboardFourthRow.add(new KeyboardButton("\uD83D\uDEE0 Продать Cheap"));
		//
		//
		//		keyboardFourthRow.add(new KeyboardButton("🐡 Рыбачить"));
		//		keyboardFourthRow.add(new KeyboardButton("\uD83E\uDD88 Сдать рыбу"));
		//		//keyboardFirstRow.add(new KeyboardButton("/me"));
		//		break;
		//	}
		//	case awaitingTeaNote:
		//	case awaitingCoffeeNote:
		//	case shopPlaceGood_awaitingCost:
		//	case payAwaitingAmount:
		//		keyboardFirstRow.add(new KeyboardButton("/back"));
		//	case shopBuy:
		//	case coinDash:
		//	case awaitingTea:
		//	case awaitingCoffee:
		//	case awaitingSellArguments:
		//	case awaitingChangeNickname:
		//	case shopPlaceGood_awaitingID:
		//	case payAwaitingNickname:
		//		keyboardFirstRow.add(new KeyboardButton("/cancel"));
		//		break;
		//	default:
		//		return;
		//}

		//keyboardFirstRow.add(new KeyboardButton("/find"));
		//добавили в спиок всех кнопок
	}

	@Override
	public void onUpdateReceived(Update update)
	{
		Message message = update.getMessage();

		if (message != null && message.hasText())
		{
			long id = message.getChatId();
			String text = message.getText();

			Player player;
			if (active_players.containsKey(id))
			{
				player = active_players.get(id);
			}
			else
			{
				player = playerDAO.get_by_id(id);
				active_players.put(id, player);
			}

			System.out.printf("%s: %s [from %s | %d]\n", new Date(), text, player != null ? player.getUsername() : "new player", id);


			if (player == null) {
				if (text.equals("/start") || text.equals("⭐ Начать")) {

					player = new Player(id, this);
					active_players.put(id, player);
					//statsDAO.put(player.getStats(), player.getId());
					playerDAO.put(player);
					//abilityDAO.put(id);

					sendMsg(id, "\uD83C\uDF77 Добро пожаловать в Needle");
					sendMsg(id, "Введите ник: ");
				}
				else
				{
					sendMsg(id, "⭐ Для регистрации введите команду /start");
				}
			}
			else
			{
				state_processor.get(player.getState()).accept(player, message);
			}
		}
	}

	void awaitingNickname_processor(Player player, Message message)
	{
		long player_id = player.getId();
		String username = message.getText();
		//regex для ника
		String usernameTemplate = "([А-Яа-яA-Za-z0-9]{3,32})";
		if (username.matches(usernameTemplate))
		{
			try
			{
				Player new_player = playerDAO.get_by_name(username);
				if (new_player == null)
				{
					player.setUsername(username);
					player.setState(Player.State.awaitingCommands);
					playerDAO.update(player);
					active_players.remove(player_id);
					sendMsg(player_id, "Игрок `" + player.getUsername() + "` успешно создан");
					command_help(player);
				}
				else
				{
					throw new RuntimeException("REx");
				}
			}
			catch (RuntimeException e)  // TODO change to some reasonable <? extends Exception> class
			{
				e.printStackTrace();
				sendMsg(player_id, "⚠\t Игрок с таким ником уже существует");
			}
		}
		else
		{
			sendMsg(player_id, "⚠\t Введите корректный ник: ");
		}
	}

	void awaitingSellArguments_processor(Player player, Message message)
	{
		long player_id = player.getId();
		try
		{
			Inventory inventory = player.getInventory();
			String sellID = message.getText();
			int sell_id = Integer.parseInt(sellID);
			Item item = inventory.getItem(sell_id);
			if(item.getRarity() != ItemRarity.Limited){
				player.balance.transfer(item.getCost().value);
				inventory.removeItem(sell_id);
				inventoryDAO.delete(player_id, item.getId(), 1);

				playerDAO.update(player);
					sendMsg(player_id, "✅ Предмет продан | + " + item.getCost());
				}else{
					sendMsg(player_id, "\uD83D\uDC8D Лимитированные вещи нельзя продавать");
				}


			playerDAO.update(player);
			sendMsg(player_id, "✅ Предмет продан | + " + item.getCost());
		}
		catch (NumberFormatException e) {

			e.printStackTrace();
			player.setState(Player.State.awaitingCommands);
			sendMsg(player_id, "⚠\t Пожалуйста, введите целое число");
		}
		catch (IndexOutOfBoundsException ee)
		{
			ee.printStackTrace();
			sendMsg(player_id, "⚠\t Указан неверный ID");
		}
		catch (Money.MoneyException e)
		{
			e.printStackTrace();
			sendMsg(player_id, e.getMessage());
		}
		active_players.remove(player_id);
	}

	void awaitingCommand_processor(Player player, Message message)
	{
		String text = message.getText();
		if (command_processor.containsKey(text))
		{
			command_processor.get(text).accept(player);
		}
		else
		{
			sendMsg(player.getId(), "⚠\t Неизвестная команда\n");
		}
	}

	void capitalGame_processor(Player player, Message message){
		String input = message.getText();
		long id = player.getId();

		if(input.equals("/exit")){
			for(Player p : playersInGame){
				sendMsg(p.getId(), String.format("Игрок `%s` покинул игру", player.getUsername()));
				active_players.remove(player.getId());
				playersInGame.remove(player);
			}
		}else{
			for(Player p : playersInGame){
				sendMsg(p.getId(), String.format("[Игрок] `%s`: %s", player.getUsername(), input));
			}
		}

	}


	void awaitingChangeNickname_processor(Player player, Message message)
	{
		long player_id = player.getId();
		String nickname = message.getText();
		Item tag = itemDAO.getByName("\uD83D\uDCDD Тег");
		//regex для ника
		String usernameTemplate = "([А-Яа-яA-Za-z0-9]{3,32})";
		if (nickname.matches(usernameTemplate))
		{
			try
			{
				player.setUsername(nickname);
				playerDAO.update(player);
				inventoryDAO.delete(player.getId(), tag.getId(), 1);
				sendMsg(player_id, "Ваш никнейм успешно изменен на `" + player.getUsername() + "`");
			}
			catch (RuntimeException e)
			{
				e.printStackTrace();
				sendMsg(player_id, "Игрок с таким ником уже есть");
			}
		}
		else
		{
			sendMsg(player_id, "Пожалуйста, введите корректный ник");
		}
		active_players.remove(player_id);
	}


	public SendPhoto getPhoto(String path, Player player){
		SendPhoto photo = new SendPhoto();
		photo.setPhoto(new InputFile(new File(path)));
		photo.setChatId(player.getId());
		return photo;
	}

	void awaitingTouchId_processor(Player player, Message message) {
		Map<String, SendPhoto> magazines = new HashMap<>();

		magazines.put("Вы детально рассматриваете Аянами Рей", getPhoto(".\\pics\\mag\\magazine_evangelion.jpg", player));
		magazines.put("Вы рассматриваете винтажный журнал", getPhoto(".\\pics\\mag\\magazine_vintage.jpg", player));
		magazines.put("Вы рассматриваете космический журнал", getPhoto(".\\pics\\mag\\magazine_space.jpg", player));
		magazines.put("Продолжение следует", getPhoto(".\\pics\\mag\\magazine_playboy2.jpg", player));
		magazines.put("Бегом искать филосовский камень", getPhoto(".\\pics\\mag\\magazine_fullmetal.jpg", player));
		magazines.put("Вы рассматриваете редкий журнал Vogue 1/5", getPhoto(".\\pics\\mag\\magazine_fashion.jpg", player));
		magazines.put("Вы рассматриваете журнал Vogue 2/5", getPhoto(".\\pics\\mag\\magazine_fashion2.jpg", player));
		magazines.put("Вы рассматриваете журнал Vogue 3/5", getPhoto(".\\pics\\mag\\magazine_fashion3.jpg", player));
		magazines.put("Вы рассматриваете редкий журнал Vogue 4/5", getPhoto(".\\pics\\mag\\magazine_fashion4.jpg", player));
		magazines.put("Вы рассматриваете журнал Vogue 5/5", getPhoto(".\\pics\\mag\\magazine_fashion5.jpg", player));
		magazines.put("Вы рассматриваете редкий журнал Playboy 1/2", getPhoto(".\\pics\\mag\\magazine_playboy.jpg", player));
		magazines.put("Вы рассматриваете редкий журнал Playboy 2/2", getPhoto(".\\pics\\mag\\magazine_playboy2.jpg", player));
		magazines.put("Вы рассматриваете машину с глазками", getPhoto(".\\pics\\mag\\magazine_car.jpg", player));
		magazines.put("Вы рассматриваете редкий журнал The Male Point Of View 1/3", getPhoto(".\\pics\\mag\\magazine_point.jpg", player));
		magazines.put("Вы рассматриваете редкий журнал The Male Point Of View 2/3", getPhoto(".\\pics\\mag\\magazine_point2.jpg", player));
		magazines.put("Вы рассматриваете редкий журнал The Male Point Of View 3/3", getPhoto(".\\pics\\mag\\magazine_point3.jpg", player));


		Map<Item, String> info = new HashMap<>();
		Calendar cal = Calendar.getInstance();
		int hours = cal.get(Calendar.HOUR);
		int minutes = cal.get(Calendar.MINUTE);

		List<Player> players = playerDAO.getAll();
		Random randomPlayer = new Random();
		int randomIndex = randomPlayer.nextInt(players.size());
		String anotherPlayer = players.get(randomIndex).getUsername();

		info.put(itemDAO.getByName("\uD83E\uDD8B Брелок с бабочкой"), "Красивый брелок, надеюсь не улетит...");
		info.put(itemDAO.getByName("\uD83D\uDCE6 Кейс Gift"), "Кажется, он пустой");
		info.put(itemDAO.getByName("\uD83D\uDCDD Тег"), "Можно сменить ник на `super-" + player.getUsername() + "`, чтобы быть еще круче");
		info.put(itemDAO.getByName("\uD83D\uDCC0 Whirr - Feels Like You"), "У вас в руках лучший shoegaze альбом 2019 года");
		info.put(itemDAO.getByName("\uD83C\uDF92 Рюкзак"), "Блин, места много, но сменка все равно не влазит...");
		info.put(itemDAO.getByName("\uD83D\uDC8E Плюшевая Аянами Рей"), "Такая мягкая и такая chikita...");
		info.put(itemDAO.getByName("\uD83D\uDD26 Поисковый фонарь"), "Светит ярко, особенно если в глаза");
		info.put(itemDAO.getByName("☕ Чашка 'Египет'"), "Говорят кофе из этой чашки еще вкуснее");
		info.put(itemDAO.getByName("\uD83D\uDC1FУдочка"), "Этой удочкой ловят не только рыбу, но и преступников");
		info.put(itemDAO.getByName("Текст песни 'FF'"), "FF, я уперся в потолок...");
		info.put(itemDAO.getByName("Бипки"), "Что такое бипки, кто-то знает?");
		info.put(itemDAO.getByName("Камень"), "Вы попали в голову игроку `" + anotherPlayer + "` ему не понравилось, странно...");
		info.put(itemDAO.getByName("Сим-карта 777"), "Ало, это пиццерия? Мне гавайскую");
		info.put(itemDAO.getByName("Пачка сигарет"), "Курить вредно, очень вредно...");
		info.put(itemDAO.getByName("Стиральный порошок"), "Порошок пахнет свежестью, теперь нужно понюхать стиральный");
		info.put(itemDAO.getByName("Зубная щетка"), "Щубная зетка");
		info.put(itemDAO.getByName("Цветная резинка для волос"), "Так сложно найти резинку, когда она так нужна...");
		info.put(itemDAO.getByName("Отвертка"), "Вы держите отвертку");
		info.put(itemDAO.getByName("Букет цветов"), "Пахнет хризантемами");
		info.put(itemDAO.getByName("Витаминки"), "Это были не витаминки...");
		info.put(itemDAO.getByName("Чулки"), "Слышно аромат мускуса, на фоне играет George Michael - Careless Whisper...");
		info.put(itemDAO.getByName("Чупа-чупс"), "Если долго сосать чупа-чупс, то в какой-то момент можно начать сосать палку");
		info.put(itemDAO.getByName("Ожерелье"), "Его можно подарить Вашей девушке, хотя на руке тоже ничего смотрится...");
		info.put(itemDAO.getByName("Кукурушки"), "Не хватает молока");
		info.put(itemDAO.getByName("Карась"), "Карась, кадвась, катрись...");
		info.put(itemDAO.getByName("Бычок"), "Погодите, это что окурок?!");
		info.put(itemDAO.getByName("Браслет 'Сириус'"), "Красивый браслет со звездочками");
		info.put(itemDAO.getByName("Шоколадка"), "Лучше съесть ее в сторонке, пока игрок `" + anotherPlayer + "` не видит");
		info.put(itemDAO.getByName("Стальной нож"), "Им можно порезать хлеб, остается найти кто такой Хлеб");
		info.put(itemDAO.getByName("USB провод"), "Черный и такой длиииинный");
		info.put(itemDAO.getByName("Энергетик"), "Вы делаете глоток и чувствуете как энергия течет в ваших венах");
		info.put(itemDAO.getByName("Бутылка"), "Не удалось рассмотреть бутылку, так как ее уже тестирует игрок `" + anotherPlayer + "`");
		info.put(itemDAO.getByName("Носки"), "Странно, что оба на месте");
		info.put(itemDAO.getByName("Баллончик с краской"), "Вы тегнули");
		info.put(itemDAO.getByName("Синий браслет"), "Для полной картины не хватает желтого браслета");
		info.put(itemDAO.getByName("Желтый браслет"), "Для полной картины не хватает синего браслета");
		info.put(itemDAO.getByName("Красный браслет"), "Пацаны с района с завистью смотрят на ваш красный браслет");
		info.put(itemDAO.getByName("Зеленый браслет"), "Зеленый браслет стильно смотрится на вашей руке");
		info.put(itemDAO.getByName("Браслет 'Орион'"), "Не показывайте этот браслет игроку `" +anotherPlayer + "` иначе отберет");
		info.put(itemDAO.getByName("Струны"), "Сейчас бы гитарку...");
		info.put(itemDAO.getByName("Журнал Евангелион"), "Вы детально рассматриваете Аянами Рей");
		info.put(itemDAO.getByName("Крем для рук"), "Интересно, а что если помазать ноги...");
		info.put(itemDAO.getByName("Бутылка вина 'Cabernet Sauvignon'"), "Оно просроченное");
		info.put(itemDAO.getByName("Банан"), "Если его съесть, то будет вкусно");
		info.put(itemDAO.getByName("Винтажный журнал"), "Вы рассматриваете винтажный журнал");
		info.put(itemDAO.getByName("Горбуша"), "Вы рассматриваете потенциальный ужин");
		info.put(itemDAO.getByName("\uD83D\uDD11 Ключ от кейса"), "Им можно открыть кейс или дом игрока `" + anotherPlayer  +"`");
		info.put(itemDAO.getByName("Ручка"), "Она принадлежит игроку `" + anotherPlayer + "`, возможно рядом завалялась и ношка");
		info.put(itemDAO.getByName("Крекеры"), "Хорошо подходят, чтобы попить чай или кофе с игроком `" + anotherPlayer + "`");
		info.put(itemDAO.getByName("Платок"), "Сразу видно что краденный, с какой-то бабушки сняли. Вы ужасный человек");
		info.put(itemDAO.getByName("Подвеска 'Nosebleed'"), "Если надеть ее игрок `" + anotherPlayer + "` будет в шоке");
		info.put(itemDAO.getByName("Лопата"), "Пора картошку копать");
		info.put(itemDAO.getByName("Футболка 'Drain'"), "Если эта футболка у Вас, получается `" + anotherPlayer + "` сейчас без футболки?");
		info.put(itemDAO.getByName("Бусы"), "Красивые бусы, их можно продать скупщику");
		info.put(itemDAO.getByName("Саженец"), "Саженцы можно посадить в *Лесу* и получить за это опыт или деньги");
		info.put(itemDAO.getByName("Подшипник"), "Вы искачкали руки в мазуте");
		info.put(itemDAO.getByName("⌚ Часы"), String.format("На часах %d:%d", hours, minutes));
		info.put(itemDAO.getByName("\uD83E\uDDDA\u200D♀ Фея"), "Карманная фея, ну такого `" + anotherPlayer + "` точно не видел");
		info.put(itemDAO.getByName("Космический журнал"), "Вы рассматриваете космический журнал");
		info.put(itemDAO.getByName("\uD83C\uDF53 Журнал Playboy 1/2"), "Вы рассматриваете редкий журнал Playboy 1/2");
		info.put(itemDAO.getByName("\uD83C\uDF53 Журнал Playboy 2/2"), "Вы рассматриваете редкий журнал Playboy 2/2");
		info.put(itemDAO.getByName("Журнал 'Стальной алхимик'"), "Вы держите журнал по Стальному Алхимику");
		info.put(itemDAO.getByName("\uD83D\uDD2E Журнал Vogue 1/5"), "Вы рассматриваете редкий журнал Vogue 1/5");
		info.put(itemDAO.getByName("\uD83D\uDD2E Журнал Vogue 2/5"), "Вы рассматриваете журнал Vogue 2/5");
		info.put(itemDAO.getByName("\uD83D\uDD2E Журнал Vogue 3/5"), "Вы рассматриваете журнал Vogue 3/5");
		info.put(itemDAO.getByName("\uD83D\uDD2E Журнал Vogue 4/5"), "Вы рассматриваете редкий журнал Vogue 4/5");
		info.put(itemDAO.getByName("\uD83D\uDD2E Журнал Vogue 5/5"), "Вы рассматриваете журнал Vogue 5/5");
		info.put(itemDAO.getByName("\uD83C\uDF53 Журнал The Male Point Of View 1/3"), "Вы рассматриваете редкий журнал The Male Point Of View 1/3");
		info.put(itemDAO.getByName("\uD83C\uDF53 Журнал The Male Point Of View 2/3"), "Вы рассматриваете редкий журнал The Male Point Of View 2/3");
		info.put(itemDAO.getByName("\uD83C\uDF53 Журнал The Male Point Of View 3/3"), "Вы рассматриваете редкий журнал The Male Point Of View 3/3");
		info.put(itemDAO.getByName("Автомобильный журнал"), "Вы рассматриваете машину с глазками");
		info.put(itemDAO.getByName("Джинсы"), "Плотная ткань, удобный шов, глубокие карманы");
		info.put(itemDAO.getByName("Сомбреро"), "Ваша привлекательность в этой шляпе увеличивается на 35%");
		info.put(itemDAO.getByName("Медиатор"), "Та штука, которая постоянно падает в гитару");
		info.put(itemDAO.getByName("Пакет"), "В теории, в него можно положить хлеб");
		info.put(itemDAO.getByName("Курточка"), "Очень подходит для дождливой погоды");
		info.put(itemDAO.getByName("Петарда"), "У вас в руках корсар-1");
		info.put(itemDAO.getByName("Тетрадь"), "У вас в руках крутая тетрадка с машинами и голыми девушками, пруфов не будет, поверьте мне наслово");

		long id = player.getId();
		try {
			String itemID = message.getText();
			int item_id = Integer.parseInt(itemID);
			String responseText = info.get(player.getInventory().getItem(item_id));
			if(responseText != null){
				if(magazines.containsKey(responseText))
					this.execute(magazines.get(responseText));

				sendMsg(id, "\uD83E\uDEA1 " + responseText);
			}else{
				sendMsg(id, "\uD83E\uDEA1 " + "Обычный предмет, его не интересно трогать");
			}

			playerDAO.update(player);

		} catch (NumberFormatException e) {
			e.printStackTrace();
			sendMsg(id, "⚠\t Пожалуйста, введите целое число");
		} catch (IndexOutOfBoundsException ee) {
			ee.printStackTrace();
			sendMsg(id, "⚠\t Указан неверный ID");
		} catch (TelegramApiException e) {
			e.printStackTrace();
		}
		active_players.remove(id);
	}

	void coinDash_processor(Player player, Message message) {

		List<String> text = new ArrayList<>();
		text.add("Подбрасываем монетку...");
		text.add("Молим удачу...");
		text.add("Скрещиваем пальцы...");
		text.add("Не не надеемся не на проигрыш...");
		text.add("Закрываем глаза...");
		text.add("Держим кулачки...");
		text.add("Затаиваем дыхание...");
		text.add("Верим в Бога...");
		text.add("Надеемся на выигрыш...");
		text.add("Загадываем желание...");
		text.add("Думаем о победе...");
		text.add("Надеемся на решку...");
		text.add("Надеемся на орла...");
		text.add("И выпадает...");


		long player_id = player.getId();
		String dash = message.getText();
		try
		{
			int i_dash = Integer.parseInt(dash);
			Random r = new Random();
			int ran = r.nextInt(text.size());

				if (i_dash > 0 && i_dash <= player.balance.value) {

					sendMsg(player_id, "\uD83C\uDFB0 Ваша ставка: " + new Money(i_dash));

					sendMsg(player_id, text.get(ran));

					Cooldown kd = new Cooldown(2, () -> coin_dash_callback(player, i_dash));
					kd.startCooldown();
				} else {
					sendMsg(player_id, "⚠\t У вас нет такой суммы");
				}

		}catch(NumberFormatException e)	{

				sendMsg(player_id, "⚠\tВаша ставка должна быть целым числом");
				e.printStackTrace();

		}
		active_players.remove(player_id);
	}

	void shopBuy_processor(Player player, Message message)
	{
		try
		{
			int userInput = Integer.parseInt(message.getText());

			synchronized (shopDAO)
			{
				ShopItem wanted_item = shopDAO.getByID(userInput);
				Item item = wanted_item.getItem();
				long itemCost = wanted_item.getCost().value;
				Player seller = wanted_item.getSeller();

				if (player.equals(seller))
				{
					inventoryDAO.putItem(player.getId(), item.getId());
					sendMsg(player.getId(), String.format("Ваш товар %s снят с продажи", item));
					shopDAO.delete(userInput);
				}
				else if (player.balance.value >= itemCost)
				{
					player.balance.transfer(-itemCost);
					seller.balance.transfer(itemCost);
					inventoryDAO.putItem(player.getId(), item.getId());
					sendMsg(player.getId(), String.format("\uD83C\uDF6D Предмет `%s` успешно куплен", item));
					sendMsg(seller.getId(), String.format("\uD83D\uDCC8 Ваш предмет `%s` купил игрок `%s` | + %s", item.getTitle(), player.getUsername(), new Money(itemCost)));
					seller.addXp(3);

					shopDAO.delete(userInput);
					playerDAO.update(player);
					playerDAO.update(seller);
				}
				else
				{
					sendMsg(player.getId(), "Недостаточно средств");
				}
			}
		}
		catch (NumberFormatException e)
		{
			e.printStackTrace();
			sendMsg(player.getId(), "Введите целое число");  // << звучит как приглашение, хотя стейт тут же меняется
			player.setState(Player.State.awaitingCommands);
			playerDAO.update(player);
		}
		catch (IndexOutOfBoundsException ee)
		{
			ee.printStackTrace();
			sendMsg(player.getId(), "Неверный ID");
		}
		catch (Money.MoneyException e)
		{
			e.printStackTrace();
			sendMsg(player.getId(), e.getMessage());
		}
		finally
		{
			active_players.remove(player.getId());
		}
	}


	void shopPlaceGood_awaitingID_processor(Player player, Message message)
	{
		try
		{

			long id = player.getId();
			int itemID = Integer.parseInt(message.getText());
			if (itemID >= player.getInventory().getInvSize())
			{
				throw new IndexOutOfBoundsException();
			}
			else if (player.getInventory().getInvSize() > 20)
			{
				throw new BackpackException(itemID);
			}
			player.to_place_item = itemID;
			sendMsg(player.getId(), "Введите стоимость товара: ");
			player.setState(Player.State.shopPlaceGood_awaitingCost);

			//playerDAO.update(player);
		}
		catch (NumberFormatException e)
		{
			e.printStackTrace();
			sendMsg(player.getId(), "Введите целое число");
			player.setState(Player.State.awaitingCommands);
			playerDAO.update(player);
		}
		catch (IndexOutOfBoundsException ee)
		{
			ee.printStackTrace();
			sendMsg(player.getId(), "Неверный ID");
			player.setState(Player.State.awaitingCommands);
			playerDAO.update(player);
		}
		catch (BackpackException e)
		{
			long id = player.getId();
			Item ii = player.getInventory().getItem(e.backpackID);
			Item backpack = itemDAO.getByName("\uD83C\uDF92 Рюкзак");
			if (ii.equals(backpack))
			{
				sendMsg(id, String.format("Избавьтесь от дополнительных слотов, прежде чем продать `%s`", backpack.getTitle()));
				player.setState(Player.State.awaitingCommands);
				playerDAO.update(player);
				active_players.remove(id);
			}
			else
			{
				player.to_place_item = e.backpackID;
				sendMsg(player.getId(), "Введите стоимость товара: ");
				player.setState(Player.State.shopPlaceGood_awaitingCost);
			}
		}
	}


	void shopPlaceGood_awaitingCost_processor(Player player, Message message)
	{
		long player_id = player.getId();
		try
		{
			int cost = Integer.parseInt(message.getText());
			if (cost > 0)
			{
				Inventory inventory = player.getInventory();
				ShopItem shopItem = new ShopItem(inventory.getItem(player.to_place_item), cost, player);
				shopDAO.put(shopItem);

				sendMsg(player_id, String.format("Товар `%s` выставлен на продажу", inventory.getItem(player.to_place_item).getTitle()));
				inventory.removeItem(player.to_place_item);

				inventoryDAO.delete(player_id, shopItem.getItem().getId(), 1);
				player.setState(Player.State.awaitingCommands);
				playerDAO.update(player);
			}
			else
			{
				sendMsg(player_id, "Сумма не может быть нулем");
			}
		}
		catch (NumberFormatException e)
		{
			e.printStackTrace();
			sendMsg(player_id, "⚠\t Пожалуйста, введите целое число");
		}
		active_players.remove(player_id);
	}

	public void checkAwaitingNickname_processor(Player player, Message message){
		long player_id = player.getId();
		String nickname = message.getText();

		Player anotherPlayer = playerDAO.get_by_name(nickname);

		if (anotherPlayer != null){
			Inventory inventory = anotherPlayer.getInventory();
			if (inventory.getInvSize() != 0)
			{
				StringBuilder sb = new StringBuilder("\uD83C\uDF81\t Инвентарь игрока `" + anotherPlayer.getUsername() + "`: ");
				sb.append("\n");
				sb.append("========================\n");
				for (int i = 0; i < inventory.getInvSize(); i++)
				{
					sb.append(String.format("Предмет #[%d] : %s\n", i, inventory.getItem(i).toString()));
				}
				sb.append("========================\n");
				//sendMsg(message, "\u26BD");
				sb.append("\uD83C\uDF81\t Всего предметов: ").append(inventory.getInvSize());
				sendMsg(player_id, sb.toString());
			}
			else
			{
				sendMsg(player_id, "\uD83C\uDF81\t Инвентарь `" + nickname + "` пуст\n");
			}
		} else {
				sendMsg(player_id, "Такого игрока не существует");
		}

		active_players.remove(player.getId());

	}



	public void payAwaitingNickname_processor(Player player, Message message)
	{
		long player_id = player.getId();
		String nickname = message.getText();

		if (!nickname.equals(player.getUsername()))
		{
			Player acceptor = playerDAO.get_by_name(nickname);
			if (acceptor != null)
			{
				player.payment_acceptor = acceptor;
				player.setState(Player.State.payAwaitingAmount);
				sendMsg(player_id, "\uD83D\uDCB3 Введите сумму: ");
			}
			else
			{
				sendMsg(player_id, "Такого игрока не существует");
				player.setState(Player.State.awaitingCommands);
			}
		}
		else
		{
			sendMsg(player_id, String.format("\uD83C\uDF38 Игрок `%s` очень богат и не нуждается в Ваших копейках", player.getUsername()));
		}
	}

	public void payAwaitingAmount_processor(Player player, Message message)
	{
		try
		{
			long cost = Long.parseLong(message.getText());
			if (cost > player.balance.value || cost <= 0)
			{
				sendMsg(player.getId(), "⚠\t Некорректная сумма");
			}
			else
			{
				Player receiver = player.payment_acceptor;
				player.balance.transfer(-cost);
				receiver.balance.transfer(cost);
				sendMsg(receiver.getId(), String.format("\uD83D\uDCB3 Вам начислено %s | Отправитель: `%s` ", new Money(cost), player.getUsername()));
				sendMsg(player.getId(), "✅ Деньги отправлены");
				player.setState(Player.State.awaitingCommands);
				player.payment_acceptor = null;
				playerDAO.update(receiver);
				playerDAO.update(player);
			}
		}
		catch (NumberFormatException e)
		{
			e.printStackTrace();
			sendMsg(player.getId(), "⚠\t Вы ввели некорректную сумму");
		}
		catch (Money.MoneyException ex)
		{
			ex.printStackTrace();
			sendMsg(player.getId(), ex.getMessage());
		}
		active_players.remove(player.getId());
	}

	public void awaitingCoffee_processor(Player player, Message message)
	{
		long player_id = player.getId();
		String nickname = message.getText();

		if (!nickname.equals(player.getUsername()))
		{
			Player acceptor = playerDAO.get_by_name(nickname);
			if (acceptor != null)
			{
				player.coffee_acceptor = acceptor;
				sendMsg(player_id, "Введите сообщение для игрока (48 символов): ");
				player.setState(Player.State.awaitingCoffeeNote);
			}
			else
			{
				sendMsg(player_id, "Такого игрока не существует");
				player.setState(Player.State.awaitingCommands);
			}
		}
		else
		{
			sendMsg(player_id, "\uD83C\uDF38 Кофе можно отправлять только другим игрокам");
		}
	}


	public void awaitingCoffeeNote_processor(Player player, Message message)
	{
		int goal;
		Item cup = itemDAO.getByName("☕ Чашка 'Египет'");
		if (player.getInventory().getItems().contains(cup))
		{
			goal = 200;
		}
		else
		{
			goal = 500;
		}

		try
		{
			String note = message.getText();
			if (note.length() < 48)
			{
				Player receiver = player.coffee_acceptor;

				player.balance.transfer(-goal);

				receiver.stats.coffee++;
				//statsDAO.update(receiver.getStats(), receiver.getId());
				sendMsg(player.getId(), "☕ Кофе отправлен");
				player.addXp(1);
				sendMsg(receiver.getId(), String.format("☕ Игрок `%s` угостил вас кружечкой кофе с сообщением: `%s`", player.getUsername(), note));
				//statsDAO.update(receiver.getStats(), receiver.getId());
				playerDAO.update(player);
				playerDAO.update(receiver);
				player.setState(Player.State.awaitingCommands);
			}
			else
			{
				sendMsg(player.getId(), "Сообщение больше, чем 48 символов");
			}
		}
		catch (NumberFormatException e)
		{
			e.printStackTrace();
			sendMsg(player.getId(), "⚠\t Некорректное сообщение");
		}
		catch (Money.MoneyException ex)
		{
			ex.printStackTrace();
			sendMsg(player.getId(), ex.getMessage());
		}
		active_players.remove(player.getId());
	}

	public void awaitingTea_processor(Player player, Message message)
	{
		long player_id = player.getId();
		String nickname = message.getText();

		if (!nickname.equals(player.getUsername()))
		{
			Player acceptor = playerDAO.get_by_name(nickname);
			if (acceptor != null)
			{
				player.tea_acceptor = acceptor;
				sendMsg(player_id, "Введите сообщение для игрока (48 символов): ");
				player.setState(Player.State.awaitingTeaNote);
			}
			else
			{
				sendMsg(player_id, "Такого игрока не существует");
				player.setState(Player.State.awaitingCommands);
			}
		}
		else
		{
			sendMsg(player_id, "\uD83C\uDF38 Чай можно отправлять только другим игрокам");
		}
	}

	public void awaitingTeaNote_processor(Player player, Message message)
	{
		int goal;
		Item cup = itemDAO.getByName("☕ Чашка 'Египет'");
		if (player.getInventory().getItems().contains(cup))
		{
			goal = 200;
		}
		else
		{
			goal = 500;
		}

		try
		{
			String note = message.getText();
			if (note.length() < 48)
			{
				Player receiver = player.tea_acceptor;

				player.balance.transfer(-goal);

				receiver.stats.tea++;
				//statsDAO.update(receiver.getStats(), receiver.getId());
				sendMsg(player.getId(), "\uD83C\uDF3F Чай отправлен");
				player.addXp(1);
				sendMsg(receiver.getId(), String.format("\uD83C\uDF3F Игрок `%s` угостил вас кружечкой чая с сообщением: `%s`", player.getUsername(), note));
				//statsDAO.update(receiver.getStats(), receiver.getId());
				playerDAO.update(player);
				playerDAO.update(receiver);
				player.setState(Player.State.awaitingCommands);
			}
			else
			{
				sendMsg(player.getId(), "Сообщение больше, чем 48 символов");
			}
		}
		catch (NumberFormatException e)
		{
			e.printStackTrace();
			sendMsg(player.getId(), "⚠\t Некорректное сообщение");
		}
		catch (Money.MoneyException ex)
		{
			ex.printStackTrace();
			sendMsg(player.getId(), ex.getMessage());
		}
		active_players.remove(player.getId());
	}


	public void command_help(Player player)
	{

		//playerDAO.update(player);

		sendMsg(player.getId(), "\\[`Needle`] Бот содержит следующие команды: \n" +
				"\n" +
				" \\[Команды поиска] \n" +
				"\uD83D\uDD0D /find - искать редкие предметы \n" +
				"\uD83D\uDD0D /pockets - проверить карманы \n" +
				"\uD83D\uDD0D /mud - рыться в грязи \n" +
				"\n" +
				" \\[Команды магазина] \n" +
				"\uD83D\uDD0D /shopshow - посмотреть магазин \n" +
				"\uD83D\uDD0D /shopplace - продать предмет \n" +
				"\uD83D\uDD0D /shopbuy - купить предмет \n" +
				"\n" +
				" \\[Команды игрока] \n" +
				"\uD83D\uDCC3 /inv - открыть инвентарь \n" +
				"\uD83D\uDCB0 /sell - продать скупщику\n" +
				"\uD83D\uDCB3 /balance - проверить баланс  \n" +
				"\uD83D\uDCB0 /pay - переслать деньги \n" +
				"\uD83D\uDC80 /changenickname - сменить никнейм \n" +
				"⭐ /me - ифнормация о персонаже \n" +
				"\n" +
				" \\[Общие команы] \n" +
				"\uD83D\uDCE9 /help - список всех команд \n" +
				"ℹ /info - информация об игре \n" +
				"\uD83C\uDF80 /top - посмотреть рейтинг игроков \n" +
				"\uD83D\uDCB9 /stats - онлайн игроков \n" +
				"\n" +
				" \\[Развлечения] \n" +
				"\uD83C\uDFB0 /coin - сыграть в Монетку \n" +
				"\uD83C\uDF3F /tea - отправить чай \n" +
				"☕️ /coffee - отправить кофе \n" +
				"\uD83D\uDD11 /case - открывать кейсы \n" +
				"\n" +
				" \\[Локации] \n" +

				"\uD83C\uDF33 /forest - посетить Лес \n" +

				"\uD83D\uDC21 /fish - пойти на рыбалку \n"


		);
	}

	public void command_forest(Player player)
	{


		Random r = new Random();
		boolean success = r.nextBoolean();
		long fee = r.nextInt(3500);
		try
		{
			Item i = itemDAO.getByName("\uD83D\uDD26 Поисковый фонарь");
			Item j = itemDAO.getByName("Саженец");
			if (player.getInventory().getItems().contains(i))
			{
				if(player.getInventory().getItems().contains(j)){
					if(success == true){
						sendMsg(player.getId(), "\uD83C\uDF33 Вы посадили саженец, природа это оценила | +$" + fee);
						player.getMoney().transfer(fee);
						player.addXp(1);
						inventoryDAO.delete(player.getId(), j.getId(), 1);

					}else{
						sendMsg(player.getId(), "\uD83C\uDF33 Вы посадили саженец");
						player.addXp(2);
					}
					inventoryDAO.delete(player.getId(), j.getId(), 1);
					player.stats.trees++;
					playerDAO.update(player);

				}else{
					sendMsg(player.getId(), "У вас нет саженцов");

				}

			}
			else
			{
				sendMsg(player.getId(), String.format("Для похода в лес вам нужен предмет `%s` \n\uD83D\uDED2 Его можно купить у других игроков в магазине", i.getTitle()));
			}
		}
		catch (RuntimeException | Money.MoneyException e)
		{
			e.printStackTrace();
		}
	}

	public void command_fish(Player player)
	{
		//Item i = new Item(46, "Удочка", ItemRarity.Rare, 5000);
		Item i = itemDAO.getByName("\uD83D\uDC1FУдочка");
		int limitSpace;
		Item backpack = itemDAO.getByName("\uD83C\uDF92 Рюкзак");

		if (player.getInventory().getItems().contains(backpack))
		{
			limitSpace = 25;
		}
		else
		{
			limitSpace = 20;
		}


		if (player.getLevel() >= 5)
		{
			if (player.getInventory().getItems().contains(i))
			{
				if (player.getInventory().getInvSize() < limitSpace)
				{
					Item item = fishRoller.roll();

					if (item != null)
					{
						inventoryDAO.putItem(player.getId(), item.getId());
						playerDAO.update(player);
						sendMsg(player.getId(), String.format("Вы поймали %s", item));
						player.addXp(1);
					}
					else
					{
						sendMsg(player.getId(), "Не клюет");
					}
				}
				else
				{
					sendMsg(player.getId(), "⚠ В вашем инвентаре нет места");
				}
			}
			else
			{
				sendMsg(player.getId(), String.format("Для похода на рыбалку вам нужен предмет `%s` \n\uD83D\uDED2 Его можно купить у других игроков в магазине или найти", i.getTitle()));
			}
		}
		else
		{
			sendMsg(player.getId(), "\uD83D\uDC7E Для похода на рыбалку вам нужен 5 уровень");
		}
	}


	public void command_sellfish(Player player)
	{
		long id = player.getId();


		LocalTime open = LocalTime.of(10, 0);
		LocalTime close = LocalTime.of(15, 0);

		LocalTime currentTime = LocalTime.now();

		if (currentTime.isBefore(open) || currentTime.isAfter(close))
		{
			sendMsg(id, "\uD83E\uDD88 Рыбная лавка работает с 10:00 до 15:00\n\nСдавая рыбу в лавке, Вы можете получить " +
					"в несколько раз больше выручки, чем если бы сдавали ее \uD83D\uDCDE Скупщику");
		}
		else
		{
			List<String> fish_titles = new ArrayList<>();
			fish_titles.add("Горбуша");
			fish_titles.add("Бычок");
			fish_titles.add("Карась");
			int fee = 0;
			for (int i = 0; i < player.getInventory().getItems().size(); i++)
			{
				Item fish = player.getInventory().getItem(i);
				if (fish_titles.contains(fish.getTitle()))
				{
					fee += fish.getCost().value * 7;
					inventoryDAO.delete(player, fish.getId(), 1);
				}
			}
			if (fee > 0)
			{
				sendMsg(id, String.format("\uD83E\uDD88 Покупатель выложил за всю рыбу %s", new Money(fee)));
				try
				{
					player.balance.transfer(fee);
				}
				catch (Money.MoneyException e)
				{
					e.printStackTrace();
					sendMsg(player.getId(), e.getMessage());
				}
				playerDAO.update(player);
			}
			else
			{
				sendMsg(id, "\uD83E\uDD88У вас нет рыбы\nЧтобы ловить рыбу, введите /fish");
			}
		}
	}


	public void command_drop(Player player)
	{

		long id = player.getId();
		int fee = 0;

		for (int i = 0; i < player.getInventory().getItems().size(); i++)
		{
			Item cheapItem = player.getInventory().getItem(i);
			if (cheapItem.getRarity() == ItemRarity.Cheap)
			{
				fee += cheapItem.getCost().value;
				inventoryDAO.delete(player, cheapItem.getId(), 1);
			}
		}

		if (fee > 0)
		{
			sendMsg(id, String.format("\uD83D\uDCB3 Вы продали все дешевые вещи за %s", new Money(fee)));
			try
			{
				player.balance.transfer(fee);
			}
			catch (Money.MoneyException e)
			{
				e.printStackTrace();
				sendMsg(id, e.getMessage());
			}
			playerDAO.update(player);
		}
		else
		{
			sendMsg(id, "У вас нет дешевых вещей");
		}
	}

	//SUPER SECRET BONUS
	public void command_bonus(Player player)
	{
		long id = player.getId();
		if (player.getStats().bonus == 0)
		{
			sendMsg(id, "\uD83C\uDF3A Вы получили бонус | +" + new Money(15000L));
			try
			{
				player.balance.transfer(15000);
			}
			catch (Money.MoneyException e)
			{
				e.printStackTrace();
				sendMsg(id, e.getMessage());
			}
			player.stats.bonus++;
			playerDAO.update(player);
			//statsDAO.update(player.getStats(), player.getId());
		}
		else
		{
			sendMsg(id, "Вы уже получили свой бонус");
		}
	}

	public void command_inv(Player player)
	{
		int limitSpace;
		Item backpack = itemDAO.getByName("\uD83C\uDF92 Рюкзак");
		if (player.getInventory().getItems().contains(backpack))
		{
			limitSpace = 25;
		}
		else
		{
			limitSpace = 20;
		}


		long player_id = player.getId();
		Inventory inventory = player.getInventory();
		if (inventory.getInvSize() != 0)
		{
			StringBuilder sb = new StringBuilder("\uD83C\uDF81\t Ваш инвентарь: ");
			sb.append("\n");
			sb.append("========================\n");
			for (int i = 0; i < inventory.getInvSize(); i++)
			{
				sb.append(String.format("Предмет #[%d] : %s\n", i, inventory.getItem(i).toString()));
			}
			sb.append("========================\n");
			//sendMsg(message, "\u26BD");
			sb.append("\uD83C\uDF81\t Всего предметов: ").append(inventory.getInvSize()).append("/").append(limitSpace);
			sendMsg(player_id, sb.toString());
		}
		else
		{
			sendMsg(player_id, "\uD83C\uDF81\t Ваш инвентарь пуст ");
		}
	}

	public void command_find(Player player)
	{
		int limitSpace;
		Item backpack = itemDAO.getByName("\uD83C\uDF92 Рюкзак");
		if (player.getInventory().getItems().contains(backpack))
		{
			limitSpace = 25;
		}
		else
		{
			limitSpace = 20;
		}


		long player_id = player.getId();
		long now_ts = System.currentTimeMillis();
		if (player.getInventory().getInvSize() < limitSpace)
		{
			if (player.findExpiration != null && player.findExpiration > now_ts)
			{
				sendMsg(player_id, String.format("\u231B Время ожидания: %s",
						PrettyDate.prettify(player.findExpiration - now_ts, TimeUnit.MILLISECONDS)));
			}
			else
			{
				Item new_item = findRoller.roll();
				inventoryDAO.putItem(player_id, new_item.getId());
				sendMsg(player_id, String.format("\uD83C\uDF81\t Вы нашли: %s", new_item));
				player.addXp(5);
				player.findExpiration = now_ts + findCooldown;

				playerDAO.update(player);
				//abilityDAO.updateFind(player_id, now_ts + cooldownMs);
			}
		}
		else
		{
			sendMsg(player_id, "⚠ В вашем инвентаре нет места");
		}
	}


	public void command_mud(Player player)
	{
		int limitSpace;
		Item backpack = itemDAO.getByName("\uD83C\uDF92 Рюкзак");
		if (player.getInventory().getItems().contains(backpack))
		{
			limitSpace = 25;
		}
		else
		{
			limitSpace = 20;
		}


		long id = player.getId();
		if (player.getInventory().getInvSize() < limitSpace)
		{
			Item item = mudRoller.roll();
			if (item != null)
			{
				inventoryDAO.putItem(id, item.getId());
				sendMsg(id, String.format("Вы нашли в грязи %s", item));
				player.addXp(1);
				playerDAO.update(player);
			}
			else
			{
				sendMsg(id, "Вы ничего не нашли");
			}
		}
		else
		{
			sendMsg(id, "⚠ В вашем инвентаре нет места");
		}
	}


	public void command_pockets(Player player)
	{
		long player_id = player.getId();
		long now_ts = System.currentTimeMillis();
		long cooldownMs = pocketsCooldown;
		if (player.pocketsExpiration != null && player.pocketsExpiration > now_ts)
		{
			sendMsg(player_id, String.format("\u231B Время ожидания: %s",
					PrettyDate.prettify(player.pocketsExpiration - now_ts, TimeUnit.MILLISECONDS)));
		}
		else
		{
			int money = moneyRoller.roll();
			if (money > 0)
			{
				sendMsg(player_id, String.format("Вы пошарили в карманах и нашли %s", new Money(money)));
				try
				{
					player.balance.transfer(money);
				}
				catch (Money.MoneyException e)
				{
					e.printStackTrace();
					sendMsg(player_id, e.getMessage());
				}
				//playerDAO.update(player);
			}
			else if (money == 0)
			{
				sendMsg(player.getId(), "Вы ничего не нашли в своих карманах");
			}
			else
			{
				throw new RuntimeException("WTF?");
			}
			player.pocketsExpiration = now_ts + cooldownMs;
			//abilityDAO.updatePockets(player_id, now_ts + cooldownMs);
			playerDAO.update(player);
		}
	}


	public void command_balance(Player player)
	{
		sendMsg(player.getId(), String.format("\uD83D\uDCB2 Ваш баланс: %s", player.balance));
	}

	public void command_capitalgame(Player player){

		sendMsg(player.getId(), "Чтобы выйти введите /exit");

		playersInGame.add(player);
		for(Player currentPlayer: playersInGame){
			sendMsg(currentPlayer.getId(), String.format("Игрок `%s` присоединился к игре", player.getUsername()));
		}


		active_players.put(player.getId(), player);
		player.setState(Player.State.capitalGame);

	}

	public void command_check(Player player){
		sendMsg(player.getId(), "\uD83D\uDC41 Введите ник игрока, чей инвентарь Вы хотите просмотреть: ");
		active_players.put(player.getId(), player);
		player.setState(Player.State.checkAwaitingNickname);
	}

	public void command_stats(Player player)
	{
		sendMsg(player.getId(), "\uD83D\uDCBB Всего игроков: " + playerDAO.size());
	}

	public void command_top(Player player)
	{
		StringBuilder players_list = new StringBuilder("\uD83D\uDCBB Топ 10 самых богатых игроков:\n\n");
		players_list.append("========================");
		players_list.append("\n");
		for (Player pl : playerDAO.getTopN("balance", false, 10))
		{
			if(pl.getInventory().getItems().contains(itemDAO.getByName("\uD83E\uDDDA\u200D♀ Фея"))){
				players_list.append(String.format("Игрок \uD83E\uDDDA\u200D♀ `%s` | %s | %d LVL", pl.getUsername(), pl.balance, pl.getLevel()));
				players_list.append("\n");
				players_list.append("========================");
				players_list.append("\n");
			}else{
				players_list.append(String.format("Игрок `%s` | %s | %d LVL", pl.getUsername(), pl.balance, pl.getLevel()));
				players_list.append("\n");
				players_list.append("========================");
				players_list.append("\n");

			}

		}
		players_list.append("\n");
		players_list.append("\uD83D\uDCBB Всего игроков: " + playerDAO.getAll().size());
		sendMsg(player.getId(), players_list.toString());
	}

	public void command_info(Player player)
	{
		long id = player.getId();

		StringBuilder sb = new StringBuilder("*Информация об игре*\n\n");
		sb.append("\uD83C\uDF38 Needle - многопользовательская телеграм игра, где можно весело проводить время с друзьями и другими игроками \n\n");
		sb.append("*Предметы делятся на 4 категории:*\n\n");
		sb.append("`Cheap` - их можно найти практически везде, не несут почти никакой ценности, их можно смело продавать \uD83D\uDCDE Скупщику\n\n");
		sb.append("`Common` - более ценные, чем Cheap. Могут быть проданы дороже, если их сдавать, например Рыба. Ее можно продать в 10 раз дороже");
		sb.append(" если дождаться ярмарки и сдать ее там\n\n");
		sb.append("`Rare` - редкие можно найти при \uD83D\uDC8E Поиске редких предметов, а также в Грязи и на Рыбалке, но с очень маленьким шансом, ");
		sb.append("среди редких предметов много тех, которые нужны для каких-то локаций или функция, как например `\uD83D\uDCDD Тег`, который можно использовать ");
		sb.append("для смены ника \n\n");
		sb.append("`Gift` - самая ценная категория предметов в игре, среди них либо дорогие предметы, либо важные внутриигровые, например `\uD83D\uDC1F Удочка` ");
		sb.append("такие предметы либо дают бонусы, либо нужны для каких-то функций\n\n");
		sb.append("Предметы, помеченные определенным значком перед названием, либо нужны для игры, либо просто являются редким экземпялром в коллекции игрока\n\n");
		sb.append("*☕️Кофе и чай*\n");
		sb.append("Кофе и чай выполняют функцию сообщений. Заказывать кофе или чай для игрока - означает написать ему сообщение в размере 48 символов\n");
		sb.append("услуга стоит $500, но некоторые предметы, например `\uD83C\uDF75 Кружка 'Египет'` могут опускать цену до $100 за раз\n\n");
		sb.append("*\uD83D\uDED2 Магазин*\n");
		sb.append("Магазин - это место, где игроки могут размещать свои предметы и устанавливать на них свою стоимость, а другие игроки могут купить их.\n");
		sb.append("В случае если предмет никто не покупает в течение 24 часов, он автоматически возвращается в инвентарь продавца\n\n");
		sb.append("*\uD83C\uDFB0 Монетка*\n");
		sb.append("В монетке игроки могут испытать удачу, поставив ставку и она либо удвоится, либо...\n\n");
		sb.append("*\uD83D\uDD26 Поиск предметов и уровни*\n");
		sb.append("За поиск предметов игрок получает опыт и прокачивает уровень. Система уровней позволяет игрокам открывать новые возможности в игре. ");
		sb.append("\uD83D\uDC8E Поиск редких предметов имеет задержку в 20 минут, в то время как \uD83D\uDD26 Рыться в грязи можно ежесекундно \n\n");
		sb.append("*\uD83C\uDF80 Топ 10*\n");
		sb.append("Топ-10 - это список самых настойчивых и верных игроков, которые приложили усилия, чтобы оказаться среди самых лучших\n\n");
		sb.append("*\uD83C\uDF3A Помощь*\n");
		sb.append("Для того, чтобы получить полный список команд, существует команда /help также игрок может воспользоваться нижней кнопочной панелью ");
		sb.append("для более быстрой и удобной навигации\n\n");
		sb.append("⚡ Ссылка на официальный телеграм канал Needle, где можно узнавать о новых обновлениях первыми: https://t.me/needlechat\n\n");
		sb.append("Удачной игры!\n");

		sendMsg(id, sb.toString());
	}

	public void command_sell(Player player)
	{
		Inventory inventory = player.getInventory();

		StringBuilder stringBuilder = new StringBuilder();
		if (inventory.getInvSize() > 0)
		{
			active_players.put(player.getId(), player);
			stringBuilder.append("\uD83E\uDDF6 Предметы, доступные к продаже:\n");
			stringBuilder.append("\n");
			stringBuilder.append("============================\n");
			for (int i = 0; i < inventory.getInvSize(); i++)
			{
				stringBuilder.append(String.format("Предмет #[%d] : %s\n", i, inventory.getItem(i).toString()));
			}

			stringBuilder.append("============================\n");
			stringBuilder.append("\n");
			stringBuilder.append("Введите номер предмета, который хотите продать:\n");
			player.setState(Player.State.awaitingSellArguments);
		}
		else
		{
			stringBuilder.append("⚠\t Ваш инвентарь пуст. Нет доступных вещей для продажи ");
		}

		sendMsg(player.getId(), stringBuilder.toString());
	}

	public void command_changeNickname(Player player)
	{
		long id = player.getId();
		Item i = itemDAO.getByName("\uD83D\uDCDD Тег");
		if (player.getInventory().getItems().contains(i))
		{
			active_players.put(player.getId(), player);

			sendMsg(player.getId(), "Введите никнейм, на который вы хотите сменить: ");

			player.setState(Player.State.awaitingChangeNickname);
		}
		else
		{
			sendMsg(id, String.format("Для смены ника нужен предмет `%s`\n\uD83D\uDED2 Его можно купить у других игроков в магазине или найти", i.getTitle()));
		}
	}

	public void command_coin(Player player)
	{
		long player_id = player.getId();
		if (player.getLevel() >= 4)
		{
			if (player.balance.value > 0)
			{
				active_players.put(player_id, player);
				sendMsg(player_id, "\uD83D\uDCB0 Ваш баланс: " + player.getMoney());
				sendMsg(player_id, "\uD83C\uDFB0 Введите ставку: ");
				player.setState(Player.State.coinDash);
			}
			else
			{
				sendMsg(player_id, "\uD83C\uDFB0 У вас недостаточно денег	");
			}
		}
		else
		{
			sendMsg(player_id, "\uD83D\uDC7E Для игры в монетку нужен 4 уровень \n⚡ Повысить уровень можно за поиск предметов");
		}
	}


	public void command_touch(Player player){



		Inventory inventory = player.getInventory();
		long id = player.getId();

		if (inventory.getInvSize() != 0)
		{
			StringBuilder sb = new StringBuilder("\uD83C\uDF81\t *Выберите предмет, который вы хотите осмотреть:* ");
			sb.append("\n");
			sb.append("========================\n");
			for (int i = 0; i < inventory.getInvSize(); i++)
			{
				sb.append(String.format("Предмет #[%d] : %s\n", i, inventory.getItem(i).toString()));
			}
			sb.append("========================\n");
			//sendMsg(message, "\u26BD");
			sendMsg(id, sb.toString());
			active_players.put(id, player);
			player.setState(Player.State.touch);
		}
		else
		{
			sendMsg(id, "\uD83C\uDF81\t Потрогать не получится, так как инвентарь пуст ");
		}

	}

	public void command_me(Player player)
	{
		try
		{
			SendPhoto photo = new SendPhoto();
			photo.setPhoto(new InputFile(new File(".\\pics\\me.jpg")));
			photo.setChatId(player.getId());


		long player_id = player.getId();
		StringBuilder sb = new StringBuilder("*Информация о персонаже*\n\n");
		sb.append("Здесь показывается вся Ваша статистика и достижения\n\n");
		sb.append("⭐ Ваш ник: " + player.getUsername() + "\n\n");
		sb.append("\uD83D\uDCB0 Ваш баланс: " + player.getMoney() + "\n\n");
		sb.append("\uD83C\uDF20 Ваш GameID: " + player_id + "\n\n");
		sb.append(String.format("\uD83D\uDC7E Ваш уровень: %d (%d XP) \n", player.getLevel(), player.getXp()));
		sb.append("\uD83C\uDF3F Выпито кружек чая: " + player.stats.tea + "\n");
		sb.append("☕️ Выпито кружек кофе: " + player.stats.coffee + "\n");
		sb.append(	"\uD83C\uDFC6 Победы в монетке: " + player.stats.coinWins + "\n");
		sb.append("\uD83D\uDCC9 Проигрыши в монетке: " + player.stats.coinLosses +"\n");
		sb.append("\uD83C\uDF31 Посажено деревьев: " + player.stats.trees +"\n\n");

			long id = player.getId();
			StringBuilder sb2 = new StringBuilder("*Ваши достижения:* \n\n");

			if (player.stats.coffee < 30)
			{
				sb2.append("❌");
			}
			else
			{
				sb2.append("✅");
			}
			sb2.append(" Выпить 30 кружек кофе\n");

			if (player.stats.tea < 30)
			{
				sb2.append("❌");
			}
			else
			{
				sb2.append("✅");
			}
			sb2.append(" Выпить 30 кружек чая\n");


			this.execute(photo);
			sendMsg(player_id, sb.toString() + sb2);


		}catch (TelegramApiException e){
			e.printStackTrace();
		}
	}

	public void command_shopbuy(Player player)
	{
		long player_id = player.getId();
		int limitSpace;
		Item backpack = itemDAO.getByName("\uD83C\uDF92 Рюкзак");
		if (player.getInventory().getItems().contains(backpack))
		{
			limitSpace = 25;
		}
		else
		{
			limitSpace = 20;
		}

		if (player.getInventory().getInvSize() < limitSpace)
		{


			if (shopDAO.getAll().isEmpty())
			{

				sendMsg(player_id, "\uD83D\uDC40 В магазине пока нет товаров, чтобы разместить введите /shopplace\n");
			}
			else
			{
				active_players.put(player_id, player);
				StringBuilder sb = new StringBuilder("\uD83D\uDC5C Все предметы в магазине:\n\n");
				//sb.append("=====================\n");
				for (ShopItem i : shopDAO.getAll())
				{
					//сделать привязку не по нику, а по playerID
					sb.append(String.format("\uD83C\uDFA9 Товар |# %d| `%s` | Цена: %s | Продавец: `%s` \n", i.getId(), i.getItem().getTitle(), i.getCost(), i.getSeller().getUsername()));
				}
				sb.append("\n");

				sendMsg(player_id, sb.toString());
				sendMsg(player_id, "Введите ID товара, который вы хотите купить: ");
				player.setState(Player.State.shopBuy);
				//playerDAO.update(player);
			}
		}
		else
		{
			sendMsg(player.getId(), "⚠ В вашем инвентаре нет места");
		}
	}

	public void command_shopshow(Player player)
	{
		try
		{
			SendPhoto photo = new SendPhoto();
			photo.setPhoto(new InputFile(new File(".\\pics\\shop.jpg")));
			photo.setChatId(player.getId());

			long player_id = player.getId();

			if (shopDAO.getAll().isEmpty())
			{
				sendMsg(player_id, "\uD83D\uDC40 В магазине пока нет товаров, чтобы разместить введите /shopplace\n");
			}
			else
			{

				StringBuilder sb = new StringBuilder("\uD83D\uDCE1 Новости\n\nОфициальный телеграм канал: *@needlechat*\n\n");
						sb.append("\uD83D\uDC5C Все предметы в магазине:\n\n");
				//sb.append("=====================\n");
				for (ShopItem i : shopDAO.getAll())
				{
					//сделать привязку не по нику, а по playerID
					sb.append(i);
				}
				sb.append("\n");
				sb.append("\uD83D\uDCB3 Чтобы купить, введите /shopbuy \n");
				sb.append("\uD83D\uDED2 Чтобы разместить свой товар, введите /shopplace \n");
				//sb.append("=====================\n");

				//photo.setCaption(sb.toString());
				this.execute(photo);
				sendMsg(player_id, sb.toString());
			}
		}
		catch (TelegramApiException e)
		{
			e.printStackTrace();
		}
	}

	public void command_shopplace(Player player)
	{
		long player_id = player.getId();

		if (player.getInventory().getInvSize() == 0)
		{
			sendMsg(player_id, "Вы не можете ничего продать, так как Ваш инвентарь пуст");
		}
		else
		{
			active_players.put(player_id, player);
			Inventory inventory = player.getInventory();

			StringBuilder sb = new StringBuilder("Предметы, доступные для продажи \n");
			sb.append("=====================\n");
			for (int i = 0; i < inventory.getInvSize(); i++)
			{

				sb.append(String.format("Предмет | %d |: ", i)).append(inventory.getItem(i)).append("\n");
			}
			sb.append("=====================\n");
			sendMsg(player_id, sb.toString());
			sendMsg(player_id, "Введите ID предмета, который хотите продать\n");

			player.setState(Player.State.shopPlaceGood_awaitingID);
			//playerDAO.update(player);
		}
	}


	public void command_case(Player player)
	{
		try
		{
			SendPhoto photo = new SendPhoto();
			photo.setPhoto(new InputFile(new File(".\\pics\\case.jpg")));
			photo.setChatId(player.getId());

			int casesCounter = 0;
			int keysCounter = 0;
			long id = player.getId();

			StringBuilder sb = new StringBuilder("*Открытие кейсов*\n\n");

			Item _case = itemDAO.getByName("\uD83D\uDCE6 Кейс Gift");
			Item _key = itemDAO.getByName("\uD83D\uDD11 Ключ от кейса");


			for (int i = 0; i < player.getInventory().getInvSize(); i++)
			{
				if (player.getInventory().getItem(i).equals(_case))
				{
					casesCounter++;
				}
				else if (player.getInventory().getItem(i).equals(_key))
				{
					keysCounter++;
				}
			}

			sb.append("В кейсах могут выпадать различные предметы редкости `Gift` и `Rare`\n");
			sb.append("Чтобы открыть кейс нужен предмет `\uD83D\uDD11 Ключ от кейса`\n\n");

			sb.append(String.format("\uD83D\uDCE6 Кейсы: %d\n", casesCounter));
			sb.append(String.format("\uD83D\uDD11 Ключи: %d\n", keysCounter));
			sb.append("\n\n");

			sb.append("Введите /open чтобы открыть кейс: \n");
			this.execute(photo);
			sendMsg(id, sb.toString());
		}
		catch (TelegramApiException e)
		{
			e.printStackTrace();
		}
	}

	public void command_open(Player player)
	{

		Random ran = new Random();
		long id = player.getId();
		Item _case = itemDAO.getByName("\uD83D\uDCE6 Кейс Gift");
		Item _key = itemDAO.getByName("\uD83D\uDD11 Ключ от кейса");

		List<Item> loot;


		loot = itemDAO.getAll().stream().filter((item -> item.getRarity().equals(ItemRarity.Gift) ||
				item.getRarity().equals(ItemRarity.Rare))).collect(Collectors.toList());


		int ranIndex = ran.nextInt(loot.size());


		if (player.getInventory().getItems().contains(_case))
		{
			if (player.getInventory().getItems().contains(_key))
			{
				Item prize = loot.get(ranIndex);
				sendMsg(id, String.format("\uD83C\uDF89 Ура! Вам выпал предмет: `%s`", prize.getTitle()));
				inventoryDAO.putItem(id, prize.getId());
				inventoryDAO.delete(player.getId(), _key.getId(), 1);
				inventoryDAO.delete(player.getId(), _case.getId(), 1);
				playerDAO.update(player);
			}
			else
			{
				sendMsg(id, "У вас нет ключей");
			}
		}
		else
		{
			sendMsg(id, "У вас нет кейсов");
		}
	}

	public void command_pay(Player player)
	{
		if (player.balance.value <= 0)
		{
			sendMsg(player.getId(), "У вас нет денег для перевода");
		}
		else
		{
			active_players.put(player.getId(), player);
			sendMsg(player.getId(), "Введите ник игрока: ");
			player.setState(Player.State.payAwaitingNickname);
		}
	}

	public void command_coffee(Player player)
	{

		int goal;
		Item cup = itemDAO.getByName("☕ Чашка 'Египет'");
		if (player.getInventory().getItems().contains(cup))
		{
			goal = 200;
		}
		else
		{
			goal = 500;
		}


		if (player.getMoney().value < goal)
		{

			sendMsg(player.getId(), "☕ Не хватает деняк на кофе :'(");
		}
		else
		{
			active_players.put(player.getId(), player);
			sendMsg(player.getId(), String.format("☕($%d) Введите ник игрока: ", goal));
			player.setState(Player.State.awaitingCoffee);
		}
	}

	public void command_tea(Player player)
	{

		int goal;
		Item cup = itemDAO.getByName("☕ Чашка 'Египет'");
		if (player.getInventory().getItems().contains(cup))
		{
			goal = 200;
		}
		else
		{
			goal = 500;
		}

		if (player.getMoney().value < goal)
		{
			sendMsg(player.getId(), "\uD83C\uDF3F Не хватает деняк на чай :'(");
		}
		else
		{
			active_players.put(player.getId(), player);
			sendMsg(player.getId(), String.format("\uD83C\uDF3F($%d) Введите ник игрока: ", goal));
			player.setState(Player.State.awaitingTea);
		}
	}

	public void command_start_already_registered(Player player)
	{
		sendMsg(player.getId(), "Вы уже зарегистрированы.\n");
	}

	public void command_previous(Player player)
	{
		if (player.page > 0)
		{
			player.page--;
		}
		sendMsg(player.getId(), String.format("Страница команд №%d", player.page + 1));
	}

	public void command_next(Player player)
	{
		if (player.page < paginator.size - 1)
		{
			player.page++;
		}
		sendMsg(player.getId(), String.format("Страница команд №%d", player.page + 1));
	}

	public void cleanShopFromExpired()
	{
		List<ShopItem> shopItems = shopDAO.expire();
		for (ShopItem shopItem : shopItems)
		{
			Player seller = shopItem.getSeller();
			long seller_id = seller.getId();
			inventoryDAO.putItem(seller_id, shopItem.getItem().getId());
			sendMsg(seller_id, String.format("Ваш товар %sбыл снят с продажи, предмет добавлен в ваш инвентарь", shopItem));
		}
	}

	void sendFindCooldownNotification()
	{
		List<Long> expires = abilityDAO.expireFind();
		for (long id : expires)
		{
			sendMsg(id, "⭐ Вы снова можете искать редкие предметы!");
		}
	}

	public void dump_database()
	{
		System.out.println("Dump database fired (NO-OP)");
	}

	public void on_closing()
	{
		System.out.println("Exiting...");
		sf_dump.cancel(false);
		sf_find.cancel(false);
		sf_timers.cancel(false);
		STPE.stpe.shutdown();
		dump_database();
		System.out.println("Goodbye!");
	}

	public void level_up_notification(Player player)
	{
		int fee = 1375 * player.getLevel();
		sendMsg(player.getId(), String.format("\uD83C\uDF88 Поздравляем! Вы перешли на новый уровень (Уровень %d)\n\uD83C\uDF81 Бонус за переход на новый уровень +%s",
				player.getLevel(), new Money(fee)));
		try
		{
			player.balance.transfer(fee);
		}
		catch (Money.MoneyException e)
		{
			e.printStackTrace();
			sendMsg(player.getId(), e.getMessage());
		}
		playerDAO.update(player);
	}

	public String getBotUsername()
	{
		return "Needle";
	}

	private String init_token() throws FileNotFoundException
	{
		Scanner scanner = new Scanner(new File("token"));
		return scanner.nextLine();
	}

	public String getBotToken()
	{
		return token;
	}

	private void coin_dash_callback(Player player, int i_dash)
	{
		long player_id = player.getId();
		CoinGame coinGame = new CoinGame(i_dash);
		if (coinGame.roll())
		{
			sendMsg(player_id, "\uD83D\uDCB0 Вы выиграли " + new Money(i_dash));
			coinGame.coinWin(player, i_dash);
			player.addXp(1);
			player.stats.coinWins++;
		}
		else
		{
			sendMsg(player_id, "❌ Вы проиграли " + new Money(i_dash));
			coinGame.coinLose(player, i_dash);

			player.stats.coinLosses++;
		}

		player.setState(Player.State.awaitingCommands);
		sendMsg(player_id, "Ваш баланс: " + player.balance + " \uD83D\uDCB2");


		//statsDAO.update(player.getStats(), player.getId());
		playerDAO.update(player);
	}
}



