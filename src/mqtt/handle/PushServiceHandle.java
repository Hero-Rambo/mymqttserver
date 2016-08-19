package mqtt.handle;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.handler.codec.mqtt.MqttFixedHeader;
import io.netty.handler.codec.mqtt.MqttMessage;
import io.netty.handler.codec.mqtt.MqttMessageIdVariableHeader;
import io.netty.handler.codec.mqtt.MqttMessageType;
import io.netty.handler.codec.mqtt.MqttPubAckMessage;
import io.netty.handler.codec.mqtt.MqttPublishMessage;
import io.netty.handler.codec.mqtt.MqttQoS;

import java.io.UnsupportedEncodingException;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.apache.ibatis.session.SqlSession;

import mqtt.db.DBSessionFactory;
import mqtt.entity.MsgRep;
import mqtt.entity.MsgRepExample;
import mqtt.entity.TransportMessage;
import mqtt.service.ChannelDataService;
import mqtt.service.MessageDataService;

public class PushServiceHandle extends ChannelInboundHandlerAdapter {

	
	final  String saveMsg="mqtt.entity.MsgRepMapper.insert";
	
	final  String queryMsg="mqtt.entity.MsgRepMapper.selectByExample";
	
	ConcurrentHashMap<String, Channel> str2channel;
	
	ConcurrentHashMap<Channel, String> channel2str;

	/**
	 * ÿһ���ͻ��˶��ĵ�����
	 */
	ConcurrentHashMap<String, List<String>> submap;
	
	/**
	 * ������Ϣ �����
	 */
	ConcurrentHashMap<String, Integer> topContent;
	/**
	 * ������Ϣ  �����
	 */
	ConcurrentHashMap<Integer, TransportMessage> messages;
	
	/**
	 * �Ѿ����͹�����Ϣ  �������
	 */
	ConcurrentHashMap<String, ConcurrentLinkedQueue<Integer>> messageSends;
	
	NioEventLoopGroup dboptgroup;
	
	
	
	public PushServiceHandle(NioEventLoopGroup dboptgroup){
		
		messages=MessageDataService.getMessages();
		messageSends=MessageDataService.getMessageSends();
		topContent=MessageDataService.getTopContent();
		
		str2channel=ChannelDataService.getStr2channel();
		channel2str=ChannelDataService.getChannel2str();
		
		this.dboptgroup=dboptgroup;
	
		
	}
	
	
	public void channelRead(ChannelHandlerContext ctx, Object msg) {

		if (msg instanceof MqttMessage) {

			MqttMessage message = (MqttMessage) msg;
			MqttMessageType messageType = message.fixedHeader().messageType();

			switch (messageType) {
			
			case PUBLISH://�ͻ��˷�����ͨ��Ϣ
				MqttPublishMessage messagepub = (MqttPublishMessage) msg;
				pub(ctx, messagepub);
				break;
				
			case  PUBREL: //�ͻ��˷����ͷ�
				pubrel(ctx, message);
				break;
			case PUBREC://�ͻ��˷����յ�
				pubrec(ctx, message);
			default:
				ctx.fireChannelRead(msg);
				break;
			}

		}

		else
			ctx.channel().close();
	}
	
	/**
	 * ������Ϣ
	 * 
	 * ���ݿͻ��˷�����QOS���� ������Ӧ�� pub�ظ��������� ������Ӧ��QOS����
	 * ȡ�����ݰ����������  �洢
	 * @param ctx
	 * @param messagepub
	 */
	private void pub(final ChannelHandlerContext ctx,
			MqttPublishMessage messagepub) {
		
		final int  messageid=messagepub.variableHeader().messageId();
		
		
		MqttQoS mqttQoS=messagepub.fixedHeader().qosLevel();
		
		MqttFixedHeader fixedHeader=null;
		boolean  islastone=false; 
		if(mqttQoS.value()<=1){
			//���Ǽ�����ߵ�QOS  ���� puback ����
			fixedHeader	= new MqttFixedHeader(
					MqttMessageType.PUBACK, false, MqttQoS.AT_MOST_ONCE, false, 0);
		}
		else{
			//�����ͷ����յ�  QOS�����2
			fixedHeader	= new MqttFixedHeader(
					MqttMessageType.PUBREC, false, MqttQoS.EXACTLY_ONCE, false, 0);
		}
		
		

		MqttMessageIdVariableHeader connectVariableHeader = MqttMessageIdVariableHeader
				.from(messageid);

		MqttPubAckMessage ackMessage = new MqttPubAckMessage(fixedHeader,
				connectVariableHeader);
		ctx.writeAndFlush(ackMessage);
		
		
		
		final	String topname = messagepub.variableHeader().topicName();

		 ByteBuf buf = messagepub.content();
		final byte[] bs = new byte[buf.readableBytes()];
		
		
		
		TransportMessage message=new TransportMessage();
		message.setContent(bs);
		message.setMessageId(messageid);
		message.setTopName(topname);
		buf.readBytes(bs);
		
		
		dboptgroup.submit(new Runnable() {
			
			@Override
			public void run() {
				
				SqlSession session= DBSessionFactory.getSqlSession();
				MsgRep msgRep=new MsgRep();
				msgRep.setContent(bs);
				msgRep.setTopname(topname);
				msgRep.setMessageid(messageid);
				session.insert(saveMsg,msgRep);
				session.commit();
				
			}
		});
		messages.put(messageid, message);
		
		
	   if(islastone) {//���ֻ����ͨ�ķ��� QOS����͵Ļ���ֱ�ӷ�������Ϣ
		   MessageDataService.sendPubMsg( message);
		   String iden=channel2str.get(ctx.channel());
		   saveSendMsg(messageid, iden);
	   }

		try {
			System.out.println("����������Ϊ" +topname+ new String(bs, "UTF-8"));
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}

		

	}
	
	
	/**
	 * ����ͻ��˹����ķ����ͷ�
	 * 
	 * �Կͻ��˸���QOS�����ͷ������
	 * �Զ��ĵ����ߵĿͻ��˷�����Ϣ
	 * @param ctx
	 * @param messagepub
	 */
	private void pubrel(final ChannelHandlerContext ctx,
			MqttMessage messagepub) {
		
		MqttMessageIdVariableHeader variableHeader=(MqttMessageIdVariableHeader)messagepub.variableHeader();

		final	Integer messageid=variableHeader.messageId();
		
		MqttQoS mqttQoS=messagepub.fixedHeader().qosLevel();
		
		MqttFixedHeader fixedHeader=null;
		
		if(mqttQoS.value()<=1)
			//���ͷ����յ�  QOS�����1
			fixedHeader	= new MqttFixedHeader(
					MqttMessageType.PUBCOMP, false, MqttQoS.AT_MOST_ONCE, false, 0);
		else
			//�����ͷ����յ�  QOS�����2
			fixedHeader	= new MqttFixedHeader(
					MqttMessageType.PUBCOMP, false, MqttQoS.EXACTLY_ONCE, false, 0);
		
		

		MqttMessageIdVariableHeader connectVariableHeader = MqttMessageIdVariableHeader
				.from(messageid);

		MqttPubAckMessage ackMessage = new MqttPubAckMessage(fixedHeader,
				connectVariableHeader);
		ctx.writeAndFlush(ackMessage);
		
		
		TransportMessage message=messages.get(messageid);
		
		if(message!=null){
			topContent.put(message.getTopName(), messageid);			
			MessageDataService.sendPubMsg( message);
		}else{
			
			dboptgroup.submit(new Runnable() {
				
				@Override
				public void run() {
				        SqlSession session=DBSessionFactory.getSqlSession();
				        
				        MsgRepExample example=new MsgRepExample();
				        
				        example.createCriteria().andMessageidEqualTo(messageid);
				        MsgRep msgRep= session.selectOne(queryMsg, example);
				        session.close();
				        if(msgRep!=null){
				        	topContent.put(msgRep.getTopname(), messageid);	
				            TransportMessage	message=new TransportMessage();
				        	message.setMessageId(messageid);
				        	message.setContent(msgRep.getContent());
				        	MessageDataService.sendPubMsg( message);
				        }
				}
			});
		}

	}
	
	
	/**
	 *   ����ͻ��� �����յ�
	 *   
	 *   �Կͻ��˷��ͷ����ͷ�
	 *   ���� �ͻ����յ���messageid �ҵ���Ӧ��message  ����  �洢����Ϣ��¼����
	 * @param ctx
	 * @param messagepub
	 */
	private void pubrec(final ChannelHandlerContext ctx,
			MqttMessage messagepub) {
		
		MqttMessageIdVariableHeader variableHeader=(MqttMessageIdVariableHeader)messagepub.variableHeader();

		Integer messageid=variableHeader.messageId();
		
		
		MqttQoS mqttQoS=messagepub.fixedHeader().qosLevel();
		
		MqttFixedHeader fixedHeader=null;
		
		if(mqttQoS.value()<=1)
			//���ͷ����յ�  QOS�����1
			fixedHeader	= new MqttFixedHeader(
					MqttMessageType.PUBREL, false, MqttQoS.AT_MOST_ONCE, false, 0);
		else
			//�����ͷ����յ�  QOS�����2
			fixedHeader	= new MqttFixedHeader(
					MqttMessageType.PUBREL, false, MqttQoS.EXACTLY_ONCE, false, 0);
		
		

		MqttMessageIdVariableHeader connectVariableHeader = MqttMessageIdVariableHeader
				.from(messageid);

		MqttPubAckMessage ackMessage = new MqttPubAckMessage(fixedHeader,
				connectVariableHeader);
		ctx.writeAndFlush(ackMessage);
		
		
		
		String iden=channel2str.get(ctx.channel());
		
		saveSendMsg(messageid, iden);

	}
	
	private  void   saveSendMsg(Integer messageid,String iden){
		 ConcurrentLinkedQueue<Integer> sendsMsgIds=messageSends.get(
				   iden);
		 
		 if(sendsMsgIds==null){
			 sendsMsgIds=new ConcurrentLinkedQueue<Integer>();
			 messageSends.put(iden, sendsMsgIds);
		 }
		 
		 TransportMessage message=messages.get(messageid);
		
       sendsMsgIds.add(message.getMessageId());
	}
	
	
	@Override
	public void channelInactive(final ChannelHandlerContext ctx)
			throws Exception {
		super.channelInactive(ctx);
		ctx.close();
	}
	
}
