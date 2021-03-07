import java.io.File;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.RandomAccessFile;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;

//ACK信息
//0 : 请求成功
//-1 : 文件传输结束
//-2 : 登陆失败
//-3 : 文件重名
//-4 : 文件不存在
//-6 ： 服务器连接用户已满

//ID 信息
//0 : 文件开始传输
//-1 : 上传文件结束
//-2 : 请求上传文件
//-3 ： 请求下载文件
//-5 : 登陆（密码在data）

public class UDPClient {
	private int myPort;
	private int severPort;
	private String severIP;
	private DatagramSocket dataSocket;
	private final int MSS = 1024;
	private int TimeoutInterval = 1000;
	private int EstimatedRTT = TimeoutInterval;
	private int SampleRTT = 0;
	private int DevRTT;
	private final int WINDOW_SIZE = 20;
	private int rwnd;
	private int cwnd = 1;
	private int ssthresh = 64;
	private int cwnd_state = 0; //0为慢起动阶段，1为拥塞避免阶段，2为快速恢复阶段
	private int LastByteAcked;
	private int LastByteSend;
	private int fileEndID;
	private int AckTimes;
	private HashMap<Integer, Date> sendTime = new HashMap<>();
	private boolean timer = false;
	private Date beginTimer;
	private boolean hasAckedLastByte;
	private boolean isAgain = false;
	
	private int length;
	
	private synchronized void setIsAgain(boolean b) {
		isAgain = b;
	}
	
	/**
	 * 0 为 = 1， 1 为 += 1， 2 为 *= 2， 3 为ssthresh + 3， 4 为 = ssthresh
	 * @param mode
	 */
	private synchronized void setCwnd(int mode) {
		switch (mode) {
		case 0:
			cwnd = 1;
			break;
		case 1:
			cwnd += 1;
			break;
		case 2:
			cwnd *= 2;
			break;
		case 3:
			cwnd = ssthresh + 3;
			break;
		case 4:
			cwnd = ssthresh;
			break;
		default:
			break;
		}
	}
	
	//设置状态，0为慢起动阶段，1为拥塞避免阶段，2为快速恢复阶段
	private synchronized void setCwndState(int state) {
		cwnd_state = state;
	}
	
	private synchronized void setTimer() {
		timer = true;
		beginTimer = new Date();
	}
	
	//0 to set, 1 to add
	private synchronized void addOrSetAT(int mode) {
		if(mode == 0) {
			AckTimes = 0;
		} else if(mode == 1) {
			AckTimes++;
		} else {
			System.err.println("Wrong parameter in addOrSetAT methon.");
		}
	}
	
	//0 to set, 1 to delete
	private synchronized Date dealSendTime(int mode, int key) {
		if(mode == 0) {
			sendTime.put(key, new Date());
			return new Date();
		} else{
			return sendTime.remove(key);
		}
	}
	
	class SendData implements Runnable {
		private InetAddress address;
		private RandomAccessFile randomFile;
		public SendData(InetAddress ad, RandomAccessFile rf) {
			super();
			this.address = ad;
			this.randomFile = rf;
		}
		@Override
		public void run() {
			try {
				byte[] data = readFrameFile(randomFile, LastByteSend);
				while(data != null) {
					System.out.println("A" + LastByteAcked + " " + LastByteSend + " " + rwnd + " " + cwnd + " " + isAgain + " " +fileEndID + " " + length );
					if(LastByteSend >= fileEndID && hasAckedLastByte) {
						System.out.println("break");
						break;
					}
					else if(LastByteSend >= fileEndID && !isAgain) {
						System.out.println("continue");
						continue;
					}
					//System.out.print(isAgain);
					if(isAgain) {
						System.out.println("C" + LastByteAcked + " " + LastByteSend + " " + rwnd + " " + cwnd);
						SendMsg sendMsg = new SendMsg();
						if(AckTimes >= 3) {
							data = readFrameFile(randomFile, LastByteAcked);
							addOrSetAT(0);
							if(cwnd_state == 0 || cwnd_state == 1) {
								ssthresh = cwnd > 1 ? cwnd / 2 : 1;
								setCwnd(3);
								setCwndState(2);
							}
							sendMsg.setId(LastByteAcked);
							sendMsg.setData(data);
							sendMsg.setLen(data.length);
							byte[] sendData = sendMsg.toByte();
							System.out.println(sendData.length);
							DatagramPacket packet = new DatagramPacket(sendData, sendData.length, address, severPort);
							dataSocket.send(packet);
							dealSendTime(0, LastByteAcked);
							setTimer();
						} else {
							data = readFrameFile(randomFile, LastByteAcked);
							sendMsg.setData(data);
							sendMsg.setId(LastByteAcked);
							sendMsg.setLen(data.length);
							byte[] sendData = sendMsg.toByte();
							DatagramPacket packet = new DatagramPacket(sendData, sendData.length, address, severPort);
							dataSocket.send(packet);
							dealSendTime(0, LastByteAcked);
							addOrSetAT(0);
							TimeoutInterval = 2 * TimeoutInterval;
							setTimer();
							if(cwnd_state == 0) {
								ssthresh = cwnd > 1 ? cwnd / 2 : 1;
								setCwnd(0);
							} else {
								ssthresh = cwnd > 1 ? cwnd / 2 : 1;
								setCwnd(0);
								setCwndState(0);
							}
						} 
						setIsAgain(false);
					}
					else if(LastByteSend - LastByteAcked <= Math.min(rwnd, cwnd)) {
						System.out.println("B" + LastByteAcked + " " + LastByteSend + " " + rwnd + " " + cwnd);
						SendMsg sendMsg = new SendMsg();
						sendMsg.setId(LastByteSend);
						sendMsg.setData(data);
						sendMsg.setLen(data.length);
						byte[] sendData = sendMsg.toByte();
						System.out.println(sendData.length);
						DatagramPacket packet = new DatagramPacket(sendData, sendData.length, address, severPort);
						if(!timer) {
							setTimer();
						}
						dataSocket.send(packet);
						dealSendTime(0, LastByteSend);
						LastByteSend = LastByteSend + 1;
					}
					if(LastByteSend < fileEndID)
						data = readFrameFile(randomFile, LastByteSend);
						
				}
				SendMsg sendMsg = new SendMsg();
				sendMsg.setId(-1);
				sendMsg.setData(new byte[0]);
				sendMsg.setLen(0);
				byte[] sendData = sendMsg.toByte();
				DatagramPacket packet = new DatagramPacket(sendData, sendData.length, address, severPort);
				dataSocket.send(packet);
				randomFile.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	class ReceiveACK implements Runnable {
		private InetAddress address;
		public ReceiveACK(InetAddress ad) {
			super();
			this.address = ad;
		}
		@Override
		public void run() {
			byte[] buf = new byte[1024];
			DatagramPacket packet = new DatagramPacket(buf, buf.length, address, severPort);
			while(true) {
				try {
					System.out.println("receiving ack...");
					dataSocket.receive(packet);
				} catch (SocketTimeoutException e) {
					setIsAgain(true);// TODO: handle exception
				}catch (IOException e) {
					e.printStackTrace();
				}
				ACKMsg ack = new ACKMsg();
				ack.toMsg(packet.getData());
				if(ack.getRecId() == fileEndID) {
					hasAckedLastByte = true;
					LastByteAcked = -1;
					break;
				}
				rwnd = ack.getRecWin();
				if(ack.getRecId() == LastByteAcked) {
					addOrSetAT(1);
					if(cwnd_state == 2) {
						cwnd += 1;
						setCwnd(1);
					}
				} else if(ack.getRecId() > LastByteAcked) {
					LastByteAcked = ack.getRecId();
					addOrSetAT(0);
					try {
						
						SampleRTT = (int) (dealSendTime(1, LastByteAcked - 1).getTime() - new Date().getTime());
						EstimatedRTT = (int) (0.875 * EstimatedRTT + 0.125 * SampleRTT);
						DevRTT = (int) (0.75*DevRTT + 0.25*Math.abs(SampleRTT - EstimatedRTT));
						TimeoutInterval = EstimatedRTT + 4*DevRTT;
						
						if(LastByteSend > LastByteAcked) {
							setTimer();
						}
						if(TimeoutInterval > 0) dataSocket.setSoTimeout(TimeoutInterval);
						if(cwnd_state == 0) {
							setCwnd(2);
							if(cwnd >= ssthresh) {
								setCwndState(1);
							}
						} else if(cwnd_state == 1){
							setCwnd(1);
						} else if(cwnd_state == 2) {
							setCwnd(4);
							setCwndState(1);
						}
					}catch (Exception e) {
						e.printStackTrace();// TODO: handle exception
					}
				}
				if(AckTimes >= 3 || timer && new Date().getTime() - beginTimer.getTime() >= TimeoutInterval) {
					setIsAgain(true);
					System.out.println("set again");
				}
				System.out.println("the received id is " + LastByteAcked + " win: " + ack.getRecWin() + " ACKTimes: "+ AckTimes);
			}
		}
	}
	
	public UDPClient(DatagramSocket mySocket, String address, int port) {
		this.dataSocket = mySocket;
		this.severPort = port;
		this.severIP = address;
		LastByteSend = 0;
		LastByteAcked = -1;
		rwnd = WINDOW_SIZE;		
	}
	
	public void sendFile(String fileName) throws Exception {
		DatagramPacket recpacket = new DatagramPacket(new byte[8], 8);
		dataSocket.receive(recpacket);
		System.out.println("received " + Arrays.toString(recpacket.getData()));
		InetAddress address = recpacket.getAddress();
		severPort = recpacket.getPort();
		DatagramPacket packet1 = new DatagramPacket(new byte[3], 3, address, severPort);
		dataSocket.send(packet1);
		//InetAddress address = InetAddress.getByName(severIP);
		SendMsg askToSend = new SendMsg();
		askToSend.setId(-2);
		File tempFile =new File( fileName.trim());  
        String t_fileName = tempFile.getName();  
		askToSend.setData(t_fileName.getBytes());
		askToSend.setLen(t_fileName.getBytes().length);
		byte[] sendData = askToSend.toByte();
		DatagramPacket packet = new DatagramPacket(sendData, sendData.length, address, severPort);
		System.out.println("sned -2");
		dataSocket.setSoTimeout(3000);
		dataSocket.send(packet);
		//接受服务端的信息，如果服务端为ACK 0，则说明可传输
		//DatagramPacket recpacket = new DatagramPacket(new byte[8], 8);
		boolean receivedResponse = false;
		int count = 5;   //可考虑不要
		while(!receivedResponse && count >= 0) {
			try {
				dataSocket.receive(recpacket);
				if(!packet.getAddress().equals(address)){
					throw new IOException("Received packet from an unknown source");
				}
				receivedResponse = true;				
			} catch(InterruptedIOException e) {
				System.out.println("time out");
				dataSocket.send(packet);
				System.out.println("send again");
				count--;
			}
		}
		if(count < 0) {
			System.err.println("Can not link to the Server!!!");
			return;
		}
		if(receivedResponse) {
			ACKMsg ack = new ACKMsg();
			ack.toMsg(recpacket.getData());
			int serverState = ack.getRecId();
			rwnd = ack.getRecWin();
			if(serverState == 0) {
				System.out.println("Begin uploading the file!");
				RandomAccessFile randomFile = new RandomAccessFile(fileName, "r");
				LastByteSend = 0;
				System.out.println(tempFile.length());
				length = (int)tempFile.length();
				fileEndID = (int) (randomFile.length()/MSS + 1);
				hasAckedLastByte = false;
				Thread sendThread = new Thread(new SendData(address, randomFile));
				Thread recThread = new Thread(new ReceiveACK(address));
				sendThread.start();
				recThread.start();
				sendThread.join();
				recThread.join();
			}
			//ACK信息
			//0 : 请求成功
			//-1 : 文件传输结束
			//-2 : 登陆失败
			//-3 : 文件重名
			//-4 : 文件不存在
			//-6 ： 服务器连接用户已满
			else {
				switch (serverState) {
				case -3:
					System.err.println("该文件已存在...");
					break;
				case -6:
					System.err.println("服务器连接用户已满...");
				default:
					System.err.println("未知错误...");
					break;
				}
			}
		}	
	}
	public byte[] readFrameFile(RandomAccessFile randomFile, int number) throws IOException {
		long fileLength = randomFile.length();
		long beginIndex = number * MSS;
		if(beginIndex >= fileLength) 
			return null; 
		int byteSize = (int) (fileLength - beginIndex >= MSS ? MSS : fileLength - beginIndex);
		randomFile.seek(beginIndex);
		byte[] bytes = new byte[byteSize];

		if(randomFile.read(bytes) == -1) {
			throw new IOException("Wrong in UDPClient;readFrameFile");
		}
		return bytes;
	}
	public void getFile(String fileName) throws Exception {
		InetAddress address = InetAddress.getByName(severIP);
		SendMsg askToSend = new SendMsg();
		askToSend.setId(-3);
		askToSend.setData(fileName.getBytes());
		askToSend.setLen(fileName.length());
		byte[] sendData = askToSend.toByte();
		DatagramPacket packet = new DatagramPacket(sendData, sendData.length, address, severPort);
		dataSocket.send(packet);
		//接受服务端的信息，如果服务端为ACK 0，则说明可传输/下载
		DatagramPacket recpacket = new DatagramPacket(new byte[8], 8);
		dataSocket.setSoTimeout(3000);
		boolean receivedResponse = false;
		int count = 5;
		while(!receivedResponse && count >= 0) {
			try {
				dataSocket.receive(recpacket);
				if(!packet.getAddress().equals(address)){
					throw new IOException("Received packet from an unknown source");
				}
				receivedResponse = true;				
			} catch(InterruptedIOException e) {
				System.out.println("time out");
				dataSocket.send(packet);
				count--;
			}
		}
		if(count < 0) {
			System.err.println("Can not link to the Server!!!");
			return;
		}
		if(receivedResponse) {
			ACKMsg ack = new ACKMsg();
			ack.toMsg(recpacket.getData());
			if(ack.getRecId() != 0 && ack.getRecId() != -2) {
				System.out.println("The command was denied! error: " + ack.getRecId());
			} else {
//				LastByteAcked = ack.getRecId();
				System.out.println("Begin downloading the file!");
				Server.main(null);
//				RandomAccessFile randomFile = new RandomAccessFile(fileName, "r");
//				Thread sendThread = new Thread(new SendData(address, randomFile));
//				Thread recThread = new Thread(new ReceiveACK(address));
//				sendThread.start();
//				recThread.start();
//				sendThread.join();
//				recThread.join();
			}
		}
	}
	public void endLink() {
		dataSocket.close();
	}
	public static void main(DatagramSocket mySocket, String address, int port, String filePath) {
		System.out.println("inter UDPClient");
		UDPClient client = new UDPClient(mySocket, address, port);
		try {
			client.sendFile(filePath);
		}catch (Exception e) {
			e.printStackTrace();
			// TODO: handle exception
		}
		client.endLink();
	}	
}