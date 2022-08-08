import ability.Ability;
import ability.Cooldown;
import database.InventoryDAO;
import database.PlayerDAO;
import database.SQLExecutor;
import database.SQLSession;
import main.Inventory;
import main.Item;
import main.Player;
import main.PrettyDate;
import org.telegram.telegrambots.ApiContextInitializer;
import org.telegram.telegrambots.TelegramBotsApi;
import org.telegram.telegrambots.api.methods.send.SendMessage;
import org.telegram.telegrambots.api.objects.Message;
import org.telegram.telegrambots.api.objects.Update;
import org.telegram.telegrambots.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.exceptions.TelegramApiException;
import org.telegram.telegrambots.exceptions.TelegramApiRequestException;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;


public class Bot extends TelegramLongPollingBot
{
	//private static Map<Long, Player> players = new HashMap<>(); //контейнер игроков

	//private static Connection connection;
	static PlayerDAO playerDAO = new PlayerDAO(SQLSession.sqlConnection);
	static InventoryDAO inventoryDAO = new InventoryDAO(SQLSession.sqlConnection);

	//🐳

	public static void main(String[] args) throws IOException, SQLException
	{
		//readFile();
		initDB();

		//players = new HashMap<>();
		//for (Player player : playerDAO.getAll())
		//{
		//	players.put(player.getId(), player);
		//}

		ApiContextInitializer.init(); //инициализация API
		TelegramBotsApi telegramBotsApi = new TelegramBotsApi(); //создание объекта в API
		try
		{
			telegramBotsApi.registerBot(new Bot());
		}
		catch (TelegramApiRequestException e)
		{
			e.printStackTrace();
		}
	}

	private static void initDB() throws IOException, SQLException
	{
		SQLExecutor executor = new SQLExecutor(new File("src\\main\\java\\database\\init.sql"), SQLSession.sqlConnection);
		executor.execute();
		/*
		Statement statement = connection.createStatement();
		statement.execute("create table if not exists players\n" +
				"(\n" +
				"    id integer primary key,\n" +
				"    name text,\n" +
				"    state text not null,\n" +
				"    lastfia text default \"NEVER\"\n" +
				");");
		statement.execute("create table if not exists items\n" +
				"(\n" +
				"    id integer primary key,\n" +
				"    name text not null,\n" +
				"    rarity text not null,\n" +
				"    cost integer\n" +
				");");
		statement.execute("create table if not exists inventory\n" +
				"(\n" +
				"    id integer primary key,\n" +
				"    player_id,\n" +
				"    item_id,\n" +
				"\n" +
				"    foreign key (player_id) references players (id) on delete cascade,\n" +
				"    foreign key (item_id) references items (id) on update cascade on delete cascade\n" +
				");");
		statement.execute("insert or ignore into items values\n" +
				"(1, \"Лопата\", \"Common\", 200),\n" +
				"(2, \"Поисковый фонарь\", \"Rare\", 7000),\n" +
				"(3, \"Подвеска 'Nosebleed'\", \"Rare\", 30000),\n" +
				"(4, \"Струны\", \"Common\", 500),\n" +
				"(5, \"Футболка 'Drain'\", \"Common\", 500),\n" +
				"(6, \"Банан\", \"Common\", 100),\n" +
				"(7, \"Чашка 'Египет'\", \"Rare\", 1000),\n" +
				"(8, \"Носки\", \"Common\", 100),\n" +
				"(9, \"Ручка\", \"Common\", 100),\n" +
				"(10, \"Баллончик с краской\", \"Common\", 750),\n" +
				"(11, \"Платок\", \"Common\", 150),\n" +
				"(12, \"Пачка сигарет\", \"Common\", 50),\n" +
				"(13, \"Синий браслет\", \"Common\", 300),\n" +
				"(14, \"Красный браслет\", \"Common\", 300),\n" +
				"(15, \"Желтый браслет\", \"Common\", 300),\n" +
				"(16, \"Зеленый браслет\", \"Common\", 300),\n" +
				"(17, \"Браслет 'Орион'\", \"Common\", 1000),\n" +
				"(18, \"Браслет 'Сириус'\", \"Common\", 900),\n" +
				"(19, \"Зубная щетка\", \"Common\", 50),\n" +
				"(20, \"Шоколадка\", \"Common\", 200),\n" +
				"(21, \"Рюкзак\", \"Rare\", 700),\n" +
				"(22, \"Синий фонарик\", \"Gift\", 25000);");*/
	}


	//что бот будет отвечать
	public void sendMsg(Message message, String text)
	{
		SendMessage sendMessage = new SendMessage();
		sendMessage.enableMarkdown(true);

		//чат айди, чтобы было понятно кому отвечать
		sendMessage.setChatId(message.getChatId().toString());

		//конкретно, на какое сообщение ответить
		//sendMessage.setReplyToMessageId(message.getMessageId());

		sendMessage.setText(text);
		try
		{
			//добавили кнопку и поместили в нее сообщение
			setButtons(sendMessage);
			sendMessage(sendMessage);
		}
		catch (TelegramApiException e)
		{
			e.printStackTrace();
		}

	}

	public void command_help(Message message)
	{
		sendMsg(message, "\\[`Needle`] Бот содержит следующие команды: \n" +
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
				"\uD83C\uDFB0 /coin - сыграть в Монетку"


		);
	}

	public void command_inv(Message message)
	{
		Inventory inventory = inventoryDAO.get(message.getChatId());
		if (inventory.getInvSize() != 0)
		{

			sendMsg(message, "\uD83C\uDF81\t Ваш инвентарь: ");
			//sendMsg(message, "\u26BD");
			sendMsg(message, "\n" + inventory.showInventory() + "\n");
			sendMsg(message, "\uD83C\uDF81\t Всего предметов: " + inventory.getInvSize());
		}
		else
		{
			sendMsg(message, "\uD83C\uDF81\t Ваш инвентарь пуст ");

		}
	}

	public void command_find(Message message)
	{
		long id = message.getChatId();
		Player player = playerDAO.get(id);
		Ability<Item> fia = player.getFindItemAbility();
		if (fia.isUsable())
		{
			Item new_item = fia.use();
			inventoryDAO.putItem(id, new_item.getId());
			sendMsg(message, String.format("\uD83C\uDF81\t Вы нашли: %s", new_item));
		}
		else
		{
			sendMsg(message, String.format("\u231B Время ожидания: %s",
					PrettyDate.prettify(fia.getCDTimer(), TimeUnit.SECONDS)));
		}
	}

	public void command_balance(Message message)
	{
		sendMsg(message, String.format("\uD83D\uDCB2 Ваш баланс: $%d", playerDAO.get(message.getChatId()).balance));
	}


	public void command_stats(Message message)
	{
		sendMsg(message, "\uD83D\uDCBB Всего игроков: " + playerDAO.size());
	}

	public void command_top(Message message)
	{
		StringBuilder players_list = new StringBuilder("\uD83D\uDCBB Все игроки:\n");
		players_list.append("========================");
		players_list.append("\n");
		for (Player player : playerDAO.getAll())
		{
			players_list.append(String.format("Игрок %s | $%d", player.getUsername(), player.balance));
			players_list.append("\n");
			players_list.append("========================");
			players_list.append("\n");
		}
		sendMsg(message, players_list.toString());
		//for(Map.Entry<Long, main.Player> pair : players.entrySet()){
		//	sendMsg(message, "Игрок: " + pair.getValue().getUsername() + " | " + "$" + pair.getValue().getInventory().getBalance());
		//}
	}

	public void command_info(Message message)
	{
		sendMsg(message, "Needle - это многопользовательская телеграм игра, нацеленная на коллекционирование " +
				"предметов. Вам как игроку предстоит собирать их, открывать ачивки и соревноваться с другими " +
				"игроками. Предметы Вы можете продавать, тем самым увеличивая свой игровой баланс. Внутриигровую валюту " +
				"вы можете тратить на покупку предметов у других игроков, на внутриигровое казино, а также на поиск предметов " +
				"сокращая время ожидания для поиска. Предметы вы можете искать раз в 6 часов. Среди них есть обычные, редкие, коллекционные " +
				"и подарочные. Последняя категория не имеет цены, а это значит, что она может быть продана среди игроков за установленную " +
				"цену. Покупать и выставлять предметы можно на аукционе. Удачи и приятной игры. ");
	}

	public void command_sell(Message message)
	{
		long id = message.getChatId();
		Player player = playerDAO.get(id);
		Inventory inventory = player.getInventory();

		StringBuilder stringBuilder = new StringBuilder();
		if (inventory.getInvSize() > 0)
		{
			stringBuilder.append("Предметы, доступные к продаже:\n");
			for (int i = 0; i < inventory.getInvSize(); i++)
			{
				stringBuilder.append(String.format("Предмет #[%d] : %s\n", i, inventory.getItem(i).toString()));
			}
			stringBuilder.append("Введите номер предмета, который хотите продать:\n");
			player.setState(Player.State.awaitingSellArguments);
			playerDAO.update(player);
		}
		else
		{
			stringBuilder.append("⚠\t Ваш инвентарь пуст. Нет доступных вещей для продажи ");
		}

		sendMsg(message, stringBuilder.toString());
	}

	public void command_changeNickname(Message message)
	{
		Player player = playerDAO.get(message.getChatId());
		sendMsg(message, "Введите никнейм, на который вы хотите сменить: ");
		player.setState(Player.State.awaitingChangeNickname);
		playerDAO.update(player);
	}

	//метод для приема сообщений и обновлений
	public void onUpdateReceived(Update update)
	{
		Message message = update.getMessage();
		//regex для ника
		String usernameTemplate = "([А-Яа-яA-Za-z0-9]{3,32})";

		if (message != null && message.hasText())
		{
			Long id = message.getChatId();
			String text = message.getText();

			System.out.println("Текстик: " + message.getText());


			switch (text)
			{
				case "/start":
					if (playerDAO.get(id) != null)
					{
						sendMsg(message, "Вы уже зарегистрированы");
					}
					else
					{
						playerDAO.put(new Player(id, "player" + id, 0, Player.State.awaitingNickname, new Inventory()));
						sendMsg(message, "\uD83C\uDF77 Добро пожаловать в Needle");
					}
					break;
				default:
					if (playerDAO.get(id) == null)
					{
						sendMsg(message, "⭐ Для регистрации введите команду /start");
					}
					break;
			}

			Player player = playerDAO.get(id);
			//использовать containsKey

			//if(!players.containsKey(player)){
			//sendMsg(message, "Введите команду /start");
			//	}

			switch (text)
			{
				case "/inv":
					command_inv(message);
					break;
				case "/find":
					command_find(message);
					break;
				case "/balance":
					command_balance(message);
					break;
				case "/stats":
					command_stats(message);
					break;
				case "/sell":
					command_sell(message);
					break;
				case "/top":
					//bug
					command_top(message);
					break;
				case "/help":
					command_help(message);
					break;
				case "/info":
					command_info(message);
					break;
				case "/changenickname":
					command_changeNickname(message);
					break;
				case "/cheat":
					sendMsg(message, "Игрок " + player.getUsername() + " обзавелся префиксом");
					player.setUsername("\uD83D\uDC33 " + player.getUsername());
					playerDAO.update(player);
					break;
				case "/coin":

					if (player.balance > 0)
					{
						sendMsg(message, "\uD83C\uDFB0 Введите ставку: ");
					}
					else
					{
						sendMsg(message, "\uD83C\uDFB0 У вас недостаточно денег	");
					}


					player.setState(Player.State.coinDash);
					playerDAO.update(player);
					break;
				default:

					if (player.getId() == id)
					{
						if (player.getState() == Player.State.awaitingNickname)
						{
							String username = message.getText();

							if (username.matches(usernameTemplate))
							{
								player.setUsername(username);
								player.setState(Player.State.awaitingCommands);
								playerDAO.update(player);
								sendMsg(message, "Игрок " + "`" + player.getUsername() + "`" + " успешно создан");
								command_help(message);
							}
							else
							{
								//sendMsg(message, "Введите корректный ник: ");
								sendMsg(message, "Введите ник: ");
								//player.setState(main.Player.State.awaitingNickname);
							}


						}
						else if (player.getState() == Player.State.awaitingSellArguments)
						{
							try
							{
								Inventory inventory = player.getInventory();
								String sellID = message.getText();
								int sell_id = Integer.parseInt(sellID);
								Item item = inventory.getItem(sell_id);
								player.balance += item.getCost();
								inventory.removeItem(sell_id);
								inventoryDAO.delete(id, item.getId(), 1);

								player.setState(Player.State.awaitingCommands);
								playerDAO.update(player);

								sendMsg(message, "✅ Предмет успешно продан");
							}
							catch (NumberFormatException e)
							{
								e.printStackTrace();
								sendMsg(message, "⚠\t Пожалуйста, введите целое число");
								player.setState(Player.State.awaitingCommands);
								playerDAO.update(player);
							}
							catch (IndexOutOfBoundsException ee)
							{
								ee.printStackTrace();
								sendMsg(message, "⚠\t Указан неверный ID");
								player.setState(Player.State.awaitingCommands);
								playerDAO.update(player);
							}
						}
						else if (player.getState() == Player.State.awaitingCommands)
						{
							String getText = message.getText();
							//небольшая проверка /start и чтобы не писало Неизвестная команда
							//FIX HERE
							if (!getText.equals("/start"))
							{
								sendMsg(message, "⚠\t Неизвестная команда");
							}
						}
						else if (player.getState() == Player.State.awaitingChangeNickname)
						{
							String nickname = message.getText();
							if (nickname.matches(usernameTemplate))
							{
								player.setUsername(nickname);
								sendMsg(message, "Ваш никнейм успешно изменен на " + "`" + player.getUsername() + "`");
								player.setState(Player.State.awaitingCommands);
								playerDAO.update(player);
							}
							else
							{
								sendMsg(message, "Пожалуйста, введите корректный ник");
								player.setState(Player.State.awaitingChangeNickname);
								playerDAO.update(player);
							}

						}
						else if (player.getState() == Player.State.coinDash)
						{
							try
							{
								String dash = message.getText();
								int i_dash = Integer.parseInt(dash);

								if (i_dash > 0 && i_dash <= player.balance)
								{
									sendMsg(message, "\uD83C\uDFB0 Ваша ставка: " + "$" + i_dash);

									sendMsg(message, "Подбрасываем монетку...");

									Cooldown kd = new Cooldown(2, new CooldownForPlayer(player, message, i_dash, this));
									kd.startCooldown();


								}
								else
								{
									sendMsg(message, "⚠\t У вас нет такой суммы");

								}
							}
							catch (NumberFormatException e)
							{
								sendMsg(message, "⚠\tВаша ставка должна быть целым числом");
								e.printStackTrace();
								player.setState(Player.State.awaitingCommands);
								playerDAO.update(player);
							}

						}
					}
					break;
				// чтобы сначала проверялся ID пользователя, а потом если его не существует то инстанцировать для него новый ID

				//Ну и самое сложное пока что, это возможность /find ить предметы раз в 20 минут например, проверять дату нужно и время

			}

			//playerDAO.update(player);
		}
	}

	//кнопки

	public void setButtons(SendMessage sendMessage)
	{
		long id = Long.parseLong(sendMessage.getChatId());
		Player player = playerDAO.get(id);
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
			keyboardFirstRow.add(new KeyboardButton("/start"));
		}
		else
		{
			keyboardFirstRow.add(new KeyboardButton("/help"));
		}

		//keyboardFirstRow.add(new KeyboardButton("/find"));
		//добавили в спиок всех кнопок
		keyboardRowList.add(keyboardFirstRow);
		replyKeyboardMarkup.setKeyboard(keyboardRowList);

	}

	public String getBotUsername()
	{
		return "Needle";
	}

	public String getBotToken()
	{
		try
		{
			Scanner scanner = new Scanner(new File("token"));
			return scanner.nextLine();
		}
		catch (FileNotFoundException e)
		{
			e.printStackTrace();
			throw new RuntimeException("No token", e);
		}
		//токен регается через бот самого тг BotFather, там же пишется описание, название и токен
	}

	private static class CooldownForPlayer implements Runnable
	{
		private final Player player;
		private final int i_dash;
		private final Message message;
		private final Bot botik;

		CooldownForPlayer(Player player, Message message, int i_dash, Bot botik)
		{
			this.player = player;
			this.i_dash = i_dash;
			this.message = message;
			this.botik = botik;
		}

		@Override
		public void run()
		{
			CoinGame coinGame = new CoinGame(i_dash);
			if (coinGame.roll())
			{
				botik.sendMsg(message, "\uD83D\uDCB0 Вы выиграли " + "$" + i_dash);
				player.getInventory().coinWin(i_dash);
			}
			else
			{
				botik.sendMsg(message, "❌ Вы проиграли " + "$" + i_dash);
				player.getInventory().coinLose(i_dash);
			}
			botik.sendMsg(message, "Ваш баланс: " + player.balance + " \uD83D\uDCB2");
			player.setState(Player.State.awaitingCommands);
			playerDAO.update(player);
		}
	}
}



