package com.zx.bt.socket.processor;

import com.zx.bt.dto.MessageInfo;
import com.zx.bt.dto.method.AnnouncePeer;
import com.zx.bt.entity.InfoHash;
import com.zx.bt.entity.Node;
import com.zx.bt.enums.InfoHashTypeEnum;
import com.zx.bt.enums.MethodEnum;
import com.zx.bt.enums.NodeRankEnum;
import com.zx.bt.enums.YEnum;
import com.zx.bt.repository.InfoHashRepository;
import com.zx.bt.repository.NodeRepository;
import com.zx.bt.store.RoutingTable;
import com.zx.bt.util.BTUtil;
import com.zx.bt.util.SendUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.net.InetSocketAddress;
import java.util.Map;

/**
 * author:ZhengXing
 * datetime:2018/3/1 0001 10:30
 * ANNOUNCE_PEER 请求 处理器
 */
@Slf4j
@Component
public class AnnouncePeerRequestUDPProcessor extends UDPProcessor{
	private static final String LOG = "[ANNOUNCE_PEER]";

	private final RoutingTable routingTable;
	private final InfoHashRepository infoHashRepository;
	private final NodeRepository nodeRepository;

	public AnnouncePeerRequestUDPProcessor(RoutingTable routingTable, InfoHashRepository infoHashRepository, NodeRepository nodeRepository) {
		this.routingTable = routingTable;
		this.infoHashRepository = infoHashRepository;
		this.nodeRepository = nodeRepository;
	}

	@Override
	boolean process1(ProcessObject processObject) {
		InetSocketAddress sender = processObject.getSender();
		Map<String, Object> rawMap = processObject.getRawMap();
		MessageInfo messageInfo = processObject.getMessageInfo();

		AnnouncePeer.RequestContent requestContent = new AnnouncePeer.RequestContent(rawMap, sender.getPort());

		log.info("{}ANNOUNCE_PEER.发送者:{},port:{},info_hash:{},map:{}",
				LOG, sender, requestContent.getPort(), requestContent.getInfo_hash(),rawMap);

		InfoHash infoHash = infoHashRepository.findFirstByInfoHashAndType(requestContent.getInfo_hash(), InfoHashTypeEnum.ANNOUNCE_PEER.getCode());
		if (infoHash == null) {
			//如果为空,则新建
			infoHash = new InfoHash(requestContent.getInfo_hash(), InfoHashTypeEnum.ANNOUNCE_PEER.getCode(),
					BTUtil.getIpBySender(sender) + ":" + requestContent.getPort() + ";");
		} else if(StringUtils.isEmpty(infoHash.getPeerAddress()) || infoHash.getPeerAddress().split(";").length <= 16){
			//如果不为空,并且长度小于一定值,则追加
			infoHash.setPeerAddress(infoHash.getPeerAddress() + ";" + BTUtil.getIpBySender(sender) + ":" + requestContent.getPort() + ";");
		}
		//入库
		infoHashRepository.save(infoHash);

		//回复
		SendUtil.announcePeerReceive(messageInfo.getMessageId(), sender, processObject.getConfig().getMain().getNodeId());
		Node node = new Node(requestContent.getId(), BTUtil.getIpBySender(sender), sender.getPort(), NodeRankEnum.ANNOUNCE_PEER.getCode());
		//加入路由表
		routingTable.put(node);
		//入库
		nodeRepository.save(node);
		return true;
	}

	@Override
	boolean isProcess(ProcessObject processObject) {
		return MethodEnum.ANNOUNCE_PEER.equals(processObject.getMessageInfo().getMethod()) && YEnum.QUERY.equals(processObject.getMessageInfo().getStatus());
	}
}