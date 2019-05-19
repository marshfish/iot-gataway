package com.mcode.gateway.util;

import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpResponse;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClients;
import org.apache.http.impl.nio.conn.PoolingNHttpClientConnectionManager;
import org.apache.http.impl.nio.reactor.DefaultConnectingIOReactor;
import org.apache.http.impl.nio.reactor.IOReactorConfig;
import org.apache.http.nio.reactor.ConnectingIOReactor;
import org.apache.http.nio.reactor.IOReactorException;

import java.io.UnsupportedEncodingException;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

@Slf4j
public class AsyncHttpClient {
    private static CloseableHttpAsyncClient client;

    static {
        client = constructorSyncHttpClient();
        client.start();
    }

    private static CloseableHttpAsyncClient constructorSyncHttpClient() {
        //配置Reactor io线程
        IOReactorConfig ioReactorConfig = IOReactorConfig.custom().
                setIoThreadCount(Runtime.getRuntime().availableProcessors())
                .setSoKeepAlive(true)
                .build();
        //设置连接池大小
        ConnectingIOReactor ioReactor;
        try {
            ioReactor = new DefaultConnectingIOReactor(ioReactorConfig);
        } catch (IOReactorException e) {
            e.printStackTrace();
            throw new RuntimeException("创建httpClient reactor I/O 配置失败");
        }
        //连接池配置
        PoolingNHttpClientConnectionManager connManager = new PoolingNHttpClientConnectionManager(ioReactor);
        connManager.setMaxTotal(100);
        connManager.setDefaultMaxPerRoute(100);
        connManager.closeExpiredConnections();
        //请求配置
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(30000)
                .setSocketTimeout(30000)
                .setConnectionRequestTimeout(1000)
                .build();
        //构造请求client
        return HttpAsyncClients.custom().
                setConnectionManager(connManager)
                .setDefaultRequestConfig(requestConfig)
                .build();
    }

    public static void sendPost(String url, Map<String, String> body) {
        String param = new Gson().toJson(body);
        sendPost(url, param, new DefaultHttpResponseFuture(url, param));
    }

    public static void sendPost(String url, String body) {
        sendPost(url, body, new DefaultHttpResponseFuture(url, body));
    }

    public static void sendPost(String url, String body, FutureCallback<HttpResponse> future) {
        HttpPost httpPost = new HttpPost(url);
        String param = new Gson().toJson(body);
        StringEntity entity;
        try {
            entity = new StringEntity(param);
            entity.setContentEncoding("utf-8");
            entity.setContentType("application/json");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            throw new RuntimeException("httpclient编码错误");
        }
        httpPost.setEntity(entity);
        client.execute(httpPost, future);
    }


    @Slf4j
    private static class DefaultHttpResponseFuture implements FutureCallback<HttpResponse> {
        private String url;
        private String param;

        public DefaultHttpResponseFuture(String url, String param) {
            this.url = url;
            this.param = param;
        }

        @Override
        public void completed(HttpResponse httpResponse) {
            log.info("调用 {} 接口成功,参数：{}", url, param);
        }

        @Override
        public void failed(Exception e) {
            log.warn("访问接口失败，uri：{},param:{},Exception:{}", url, param, e);
        }

        @Override
        public void cancelled() {
            log.info("取消调用 {} 接口,参数：{}", url, param);
        }
    }

    public static void main(String[] args) {
        ListNode es = new ListNode(2);
        es.next = new ListNode(4);
        es.next.next = new ListNode(3);

        ListNode qq = new ListNode(5);
        qq.next = new ListNode(6);
        qq.next.next = new ListNode(4);

        ListNode listNode = addTwoNumbers(es, qq);
        System.out.println(listNode.val);
        ListNode temp = listNode;
        while (temp.next != null) {
            System.out.println(listNode.next.val);
            temp = temp.next;
        }
    }

    public static class ListNode {
        int val;
        ListNode next;

        ListNode(int x) {
            val = x;
        }
    }

    public static ListNode addTwoNumbers(ListNode l1, ListNode l2) {
        ListNode temp1 = l1;
        ListNode temp2 = l2;
        boolean addBit = false;
        ListNode node = null;
        ListNode temp =null;
        do {
            int add;
            int tempBit;
            if (addBit) {
                addBit = true;
                add = 1 + temp1.val + temp2.val;
            } else {
                add = temp1.val + temp2.val;
            }
            if (add / 10 > 0) {
                addBit = true;
                tempBit = add % 10;
            } else {
                tempBit = add;
            }
            if (node == null) {
                node = new ListNode(tempBit);
                temp = node;
            } else {
                temp.next = new ListNode(tempBit);
                temp = temp.next;
            }
            temp1 = temp1.next;
            temp2 = temp2.next;
        } while (temp1 != null && temp2 != null);
        return node;
    }
}
