package tw.com.chainsea.jocket;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import cn.hadcn.davinci.DaVinci;
import cn.hadcn.davinci.log.VinciLog;
import cn.hadcn.davinci.http.OnDaVinciRequestListener;

/**
 * Jocket
 * Created by 90Chris on 2016/5/25.
 */
public class Jocket {
    private final static String PING_PACK = "{\"type\":\"ping\"}";
    private final static String PONG_PACK = "{\"type\":\"pong\"}";
    private final static String OPEN_PACK = "{\"type\":\"open\"}";
    private final static String NOOP_PACK = "{\"type\":\"noop\"}";
    private final static String CLOSE_PACK = "{\"type\":\"close\"}";

    private OnJocketListener mJocketListener = null;
    private String mSessionId = null;
    private int mPingTimeout;
    private int mPingInterval;
    private Map<String, String> header;
    private String mBaseUrl;
    private Timer timer;

    public Jocket(String baseUrl){
        mBaseUrl = baseUrl;
        header = new HashMap<>();
        header.put("Referer", "Android");
    }

    /**
     * connect
     * @param path path, like /chat/simple
     * @param map args
     * @param listener Jocket listener
     */
    public void connect(String path, Map<String, Object> map, OnJocketListener listener){
        mJocketListener = listener;
        String prepareUrl = "http://" + mBaseUrl + path + ".jocket";

        DaVinci.with().getHttpRequest()
                .headers(header)
                .doGet(prepareUrl, map, new PrepareListener());
    }

    public void send(JSONObject jsonObject) {
        DaVinci.with().getHttpRequest()
                .headers(header)
                .doPost(pollingUrl, jsonObject, null);
    }

    private class PrepareListener implements OnDaVinciRequestListener {

        @Override
        public void onDaVinciRequestSuccess(String s) {
            try {
                JSONObject jsonObject = new JSONObject(s);
                mSessionId = jsonObject.getString("sessionId");
                mPingTimeout = jsonObject.getInt("pingTimeout");
                mPingInterval = jsonObject.getInt("pingInterval");
                tryPolling();
            } catch (JSONException e) {
                VinciLog.e("Prepare json parse", e);
                mJocketListener.onDisconnect(ErrCode.SYSTEM_ERR, "json parse error");
            }
        }

        @Override
        public void onDaVinciRequestFailed(String s) {
            VinciLog.e("prepare failed, reason = " + s);
            mJocketListener.onDisconnect(ErrCode.SYSTEM_ERR, "prepare failed");
        }
    }

    String pollingUrl;
    private void tryPolling() {
        pollingUrl = "http://" + mBaseUrl + "/jocket?s=" + mSessionId;
        VinciLog.d("trying polling connection: " + pollingUrl);

        DaVinci.with()
                .getHttpRequest()
                .headers(header)
                .doPost(pollingUrl, PING_PACK, null);
        polling();

        timer = new Timer();
        timer.schedule(new PingTask(), mPingInterval, mPingInterval);
    }

    private class PingTask extends TimerTask {

        @Override
        public void run() {
            DaVinci.with()
                    .getHttpRequest()
                    .headers(header)
                    .doPost(pollingUrl, PING_PACK, null);
            polling();
        }
    }

    private void polling() {
        DaVinci.with().getHttpRequest()
                .timeOut(mPingTimeout)
                .headers(header)
                .doGet(pollingUrl, null, new PollingListener());
    }

    public void close() {
        DaVinci.with()
                .getHttpRequest()
                .headers(header)
                .doPost(pollingUrl, CLOSE_PACK, null);
        timer.cancel();
        timer.purge();
        timer = null;
    }

    private class PollingListener implements OnDaVinciRequestListener {

        @Override
        public void onDaVinciRequestSuccess(String s) {
            try {
                JSONObject jsonObject = new JSONObject(s);
                String type = jsonObject.getString("type");
                switch (type) {
                    case "close":
                        /**
                         * {"message":"invalid token","code":4901}
                         */
                        String data = jsonObject.getString("data");
                        JSONObject dataJson = new JSONObject(data);
                        mJocketListener.onDisconnect(ErrCode.ofValue(dataJson.getInt("code")), dataJson.getString("message"));
                        close();
                        break;
                    case "pong":
                        VinciLog.i("pong received");
                        mJocketListener.onConnected();
                        /*DaVinci.with().getHttpRequest()
                                .headers(header)
                                .doPost(pollingUrl, OPEN_PACK, null);*/
                        polling();
                        break;
                    case "noop":
                        VinciLog.d("noop received");
                        break;
                    default:
                        polling();
                        mJocketListener.onReceive(s);

                        break;
                }
            } catch (JSONException e) {
                VinciLog.e("parse json error", e);
            }
        }

        @Override
        public void onDaVinciRequestFailed(String s) {
            VinciLog.e("polling failed, failed reason = " + s);
            mJocketListener.onDisconnect(ErrCode.SYSTEM_ERR, s);
            close();
        }
    }
}
