import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;

public class ACKMsg {
	int recId = -2;
	int recWin = 20;
	
	public byte[] toByte() {
		try {
			ByteArrayOutputStream stream = new ByteArrayOutputStream();
			DataOutputStream outputStream = new DataOutputStream(stream);
			outputStream.writeInt(recId);
			outputStream.writeInt(recWin);
			outputStream.flush();
			
			return stream.toByteArray();
		}catch (Exception e) {
			e.printStackTrace();
			return null;// TODO: handle exception
		}
	}
	
	public boolean toMsg(byte[] sendMsg) {
		try {
			ByteArrayInputStream stream = new ByteArrayInputStream(sendMsg);
			DataInputStream inputStream = new DataInputStream(stream);
			recId = inputStream.readInt();
			recWin = inputStream.readInt();
			return true;
		}catch (Exception e) {
			e.printStackTrace();
			return false;// TODO: handle exception
		}
	}
	
	public int getRecId() {
		return recId;
	}
	
	public int getRecWin() {
		return recWin;
	}
	
	public void setRecId(int recId) {
		this.recId = recId;
	}
	
	public void setRecWin(int recWin) {
		this.recWin = recWin;
	}
}
