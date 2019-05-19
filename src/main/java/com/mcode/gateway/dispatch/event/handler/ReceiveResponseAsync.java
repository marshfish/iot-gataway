package com.mcode.gateway.dispatch.event.handler;

import com.google.gson.Gson;
import com.mcode.gateway.Bootstrap;
import com.mcode.gateway.dispatch.event.AsyncEventHandler;
import com.mcode.gateway.dispatch.event.MapDatabase;
import com.mcode.gateway.rpc.MqConnector;
import com.mcode.gateway.rpc.PublishEvent;
import com.mcode.gateway.rpc.SessionEntry;
import com.mcode.gateway.rpc.serialization.Trans;
import com.mcode.gateway.type.EventTypeEnum;
import com.mcode.gateway.util.AsyncHttpClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import javax.annotation.Resource;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class ReceiveResponseAsync extends AsyncEventHandler implements Bootstrap {
    @Resource
    private MapDatabase mapDatabase;
    @Resource
    private MqConnector mqConnector;
    @Resource
    private JedisPool jedisPool;
    @Resource
    private Gson gson;
    private Map<String, PublishEvent> backupData = new HashMap<>();
    private Queue<PublishEvent> failQueue = new LinkedBlockingQueue<>(200);
    public static final String QOS1_BACKUP = "qos1_backup";
    private static ScheduledExecutorService reSendThread = Executors.newScheduledThreadPool(1, r -> {
        Thread thread = new Thread(r);
        thread.setName("rePost-exec-1");
        thread.setDaemon(true);
        return thread;
    });

    @Override
    public void accept(Trans.event_data event) {
        log.info("connector响应：{}", event);
        String serialNumber = event.getSerialNumber();
        validEmpty("connector端响应", serialNumber);
        Optional.ofNullable(backupData.get(serialNumber)).ifPresent(e -> {
            if (e.isEndurance()) {
                mapDatabase.remove(serialNumber, QOS1_BACKUP);
            }
            backupData.remove(serialNumber);
        });
    }

    /**
     * qos1下的指令传输，若得不到connector端的ack，则尝试重发，
     * 若依然失败，则持久化到MapDB准备重发
     */
    public void qos1Publish(String id, PublishEvent publishEvent) {
        backupData.put(id, publishEvent);
        publishEvent.addTimer(timeoutEvent -> {
            log.warn("缓存失败消息");
            String serialNumber = timeoutEvent.getSerialNumber();
            if (backupData.containsKey(serialNumber)) {
                backupData.remove(serialNumber);
                failQueue.offer(timeoutEvent.addRePostCount());
            }
        });
        mqConnector.publishAsync(publishEvent);
    }

    @Override
    public Integer setEventType() {
        return EventTypeEnum.CLIENT_RESPONSE.getType();
    }

    @SuppressWarnings("UnusedAssignment")
    @Override
    public void init() {
        reSendThread.scheduleAtFixedRate(new Runnable() {
            private int count;

            @Override
            public void run() {
                try {
                    count++;
                    PublishEvent publishEvent;
                    while ((publishEvent = failQueue.poll()) != null) {
                        long now = System.currentTimeMillis();
                        publishEvent.addRePostCount();
                        String serialNumber = publishEvent.getSerialNumber();
                        //判断是否超时
                        if (now > publishEvent.getTimeStamp() + publishEvent.getTimeout()) {
                            //当该消息投递次数小于2次时，暂不持久化到DB，继续尝试重发
                            if (publishEvent.getRePostCount() < 2) {
                                log.info("尝试立即重发失败消息");
                                String device;
                                //重发时需注意设备可能经负载均衡连到其他节点，需重新获取其所处节点位置
                                try (Jedis jedis = jedisPool.getResource()) {
                                    device = jedis.hget(EquipmentLogin.SESSION_MAP, publishEvent.getUniqueId());
                                }
                                final PublishEvent publisher = publishEvent;
                                Optional.ofNullable(device).
                                        map(d -> gson.fromJson(d, SessionEntry.class)).
                                        map(SessionEntry::getNode).
                                        ifPresent(node -> {
                                            publisher.addHeaders(MqConnector.CONNECTOR_ID, node);
                                            qos1Publish(serialNumber, publisher);
                                        });
                            } else {
                                log.info("尝试次数大于两次，持久化失败消息到DB，等待重新发送");
                                //写入到嵌入式数据库中
                                publishEvent.setEnduranceFlag(true);
                                mapDatabase.write(serialNumber,
                                        publishEvent,
                                        QOS1_BACKUP).
                                        close();
                            }
                        }
                    }
                    //每2小时检查一下数据库，尝试重发
                    if (count == 2) {
                        log.info("检查消息重发DB");
                        long now = System.currentTimeMillis();
                        //重发qos1消息
                        mapDatabase.read(PublishEvent.class, QOS1_BACKUP).
                                forEach(event -> {
                                    if (now > event.getTimeStamp() + event.getTimeout()) {
                                        log.info("重发消息：{}", event);
                                        qos1Publish(event.getSerialNumber(), event);
                                    } else {
                                        //过期消息
                                        mapDatabase.remove(event.getSerialNumber(), QOS1_BACKUP);
                                    }
                                });
                        //重发数据上传
                        mapDatabase.read(DataUpload.FailHandler.class, DataUpload.RE_POST_MESSAGE).
                                forEach(handler -> {
                                    String url = handler.getUrl();
                                    String param = handler.getParam();
                                    AsyncHttpClient.sendPost(url, param, new DataUpload.FailHandler(url, param, handler.getId(), mapDatabase));
                                });
                        mapDatabase.close();
                        count = 0;
                    }
                } catch (Exception e) {
                    log.error("消息重发线程异常：{}", Arrays.toString(e.getStackTrace()));
                }
            }
        }, 30 * 1000, 1 * 60 * 1000, TimeUnit.MILLISECONDS);
    }
}
