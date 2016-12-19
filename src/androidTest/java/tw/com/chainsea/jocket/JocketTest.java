package tw.com.chainsea.jocket;

import android.test.AndroidTestCase;

import java.util.HashMap;
import java.util.Map;

import cn.hadcn.davinci.DaVinci;
import cn.hadcn.davinci.log.LogLevel;

/**
 * jocket test
 * Created by 90Chris on 2016/6/7.
 */
public class JocketTest extends AndroidTestCase {
    private Jocket jocket;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        DaVinci.init(LogLevel.DEBUG, "JocketTest", getContext());
        jocket = new Jocket("ce.topnology.com.cn:12721/ce");
    }

    public void testJocketConnection() throws Exception {
        Map<String, Object> header = new HashMap<>();
        header.put("Referer", "Android");
        jocket.connect("/jocket/mobile", header, new OnJocketListener() {
            @Override
            public void onDisconnect(JocketCode code, String reason) {

            }

            @Override
            public void onConnected() {

            }

            @Override
            public void onReceive(String msg) {

            }
        });
        Thread.sleep(10000);
    }
}
