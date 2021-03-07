import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;

public class SendMsg {
	private int id;
	private int len;
	private byte[] data;
	
	
	public byte[] toByte() {
		try {
			ByteArrayOutputStream bous = new ByteArrayOutputStream();
			DataOutputStream dous = new DataOutputStream(bous);
			dous.writeInt(id);
			dous.writeInt(len);
			dous.write(data);
			dous.flush();
			return bous.toByteArray();
		} catch(Exception e) {
			e.printStackTrace();
		}
		return null;
	}
	
	public boolean toMsg(byte[] msg) {
		try {
			ByteArrayInputStream bins = new ByteArrayInputStream(msg);
			DataInputStream dins = new DataInputStream(bins);
			
			this.id = dins.readInt();
			this.len = dins.readInt();
			byte[] tempData = new byte[len];
			dins.readFully(tempData);
			this.data = tempData;
		} catch(Exception e) {
			e.printStackTrace();
		}
		return true;
	}
	
	public int getId() {
		return id;
	}
	public void setId(int id) {
		this.id = id;
	}
	public byte[] getData() {
		return data;
	}
	public void setData(byte[] data) {
		this.data = data;
	}
	
	public void setLen(int len) {
		this.len = len;
	}
	
}
