# Mail Client Java (Socket + JavaMail) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Xây dựng Mail Client có giao diện Java Swing, gửi và nhận mail qua Gmail bằng hai cách (Java Socket thuần và JavaMail `javax.mail`), nhận bằng cả POP3 và IMAP, có HTML + file đính kèm + log + retry (yêu cầu cuối bài, mục 10 của `MAIL-Java.md`).

**Architecture:** Một interface `MailService` (gửi + nhận) có hai bản cài đặt: `SocketMailService` (tự viết SMTP/POP3/IMAP trên `Socket`/`SSLSocket`, bám mục 4, 5, 6, 7, 8) và `JavaMailService` (bám mục 10). Cả hai chạy qua `Retrier` (exponential backoff, chỉ retry lỗi 4xx/lỗi mạng, theo mục 9) và ghi log bằng `MailLog`. GUI Swing chỉ gọi `MailService` trong `SwingWorker`, không chứa logic giao thức.

**Tech Stack:** Java 8+ (cú pháp Java 8, chạy được trên JDK mới hơn), `javax.mail-1.6.2.jar`, activation jar, JUnit 5 (`junit-platform-console-standalone`), build bằng `javac` + `lib/*.jar` như trong file.

## Global Constraints

- Cấu trúc thư mục theo mục 10: `EmailClientApp/` gồm `src/`, `lib/`, `attachments/`, `bin/` (thêm `test/`, `logs/`).
- Lệnh biên dịch/chạy theo mục 10 (Windows dùng `;`, Linux/macOS đổi thành `:`): `javac -cp ".;lib/*" ... -d bin/` và `java -cp "bin;lib/*" EmailClientApp`.
- Thư viện đúng như file: `javax.mail-1.6.2.jar` và `activation` (file ghi `activation-1.2.2.jar`; trên Maven Central bản 1.2.2 có tên `jakarta.activation-1.2.2.jar`, dùng package `javax.activation`, đổi tên hay giữ nguyên đều chạy).
- Tất cả dòng lệnh SMTP/POP3/IMAP kết thúc bằng CRLF (`\r\n`) (mục 4).
- Kết thúc DATA bằng `\r\n.\r\n`; mọi dòng bắt đầu bằng `.` phải dot-stuffing (mục 5).
- Base64 của file đính kèm chia dòng 76 ký tự (mục 7).
- Xác thực `AUTH LOGIN` gửi username và password dạng Base64 (mục 5).
- Cổng Gmail: SMTP `smtp.gmail.com` 465 (SSL ngay từ đầu) hoặc 587 (STARTTLS); POP3 `pop.gmail.com` 995; IMAP `imap.gmail.com` 993 (mục 3, 6).
- Mã trả về: 2xx thành công, 3xx trung gian, 4xx lỗi tạm thời thì **retry**, 5xx lỗi vĩnh viễn thì **không retry** (mục 9).
- Tài khoản dùng **App Password** của Gmail, nhập qua GUI, không hard-code, không ghi vào log (mục 1).
- Nội dung UTF-8, hỗ trợ tiếng Việt (mục 1, 7).
- Không lưu mật khẩu ra đĩa. Không đưa địa chỉ/tiêu đề chứa CR/LF vào header (chống header injection).

## Lưu ý: code mẫu trong file có lỗi, plan này sửa

| # | Code mẫu trong file | Cách sửa trong plan |
|---|---|---|
| 1 | `SmtpMailClient` cổng 587 gửi `STARTTLS` nhưng **không nâng cấp socket** lên TLS, vẫn gửi mật khẩu dạng thường | `LineConnection.startTls()` bọc socket bằng `SSLSocket` (Task 8) |
| 2 | `sendCommand` chỉ đọc 1 dòng, nhưng `EHLO` trả nhiều dòng (`250-...`) nên lệch response | Đọc đến dòng có ký tự thứ 4 là dấu cách (Task 8) |
| 3 | `Subject` chứa tiếng Việt bị đưa thẳng vào header | Mã hóa RFC 2047 `=?UTF-8?B?...?=` (Task 7) |
| 4 | `MimeAttachmentHelper` gán cứng `application/pdf` | Dò bằng `Files.probeContentType`, mặc định `application/octet-stream` (Task 7) |
| 5 | `Pop3Client` đọc bằng `US_ASCII` nên mất ký tự 8-bit, không bỏ dot-stuffing | Đọc `ISO_8859_1` giữ nguyên byte, bỏ một dấu `.` đầu dòng `..` (Task 9) |

## Ngoài phạm vi (non-goals)

- Đính kèm **thư mục** (MIME lồng nhau) có trong mục lục mục 7 nhưng không có trong mục 10. Plan chỉ hỗ trợ nhiều file, chọn thư mục sẽ báo lỗi rõ ràng.
- POP3 `DELE`, quản lý thư mục IMAP, OAuth2, lưu tài khoản ra đĩa, hiển thị HTML trong thư nhận (hiện nguồn HTML dạng text cho an toàn).

## File Structure

```
EmailClientApp/
├── src/
│   ├── EmailClientApp.java        # main (mục 10)
│   ├── MainFrame.java             # GUI Swing
│   ├── MailService.java           # interface gửi + nhận
│   ├── Engine.java                # SOCKET | JAVAMAIL
│   ├── ReceiveProtocol.java       # POP3 | IMAP
│   ├── Security.java              # NONE | SSL | STARTTLS
│   ├── AccountConfig.java         # host/port/tài khoản (mặc định Gmail)
│   ├── OutgoingMail.java          # thư gửi + validate
│   ├── ReceivedMail.java          # thư nhận (đã parse)
│   ├── MailException.java         # lỗi + cờ transient
│   ├── ReplyCode.java             # phân loại 2xx/3xx/4xx/5xx
│   ├── Retrier.java               # exponential backoff
│   ├── MailLog.java               # log file + listener cho GUI
│   ├── MailParser.java            # byte[]/Message -> ReceivedMail
│   ├── MimeBuilder.java           # dựng thư MIME cho đường Socket
│   ├── LineConnection.java        # Socket/SSLSocket + đọc ghi dòng
│   ├── SmtpSocketClient.java
│   ├── Pop3SocketClient.java
│   ├── ImapSocketClient.java
│   ├── SocketMailService.java
│   ├── JavaMailErrors.java        # phân loại lỗi javax.mail
│   ├── JavaMailService.java
│   └── MailServiceFactory.java
├── test/                          # JUnit 5, mỗi class một file *Test.java
├── lib/                           # javax.mail, activation, junit (không commit jar)
├── attachments/                   # file mẫu để thử gửi
├── bin/                           # output biên dịch (gitignore)
├── logs/                          # mail-client.log (gitignore)
├── setup-libs.bat  build.bat  test.bat  run.bat
└── .gitignore
```

Tất cả file ở default package (giống file `EmailClientApp.java` trong mục 10), nên biên dịch bằng `src/*.java test/*.java`.

---

### Task 1: Khung dự án, thư viện, script build/test

**Files:**
- Create: `.gitignore`, `setup-libs.bat`, `build.bat`, `test.bat`, `run.bat`
- Create: `attachments/report.txt`
- Test: `test/SmokeTest.java`

**Interfaces:**
- Consumes: không
- Produces: lệnh `build.bat`, `test.bat [TênClassTest]`, `run.bat` dùng cho mọi task sau.

- [ ] **Step 1: Tạo thư mục, git và script**

```bash
mkdir EmailClientApp && cd EmailClientApp
mkdir src test lib attachments bin logs
git init
```

`.gitignore`:

```
bin/
logs/
lib/*.jar
```

`setup-libs.bat` (cần Windows 10+ có sẵn `curl`):

```bat
@echo off
curl -L -o lib\javax.mail-1.6.2.jar https://repo1.maven.org/maven2/com/sun/mail/javax.mail/1.6.2/javax.mail-1.6.2.jar
curl -L -o lib\jakarta.activation-1.2.2.jar https://repo1.maven.org/maven2/com/sun/activation/jakarta.activation/1.2.2/jakarta.activation-1.2.2.jar
curl -L -o lib\junit-platform-console-standalone-1.10.2.jar https://repo1.maven.org/maven2/org/junit/platform/junit-platform-console-standalone/1.10.2/junit-platform-console-standalone-1.10.2.jar
dir lib
```

`build.bat`:

```bat
@echo off
if not exist bin mkdir bin
javac -encoding UTF-8 -cp "lib/*" -d bin src/*.java test/*.java
```

`test.bat` (không tham số: chạy tất cả; có tham số: chạy một class):

```bat
@echo off
call build.bat
if errorlevel 1 exit /b 1
if "%~1"=="" (
  java -cp "bin;lib/*" org.junit.platform.console.ConsoleLauncher execute --scan-class-path bin --disable-banner
) else (
  java -cp "bin;lib/*" org.junit.platform.console.ConsoleLauncher execute --select-class %~1 --disable-banner
)
```

`run.bat`:

```bat
@echo off
call build.bat
if errorlevel 1 exit /b 1
java -cp "bin;lib/*" EmailClientApp
```

`attachments/report.txt`:

```
Bao cao thu nghiem - Mail Client Java
```

- [ ] **Step 2: Tải thư viện**

Run: `setup-libs.bat`
Expected: `dir lib` liệt kê đủ 3 file jar, mỗi file lớn hơn 0 byte. (Linux/macOS: tải 3 URL trên bằng `curl -L -o`.)

- [ ] **Step 3: Viết smoke test**

`test/SmokeTest.java`:

```java
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Properties;
import javax.mail.Session;
import org.junit.jupiter.api.Test;

public class SmokeTest {
    @Test
    void javaMailIsOnClasspath() {
        assertNotNull(Session.getInstance(new Properties()));
    }
}
```

- [ ] **Step 4: Chạy test**

Run: `test.bat SmokeTest`
Expected: PASS, dòng tổng kết có `1 tests successful`, `0 tests failed`. Nếu `ClassNotFoundException: javax.mail.Session` thì thiếu jar trong `lib/`.

- [ ] **Step 5: Commit**

```bash
git add .gitignore setup-libs.bat build.bat test.bat run.bat attachments test
git commit -m "chore: project skeleton, build scripts, smoke test"
```

---

### Task 2: `MailException` và `ReplyCode` (mục 9)

**Files:**
- Create: `src/MailException.java`, `src/ReplyCode.java`
- Test: `test/ReplyCodeTest.java`

**Interfaces:**
- Consumes: không
- Produces:
  - `class MailException extends Exception` — `MailException(String message, int replyCode, boolean transientError)`, `MailException(String message, int replyCode, boolean transientError, Throwable cause)`, `int getReplyCode()`, `boolean isTransientError()`, `static MailException fromIo(IOException e)`
  - `final class ReplyCode` — `static int parse(String line)` (trả `-1` nếu không hợp lệ), `isSuccess(int)`, `isIntermediate(int)`, `isTransient(int)`, `isPermanent(int)`, `static boolean isLastLine(String line)`

- [ ] **Step 1: Write the failing test**

`test/ReplyCodeTest.java`:

```java
import static org.junit.jupiter.api.Assertions.*;

import java.net.SocketTimeoutException;
import javax.net.ssl.SSLException;
import org.junit.jupiter.api.Test;

public class ReplyCodeTest {
    @Test
    void parsesThreeDigitCode() {
        assertEquals(250, ReplyCode.parse("250 OK"));
        assertEquals(334, ReplyCode.parse("334 VXNlcm5hbWU6"));
    }

    @Test
    void invalidLineGivesMinusOne() {
        assertEquals(-1, ReplyCode.parse("abc"));
        assertEquals(-1, ReplyCode.parse("+OK"));
        assertEquals(-1, ReplyCode.parse("25"));
        assertEquals(-1, ReplyCode.parse(null));
    }

    @Test
    void classifiesCodesLikeSection9() {
        assertTrue(ReplyCode.isSuccess(235));
        assertTrue(ReplyCode.isIntermediate(354));
        assertTrue(ReplyCode.isTransient(421));
        assertTrue(ReplyCode.isTransient(450));
        assertTrue(ReplyCode.isPermanent(535));
        assertTrue(ReplyCode.isPermanent(550));
        assertFalse(ReplyCode.isTransient(550));
        assertFalse(ReplyCode.isPermanent(421));
    }

    @Test
    void detectsLastLineOfMultilineReply() {
        assertFalse(ReplyCode.isLastLine("250-smtp.gmail.com"));
        assertTrue(ReplyCode.isLastLine("250 AUTH LOGIN"));
        assertTrue(ReplyCode.isLastLine("250"));
    }

    @Test
    void ioErrorsAreTransientExceptSsl() {
        assertTrue(MailException.fromIo(new SocketTimeoutException("timeout")).isTransientError());
        assertFalse(MailException.fromIo(new SSLException("bad cert")).isTransientError());
    }

    @Test
    void exceptionKeepsCodeAndFlag() {
        MailException e = new MailException("busy", 450, true);
        assertEquals(450, e.getReplyCode());
        assertTrue(e.isTransientError());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `test.bat ReplyCodeTest`
Expected: FAIL, lỗi biên dịch `cannot find symbol ... class ReplyCode`.

- [ ] **Step 3: Write minimal implementation**

`src/MailException.java`:

```java
import java.io.IOException;
import javax.net.ssl.SSLException;

public class MailException extends Exception {
    private final int replyCode;
    private final boolean transientError;

    public MailException(String message, int replyCode, boolean transientError) {
        this(message, replyCode, transientError, null);
    }

    public MailException(String message, int replyCode, boolean transientError, Throwable cause) {
        super(message, cause);
        this.replyCode = replyCode;
        this.transientError = transientError;
    }

    public int getReplyCode() {
        return replyCode;
    }

    /** true = lỗi tạm thời (4xx, mạng) nên thử lại; false = lỗi vĩnh viễn (5xx, sai mật khẩu). */
    public boolean isTransientError() {
        return transientError;
    }

    /** Lỗi I/O là tạm thời, trừ lỗi SSL (chứng chỉ sai, handshake hỏng) thì thử lại vô ích. */
    public static MailException fromIo(IOException e) {
        boolean permanent = e instanceof SSLException;
        return new MailException("Lỗi mạng: " + e.getMessage(), -1, !permanent, e);
    }
}
```

`src/ReplyCode.java`:

```java
public final class ReplyCode {
    private ReplyCode() {
    }

    public static int parse(String line) {
        if (line == null || line.length() < 3) {
            return -1;
        }
        try {
            return Integer.parseInt(line.substring(0, 3));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    public static boolean isSuccess(int code) {
        return code >= 200 && code < 300;
    }

    public static boolean isIntermediate(int code) {
        return code >= 300 && code < 400;
    }

    public static boolean isTransient(int code) {
        return code >= 400 && code < 500;
    }

    public static boolean isPermanent(int code) {
        return code >= 500 && code < 600;
    }

    /** Dòng cuối của reply SMTP nhiều dòng: "250 text" (dấu cách ở vị trí 4) hoặc chỉ "250". */
    public static boolean isLastLine(String line) {
        return line.length() == 3 || (line.length() >= 4 && line.charAt(3) == ' ');
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `test.bat ReplyCodeTest`
Expected: PASS, `6 tests successful`.

- [ ] **Step 5: Commit**

```bash
git add src/MailException.java src/ReplyCode.java test/ReplyCodeTest.java
git commit -m "feat: MailException and ReplyCode classification"
```

---

### Task 3: `Retrier` với exponential backoff (mục 9)

**Files:**
- Create: `src/Retrier.java`, `src/MailLog.java` (bản tối thiểu để Retrier dùng, hoàn thiện ở Task 4)
- Test: `test/RetrierTest.java`

**Interfaces:**
- Consumes: `MailException.isTransientError()`
- Produces:
  - `class Retrier` — `Retrier(int maxAttempts, long baseDelayMs, Retrier.Sleeper sleeper, MailLog log)`, `static Retrier standard(MailLog log)` (3 lần, 1000 ms, nhân đôi), `<T> T run(String what, Retrier.Action<T> action) throws MailException`
  - `interface Retrier.Action<T> { T run() throws MailException; }`, `interface Retrier.Sleeper { void sleep(long ms) throws InterruptedException; }`
  - `class MailLog` — `MailLog(Path file)` (null = chỉ listener), `info/warn/error(String)`, `addListener(MailLog.Listener)`. Phần này viết đầy đủ luôn ở Task 4; Task 3 chỉ cần constructor và `warn`.

- [ ] **Step 1: Write the failing test**

`test/RetrierTest.java`:

```java
import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

public class RetrierTest {
    private final List<Long> sleeps = new ArrayList<>();

    private Retrier retrier() {
        return new Retrier(3, 1000, sleeps::add, new MailLog(null));
    }

    @Test
    void retriesTransientErrorsWithDoublingDelay() throws Exception {
        int[] calls = {0};
        String result = retrier().run("test", () -> {
            calls[0]++;
            if (calls[0] < 3) {
                throw new MailException("busy", 450, true);
            }
            return "ok";
        });
        assertEquals("ok", result);
        assertEquals(3, calls[0]);
        assertEquals(Arrays.asList(1000L, 2000L), sleeps);
    }

    @Test
    void doesNotRetryPermanentErrors() {
        int[] calls = {0};
        MailException e = assertThrows(MailException.class, () -> retrier().run("test", () -> {
            calls[0]++;
            throw new MailException("auth failed", 535, false);
        }));
        assertEquals(535, e.getReplyCode());
        assertEquals(1, calls[0]);
        assertTrue(sleeps.isEmpty());
    }

    @Test
    void givesUpAfterMaxAttempts() {
        int[] calls = {0};
        assertThrows(MailException.class, () -> retrier().run("test", () -> {
            calls[0]++;
            throw new MailException("down", 421, true);
        }));
        assertEquals(3, calls[0]);
        assertEquals(Arrays.asList(1000L, 2000L), sleeps);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `test.bat RetrierTest`
Expected: FAIL, `cannot find symbol ... class Retrier`.

- [ ] **Step 3: Write minimal implementation**

`src/MailLog.java` (bản đầy đủ, Task 4 chỉ thêm test):

```java
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class MailLog {
    public interface Listener {
        void onLine(String line);
    }

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final Path file;
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();

    /** file = null thì chỉ báo cho listener, không ghi đĩa (dùng trong test). */
    public MailLog(Path file) {
        this.file = file;
    }

    public void addListener(Listener listener) {
        listeners.add(listener);
    }

    public void info(String message) {
        write("INFO", message);
    }

    public void warn(String message) {
        write("WARN", message);
    }

    public void error(String message) {
        write("ERROR", message);
    }

    private synchronized void write(String level, String message) {
        String line = LocalDateTime.now().format(FMT) + " [" + level + "] " + message;
        if (file != null) {
            try {
                Path dir = file.toAbsolutePath().getParent();
                if (dir != null) {
                    Files.createDirectories(dir);
                }
                Files.write(file, (line + System.lineSeparator()).getBytes(StandardCharsets.UTF_8),
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException e) {
                System.err.println("Không ghi được log: " + e.getMessage());
            }
        }
        for (Listener listener : listeners) {
            listener.onLine(line);
        }
    }
}
```

`src/Retrier.java`:

```java
public class Retrier {
    public interface Action<T> {
        T run() throws MailException;
    }

    public interface Sleeper {
        void sleep(long ms) throws InterruptedException;
    }

    private final int maxAttempts;
    private final long baseDelayMs;
    private final Sleeper sleeper;
    private final MailLog log;

    public Retrier(int maxAttempts, long baseDelayMs, Sleeper sleeper, MailLog log) {
        this.maxAttempts = maxAttempts;
        this.baseDelayMs = baseDelayMs;
        this.sleeper = sleeper;
        this.log = log;
    }

    /** 3 lần thử, chờ 1s rồi 2s (nhân đôi mỗi lần). */
    public static Retrier standard(MailLog log) {
        return new Retrier(3, 1000, Thread::sleep, log);
    }

    public <T> T run(String what, Action<T> action) throws MailException {
        int attempt = 1;
        while (true) {
            try {
                return action.run();
            } catch (MailException e) {
                if (!e.isTransientError() || attempt >= maxAttempts) {
                    throw e;
                }
                long delay = baseDelayMs * (1L << (attempt - 1));
                log.warn(what + ": lỗi tạm thời (lần " + attempt + "/" + maxAttempts
                        + "), thử lại sau " + delay + " ms: " + e.getMessage());
                try {
                    sleeper.sleep(delay);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw e;
                }
                attempt++;
            }
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `test.bat RetrierTest`
Expected: PASS, `3 tests successful`.

- [ ] **Step 5: Commit**

```bash
git add src/Retrier.java src/MailLog.java test/RetrierTest.java
git commit -m "feat: Retrier with exponential backoff, MailLog"
```

---

### Task 4: Kiểm thử `MailLog` (ghi file + listener)

**Files:**
- Test: `test/MailLogTest.java`
- (Implementation đã có ở Task 3)

**Interfaces:**
- Consumes: `MailLog(Path)`, `addListener`, `info`, `warn`
- Produces: log ghi vào `logs/mail-client.log` với định dạng `yyyy-MM-dd HH:mm:ss [LEVEL] message`.

- [ ] **Step 1: Write the test**

`test/MailLogTest.java`:

```java
import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class MailLogTest {
    @Test
    void writesLinesToFileAndNotifiesListeners(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("logs").resolve("mail.log");
        MailLog log = new MailLog(file);
        List<String> seen = new ArrayList<>();
        log.addListener(seen::add);

        log.info("xin chào");
        log.warn("cảnh báo");

        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        assertEquals(2, lines.size());
        assertTrue(lines.get(0).contains("[INFO] xin chào"));
        assertTrue(lines.get(1).contains("[WARN] cảnh báo"));
        assertEquals(2, seen.size());
    }

    @Test
    void nullFileOnlyNotifiesListeners() {
        MailLog log = new MailLog(null);
        List<String> seen = new ArrayList<>();
        log.addListener(seen::add);
        log.error("lỗi");
        assertEquals(1, seen.size());
        assertTrue(seen.get(0).contains("[ERROR] lỗi"));
    }
}
```

- [ ] **Step 2: Run test**

Run: `test.bat MailLogTest`
Expected: PASS, `2 tests successful` (implementation đã viết ở Task 3 nên test này xanh ngay; đây là test khóa hành vi). Nếu FAIL thì sửa `MailLog.write` cho tới khi PASS.

- [ ] **Step 3: Commit**

```bash
git add test/MailLogTest.java
git commit -m "test: MailLog file output and listeners"
```

---

### Task 5: Model, cấu hình, interface dùng chung

**Files:**
- Create: `src/Security.java`, `src/ReceiveProtocol.java`, `src/Engine.java`, `src/AccountConfig.java`, `src/OutgoingMail.java`, `src/ReceivedMail.java`, `src/MailService.java`
- Test: `test/OutgoingMailTest.java`, `test/AccountConfigTest.java`

**Interfaces:**
- Consumes: không
- Produces:
  - `enum Security { NONE, SSL, STARTTLS }`, `enum ReceiveProtocol { POP3, IMAP }`, `enum Engine { SOCKET, JAVAMAIL }`
  - `class AccountConfig` — public field: `String username, password`; `String smtpHost; int smtpPort; Security smtpSecurity`; `String pop3Host; int pop3Port; Security pop3Security`; `String imapHost; int imapPort; Security imapSecurity`; `AccountConfig(String username, String password)` (mặc định Gmail SSL, ném `IllegalArgumentException` nếu địa chỉ/mật khẩu không hợp lệ); `static AccountConfig gmail(String username, String password)`
  - `final class OutgoingMail` — public final: `List<String> to; String subject; String body; boolean html; List<File> attachments`; `OutgoingMail(List<String> to, String subject, String body, boolean html, List<File> attachments)`; `static List<String> splitRecipients(String text)`; `static boolean isValidAddress(String s)`
  - `final class ReceivedMail` — public final: `String id, from, subject, date, body; boolean html; List<String> attachmentNames`; constructor theo đúng thứ tự `(id, from, subject, date, body, html, attachmentNames)`
  - `interface MailService { void send(OutgoingMail mail) throws MailException; List<ReceivedMail> fetchInbox(ReceiveProtocol protocol, int limit) throws MailException; }`

- [ ] **Step 1: Write the failing tests**

`test/OutgoingMailTest.java`:

```java
import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class OutgoingMailTest {
    private static final List<File> NO_FILES = Collections.emptyList();

    @Test
    void splitsRecipientsByCommaAndSemicolon() {
        assertEquals(Arrays.asList("a@x.com", "b@y.com", "c@z.com"),
                OutgoingMail.splitRecipients("a@x.com, b@y.com; c@z.com ,"));
        assertTrue(OutgoingMail.splitRecipients(null).isEmpty());
    }

    @Test
    void requiresAtLeastOneValidRecipient() {
        assertThrows(IllegalArgumentException.class,
                () -> new OutgoingMail(Collections.<String>emptyList(), "s", "b", false, NO_FILES));
        assertThrows(IllegalArgumentException.class,
                () -> new OutgoingMail(Arrays.asList("khong-hop-le"), "s", "b", false, NO_FILES));
    }

    @Test
    void rejectsHeaderInjectionInAddress() {
        assertThrows(IllegalArgumentException.class, () -> new OutgoingMail(
                Arrays.asList("a@x.com\r\nBcc: evil@x.com"), "s", "b", false, NO_FILES));
    }

    @Test
    void stripsNewlinesFromSubjectAndDefaultsNulls() {
        OutgoingMail m = new OutgoingMail(Arrays.asList("a@x.com"), "dòng 1\r\nBcc: e@x.com", null, false, null);
        assertEquals("dòng 1 Bcc: e@x.com", m.subject);
        assertEquals("", m.body);
        assertTrue(m.attachments.isEmpty());
    }

    @Test
    void rejectsMissingFileAndDirectoryAttachments(@TempDir Path dir) throws Exception {
        File missing = dir.resolve("none.pdf").toFile();
        assertThrows(IllegalArgumentException.class, () -> new OutgoingMail(
                Arrays.asList("a@x.com"), "s", "b", false, Arrays.asList(missing)));
        assertThrows(IllegalArgumentException.class, () -> new OutgoingMail(
                Arrays.asList("a@x.com"), "s", "b", false, Arrays.asList(dir.toFile())));

        File real = Files.write(dir.resolve("a.txt"), "x".getBytes()).toFile();
        OutgoingMail ok = new OutgoingMail(Arrays.asList("a@x.com"), "s", "b", true, Arrays.asList(real));
        assertEquals(1, ok.attachments.size());
        assertTrue(ok.html);
    }
}
```

`test/AccountConfigTest.java`:

```java
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

public class AccountConfigTest {
    @Test
    void defaultsAreGmail() {
        AccountConfig c = AccountConfig.gmail("me@gmail.com", "abcdabcdabcdabcd");
        assertEquals("smtp.gmail.com", c.smtpHost);
        assertEquals(465, c.smtpPort);
        assertEquals(Security.SSL, c.smtpSecurity);
        assertEquals("pop.gmail.com", c.pop3Host);
        assertEquals(995, c.pop3Port);
        assertEquals("imap.gmail.com", c.imapHost);
        assertEquals(993, c.imapPort);
    }

    @Test
    void rejectsBadUsernameOrPassword() {
        assertThrows(IllegalArgumentException.class, () -> new AccountConfig("khong-phai-email", "x"));
        assertThrows(IllegalArgumentException.class, () -> new AccountConfig("a@x.com", ""));
        assertThrows(IllegalArgumentException.class, () -> new AccountConfig("a@x.com", "pw\r\nQUIT"));
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `test.bat OutgoingMailTest` rồi `test.bat AccountConfigTest`
Expected: FAIL, `cannot find symbol` cho `OutgoingMail`, `AccountConfig`, `Security`.

- [ ] **Step 3: Write minimal implementation**

`src/Security.java`:

```java
public enum Security {
    NONE, SSL, STARTTLS
}
```

`src/ReceiveProtocol.java`:

```java
public enum ReceiveProtocol {
    POP3, IMAP
}
```

`src/Engine.java`:

```java
public enum Engine {
    SOCKET, JAVAMAIL
}
```

`src/AccountConfig.java`:

```java
public class AccountConfig {
    public final String username;
    public final String password;

    public String smtpHost = "smtp.gmail.com";
    public int smtpPort = 465;
    public Security smtpSecurity = Security.SSL;

    public String pop3Host = "pop.gmail.com";
    public int pop3Port = 995;
    public Security pop3Security = Security.SSL;

    public String imapHost = "imap.gmail.com";
    public int imapPort = 993;
    public Security imapSecurity = Security.SSL;

    public AccountConfig(String username, String password) {
        if (!OutgoingMail.isValidAddress(username)) {
            throw new IllegalArgumentException("Địa chỉ email không hợp lệ: " + username);
        }
        if (password == null || password.isEmpty() || password.contains("\r") || password.contains("\n")) {
            throw new IllegalArgumentException("Mật khẩu (App Password) không hợp lệ");
        }
        this.username = username;
        this.password = password;
    }

    public static AccountConfig gmail(String username, String password) {
        return new AccountConfig(username, password);
    }
}
```

`src/OutgoingMail.java`:

```java
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

public final class OutgoingMail {
    private static final Pattern ADDRESS = Pattern.compile("^[^\\s<>@,;]+@[^\\s<>@,;]+$");

    public final List<String> to;
    public final String subject;
    public final String body;
    public final boolean html;
    public final List<File> attachments;

    public OutgoingMail(List<String> to, String subject, String body, boolean html, List<File> attachments) {
        if (to == null || to.isEmpty()) {
            throw new IllegalArgumentException("Cần ít nhất một người nhận");
        }
        for (String address : to) {
            if (!isValidAddress(address)) {
                throw new IllegalArgumentException("Địa chỉ không hợp lệ: " + address);
            }
        }
        List<File> files = attachments == null ? new ArrayList<File>() : new ArrayList<>(attachments);
        for (File f : files) {
            if (f.isDirectory()) {
                throw new IllegalArgumentException("Chưa hỗ trợ đính kèm thư mục: " + f);
            }
            if (!f.isFile()) {
                throw new IllegalArgumentException("Không tìm thấy file: " + f);
            }
        }
        this.to = Collections.unmodifiableList(new ArrayList<>(to));
        this.subject = subject == null ? "" : subject.replaceAll("[\\r\\n]+", " ");
        this.body = body == null ? "" : body;
        this.html = html;
        this.attachments = Collections.unmodifiableList(files);
    }

    public static boolean isValidAddress(String s) {
        return s != null && ADDRESS.matcher(s).matches();
    }

    public static List<String> splitRecipients(String text) {
        List<String> out = new ArrayList<>();
        if (text == null) {
            return out;
        }
        for (String part : text.split("[,;]")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                out.add(trimmed);
            }
        }
        return out;
    }
}
```

`src/ReceivedMail.java`:

```java
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ReceivedMail {
    public final String id;
    public final String from;
    public final String subject;
    public final String date;
    public final String body;
    public final boolean html;
    public final List<String> attachmentNames;

    public ReceivedMail(String id, String from, String subject, String date, String body,
                        boolean html, List<String> attachmentNames) {
        this.id = id;
        this.from = from;
        this.subject = subject;
        this.date = date;
        this.body = body;
        this.html = html;
        this.attachmentNames = Collections.unmodifiableList(new ArrayList<>(attachmentNames));
    }
}
```

`src/MailService.java`:

```java
import java.util.List;

public interface MailService {
    void send(OutgoingMail mail) throws MailException;

    /** Trả về tối đa {@code limit} thư mới nhất, mới nhất đứng đầu. */
    List<ReceivedMail> fetchInbox(ReceiveProtocol protocol, int limit) throws MailException;
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `test.bat OutgoingMailTest` rồi `test.bat AccountConfigTest`
Expected: PASS, `5 tests successful` và `2 tests successful`.

- [ ] **Step 5: Commit**

```bash
git add src test
git commit -m "feat: mail models, AccountConfig (Gmail defaults), MailService interface"
```

---

### Task 6: `MailParser` (thư thô/`Message` thành `ReceivedMail`)

**Files:**
- Create: `src/MailParser.java`
- Test: `test/MailParserTest.java`

**Interfaces:**
- Consumes: `ReceivedMail`, `MailException`
- Produces:
  - `static ReceivedMail MailParser.parse(String id, byte[] raw) throws MailException` — dùng cho đường Socket (POP3/IMAP trả byte thô)
  - `static ReceivedMail MailParser.fromMessage(String id, javax.mail.Message msg) throws MessagingException, IOException` — dùng cho đường JavaMail
  - Quy tắc: body ưu tiên `text/plain`, không có thì lấy `text/html` và đặt `html = true`; phần có `Content-Disposition: attachment` hoặc có filename được liệt kê vào `attachmentNames` (đã giải mã RFC 2047).

- [ ] **Step 1: Write the failing test**

`test/MailParserTest.java`:

```java
import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import org.junit.jupiter.api.Test;

public class MailParserTest {
    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void parsesPlainTextWithEncodedHeaders() throws Exception {
        String raw = "From: =?UTF-8?B?VsO9?= <vy@test.com>\r\n"
                + "Subject: =?UTF-8?B?Q2jDoG8=?=\r\n"
                + "Date: Sun, 18 Oct 2026 10:00:00 +0700\r\n"
                + "Content-Type: text/plain; charset=utf-8\r\n\r\n"
                + "Xin chao\r\n";
        ReceivedMail m = MailParser.parse("1", bytes(raw));
        assertEquals("1", m.id);
        assertEquals("Chào", m.subject);
        assertTrue(m.from.contains("Vý"));
        assertTrue(m.from.contains("vy@test.com"));
        assertEquals("Xin chao", m.body.trim());
        assertFalse(m.html);
        assertFalse(m.date.isEmpty());
        assertEquals(Collections.emptyList(), m.attachmentNames);
    }

    @Test
    void htmlOnlyMailIsFlaggedAsHtml() throws Exception {
        String raw = "From: a@x.com\r\nSubject: H\r\nContent-Type: text/html; charset=utf-8\r\n\r\n<p>Hi</p>\r\n";
        ReceivedMail m = MailParser.parse("2", bytes(raw));
        assertTrue(m.html);
        assertEquals("<p>Hi</p>", m.body.trim());
    }

    @Test
    void listsAttachmentNamesFromMultipart() throws Exception {
        String raw = "From: a@x.com\r\nSubject: F\r\n"
                + "Content-Type: multipart/mixed; boundary=\"B\"\r\n\r\n"
                + "--B\r\nContent-Type: text/plain; charset=utf-8\r\n\r\nNoi dung\r\n"
                + "--B\r\nContent-Type: application/pdf; name=\"report.pdf\"\r\n"
                + "Content-Disposition: attachment; filename=\"report.pdf\"\r\n"
                + "Content-Transfer-Encoding: base64\r\n\r\nSGVsbG8=\r\n"
                + "--B--\r\n";
        ReceivedMail m = MailParser.parse("3", bytes(raw));
        assertEquals("Noi dung", m.body.trim());
        assertEquals(Collections.singletonList("report.pdf"), m.attachmentNames);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `test.bat MailParserTest`
Expected: FAIL, `cannot find symbol ... class MailParser`.

- [ ] **Step 3: Write minimal implementation**

`src/MailParser.java`:

```java
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import javax.mail.Address;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Part;
import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeUtility;

public final class MailParser {
    private MailParser() {
    }

    public static ReceivedMail parse(String id, byte[] raw) throws MailException {
        try {
            MimeMessage msg = new MimeMessage(Session.getInstance(new Properties()), new ByteArrayInputStream(raw));
            return fromMessage(id, msg);
        } catch (MessagingException | IOException e) {
            throw new MailException("Không đọc được nội dung thư: " + e.getMessage(), -1, false, e);
        }
    }

    public static ReceivedMail fromMessage(String id, Message msg) throws MessagingException, IOException {
        String from = "";
        Address[] addresses = msg.getFrom();
        if (addresses != null && addresses.length > 0) {
            from = addresses[0] instanceof InternetAddress
                    ? ((InternetAddress) addresses[0]).toUnicodeString()
                    : addresses[0].toString();
        }
        String subject = msg.getSubject() == null ? "" : msg.getSubject();
        Date sent = msg.getSentDate();
        String date = sent == null ? "" : new SimpleDateFormat("dd/MM/yyyy HH:mm").format(sent);

        Collected c = new Collected();
        collect(msg, c);
        boolean html = c.text == null && c.html != null;
        String body = c.text != null ? c.text : (c.html != null ? c.html : "");
        return new ReceivedMail(id, from, subject, date, body, html, c.attachments);
    }

    private static final class Collected {
        String text;
        String html;
        final List<String> attachments = new ArrayList<>();
    }

    private static void collect(Part part, Collected c) throws MessagingException, IOException {
        String fileName = part.getFileName();
        boolean attachment = Part.ATTACHMENT.equalsIgnoreCase(part.getDisposition()) || fileName != null;
        if (attachment) {
            c.attachments.add(fileName == null ? "(không tên)" : MimeUtility.decodeText(fileName));
        } else if (part.isMimeType("text/plain")) {
            if (c.text == null) {
                c.text = String.valueOf(part.getContent());
            }
        } else if (part.isMimeType("text/html")) {
            if (c.html == null) {
                c.html = String.valueOf(part.getContent());
            }
        } else if (part.isMimeType("multipart/*")) {
            Multipart mp = (Multipart) part.getContent();
            for (int i = 0; i < mp.getCount(); i++) {
                collect(mp.getBodyPart(i), c);
            }
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `test.bat MailParserTest`
Expected: PASS, `3 tests successful`.

- [ ] **Step 5: Commit**

```bash
git add src/MailParser.java test/MailParserTest.java
git commit -m "feat: MailParser for raw bytes and javax.mail messages"
```

---

### Task 7: `MimeBuilder` dựng thư cho đường Socket (mục 5, 7)

**Files:**
- Create: `src/MimeBuilder.java`
- Test: `test/MimeBuilderTest.java`

**Interfaces:**
- Consumes: `OutgoingMail`, `MailParser.parse` (để kiểm tra vòng tròn trong test)
- Produces:
  - `static String MimeBuilder.build(String from, OutgoingMail mail) throws IOException` — thư hoàn chỉnh (header + body), mọi dòng kết thúc CRLF, **chỉ ký tự ASCII**, chưa dot-stuff, chưa có dòng `.` kết thúc
  - `static String MimeBuilder.dotStuff(String raw)`
  - `static String MimeBuilder.wrapBase64(byte[] data)` — dòng tối đa 76 ký tự, mỗi dòng kết thúc CRLF
  - Thiết kế: phần text luôn mã hóa `base64` + `charset=utf-8` nên toàn bộ thư là ASCII, tránh lỗi charset trên đường truyền; có file đính kèm thì dùng `multipart/mixed`.

- [ ] **Step 1: Write the failing test**

`test/MimeBuilderTest.java`:

```java
import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class MimeBuilderTest {
    private static OutgoingMail mail(String subject, String body, boolean html, File... files) {
        return new OutgoingMail(Arrays.asList("to@test.com"), subject, body, html, Arrays.asList(files));
    }

    private static ReceivedMail roundTrip(String built) throws Exception {
        return MailParser.parse("1", built.getBytes(StandardCharsets.US_ASCII));
    }

    @Test
    void wrapBase64SplitsAt76CharsWithCrlf() {
        byte[] data = new byte[200];
        String wrapped = MimeBuilder.wrapBase64(data);
        String[] lines = wrapped.split("\r\n");
        assertTrue(wrapped.endsWith("\r\n"));
        for (String line : lines) {
            assertTrue(line.length() <= 76);
        }
        StringBuilder joined = new StringBuilder();
        for (String line : lines) {
            joined.append(line);
        }
        assertArrayEquals(data, Base64.getDecoder().decode(joined.toString()));
        assertEquals(76, lines[0].length());
    }

    @Test
    void dotStuffDoublesLeadingDots() {
        assertEquals("..a\r\nb\r\n..\r\n...c\r\n", MimeBuilder.dotStuff(".a\r\nb\r\n.\r\n..c\r\n"));
        assertEquals("abc\r\n", MimeBuilder.dotStuff("abc\r\n"));
    }

    @Test
    void vietnameseSubjectAndBodySurviveAsAscii() throws Exception {
        String built = MimeBuilder.build("me@test.com", mail("Báo cáo doanh thu tháng 8", "Xin chào!", false));
        assertTrue(built.chars().allMatch(ch -> ch < 128), "toàn bộ thư phải là ASCII");
        assertTrue(built.contains("\r\nMIME-Version: 1.0\r\n"));
        ReceivedMail parsed = roundTrip(built);
        assertEquals("Báo cáo doanh thu tháng 8", parsed.subject);
        assertEquals("Xin chào!", parsed.body);
        assertFalse(parsed.html);
    }

    @Test
    void htmlBodyIsFlaggedAsHtml() throws Exception {
        String built = MimeBuilder.build("me@test.com", mail("H", "<h2>Chào</h2>", true));
        assertTrue(built.contains("Content-Type: text/html; charset=utf-8"));
        ReceivedMail parsed = roundTrip(built);
        assertTrue(parsed.html);
        assertEquals("<h2>Chào</h2>", parsed.body);
    }

    @Test
    void attachmentsBecomeMultipartMixed(@TempDir Path dir) throws Exception {
        File a = Files.write(dir.resolve("report.txt"), "hello".getBytes()).toFile();
        File b = Files.write(dir.resolve("data.bin"), new byte[]{1, 2, 3}).toFile();
        String built = MimeBuilder.build("me@test.com", mail("F", "Nội dung", false, a, b));
        assertTrue(built.contains("Content-Type: multipart/mixed; boundary=\""));
        assertTrue(built.contains("Content-Type: text/plain; name=\"report.txt\""));
        assertTrue(built.contains("Content-Type: application/octet-stream; name=\"data.bin\""));
        ReceivedMail parsed = roundTrip(built);
        assertEquals("Nội dung", parsed.body);
        assertEquals(Arrays.asList("report.txt", "data.bin"), parsed.attachmentNames);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `test.bat MimeBuilderTest`
Expected: FAIL, `cannot find symbol ... class MimeBuilder`.

- [ ] **Step 3: Write minimal implementation**

`src/MimeBuilder.java`:

```java
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.UUID;
import javax.mail.internet.MimeUtility;

public final class MimeBuilder {
    private static final String CRLF = "\r\n";

    private MimeBuilder() {
    }

    public static String build(String from, OutgoingMail mail) throws IOException {
        StringBuilder sb = new StringBuilder();
        header(sb, "From", from);
        header(sb, "To", String.join(", ", mail.to));
        header(sb, "Subject", encodeHeader(mail.subject));
        header(sb, "Date", DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.now()));
        header(sb, "MIME-Version", "1.0");

        String textType = mail.html ? "text/html" : "text/plain";
        byte[] bodyBytes = mail.body.getBytes(StandardCharsets.UTF_8);

        if (mail.attachments.isEmpty()) {
            header(sb, "Content-Type", textType + "; charset=utf-8");
            header(sb, "Content-Transfer-Encoding", "base64");
            sb.append(CRLF);
            sb.append(wrapBase64(bodyBytes));
            return sb.toString();
        }

        String boundary = "----=_Part_" + UUID.randomUUID();
        header(sb, "Content-Type", "multipart/mixed; boundary=\"" + boundary + "\"");
        sb.append(CRLF);

        // Part 1: nội dung
        sb.append("--").append(boundary).append(CRLF);
        header(sb, "Content-Type", textType + "; charset=utf-8");
        header(sb, "Content-Transfer-Encoding", "base64");
        sb.append(CRLF);
        sb.append(wrapBase64(bodyBytes));

        // Part 2..n: từng file đính kèm
        for (java.io.File file : mail.attachments) {
            String type = Files.probeContentType(file.toPath());
            if (type == null) {
                type = "application/octet-stream";
            }
            String name = encodeHeader(file.getName());
            sb.append("--").append(boundary).append(CRLF);
            header(sb, "Content-Type", type + "; name=\"" + name + "\"");
            header(sb, "Content-Transfer-Encoding", "base64");
            header(sb, "Content-Disposition", "attachment; filename=\"" + name + "\"");
            sb.append(CRLF);
            sb.append(wrapBase64(Files.readAllBytes(file.toPath())));
        }
        sb.append("--").append(boundary).append("--").append(CRLF);
        return sb.toString();
    }

    /** Thêm một dấu chấm vào đầu mọi dòng bắt đầu bằng dấu chấm (RFC 5321 mục 4.5.2). */
    public static String dotStuff(String raw) {
        String s = raw.startsWith(".") ? "." + raw : raw;
        return s.replace(CRLF + ".", CRLF + "..");
    }

    /** Base64 chia dòng 76 ký tự, mỗi dòng kết thúc CRLF. */
    public static String wrapBase64(byte[] data) {
        String raw = Base64.getEncoder().encodeToString(data);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < raw.length(); i += 76) {
            out.append(raw, i, Math.min(i + 76, raw.length())).append(CRLF);
        }
        return out.toString();
    }

    /** RFC 2047: ký tự không phải ASCII thành =?UTF-8?B?...?=. Chuỗi ASCII giữ nguyên. */
    static String encodeHeader(String text) {
        try {
            return MimeUtility.encodeText(text, "UTF-8", "B");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void header(StringBuilder sb, String name, String value) {
        sb.append(name).append(": ").append(value).append(CRLF);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `test.bat MimeBuilderTest`
Expected: PASS, `5 tests successful`. Nếu test `attachmentsBecomeMultipartMixed` fail ở `text/plain; name="report.txt"` vì JDK trả kiểu khác cho `.txt` (hiếm gặp), đổi file mẫu trong test sang `report.pdf` và kỳ vọng `application/pdf` hoặc `application/octet-stream` theo kết quả `Files.probeContentType` của máy.

- [ ] **Step 5: Commit**

```bash
git add src/MimeBuilder.java test/MimeBuilderTest.java
git commit -m "feat: MimeBuilder with RFC2047 subject, base64 76-col, dot-stuffing"
```

---

### Task 8: `LineConnection` + `ScriptedServer` + `SmtpSocketClient` (mục 4, 5, 6)

**Files:**
- Create: `src/LineConnection.java`, `src/SmtpSocketClient.java`
- Create (test helper): `test/ScriptedServer.java`
- Test: `test/SmtpSocketClientTest.java`

**Interfaces:**
- Consumes: `AccountConfig`, `Security`, `OutgoingMail`, `MimeBuilder.build/dotStuff`, `MailException`, `ReplyCode`, `MailLog`
- Produces:
  - `class LineConnection implements Closeable` — `LineConnection(String host, int port, boolean implicitTls, int timeoutMs)` ném `IOException`; `String readLine() throws IOException` (bỏ CRLF, đọc `ISO_8859_1`); `byte[] readBytes(int n)`; `void writeLine(String line)` (thêm CRLF, flush); `void write(String raw)` (flush); `void startTls()` (nâng cấp socket đang có thành TLS, có kiểm tra hostname); `void close()`
  - `class SmtpSocketClient` — `SmtpSocketClient(AccountConfig cfg, MailLog log)`; `void send(OutgoingMail mail) throws MailException` (một lần thử, không retry; lỗi mạng ném `MailException.fromIo`)
  - Test helper `ScriptedServer` — `ScriptedServer(String... script)`, `int port()`, `List<String> received()`, `List<String> dataLines()`, `void assertCompleted()`, `close()`. Cú pháp script: `"S: text"` server gửi `text + CRLF`; `"L: raw"` server gửi nguyên văn không thêm CRLF; `"C: text"` server chờ đúng dòng đó từ client (kết thúc bằng `*` nghĩa là khớp tiền tố); `"DATA"` server đọc đến dòng chỉ có `.`.

- [ ] **Step 1: Write the test helper and the failing test**

`test/ScriptedServer.java`:

```java
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Server giả một kết nối, chạy theo kịch bản, dùng để test client Socket mà không cần mạng. */
public class ScriptedServer implements Closeable {
    private final ServerSocket server;
    private final Thread thread;
    private final List<String> received = Collections.synchronizedList(new ArrayList<String>());
    private final List<String> dataLines = Collections.synchronizedList(new ArrayList<String>());
    private final List<String> errors = Collections.synchronizedList(new ArrayList<String>());

    public ScriptedServer(String... script) throws IOException {
        server = new ServerSocket(0);
        server.setSoTimeout(5000);
        thread = new Thread(() -> serve(script));
        thread.setDaemon(true);
        thread.start();
    }

    public int port() {
        return server.getLocalPort();
    }

    public List<String> received() {
        return received;
    }

    public List<String> dataLines() {
        return dataLines;
    }

    private void serve(String[] script) {
        try (Socket s = server.accept()) {
            s.setSoTimeout(5000);
            InputStream in = s.getInputStream();
            OutputStream out = s.getOutputStream();
            for (String step : script) {
                if (step.startsWith("S: ")) {
                    out.write((step.substring(3) + "\r\n").getBytes(StandardCharsets.ISO_8859_1));
                    out.flush();
                } else if (step.startsWith("L: ")) {
                    out.write(step.substring(3).getBytes(StandardCharsets.ISO_8859_1));
                    out.flush();
                } else if (step.equals("DATA")) {
                    String line;
                    while (!(line = readLine(in)).equals(".")) {
                        dataLines.add(line);
                    }
                } else if (step.startsWith("C: ")) {
                    String expected = step.substring(3);
                    String actual = readLine(in);
                    received.add(actual);
                    boolean ok = expected.endsWith("*")
                            ? actual.startsWith(expected.substring(0, expected.length() - 1))
                            : expected.equals(actual);
                    if (!ok) {
                        errors.add("mong đợi [" + expected + "] nhưng nhận [" + actual + "]");
                    }
                } else {
                    errors.add("bước kịch bản không hợp lệ: " + step);
                }
            }
        } catch (Exception e) {
            errors.add("lỗi server giả: " + e);
        }
    }

    private static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        int b;
        while ((b = in.read()) != -1 && b != '\n') {
            if (b != '\r') {
                buf.write(b);
            }
        }
        if (b == -1 && buf.size() == 0) {
            throw new EOFException("client đóng kết nối sớm");
        }
        return new String(buf.toByteArray(), StandardCharsets.ISO_8859_1);
    }

    public void assertCompleted() throws InterruptedException {
        thread.join(5000);
        assertTrue(errors.isEmpty(), errors.toString());
        assertTrue(!thread.isAlive(), "kịch bản chưa chạy hết");
    }

    @Override
    public void close() throws IOException {
        server.close();
    }
}
```

`test/SmtpSocketClientTest.java`:

```java
import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class SmtpSocketClientTest {
    private static String b64(String s) {
        return Base64.getEncoder().encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }

    private static AccountConfig config(int port) {
        AccountConfig c = new AccountConfig("u@test.com", "secret");
        c.smtpHost = "localhost";
        c.smtpPort = port;
        c.smtpSecurity = Security.NONE;
        return c;
    }

    private static OutgoingMail mail() {
        return new OutgoingMail(Arrays.asList("a@test.com"), "Hello", "Body", false, Collections.<File>emptyList());
    }

    @Test
    void sendsMailFollowingEightStepFlow(@TempDir Path dir) throws Exception {
        try (ScriptedServer server = new ScriptedServer(
                "S: 220 test ESMTP",
                "C: EHLO *",
                "S: 250-test",
                "S: 250 AUTH LOGIN",
                "C: AUTH LOGIN",
                "S: 334 VXNlcm5hbWU6",
                "C: " + b64("u@test.com"),
                "S: 334 UGFzc3dvcmQ6",
                "C: " + b64("secret"),
                "S: 235 ok",
                "C: MAIL FROM:<u@test.com>",
                "S: 250 ok",
                "C: RCPT TO:<a@test.com>",
                "S: 250 ok",
                "C: DATA",
                "S: 354 go ahead",
                "DATA",
                "S: 250 queued",
                "C: QUIT",
                "S: 221 bye")) {
            Path logFile = dir.resolve("smtp.log");
            new SmtpSocketClient(config(server.port()), new MailLog(logFile)).send(mail());
            server.assertCompleted();

            assertTrue(server.dataLines().contains("Subject: Hello"));
            assertTrue(server.dataLines().contains("From: u@test.com"));
            String log = new String(Files.readAllBytes(logFile), StandardCharsets.UTF_8);
            assertFalse(log.contains("secret"));
            assertFalse(log.contains(b64("secret")), "mật khẩu không được xuất hiện trong log");
        }
    }

    @Test
    void wrongPasswordIsPermanentError() throws Exception {
        try (ScriptedServer server = new ScriptedServer(
                "S: 220 hi", "C: EHLO *", "S: 250 ok",
                "C: AUTH LOGIN", "S: 334 x",
                "C: *", "S: 334 y",
                "C: *", "S: 535 5.7.8 Username and Password not accepted")) {
            MailException e = assertThrows(MailException.class,
                    () -> new SmtpSocketClient(config(server.port()), new MailLog(null)).send(mail()));
            assertEquals(535, e.getReplyCode());
            assertFalse(e.isTransientError());
            server.assertCompleted();
        }
    }

    @Test
    void mailboxBusyIsTransientError() throws Exception {
        try (ScriptedServer server = new ScriptedServer(
                "S: 220 hi", "C: EHLO *", "S: 250 ok",
                "C: AUTH LOGIN", "S: 334 x",
                "C: *", "S: 334 y",
                "C: *", "S: 235 ok",
                "C: MAIL FROM:*", "S: 250 ok",
                "C: RCPT TO:*", "S: 450 Mailbox busy")) {
            MailException e = assertThrows(MailException.class,
                    () -> new SmtpSocketClient(config(server.port()), new MailLog(null)).send(mail()));
            assertEquals(450, e.getReplyCode());
            assertTrue(e.isTransientError());
            server.assertCompleted();
        }
    }

    @Test
    void connectionRefusedIsTransientNetworkError() {
        AccountConfig c = config(1); // cổng 1 không có ai nghe
        MailException e = assertThrows(MailException.class,
                () -> new SmtpSocketClient(c, new MailLog(null)).send(mail()));
        assertTrue(e.isTransientError());
        assertEquals(-1, e.getReplyCode());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `test.bat SmtpSocketClientTest`
Expected: FAIL, `cannot find symbol ... class SmtpSocketClient`.

- [ ] **Step 3: Write minimal implementation**

`src/LineConnection.java`:

```java
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/** Socket (thường hoặc SSL) với đọc/ghi theo dòng CRLF. Dữ liệu đi qua dạng ISO_8859_1 để giữ nguyên byte. */
public class LineConnection implements Closeable {
    private final String host;
    private final int timeoutMs;
    private Socket socket;
    private InputStream in;
    private OutputStream out;

    public LineConnection(String host, int port, boolean implicitTls, int timeoutMs) throws IOException {
        this.host = host;
        this.timeoutMs = timeoutMs;
        Socket plain = new Socket();
        plain.connect(new InetSocketAddress(host, port), timeoutMs);
        plain.setSoTimeout(timeoutMs);
        attach(implicitTls ? wrapTls(plain) : plain);
    }

    private SSLSocket wrapTls(Socket plain) throws IOException {
        SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
        SSLSocket ssl = (SSLSocket) factory.createSocket(plain, host, plain.getPort(), true);
        SSLParameters params = ssl.getSSLParameters();
        params.setEndpointIdentificationAlgorithm("HTTPS"); // kiểm tra hostname trong chứng chỉ
        ssl.setSSLParameters(params);
        ssl.setSoTimeout(timeoutMs);
        ssl.startHandshake();
        return ssl;
    }

    private void attach(Socket s) throws IOException {
        this.socket = s;
        this.in = new BufferedInputStream(s.getInputStream());
        this.out = new BufferedOutputStream(s.getOutputStream());
    }

    /** Nâng cấp kết nối đang mở lên TLS (sau khi server trả 220 cho STARTTLS). */
    public void startTls() throws IOException {
        attach(wrapTls(socket));
    }

    public String readLine() throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        int b;
        while ((b = in.read()) != -1 && b != '\n') {
            buf.write(b);
        }
        if (b == -1 && buf.size() == 0) {
            throw new EOFException("Server đã đóng kết nối");
        }
        byte[] raw = buf.toByteArray();
        int len = raw.length;
        if (len > 0 && raw[len - 1] == '\r') {
            len--;
        }
        return new String(raw, 0, len, StandardCharsets.ISO_8859_1);
    }

    public byte[] readBytes(int n) throws IOException {
        byte[] data = new byte[n];
        int off = 0;
        while (off < n) {
            int r = in.read(data, off, n - off);
            if (r < 0) {
                throw new EOFException("Server đóng kết nối khi đang gửi dữ liệu");
            }
            off += r;
        }
        return data;
    }

    public void writeLine(String line) throws IOException {
        write(line + "\r\n");
    }

    public void write(String raw) throws IOException {
        out.write(raw.getBytes(StandardCharsets.ISO_8859_1));
        out.flush();
    }

    @Override
    public void close() throws IOException {
        socket.close();
    }
}
```

`src/SmtpSocketClient.java`:

```java
import java.io.IOException;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/** SMTP client viết tay trên Socket, theo quy trình 8 bước ở mục 4. Mỗi lần gọi send là một lần thử. */
public class SmtpSocketClient {
    private static final int TIMEOUT_MS = 15000;

    private static final class Reply {
        final int code;
        final String text;

        Reply(int code, String text) {
            this.code = code;
            this.text = text;
        }
    }

    private final AccountConfig cfg;
    private final MailLog log;

    public SmtpSocketClient(AccountConfig cfg, MailLog log) {
        this.cfg = cfg;
        this.log = log;
    }

    public void send(OutgoingMail mail) throws MailException {
        log.info("SMTP(Socket): kết nối " + cfg.smtpHost + ":" + cfg.smtpPort + " (" + cfg.smtpSecurity + ")");
        try (LineConnection c = new LineConnection(cfg.smtpHost, cfg.smtpPort,
                cfg.smtpSecurity == Security.SSL, TIMEOUT_MS)) {
            expect(readReply(c), 220);                                   // 1. Connect (banner 220)
            String hello = "EHLO " + clientName();
            command(c, hello, hello, 250);                                // 2. EHLO
            if (cfg.smtpSecurity == Security.STARTTLS) {
                command(c, "STARTTLS", "STARTTLS", 220);
                c.startTls();                                             // nâng cấp socket lên TLS
                command(c, hello, hello, 250);                            // EHLO lại sau khi có TLS
            }
            command(c, "AUTH LOGIN", "AUTH LOGIN", 334);                  // 3. AUTH LOGIN
            command(c, b64(cfg.username), "<username base64>", 334);
            command(c, b64(cfg.password), "<password ẩn>", 235);
            String from = "MAIL FROM:<" + cfg.username + ">";             // 4. MAIL FROM
            command(c, from, from, 250);
            for (String to : mail.to) {                                   // 5. RCPT TO
                String rcpt = "RCPT TO:<" + to + ">";
                command(c, rcpt, rcpt, 250);
            }
            command(c, "DATA", "DATA", 354);                              // 6. DATA
            String message = MimeBuilder.dotStuff(MimeBuilder.build(cfg.username, mail)); // 7. nội dung + "."
            log.info("C: <nội dung thư " + message.length() + " ký tự>");
            c.write(message + ".\r\n");
            expect(readReply(c), 250);
            try {                                                         // 8. QUIT
                command(c, "QUIT", "QUIT", 221);
            } catch (MailException | IOException ignored) {
                // thư đã được nhận (250), lỗi lúc thoát không quan trọng
            }
            log.info("SMTP(Socket): gửi thành công tới " + mail.to);
        } catch (IOException e) {
            throw MailException.fromIo(e);
        }
    }

    private Reply command(LineConnection c, String wire, String logText, int expected)
            throws IOException, MailException {
        log.info("C: " + logText);
        c.writeLine(wire);
        Reply reply = readReply(c);
        expect(reply, expected);
        return reply;
    }

    /** Đọc reply nhiều dòng: "250-..." tiếp tục, "250 ..." là dòng cuối. */
    private Reply readReply(LineConnection c) throws IOException {
        StringBuilder text = new StringBuilder();
        String line;
        while (true) {
            line = c.readLine();
            log.info("S: " + line);
            text.append(line).append('\n');
            if (ReplyCode.isLastLine(line)) {
                break;
            }
        }
        return new Reply(ReplyCode.parse(line), text.toString().trim());
    }

    private static void expect(Reply reply, int expected) throws MailException {
        if (reply.code != expected) {
            throw new MailException("SMTP: " + reply.text, reply.code, ReplyCode.isTransient(reply.code));
        }
    }

    private static String b64(String s) {
        return Base64.getEncoder().encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }

    private static String clientName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (IOException e) {
            return "localhost";
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `test.bat SmtpSocketClientTest`
Expected: PASS, `4 tests successful`. Phần TLS thật (465, 587) được kiểm bằng Gmail ở Task 14, vì server giả chỉ nói TCP thường.

- [ ] **Step 5: Commit**

```bash
git add src/LineConnection.java src/SmtpSocketClient.java test/ScriptedServer.java test/SmtpSocketClientTest.java
git commit -m "feat: LineConnection and SMTP socket client (SSL, STARTTLS, multiline replies)"
```

---

### Task 9: `Pop3SocketClient` (mục 8)

**Files:**
- Create: `src/Pop3SocketClient.java`
- Test: `test/Pop3SocketClientTest.java`

**Interfaces:**
- Consumes: `LineConnection`, `AccountConfig.pop3*`, `MailParser.parse`, `MailException`, `MailLog`, `ScriptedServer`
- Produces: `class Pop3SocketClient` — `Pop3SocketClient(AccountConfig cfg, MailLog log)`; `List<ReceivedMail> fetchLatest(int limit) throws MailException` (mới nhất đứng đầu; `id` là số thứ tự thư POP3). Lỗi `-ERR` chứa `[SYS/TEMP]` là transient, còn lại permanent.

- [ ] **Step 1: Write the failing test**

`test/Pop3SocketClientTest.java`:

```java
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

public class Pop3SocketClientTest {
    private static AccountConfig config(int port) {
        AccountConfig c = new AccountConfig("u@test.com", "secret");
        c.pop3Host = "localhost";
        c.pop3Port = port;
        c.pop3Security = Security.NONE;
        return c;
    }

    @Test
    void fetchesNewestFirstAndUnstuffsDots() throws Exception {
        try (ScriptedServer server = new ScriptedServer(
                "S: +OK POP3 ready",
                "C: USER u@test.com", "S: +OK",
                "C: PASS secret", "S: +OK logged in",
                "C: STAT", "S: +OK 2 300",
                "C: RETR 2", "S: +OK",
                "S: From: b@x.com", "S: Subject: Second", "S: ", "S: ..dong bat dau bang cham", "S: .",
                "C: RETR 1", "S: +OK",
                "S: From: a@x.com", "S: Subject: First", "S: ", "S: noi dung 1", "S: .",
                "C: QUIT", "S: +OK bye")) {
            List<ReceivedMail> mails = new Pop3SocketClient(config(server.port()), new MailLog(null)).fetchLatest(5);
            server.assertCompleted();

            assertEquals(2, mails.size());
            assertEquals("Second", mails.get(0).subject);
            assertEquals("2", mails.get(0).id);
            assertTrue(mails.get(0).body.contains(".dong bat dau bang cham"));
            assertFalse(mails.get(0).body.contains("..dong"));
            assertEquals("First", mails.get(1).subject);
        }
    }

    @Test
    void respectsLimit() throws Exception {
        try (ScriptedServer server = new ScriptedServer(
                "S: +OK ready",
                "C: USER *", "S: +OK",
                "C: PASS *", "S: +OK",
                "C: STAT", "S: +OK 3 900",
                "C: RETR 3", "S: +OK",
                "S: Subject: Third", "S: ", "S: x", "S: .",
                "C: QUIT", "S: +OK bye")) {
            List<ReceivedMail> mails = new Pop3SocketClient(config(server.port()), new MailLog(null)).fetchLatest(1);
            server.assertCompleted();
            assertEquals(1, mails.size());
            assertEquals("Third", mails.get(0).subject);
        }
    }

    @Test
    void wrongPasswordIsPermanentError() throws Exception {
        try (ScriptedServer server = new ScriptedServer(
                "S: +OK ready",
                "C: USER *", "S: +OK",
                "C: PASS *", "S: -ERR [AUTH] Username and password not accepted.")) {
            MailException e = assertThrows(MailException.class,
                    () -> new Pop3SocketClient(config(server.port()), new MailLog(null)).fetchLatest(5));
            assertFalse(e.isTransientError());
            assertTrue(e.getMessage().contains("not accepted"));
            server.assertCompleted();
        }
    }

    @Test
    void tempErrorIsTransient() throws Exception {
        try (ScriptedServer server = new ScriptedServer(
                "S: +OK ready",
                "C: USER *", "S: -ERR [SYS/TEMP] Server busy")) {
            MailException e = assertThrows(MailException.class,
                    () -> new Pop3SocketClient(config(server.port()), new MailLog(null)).fetchLatest(5));
            assertTrue(e.isTransientError());
            server.assertCompleted();
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `test.bat Pop3SocketClientTest`
Expected: FAIL, `cannot find symbol ... class Pop3SocketClient`.

- [ ] **Step 3: Write minimal implementation**

`src/Pop3SocketClient.java`:

```java
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** POP3 client viết tay: USER, PASS, STAT, RETR, QUIT (mục 8). */
public class Pop3SocketClient {
    private static final int TIMEOUT_MS = 15000;

    private final AccountConfig cfg;
    private final MailLog log;

    public Pop3SocketClient(AccountConfig cfg, MailLog log) {
        this.cfg = cfg;
        this.log = log;
    }

    public List<ReceivedMail> fetchLatest(int limit) throws MailException {
        log.info("POP3(Socket): kết nối " + cfg.pop3Host + ":" + cfg.pop3Port);
        try (LineConnection c = new LineConnection(cfg.pop3Host, cfg.pop3Port,
                cfg.pop3Security == Security.SSL, TIMEOUT_MS)) {
            String greeting = c.readLine();
            log.info("S: " + greeting);
            requireOk(greeting);

            command(c, "USER " + cfg.username, "USER " + cfg.username);
            command(c, "PASS " + cfg.password, "PASS <ẩn>");
            String stat = command(c, "STAT", "STAT");
            int count = Integer.parseInt(stat.trim().split("\\s+")[1]);

            List<ReceivedMail> mails = new ArrayList<>();
            for (int i = count; i > Math.max(0, count - limit); i--) {
                byte[] raw = retrieve(c, i);
                mails.add(MailParser.parse(String.valueOf(i), raw));
            }
            try {
                command(c, "QUIT", "QUIT");
            } catch (MailException ignored) {
                // không quan trọng
            }
            log.info("POP3(Socket): đã tải " + mails.size() + " thư");
            return mails;
        } catch (IOException e) {
            throw MailException.fromIo(e);
        }
    }

    private String command(LineConnection c, String wire, String logText) throws IOException, MailException {
        log.info("C: " + logText);
        c.writeLine(wire);
        String response = c.readLine();
        log.info("S: " + response);
        requireOk(response);
        return response;
    }

    /** RETR n: đọc đến dòng chỉ có ".", bỏ một dấu "." ở các dòng "..xxx" (dot-unstuffing). */
    private byte[] retrieve(LineConnection c, int n) throws IOException, MailException {
        command(c, "RETR " + n, "RETR " + n);
        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        String line;
        while (!(line = c.readLine()).equals(".")) {
            if (line.startsWith("..")) {
                line = line.substring(1);
            }
            raw.write(line.getBytes(StandardCharsets.ISO_8859_1));
            raw.write('\r');
            raw.write('\n');
        }
        log.info("S: <thư " + n + ": " + raw.size() + " byte>");
        return raw.toByteArray();
    }

    private static void requireOk(String response) throws MailException {
        if (!response.startsWith("+OK")) {
            throw new MailException("POP3: " + response, -1, response.contains("[SYS/TEMP]"));
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `test.bat Pop3SocketClientTest`
Expected: PASS, `4 tests successful`.

- [ ] **Step 5: Commit**

```bash
git add src/Pop3SocketClient.java test/Pop3SocketClientTest.java
git commit -m "feat: POP3 socket client with dot-unstuffing and error classification"
```

---

### Task 10: `ImapSocketClient` (IMAP bằng Socket thuần)

**Files:**
- Create: `src/ImapSocketClient.java`
- Test: `test/ImapSocketClientTest.java`

**Interfaces:**
- Consumes: `LineConnection`, `AccountConfig.imap*`, `MailParser.parse`, `MailException`, `MailLog`, `ScriptedServer`
- Produces: `class ImapSocketClient` — `ImapSocketClient(AccountConfig cfg, MailLog log)`; `List<ReceivedMail> fetchLatest(int limit) throws MailException`. Dùng lệnh có tag `A1`, `A2`...: `LOGIN`, `SELECT INBOX`, `FETCH n BODY.PEEK[]` (không đánh dấu đã đọc), `LOGOUT`. Hiểu literal `{N}` của IMAP: đọc đúng N byte rồi đọc tiếp phần còn lại của dòng. `NO`/`BAD` là permanent, riêng `[UNAVAILABLE]` là transient.

- [ ] **Step 1: Write the failing test**

`test/ImapSocketClientTest.java`:

```java
import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

public class ImapSocketClientTest {
    private static AccountConfig config(int port) {
        AccountConfig c = new AccountConfig("u@test.com", "se\"cret");
        c.imapHost = "localhost";
        c.imapPort = port;
        c.imapSecurity = Security.NONE;
        return c;
    }

    @Test
    void fetchesMessageThroughLiteral() throws Exception {
        String raw = "From: a@x.com\r\nSubject: Hi\r\n\r\nBody\r\n";
        int n = raw.getBytes(StandardCharsets.US_ASCII).length;
        try (ScriptedServer server = new ScriptedServer(
                "S: * OK IMAP4rev1 ready",
                "C: A1 LOGIN \"u@test.com\" \"se\\\"cret\"",
                "S: A1 OK logged in",
                "C: A2 SELECT INBOX",
                "S: * 1 EXISTS",
                "S: A2 OK [READ-WRITE] done",
                "C: A3 FETCH 1 BODY.PEEK[]",
                "S: * 1 FETCH (BODY[] {" + n + "}",
                "L: " + raw,
                "S:  FLAGS (\\Seen))",
                "S: A3 OK Success",
                "C: A4 LOGOUT",
                "S: * BYE",
                "S: A4 OK done")) {
            List<ReceivedMail> mails = new ImapSocketClient(config(server.port()), new MailLog(null)).fetchLatest(10);
            server.assertCompleted();
            assertEquals(1, mails.size());
            assertEquals("Hi", mails.get(0).subject);
            assertEquals("Body", mails.get(0).body.trim());
            assertEquals("1", mails.get(0).id);
        }
    }

    @Test
    void emptyMailboxReturnsEmptyList() throws Exception {
        try (ScriptedServer server = new ScriptedServer(
                "S: * OK ready",
                "C: A1 LOGIN *", "S: A1 OK ok",
                "C: A2 SELECT INBOX", "S: * 0 EXISTS", "S: A2 OK done",
                "C: A3 LOGOUT", "S: * BYE", "S: A3 OK done")) {
            List<ReceivedMail> mails = new ImapSocketClient(config(server.port()), new MailLog(null)).fetchLatest(10);
            server.assertCompleted();
            assertTrue(mails.isEmpty());
        }
    }

    @Test
    void invalidCredentialsArePermanent() throws Exception {
        try (ScriptedServer server = new ScriptedServer(
                "S: * OK ready",
                "C: A1 LOGIN *",
                "S: A1 NO [AUTHENTICATIONFAILED] Invalid credentials (Failure)")) {
            MailException e = assertThrows(MailException.class,
                    () -> new ImapSocketClient(config(server.port()), new MailLog(null)).fetchLatest(10));
            assertFalse(e.isTransientError());
            assertTrue(e.getMessage().contains("AUTHENTICATIONFAILED"));
            server.assertCompleted();
        }
    }

    @Test
    void unavailableIsTransient() throws Exception {
        try (ScriptedServer server = new ScriptedServer(
                "S: * OK ready",
                "C: A1 LOGIN *",
                "S: A1 NO [UNAVAILABLE] try later")) {
            MailException e = assertThrows(MailException.class,
                    () -> new ImapSocketClient(config(server.port()), new MailLog(null)).fetchLatest(10));
            assertTrue(e.isTransientError());
            server.assertCompleted();
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `test.bat ImapSocketClientTest`
Expected: FAIL, `cannot find symbol ... class ImapSocketClient`.

- [ ] **Step 3: Write minimal implementation**

`src/ImapSocketClient.java`:

```java
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** IMAP client viết tay: LOGIN, SELECT, FETCH BODY.PEEK[], LOGOUT, có xử lý literal {N}. */
public class ImapSocketClient {
    private static final int TIMEOUT_MS = 15000;
    private static final Pattern LITERAL = Pattern.compile("\\{(\\d+)\\}$");
    private static final Pattern EXISTS = Pattern.compile("^\\* (\\d+) EXISTS$");

    private static final class Response {
        final List<String> lines = new ArrayList<>();
        final List<byte[]> literals = new ArrayList<>();
        String status = "";
    }

    private final AccountConfig cfg;
    private final MailLog log;
    private int tagCounter = 0;

    public ImapSocketClient(AccountConfig cfg, MailLog log) {
        this.cfg = cfg;
        this.log = log;
    }

    public List<ReceivedMail> fetchLatest(int limit) throws MailException {
        log.info("IMAP(Socket): kết nối " + cfg.imapHost + ":" + cfg.imapPort);
        tagCounter = 0;
        try (LineConnection c = new LineConnection(cfg.imapHost, cfg.imapPort,
                cfg.imapSecurity == Security.SSL, TIMEOUT_MS)) {
            String greeting = c.readLine();
            log.info("S: " + greeting);
            if (!greeting.startsWith("* OK")) {
                throw new MailException("IMAP: " + greeting, -1, false);
            }

            run(c, "LOGIN " + quote(cfg.username) + " " + quote(cfg.password),
                    "LOGIN " + quote(cfg.username) + " <ẩn>");
            Response select = run(c, "SELECT INBOX", "SELECT INBOX");
            int count = parseExists(select.lines);

            List<ReceivedMail> mails = new ArrayList<>();
            for (int i = count; i > Math.max(0, count - limit); i--) {
                Response fetch = run(c, "FETCH " + i + " BODY.PEEK[]", "FETCH " + i + " BODY.PEEK[]");
                if (fetch.literals.isEmpty()) {
                    throw new MailException("IMAP: server không trả nội dung thư " + i, -1, false);
                }
                mails.add(MailParser.parse(String.valueOf(i), fetch.literals.get(0)));
            }
            try {
                run(c, "LOGOUT", "LOGOUT");
            } catch (MailException ignored) {
                // không quan trọng
            }
            log.info("IMAP(Socket): đã tải " + mails.size() + " thư");
            return mails;
        } catch (IOException e) {
            throw MailException.fromIo(e);
        }
    }

    /** Gửi một lệnh có tag, đọc đến dòng "tag OK/NO/BAD", gom cả literal. */
    private Response run(LineConnection c, String command, String logText) throws IOException, MailException {
        String tag = "A" + (++tagCounter);
        log.info("C: " + tag + " " + logText);
        c.writeLine(tag + " " + command);

        Response r = new Response();
        String line = c.readLine();
        while (true) {
            Matcher m = LITERAL.matcher(line);
            if (m.find()) {
                r.lines.add(line);
                r.literals.add(c.readBytes(Integer.parseInt(m.group(1))));
                line = c.readLine(); // phần còn lại của dòng sau literal
                continue;
            }
            if (line.startsWith(tag + " ")) {
                r.status = line.substring(tag.length() + 1);
                break;
            }
            r.lines.add(line);
            line = c.readLine();
        }
        log.info("S: " + tag + " " + r.status);
        if (!r.status.startsWith("OK")) {
            throw new MailException("IMAP: " + r.status, -1, r.status.contains("[UNAVAILABLE]"));
        }
        return r;
    }

    private static int parseExists(List<String> lines) throws MailException {
        for (String line : lines) {
            Matcher m = EXISTS.matcher(line);
            if (m.matches()) {
                return Integer.parseInt(m.group(1));
            }
        }
        throw new MailException("IMAP: không thấy số lượng thư (EXISTS) sau SELECT", -1, false);
    }

    private static String quote(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `test.bat ImapSocketClientTest`
Expected: PASS, `4 tests successful`.

- [ ] **Step 5: Commit**

```bash
git add src/ImapSocketClient.java test/ImapSocketClientTest.java
git commit -m "feat: IMAP socket client with tagged commands and literal handling"
```

---

### Task 11: `JavaMailErrors` + `JavaMailService` (mục 10)

**Files:**
- Create: `src/JavaMailErrors.java`, `src/JavaMailService.java`
- Test: `test/JavaMailErrorsTest.java`, `test/JavaMailServiceTest.java`

**Interfaces:**
- Consumes: `MailService`, `AccountConfig`, `OutgoingMail`, `ReceivedMail`, `MailParser.fromMessage`, `Retrier.run`, `MailLog`, `MailException`, `ReplyCode`
- Produces:
  - `final class JavaMailErrors` — `static MailException classify(Exception e)`: `MailException` giữ nguyên; `AuthenticationFailedException` thì permanent; `SMTPSendFailedException` thì theo `getReturnCode()` (4xx transient); `SendFailedException` khác thì permanent; lỗi có nguyên nhân `SocketException`/`SocketTimeoutException`/`UnknownHostException` thì transient; còn lại permanent
  - `class JavaMailService implements MailService` — `JavaMailService(AccountConfig cfg, MailLog log, Retrier retrier)`; `static Properties smtpProperties(AccountConfig cfg)` (package-private, để test); `send` và `fetchInbox` đều chạy qua `retrier.run`

- [ ] **Step 1: Write the failing tests**

`test/JavaMailErrorsTest.java`:

```java
import static org.junit.jupiter.api.Assertions.*;

import com.sun.mail.smtp.SMTPSendFailedException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import javax.mail.AuthenticationFailedException;
import javax.mail.MessagingException;
import javax.mail.SendFailedException;
import org.junit.jupiter.api.Test;

public class JavaMailErrorsTest {
    @Test
    void authenticationFailureIsPermanent() {
        MailException e = JavaMailErrors.classify(new AuthenticationFailedException("535 bad credentials"));
        assertFalse(e.isTransientError());
    }

    @Test
    void smtp4xxIsTransientAnd5xxIsPermanent() {
        MailException busy = JavaMailErrors.classify(
                new SMTPSendFailedException("RCPT", 450, "Mailbox busy", null, null, null, null));
        assertTrue(busy.isTransientError());
        assertEquals(450, busy.getReplyCode());

        MailException missing = JavaMailErrors.classify(
                new SMTPSendFailedException("RCPT", 550, "No such user", null, null, null, null));
        assertFalse(missing.isTransientError());
        assertEquals(550, missing.getReplyCode());
    }

    @Test
    void otherSendFailureIsPermanent() {
        assertFalse(JavaMailErrors.classify(new SendFailedException("invalid address")).isTransientError());
    }

    @Test
    void networkCausesAreTransient() {
        assertTrue(JavaMailErrors.classify(
                new MessagingException("Could not connect", new SocketTimeoutException("t"))).isTransientError());
        assertTrue(JavaMailErrors.classify(
                new MessagingException("Unknown host", new UnknownHostException("smtp.gmail.com"))).isTransientError());
    }

    @Test
    void unknownErrorIsPermanentAndMailExceptionIsPassedThrough() {
        assertFalse(JavaMailErrors.classify(new MessagingException("lạ")).isTransientError());
        MailException original = new MailException("x", 421, true);
        assertSame(original, JavaMailErrors.classify(original));
    }
}
```

`test/JavaMailServiceTest.java`:

```java
import static org.junit.jupiter.api.Assertions.*;

import java.util.Properties;
import org.junit.jupiter.api.Test;

public class JavaMailServiceTest {
    @Test
    void sslConfigEnablesSslAndAuth() {
        AccountConfig cfg = AccountConfig.gmail("u@gmail.com", "pw");
        Properties p = JavaMailService.smtpProperties(cfg);
        assertEquals("smtp.gmail.com", p.getProperty("mail.smtp.host"));
        assertEquals("465", p.getProperty("mail.smtp.port"));
        assertEquals("true", p.getProperty("mail.smtp.auth"));
        assertEquals("true", p.getProperty("mail.smtp.ssl.enable"));
        assertNull(p.getProperty("mail.smtp.starttls.enable"));
        assertEquals("true", p.getProperty("mail.smtp.ssl.checkserveridentity"));
        assertEquals("true", p.getProperty("mail.mime.encodefilename"));
    }

    @Test
    void starttlsConfigRequiresStarttls() {
        AccountConfig cfg = AccountConfig.gmail("u@gmail.com", "pw");
        cfg.smtpPort = 587;
        cfg.smtpSecurity = Security.STARTTLS;
        Properties p = JavaMailService.smtpProperties(cfg);
        assertEquals("587", p.getProperty("mail.smtp.port"));
        assertEquals("true", p.getProperty("mail.smtp.starttls.enable"));
        assertEquals("true", p.getProperty("mail.smtp.starttls.required"));
        assertNull(p.getProperty("mail.smtp.ssl.enable"));
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `test.bat JavaMailErrorsTest`
Expected: FAIL, `cannot find symbol ... class JavaMailErrors`. Nếu báo thiếu constructor `SMTPSendFailedException(String,int,String,Exception,Address[],Address[],Address[])` thì kiểm tra jar `javax.mail-1.6.2.jar` đúng phiên bản trong `lib/`.

- [ ] **Step 3: Write minimal implementation**

`src/JavaMailErrors.java`:

```java
import com.sun.mail.smtp.SMTPSendFailedException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import javax.mail.AuthenticationFailedException;
import javax.mail.SendFailedException;

public final class JavaMailErrors {
    private JavaMailErrors() {
    }

    public static MailException classify(Exception e) {
        if (e instanceof MailException) {
            return (MailException) e;
        }
        if (e instanceof AuthenticationFailedException) {
            return new MailException("Sai tài khoản hoặc App Password: " + e.getMessage(), 535, false, e);
        }
        if (e instanceof SMTPSendFailedException) {
            int code = ((SMTPSendFailedException) e).getReturnCode();
            return new MailException("SMTP " + code + ": " + e.getMessage(), code, ReplyCode.isTransient(code), e);
        }
        if (e instanceof SendFailedException) {
            return new MailException("Gửi thất bại: " + e.getMessage(), -1, false, e);
        }
        if (hasNetworkCause(e)) {
            return new MailException("Lỗi mạng: " + e.getMessage(), -1, true, e);
        }
        return new MailException(String.valueOf(e.getMessage()), -1, false, e);
    }

    private static boolean hasNetworkCause(Throwable t) {
        for (Throwable cur = t; cur != null; cur = cur.getCause()) {
            if (cur instanceof SocketException || cur instanceof SocketTimeoutException
                    || cur instanceof UnknownHostException) {
                return true;
            }
        }
        return false;
    }
}
```

`src/JavaMailService.java`:

```java
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import javax.mail.Authenticator;
import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Store;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;

/** Gửi/nhận bằng JavaMail API (mục 10), có log và retry. */
public class JavaMailService implements MailService {
    private final AccountConfig cfg;
    private final MailLog log;
    private final Retrier retrier;

    public JavaMailService(AccountConfig cfg, MailLog log, Retrier retrier) {
        this.cfg = cfg;
        this.log = log;
        this.retrier = retrier;
    }

    static Properties smtpProperties(AccountConfig cfg) {
        Properties props = new Properties();
        props.put("mail.smtp.host", cfg.smtpHost);
        props.put("mail.smtp.port", String.valueOf(cfg.smtpPort));
        props.put("mail.smtp.auth", "true");
        if (cfg.smtpSecurity == Security.SSL) {
            props.put("mail.smtp.ssl.enable", "true");
        } else if (cfg.smtpSecurity == Security.STARTTLS) {
            props.put("mail.smtp.starttls.enable", "true");
            props.put("mail.smtp.starttls.required", "true");
        }
        props.put("mail.smtp.ssl.checkserveridentity", "true");
        props.put("mail.smtp.connectiontimeout", "15000");
        props.put("mail.smtp.timeout", "15000");
        props.put("mail.mime.encodefilename", "true");
        return props;
    }

    @Override
    public void send(OutgoingMail mail) throws MailException {
        retrier.run("JavaMail SMTP", () -> {
            doSend(mail);
            return null;
        });
    }

    private void doSend(OutgoingMail mail) throws MailException {
        log.info("JavaMail SMTP: gửi tới " + mail.to + " qua " + cfg.smtpHost + ":" + cfg.smtpPort);
        Session session = Session.getInstance(smtpProperties(cfg), new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(cfg.username, cfg.password);
            }
        });
        try {
            MimeMessage message = new MimeMessage(session);
            message.setFrom(new InternetAddress(cfg.username));
            for (String to : mail.to) {
                message.addRecipient(Message.RecipientType.TO, new InternetAddress(to));
            }
            message.setSubject(mail.subject, "UTF-8");

            MimeBodyPart bodyPart = new MimeBodyPart();
            if (mail.html) {
                bodyPart.setContent(mail.body, "text/html; charset=utf-8");
            } else {
                bodyPart.setText(mail.body, "UTF-8");
            }
            Multipart multipart = new MimeMultipart();
            multipart.addBodyPart(bodyPart);
            for (File file : mail.attachments) {
                MimeBodyPart attachment = new MimeBodyPart();
                attachment.attachFile(file);
                multipart.addBodyPart(attachment);
            }
            message.setContent(multipart);

            Transport.send(message);
            log.info("JavaMail SMTP: gửi thành công");
        } catch (MessagingException | IOException e) {
            throw JavaMailErrors.classify(e);
        }
    }

    @Override
    public List<ReceivedMail> fetchInbox(ReceiveProtocol protocol, int limit) throws MailException {
        return retrier.run("JavaMail " + protocol, () -> doFetch(protocol, limit));
    }

    private List<ReceivedMail> doFetch(ReceiveProtocol protocol, int limit) throws MailException {
        boolean imap = protocol == ReceiveProtocol.IMAP;
        Security security = imap ? cfg.imapSecurity : cfg.pop3Security;
        String storeName = (imap ? "imap" : "pop3") + (security == Security.SSL ? "s" : "");
        String host = imap ? cfg.imapHost : cfg.pop3Host;
        int port = imap ? cfg.imapPort : cfg.pop3Port;

        Properties props = new Properties();
        props.put("mail." + storeName + ".ssl.checkserveridentity", "true");
        props.put("mail." + storeName + ".connectiontimeout", "15000");
        props.put("mail." + storeName + ".timeout", "15000");
        log.info("JavaMail " + storeName + ": kết nối " + host + ":" + port);

        try (Store store = Session.getInstance(props).getStore(storeName)) {
            store.connect(host, port, cfg.username, cfg.password);
            Folder inbox = store.getFolder("INBOX");
            inbox.open(Folder.READ_ONLY);
            int total = inbox.getMessageCount();
            List<ReceivedMail> mails = new ArrayList<>();
            for (int i = total; i > Math.max(0, total - limit); i--) {
                mails.add(MailParser.fromMessage(String.valueOf(i), inbox.getMessage(i)));
            }
            inbox.close(false);
            log.info("JavaMail " + storeName + ": đã tải " + mails.size() + " thư");
            return mails;
        } catch (MessagingException | IOException e) {
            throw JavaMailErrors.classify(e);
        }
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `test.bat JavaMailErrorsTest` rồi `test.bat JavaMailServiceTest`
Expected: PASS, `5 tests successful` và `2 tests successful`. Gửi/nhận thật bằng JavaMail kiểm ở Task 14.

- [ ] **Step 5: Commit**

```bash
git add src/JavaMailErrors.java src/JavaMailService.java test/JavaMailErrorsTest.java test/JavaMailServiceTest.java
git commit -m "feat: JavaMailService (HTML, attachments, log, retry) and error classification"
```

---

### Task 12: `SocketMailService` + `MailServiceFactory`

**Files:**
- Create: `src/SocketMailService.java`, `src/MailServiceFactory.java`
- Test: `test/SocketMailServiceTest.java`, `test/MailServiceFactoryTest.java`

**Interfaces:**
- Consumes: `SmtpSocketClient.send`, `Pop3SocketClient.fetchLatest`, `ImapSocketClient.fetchLatest`, `Retrier.run`, `JavaMailService(AccountConfig, MailLog, Retrier)`, `Engine`
- Produces:
  - `class SocketMailService implements MailService` — `SocketMailService(AccountConfig cfg, MailLog log, Retrier retrier)`; mỗi lần thử tạo client mới (mỗi lần thử một kết nối mới)
  - `final class MailServiceFactory` — `static MailService create(Engine engine, AccountConfig cfg, MailLog log)` dùng `Retrier.standard(log)`

- [ ] **Step 1: Write the failing tests**

`test/SocketMailServiceTest.java`:

```java
import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

public class SocketMailServiceTest {
    @Test
    void sendGoesThroughSmtpClient() throws Exception {
        AccountConfig cfg = new AccountConfig("u@test.com", "secret");
        cfg.smtpHost = "localhost";
        cfg.smtpSecurity = Security.NONE;
        try (ScriptedServer server = new ScriptedServer(
                "S: 220 hi", "C: EHLO *", "S: 250 ok",
                "C: AUTH LOGIN", "S: 334 x", "C: *", "S: 334 y", "C: *", "S: 235 ok",
                "C: MAIL FROM:*", "S: 250 ok", "C: RCPT TO:*", "S: 250 ok",
                "C: DATA", "S: 354 go", "DATA", "S: 250 queued",
                "C: QUIT", "S: 221 bye")) {
            cfg.smtpPort = server.port();
            MailService service = new SocketMailService(cfg, new MailLog(null), new Retrier(1, 1, ms -> { }, new MailLog(null)));
            service.send(new OutgoingMail(Arrays.asList("a@test.com"), "S", "B", false, Collections.<File>emptyList()));
            server.assertCompleted();
        }
    }

    @Test
    void fetchInboxDispatchesByProtocol() throws Exception {
        AccountConfig cfg = new AccountConfig("u@test.com", "secret");
        cfg.pop3Host = "localhost";
        cfg.pop3Security = Security.NONE;
        try (ScriptedServer server = new ScriptedServer(
                "S: +OK ready", "C: USER *", "S: +OK", "C: PASS *", "S: +OK",
                "C: STAT", "S: +OK 1 10",
                "C: RETR 1", "S: +OK", "S: Subject: P", "S: ", "S: x", "S: .",
                "C: QUIT", "S: +OK bye")) {
            cfg.pop3Port = server.port();
            MailService service = new SocketMailService(cfg, new MailLog(null), new Retrier(1, 1, ms -> { }, new MailLog(null)));
            List<ReceivedMail> mails = service.fetchInbox(ReceiveProtocol.POP3, 5);
            server.assertCompleted();
            assertEquals("P", mails.get(0).subject);
        }
    }
}
```

`test/MailServiceFactoryTest.java`:

```java
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

public class MailServiceFactoryTest {
    @Test
    void createsServiceMatchingEngine() {
        AccountConfig cfg = AccountConfig.gmail("u@gmail.com", "pw");
        MailLog log = new MailLog(null);
        assertTrue(MailServiceFactory.create(Engine.SOCKET, cfg, log) instanceof SocketMailService);
        assertTrue(MailServiceFactory.create(Engine.JAVAMAIL, cfg, log) instanceof JavaMailService);
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `test.bat SocketMailServiceTest`
Expected: FAIL, `cannot find symbol ... class SocketMailService`.

- [ ] **Step 3: Write minimal implementation**

`src/SocketMailService.java`:

```java
import java.util.List;

/** Gửi/nhận bằng Socket thuần (SMTP/POP3/IMAP tự viết), có log và retry. */
public class SocketMailService implements MailService {
    private final AccountConfig cfg;
    private final MailLog log;
    private final Retrier retrier;

    public SocketMailService(AccountConfig cfg, MailLog log, Retrier retrier) {
        this.cfg = cfg;
        this.log = log;
        this.retrier = retrier;
    }

    @Override
    public void send(OutgoingMail mail) throws MailException {
        retrier.run("Socket SMTP", () -> {
            new SmtpSocketClient(cfg, log).send(mail);
            return null;
        });
    }

    @Override
    public List<ReceivedMail> fetchInbox(ReceiveProtocol protocol, int limit) throws MailException {
        return retrier.run("Socket " + protocol, () -> protocol == ReceiveProtocol.POP3
                ? new Pop3SocketClient(cfg, log).fetchLatest(limit)
                : new ImapSocketClient(cfg, log).fetchLatest(limit));
    }
}
```

`src/MailServiceFactory.java`:

```java
public final class MailServiceFactory {
    private MailServiceFactory() {
    }

    public static MailService create(Engine engine, AccountConfig cfg, MailLog log) {
        Retrier retrier = Retrier.standard(log);
        return engine == Engine.SOCKET
                ? new SocketMailService(cfg, log, retrier)
                : new JavaMailService(cfg, log, retrier);
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `test.bat` (chạy toàn bộ test)
Expected: PASS, tất cả test xanh, `0 tests failed`.

- [ ] **Step 5: Commit**

```bash
git add src/SocketMailService.java src/MailServiceFactory.java test/SocketMailServiceTest.java test/MailServiceFactoryTest.java
git commit -m "feat: SocketMailService and MailServiceFactory"
```

---

### Task 13: Giao diện Swing + `EmailClientApp`

GUI chỉ gọi `MailService` trong `SwingWorker` (không treo giao diện), không có logic giao thức. Phần này kiểm bằng cách chạy tay ở Task 14.

**Files:**
- Create: `src/MainFrame.java`, `src/EmailClientApp.java`

**Interfaces:**
- Consumes: `MailService`, `MailServiceFactory.create`, `AccountConfig.gmail`, `OutgoingMail(List<String>, String, String, boolean, List<File>)`, `OutgoingMail.splitRecipients`, `ReceivedMail` (các field public), `Engine`, `ReceiveProtocol`, `MailLog.addListener/error`
- Produces: `class MainFrame extends JFrame` — `MainFrame(MailLog log)`; `class EmailClientApp` — `public static void main(String[] args)` (khớp mục 10: `java -cp "bin;lib/*" EmailClientApp`)

- [ ] **Step 1: Viết `MainFrame`**

`src/MainFrame.java`:

```java
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.table.DefaultTableModel;

public class MainFrame extends JFrame {
    private static final int FETCH_LIMIT = 20;

    private final MailLog log;

    private final JTextField emailField = new JTextField(20);
    private final JPasswordField passwordField = new JPasswordField(16);
    private final JComboBox<Engine> engineBox = new JComboBox<>(Engine.values());

    private final JTextField toField = new JTextField();
    private final JTextField subjectField = new JTextField();
    private final JTextArea bodyArea = new JTextArea(12, 50);
    private final JCheckBox htmlBox = new JCheckBox("Nội dung là HTML");
    private final DefaultListModel<File> attachModel = new DefaultListModel<>();
    private final JList<File> attachList = new JList<>(attachModel);
    private final JButton sendButton = new JButton("Gửi thư");

    private final JComboBox<ReceiveProtocol> protocolBox = new JComboBox<>(ReceiveProtocol.values());
    private final JButton refreshButton = new JButton("Tải hộp thư");
    private final DefaultTableModel inboxModel = new DefaultTableModel(
            new Object[]{"#", "Từ", "Tiêu đề", "Ngày"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTable inboxTable = new JTable(inboxModel);
    private final JTextArea detailArea = new JTextArea();
    private final JTextArea logArea = new JTextArea(7, 80);
    private List<ReceivedMail> inbox = new ArrayList<>();

    public MainFrame(MailLog log) {
        super("Mail Client Java - Lập Trình Mạng");
        this.log = log;
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout(6, 6));
        add(buildAccountPanel(), BorderLayout.NORTH);
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Soạn thư", buildComposePanel());
        tabs.addTab("Hộp thư đến", buildInboxPanel());
        add(tabs, BorderLayout.CENTER);
        add(buildLogPanel(), BorderLayout.SOUTH);
        log.addListener(line -> SwingUtilities.invokeLater(() -> logArea.append(line + "\n")));
        pack();
        setLocationRelativeTo(null);
    }

    private JPanel buildAccountPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        panel.add(new JLabel("Gmail:"));
        panel.add(emailField);
        panel.add(new JLabel("App Password:"));
        panel.add(passwordField);
        panel.add(new JLabel("Cách làm:"));
        panel.add(engineBox);
        return panel;
    }

    private JPanel buildComposePanel() {
        JPanel panel = new JPanel(new BorderLayout(6, 6));
        JPanel header = new JPanel(new GridLayout(2, 2, 4, 4));
        header.add(new JLabel("Đến (cách nhau bởi , hoặc ;):"));
        header.add(toField);
        header.add(new JLabel("Tiêu đề:"));
        header.add(subjectField);
        panel.add(header, BorderLayout.NORTH);

        bodyArea.setLineWrap(true);
        bodyArea.setWrapStyleWord(true);
        panel.add(new JScrollPane(bodyArea), BorderLayout.CENTER);

        JButton addButton = new JButton("Thêm file...");
        JButton removeButton = new JButton("Bỏ file đã chọn");
        addButton.addActionListener(e -> onAddAttachments());
        removeButton.addActionListener(e -> {
            for (File f : attachList.getSelectedValuesList()) {
                attachModel.removeElement(f);
            }
        });
        sendButton.addActionListener(e -> onSend());
        attachList.setVisibleRowCount(3);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttons.add(addButton);
        buttons.add(removeButton);
        buttons.add(htmlBox);
        buttons.add(sendButton);
        JPanel south = new JPanel(new BorderLayout(4, 4));
        south.add(new JScrollPane(attachList), BorderLayout.CENTER);
        south.add(buttons, BorderLayout.SOUTH);
        panel.add(south, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel buildInboxPanel() {
        JPanel panel = new JPanel(new BorderLayout(6, 6));
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        top.add(new JLabel("Giao thức:"));
        top.add(protocolBox);
        top.add(refreshButton);
        refreshButton.addActionListener(e -> onRefresh());
        panel.add(top, BorderLayout.NORTH);

        inboxTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                showSelected();
            }
        });
        detailArea.setEditable(false);
        detailArea.setLineWrap(true);
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                new JScrollPane(inboxTable), new JScrollPane(detailArea));
        split.setResizeWeight(0.4);
        panel.add(split, BorderLayout.CENTER);
        panel.setPreferredSize(new java.awt.Dimension(760, 360));
        return panel;
    }

    private JScrollPane buildLogPanel() {
        logArea.setEditable(false);
        return new JScrollPane(logArea);
    }

    private MailService currentService() {
        String email = emailField.getText().trim();
        String password = new String(passwordField.getPassword()).replace(" ", "");
        if (email.isEmpty() || password.isEmpty()) {
            throw new IllegalArgumentException("Nhập Gmail và App Password trước");
        }
        AccountConfig cfg = AccountConfig.gmail(email, password);
        return MailServiceFactory.create((Engine) engineBox.getSelectedItem(), cfg, log);
    }

    private void onAddAttachments() {
        JFileChooser chooser = new JFileChooser(new File("attachments"));
        chooser.setMultiSelectionEnabled(true);
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            for (File f : chooser.getSelectedFiles()) {
                attachModel.addElement(f);
            }
        }
    }

    private void onSend() {
        final MailService service;
        final OutgoingMail mail;
        try {
            service = currentService();
            mail = new OutgoingMail(OutgoingMail.splitRecipients(toField.getText()),
                    subjectField.getText(), bodyArea.getText(), htmlBox.isSelected(),
                    Collections.list(attachModel.elements()));
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Thiếu thông tin", JOptionPane.WARNING_MESSAGE);
            return;
        }
        sendButton.setEnabled(false);
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                service.send(mail);
                return null;
            }

            @Override
            protected void done() {
                sendButton.setEnabled(true);
                try {
                    get();
                    JOptionPane.showMessageDialog(MainFrame.this, "Đã gửi thư thành công");
                } catch (InterruptedException | ExecutionException ex) {
                    showError(ex);
                }
            }
        }.execute();
    }

    private void onRefresh() {
        final MailService service;
        try {
            service = currentService();
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Thiếu thông tin", JOptionPane.WARNING_MESSAGE);
            return;
        }
        final ReceiveProtocol protocol = (ReceiveProtocol) protocolBox.getSelectedItem();
        refreshButton.setEnabled(false);
        new SwingWorker<List<ReceivedMail>, Void>() {
            @Override
            protected List<ReceivedMail> doInBackground() throws Exception {
                return service.fetchInbox(protocol, FETCH_LIMIT);
            }

            @Override
            protected void done() {
                refreshButton.setEnabled(true);
                try {
                    inbox = get();
                    inboxModel.setRowCount(0);
                    for (ReceivedMail m : inbox) {
                        inboxModel.addRow(new Object[]{m.id, m.from, m.subject, m.date});
                    }
                    detailArea.setText("");
                } catch (InterruptedException | ExecutionException ex) {
                    showError(ex);
                }
            }
        }.execute();
    }

    private void showSelected() {
        int row = inboxTable.getSelectedRow();
        if (row < 0 || row >= inbox.size()) {
            return;
        }
        ReceivedMail m = inbox.get(row);
        StringBuilder sb = new StringBuilder();
        sb.append("Từ: ").append(m.from).append('\n');
        sb.append("Tiêu đề: ").append(m.subject).append('\n');
        sb.append("Ngày: ").append(m.date).append('\n');
        if (!m.attachmentNames.isEmpty()) {
            sb.append("Đính kèm: ").append(String.join(", ", m.attachmentNames)).append('\n');
        }
        if (m.html) {
            sb.append("[Thư HTML, hiển thị mã nguồn để tránh tải nội dung từ bên ngoài]\n");
        }
        sb.append('\n').append(m.body);
        detailArea.setText(sb.toString());
        detailArea.setCaretPosition(0);
    }

    private void showError(Throwable t) {
        Throwable cause = (t instanceof ExecutionException && t.getCause() != null) ? t.getCause() : t;
        String message = String.valueOf(cause.getMessage());
        log.error(message);
        JOptionPane.showMessageDialog(this, message, "Lỗi", JOptionPane.ERROR_MESSAGE);
    }
}
```

- [ ] **Step 2: Viết `EmailClientApp`**

`src/EmailClientApp.java`:

```java
import java.nio.file.Paths;
import javax.swing.SwingUtilities;

public class EmailClientApp {
    public static void main(String[] args) {
        MailLog log = new MailLog(Paths.get("logs", "mail-client.log"));
        SwingUtilities.invokeLater(() -> new MainFrame(log).setVisible(true));
    }
}
```

- [ ] **Step 3: Biên dịch**

Run: `build.bat`
Expected: không có lỗi biên dịch (không in gì). Nếu lỗi `cannot find symbol` thì đối chiếu tên field/method với mục Interfaces của Task 5, 11, 12.

- [ ] **Step 4: Chạy thử giao diện**

Run: `run.bat`
Expected: cửa sổ "Mail Client Java - Lập Trình Mạng" hiện lên, có ô Gmail, App Password, combo "Cách làm" (SOCKET/JAVAMAIL), 2 tab "Soạn thư" và "Hộp thư đến", khung log ở dưới. Bấm "Gửi thư" khi chưa nhập gì thì hiện hộp thoại "Nhập Gmail và App Password trước".

- [ ] **Step 5: Chạy lại toàn bộ test rồi commit**

Run: `test.bat`
Expected: tất cả test xanh.

```bash
git add src/MainFrame.java src/EmailClientApp.java
git commit -m "feat: Swing GUI for sending and receiving mail"
```

---

### Task 14: Kiểm thử thật với Gmail và tài liệu chạy

**Files:**
- Create: `README.md`

**Interfaces:**
- Consumes: toàn bộ ứng dụng
- Produces: bảng kiểm thử tay đã tick, `README.md` hướng dẫn cài và chạy.

- [ ] **Step 1: Chuẩn bị Gmail (làm một lần)**

1. Bật Xác minh 2 bước cho tài khoản Google.
2. Tạo **App Password** (16 ký tự) trong phần bảo mật của tài khoản Google. Có thể gõ có hoặc không có dấu cách, ứng dụng tự bỏ dấu cách.
3. Gmail → Cài đặt → Xem tất cả cài đặt → **Chuyển tiếp và POP/IMAP**: bật **IMAP**, bật **POP** (Enable POP for all mail), lưu thay đổi.

- [ ] **Step 2: Chạy ma trận kiểm thử tay**

Run: `run.bat`, nhập Gmail + App Password. Gửi thư tới chính địa chỉ Gmail đó (tiêu đề tiếng Việt, ví dụ `Báo cáo doanh thu tháng 8`), nội dung có một dòng bắt đầu bằng dấu chấm, đính kèm `attachments/report.txt` và một file thứ hai.

| # | Cách làm | Hành động | Kết quả mong đợi | Tick |
|---|---|---|---|---|
| 1 | SOCKET | Gửi text + 2 file | Thư tới hộp thư, tiêu đề đúng tiếng Việt, đủ 2 đính kèm, dòng bắt đầu bằng `.` còn nguyên | [ ] |
| 2 | SOCKET | Gửi HTML (tick "Nội dung là HTML") | Gmail hiển thị đúng định dạng HTML | [ ] |
| 3 | JAVAMAIL | Gửi text + 2 file | Giống mục 1 | [ ] |
| 4 | JAVAMAIL | Gửi HTML | Giống mục 2 | [ ] |
| 5 | SOCKET + POP3 | Tải hộp thư | Hiện tối đa 20 thư mới nhất, bấm một thư thấy nội dung và tên đính kèm | [ ] |
| 6 | SOCKET + IMAP | Tải hộp thư | Giống mục 5 | [ ] |
| 7 | JAVAMAIL + POP3 | Tải hộp thư | Giống mục 5 | [ ] |
| 8 | JAVAMAIL + IMAP | Tải hộp thư | Giống mục 5 | [ ] |
| 9 | SOCKET | Nhập sai App Password rồi gửi | Hộp thoại lỗi 535, log **không** có dòng "thử lại" (lỗi vĩnh viễn, không retry) | [ ] |
| 10 | SOCKET | Rút mạng rồi gửi | Log hiện "lỗi tạm thời (lần 1/3)", "(lần 2/3)", sau đó báo lỗi (có retry) | [ ] |
| 11 | Cả hai | Mở `logs/mail-client.log` | Có đủ dòng `C:` / `S:`, **không** có App Password và không có dạng Base64 của nó | [ ] |

Nếu mục 1 hoặc 5 báo lỗi SSL/handshake, kiểm tra đồng hồ máy đúng giờ và đang không dùng proxy/antivirus chặn TLS.

- [ ] **Step 3: Viết `README.md`**

````markdown
# Mail Client Java (Socket + JavaMail)

Mail Client có giao diện Swing, gửi/nhận Gmail bằng Java Socket thuần và JavaMail, nhận bằng POP3 hoặc IMAP.

## Cài đặt
1. JDK 8 trở lên.
2. `setup-libs.bat` để tải `javax.mail`, `activation`, `junit` vào `lib/`.
3. Tạo App Password cho Gmail và bật POP/IMAP (xem Task 14 của plan).

## Chạy
- `run.bat` mở ứng dụng. Nhập Gmail, App Password, chọn "Cách làm" (SOCKET hoặc JAVAMAIL).
- Lệnh tương đương mục 10: `javac -cp ".;lib/*" -d bin src/*.java` rồi `java -cp "bin;lib/*" EmailClientApp`.
- Linux/macOS: đổi `;` thành `:`.

## Test
- `test.bat` chạy toàn bộ test, `test.bat TênClassTest` chạy một class.

## Log
- `logs/mail-client.log`. Mật khẩu không bao giờ được ghi vào log.
````

- [ ] **Step 4: Chạy lại toàn bộ test lần cuối**

Run: `test.bat`
Expected: tất cả test xanh, `0 tests failed`.

- [ ] **Step 5: Commit**

```bash
git add README.md
git commit -m "docs: README and Gmail manual test matrix"
```

---

## Self-Review

**1. Spec coverage** (đối chiếu `MAIL-Java.md`):

| Mục trong file | Task |
|---|---|
| 1 Mục tiêu (Socket, I/O, SSL/TLS, javax.mail, UTF-8, Base64, retry, App Password) | 7, 8, 11, 14 |
| 2, 3 Kiến trúc, bảng giao thức và cổng | Global Constraints, `AccountConfig` (Task 5) |
| 4 SMTP: lệnh, CRLF, quy trình 8 bước | Task 8 |
| 5 SMTP client Socket: AUTH LOGIN Base64, DATA, dot-stuffing | Task 7, 8 |
| 6 SSL/TLS: 465 implicit, 587 STARTTLS | Task 8 (`LineConnection.startTls`, `Security`) |
| 7 MIME: multipart/mixed, Base64 76 ký tự | Task 7 |
| 8 POP3 | Task 9 |
| 8 IMAP (mục lục ghi, thân bài không có code) | Task 10 |
| 9 Reply code, retry, backoff | Task 2, 3, 11 |
| 10 JavaMail HTML + attachment + Log + Retry, cấu trúc thư mục, lệnh javac/java | Task 1, 11, 13, 14 |
| Mục lục mục 7 "nhiều file & thư mục (MIME lồng nhau)" | Nhiều file: Task 7. Thư mục: ngoài phạm vi, có báo lỗi rõ ở `OutgoingMail` (Task 5) |

Không còn yêu cầu nào chưa có task, ngoài mục thư mục đã nêu ở trên.

**2. Placeholder scan:** không có "TBD/TODO/implement later"; mọi step code đều có code đầy đủ và lệnh chạy kèm kết quả mong đợi.

**3. Type consistency:** đã đối chiếu chữ ký giữa các task: `MailException(String,int,boolean[,Throwable])`, `Retrier.run(String, Action<T>)`, `ReceivedMail(id, from, subject, date, body, html, attachmentNames)`, `AccountConfig` field `smtp*/pop3*/imap*`, `MailService.fetchInbox(ReceiveProtocol,int)`, `SmtpSocketClient.send`, `Pop3SocketClient/ImapSocketClient.fetchLatest(int)`, `JavaMailService(AccountConfig, MailLog, Retrier)` đều khớp giữa nơi định nghĩa và nơi dùng (Task 11, 12, 13).

**Quyết định tôi tự đặt, cần bạn xác nhận hoặc đổi:**
1. Thêm JUnit 5 (một jar `junit-platform-console-standalone`) để làm TDD, vẫn build bằng `javac` + `lib/*.jar`.
2. Text trong thư gửi bằng Socket luôn mã hóa base64 (thay vì 8bit như file) để toàn bộ thư là ASCII.
3. Dùng `MimeUtility.encodeText` (JavaMail) để mã hóa header tiếng Việt, và `MimeMessage` để parse thư nhận (cả đường Socket), vì viết tay phần này không phải trọng tâm bài.
4. Thư HTML nhận về hiển thị dạng mã nguồn, không render, để tránh tải ảnh/tracking từ bên ngoài.
5. Đính kèm thư mục không làm (xem Ngoài phạm vi).
