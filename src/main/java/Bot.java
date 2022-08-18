import ability.Cooldown;
import database.dao.InventoryDAO;
import database.dao.PlayerDAO;
import database.dao.ShopDAO;
import main.*;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.io.File;
import java.io.FileNotFoundException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;


public class Bot extends TelegramLongPollingBot
{
	private final Connection connection;

	private final PlayerDAO playerDAO;
	private final InventoryDAO inventoryDAO;
	private final ShopDAO shopDAO;

	private static final Roller<Item> mudRoller = RollerFactory.getMudRoller(new Random());
	private static final Roller<Integer> moneyRoller = RollerFactory.getMoneyRoller(new Random());
	private static final Roller<Item> findRoller = RollerFactory.getFindRoller(new Random());

	//ОБЩИЕ ДЛЯ ВСЕХ ПОЛЬЗОВАТЕЛЕЙ БУДУТ БАГИ
	//Пофиксить, чтобы Продавец хранился по ID, а не по нику
	private int shopItemID;

	private final String token;

	Map<Player.State, BiConsumer<Player, Message>> state_processor;
	Map<String, Consumer<Player>> command_processor;

	public Bot(Connection connection) throws FileNotFoundException
	{
		this.connection = connection;
		playerDAO = new PlayerDAO(connection);
		inventoryDAO = new InventoryDAO(connection);
		shopDAO = new ShopDAO(connection);
		token = init_token();
		state_processor = BotStateProcessor.get_map(this);
		command_processor = BotCommandProcessor.get_map(this);
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
		Player player = playerDAO.get_by_id(id);
		//инициаллизация клавиатуры
		ReplyKeyboardMarkup replyKeyboardMarkup = new ReplyKeyboardMarkup();
		//установка разметки
		sendMessage.setReplyMarkup(replyKeyboardMarkup);
		//вывод клавиатуры (видел или нет)
		replyKeyboardMarkup.setSelective(true);
		replyKeyboardMarkup.setResizeKeyboard(true);
		//скрывать или не скрывать после использования
		replyKeyboardMarkup.setOneTimeKeyboard(true);

		List<KeyboardRow> keyboardRowList = new ArrayList<>();
		KeyboardRow keyboardFirstRow = new KeyboardRow();

		//добавили новую кнопку в первый ряд
		//KeyboardButton startButton = new KeyboardButton("/start");

		if (player == null)
		{
			keyboardFirstRow.add(new KeyboardButton("⭐ Начать"));
			//keyboardFirstRow.add(new KeyboardButton("/start"));
		}
		else
		{

			keyboardFirstRow.add(new KeyboardButton("\uD83C\uDF3A Помощь"));
			//keyboardFirstRow.add(new KeyboardButton("/help"));
			keyboardFirstRow.add(new KeyboardButton("⭐️ Персонаж"));
			//keyboardFirstRow.add(new KeyboardButton("/me"));


		}

		//keyboardFirstRow.add(new KeyboardButton("/find"));
		//добавили в спиок всех кнопок
		keyboardRowList.add(keyboardFirstRow);
		replyKeyboardMarkup.setKeyboard(keyboardRowList);

	}

	@Override
	public void onUpdateReceived(Update update)
	{
		Message message = update.getMessage();

		if (message != null && message.hasText())
		{
			long id = message.getChatId();
			String text = message.getText();

			Player player = playerDAO.get_by_id(id);

			System.out.printf("%s: %s [from %s | %d]\n", new Date(), text, player != null ? player.getUsername() : "new player", id);

			if (player == null)
			{
				if (text.equals("/start"))
				{
					player = new Player(id);
					playerDAO.put(player);
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
				playerDAO.update(player);
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
				player.setUsername(username);
				player.setState(Player.State.awaitingCommands);
				playerDAO.update(player);
				sendMsg(player_id, "Игрок `" + player.getUsername() + "` успешно создан");
				command_help(player);
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
			player.balance += item.getCost();
			inventory.removeItem(sell_id);
			inventoryDAO.delete(player_id, item.getId(), 1);

			player.setState(Player.State.awaitingCommands);
			playerDAO.update(player);
			sendMsg(player_id, "✅ Предмет продан | + $" + item.getCost());
		}
		catch (NumberFormatException e)
		{
			e.printStackTrace();
			sendMsg(player_id, "⚠\t Пожалуйста, введите целое число");
			player.setState(Player.State.awaitingCommands);
			playerDAO.update(player);
		}
		catch (IndexOutOfBoundsException ee)
		{
			ee.printStackTrace();
			sendMsg(player_id, "⚠\t Указан неверный ID");
			player.setState(Player.State.awaitingCommands);
			playerDAO.update(player);
		}
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


	void awaitingChangeNickname_processor(Player player, Message message)
	{
		long player_id = player.getId();
		String nickname = message.getText();
		//regex для ника
		String usernameTemplate = "([А-Яа-яA-Za-z0-9]{3,32})";
		if (nickname.matches(usernameTemplate))
		{
			try
			{
				player.setUsername(nickname);
				player.setState(Player.State.awaitingCommands);
				playerDAO.update(player);
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
			//player.setState(Player.State.awaitingChangeNickname);
			//playerDAO.update(player);
		}
	}

	void coinDash_processor(Player player, Message message)
	{
		long player_id = player.getId();
		String dash = message.getText();
		try
		{
			int i_dash = Integer.parseInt(dash);

			if (i_dash > 0 && i_dash <= player.balance)
			{
				sendMsg(player_id, "\uD83C\uDFB0 Ваша ставка: $" + i_dash);

				sendMsg(player_id, "Подбрасываем монетку...");

				Cooldown kd = new Cooldown(2, () -> coin_dash_callback(player, i_dash));
				kd.startCooldown();
			}
			else
			{
				sendMsg(player_id, "⚠\t У вас нет такой суммы");

			}
		}
		catch (NumberFormatException e)
		{
			sendMsg(player_id, "⚠\tВаша ставка должна быть целым числом");
			e.printStackTrace();
			player.setState(Player.State.awaitingCommands);
			playerDAO.update(player);
		}
	}

	void shopBuy_processor(Player player, Message message)
	{
		try
		{
			int userInput = Integer.parseInt(message.getText());
			int itemCost = shopDAO.getByID(userInput).getCost();
			long sellerID = playerDAO.get_by_name(shopDAO.getByID(userInput).getSeller()).getId();
			if (player.getMoney() >= itemCost)
			{
				player.balance -= itemCost;
				inventoryDAO.putItem(player.getId(), shopDAO.getByID(userInput).getItem().getId());
				sendMsg(player.getId(), String.format("Предмет `%s` успешно куплен", shopDAO.getByID(userInput).getItem().getTitle()));
				sendMsg(sellerID, String.format("Ваш предмет `%s` купил игрок `%s` | + $%d", shopDAO.getByID(userInput).getItem().getTitle(), player.getUsername(), itemCost));
				playerDAO.get_by_name(shopDAO.getByID(userInput).getSeller()).balance += itemCost;
				shopDAO.delete(userInput);
				player.setState(Player.State.awaitingCommands);
				playerDAO.update(player);
			}
			else
			{
				sendMsg(player.getId(), "нема стока деняк");
				player.setState(Player.State.awaitingCommands);
				playerDAO.update(player);
			}
		}
		catch (NumberFormatException | SQLException e)
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
	}


	void shopPlaceGood_awaitingID_processor(Player player, Message message)
	{
		shopItemID = 0;

		try
		{
			int itemID = Integer.parseInt(message.getText());
			if (itemID >= player.getInventory().getInvSize())
			{
				throw new IndexOutOfBoundsException();
			}
			shopItemID = itemID;
			sendMsg(player.getId(), "Введите стоимость товара: ");
			player.setState(Player.State.shopPlaceGood_awaitingCost);
			playerDAO.update(player);

		}
		catch (NumberFormatException e)
		{
			e.printStackTrace();
			sendMsg(player.getId(), "Введите целое число");
		}
		catch (IndexOutOfBoundsException ee)
		{
			ee.printStackTrace();
			sendMsg(player.getId(), "Неверный ID");
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
				ShopItem shopItem = new ShopItem(inventory.getItem(shopItemID), cost, player.getUsername());
				shopDAO.put(shopItem);
				sendMsg(player_id, String.format("Товар ` %s ` выставлен на продажу", inventory.getItem(shopItemID).getTitle()));
				inventory.removeItem(shopItemID);
				inventoryDAO.delete(player_id, shopItem.getItem().getId(), 1);
				//inventoryDAO.delete(player, , 1);
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
			//костыль, чтобы выходить из стейтов
			player.setState(Player.State.awaitingCommands);
			playerDAO.update(player);

		}

	}

	public String getNick(String name) throws SQLException
	{
		return playerDAO.get_by_name(name).getUsername();
	}

	public void payAwaitingNickname_processor(Player player, Message message)
	{
		payNickname = "";
		long player_id = player.getId();
		String nickname = message.getText();

		if (!nickname.equals(player.getUsername()))
		{
			Player acceptor = playerDAO.get_by_name(nickname);
			if (acceptor != null)
			{
				player.payment_acceptor = acceptor;
				player.setState(Player.State.payAwaitingAmount);
				playerDAO.update(player);
				sendMsg(player_id, "\uD83D\uDCB3 Введите сумму: ");
			}
			else
			{
				sendMsg(player_id, "Такого игрока не существует");
				player.setState(Player.State.awaitingCommands);
				playerDAO.update(player);
			}
		}
		else
		{
			sendMsg(player_id, String.format("\uD83C\uDF38 Игрок `%s` очень богат и не нуждается в Ваших копейках", player.getUsername()));
			player.setState(Player.State.awaitingCommands);
			playerDAO.update(player);
		}
	}

	public void payAwaitingAmount_processor(Player player, Message message)
	{
		try
		{
			int cost = Integer.parseInt(message.getText());
			if (cost > player.getMoney() || cost <= 0)
			{
				sendMsg(player.getId(), "⚠\t Некорректная сумма");
			}
			else
			{
				Player receiver = player.payment_acceptor;
				player.balance -= cost;
				sendMsg(receiver.getId(), String.format("\uD83D\uDCB3 Вам начислено $%d | Отправитель: `%s` ", cost, player.getUsername()));
				sendMsg(player.getId(), "✅ Деньги отправлены");
				receiver.balance += cost;
				player.setState(Player.State.awaitingCommands);
				playerDAO.update(receiver);
				playerDAO.update(player);
			}
		}
		catch (NumberFormatException e)
		{
			e.printStackTrace();
			sendMsg(player.getId(), "⚠\t Вы ввели некорректную сумму");
			player.setState(Player.State.awaitingCommands);
			playerDAO.update(player);
		}
	}


	public void command_help(Player player)
	{
		sendMsg(player.getId(), "\\[`Needle`] Бот содержит следующие команды: \n" +
				"\n" +
				"\uD83D\uDD0D /find - искать предметы \n" +
				"\n" +
				"\uD83D\uDCC3 /inv - открыть инвентарь \n" +
				"\n" +
				"\uD83C\uDF80 /top - посмотреть рейтинг всех игроков \n" +
				"\n" +
				"\uD83D\uDCB9 /stats - онлайн игроков \n" +
				"\n" +
				"\uD83D\uDCB3 /balance - проверить баланс  \n" +
				"\n" +
				"\uD83D\uDCB0 /sell - продать предмет скупщику\n" +
				"\n" +
				"\uD83D\uDCE9 /help - список всех команд \n" +
				"\n" +
				"ℹ /info - информация об игре \n" +

				"\n" +
				"\uD83D\uDC80 /changenickname - сменить никнейм \n \n" +
				"\uD83C\uDFB0 /coin - сыграть в Монетку \n\n" +
				"⭐ /me - ифнормация о персонаже \n\n" +
				"\uD83D\uDCB0 /pay - переслать деньги"
		);
	}

	public void command_inv(Player player)
	{
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
			sb.append("\uD83C\uDF81\t Всего предметов: ").append(inventory.getInvSize());
			sendMsg(player_id, sb.toString());
		}
		else
		{
			sendMsg(player_id, "\uD83C\uDF81\t Ваш инвентарь пуст ");
		}
	}

	public void command_find(Player player)
	{
		long player_id = player.getId();
		long now_ts = System.currentTimeMillis();
		long used_ts = player.last_fia;
		long cooldown_s = 60L * 60L * 6L;
		long cooldown_ms = cooldown_s * 1000L;
		long left_ms = used_ts + cooldown_ms - now_ts;

		if (left_ms > 0L)
		{
			sendMsg(player_id, String.format("\u231B Время ожидания: %s",
					PrettyDate.prettify(left_ms, TimeUnit.MILLISECONDS)));
		}
		else
		{
			//Item new_item = ItemFactory.getRandomItem();
			//Item new_item = ItemFactory.getRandomWeighted();
			Item new_item = findRoller.roll();
			inventoryDAO.putItem(player_id, new_item.getId());
			sendMsg(player_id, String.format("\uD83C\uDF81\t Вы нашли: %s", new_item));
			player.last_fia = now_ts;
			player.addXp(2);
			//if (player.getXp() >= 10)
			//{
			//	player.levelUp();
			//	sendMsg(player_id, "\uD83D\uDC7E Вы перешли на " + player.getLevel() + " уровень");
			//}

			playerDAO.update(player);
		}
	}


	public void command_mud(Player player)
	{
		Item item = mudRoller.roll();
		if (item != null)
		{
			player.addXp(1);
			inventoryDAO.putItem(player.getId(), item.getId());
			playerDAO.update(player);
			sendMsg(player.getId(), String.format("Вы нашли в грязи %s", item));
		}
		else
		{
			sendMsg(player.getId(), "Вы ничего не нашли");
		}
	}

	public void command_pockets(Player player)
	{
		long player_id = player.getId();
		long now_ts = System.currentTimeMillis();
		long used_ts = player.last_pockets;
		long cooldown_s = 30L;
		long cooldown_ms = cooldown_s * 1000L;
		long left_ms = used_ts + cooldown_ms - now_ts;

		if (left_ms > 0L)
		{
			sendMsg(player_id, String.format("\u231B Время ожидания: %s",
					PrettyDate.prettify(left_ms, TimeUnit.MILLISECONDS)));
		}
		else
		{
			int money = moneyRoller.roll();
			player.last_pockets = now_ts;
			if (money > 0)
			{
				sendMsg(player_id, String.format("Вы пошарили в карманах и нашли $%d", money));
				player.balance += money;
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
			playerDAO.update(player);
		}
	}


	public void command_balance(Player player)
	{
		sendMsg(player.getId(), String.format("\uD83D\uDCB2 Ваш баланс: $%d", player.balance));
	}


	public void command_stats(Player player)
	{
		sendMsg(player.getId(), "\uD83D\uDCBB Всего игроков: " + playerDAO.size());
	}

	public void command_top(Player player)
	{
		StringBuilder players_list = new StringBuilder("\uD83D\uDCBB Топ игроков:\n");
		players_list.append("========================");
		players_list.append("\n");
		for (Player pl : playerDAO.getTopN("balance", false, 5))
		{
			players_list.append(String.format("Игрок %s | $%d | %d LVL", "`" + pl.getUsername() + "`", pl.balance, pl.getLevel()));
			players_list.append("\n");
			players_list.append("========================");
			players_list.append("\n");

		}
		sendMsg(player.getId(), players_list.toString());
	}

	public void command_info(Player player)
	{
		sendMsg(player.getId(), "Needle - это многопользовательская телеграм игра, нацеленная на коллекционирование " +
				"предметов. Вам как игроку предстоит собирать их, открывать ачивки и соревноваться с другими " +
				"игроками. Предметы Вы можете продавать, тем самым увеличивая свой игровой баланс. Внутриигровую валюту " +
				"вы можете тратить на покупку предметов у других игроков, на внутриигровое казино, а также на поиск предметов " +
				"сокращая время ожидания для поиска. Предметы вы можете искать раз в 6 часов. Среди них есть обычные, редкие, коллекционные " +
				"и подарочные. Последняя категория не имеет цены, а это значит, что она может быть продана среди игроков за установленную " +
				"цену. Покупать и выставлять предметы можно на аукционе. Удачи и приятной игры. ");
	}

	public void command_sell(Player player)
	{
		Inventory inventory = player.getInventory();

		StringBuilder stringBuilder = new StringBuilder();
		if (inventory.getInvSize() > 0)
		{
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
			playerDAO.update(player);
		}
		else
		{
			stringBuilder.append("⚠\t Ваш инвентарь пуст. Нет доступных вещей для продажи ");
		}

		sendMsg(player.getId(), stringBuilder.toString());
	}


	public void command_changeNickname(Player player)
	{
		sendMsg(player.getId(), "Введите никнейм, на который вы хотите сменить: ");
		player.setState(Player.State.awaitingChangeNickname);
		playerDAO.update(player);
	}

	public void command_coin(Player player)
	{
		long player_id = player.getId();
		if (player.getLevel() >= 4)
		{
			if (player.balance > 0)
			{
				sendMsg(player_id, "\uD83C\uDFB0 Введите ставку: ");
				player.setState(Player.State.coinDash);
				playerDAO.update(player);
			}
			else
			{
				sendMsg(player_id, "\uD83C\uDFB0 У вас недостаточно денег	");
			}
		}
		else
		{
			sendMsg(player_id, "\uD83D\uDC7E Для игры в монетку нужен 4 уровень");
		}
	}

	public void command_me(Player player)
	{
		long player_id = player.getId();
		StringBuilder sb = new StringBuilder("Информация о персонаже\n");
		//sb.append("==============================\n");
		sb.append("\n");
		sb.append("⭐ Ваш ник: ").append(player.getUsername());
		sb.append("\n");
		//sb.append("==============================\n");
		sb.append("\n");
		sb.append("\uD83D\uDCB0 Ваш баланс: $").append(player.getMoney());
		sb.append("\n");
		//sb.append("==============================\n");
		sb.append("\n");
		sb.append("\uD83C\uDF20 Ваш GameID: ").append(player_id);
		sb.append("\n");
		//sb.append("==============================\n");
		sb.append("\n");
		sb.append(String.format("\uD83D\uDC7E Ваш уровень: %d (%d XP)", player.getLevel(), player.getXp()));
		sb.append("\n");
		//sb.append("==============================\n");

		sendMsg(player_id, sb.toString());
	}

	public void command_shopbuy(Player player)
	{


		if (shopDAO.getAll().isEmpty())
		{
			sendMsg(player.getId(), "\uD83D\uDC40 В магазине пока нет товаров\n");
			player.setState(Player.State.awaitingCommands);
			playerDAO.update(player);
		}
		else
		{
			StringBuilder sb = new StringBuilder("\uD83D\uDC5C Все предметы в магазине:\n\n");
			//sb.append("=====================\n");
			for (ShopItem i : shopDAO.getAll())
			{
				//сделать привязку не по нику, а по playerID
				sb.append(String.format("\uD83C\uDFA9 Товар |# %d| `%s` | Цена: %d$ | Продавец: `%s` \n", i.getId(), i.getItem().getTitle(), i.getCost(), i.getSeller()));

			}
			//sb.append("=====================\n");
			sendMsg(player.getId(), sb.toString());
			sendMsg(player.getId(), "Введите ID товара, который вы хотите купить: ");
			player.setState(Player.State.shopBuy);
			playerDAO.update(player);
		}
	}

	public void command_shopshow(Player player)
	{
		long player_id = player.getId();

		if (shopDAO.getAll().isEmpty())
		{
			sendMsg(player_id, "\uD83D\uDC40 В магазине пока нет товаров\n");
			player.setState(Player.State.awaitingCommands);
			playerDAO.update(player);
		}
		else
		{
			StringBuilder sb = new StringBuilder("\uD83D\uDC5C Все предметы в магазине:\n\n");
			//sb.append("=====================\n");
			for (ShopItem i : shopDAO.getAll())
			{
				//сделать привязку не по нику, а по playerID
				sb.append(String.format("\uD83C\uDFA9 Товар `%s` | Цена: %d$ | Продавец: `%s` \n", i.getItem().getTitle(), i.getCost(), i.getSeller()));
			}
			//sb.append("=====================\n");
			sendMsg(player_id, sb.toString());
			player.setState(Player.State.awaitingCommands);
			playerDAO.update(player);
		}
	}

	public void command_shopplace(Player player)
	{
		long player_id = player.getId();

		if (player.getInventory().getInvSize() == 0)
		{
			sendMsg(player_id, "Вы не можете ничего продать, так как Ваш инвентарь пуст");
			player.setState(Player.State.awaitingCommands);
			playerDAO.update(player);
		}
		else
		{
			Inventory inventory = player.getInventory();

			StringBuilder sb = new StringBuilder("Предметы, доступные для продажи \n");
			sb.append("=====================\n");
			for (int i = 0; i < inventory.getInvSize(); i++)
			{

				sb.append(String.format("Предмет | %d |: ", i) + inventory.getItem(i) + "\n");
			}
			sb.append("=====================\n");
			sendMsg(player_id, sb.toString());
			sendMsg(player_id, "Введите ID предмета, который хотите продать\n");

			player.setState(Player.State.shopPlaceGood_awaitingID);
			playerDAO.update(player);
		}
	}


	public void command_pay(Player player)
	{
		if (player.getMoney() <= 0)
		{
			sendMsg(player.getId(), "У вас нет денег для перевода");
		}
		else
		{
			sendMsg(player.getId(), "Введите ник игрока: ");
			player.setState(Player.State.payAwaitingNickname);
			playerDAO.update(player);
		}
	}

	public void command_start_already_registered(Player player)
	{
		sendMsg(player.getId(), "Вы уже зарегистрированы.\n");
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
			sendMsg(player_id, "\uD83D\uDCB0 Вы выиграли " + "$" + i_dash);
			coinGame.coinWin(player, i_dash);
		}
		else
		{
			sendMsg(player_id, "❌ Вы проиграли " + "$" + i_dash);
			coinGame.coinLose(player, i_dash);
		}

		player.setState(Player.State.awaitingCommands);
		sendMsg(player_id, "Ваш баланс: " + player.balance + " \uD83D\uDCB2");
		playerDAO.update(player);
	}
}



