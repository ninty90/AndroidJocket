package tw.com.chainsea.jocket;

/**
 * listener of jocket
 * Created by 90Chris on 2016/5/25.
 */
public interface OnJocketListener {
    void onDisconnect();
    void onConnected();
    void onReceive(String msg);
}
