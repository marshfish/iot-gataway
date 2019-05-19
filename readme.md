# 消息代理服务器
>注：需与iot-connector搭配食用.
主要职责：REST网关、配置中心、消息代理/上传（转发）、健康检查、消息持久化、设备管理、

## REST API：
1。设备管理与配置中心API：不与connector组件交互，但会与缓存或数据库等交互CURD等，需要分页的需要继承PageDTO。

2.消息代理API：可在HTTP请求头中指定responseAck（true/false）字段表明是否需要挂起请求，等待设备端的响应，
出于吞吐量考虑，默认为关闭状态，即发送指令后立即返回不会阻塞，但无法确认设备是否能收到请求。若打开responseAck，
则HTTP请求的最大挂起时间不超过yml中配置的maxHTTPIdleTime（默认5s）。接口参数实体必须继承EquipmentDTO，
其中的serialNumber属性为本次请求下行流水号。在设备响应后会通过EventBus回调给Http响应，若不继承则responseAck会失效。
