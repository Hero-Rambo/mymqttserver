package mqtt.service;

import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.mqtt.MqttFixedHeader;
import io.netty.handler.codec.mqtt.MqttMessageType;
import io.netty.handler.codec.mqtt.MqttPublishMessage;
import io.netty.handler.codec.mqtt.MqttPublishVariableHeader;
import io.netty.handler.codec.mqtt.MqttQoS;

import java.nio.charset.Charset;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import mqtt.entity.TransportMessage;

/**
 * 
 * @author tzj
 *
 */
public class MessageDataService {


	static ConcurrentHashMap<String, Channel> str2channel=ChannelDataService.str2channel;
	/**
	 * ÿһ���ͻ��˶��ĵ�����
	 */
	static ConcurrentHashMap<String, List<String>> submap=new ConcurrentHashMap<String, List<String>>();
	/**
	 * ������Ϣ �����
	 */
	static ConcurrentHashMap<String, Integer> topContent=new ConcurrentHashMap<String, Integer>();
	

	/**
	 * ������Ϣ  �����
	 */
	static ConcurrentHashMap<Integer, TransportMessage> messages=new ConcurrentHashMap<Integer, TransportMessage>(1);
	
	
	/**
	 * �Ѿ����͹�����Ϣ  �������
	 */
	static ConcurrentHashMap<String, ConcurrentLinkedQueue<Integer>> messageSends=
			new ConcurrentHashMap<String, ConcurrentLinkedQueue<Integer>>();
	
	
	
	/**
	 * �Կͻ��˷�������
	 * @param transportMessage
	 */
	public static void sendPubMsg(TransportMessage transportMessage) {

		Set<String> strings = submap.keySet();

		for (String key : strings) {

			
			List<String> list = submap.get(key);//keyΪ�ͻ��˱�ʶ

			if (list.contains(transportMessage.getTopName())) {
				Channel channel = str2channel.get(key);

				if (channel != null) {
					
					MqttFixedHeader Header = new MqttFixedHeader(
							MqttMessageType.PUBLISH, true,
							MqttQoS.EXACTLY_ONCE, false, 0);
					MqttPublishVariableHeader publishVariableHeader =
						new MqttPublishVariableHeader(
							transportMessage.getTopName(),
							transportMessage.getMessageId());

					MqttPublishMessage publishMessage = new MqttPublishMessage(
							Header, publishVariableHeader,
							Unpooled.copiedBuffer(transportMessage.getContent()));
					
		
					 channel.writeAndFlush(publishMessage);
	              
					System.out.println("��" + key + "������ id:"+transportMessage.getMessageId()+"��Ϣ��"
							+ new String(transportMessage.getContent(), Charset.forName("UTF-8")));
				}

			}
		}
	}
	public static ConcurrentHashMap<String, Channel> getStr2channel() {
		return str2channel;
	}
	public static void setStr2channel(ConcurrentHashMap<String, Channel> str2channel) {
		MessageDataService.str2channel = str2channel;
	}
	public static ConcurrentHashMap<String, List<String>> getSubmap() {
		return submap;
	}
	public static void setSubmap(ConcurrentHashMap<String, List<String>> submap) {
		MessageDataService.submap = submap;
	}
	public static ConcurrentHashMap<String, Integer> getTopContent() {
		return topContent;
	}
	public static void setTopContent(ConcurrentHashMap<String, Integer> topContent) {
		MessageDataService.topContent = topContent;
	}
	public static ConcurrentHashMap<Integer, TransportMessage> getMessages() {
		return messages;
	}
	public static void setMessages(
			ConcurrentHashMap<Integer, TransportMessage> messages) {
		MessageDataService.messages = messages;
	}
	public static ConcurrentHashMap<String, ConcurrentLinkedQueue<Integer>> getMessageSends() {
		return messageSends;
	}
	public static void setMessageSends(
			ConcurrentHashMap<String, ConcurrentLinkedQueue<Integer>> messageSends) {
		MessageDataService.messageSends = messageSends;
	}
	
	
	
	
}
