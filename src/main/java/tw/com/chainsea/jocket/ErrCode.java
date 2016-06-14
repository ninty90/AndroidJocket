package tw.com.chainsea.jocket;

/**
 * error code
 * Created by 90Chris on 2016/6/14.
 */
public enum ErrCode {
    UNDEF(-1),
    SYSTEM_ERR(0),
    INVALID_TOKEN(4901);

    private int mValue;
    ErrCode(int value){
        mValue = value;
    }

    public final int getValue() {
        return mValue;
    }

    public static ErrCode ofValue(int value) {
        for ( ErrCode code : values()) {
            if ( code.getValue() == value ) {
                return code;
            }
        }
        return UNDEF;
    }
}
