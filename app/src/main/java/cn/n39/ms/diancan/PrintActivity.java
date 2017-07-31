package cn.n39.ms.diancan;

import android.app.AlertDialog;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.DialogInterface;
import android.content.Intent;
import android.media.MediaPlayer;
import android.media.SoundPool;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Toast;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.util.UUID;

import static android.content.ContentValues.TAG;


public class PrintActivity extends Service {

    private static final UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    BluetoothDevice device;
    private boolean isConnection;
    private BluetoothSocket bluetoothSocket;
    private OutputStream outputStream;
    private boolean TCPon = false, autoConnent = true;
    private String myName = "", myAddress = "";
    private String[] userData;
    private long dataCount = 0;
    private SoundPool soundPool;
    public static final String TAG = "MyService";

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
                    if (isConnection)
                        try {
                            outputStream.write(CommandsUtil.BYTE_COMMANDS[4]);//加大字体
                            outputStream.write(CommandsUtil.BYTE_COMMANDS[6]);//加粗打印订单号
                            print("\n" + "#" + orderData[0] + "\n");
                            outputStream.write(CommandsUtil.BYTE_COMMANDS[3]);//变小字体
                            outputStream.write(CommandsUtil.BYTE_COMMANDS[5]);//取消加粗
                            print(orderData[1] + userData[1] + "\n\n\n");
                            MediaPlayer player = MediaPlayer.create(PrintActivity.this, R.raw.dingdong);
                            player.start();
                        } catch (IOException e) {
                            // ToastUtil.showToast(PrintActivity.this,"设置指令失败！");
                        }
                    //在这里进行UI操作，将结果显示到界面上
                    break;
                default:
                    break;
            }

        }

    };

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate() executed");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand() executed");
        initData(intent);
        startConnect();
        ServerListener mt = new ServerListener();
        new Thread(mt).start();
        Log.d(TAG, "onStartCommand()2 executed");
        return super.onStartCommand(intent, flags, startId);
    }

    private void initData(Intent intent) {
        device = intent.getParcelableExtra(MainActivity.DEVICE);
        // if (device == null) return;
        String str = readFile("user.ng");
        if (str.length() > 1) {
            userData = str.split("\\|");
        } else {
            autoConnent = false;
            Toast.makeText(PrintActivity.this, "提示，你还未设置商家信息。", Toast.LENGTH_LONG).show();
        }
    }

    public String readFile(String fileName) {
        String res = "";
        try {
            FileInputStream fin = openFileInput(fileName);
            int length = fin.available();
            byte[] buffer = new byte[length];
            fin.read(buffer);
            res = new String(buffer);
            fin.close();
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


    private void initView() {
        myName = device.getName() == null ? device.getAddress() : device.getName();
        myAddress = device.getAddress();  //获取设备地址，保持这个玩意，下次直接连接
    }

    /**
     * 连接蓝牙设备
     */
    private void startConnect() {
        try {
            bluetoothSocket = device.createRfcommSocketToServiceRecord(uuid);
            bluetoothSocket.connect();
            outputStream = bluetoothSocket.getOutputStream();

            isConnection = bluetoothSocket.isConnected();

            if (bluetoothAdapter.isDiscovering()) {
                System.out.println("关闭适配器！");
                bluetoothAdapter.isDiscovering();
            }
            setConnectResult(isConnection);
        } catch (Exception e) {
            setConnectResult(false);
        }
    }

    private void setConnectResult(boolean result) {
        if (result) writeFile("defaultDevice.ng", myAddress);
    }

    /**
     * 打印数据
     */
    public void print(String sendData) {
        if (TextUtils.isEmpty(sendData)) {
            ToastUtil.showToast(PrintActivity.this, "请输入打印内容！");
            return;
        }
        if (isConnection) {
            System.out.println("开始打印！！");
            try {
                byte[] data = sendData.getBytes("gbk");
                outputStream.write(data, 0, data.length);
                outputStream.flush();
            } catch (IOException e) {
                ToastUtil.showToast(PrintActivity.this, "发送失败！");
            }
        } else {
            ToastUtil.showToast(PrintActivity.this, "设备未连接，请重新连接！");
        }
    }

    public Socket mySocket;

    class ServerListener implements Runnable {
        public void run() {
            while (autoConnent) {  //自动重连
                if (!TCPon)
                    try {
                        mySocket = new Socket("121.42.24.85", 45612);
                        DataInputStream input = new DataInputStream(mySocket.getInputStream());
                        DataOutputStream ouput = new DataOutputStream(mySocket.getOutputStream());

                        String str1 = userData[0];
                        byte[] a = str1.getBytes();
                        ouput.write(a, 0, str1.length());
                        ouput.flush();

                        TCPon = true;
                        byte[] b = new byte[10000];
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
                        TCPon = false;
                        Message message = new Message();
                        message.what = 1;
                        //将服务器返回的结果存放到Message中
                        message.obj = ex.toString();
                        handler.sendMessage(message);
                    }

                try {
                    Thread.sleep(3000);  //3s延迟重连
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * 断开蓝牙设备连接
     */
    public void disconnect() {
        System.out.println("断开蓝牙设备连接");
        try {
            bluetoothSocket.close();
            outputStream.close();
        } catch (IOException e) {
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
