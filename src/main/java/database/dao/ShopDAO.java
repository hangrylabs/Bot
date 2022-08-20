package database.dao;

import main.Bot;
import main.Item;
import main.Player;
import main.ShopItem;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class ShopDAO
{
	private final Connection connection;
	ItemDAO item;
    PlayerDAO playerDAO;

	Bot host;

	public ShopDAO(Connection connection, Bot host)
	{
		this.connection = connection;
		item = new ItemDAO(this.connection);
		this.host = host;
        playerDAO = new PlayerDAO(connection, host);
	}

	public void put(ShopItem shopItem)
	{
		try
		{
			PreparedStatement ps = connection.prepareStatement("insert into shop(item_id, cost, seller_id) values (?, ?, ?);");
			ps.setLong(1, shopItem.getItem().getId());
			ps.setInt(2, shopItem.getCost());
			ps.setLong(3, shopItem.getSeller().getId());
			ps.execute();
		}
		catch (SQLException e)
		{
			System.err.println(e.getErrorCode());
			e.printStackTrace();
			throw new RuntimeException("SQL Exception in ShopDAO");
		}
	}

	public List<ShopItem> getBySellerName(String sellerName)
	{
		List<ShopItem> result = new ArrayList<>();
		try
		{
			PreparedStatement ps = connection.prepareStatement("select * from shop where sellerName = ?;");
			ps.setString(1, sellerName);
			ResultSet rs = ps.executeQuery();
			while (rs.next())
			{
				result.add(form(rs));
			}
			return result;
		}
		catch (SQLException e)
		{
			e.printStackTrace();
			throw new RuntimeException("SQL Exception in ShopDAO", e);
		}
	}

	public List<ShopItem> getAll()
	{
		List<ShopItem> result = new ArrayList<>();
		try
		{
			PreparedStatement ps = connection.prepareStatement("select * from shop");
			ResultSet rs = ps.executeQuery();
			while (rs.next())
			{
				result.add(form(rs));
			}
			return result;
		}
		catch (SQLException e)
		{
			e.printStackTrace();
			throw new RuntimeException("SQL Exception in ShopDAO", e);
		}
	}

	public ShopItem getByID(int index)
	{
		ShopItem s = null;
		try
		{
			PreparedStatement ps = connection.prepareStatement("select * from shop where id = ?");
			ps.setInt(1, index);
			ResultSet rs = ps.executeQuery();
			if (rs.next())
			{
				s = form(rs);
			}
			else
			{
				throw new IndexOutOfBoundsException();
			}

		}
		catch (SQLException e)
		{
			e.printStackTrace();
			throw new RuntimeException("SQL Exception in ShopDAO", e);
		}
		return s;
	}

	/*
	public void update(ShopItem shopItem){
		try {
			PreparedStatement ps = connection.prepareStatement("update shop set cost = ?, where id = ?");
			ps.setString(1, shopItem.getItem().getTitle());
			ps.setInt(2, shopItem.getCost());
			ps.setString(3, shopItem.getSeller());


		}catch (SQLException e){
			e.printStackTrace();
		}
	}

	*/
	public void delete(int id)
	{
		try
		{
			PreparedStatement ps = connection.prepareStatement("delete from shop where id = ?;");
			ps.setInt(1, id);
			ps.execute();
		}
		catch (SQLException e)
		{
			System.err.println(e.getErrorCode());
			e.printStackTrace();
			throw new RuntimeException("SQL Exception in ShopDAO");
		}
	}

	private ShopItem form(ResultSet rs) throws SQLException
	{
		int id = rs.getInt("id");
        Item item = this.item.get(rs.getLong("item_id"));
		int cost = rs.getInt("cost");
        Player seller = playerDAO.get_by_id(rs.getLong("seller_id"));
		return new ShopItem(id, item, cost, seller);
	}
}
