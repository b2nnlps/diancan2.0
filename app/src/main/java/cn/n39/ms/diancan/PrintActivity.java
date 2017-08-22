package cn.n39.ms.diancan;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.text.TextUtils;
import android.widget.Toast;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.UUID;

public class PrintActivity extends Service {

    private static final UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    BluetoothDevice device;
    private BluetoothSocket bluetoothSocket;
    private ArrayList<BluetoothSocket> printers = new ArrayList<>();
    private ArrayList<BluetoothDevice> bondDevicesList = new ArrayList<>();
    private boolean TCPon = false, autoConnent = true;
    private String myAddress = "", cmd = "", lastUserData = "";
    private String[] userData;
    private int printerCount = 0;
    BluetoothSocket printer;
    OutputStream outputStream;

    private Handler handler = new Handler() {

        @Override
        public void handleMessage(Message msg) {
            // TODO Auto-generated method stub
            super.handleMessage(msg);

            String response = (String) msg.obj;
            String[] orderData;
            switch (msg.what) {
                case 0:
                    orderData = response.split("\\|");                    //0是订单号，1是打印的内容
                    if (orderData.length == 1) {//如果是包重发，直接接着打印
                        print("", orderData[0]);
                    } else {
                        print(orderData[0], orderData[1]);
                    }
                    MediaPlayer player = MediaPlayer.create(PrintActivity.this, R.raw.dingdong);
                    player.start();

                    MainActivity.acquireWakeLock(true); //唤醒屏幕

                    //在这里进行UI操作，将结果显示到界面上
                    break;
                case 1:
                    System.out.println(response);//打印错误信息
                    break;
                default:
                    break;
            }

        }

    };

    @Override
    public void onCreate() {
        super.onCreate();
        showBarMess("正在运行中...");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        initData(intent);
//        return super.onStartCommand(intent, flags, startId);
        return START_REDELIVER_INTENT;//保持进程后台运行
    }

    public void showBarMess(String mess) {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        Notification builder = new Notification.Builder(this)
                .setTicker("容合点餐")
                .setContentTitle("容合点餐")
                .setContentText(mess)
                .setSmallIcon(R.drawable.ic_menu_gallery)
                .build();
        manager.notify(67, builder);
    }

    private void initData(Intent intent) {
        readFile("user.ng");
        if (userData[2].length() > 1) {//设置了设备ID才启动线程和连接
            isDisconnect();//更新打印机状态
            cmd = intent.getStringExtra(MainActivity.CMD);//获取命令，字符串类型
            System.out.println(cmd);
            if (cmd.equals("reConnect")) {//重连服务器，readFile里自带了
                return;
            }
            device = intent.getParcelableExtra(MainActivity.DEVICE);

            if (!bondDevicesList.contains(device)) {//是否已连接过 没有点击或或者连接丢失
                startConnect();
            } else {
                ToastUtil.showToast(PrintActivity.this, "已连接！");
            }
        } else {
            autoConnent = false;
            Toast.makeText(PrintActivity.this, "您还未设置打印机编号", Toast.LENGTH_LONG).show();
            stopSelf();
        }

    }

    private void initView() {
//        myName = device.getName() == null ? device.getAddress() : device.getName();
        myAddress = device.getAddress();  //获取设备地址，保持这个玩意，下次直接连接
    }

    /**
     * 连接蓝牙设备
     */
    private void startConnect() {
        try {
            bluetoothSocket = device.createRfcommSocketToServiceRecord(uuid);
            bluetoothSocket.connect();
            printers.add(bluetoothSocket);//如果连接成功，加入打印机输出流列表
            bondDevicesList.add(device);//添加到已连接队列
            if (!bluetoothAdapter.isDiscovering()) {
                System.out.println("蓝牙没扫描到！");
            }
            setConnectResult(bluetoothSocket.isConnected());
            //蓝牙连接成功后才连接服务器
            ServerListener mt = new ServerListener();
            new Thread(mt).start();
        } catch (Exception e) {
            setConnectResult(false);
        }
    }

    private void setConnectResult(boolean result) {
        if (result) {
            writeFile("defaultDevice.ng", myAddress);
            ToastUtil.showToast(PrintActivity.this, "打印机连接成功！");
            printerCount++;
            showBarMess("已连接" + String.valueOf(printerCount) + "个打印机");
        } else {
            ToastUtil.showToast(PrintActivity.this, "打印机连接失败，请检查设置！");
        }

    }

    /**
     * 打印数据
     */
    public void print(String no, String sendData) {
        if (TextUtils.isEmpty(sendData)) {
            return;
        }
        if (!no.equals(""))//如果不是续传
            no = "#" + no + "\n";
        int a = sendData.length();
        String deng = sendData.substring(a - 1, a);
        if (deng.equals("="))//如果结束了
            sendData = sendData + "\n" + userData[3] + "\n\n\n";//3行回车正好加上打印后缀
        int i;
        BluetoothSocket printer;
        for (i = 0; i < printers.size(); i++) {
            printer = printers.get(i);
            if (printer.isConnected()) {
                try {
                    outputStream = printer.getOutputStream();
                    byte[] print_data = sendData.getBytes("gbk");
                    if (!no.equals("")) {
                        byte[] print_no = no.getBytes("gbk");
                        outputStream.write(CommandsUtil.BYTE_COMMANDS[4]);//加大字体
                        outputStream.write(CommandsUtil.BYTE_COMMANDS[6]);//加粗打印订单号
                        //      outputStream.write(print_no, 0, print_no.length);
                    }
                    outputStream.write(CommandsUtil.BYTE_COMMANDS[13]);//变小字体
                    outputStream.write(print_data, 0, print_data.length);
                    outputStream.flush();
                } catch (IOException e) {
                    ToastUtil.showToast(PrintActivity.this, "发送失败！");
                }
            } else {
                ToastUtil.showToast(PrintActivity.this, "有设备掉线了，请检查连接！");
                printerCount--;
                showBarMess("已连接" + String.valueOf(printerCount) + "个打印机");
                isDisconnect();//更新打印机状态
            }
        }
    }

    public Socket mySocket;

    class ServerListener implements Runnable {
        public void run() {
            android.os.Process.setThreadPriority(10);
            while (autoConnent) {  //自动重连
                if (!TCPon) {//只连接一次
                    try {
                        mySocket = new Socket("121.42.24.85", 45612);
                        DataInputStream input = new DataInputStream(mySocket.getInputStream());
                        DataOutputStream ouput = new DataOutputStream(mySocket.getOutputStream());

                        String str1 = userData[2];
                        byte[] a = str1.getBytes();
                        ouput.write(a, 0, str1.length());
                        ouput.flush();

                        TCPon = true;
                        byte[] b = new byte[65535];
                        while (true) {
                            int length = input.read(b);
                            String Msg = new String(b, 0, length, "gb2312");

                            if (Msg.equals("Alive")) {
                                ouput.write("Alive".getBytes(), 0, 5);
                                ouput.flush();
                            } else {
                                ouput.write("OK".getBytes(), 0, 2);
                                ouput.flush();

                                Message message = new Message();
                                message.what = 0;
                                //将服务器返回的结果存放到Message中
                                message.obj = Msg;
                                handler.sendMessage(message);
                            }
                        }

                    } catch (Exception ex) {
                        //连接断开
                        TCPon = false;
                        Message message = new Message();
                        message.what = 1;
                        //将服务器返回的结果存放到Message中
                        message.obj = ex.toString();
                        handler.sendMessage(message);
                    }
                }

                try {
                    Thread.sleep(3000);  //5s延迟重连
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public void isDisconnect() {//更新最新的连接状态,关闭废弃的连接
        int i, j;
        for (i = 0; i < printers.size(); i++) {//遍历所有蓝牙连接
            printer = printers.get(i);
            if (!printer.isConnected()) {//定时监测打印机失联，把连接队列删除
//                try {
//                    printer.close();//关闭蓝牙连接
//                    outputStream=printer.getOutputStream();
//                    outputStream.close();//关闭输出流
//                } catch (IOException e) {
//                    e.printStackTrace();
//                }
                System.out.println(i);
                j = printers.indexOf(printer);
                printers.remove(printers.get(j));
                bondDevicesList.remove(bondDevicesList.get(j));
            }
        }
    }
    /**
     * 断开蓝牙设备连接,服务关闭的时候触发
     */
    public void disconnect() {
        System.out.println("断开蓝牙设备连接");

        int i;
        BluetoothSocket printer;
        for (i = 0; i < printers.size(); i++) {//遍历所有蓝牙连接
            printer = printers.get(i);
            try {
                printer.close();//关闭蓝牙连接
                outputStream = printer.getOutputStream();
                outputStream.close();//关闭输出流
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
//            startConnect();//重新连接

    }

    public String readFile(String fileName) {//读取商家设置文件，如果有变更则重连服务器
        String res = "";
        try {
            FileInputStream fin = openFileInput(fileName);
            int length = fin.available();
            byte[] buffer = new byte[length];
            fin.read(buffer);
            res = new String(buffer);
            fin.close();
            if (res.length() > 1) {
                userData = res.split("\\|");
            }
            if (lastUserData.length() != 0) {
                if (!res.equals(lastUserData)) //如果发生了信息变更则重连
                    try {
                        mySocket.close();
                    } catch (IOException e) {

                    }
            } else lastUserData = res;
        } catch (Exception e) {
        }
        return res;
    }

    public void writeFile(String fileName, String writestr) {
        try {
            FileOutputStream fout = openFileOutput(fileName, MODE_PRIVATE);
            byte[] bytes = writestr.getBytes();
            fout.write(bytes);
            fout.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    @Override
    public void onDestroy() {
        super.onDestroy();
        disconnect();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

}
