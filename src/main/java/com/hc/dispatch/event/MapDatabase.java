package com.hc.dispatch.event;

import com.google.gson.Gson;
import com.hc.business.dto.ConfigDTO;
import com.hc.business.dto.PageDTO;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.HTreeMap;
import org.mapdb.Serializer;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Component
public class MapDatabase {
    @Resource
    private Gson gson = new Gson();
    private volatile DB db;
    private static final String FILE_PATH = System.getProperty("user.dir") +
            File.separator + "db" + File.separator + "msgDB";

    /**
     * 持久化的是上传的消息，消息重发并不频繁，因此默认不连接数据库文件，读写时连接
     */
    @SuppressWarnings({"unchecked", "ConstantConditions"})
    private HTreeMap<String, String> connect(String selectDB) {
        //并发创建同一DB可能导致，加双锁防止重复创建DB对象后先创建的对象未被使用，且未调用close方法而被GC，导致锁mapDB的数据库文件
        if (db == null) {
            synchronized (this) {
                if (db == null) {
                    db = DBMaker.fileDB(new File(FILE_PATH)).closeOnJvmShutdown().make();
                }
            }
        }
        return db.hashMap(selectDB).
                keySerializer(Serializer.STRING).
                valueSerializer(Serializer.JAVA).
                //持久化的消息最多保存48小时，48小时后无论发送成功或失败都会彻底删除
                        expireAfterCreate(24 * 60 * 60 * 1000, TimeUnit.MILLISECONDS).
                        createOrOpen();
    }

    /**
     * 支持并发写
     */
    @SneakyThrows
    public MapDatabase write(String key, Object value, String selectDB) {
        Optional.of(connect(selectDB)).ifPresent(map -> map.put(key, gson.toJson(value)));
        return this;
    }

    /**
     * 删除记录
     * @param key 键
     * @param selectDB dbName
     */
    public void remove(String key, String selectDB) {
        Optional.of(connect(selectDB)).ifPresent(map -> map.remove(key));
    }

    /**
     * 注意DB一旦关闭，map也随之关闭，FileDB里的HTreeMap是文件内容的映射，并不会一直存在
     * 小心db过大吃满数据上传线程
     */
    public synchronized <T> List<T> read(Class<T> type, String selectDB) {
        LinkedList<T> msgList = new LinkedList<>();
        Optional.of(connect(selectDB)).ifPresent(map -> map.forEach((key, value) -> {
            T obj = gson.fromJson(value, type);
            msgList.add(obj);
        }));
        return msgList;
    }

    /**
     * 注意写操作完成一定要closeDB，否则会导致脏数据
     */
    public void close() {
        if (db != null) {
            db.close();
        }
    }

}
