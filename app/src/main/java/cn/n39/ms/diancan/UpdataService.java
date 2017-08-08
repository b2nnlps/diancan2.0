package cn.n39.ms.diancan;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DownloadManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.view.WindowManager;
import android.webkit.MimeTypeMap;
import android.widget.Toast;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;

import java.io.File;
import java.net.URLDecoder;

/**
 * 检测安装更新文件的助手类
 *
 * @author G.Y.Y
 */

public class UpdataService extends Service {

    /**
     * 安卓系统下载类
     **/
    DownloadManager manager;

    /**
     * 接收下载完的广播
     **/
    DownloadCompleteReceiver receiver;

    String versionUrl = "http://ms.n39.cn/dc.php?ver=", version = "2.0", apkUrl, apkText;
    String DOWNLOADPATH = "/cn.n39.ms.diancan/";
    String[] Data;


    private Handler handler = new Handler() {

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case 200:
                    String response = (String) msg.obj;
                    System.out.println("成功获取，返回内容");
                    response = toURLDecoded(response);
                    System.out.println(response);
                    if (response.length() > 5) {
                        Data = response.split(",");//[0] 0最新 1更新 2强制 [1]更新链接 [2]更新内容
                        if (!Data[0].equals("0")) {
                            apkUrl = Data[1];
                            apkText = Data[2];
                            showNormalDialog(apkText, Data[0]);
                        }
                    }
                    break;
                case 400:
                    System.out.println("关闭更新");
                    stopSelf();//停止更新服务
                    break;
                default:
                    break;
            }
        }

    };

    /**
     * 初始化下载器
     **/
    private void initDownManager() {

        manager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);

        receiver = new DownloadCompleteReceiver();

        System.out.println(apkUrl);
        //设置下载地址
        DownloadManager.Request down = new DownloadManager.Request(
                Uri.parse(apkUrl));

        // 设置允许使用的网络类型，这里是移动网络和wifi都可以
        down.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_MOBILE
                | DownloadManager.Request.NETWORK_WIFI);

        // 下载时，通知栏显示途中
        down.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE);

        // 显示下载界面
        down.setVisibleInDownloadsUi(true);

        // 设置下载后文件存放的位置
        //down.setDestinationInExternalFilesDir(this,
        //         Environment.DIRECTORY_DOWNLOADS, "dcapp.apk");

        down.setDestinationInExternalPublicDir(DOWNLOADPATH, "dc.apk");

        down.setTitle("更新中...");
        // 将下载请求放入队列
        manager.enqueue(down);

        //注册下载广播
        registerReceiver(receiver, new IntentFilter(
                DownloadManager.ACTION_DOWNLOAD_COMPLETE));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        try {
            version = getVersionCode();
        } catch (Exception e) {

        }
        versionUrl += version;
        System.out.println(versionUrl);

        // 检测更新
        checkUpdate();

        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public IBinder onBind(Intent intent) {

        return null;
    }

    @Override
    public void onDestroy() {

        // 注销下载广播
        if (receiver != null)
            unregisterReceiver(receiver);

        super.onDestroy();
    }

    // 接受下载完成后的intent
    class DownloadCompleteReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {

            System.out.println("下载完成");

            //判断是否下载完成的广播
            if (intent.getAction().equals(
                    DownloadManager.ACTION_DOWNLOAD_COMPLETE)) {

                //获取下载的文件id
                long downId = intent.getLongExtra(
                        DownloadManager.EXTRA_DOWNLOAD_ID, -1);

                if (manager.getUriForDownloadedFile(downId) != null) {
                    //自动安装apk
                    installAPK(manager.getUriForDownloadedFile(downId), context);
                    //installAPK(context);
                } else {
                    Toast.makeText(context, "下载失败", Toast.LENGTH_SHORT).show();
                }

                //自动安装apk
                //  installAPK(manager.getUriForDownloadedFile(downId));

                //停止服务并关闭广播
                UpdataService.this.stopSelf();

            }
        }

        /**
         * 安装apk文件
         */
        private void installAPK(Uri apk, Context context) {

            // 通过Intent安装APK文件
            if (Build.VERSION.SDK_INT < 23) {
                Intent intents = new Intent();
                System.out.println("开始安装");
                intents.setAction("android.intent.action.VIEW");
                intents.addCategory("android.intent.category.DEFAULT");
                intents.setType("application/vnd.android.package-archive");
                intents.setData(apk);
                intents.setDataAndType(apk, "application/vnd.android.package-archive");
                intents.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                System.out.println("配置安装");
                startActivity(intents);
                System.out.println("启动安装");
                android.os.Process.killProcess(android.os.Process.myPid());
                // 如果不加上这句的话在apk安装完成之后点击单开会崩溃
            } else {
                File file = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + DOWNLOADPATH + "dc.apk");
                if (file.exists()) {
                    openFile(file, context);
                }
            }
        }
    }

    public void openFile(File file, Context context) {
        Intent intent = new Intent();
        intent.addFlags(268435456);
        intent.setAction("android.intent.action.VIEW");
        String type = getMIMEType(file);
        intent.setDataAndType(Uri.fromFile(file), type);
        try {
            context.startActivity(intent);
        } catch (Exception var5) {
            var5.printStackTrace();
            Toast.makeText(context, "没有找到打开此类文件的程序", Toast.LENGTH_SHORT).show();
        }
    }

    public String getMIMEType(File var0) {
        String var1 = "";
        String var2 = var0.getName();
        String var3 = var2.substring(var2.lastIndexOf(".") + 1, var2.length()).toLowerCase();
        var1 = MimeTypeMap.getSingleton().getMimeTypeFromExtension(var3);
        return var1;
    }

    private void showNormalDialog(String mess, String force) {
        /* @setIcon 设置对话框图标
         * @setTitle 设置对话框标题
         * @setMessage 设置对话框消息提示
         * setXXX方法返回Dialog对象，因此可以链式设置属性
         */

        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.getMyApplication());
        builder.setIcon(R.drawable.ic_kitchen);
        builder.setTitle("有新版本");
        builder.setMessage(mess);
        builder.setPositiveButton("自动更新",//系统下载
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        initDownManager();//自动更新
                    }
                });
        builder.setNeutralButton("手动更新",
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Intent intent = new Intent();//浏览器下载
                        intent.setAction("android.intent.action.VIEW");
                        Uri content_url = Uri.parse("http://ms.n39.cn/dcsd.php");
                        intent.setData(content_url);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(intent);
                    }
                });
        if (force.equals("1")) {//如果不是强制更新 1为正常 2为强制
            builder.setNegativeButton("下次",
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            //...To-do
                            System.out.println("关闭更新");
                            stopSelf();//停止更新服务
                        }
                    });
        }
        builder.setCancelable(false);//不可点击其他地方，按钮也不行
        Dialog dialog = builder.create();
        dialog.show();
        System.out.println("我的弹出对话框呢？");
    }

    //获取版本号
    private String getVersionCode() throws Exception {
        //获取packagemanager的实例
        PackageManager packageManager = getPackageManager();
        //getPackageName()是你当前类的包名，0代表是获取版本信息
        PackageInfo packInfo = packageManager.getPackageInfo(getPackageName(), 0);
        return String.valueOf(packInfo.versionCode);
    }

    public static String toURLDecoded(String paramString) {
        if (paramString == null || paramString.equals("")) {
            //  LogD("toURLDecoded error:"+paramString);
            return "";
        }

        try {
            String str = new String(paramString.getBytes(), "UTF-8");
            str = URLDecoder.decode(str, "UTF-8");
            return str;
        } catch (Exception localException) {
            //  LogE("toURLDecoded error:"+paramString, localException);
        }

        return "";
    }

    private void checkUpdate() {
        //  自动检测更新
        new Thread(new Runnable() {

            @Override
            public void run() {

                try {
                    String result;
                    HttpGet httpRequest = new HttpGet(versionUrl);// 建立http get联机
                    HttpResponse httpResponse = new DefaultHttpClient().execute(httpRequest);// 发出http请求
                    if (httpResponse.getStatusLine().getStatusCode() == 200) {
                        result = EntityUtils.toString(httpResponse.getEntity());// 获取相应的字符串
                        //在子线程中将Message对象发出去
                        Message message = new Message();
                        message.what = 200;
                        message.obj = result;
                        handler.sendMessage(message);
                    }
                } catch (Exception e) {
                    // TODO Auto-generated catch block
                    Message message = new Message();
                    message.what = 400;
                    message.obj = "error";
                    handler.sendMessage(message);
                    e.printStackTrace();
                }
            }
        }).start();//这个start()方法不要忘记了

    }
}