package cn.n39.ms.diancan;

import android.content.Context;
import android.widget.Toast;

/**
 * Created by qs on 2016/10/17.
 */

public class ToastUtil {


    public static void showToast(Context context, String str) {

        Toast.makeText(context, str, Toast.LENGTH_SHORT).show();
    }
}
