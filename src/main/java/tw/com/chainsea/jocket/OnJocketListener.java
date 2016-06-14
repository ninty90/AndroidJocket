package tw.com.chainsea.jocket;

/**
 * listener of jocket
 * Created by 90Chris on 2016/5/25.
 */
public interface OnJocketListener {
    void onDisconnect(ErrCode code, String reason);
    void onConnected();
    void onReceive(String msg);
}
