package top.yumbo.autopppoe;

import com.alibaba.fastjson.JSONObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import top.yumbo.config.InternetConfig;
import top.yumbo.ddns.DDNS;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.concurrent.TimeUnit;

public class AutoPPPOE {
    private static String pppoeName = InternetConfig.pppoeName;        // 宽带链接名称
    private static String pppoeAccount = InternetConfig.pppoeAccount;  // 宽带账号
    private static String pppoePassword = InternetConfig.pppoePassword;// 宽带密码
    private static boolean flag = false;
    private static final int COUNT = 5;
    private static int count = COUNT;//4次检查都失败则进行重新拨号，否则不需要
    private static String ipv6Address = "";

    /**
     * 执行CMD命令,并返回String字符串
     */
    public static String executeCmd(String strCmd) {
        Process p = null;
        StringBuilder sbCmd = new StringBuilder();
        try {
            p = Runtime.getRuntime().exec("cmd /c " + strCmd);
            BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream(), Charset.forName("GBK")));
            String line;
            while ((line = br.readLine()) != null) {
                sbCmd.append(line + "\n");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return sbCmd.toString();
    }

    /**
     * 检测网络
     * 能ping通则返回true，ping不通返回false
     */
    private static boolean checkInternet() {
        String comm = "ping -n 1 114.114.114.114";
        String s = executeCmd(comm);

        if (s.indexOf("TTL") >= 0) {
            System.out.println(s);
            count = COUNT;// 网络通了则重新计数
            return true;
        } else if (s.indexOf("请求超时") >= 0 || s.indexOf("无法访问目标网") >= 0 || s.indexOf("传输失败") >= 0) {
            System.err.println(s);
            count--;
            return false;
        } else {
            count--;
            return false;
        }
    }

    /**
     * 检查IPv6
     */
    private static boolean checkIPV6() {
        if (flag) {
            return true;// 已经获取到ipv6则不需要再发请求了直接返回
        }
        // 这里使用jsonip.com第三方接口获取本地IPv6，ipv4则改前缀为ipv4即可
        String jsonip = "https://ipv6.jsonip.com/";
        // 接口返回结果
        HttpComponentsClientHttpRequestFactory httpRequestFactory = new HttpComponentsClientHttpRequestFactory();
        httpRequestFactory.setConnectionRequestTimeout(6000);
        httpRequestFactory.setConnectTimeout(6000);
        httpRequestFactory.setReadTimeout(6000);
        RestTemplate restTemplate = new RestTemplate(httpRequestFactory);
        try {
            ResponseEntity<String> forEntity = restTemplate.getForEntity(jsonip, String.class);
            if (forEntity.getStatusCode() == HttpStatus.OK) {
                String ipv6 = JSONObject.parseObject(forEntity.getBody()).getString("ip");
                if (StringUtils.hasText(ipv6)) {
                    System.out.println(ipv6);
                    if (!ipv6.equals(ipv6Address)) {
                        ipv6Address = ipv6;// 新的ipv6地址
                        //进行一次DNS解析
                        DDNS.updateDNSWithIpValue(InternetConfig.domainName,InternetConfig.recordType,ipv6Address);
                        // 只要有一次成功了就说明网络是正常的
                        flag = true;// 获取到了ipv6，表示已经获取到了ipv6再次检查ipv6时直接返回true
                    }
                    return true;// 有公网ipv6地址
                }
            }
        } catch (Exception e) {
            return false;// 没有公网ipv6地址
        }
        return false;// 没有公网ipv6地址
    }

    /**
     * 自动联网并且实现DDNS
     */
    public static void main(String[] args) throws Exception {
        int i = 0;
        // 下面是断线重连的代码逻辑
        while (true) {
            if (!checkInternet()) {
                TimeUnit.SECONDS.sleep(3);// 再等3秒，重新检测如果还没有ping通则说明网络确实没通（小心谷歌的dns服务器把你的ping作为攻击）
                if (!checkInternet()) {
                    if (count > 0) {
                        continue; // 个别几次检测ping不通不算
                    }
                    reconnectIneternet();// 重连网络
                }
            } else {
                // 网络正常则进行ipv6检查
                if (!checkIPV6()) {
                    checkIPV6();// 再检查一次ipv6
                    if (!checkIPV6()) {
                        // 3次检查都没有获取到ipv6,则进行重连网络
                        reconnectIneternet();
                    }
                }
                TimeUnit.SECONDS.sleep(10);
            }
        }

    }

    /**
     * 断开连接重新拨号
     */
    public static void reconnectIneternet() {
        String disconnectResult = executeCmd("rasdial " + pppoeName + " /DISCONNECT");// 断开拨号名称为 “宽带连接” 的网络连接
        System.out.println(disconnectResult);
        if (disconnectResult.indexOf("没有连接") >= 0 || disconnectResult.indexOf("命令已完成") >= 0) {
            String connectResult = executeCmd("rasdial " + pppoeName + " " + pppoeAccount + " " + pppoePassword);// 使用网络连接为 "宽带连接" 的网络进行拨号连接（宽带账号 和 密码 别忘了改）
            System.out.println(connectResult);
            try {
                TimeUnit.SECONDS.sleep(10);// 暂停一会，不暂停的话重新进行判断网络的时候会导致ping不通，实际已经连接成功了
                flag = false;// 进行了重连需要重新检查有没有获取ipv6地址
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}
