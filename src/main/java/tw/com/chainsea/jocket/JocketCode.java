package tw.com.chainsea.jocket;

/**
 * error code
 * Created by 90Chris on 2016/6/14.
 */
public enum JocketCode {
    UNDEF(-1),
    SYSTEM_ERR(0),
    INVALID_TOKEN(4901);

    private int mValue;
    JocketCode(int value){
        mValue = value;
    }

    public final int getValue() {
        return mValue;
    }

    public static JocketCode ofValue(int value) {
        for ( JocketCode code : values()) {
            if ( code.getValue() == value ) {
                return code;
            }
        }
        return UNDEF;
    }
}
