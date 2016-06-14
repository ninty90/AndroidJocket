package tw.com.chainsea.jocket;

import android.test.AndroidTestCase;

import cn.hadcn.davinci.DaVinci;
import cn.hadcn.davinci.base.LogLevel;

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
        jocket = new Jocket("127.0.0.1:12821/ecp");
    }

    public void testJocketConnection() throws Exception {
        jocket.connect("/jocket/mobile", null, new OnJocketListener() {
            @Override
            public void onDisconnect(ErrCode code, String reason) {

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
