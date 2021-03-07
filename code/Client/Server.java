import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.ArrayList;	
import java.util.HashMap;
import java.util.Map;

import org.omg.CORBA.TIMEOUT;

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
	final static int winSize = 20;
	private ArrayList<Integer> msgWins = new ArrayList<>();
	private int lastAckId;
	private int maxFileNumber;
	private String totalFileName;
	private int PORT;
	private DatagramSocket mySocket = null;
	private Map<InetAddress, Integer> clients = new HashMap<>();
	private String storagePath = "d://fly/java2/";
	
	public Server(DatagramSocket socket, InetAddress address) {
		mySocket =socket;
		DatagramPacket resPacket = new DatagramPacket(new byte[3], 3, address, 18000);
		DatagramPacket recPacket = new DatagramPacket(new byte[3], 3);
		while(true) {
			try {
				mySocket.setSoTimeout(1000);
				mySocket.send(resPacket);
				System.out.println("send ");
				mySocket.receive(recPacket);
				mySocket.setSoTimeout(0);
				break;
			}catch (Exception e) {
				e.printStackTrace();// TODO: handle exception
			}
		}
		
//		try {
//			mySocket.setSoTimeout(0);
//		}catch (Exception e) {
//			e.printStackTrace();// TODO: handle exception
//		}
		
		lastAckId = 0;
		totalFileName = "";
		maxFileNumber = -1;
	}
	
//	class SendData implements Runnable{
//		private InetAddress clientIP;
//		@SuppressWarnings("unused")
//		private int clientPort;
//		private String filePath;
//		
//		public SendData(InetAddress inetAddress, String fileName, int port) {
//			super();
//			this.clientIP = inetAddress;
//			this.filePath = storagePath + fileName;
//			this.clientPort = port;
//		}
//		
//		@Override
//		public void run() {
//			// TODO Auto-generated method stub
//			String[] args = new String[3];
//			args[0] = "lsend";
//			args[1] = clientIP.toString();
//			args[2] = filePath;
//			UDPClient.main(args);
//		}
//	}
	
	class ReceiveData implements Runnable{
		@Override
		public void run() {
			while(true) {
				try {
					System.out.println("waiting ");
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
							return;
						case -2:
							startNewReceive(clientIP, clientPort, fromClientMsg);
							continue;
						case -3:
				//			startNewSend(clientIP, clientPort, fromClientMsg);
							continue;
						default:
							if(recId < 0) continue;
							break;
					}
					
					if(recId == lastAckId) {
						moveWinForward(recId);
					}else if(recId < lastAckId) {
						ACKMsg ackMsg = new ACKMsg();
						ackMsg.setRecId(lastAckId);
						ackMsg.setRecWin(20 - msgWins.size());
						byte[] toClientMsg = ackMsg.toByte();
						InetAddress address = recPacket.getAddress();
						int port = recPacket.getPort();
						DatagramPacket resPacket = new DatagramPacket(toClientMsg, toClientMsg.length, address, port);
						mySocket.send(resPacket);
						System.out.println("send ack " + ackMsg.getRecId());
						continue;
					}else {
						pushInWin(recId);
					}
					int lastReceiveInWin = -1;
					if(msgWins.size() > 0) 
						lastReceiveInWin = msgWins.get(msgWins.size() - 1);
					maxFileNumber = Math.max(lastReceiveInWin, lastAckId);
					
					byte[] data = fromClientMsg.getData();
					System.out.println(totalFileName);
					System.out.println(data.length);
					
					File file = new File(storagePath + String.valueOf(clientIP) + "/" + totalFileName.split("\\.")[0] + "_" + totalFileName.split("\\.")[1] + "/" + String.format("%7d", recId).replace(' ', '0'));
					if(!file.getParentFile().getParentFile().exists()) 
						file.getParentFile().getParentFile().mkdirs();
					if(!file.getParentFile().exists()) 
						file.getParentFile().mkdirs();
					FileOutputStream fileOutputStream = new FileOutputStream(file, false);
					fileOutputStream.write(data);
					fileOutputStream.close();
					/*
					String info = new String(data, 0, data.length);
					System.out.println(info);*/
					System.out.println(msgWins + " // lastAckId: " + lastAckId);
					
					ACKMsg ackMsg = new ACKMsg();
					ackMsg.setRecId(lastAckId);
					ackMsg.setRecWin(20 - msgWins.size());
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
			File root = new File("d://fly/java2");
			File[] files = root.listFiles();
			for(File file : files) {
				if(!file.isDirectory() && file.getName().equals(fileName)) {
					askFail(clientIP, clientPort,-3);
					System.err.println("该文件已存在");
					System.exit(0);
					return;
				}
			}
			if(!clients.containsKey(clientIP)) {
				totalFileName = fileName;
				clients.put(clientIP, 0);
			}
			ACKMsg ackMsg = new ACKMsg();
			ackMsg.setRecId(0);
			ackMsg.setRecWin(winSize - msgWins.size());
			byte[] toClientMsg = ackMsg.toByte();
			DatagramPacket resPacket = new DatagramPacket(toClientMsg, toClientMsg.length, clientIP, clientPort);
			mySocket.send(resPacket);
			System.out.println("send ack " + 0 + " win " + (winSize - msgWins.size()));
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
		System.out.println("end a receive");
		if(mySocket == null) return;
		try {
			//System.out.println("judge ip " + clientIP);
			//System.out.println(clients);
			if(clients.containsKey(clientIP)) {
				System.out.println("begin to write");
				String TotalFileName = totalFileName;
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
		        	if(count <= maxFileNumber) {
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
		        
		        ACKMsg ackMsg = new ACKMsg();
				ackMsg.setRecId(-1);
				ackMsg.setRecWin(winSize - msgWins.size());
				byte[] toClientMsg = ackMsg.toByte();
				DatagramPacket resPacket = new DatagramPacket(toClientMsg, toClientMsg.length, clientIP, clientPort);
				mySocket.send(resPacket);
		       //若单次则无需维护
				msgWins.clear();
				totalFileName = "";
				lastAckId = 0;
				maxFileNumber = -1;
			}
			else {
				System.out.println("no ip");
			}
		}catch (Exception e) {
			e.printStackTrace();
		}
	}
	
//	private void startNewSend(InetAddress clientIP, int clientPort, SendMsg sendMsg) {
//		if(mySocket == null) return;
//		try {
//			ACKMsg ackMsg = new ACKMsg();
//			System.out.println(Arrays.toString(sendMsg.getData()));
//			String fileName = new String(sendMsg.getData());
//			File sendFile = null;
//			File root = new File("d://fly/java2");
//			File[] files = root.listFiles();
//			for(File file : files) {
//				System.out.println(file.getName() + " " + fileName);
//				if(!file.isDirectory() && file.getName().equals(fileName)) {
//					sendFile = file;
//					break;
//				}
//			}
//			ackMsg.setRecWin(20);
//			byte[] toClientMsg = ackMsg.toByte();
//			DatagramPacket resPacket = new DatagramPacket(toClientMsg, toClientMsg.length, clientIP, clientPort);
//			if(sendFile == null) {
//				askFail(clientIP, clientPort,-4);
//				return;
//			}
//			ackMsg.setRecId(0);
//			System.out.println("send 0");
//			mySocket.send(resPacket);
//			
//			//这里应该接收客户端的反馈
//			Thread sendThread = new Thread(new SendData(clientIP, sendFile.getName(), clientPort));
//			sendThread.start();
//			sendThread.join();
//		}catch (Exception e) {
//			e.printStackTrace();// TODO: handle exception
//		}
//	}
	
	public static void main(DatagramSocket socket, InetAddress address) {
		Server myServer = new Server(socket, address);
		myServer.startReceive();
		myServer.endLink();
	}
	
	private void startReceive() {
		try {
		Thread recThread = new Thread(new ReceiveData());
		recThread.start();
		recThread.join();
		}catch (Exception e) {
			e.printStackTrace();// TODO: handle exception
		}
	}
	
	private void moveWinForward(int recId) {
		if(msgWins.size() == 0) {
			lastAckId++;
		}
		else if(msgWins.size() == 1) {
			msgWins.remove(0);
			lastAckId += 2;
		}
		else {
			while(msgWins.size() >= 1) {
				if(++lastAckId == msgWins.get(0)) {
					msgWins.remove(0);
				}
				else {
					return;
				}
			}
			lastAckId++;
		}
	}
	
	private void pushInWin(int recId) {
		if(msgWins.size() == 0) {
			msgWins.add(recId);
			return;
		}
		for(int i = 0; i < msgWins.size(); i++) {
			if(msgWins.get(i) == recId) return;
		}
		msgWins.add(0);
		int index = msgWins.size() - 2;
		if(msgWins.get(index) < recId) {
			msgWins.set(index + 1, recId);
			return;
		}
		while(index >= 0) {
			if(msgWins.get(index) > recId) {
				msgWins.set(index + 1, msgWins.get(index));
				index--;
			}
			else {
				msgWins.set(index + 1, recId);
				return;
			}
		}
		msgWins.set(0, recId);
	}
}
