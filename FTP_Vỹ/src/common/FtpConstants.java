package common;

/**
 * Định nghĩa các hằng số giao thức FTP chuẩn RFC 959
 * Phục vụ đồ án Lập trình mạng Java - Đại học Sài Gòn (SGU)
 */
public final class FtpConstants {
    private FtpConstants() {}

    // Ký tự ngắt dòng chuẩn mạng bắt buộc (CRLF)
    public static final String CRLF = "\r\n";

    // Cổng FTP mặc định
    public static final int DEFAULT_FTP_PORT = 21;
    public static final int FALLBACK_FTP_PORT = 2121;

    // Buffer size cho truyền file nhị phân (8 KB)
    public static final int BUFFER_SIZE = 8192;

    // Timeout socket điều khiển (30 giây)
    public static final int SOCKET_TIMEOUT_MS = 30000;
    // Timeout cho kết nối dữ liệu (Data Socket) - 60 giây để tải file lớn an toàn
    public static final int DATA_SOCKET_TIMEOUT_MS = 60000;

    // FTP Reply Codes
    public static final int CODE_150_OPENING_DATA_CONNECTION = 150;
    public static final int CODE_200_COMMAND_OK = 200;
    public static final int CODE_213_FILE_STATUS = 213;
    public static final int CODE_215_SYSTEM_TYPE = 215;
    public static final int CODE_220_SERVICE_READY = 220;
    public static final int CODE_221_GOODBYE = 221;
    public static final int CODE_226_TRANSFER_COMPLETE = 226;
    public static final int CODE_227_ENTERING_PASSIVE_MODE = 227;
    public static final int CODE_230_LOGIN_SUCCESS = 230;
    public static final int CODE_250_FILE_ACTION_OK = 250;
    public static final int CODE_257_PATHNAME_CREATED = 257;
    public static final int CODE_331_PASSWORD_REQUIRED = 331;
    public static final int CODE_350_FILE_ACTION_PENDING = 350;
    public static final int CODE_421_SERVICE_UNAVAILABLE = 421;
    public static final int CODE_425_CANT_OPEN_DATA_CONNECTION = 425;
    public static final int CODE_500_SYNTAX_ERROR = 500;
    public static final int CODE_502_COMMAND_NOT_IMPLEMENTED = 502;
    public static final int CODE_530_LOGIN_INCORRECT = 530;
    public static final int CODE_550_FILE_UNAVAILABLE = 550;
}
