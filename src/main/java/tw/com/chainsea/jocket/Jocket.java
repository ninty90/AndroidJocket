package tw.com.chainsea.jocket;

import org.json.JSONException;
import org.json.JSONObject;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import cn.hadcn.davinci.DaVinci;
import cn.hadcn.davinci.base.VinciLog;
import cn.hadcn.davinci.http.OnDaVinciRequestListener;
import tw.com.chainsea.jocket.websocket.WebSocketClient;

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

    private static Jocket sJocket = null;
    private OnJocketListener mJocketListener = null;
    private String mSessionId = null;
    private String mUrl = null;
    private Map<String, String> header;

    public static Jocket with() {
        if ( sJocket == null ) {
            sJocket = new Jocket();
        }
        return sJocket;
    }

    private Jocket(){
        header = new HashMap<>();
        header.put("Referer", "Android");
    }

    /**
     *
     * @param url url without scheme
     * @param listener Jocket listener
     */
    public void connect(String url, OnJocketListener listener){
        mUrl = url;
        mJocketListener = listener;
        String prepareUrl = "http://" + mUrl + ".jocket_prepare";

        DaVinci.with().getHttpRequest()
                .headers(header)
                .doPost(prepareUrl, (String) null, new PrepareListener());
    }

    public void close() {
        DaVinci.with()
                .getHttpRequest()
                .headers(header)
                .doPost(pollingUrl, CLOSE_PACK, null);
    }

    private class PrepareListener implements OnDaVinciRequestListener {

        @Override
        public void onDaVinciRequestSuccess(String s) {
            try {
                JSONObject jsonObject = new JSONObject(s);
                mSessionId = jsonObject.getString("sessionId");
                //tryWebSocket();
                tryPolling();
            } catch (JSONException e) {
                VinciLog.e("session id parse", e);
                mJocketListener.onDisconnect();
            }
        }

        @Override
        public void onDaVinciRequestFailed(String s) {
            VinciLog.e("failed to get session id, reason = " + s);
            mJocketListener.onDisconnect();
        }
    }

    WebSocketClient socketClient;
    private void tryWebSocket() {
        String wsUrl = "ws://" + mUrl + "?jocket_sid=" + mSessionId;
        VinciLog.d("trying websocket connection: " + wsUrl);
        socketClient = new WebSocketClient(URI.create(wsUrl), new WebsocketListener(), null);
        socketClient.connect();
    }

    String pollingUrl;
    private void tryPolling() {
        socketClient = null;
        VinciLog.d("websocket connection failed." );
        pollingUrl = "http://" + mUrl + ".jocket_polling?jocket_sid=" + mSessionId;
        VinciLog.d("trying polling connection: " + pollingUrl);

        polling();

        DaVinci.with()
                .getHttpRequest()
                .headers(header)
                .doPost(pollingUrl, PING_PACK, null);
    }

    private void polling() {
        DaVinci.with().getHttpRequest()
                .timeOut(35000)
                .headers(header)
                .doGet(pollingUrl, null, new PollingListener());
    }

    private class PollingListener implements OnDaVinciRequestListener {

        @Override
        public void onDaVinciRequestSuccess(String s) {
            try {
                JSONObject jsonObject = new JSONObject(s);
                String type = jsonObject.getString("type");
                switch (type) {
                    case "close":
                        break;
                    case "pong":
                        VinciLog.i("pong received");
                        mJocketListener.onConnected();
                        DaVinci.with().getHttpRequest()
                                .headers(header)
                                .doPost(pollingUrl, OPEN_PACK, null);
                        polling();
                        break;
                    case "noop":
                        polling();
                        break;
                    default:
                        mJocketListener.onReceive(s);
                        polling();
                        break;
                }
            } catch (JSONException e) {
                VinciLog.e("parse json error", e);
            }
        }

        @Override
        public void onDaVinciRequestFailed(String s) {
            VinciLog.e("polling failed, failed reason = " + s);
        }
    }

    private class WebsocketListener implements WebSocketClient.Listener {

        @Override
        public void onConnect() {
            socketClient.send(PING_PACK);
        }

        @Override
        public void onMessage(String message) {
            if ( message.equals(PONG_PACK) ) {
                VinciLog.i("websocket connected");
                socketClient.send(OPEN_PACK);
            } else {
                mJocketListener.onReceive(message);
            }
        }

        @Override
        public void onMessage(byte[] data) {
            VinciLog.i("websocket onMessage");
        }

        @Override
        public void onDisconnect(int code, String reason) {
            VinciLog.i("websocket onDisconnect");
            tryPolling();
        }

        @Override
        public void onError(Exception error) {
            VinciLog.i("websocket onError");
            tryPolling();
        }
    }

    public void release() {
        sJocket = null;
    }
}
