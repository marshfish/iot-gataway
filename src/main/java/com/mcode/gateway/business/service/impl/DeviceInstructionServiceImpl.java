package com.mcode.gateway.business.service.impl;

import com.google.gson.Gson;
import com.mcode.gateway.business.dal.EquipmentDAL;
import com.mcode.gateway.business.dal.dao.EquipmentRegistry;
import com.mcode.gateway.business.dto.DeliveryInstructionDTO;
import com.mcode.gateway.business.service.DeviceInstructionService;
import com.mcode.gateway.configuration.CommonConfig;
import com.mcode.gateway.configuration.ConfigCenter;
import com.mcode.gateway.dispatch.event.handler.EquipmentLogin;
import com.mcode.gateway.dispatch.event.handler.MonitorData;
import com.mcode.gateway.dispatch.event.handler.ReceiveResponseAsync;
import com.mcode.gateway.rpc.MqConnector;
import com.mcode.gateway.rpc.NodeEntry;
import com.mcode.gateway.rpc.PublishEvent;
import com.mcode.gateway.rpc.SessionEntry;
import com.mcode.gateway.rpc.serialization.Trans;
import com.mcode.gateway.type.EventTypeEnum;
import com.mcode.gateway.type.QosType;
import com.mcode.gateway.util.CommonUtil;
import com.mcode.gateway.util.IdGenerator;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import javax.annotation.Resource;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

/**
 * 查询db设备是否注册，获取设备类型
 * 查询redis，找到设备登陆的节点
 * 获取集群节点列表，向该节点发布消息
 */
@Service
@Slf4j
public class DeviceInstructionServiceImpl extends CommonUtil implements DeviceInstructionService {
    @Resource
    private EquipmentDAL equipmentDAL;
    @Resource
    private JedisPool jedisPool;
    @Resource
    private Gson gson;
    @Resource
    private MqConnector mqConnector;
    @Resource
    private CommonConfig commonConfig;
    @Resource
    private ReceiveResponseAsync responseAsync;
    @Resource
    private ConfigCenter configCenter;
    @Resource
    private MonitorData monitorData;
    private int nodes = 0;

    @Override
    public String publishInstruction(DeliveryInstructionDTO deliveryInstructionDTO) {
        String device;
        String md5UniqueId = deliveryInstructionDTO.getUniqueId();
        try (Jedis jedis = jedisPool.getResource()) {
            device = jedis.hget(EquipmentLogin.SESSION_MAP, md5UniqueId);
        }
        if (StringUtils.isEmpty(device)) {
            List<EquipmentRegistry> equipment = equipmentDAL.getByUniqueId(md5UniqueId);
            if (CollectionUtils.isEmpty(equipment)) {
                throw new RuntimeException("该设备不存在或未注册");
            } else {
                throw new RuntimeException("该设备不在线");
            }
        } else {
            //设备会话
            SessionEntry sessionEntry = gson.fromJson(device, SessionEntry.class);
            String eqId = sessionEntry.getEqId();
            Integer eqType = sessionEntry.getEqType();
            String nodeArtifactId = sessionEntry.getNode();
            //配置信息
            String serialNumber = deliveryInstructionDTO.getSerialNumber();
            String instruction = deliveryInstructionDTO.getInstruction();
            Boolean rpcModel = deliveryInstructionDTO.getRpcModel();
            Integer rpcTimeout = deliveryInstructionDTO.getRpcTimeout();
            Integer qos = deliveryInstructionDTO.getQos();
            Integer qosTimeout = deliveryInstructionDTO.getQosTimeout();
            //序列化
            Trans.event_data.Builder entry = Trans.event_data.newBuilder();
            byte[] bytes = entry.setEqId(eqId).
                    setType(EventTypeEnum.SERVER_PUBLISH.getType()).
                    setMsg(instruction).
                    setSerialNumber(serialNumber).
                    setDispatcherId(commonConfig.getDispatcherId()).
                    setTimeStamp(System.currentTimeMillis()).
                    setQos(qos).
                    setReTryTimeout(qosTimeout).
                    build().toByteArray();
            String queue = mqConnector.getQueue(eqType);
            PublishEvent publishEvent = new PublishEvent(queue, bytes, serialNumber);
            publishEvent.setQos(qos);
            publishEvent.setTimeout(qosTimeout);
            publishEvent.addHeaders(MqConnector.CONNECTOR_ID, nodeArtifactId);
            //是否使用rpc模式，rpc模式下也支持qos1
            if (rpcModel) {
                log.info("rpc模式向【{}】发送消息【{}】,qos【{}】", eqId, instruction, qos);
                Trans.event_data eventEntry = mqConnector.publishSync(publishEvent, rpcTimeout);
                if (qos == QosType.AT_LEAST_ONCE.getType() && eventEntry == null) {
                    publishEvent.setUniqueId(md5UniqueId);
                    responseAsync.qos1Publish(serialNumber, publishEvent);
                } else {

                }
                return Optional.ofNullable(eventEntry).map(Trans.event_data::getMsg).orElse(StringUtils.EMPTY);
            } else {
                //qos1处理
                if (qos == QosType.AT_LEAST_ONCE.getType()) {
                    log.info("向【{}】发送qos1消息【{}】", eqId, instruction);
                    publishEvent.setUniqueId(md5UniqueId);
                    responseAsync.qos1Publish(serialNumber, publishEvent);
                } else {
                    log.info("向【{}】发送qos0消息【{}】", eqId, instruction);
                    mqConnector.publishAsync(publishEvent);
                }

                return StringUtils.EMPTY;
            }
        }
    }

    @Override
    public List<Response> monitor(Integer eqType) {
        //设备队列名- 设备节点ID
        Map<String, List<String>> map;
        Map<Integer, String> registry = configCenter.getEquipmentTypeRegistry();
        if (eqType == null) {
            //查询所有设备的所有节点
            map = registry.entrySet().stream().collect(Collectors.toMap(Map.Entry::getValue, s -> selectNode(s.getValue())));
        } else {
            //查询单个设备的所有节点
            map = new HashMap<>(1);
            map.put(mqConnector.getQueue(eqType), Optional.ofNullable(registry.get(eqType)).
                    map(this::selectNode).
                    orElse(Collections.emptyList()));
        }
        nodes = map.size();
        Trans.event_data.Builder entry = Trans.event_data.newBuilder();
        String id = String.valueOf(IdGenerator.buildDistributedId());
        byte[] bytes = entry.
                setType(EventTypeEnum.MONITOR.getType()).
                setSerialNumber(id).
                setDispatcherId(commonConfig.getDispatcherId()).
                setTimeStamp(System.currentTimeMillis()).
                build().toByteArray();
        //异步推送统计消息
        map.forEach((key, value) -> value.forEach(node -> {
            PublishEvent event = new PublishEvent(key, bytes, id);
            event.addHeaders(MqConnector.CONNECTOR_ID, node);
            mqConnector.publishAsync(event);
        }));
        //阻塞当前线程，直到所有节点返回统计信息或等待超时
        int timeout = 15000;
        MonitorData.MonitorWarpper monitorWarpper = monitorData.warpperMonitor(nodes, timeout);
        FutureTask<List<Trans.event_data>> future = new FutureTask<>(monitorWarpper);
        future.run();
        try {
            List<Response> list = new ArrayList<>();
            List<Trans.event_data> data;
            try {
                data = future.get(timeout, TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                //应该不会发生
                InterruptedException exception = new InterruptedException();
                exception.setStackTrace(e.getStackTrace());
                throw exception;
            }
            data.forEach(e -> {
                Response response = new Response();
                if (e != null) {
                    if (e.getMsg() == null) {
                        response.setNode(e.getNodeArtifactId());
                        response.setInfo(StringUtils.EMPTY);
                    } else {
                        response.setNode(e.getNodeArtifactId());
                        response.setInfo(e.getMsg());
                    }
                }
                list.add(response);
            });
            return list;
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
            return Collections.emptyList();
        }
    }

    @Override
    public String dump(String nodeName, Integer eqType) {
        Trans.event_data.Builder entry = Trans.event_data.newBuilder();
        String id = String.valueOf(IdGenerator.buildDistributedId());
        byte[] bytes = entry.
                setType(EventTypeEnum.DUMP.getType()).
                setSerialNumber(id).
                setDispatcherId(commonConfig.getDispatcherId()).
                setTimeStamp(System.currentTimeMillis()).
                build().toByteArray();
        PublishEvent event = new PublishEvent(mqConnector.getQueue(eqType), bytes, id);
        event.addHeaders(MqConnector.CONNECTOR_ID, nodeName);
        Trans.event_data data = mqConnector.publishSync(event, 5);
        return data.getMsg();
    }

    @Override
    public ThreadInfo[] dump() {
        ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
        return threadMXBean.dumpAllThreads(true, true);
    }

    @Data
    public static class Response {
        private String node;
        private String info;
    }

    private List<String> selectNode(String type) {
        try (Jedis jedis = jedisPool.getResource()) {
            return jedis.keys(type + "_*").
                    stream().
                    map(jedis::get).
                    map(v -> gson.fromJson(v, NodeEntry.class)).
                    map(NodeEntry::getNodeId).
                    collect(Collectors.toList());
        }
    }

    public int countNodeAndClear() {
        int temp = nodes;
        nodes = 0;
        return temp;
    }


}
