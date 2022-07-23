import java.io.IOException;

import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Random;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
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

public class Bot extends TelegramLongPollingBot{
		
	Inv inv = new Inv();
	int co = 0;
	
	
	public static void main(String[] args) throws IOException {
		
		ApiContextInitializer.init(); //������������� API
		TelegramBotsApi telegramBotsApi = new TelegramBotsApi(); //�������� ������� � API
		try {
			telegramBotsApi.registerBot(new Bot());
			
		}catch (TelegramApiRequestException e) {
			e.printStackTrace(); 
		}
			
		
	}
	
		//��� ��� ����� ��������
		public void sendMsg(Message message, String text) {
		
			
			
			SendMessage sendMessage = new SendMessage();
			sendMessage.enableMarkdown(true);
			
			//��� ����, ����� ���� ������� ���� ��������
			sendMessage.setChatId(message.getChatId());
			
			//���������, �� ����� ��������� ��������
			//sendMessage.setReplyToMessageId(message.getMessageId());
			
			sendMessage.setText(text);
			try {
				//�������� ������ � ��������� � ��� ���������
				setButtons(sendMessage);
				sendMessage(sendMessage);
			} catch (TelegramApiException e) {
				e.printStackTrace();
			}
			
			
		
		}
		
	
	//����� ��� ������ ��������� � ����������
	public void onUpdateReceived(Update update) {
		
		Message message = update.getMessage();
	
		if (message != null && message.hasText()){
			
			System.out.println("�������: " + message.getText());
			switch(message.getText()) {
			
				
			case "/start":
				long id = message.getChatId();

				sendMsg(message, "��� �������� ��������� �������: \n /help - ������ \n" + "/inv - ���������� ��������� \n" + "/find - ������ ����� �������"); 
						
				break;
				
			case "/inv":
				if(inv.getInvSize() != 0) {
					
			
				sendMsg(message, "��� ���������: ");
	
				
				//sendMsg(message, "\u26BD");

					sendMsg(message, "\n" + inv.showInventory() + "\n");
	
				}else {
					sendMsg(message, "��� ��������� ���� ");
				}
				break;
				
			case "/find":

				Item i = inv.findItem();
				sendMsg(message, "�� �����: " + i.getTitle() + " |" + i.getRarity() + "| " + 
				i.getCost() + "$");
				
				System.out.println("������� /find: " + co + " " + message.getChatId());
				System.out.println("�������: " + message.getText());
				co++;
				
				
				
				break;
			case "/balance":
				
				sendMsg(message, "��� ������: " + inv.getBalance() + "$");
				
				break;
			
			default: 
				sendMsg(message, "����������� �������");
				break;
				
				/*
				 * TODO LIST
				 * 
				 * 
				 * ����� ������� ��� ������� ������������ ���������� ��������� Inv
				 * ����� ������� ���������� ID ������������, � ����� ���� ��� �� ���������� �� �������������� ��� ���� ����� ID
				 * message.getChatId() - ���������� ID ������ � �������� �������
				 * 
				 * �������� ����� �������� ����� User � �����-�� ��������� �������, ����� ����� ����������� ������ ������������ �����������
				 * �� ���� ������ � ���� ������ ���, ��������� ���.
				 * 
				 * �������� ������� /allplayers ����� ������� ���� ���������� ����
				 * 
				 * �� � ����� ������� ���� ���, ��� ����������� /find ��� �������� ��� � 20 ����� ��������, ��������� ���� ����� � �����
				 */
					
					
			}
		}
		
	}
	
	//������
	
	public void setButtons(SendMessage sendMessage) {
		//�������������� ���������� 
		ReplyKeyboardMarkup replyKeyboardMarkup = new ReplyKeyboardMarkup();
		//��������� ��������
		sendMessage.setReplyMarkup(replyKeyboardMarkup);
		//����� ���������� (����� ��� ���)
		replyKeyboardMarkup.setSelective(true);
		replyKeyboardMarkup.setResizeKeyboard(true);
		//�������� ��� �� �������� ����� �������������
		replyKeyboardMarkup.setOneTimeKeyboard(true);
		
		List<KeyboardRow> keyboardRowList = new ArrayList<>();
		KeyboardRow keyboardFirstRow = new KeyboardRow();
		
		//�������� ����� ������ � ������ ���
		keyboardFirstRow.add(new KeyboardButton("/inventory"));
		keyboardFirstRow.add(new KeyboardButton("/find"));
		//�������� � ����� ���� ������
		keyboardRowList.add(keyboardFirstRow);
		
	}
	

	public String getBotUsername() {
		
		return "Needle";
	}

	 
	public String getBotToken() {
	
		return "1286692994:AAFxHRBuJ1FIzQFBizgPHrng37ctoFtzLLY";
		//����� �������� ����� ��� ������ �� BotFather, ��� �� ������� ��������, �������� � �����
	}

}
