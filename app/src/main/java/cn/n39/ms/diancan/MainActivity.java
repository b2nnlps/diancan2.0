package cn.n39.ms.diancan;

import android.Manifest;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.design.widget.CoordinatorLayout;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.support.design.widget.NavigationView;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;


public class MainActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener {

    private View page_main, page_setting, page_printer, page_web;
    //===============搜索蓝牙打印机================
    private BluetoothAdapter mBluetoothAdapter;
    private static final int REQUEST_ENABLE_BT = 1;
    private static final int REQUEST_FINE_LOCATION = 0;
    public static final String DEVICE = "device", CMD = "cmd";
    private ArrayList<BluetoothDevice> unbondDevicesList = new ArrayList<>();
    private ArrayList<BluetoothDevice> bondDevicesList = new ArrayList<>();
    private DeviceReceiver deviceReceiver;
    private ArrayList<String> boundName = new ArrayList<>();
    private ArrayList<String> unboundName = new ArrayList<>();
    private ArrayList<String> connentName = new ArrayList<>();
    private MyBluetoothAdapter boundAdapter;
    private MyBluetoothAdapter unboundAdapter;
    private String defaultDevice = "";
    private boolean isPrinter = false;
    String userDeviceId, userName, userPassword, userText, userHash;
    //=================绑定控件====================
    Button btnOpen, btnSearch, btn_save;
    ListView lvUnboundDevice, lvBoundDevice;
    EditText k_userDeviceId, k_userName, k_userPassword, k_userText;
    Toolbar toolbar;
    private WebView mWebView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        toolbar = (Toolbar) findViewById(R.id.toolbar);
        changeTitle(R.drawable.ic_index_white, this.getString(R.string.app_name));

        NavigationView navigationView = (NavigationView) findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);   //保持屏幕常亮
        initPage();
    }

    private void initView() {//初始化搜索蓝牙打印机相关
        if (!isPrinter) {//只初始化启动一次
            mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            defaultData();
            showBoundDevices();
            initIntentFilter();
            isPrinter = true;
        }
    }

    private void showBoundDevices() {//显示绑定的设备
        Set<BluetoothDevice> bluetoothDeviceSet = mBluetoothAdapter.getBondedDevices();
        for (BluetoothDevice device : bluetoothDeviceSet) {//搜索所有系统的蓝牙设备
            if (!bondDevicesList.contains(device)) bondDevicesList.add(device);
            if (!defaultDevice.equals("") && device.getAddress().equals(defaultDevice)) {
            }
        }
        boundName.addAll(getData(bondDevicesList));
        boundAdapter = new MyBluetoothAdapter(this, boundName);
        lvBoundDevice.setAdapter(boundAdapter);
        lvBoundDevice.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> arg0, View arg1,     //添加LIST的点击事件
                                    int arg2, long arg3) {
                BluetoothDevice device = bondDevicesList.get(arg2);
                Intent intent = new Intent(MainActivity.this, PrintActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_FORWARD_RESULT);
                intent.putExtra(CMD, "connect");
                intent.putExtra(DEVICE, device);
                startService(intent);
                ToastUtil.showToast(MainActivity.this, "正在连接打印机...");
            }
        });

        unboundName.addAll(getData(unbondDevicesList));
        unboundAdapter = new MyBluetoothAdapter(this, unboundName);
        lvUnboundDevice.setAdapter(unboundAdapter);
        lvUnboundDevice.setOnItemClickListener(new AdapterView.OnItemClickListener() {

            @Override
            public void onItemClick(AdapterView<?> arg0, View arg1,
                                    int arg2, long arg3) {
                try {
                    Method createBondMethod = BluetoothDevice.class
                            .getMethod("createBond");
                    createBondMethod.invoke(unbondDevicesList.get(arg2));
                    bondDevicesList.add(unbondDevicesList.get(arg2));
                    unbondDevicesList.remove(arg2);
                    addBondDevicesToListView();
                    addUnbondDevicesToListView();
                } catch (Exception e) {
                    ToastUtil.showToast(MainActivity.this, "配对失败");

                }

            }
        });

    }

    /*判断蓝牙是否打开*/
    public boolean isOpen() {
        return mBluetoothAdapter.isEnabled();
    }

    //点击打开蓝牙并搜索
    private void pressTb() {
        if (!isOpen()) {
            openBluetooth();
        }
        searchDevices();
    }

    //打开蓝牙
    private void openBluetooth() {
        if (mBluetoothAdapter == null) {
            ToastUtil.showToast(this, "设备不支持蓝牙");
        } else {
            Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(intent, REQUEST_ENABLE_BT);
        }
    }

    /*搜索蓝牙设备*/
    public void searchDevices() {

        int permission = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION);
        //判断是否有权限
        if (permission != PackageManager.PERMISSION_GRANTED) {
            //请求权限
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},
                    REQUEST_FINE_LOCATION);
            //判断是否需要 向用户解释，为什么要申请该权限
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.READ_CONTACTS)) {
                ToastUtil.showToast(this, "Android 6.0及以上的设备需要用户授权才能搜索蓝牙设备");
            }

        } else {
            startSearch();
        }
    }

    private void startSearch() {
        bondDevicesList.clear();
        unbondDevicesList.clear();
        mBluetoothAdapter.startDiscovery();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        switch (requestCode) {
            case REQUEST_FINE_LOCATION:
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // The requested permission is granted.
                    startSearch();
                } else {
                    // The user disallowed the requested permission.
                    ToastUtil.showToast(MainActivity.this, "您拒绝授权搜索蓝牙设备！");
                }
                break;

        }

    }

    private void initIntentFilter() {
        deviceReceiver = new DeviceReceiver(this);
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothDevice.ACTION_FOUND);
        intentFilter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
        intentFilter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        intentFilter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        registerReceiver(deviceReceiver, intentFilter);

    }

    /*蓝牙广播接收器
     */
    private class DeviceReceiver extends BroadcastReceiver {
        ProgressDialog progressDialog;

        DeviceReceiver(Context context) {
            progressDialog = new ProgressDialog(context);
            progressDialog.setTitle("请稍等...");
            progressDialog.setMessage("搜索蓝牙设备中...");
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            {
                String action = intent.getAction();
                if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                    BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    if (device.getBondState() == BluetoothDevice.BOND_BONDED) {
                        addBandDevices(device);
                    } else {
                        addUnbondDevices(device);
                    }
                } else if (BluetoothAdapter.ACTION_DISCOVERY_STARTED.equals(action)) {
                    progressDialog.show();

                } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED
                        .equals(action)) {
                    System.out.println("设备搜索完毕");
                    progressDialog.dismiss();

                    addUnbondDevicesToListView();
                    addBondDevicesToListView();

                }
                if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
                    if (mBluetoothAdapter.getState() == BluetoothAdapter.STATE_ON) {
                        btnSearch.setEnabled(true);
                        lvUnboundDevice.setEnabled(true);
                        lvBoundDevice.setEnabled(true);
                    } else if (mBluetoothAdapter.getState() == BluetoothAdapter.STATE_OFF) {
                        btnSearch.setEnabled(false);
                        lvUnboundDevice.setEnabled(false);
                        lvBoundDevice.setEnabled(false);

                    }
                }

            }
        }
    }

    private ArrayList<String> getData(ArrayList<BluetoothDevice> list) {
        ArrayList<String> data = new ArrayList<>();
        int count = list.size();
        for (int i = 0; i < count; i++) {
            String deviceName = list.get(i).getName();
            data.add(deviceName != null ? deviceName : list.get(i).getAddress());
        }
        return data;
    }

    /**
     * 添加已绑定蓝牙设备到ListView
     */
    private void addBondDevicesToListView() {
        boundName.clear();
        boundName.addAll(getData(bondDevicesList));
        boundAdapter.notifyDataSetChanged();
    }

    /**
     * 添加未绑定蓝牙设备到ListView
     */
    private void addUnbondDevicesToListView() {
        unboundName.clear();
        unboundName.addAll(getData(unbondDevicesList));
        unboundAdapter.notifyDataSetChanged();
    }

    /*添加未绑定设备*/
    private void addUnbondDevices(BluetoothDevice device) {
        if (!unbondDevicesList.contains(device)) {
            unbondDevicesList.add(device);
        }
    }

    /*添加绑定设备 */
    private void addBandDevices(BluetoothDevice device) {
        if (!bondDevicesList.contains(device)) {
            bondDevicesList.add(device);
        }
    }

    public void initPage() {//初始化绑定控件界面
        page_main = findViewById(R.id.page_main);
        page_setting = findViewById(R.id.page_setting);
        page_printer = findViewById(R.id.page_printer);
        page_web = findViewById(R.id.page_order);
        btnSearch = (Button) findViewById(R.id.btnSearch);
        btn_save = (Button) findViewById(R.id.btn_save);
        lvBoundDevice = (ListView) findViewById(R.id.lv_bound_device);
        lvUnboundDevice = (ListView) findViewById(R.id.lv_unbound_device);
        k_userDeviceId = (EditText) findViewById(R.id.userDeviceId);
        k_userName = (EditText) findViewById(R.id.userName);
        k_userPassword = (EditText) findViewById(R.id.userPassword);
        k_userText = (EditText) findViewById(R.id.userText);
        //单行输入
        k_userName.setSingleLine(true);
        k_userDeviceId.setSingleLine(true);
        btnSearch.setOnClickListener(new View.OnClickListener() {//搜索蓝牙设备
            @Override
            public void onClick(View v) {
                pressTb();//没有打开蓝牙则打开蓝牙并搜索，打开则开始搜索
            }
        });
        btn_save.setOnClickListener(new View.OnClickListener() {//搜索蓝牙设备
            @Override
            public void onClick(View v) {
                getSetting();
                if (userName.length() == 0 || userPassword.length() == 0) {
                    Toast.makeText(MainActivity.this, "前两项必须要填写", Toast.LENGTH_LONG).show();
                    return;
                }
                if (userDeviceId.length() == 0) userDeviceId = " ";//自动补
                if (userText.length() == 0) userText = " ";//自动补

                String str = userName + "|" + userPassword + "|" + userDeviceId + "|" + userText;
                writeFile("user.ng", str);//保存设置
                userHash = ToastUtil.stringToMD5(userName + "llrj" + userPassword);
                Toast.makeText(MainActivity.this, "保存成功！", Toast.LENGTH_LONG).show();
                //重置连接
                Intent intent = new Intent(MainActivity.this, PrintActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_FORWARD_RESULT);

                intent.putExtra(CMD, "reConnect");
                startService(intent);
            }
        });
        readSetting();
        mWebView = (WebView) findViewById(R.id.webview);
        // 启用javascript
        mWebView.getSettings().setJavaScriptEnabled(true);
        mWebView.getSettings().setAllowFileAccess(true);
        mWebView.getSettings().setMediaPlaybackRequiresUserGesture(false);

    }

    public void getSetting() {//获取商家设置
        userName = k_userName.getText().toString().replace(" ", "");
        userPassword = k_userPassword.getText().toString().replace(" ", "");
        userDeviceId = k_userDeviceId.getText().toString().replace(" ", "");
        userText = k_userText.getText().toString().replace(" ", "");
    }

    public void readSetting() {//读取商家设置
        String str = readFile("user.ng");
        if (str.length() > 1) {
            String[] temp = str.split("\\|");
            k_userName.setText(temp[0].replace(" ", ""));
            k_userPassword.setText(temp[1].replace(" ", ""));
            k_userDeviceId.setText(temp[2].replace(" ", ""));
            k_userText.setText(temp[3].replace(" ", ""));
            getSetting();
            userHash = ToastUtil.stringToMD5(userName + "llrj" + userPassword);
        }
    }

    public void changeTitle(int Resid, String title) {//改变APP的标题
        if (Resid != 0)
            toolbar.setLogo(Resid);//LOGO
        toolbar.setTitle(title);
        setSupportActionBar(toolbar);
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.setDrawerListener(toggle);
        toggle.syncState();
    }

    public void showPage(int page) {//切换界面
        String url = "?username=" + userName + "&hash=" + userHash + "&device_id=" + userDeviceId;
        page_setting.setVisibility(View.GONE);
        page_main.setVisibility(View.GONE);
        page_printer.setVisibility(View.GONE);
        page_web.setVisibility(View.GONE);
        switch (page) {
            case 0://首页
                page_main.setVisibility(View.VISIBLE);
                changeTitle(R.drawable.ic_index_white, "首页");
                break;
            case 1://订单查看
                page_web.setVisibility(View.VISIBLE);
                //  mWebView.loadUrl("file:///android_asset/order-list.html" + url);
                mWebView.loadUrl("http://ms.n39.cn/dc/order-list.html" + url);
                changeTitle(R.drawable.ic_order_white, "订单查看");
                break;
            case 2://厨房订单
                page_web.setVisibility(View.VISIBLE);
                //mWebView.loadUrl("file:///android_asset/cpdl.html" + url);
                mWebView.loadUrl("http://ms.n39.cn/dc/cpdl.html" + url);
                changeTitle(R.drawable.ic_kitchen_white, "厨房订单");
                break;
            case 3://传菜订单
                page_web.setVisibility(View.VISIBLE);
                // mWebView.loadUrl("file:///android_asset/ccdl.html" + url);
                mWebView.loadUrl("http://ms.n39.cn/dc/ccdl.html" + url);
                changeTitle(R.drawable.ic_chuancai_white, "传菜订单");
                break;
            case 4://打印机连接
                page_printer.setVisibility(View.VISIBLE);
                initView();
                changeTitle(R.drawable.ic_printer_white, "打印机连接");
                break;
            case 5://商家设置
                page_setting.setVisibility(View.VISIBLE);
                readSetting();
                changeTitle(R.drawable.ic_setting_white, "商家设置");
                break;
        }
    }

    @Override
    public void onBackPressed() {
        try {
            DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
            if (drawer.isDrawerOpen(GravityCompat.START)) {
                drawer.closeDrawer(GravityCompat.START);
            }
        } finally {
            moveTaskToBack(true);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @SuppressWarnings("StatementWithEmptyBody")
    @Override
    public boolean onNavigationItemSelected(MenuItem item) {
        // Handle navigation view item clicks here.
        int id = item.getItemId();

        if (id == R.id.nav_index) {
            showPage(0);
        } else if (id == R.id.nav_order) {
            showPage(1);
        } else if (id == R.id.nav_kitchen) {
            showPage(2);
        } else if (id == R.id.nav_deviler) {
            showPage(3);
        } else if (id == R.id.nav_printer) {
            showPage(4);
        } else if (id == R.id.nav_manager) {
            showPage(5);
        }

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            unregisterReceiver(deviceReceiver);
        } catch (Exception e) {

        }
    }

    private void defaultData() {//获取默认设备
        String fileName = "defaultDevice.ng"; //文件名字
        defaultDevice = readFile(fileName);
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
}
