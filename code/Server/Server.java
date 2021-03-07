import java.io.File;
import java.io.FileOutputStream;
import java.io.RandomAccessFile;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.TreeMap;

/*
 * return  message			get		message
 * 0		successful		-5		log in
 * -1		receive end		
 * -2		login fail		-1		end file
 * -3		name inflect	-2		start reveive
 * -4		no file			-3		start sned
 * -6		client full
 */

public class Server {
	
	final static int MAX_LEN = 1500;
	final static int MAX_CLIENT_COUNT = 10;
	final static int winSize = 20;
	@SuppressWarnings("unchecked")
	private ArrayList<Integer>[] msgWins = new ArrayList[MAX_CLIENT_COUNT];
	private int[] lastAckId = new int[MAX_CLIENT_COUNT];
	private int[] maxFileNumber = new int[MAX_CLIENT_COUNT];
	private String[] fileNames = new String[MAX_CLIENT_COUNT];
	private int PORT;
	private DatagramSocket mySocket = null;
	private Map<InetAddress, Integer> clients = new HashMap<>();
	private TreeMap<Integer, InetAddress> clientsByID = new TreeMap<>();
	private String storagePath = "d://fly/java/";
	private int newPort;
	
	public Server(int serverPort) {
		PORT = serverPort;
		try {
			DatagramSocket socket = new DatagramSocket(PORT);
			mySocket =socket;
		}catch (SocketException e) {
			e.printStackTrace();// TODO: handle exception
		}
		for(int i = 0; i < MAX_CLIENT_COUNT; i++) {
			lastAckId[i] = 0;
			fileNames[i] = "";
			msgWins[i] = new ArrayList<>();
			maxFileNumber[i] = -1;
		}
	}
	
	class SendData implements Runnable{
		private InetAddress clientIP;
		private String filePath;
		private int clientPort;
		
		public SendData(InetAddress inetAddress, String fileName, int port) {
			super();
			this.clientIP = inetAddress;
			this.filePath = storagePath + fileName;
			this.clientPort = port;
		}
		
		@Override
		public void run() {
			// TODO Auto-generated method stub
			System.out.println("client IP: " + clientIP.toString().split("/")[1] + " client port: " + clientPort);
			newPort = PORT + 5;
			try {
				UDPClient.main(new DatagramSocket(18000), clientIP.toString().split("/")[1], clientPort, filePath);
			}catch (Exception e) {
				System.out.println("The downloading is busy, please try later.");// TODO: handle exception
			}
			return;
		}
	}
	
	class ReceiveData implements Runnable{
		@Override
		public void run() {
			// TODO Auto-generated method stub
			while(true) {
				try {
					System.out.println("waiting");
					byte[] message = new byte[MAX_LEN];
					DatagramPacket recPacket = new DatagramPacket(message, message.length);
					mySocket.receive(recPacket);
					SendMsg fromClientMsg = new SendMsg();
					fromClientMsg.toMsg(message);
					int recId = fromClientMsg.getId();
					System.out.println("recId: " + recId);
					InetAddress clientIP = recPacket.getAddress();
					int clientPort = recPacket.getPort();
					switch(recId) {
						case -1:
							endaReceive(clientIP, clientPort);
							continue;
						case -2:
							startNewReceive(clientIP, clientPort, fromClientMsg);
							continue;
						case -3:
							startNewSend(clientIP, clientPort, fromClientMsg);
							continue;
						default:
							if(recId < 0) continue;
							break;
					}
					if(!clients.containsKey(clientIP)) continue;
					int clientId = clients.get(clientIP);
					if(recId == lastAckId[clientId]) {
						moveWinForward(recId, clientId);
					}else if(recId < lastAckId[clientId]){
						ACKMsg ackMsg = new ACKMsg();
						ackMsg.setRecId(lastAckId[clientId]);
						ackMsg.setRecWin(20 - msgWins[clientId].size());
						byte[] toClientMsg = ackMsg.toByte();
						InetAddress address = recPacket.getAddress();
						int port = recPacket.getPort();
						DatagramPacket resPacket = new DatagramPacket(toClientMsg, toClientMsg.length, address, port);
						mySocket.send(resPacket);
						System.out.println("send ack " + ackMsg.getRecId());
						continue;
					}else {
						pushInWin(recId, clientId);
					}
					int lastReceiveInWin = -1;
					if(msgWins[clientId].size() > 0) lastReceiveInWin = msgWins[clientId].get(msgWins[clientId].size() - 1);
					maxFileNumber[clientId] = Math.max(lastReceiveInWin, lastAckId[clientId]);
					byte[] data = fromClientMsg.getData();
					//System.out.println(clients);
					//System.out.println(fileNames[clientId]);
					//System.out.println(data.length);
					File file = new File(storagePath + String.valueOf(clientIP) + "/" + fileNames[clientId].split("\\.")[0] + "_" + fileNames[clientId].split("\\.")[1] + "/" + String.format("%7d", recId).replace(' ', '0'));
					if(!file.getParentFile().getParentFile().exists()) file.getParentFile().getParentFile().mkdirs();
					if(!file.getParentFile().exists()) file.getParentFile().mkdirs();
					FileOutputStream fileOutputStream = new FileOutputStream(file, false);
					fileOutputStream.write(data);
					fileOutputStream.close();
					/*
					String info = new String(data, 0, data.length);
					System.out.println(info);*/
					System.out.println(msgWins[clientId] + " // lastAckId: " + lastAckId[clientId] );
					
					ACKMsg ackMsg = new ACKMsg();
					ackMsg.setRecId(lastAckId[clientId]);
					ackMsg.setRecWin(20 - msgWins[clientId].size());
					byte[] toClientMsg = ackMsg.toByte();
					InetAddress address = recPacket.getAddress();
					int port = recPacket.getPort();
					DatagramPacket resPacket = new DatagramPacket(toClientMsg, toClientMsg.length, address, port);
					mySocket.send(resPacket);
					System.out.println("send ack " + ackMsg.getRecId());
				}catch (Exception e) {
					e.printStackTrace();// TODO: handle exception
				}
			}
		}
	}
	
	private void endLink() {
		if(mySocket == null) return;
		mySocket.close();
	}
	
	private void startNewReceive(InetAddress clientIP, int clientPort, SendMsg sendMsg) {
		if(mySocket == null) return;
		try {
			
			String fileName = new String(sendMsg.getData());
			File root = new File("d://fly/java");
			File[] files = root.listFiles();
			for(File file : files) {
				if(!file.isDirectory() && file.getName().equals(fileName)) {
					askFail(clientIP, clientPort,-3);
					return;
				}
			}
			if(!clients.containsKey(clientIP)) {
				if(clients.size() == MAX_CLIENT_COUNT) {
					askFail(clientIP, clientPort,-6);
					return;
				}
				if(clients.size() == 0) {
					clients.put(clientIP, 0);
					clientsByID.put(0, clientIP);
					fileNames[0] = fileName;
				}
				else {
					int minID = clientsByID.firstKey();
					int maxID = clientsByID.lastKey();
					if(minID > 0) {
						clients.put(clientIP, minID - 1);
						clientsByID.put(minID - 1, clientIP);
						fileNames[minID - 1] = fileName;
					}else {
						clients.put(clientIP, maxID + 1);
						clientsByID.put(maxID + 1, clientIP);
						fileNames[maxID + 1] = fileName;
					}
				}
			}
			else {
				int clientId = clients.get(clientIP);
				msgWins[clientId].clear();
				lastAckId[clientId] = 0;
				maxFileNumber[clientId] = -1;
			}
			ACKMsg ackMsg = new ACKMsg();
			ackMsg.setRecId(0);
			ackMsg.setRecWin(winSize - msgWins[clients.get(clientIP)].size());
			byte[] toClientMsg = ackMsg.toByte();
			DatagramPacket resPacket = new DatagramPacket(toClientMsg, toClientMsg.length, clientIP, clientPort);
			mySocket.send(resPacket);
			System.out.println("send ack " + 0 + " win " + (winSize - msgWins[clients.get(clientIP)].size()));
		}catch (Exception e) {
			e.printStackTrace();// TODO: handle exception
		}
	}
	
	private void askFail(InetAddress clientIP, int clientPort, int error) {
		try {
			System.out.println(error);
			ACKMsg ackMsg = new ACKMsg();
			ackMsg.setRecId(error);
			ackMsg.setRecWin(0);
			byte[] toClientMsg = ackMsg.toByte();
			DatagramPacket resPacket = new DatagramPacket(toClientMsg, toClientMsg.length, clientIP, clientPort);
			mySocket.send(resPacket);
		}catch (Exception e) {
			e.printStackTrace();// TODO: handle exception
		}
	}
	
	private void endaReceive(InetAddress clientIP, int clientPort) {
		if(mySocket == null) return;
		try {
			if(clients.containsKey(clientIP)) {
				int clientId = clients.get(clientIP);
				String TotalFileName = fileNames[clientId];
				String fileName = TotalFileName.split("\\.")[0];
				String typeName = TotalFileName.split("\\.")[1];
				String filePath = storagePath + String.valueOf(clientIP) + "/" + fileName + "_" + typeName;
				File writeFile = new File(storagePath + TotalFileName);
				writeFile.createNewFile();
				FileOutputStream writer = new FileOutputStream(writeFile, true);
		        File root = new File( filePath );
		        File[] files = root.listFiles();
		        int count = 0;
		        for ( File file : files )
		        {
		        	//System.out.println(file.getName() + " " + maxFileNumber[clientId]);
		        	if(count <= maxFileNumber[clientId]) {
			        	RandomAccessFile randomFile = new RandomAccessFile(file, "r");
			        	long length = randomFile.length();
			        	byte[] data = new byte[(int)length];
			            while (randomFile.read(data) != -1) {
			                writer.write(data);
			            }
			            randomFile.close();
			            count++;
		        	}
		        	file.delete();
		        }
		        File storageClientFile =  root.getParentFile();
		        root.delete();
		        if(storageClientFile.listFiles().length == 0)
		        	storageClientFile.delete();
		        writer.close();
		        System.out.println("write end!");
		       
		       
		        clientsByID.remove(clientId);
				clients.remove(clientIP);
				msgWins[clientId].clear();
				fileNames[clientId] = "";
				lastAckId[clientId] = 0;
				maxFileNumber[clientId] = -1;
			}
		}catch (Exception e) {
			e.printStackTrace();// TODO: handle exception
		}
	}
	
	private void startNewSend(InetAddress clientIP, int clientPort, SendMsg sendMsg) {
		if(mySocket == null) return;
		try {
			ACKMsg ackMsg = new ACKMsg();
			System.out.println(Arrays.toString(sendMsg.getData()));
			String fileName = new String(sendMsg.getData());
			File sendFile = null;
			File root = new File("d://fly/java");
			File[] files = root.listFiles();
			for(File file : files) {
				System.out.println(file.getName() + " " + fileName);
				if(!file.isDirectory() && file.getName().equals(fileName)) {
					sendFile = file;
					break;
				}
			}
			ackMsg.setRecWin(20);
			byte[] toClientMsg = ackMsg.toByte();
			DatagramPacket resPacket = new DatagramPacket(toClientMsg, toClientMsg.length, clientIP, clientPort);
			if(sendFile == null) {
				askFail(clientIP, clientPort,-4);
				return;
			}
			ackMsg.setRecId(0);
			System.out.println("send 0");
			mySocket.send(resPacket);
			
			//这里应该接收客户端的反馈
			Thread sendThread = new Thread(new SendData(clientIP, sendFile.getName(), clientPort));
			sendThread.start();
			sendThread.join();
		}catch (Exception e) {
			e.printStackTrace();// TODO: handle exception
		}
	}
	
	public static void main(String[] args) {
		Server myServer = new Server(12000);
		try {
			Thread recThread = new Thread(myServer.new ReceiveData());
			recThread.start();
			recThread.join();
		}catch (Exception e) {
			e.printStackTrace();// TODO: handle exception
		}
		Scanner input = new Scanner(System.in);
		String command;
		command = input.nextLine();
		if(command.equals("exit")) {
			myServer.endLink();
			input.close();
		}
	}
	
	private void moveWinForward(int recId, int clientId) {
		if(msgWins[clientId].size() == 0) {
			lastAckId[clientId]++;
		}
		else if(msgWins[clientId].size() == 1) {
			msgWins[clientId].remove(0);
			lastAckId[clientId] += 2;
		}
		else {
			while(msgWins[clientId].size() >= 1) {
				if(++lastAckId[clientId] == msgWins[clientId].get(0)) {
					msgWins[clientId].remove(0);
				}
				else {
					return;
				}
			}
			lastAckId[clientId]++;
		}
	}
	
	private void pushInWin(int recId, int clientId) {
		if(msgWins[clientId].size() == 0) {
			msgWins[clientId].add(recId);
			return;
		}
		for(int i = 0; i < msgWins[clientId].size(); i++) {
			if(msgWins[clientId].get(i) == recId) return;
		}
		msgWins[clientId].add(0);
		int index = msgWins[clientId].size() - 2;
		if(msgWins[clientId].get(index) < recId) {
			msgWins[clientId].set(index + 1, recId);
			return;
		}
		while(index >= 0) {
			if(msgWins[clientId].get(index) > recId) {
				msgWins[clientId].set(index + 1, msgWins[clientId].get(index));
				index--;
			}
			else {
				msgWins[clientId].set(index + 1, recId);
				return;
			}
		}
		msgWins[clientId].set(0, recId);
	}
}
