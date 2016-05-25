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
    private static String PING_PACK = "{\"type\":\"ping\"}";
    private static String PONG_PACK = "{\"type\":\"pong\"}";
    private static String OPEN_PACK = "{\"type\":\"open\"}";

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

    public void connect(String url, OnJocketListener listener){
        mUrl = url;
        mJocketListener = listener;
        String prepareUrl = mUrl + ".jocket_prepare";

        DaVinci.with().getHttpRequest()
                .headers(header)
                .doPost(prepareUrl, (String) null, new PrepareListener());
    }

    private class PrepareListener implements OnDaVinciRequestListener {

        @Override
        public void onDaVinciRequestSuccess(String s) {
            try {
                JSONObject jsonObject = new JSONObject(s);
                mSessionId = jsonObject.getString("sessionId");
                tryWebSocket();
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
        URI uri = URI.create(mUrl);
        String wsUrl = "ws://" + uri.getHost() + "?jocket_sid=" + mSessionId;
        socketClient = new WebSocketClient(URI.create(wsUrl), new WebsocketListener(), null);
        socketClient.connect();
    }

    String pollingUrl;
    private void tryPolling() {
        socketClient = null;
        DaVinci.with().getHttpRequest()
                .timeOut(35000)
                .headers(header)
                .doGet(mUrl, null, new PollingListener());
        pollingUrl = mUrl + ".jocket_polling?jocket_sid=" + mSessionId;
        DaVinci.with()
                .getHttpRequest()
                .headers(header)
                .doPost(pollingUrl, PING_PACK, null);
    }

    private class PollingListener implements OnDaVinciRequestListener {

        @Override
        public void onDaVinciRequestSuccess(String s) {
            if (s.equals(PONG_PACK)) {
                VinciLog.i("polling connected");
                DaVinci.with().getHttpRequest()
                        .headers(header)
                        .doPost(pollingUrl, OPEN_PACK, null);
            } else {
                mJocketListener.onReceive(s);
            }
        }

        @Override
        public void onDaVinciRequestFailed(String s) {
            VinciLog.e("polling connected failed, reason = " + s);
            mJocketListener.onDisconnect();
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
            tryPolling();
        }

        @Override
        public void onDisconnect(int code, String reason) {
            tryPolling();
        }

        @Override
        public void onError(Exception error) {
            tryPolling();
        }
    }

    public void release() {
        sJocket = null;
    }
}
