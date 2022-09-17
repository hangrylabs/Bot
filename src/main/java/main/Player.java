package main;

import java.util.Date;
import java.util.Objects;

public class Player
{
	private final long id;
	private final Inventory inventory;
	public Money balance;
	public Long needle;
	private String username;
	private int level;
	private int xp;
	private State state;
	public Long findExpiration = null;
	public Long pocketsExpiration = null;
	public int donateRandomer = 0;

	public Stats stats;

	public int page = 0;


	Bot host;

	// < commands args
	public Player payment_acceptor = null;
	public Integer to_place_item = null;
	public Player coffee_acceptor = null;
	public Player tea_acceptor = null;
	public String countryKey = null;
	// > commands args

	public Date sellfish = null;


	public Player(long id, Bot host)
	{
		this(id, 0, 1, "player" + id, 0, 0, State.awaitingNickname, new Inventory(), new Stats(), host);

	}

	public Player(long id, int xp, int level, String username, long balance, long needle, State state, Inventory inventory, Stats stats, Bot host)
	{
		this.id = id;
		this.username = username;
		this.balance = new Money(balance);
		this.state = state;
		this.stats = stats;
		this.inventory = inventory;
		this.xp = xp;
		this.level = level;
		this.host = host;
		this.needle = needle;
	}

	public State getState()
	{
		return state;
	}

	public void setState(State state)
	{
		this.state = state;
	}

	public long getId()
	{
		return id;
	}

	public String getUsername()
	{
		return username;
	}

	public void setUsername(String username)
	{
		this.username = username;
	}

	public Inventory getInventory()
	{
		return inventory;
	}

	public Money getMoney()
	{
		return balance;
	}

	public Stats getStats(){
		return stats;
	}

	public void levelUp()
	{
		xp -= 10 * level++;
		// back-notify the owner
		host.level_up_notification(this);
	}

	public void ach_treeHard(){
		host.achievement_notification(this, "Поздравляем! Вы заработали достижение *Зеленый лес*", 15000);
	}

	public void ach_coffee(){
		host.achievement_notification(this, "Поздравляем! Вы заработали достижение *Джон Коффи*", 7500);
	}

	public void ach_tea(){
		host.achievement_notification(this, "Поздравляем! Вы заработали достижение *Гринфилд*", 7500);
	}

	public void addXp(int xp)
	{
		this.xp += xp;
		if (this.xp >= level * 10)
			levelUp();
	}

	public int getLevel()
	{
		return level;
	}

	public int getXp()
	{
		return xp;
	}

	public enum State
	{
		awaitingNickname,
		awaitingSellArguments,
		awaitingCommands,
		awaitingChangeNickname,
		coinDash,
		shopPlaceGood_awaitingID,
		shopPlaceGood_awaitingCost,
		payAwaitingNickname,
		payAwaitingAmount,
		shopBuy,
		awaitingCoffee,
		awaitingCoffeeNote,
		awaitingTea,
		awaitingTeaNote,
		touch,
		checkAwaitingNickname,
		capitalGame,
		craftAwaitingID,
		giveID
	}

	@Override
	public boolean equals(Object o)
	{
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		Player player = (Player) o;
		return id == player.id;
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(id);
	}
}
