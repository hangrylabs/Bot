import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;

public class Inv {
	

	private ArrayList<Item> inventory = new ArrayList<Item>();
	private ArrayList<Item> allItems = new ArrayList<Item>();

	private static int balance;
	private static Random ran;
	
	public Inv(){
		balance = 0;
		allItems.add(new Item("������", '�', 20));
		allItems.add(new Item("��������� ������", '�', 300));
		allItems.add(new Item("�������� \"Nosebleed\"", '�', 1000));
		allItems.add(new Item("������", '�', 35));
		allItems.add(new Item("�������� \"Drain\"", '�', 55));
		allItems.add(new Item("�����", '�', 5));
		allItems.add(new Item("����� \"������\"", '�', 75));
		allItems.add(new Item("�����", '�', 5));
		allItems.add(new Item("�����", '�', 5));
		allItems.add(new Item("��������� � �������", '�', 25));
		allItems.add(new Item("������", '�', 10));
		allItems.add(new Item("����� �������", '�', 5));
		allItems.add(new Item("����� �������", '�', 10));
		allItems.add(new Item("������� �������", '�', 10));
		allItems.add(new Item("������ �������", '�', 10));
		allItems.add(new Item("������� �������", '�', 10));
		allItems.add(new Item("������� \"�����\"", '�', 60));
		allItems.add(new Item("������� \"������\"", '�', 65));
		allItems.add(new Item("������ �����", '�', 5));
		allItems.add(new Item("���������", '�', 10));
		allItems.add(new Item("������", '�', 700));
	}
	
	public Item findItem() {
		Random ran = new Random();
		int randomNumber = ran.nextInt(allItems.size());
		Item currentItem = allItems.get(randomNumber);
		inventory.add(currentItem);
		return currentItem;
	}
		
	public String showInventory() {
		
		return inventory.toString();
		
	}
	
	public int getInvSize() {
		return inventory.size();
	}
	
	public int getBalance() {
		return balance;
	}
	
}
