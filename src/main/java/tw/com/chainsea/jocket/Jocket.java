package tw.com.chainsea.jocket;

import org.json.JSONException;
import org.json.JSONObject;

import java.net.URI;
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
    private final static String UPGRADE_PACK = "{\"type\":\"upgrade\"}";

    private OnJocketListener mJocketListener = null;
    private String mSessionId = null;
    private int mPingTimeout;
    private int mPingInterval;
    private Map<String, String> header;
    private String mBaseUrl;
    private Timer pollingTimer;
    private Timer webSocketTimer;
    private WebSocketClient mWebSocketClient;
    private boolean isWebSocketOK = false;

    public Jocket(String baseUrl){
        mBaseUrl = baseUrl;
        header = new HashMap<>();
        header.put("Referer", "Android");
        DaVinci.with().addThreadPool("polling", 1);
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
        if ( isWebSocketOK ) {
            mWebSocketClient.send(jsonObject.toString());
        } else {
            DaVinci.with().getHttpRequest()
                    .headers(header)
                    .doPost(pollingUrl, jsonObject, null);
        }
    }

    private class PrepareListener implements OnDaVinciRequestListener {

        @Override
        public void onDaVinciRequestSuccess(String s) {
            try {
                JSONObject jsonObject = new JSONObject(s);
                mSessionId = jsonObject.getString("sessionId");
                mPingTimeout = jsonObject.getInt("pingTimeout");
                mPingInterval = jsonObject.getInt("pingInterval");
                boolean upgrade = jsonObject.getBoolean("upgrade");

                tryPolling();

                if ( upgrade ) {
                    String wsUrl = "http://" + mBaseUrl + "/jocket-ws?s=" + mSessionId;
                    mWebSocketClient = new WebSocketClient(URI.create(wsUrl), new WebSocketListener(), header);
                    mWebSocketClient.connect();
                }
            } catch (JSONException e) {
                VinciLog.e("Prepare json parse", e);
                mJocketListener.onDisconnect(JocketCode.SYSTEM_ERR, "json parse error");
            }
        }

        @Override
        public void onDaVinciRequestFailed(int code, String s) {
            VinciLog.e("prepare failed, reason = " + s);
            mJocketListener.onDisconnect(JocketCode.SYSTEM_ERR, "prepare failed");
        }
    }

    private class WebSocketListener implements WebSocketClient.Listener {

        @Override
        public void onConnect() {
            VinciLog.d("WebSocket connect, try to upgrade protocol");
            mWebSocketClient.send(PING_PACK);
        }

        @Override
        public void onMessage(String message) {
            try {
                JSONObject jsonObject = new JSONObject(message);
                String type = jsonObject.getString("type");
                switch (type) {
                    case "close":
                        /**
                         * {"message":"invalid token","code":4901}
                         */
                        String data = jsonObject.getString("data");
                        JSONObject dataJson = new JSONObject(data);
                        mJocketListener.onDisconnect(JocketCode.ofValue(dataJson.getInt("code")), dataJson.getString("message"));
                        close();
                        break;
                    case "pong":
                        VinciLog.i("pong received, protocol upgrade to websocket success");
                        mJocketListener.onConnected();

                        tryWebSocket();
                        break;
                    case "noop":
                        VinciLog.d("noop received");
                        break;
                    default:
                        mJocketListener.onReceive(message);

                        break;
                }
            } catch (JSONException e) {
                VinciLog.e("parse json error", e);
            }
        }

        @Override
        public void onMessage(byte[] data) {

        }

        @Override
        public void onDisconnect(int code, String reason) {
            VinciLog.e("onDisconnect = " + reason);
        }

        @Override
        public void onError(Exception error) {
            VinciLog.e("onError = " + error.getMessage());
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

        pollingTimer = new Timer();
        pollingTimer.schedule(new PingTask(), mPingInterval, mPingInterval);
    }


    private void tryWebSocket() {
        mWebSocketClient.send(UPGRADE_PACK);
        isWebSocketOK = true;

        webSocketTimer = new Timer();
        webSocketTimer.schedule(new WebSocketPingTask(), mPingInterval, mPingInterval);
    }

    private class WebSocketPingTask extends TimerTask {

        @Override
        public void run() {
            mWebSocketClient.send(PING_PACK);
        }
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
        if ( isWebSocketOK ) {
            if ( pollingTimer != null ) {
                pollingTimer.cancel();
                pollingTimer.purge();
                pollingTimer = null;
            }
            return;
        }

        DaVinci.with().tag("polling")
                .getHttpRequest()
                .timeOut(mPingTimeout)
                .headers(header)
                .doGet(pollingUrl, null, new PollingListener());
    }

    public void close() {
        if ( isWebSocketOK ) {
            mWebSocketClient.send(CLOSE_PACK);
            if ( webSocketTimer != null ) {
                webSocketTimer.cancel();
                webSocketTimer.purge();
                webSocketTimer = null;
            }
            return;
        }

        DaVinci.with()
                .getHttpRequest()
                .headers(header)
                .doPost(pollingUrl, CLOSE_PACK, null);
        if ( pollingTimer != null ) {
            pollingTimer.cancel();
            pollingTimer.purge();
            pollingTimer = null;
        }
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
                        mJocketListener.onDisconnect(JocketCode.ofValue(dataJson.getInt("code")), dataJson.getString("message"));
                        close();
                        break;
                    case "pong":
                        polling();
                        VinciLog.i("pong received");
                        mJocketListener.onConnected();
                        /*DaVinci.with().getHttpRequest()
                                .headers(header)
                                .doPost(pollingUrl, OPEN_PACK, null);*/
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
        public void onDaVinciRequestFailed(int code ,String s) {
            VinciLog.e("polling failed, failed reason = " + s);
            mJocketListener.onDisconnect(JocketCode.SYSTEM_ERR, s);
            close();
        }
    }
}
