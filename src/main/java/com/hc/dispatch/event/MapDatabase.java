package com.hc.dispatch.event;

import com.google.gson.Gson;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.HTreeMap;
import org.mapdb.Serializer;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.File;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class MapDatabase {
    @Resource
    private Gson gson = new Gson();
    private DB dataBase;
    private HTreeMap<String, String> map;
    private static final String FILE_PATH = System.getProperty("user.dir") +
            File.separator + "db" + File.separator + "msgDB";

    /**
     * 持久化的是上传的消息，消息重发并不频繁，因此默认不连接数据库文件，读写时连接
     */
    @SuppressWarnings("unchecked")
    private void connect(String selectDB) {
        if (dataBase == null || dataBase.isClosed()) {
            dataBase = DBMaker.fileDB(new File(FILE_PATH)).closeOnJvmShutdown().make();
            map = dataBase.hashMap(selectDB).
                    keySerializer(Serializer.STRING).
                    valueSerializer(Serializer.JAVA).
                    //持久化的消息最多保存48小时，48小时后无论发送成功或失败都会彻底删除
                    expireAfterCreate(24 * 60 * 60 * 1000, TimeUnit.MILLISECONDS).
                    createOrOpen();
        }
    }

    /**
     * 支持并发写
     */
    @SneakyThrows
    public MapDatabase write(String key, Object value,String selectDB) {
        connect(selectDB);
        map.put(key, gson.toJson(value));
        return this;
    }

    /**
     * 注意DB一旦关闭，map也随之关闭，FileDB里的HTreeMap是文件内容的映射，并不会一直存在
     * 小心db过大吃满数据上传线程
     */
    public synchronized <T> List<T> read(Class<T> type,String selectDB) {
        connect(selectDB);
        LinkedList<String> removeList = new LinkedList<>();
        LinkedList<T> msgList = new LinkedList<>();
        //HTreeMap虽然实现了ConcurrentMap，但不确定迭代时删除是否线程安全
        map.forEach((key, value) -> {
            removeList.add(key);
            T obj = gson.fromJson(value, type);
            msgList.add(obj);
        });
        removeList.forEach(map::remove);
        return msgList;
    }

    /**
     * 注意写操作完成一定要closeDB，否则会导致脏数据
     */
    public void close() {
        //TODO 频繁连接
        if (dataBase != null) {
            dataBase.close();
            dataBase = null;
            map = null;
        }
    }

}
