package mqtt.entity;

/**
 * ��Ϣ��
 * @author acer
 *
 */
public class TransportMessage {
	
	/**
	 * ��Ϣid
	 */
	int messageId;
	
	/**
	 * ��Ϣ����
	 */
	String topName;
	
	/**
	 * ��Ϣ����
	 */
	byte[] content;

	
	public int getMessageId() {
		return messageId;
	}

	public void setMessageId(int messageId) {
		this.messageId = messageId;
	}

	public String getTopName() {
		return topName;
	}

	public void setTopName(String topName) {
		this.topName = topName;
	}

	public byte[] getContent() {
		return content;
	}

	public void setContent(byte[] content) {
		this.content = content;
	}
	
	

}
