package commands;

import main.Bot;
import main.Money;
import main.Player;

import java.util.Random;

public class Capitalgame extends Command
{
	main.Capitalgame capitalgame;

	public Capitalgame(main.Capitalgame capitalgame)
	{
		this.capitalgame = capitalgame;
	}

	@Override
	public void consume(Bot host, Player player)
	{
		if (player.getLevel() < 7)
		{
			host.sendMsg(player.getId(), "⚡ Для мини-игры *Столицы* нужен 7 уровень");
		}
		else
		{
			Random ran = new Random();
			int random = ran.nextInt(capitalgame.getCountries().size());

			String country = capitalgame.getCountry(random);

			player.state = new CapitalgameState(host, player, country, player.state.base);
			host.sendMsg(player.getId(), player.state.hint);
		}
	}
}

class CapitalgameState extends State
{
	Bot host;
	Player player;
	String country;

	public CapitalgameState(Bot host, Player player, String country, BaseState base)
	{
		this.host = host;
		this.player = player;
		this.country = country;
		this.base = base;
		hint = "\uD83E\uDDE9 Столица страны: " + country;
	}

	@Override
	public void process(String arg)
	{
		Random ran = new Random();
		int money = ran.nextInt(2000);

		long id = player.getId();

		player.state = base;
		if (!arg.equals(host.capitalgame.getCapital(country)))
		{
			host.sendMsg(id, "❌ Неправильно");
		}
		else
		{
			host.sendMsg(id, "\uD83C\uDFC6 Правильно | + $" + money);
			try
			{
				player.getMoney().transfer(money);
			}
			catch (Money.MoneyException e)
			{
				e.printStackTrace();
			}
		}
	}
}
