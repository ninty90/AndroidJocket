package tw.com.chainsea.jocket;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.text.TextUtils;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import cn.hadcn.davinci.DaVinci;
import cn.hadcn.davinci.http.OnDaVinciRequestListener;
import cn.hadcn.davinci.log.VinciLog;

/**
 * Jocket
 * Created by 90Chris on 2016/5/25.
 */
public class Jocket {
    private final static String PING_PACK = "{\"type\":\"ping\"}";
    private String mSessionId;
    private boolean isClosed = false;
    private boolean isFirstHandshake = true;
    private OnJocketListener mJocketListener = null;
    private int mPingTimeout;
    private int mPingInterval;
    private Map<String, String> header;
    private String mBaseUrl;
    private String pollingUrl;
    private static final int SEND_PING = 0;
    private static final int DISCONNECT = 1;
    private static final int NOOP_POLL = 2;
    private final Handler mHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case SEND_PING:
                    sendPing();
                    break;
                case DISCONNECT:
                    disconnect(JocketCode.JOCKET_FAILED, "no pong", true);
                    break;
                case NOOP_POLL:
                    poll();
                    break;

            }
        }
    };

    public Jocket(String baseUrl) {
        mBaseUrl = baseUrl;
        header = new HashMap<>();
        header.put("Referer", "Android");
        DaVinci.with().addThreadPool("polling", 1);
    }

    /**
     * connect
     *
     * @param path     path, like /chat/simple
     * @param map      args
     * @param listener Jocket listener
     */
    public void connect(String path, Map<String, Object> map, OnJocketListener listener) {
        mJocketListener = listener;
        String prepareUrl = "http://" + mBaseUrl + path + ".jocket";
        VinciLog.d("[jocket] prepare url: " + prepareUrl + " map: " + map);
        DaVinci.with().getHttpRequest()
                .headers(header)
                .maxRetries(0)
                .timeOut(10000)
                .shouldCache(false)
                .doGet(prepareUrl, map, new PrepareListener());
    }

    private class PrepareListener implements OnDaVinciRequestListener {

        @Override
        public void onDaVinciRequestSuccess(String s) {
            VinciLog.e("[jocket] prepare received = " + s);
            try {
                JSONObject jsonObject = new JSONObject(s);
                mSessionId = jsonObject.getString("sessionId");
                mPingTimeout = jsonObject.getInt("pingTimeout");
                mPingInterval = jsonObject.getInt("pingInterval");
//                boolean upgrade = jsonObject.getBoolean("upgrade");
                pollingUrl = "http://" + mBaseUrl + "/jocket?s=" + mSessionId;
                sendPing();
                poll();
            } catch (JSONException e) {
                VinciLog.e("[jocket] prepare json parse", e);
                disconnect(JocketCode.SYSTEM_ERR, "prepare json parse error", false);
            }
        }

        @Override
        public void onDaVinciRequestFailed(int i, String s) {
            VinciLog.e("[jocket] prepare failed, reason = " + s + ",isClosed: " + isClosed);
            disconnect(JocketCode.JOCKET_FAILED, "prepare failed", false);
        }
    }

    private void sendPing() {
        if (isClosed) {
            return;
        }
        UUID uuid = UUID.randomUUID();
        String url = pollingUrl + "&uuid=" + uuid;
        VinciLog.d("[jocket][" + mSessionId + "] do ping, url = " + url);
        DaVinci.with()
                .getHttpRequest()
                .timeOut(mPingTimeout)
                .headers(header)
                .maxRetries(0)
                .shouldCache(false)
                .doPost(url, PING_PACK, new OnDaVinciRequestListener() {
                    @Override
                    public void onDaVinciRequestSuccess(String s) {
                        VinciLog.e("[jocket][" + mSessionId + "] ping success " + s);
                        if (TextUtils.isEmpty(s)) {
                            disconnect(JocketCode.JOCKET_FAILED, "ping failed", false);
                            VinciLog.e("[jocket][" + mSessionId + "] ping response is empty");
                        }
                    }

                    @Override
                    public void onDaVinciRequestFailed(int i, String s) {
                        VinciLog.e("[jocket][" + mSessionId + "] ping failed, " + s);
                        disconnect(JocketCode.JOCKET_FAILED, "ping failed", true);
                    }
                });
        mHandler.sendEmptyMessageDelayed(DISCONNECT, mPingTimeout);
    }

    private void poll() {
        if (isClosed) {
            return;
        }
        UUID uuid = UUID.randomUUID();
        String url = pollingUrl + "&uuid=" + uuid;
        VinciLog.d("[jocket][" + mSessionId + "] do poll, url = " + url);
        DaVinci.with().tag("polling")
                .getHttpRequest()
                .timeOut(60000)
                .maxRetries(0)
                .headers(header)
                .shouldCache(false)
                .doGet(url, null, new PollingListener());
    }

    private class PollingListener implements OnDaVinciRequestListener {

        @Override
        public void onDaVinciRequestSuccess(String s) {
            if (isClosed) {
                return;
            }
            try {
                VinciLog.d("[jocket][" + mSessionId + "] poll success result:　" + s);
                JSONObject jsonObject = new JSONObject(s);
                String type = jsonObject.getString("type");
                switch (type) {
                    case "close":
                        /**
                         * {"message":"invalid token","code":4901}
                         */
                        String data = jsonObject.getString("data");
                        JSONObject dataJson = new JSONObject(data);
                        disconnect(JocketCode.ofValue(dataJson.getInt("code")), dataJson.getString("message"), false);
                        VinciLog.d("[jocket][" + mSessionId + "] close received");
                        break;
                    case "pong":
                        mHandler.removeMessages(DISCONNECT);
                        poll();
                        VinciLog.i("[jocket][" + mSessionId + "] pong received");
                        if (isFirstHandshake) {
                            VinciLog.i("[jocket][" + mSessionId + "] connected");
                            isFirstHandshake = false;
                            mJocketListener.onConnected();
                        }
                        mHandler.sendEmptyMessageDelayed(SEND_PING, mPingInterval);
                        break;
                    case "noop":

                        mHandler.sendEmptyMessageDelayed(NOOP_POLL, 5000);
                        VinciLog.d("[jocket][" + mSessionId + "] noop received");
                        break;
                    case "message":
                        poll();
                        mJocketListener.onReceive(s);
                        break;
                    default:
                        VinciLog.e("[jocket][" + mSessionId + "] invalid packet type:" + type);
                        break;
                }
            } catch (JSONException e) {
                VinciLog.e("[jocket][" + mSessionId + "] poll parse json error", e);
                disconnect(JocketCode.SYSTEM_ERR, "poll json parse error", true);
            }
        }

        @Override
        public void onDaVinciRequestFailed(int i, String s) {
            VinciLog.e("[jocket][" + mSessionId + "] poll failed, failed reason = " + s);
            disconnect(JocketCode.JOCKET_FAILED, "poll failed", true);
        }
    }

    public void close() {
        disconnect(JocketCode.JOCKET_NORMAL, "closed by user", true);
    }

    private void disconnect(JocketCode code, String reason, boolean isSendClose) {
        VinciLog.e("[jocket][" + mSessionId + "] disconnect, code = " + code.getValue() + ", reason = " + reason + ", isClosed = " + isClosed);
        if (isClosed) {
            return;
        }
        isClosed = true;
        mHandler.removeCallbacksAndMessages(null);
        mJocketListener.onDisconnect(code, reason);
        if (isSendClose) {
            JSONObject jsonObject = new JSONObject();
            try {
                JSONObject data = new JSONObject();
                data.put("code", code.getValue());
                data.put("reason", reason);
                jsonObject.put("type", "close");
                jsonObject.put("data", data.toString());
            } catch (JSONException e) {
                VinciLog.e("json assembler failed");
            }
            UUID uuid = UUID.randomUUID();
            String url = pollingUrl + "&uuid=" + uuid;
            DaVinci.with()
                    .getHttpRequest()
                    .headers(header)
                    .shouldCache(false)
                    .doPost(url, jsonObject, new OnDaVinciRequestListener() {
                        @Override
                        public void onDaVinciRequestSuccess(String s) {
                            VinciLog.e("[jocket][" + mSessionId + "] close success, " + s);
                        }

                        @Override
                        public void onDaVinciRequestFailed(int i, String s) {
                            VinciLog.e("[jocket][" + mSessionId + "] close failed");
                        }
                    });
        }
    }
}