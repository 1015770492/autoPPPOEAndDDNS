package top.yumbo.ddns;

import com.alibaba.fastjson.JSONObject;
import com.aliyuncs.DefaultAcsClient;
import com.aliyuncs.IAcsClient;
import com.aliyuncs.alidns.model.v20150109.*;
import com.aliyuncs.exceptions.ClientException;
import com.aliyuncs.profile.DefaultProfile;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;
import top.yumbo.autopppoe.AutoPPPOE;
import top.yumbo.config.InternetConfig;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.*;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 动态域名解析
 */
public class DDNS {

    private static String accessKeyId = InternetConfig.accessKeyId;
    private static String accessKeySecret = InternetConfig.accessKeySecret;
    private static String RR = InternetConfig.RR;
    private static ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();


    public static void main(String[] args) {

//        scheduleUpdate();
        DDNS.updateDNS(InternetConfig.domainName, InternetConfig.recordType);// 更新dns
    }

    /**
     * 定时更新
     */
    public static void scheduleUpdate() {
        scheduledExecutorService.scheduleAtFixedRate(() -> {
            DDNS.updateDNS(InternetConfig.domainName, InternetConfig.recordType);// 更新dns
        }, 30, 60*10, TimeUnit.SECONDS);
    }

    /**
     * 更新dns记录
     */
    public static void updateDNS(String domainName, String recordType) {
        updateDNSWithIpValue(domainName,recordType,getCurrentHostIPV6());
    }

    public static void updateDNSWithIpValue(String domainName, String recordType,String ipValue) {
        // 设置鉴权参数，初始化客户端
        DefaultProfile profile = DefaultProfile.getProfile(
                "cn-qingdao",// 地域ID
                accessKeyId,// 您的AccessKey ID
                accessKeySecret);// 您的AccessKey Secret
        IAcsClient client = new DefaultAcsClient(profile);

        // 查询指定二级域名的最新解析记录
        DescribeDomainRecordsRequest describeDomainRecordsRequest = new DescribeDomainRecordsRequest();
        // 主域名
        describeDomainRecordsRequest.setDomainName(domainName);
        // 主机记录
        describeDomainRecordsRequest.setRRKeyWord("");
        // 解析记录类型
        describeDomainRecordsRequest.setType(recordType);

        DescribeDomainRecordsResponse describeDomainRecordsResponse = describeDomainRecords(describeDomainRecordsRequest, client);
        log_print("describeDomainRecords", describeDomainRecordsResponse);

        List<DescribeDomainRecordsResponse.Record> domainRecords = describeDomainRecordsResponse.getDomainRecords();
        // 当前主机公网IP
        // 最新的一条解析记录
        if (domainRecords.size() != 0) {
            System.out.println("已有记录，进行修改操作...");
            DescribeDomainRecordsResponse.Record record = domainRecords.get(0);
            // 记录ID
            String recordId = record.getRecordId();
            // 记录值
            String recordsValue = record.getValue();
            System.out.println("-------------------------------当前主机公网IP为：" + ipValue + "-------------------------------");
            if (!ipValue.equals(recordsValue)) {
                System.out.println("记录不相同需要进行dns修改");
                // 修改解析记录
                UpdateDomainRecordRequest updateDomainRecordRequest = new UpdateDomainRecordRequest();
                // 主机记录
                updateDomainRecordRequest.setRR(RR);
                // 记录ID
                updateDomainRecordRequest.setRecordId(recordId);
                // 将主机记录值改为当前主机IP
                updateDomainRecordRequest.setValue(ipValue);
                // 解析记录类型
                updateDomainRecordRequest.setType(recordType);
                UpdateDomainRecordResponse updateDomainRecordResponse = updateDomainRecord(updateDomainRecordRequest, client);
                log_print("updateDomainRecord", updateDomainRecordResponse);
            }
            System.out.println("dns记录相同不需要修改");
        } else {
            System.out.println("记录不存在进行添加");
            AddDomainRecordRequest request = new AddDomainRecordRequest();
            request.setDomainName(domainName);// 域名
            request.setTTL(600L);// ttl 改不了除非花钱升级到企业旗舰版
            request.setType(recordType);// 记录类型
            request.setPriority(1L);// 权重
            request.setValue(ipValue);// 真实ip
            request.setRR(RR);// 主机记录，例如:  @  www
            AddDomainRecordResponse addDomainRecordResponse = addDomainRecord(request, client);
            log_print("addDomainRecord", addDomainRecordResponse);
        }
    }

    /**
     * 获取主域名的所有解析记录列表
     */
    private static DescribeDomainRecordsResponse describeDomainRecords(DescribeDomainRecordsRequest request, IAcsClient client) {
        try {
            // 调用SDK发送请求
            return client.getAcsResponse(request);
        } catch (ClientException e) {
            e.printStackTrace();
            // 发生调用错误，抛出运行时异常
        }
        return null;
    }

    /**
     * 获取当前主机公网IPv6
     */
    private static String getCurrentHostIPV6() {
        // 这里使用jsonip.com第三方接口获取本地IPv6，ipv4则改前缀为ipv4即可
        String jsonip = "https://ipv6.jsonip.com/";
        // 接口返回结果
        HttpComponentsClientHttpRequestFactory httpRequestFactory = new HttpComponentsClientHttpRequestFactory();
        httpRequestFactory.setConnectionRequestTimeout(10000);
        httpRequestFactory.setConnectTimeout(10000);
        httpRequestFactory.setReadTimeout(10000);
        RestTemplate restTemplate = new RestTemplate(httpRequestFactory);
        ResponseEntity<String> forEntity = restTemplate.getForEntity(jsonip, String.class);
        if (forEntity.getStatusCode() != HttpStatus.OK) {
            System.out.println("服务异常，可能ipv6异常");
            // 没有获取到ip地址则进行断开网络进行重连
            AutoPPPOE.reconnectIneternet();
            forEntity = restTemplate.getForEntity(jsonip, String.class);
        }
        System.out.println(forEntity.getStatusCode());
        System.out.println(forEntity.getBody());
        JSONObject jsonObject = JSONObject.parseObject(forEntity.getBody());
        return jsonObject.getString("ip");// 返回公网ipv6
    }

    /**
     * 修改解析记录
     */
    private static UpdateDomainRecordResponse updateDomainRecord(UpdateDomainRecordRequest request, IAcsClient client) {
        try {
            // 调用SDK发送请求
            return client.getAcsResponse(request);
        } catch (ClientException e) {
            e.printStackTrace();
            // 发生调用错误，抛出运行时异常
        }
        return null;
    }

    /**
     * 添加域名解析
     */
    private static AddDomainRecordResponse addDomainRecord(AddDomainRecordRequest request, IAcsClient client) {
        try {
            return client.getAcsResponse(request);
        } catch (ClientException e) {
            e.printStackTrace();
        }
        return null;
    }

    private static void log_print(String functionName, Object result) {
        System.out.println("-------------------------------" + functionName + "-------------------------------");
        System.out.println(JSONObject.toJSONString(result));
    }




}