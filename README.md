### 移动宽带实现 开机自动拨号获取ipv6，并且进行DDNS
***
在使用本项目中的代码前，先看一下文章：

[家用移动光猫（型号：HS8545M5）利用公网ipv6对外提供公网服务。(100M的宽带真香！)](https://blog.csdn.net/qq_41813208/article/details/113798009)

***

### 原项目中的不便

在我将家中电脑作为服务器的过程中，网络始终是一个问题，网络可能断开，那么就需要进行拨号重连，重新修改dns，
因为重新拨号可能会导致ipv6地址发送变化，而原项目[https://github.com/NewFuture/DDNS](https://github.com/NewFuture/DDNS) 
解决了动态DNS解析，利用原项目提供的程序，实现自动拨号，断线重连，自动ddns（保险操作，加上提供的bat文件，默认的bat会有弹窗一闪，再基本任务中可以将其隐藏）


***
## 使用方式：


#### 一、修改配置类信息

路径：`top.yumbo.config.InternetConfig` 对照着注释填入对应信息
```java
public class InternetConfig {
    public static String pppoeName = "拨号的连接名"; // 宽带连接名称
    public static String pppoeAccount = "";  // 宽带账号
    public static String pppoePassword = ""; // 宽带密码
    public static String accessKeyId = "";      // 阿里云上获取accessKeyId
    public static String accessKeySecret = "";  // accessKeySecret
    public static String domainName = "huashengshu.top";// 自己的域名
    public static String recordType = "AAAA";// 记录类型，A，CNAME，AAAA等
    public static String RR="@";// 主机记录：例如取 @ 或 www 或 * 等

}
```
#### 二、先测试一下代码是否有问题

运行AutoPPPOE代码，然后断网测试一下是否能重连，以及测试一下DDNS代码是否有效

如果DDNS

#### 三、打包成jar

通过idea由此的maven生命周期 package进行打包即可

也可以使用 mvn clean package 进行打包

#### 四、创建开机启动项

目的是开机启动这个jar程序
也就是 javaw -jar jar包路径,或者java -jar jar包路径
javaw有隐藏的作作用，java如果要隐藏在创建基本任务过程中有一个隐藏的选项

参考文章： [《windows开机后台运行java程序》](https://blog.csdn.net/qq_41813208/article/details/107510678) 




