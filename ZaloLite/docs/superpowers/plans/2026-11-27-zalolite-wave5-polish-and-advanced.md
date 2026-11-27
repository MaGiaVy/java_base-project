# ZaloLite Wave 5 (Polish + Advanced Network Features) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Hoàn thiện `ZaloLite Berry Pop` đạt trải nghiệm gần nhất với Zalo PC thật bằng cách bổ sung 6 tiện ích cốt lõi (Reply, Forward, Hồ sơ/Đổi mật khẩu, Chuông báo, Drag&Drop + `Ctrl+V` ảnh, Chặn & đếm thành viên nhóm) và 4 hệ thống nâng cao tái dùng trực tiếp kiến thức môn học (OTP Gmail SMTP từ `MailClient`, Link Preview HTTP/1.1 Socket từ `Chuong12`, Gọi thoại 1-1 realtime qua UDP, và Cloud của tôi).

**Architecture:** Giữ nguyên kiến trúc 3 module Maven (`common`, `server`, `client`), mọi báo hiệu và dữ liệu chat đi qua TLS TCP `:9000`, file/media qua TCP `:9001` hoặc P2P TCP, tìm server qua UDP Multicast `:9009`, và bổ sung thêm: SMTP Socket (cổng 465 SSL / 587 STARTTLS) cho OTP, HTTP/1.1 Socket (`Socket`/`SSLSocket`) cho Link Preview, và UDP PCM 16kHz/16-bit mono trực tiếp giữa 2 client cho gọi thoại 1-1.

**Tech Stack:** Java 17+ (JDK 24), Maven Wrapper (`mvnw.cmd`), Jackson, jBCrypt, PostgreSQL JDBC, FlatLaf 3.5.2, Swing, `javax.sound.sampled`, `java.net.Socket`/`SSLSocket`/`DatagramSocket`, JUnit 5.

**Spec:** Kế hoạch nền `zalo_plan_v2.md` + quyết định chốt với user qua `/grill-me` ngày 2026-11-27.

## Global Constraints
- **Socket thuần:** Không thêm thư viện HTTP client, WebSocket, JavaMail hay WebRTC ngoài; SMTP và HTTP Link Preview tự viết bằng `Socket`/`SSLSocket`, gọi thoại bằng `DatagramSocket`.
- **Giao diện Berry Pop:** Toàn bộ dialog/card mới dùng `Theme.java` (`#5B21B6`, `#E11D48`, `#F3E8FF`, `#FFF9FB`, `Theme.emojiFont`).
- **Không chặn EDT:** Mọi thao tác mạng, DB, HTTP fetch, SMTP, UDP audio, đọc ảnh đều chạy nền (`Theme.runAsync` hoặc thread daemon).
- **An toàn demo không cần Internet/Mic:** OTP tự in console server khi `mail.*` trống; Link Preview timeout 2s không làm lỗi tin nhắn chữ; Voice Call đóng `DatagramSocket`/`TargetDataLine`/`SourceDataLine` sạch sẽ trong `finally` kể cả khi bị ngắt đột ngột.
- **Bảo mật:** Không log mật khẩu rõ hay `mail.appPassword`; `server/server.properties` luôn nằm trong `.gitignore`; mọi lệnh cần quyền (đổi mật khẩu, chặn, reply, gọi thoại) đều kiểm tra phía server.
- **Build & Test:** Mọi task kết thúc bằng `.\mvnw.cmd -B test` xanh trước khi commit.

## Review Focus
1. **Chặn 2 chiều khi gọi thoại & nhắn tin:** Nếu A chặn B, cả B nhắn cho A lẫn B gọi thoại cho A đều phải bị server từ chối rõ ràng, đồng thời A vẫn mở xem lại lịch sử cũ và bỏ chặn được. *(Pinned in Task 4 & Task 9)*
2. **OTP hết hạn / dùng lại / sai email:** Mã OTP quá 5 phút, nhập sai mã, hoặc dùng lại mã đã đổi mật khẩu phải bị từ chối; không tiết lộ mật khẩu cũ. *(Pinned in Task 3)*
3. **Link Preview chống treo & chống SSRF cục bộ:** URL trỏ về `localhost`/`127.0.0.1`/`192.168.*` hoặc trang treo quá 2s hoặc redirect vòng lặp (>2 lần) phải dừng an toàn mà không làm đơ UI hay văng exception. *(Pinned in Task 8)*
4. **Dọn dẹp tài nguyên UDP Voice Call:** Khi một bên đóng cửa sổ ngang hoặc mất kết nối TLS lúc đang gọi, luồng gửi/nhận UDP và thiết bị âm thanh (`TargetDataLine`/`SourceDataLine`) phải tắt hẳn, không kẹt mic cho lần gọi sau. *(Pinned in Task 9)*
5. **Cloud của tôi không hiện trùng lặp & không bị rời/xoá:** Mỗi tài khoản chỉ có đúng 1 hội thoại `☁️ Cloud của tôi` tự tạo khi lấy danh sách hội thoại, luôn ghim đầu danh sách, gửi media đi thẳng qua Server `:9001` (không cố mở P2P với chính mình). *(Pinned in Task 5)*

---

## Bản đồ file & thứ tự thực hiện

| Task | Module | File tạo mới / chỉnh sửa | Hệ thống cũ tái dùng |
|---|---|---|---|
| **Task 1** | `common` | Sửa `PacketType.java`, `Message.java`, `Conversation.java`, `PacketTest.java` | Hợp đồng JSON |
| **Task 2** | `server`, `client` | Sửa `UserDao.java`, `ClientHandler.java`, `MainFrame.java`; Tạo `ProfileDialog.java` | BCrypt + Swing |
| **Task 3** | `server`, `client` | Tạo `OtpDao.java`, `MailOtpService.java`, `MailOtpServiceTest.java`, `ForgotPasswordDialog.java`; Sửa `ZaloServer.java`, `ClientHandler.java`, `LoginFrame.java` | **`MailClient`** (ESMTP 8 bước) |
| **Task 4** | `server`, `client` | Sửa `FriendDao.java`, `ClientHandler.java`, `FriendPanel.java`, `ServerIntegrationTest.java` | PostgreSQL DAO |
| **Task 5** | `server`, `client` | Sửa `ConversationDao.java`, `ClientHandler.java`, `ChatPanel.java`, `ServerIntegrationTest.java` | PostgreSQL DAO |
| **Task 6** | `server`, `client` | Sửa `MessageDao.java`, `ConversationDao.java`, `ClientHandler.java`, `ChatPanel.java`, `MessageBubble.java`, `ServerIntegrationTest.java` | TCP/TLS + Swing |
| **Task 7** | `client` | Sửa `MainFrame.java`, `ChatPanel.java` | Swing Clipboard & DnD |
| **Task 8** | `client` | Tạo `LinkPreviewFetcher.java`, `LinkPreviewFetcherTest.java`; Sửa `MessageBubble.java` | **`Chuong12`** (HTTP/1.1 Socket) |
| **Task 9** | `server`, `client` | Tạo `VoiceCallSession.java`, `VoiceCallSessionTest.java`, `VoiceCallDialog.java`; Sửa `ClientHandler.java`, `MainFrame.java`, `ChatPanel.java` | **UDP** (`BroadcastMulticast`) + `AudioHelper` |

---

### Task 1: Mở rộng hợp đồng `common/` cho toàn bộ Đợt 5

**Files:**
- Modify: `ZaloLite/common/src/main/java/com/zalolite/common/PacketType.java`
- Modify: `ZaloLite/common/src/main/java/com/zalolite/common/Message.java`
- Modify: `ZaloLite/common/src/main/java/com/zalolite/common/Conversation.java`
- Test: `ZaloLite/common/src/test/java/com/zalolite/common/PacketTest.java`

**Interfaces:**
- Produces:
  - `PacketType` constants: `PASSWORD_CHANGE`, `OTP_REQUEST`, `OTP_RESET_PASSWORD`, `USER_BLOCK`, `USER_UNBLOCK`, `BLOCK_LIST`, `CALL_OFFER`, `CALL_ANSWER`, `CALL_REJECT`, `CALL_END`.
  - `Message` fields: `public String replySender;`, `public String replyPreview;`, `public boolean forwarded;`.
  - `Conversation` fields: `public boolean isCloud;`, `public int memberCount;`.

- [ ] **Step 1: Write failing test in `PacketTest.java`**
  Add `wave5FieldsSurviveRoundTrip()` verifying `Message` (`replyTo`, `replySender`, `replyPreview`, `forwarded`), `Conversation` (`isCloud`, `memberCount`), and `PacketType.CALL_OFFER`/`OTP_REQUEST`/`USER_BLOCK` round-trip through JSON.
- [ ] **Step 2: Run test to verify it fails**
  Run: `cd d:\baitap1\ZaloLite && .\mvnw.cmd -B test -pl common`
  Expected: Compilation failure on missing fields/constants.
- [ ] **Step 3: Add constants to `PacketType.java` and fields to `Message.java` & `Conversation.java`**
  Keep `@JsonIgnoreProperties(ignoreUnknown = true)` and default values (`false`/`0`/`null`) so existing tests and clients remain 100% compatible.
- [ ] **Step 4: Run test to verify it passes**
  Run: `cd d:\baitap1\ZaloLite && .\mvnw.cmd -B test -pl common`
  Expected: `Tests run: 5, Failures: 0, Errors: 0`.
- [ ] **Step 5: Commit**
  `git add ZaloLite/common && git commit -m "Wave 5 Task 1: extend common PacketType and DTO fields"`

---

### Task 2: Hồ sơ cá nhân & Đổi mật khẩu (`PROFILE_UPDATE`, `PASSWORD_CHANGE`)

**Files:**
- Modify: `ZaloLite/server/src/main/java/com/zalolite/server/dao/UserDao.java`
- Modify: `ZaloLite/server/src/main/java/com/zalolite/server/ClientHandler.java`
- Create: `ZaloLite/client/src/main/java/com/zalolite/client/ui/ProfileDialog.java`
- Modify: `ZaloLite/client/src/main/java/com/zalolite/client/ui/MainFrame.java`
- Test: `ZaloLite/server/src/test/java/com/zalolite/server/ServerIntegrationTest.java`

**Interfaces:**
- Consumes: `PacketType.PROFILE_UPDATE`, `PacketType.PASSWORD_CHANGE`.
- Produces:
  - `UserDao.changePassword(long userId, String oldPass, String newPass)` -> `boolean` (verifies `BCrypt.checkpw`, updates `pass_hash`).
  - `UserDao.resetPasswordByEmail(String email, String newPass)` -> `boolean` (used by Task 3).
  - Wire: `PASSWORD_CHANGE` `{oldPassword, newPassword}` -> `{ok}`.

- [ ] **Step 1: Write failing integration test in `ServerIntegrationTest.java`**
  Add `profileUpdateAndChangePasswordFlow()`: update name/email -> verify returned `User`; change password with wrong old password -> rejected; change with right old password -> old password fails login, new password succeeds.
- [ ] **Step 2: Run test to verify it fails**
  Run: `cd d:\baitap1\ZaloLite && .\mvnw.cmd -B test -pl common,server -Dtest=ServerIntegrationTest#profileUpdateAndChangePasswordFlow`
  Expected: FAIL (`Không hỗ trợ: PASSWORD_CHANGE`).
- [ ] **Step 3: Implement `UserDao.changePassword` / `resetPasswordByEmail` + `ClientHandler` handler + `ProfileDialog.java`**
  - In `ClientHandler`: validate `newPassword` length 6–72, never log passwords.
  - In `ProfileDialog`: Berry Pop modal dialog opened by clicking `myAvatar` in `MainFrame` rail; 2 sections (Cập nhật tên & email, Đổi mật khẩu); updates `me.name`, window title, and rail avatar on save.
- [ ] **Step 4: Run test & compile client**
  Run: `cd d:\baitap1\ZaloLite && .\mvnw.cmd -B test -pl common,server -Dtest=ServerIntegrationTest#profileUpdateAndChangePasswordFlow && .\mvnw.cmd -B test-compile -pl client`
  Expected: PASS & BUILD SUCCESS.
- [ ] **Step 5: Commit**
  `git add ZaloLite && git commit -m "Wave 5 Task 2: profile dialog and change password"`

---

### Task 3: Quên mật khẩu qua OTP Gmail SMTP thuần (tái dùng `MailClient`)

**Files:**
- Create: `ZaloLite/server/src/main/java/com/zalolite/server/dao/OtpDao.java`
- Create: `ZaloLite/server/src/main/java/com/zalolite/server/MailOtpService.java`
- Create: `ZaloLite/server/src/test/java/com/zalolite/server/MailOtpServiceTest.java`
- Modify: `ZaloLite/server/src/main/java/com/zalolite/server/ZaloServer.java`
- Modify: `ZaloLite/server/src/main/java/com/zalolite/server/ClientHandler.java`
- Create: `ZaloLite/client/src/main/java/com/zalolite/client/ui/ForgotPasswordDialog.java`
- Modify: `ZaloLite/client/src/main/java/com/zalolite/client/ui/LoginFrame.java`

**Interfaces:**
- Consumes: `PacketType.OTP_REQUEST`, `PacketType.OTP_RESET_PASSWORD`, `UserDao.resetPasswordByEmail`, `schema.sql` table `otp(email, code, expires_at)`.
- Produces:
  - `OtpDao`: `save(String email, String code, int ttlMinutes)`, `verifyAndConsume(String email, String code)` -> `boolean` (deletes row on success, rejects expired).
  - `MailOtpService`: `sendOtp(String toEmail, String code)` — tái dùng quy trình 8 bước ESMTP từ `MailClient/SmtpSocketClient.java` (`EHLO`, `STARTTLS` cổng 587 hoặc `SSLSocket` cổng 465, `AUTH LOGIN` Base64, `MAIL FROM`, `RCPT TO`, `DATA`, `QUIT`); nếu `mail.user` hoặc `mail.appPassword` trống thì in `[OTP-CONSOLE] email=... code=...` và trả thành công để demo offline.
  - Wire (cho phép gọi khi **chưa đăng nhập**):
    - `OTP_REQUEST` `{phone}` -> tìm user theo SĐT, kiểm tra có email, tạo mã 6 số `SecureRandom`, lưu `OtpDao` (TTL 5 phút), gửi qua `MailOtpService` -> res `{ok, maskedEmail, consoleMode}`.
    - `OTP_RESET_PASSWORD` `{phone, code, newPassword}` -> xác thực `OtpDao.verifyAndConsume`, kiểm tra độ dài mật khẩu 6–72, cập nhật BCrypt -> res `{ok}`.

- [ ] **Step 1: Write failing unit & integration tests**
  - `MailOtpServiceTest`: mock local `ServerSocket` scripted SMTP server verifying 8-step ESMTP (`220` -> `EHLO` -> `AUTH LOGIN` -> `MAIL FROM` -> `RCPT TO` -> `DATA` -> `QUIT`) + test fallback console mode when config is blank.
  - `ServerIntegrationTest.otpResetPasswordFlow()`: request OTP -> wrong code fails -> expired code fails -> valid code resets password -> reusing same code fails -> login with new password passes.
- [ ] **Step 2: Run tests to verify failure**
  Run: `cd d:\baitap1\ZaloLite && .\mvnw.cmd -B test -pl server -Dtest=MailOtpServiceTest`
  Expected: FAIL (classes not found).
- [ ] **Step 3: Implement `OtpDao`, `MailOtpService`, wire into `ZaloServer` & `ClientHandler`, add `ForgotPasswordDialog` + link `"Quên mật khẩu?"` on `LoginFrame`**
- [ ] **Step 4: Run tests & compile client**
  Run: `cd d:\baitap1\ZaloLite && .\mvnw.cmd -B test -pl server -Dtest=MailOtpServiceTest,ServerIntegrationTest#otpResetPasswordFlow && .\mvnw.cmd -B test-compile -pl client`
  Expected: PASS.
- [ ] **Step 5: Commit**
  `git add ZaloLite && git commit -m "Wave 5 Task 3: forgot password OTP via raw ESMTP socket with console fallback"`

---

### Task 4: Chặn / Bỏ chặn người dùng (`BLOCKED` trong `friendships`)

**Files:**
- Modify: `ZaloLite/server/src/main/java/com/zalolite/server/dao/FriendDao.java`
- Modify: `ZaloLite/server/src/main/java/com/zalolite/server/ClientHandler.java`
- Modify: `ZaloLite/client/src/main/java/com/zalolite/client/ui/FriendPanel.java`
- Test: `ZaloLite/server/src/test/java/com/zalolite/server/ServerIntegrationTest.java`

**Interfaces:**
- Consumes: `PacketType.USER_BLOCK`, `PacketType.USER_UNBLOCK`, `PacketType.BLOCK_LIST`.
- Produces:
  - `FriendDao`: `block(long blockerId, long targetId)` (xoá quan hệ cũ 2 chiều rồi insert `(blockerId, targetId, 'BLOCKED')`), `unblock(long blockerId, long targetId)`, `isBlockedEitherWay(long u1, long u2)` -> `boolean`, `listBlocked(long userId)` -> `List<User>`.
  - `ClientHandler`: chặn `MSG_SEND` cho hội thoại 1-1 khi `isBlockedEitherWay == true` (trả lỗi `"Không thể nhắn tin do một bên đã chặn"`), chặn `FRIEND_REQUEST`, và sẽ chặn `CALL_OFFER` ở Task 9.
  - `FriendPanel`: thêm chuột phải `"🚫 Chặn người dùng"` trên danh sách bạn bè + mục `"Danh sách đã chặn"` có nút `"Bỏ chặn"`.

- [ ] **Step 1: Write failing test `blockAndUnblockFlow()` in `ServerIntegrationTest.java`**
  A blocks B -> B cannot send 1-1 message to A -> A sees B in `BLOCK_LIST` -> A unblocks B -> B can send message again.
- [ ] **Step 2: Run test to verify failure**
  Run: `cd d:\baitap1\ZaloLite && .\mvnw.cmd -B test -pl server -Dtest=ServerIntegrationTest#blockAndUnblockFlow`
  Expected: FAIL.
- [ ] **Step 3: Implement `FriendDao`, `ClientHandler` block checks, and `FriendPanel` UI**
- [ ] **Step 4: Run test & compile client**
  Run: `cd d:\baitap1\ZaloLite && .\mvnw.cmd -B test -pl server -Dtest=ServerIntegrationTest#blockAndUnblockFlow && .\mvnw.cmd -B test-compile -pl client`
  Expected: PASS.
- [ ] **Step 5: Commit**
  `git add ZaloLite && git commit -m "Wave 5 Task 4: block and unblock users"`

---

### Task 5: Cloud của tôi (`☁️ Cloud của tôi`)

**Files:**
- Modify: `ZaloLite/server/src/main/java/com/zalolite/server/dao/ConversationDao.java`
- Modify: `ZaloLite/server/src/main/java/com/zalolite/server/ClientHandler.java`
- Modify: `ZaloLite/client/src/main/java/com/zalolite/client/ui/ChatPanel.java`
- Modify: `ZaloLite/client/src/main/java/com/zalolite/client/ui/MainFrame.java`
- Test: `ZaloLite/server/src/test/java/com/zalolite/server/ServerIntegrationTest.java`

**Interfaces:**
- Consumes: `Conversation.isCloud`.
- Produces:
  - `ConversationDao.ensureCloudConversation(long userId)` -> idempotent: tìm hội thoại `is_group = false` chỉ có duy nhất 1 thành viên `userId` (`owner_id = userId`, `name = '☁️ Cloud của tôi'`); nếu chưa có thì tạo mới và đặt `pinned = true`. Gọi trong `listForUser`.
  - Trong `ChatPanel.sendMediaFile`: nếu `conv.isCloud` thì bỏ qua `P2P_LOOKUP`, đi thẳng qua `FileServer :9001`; ẩn nút gọi thoại & trạng thái online trên header Cloud.

- [ ] **Step 1: Write failing test `cloudConversationIsIdempotentAndAcceptsMessages()` in `ServerIntegrationTest.java`**
  Call `CONV_LIST` twice -> verify exactly 1 conversation with `isCloud == true`, `pinned == true`; send text & file message to it -> verify persisted in `MSG_HISTORY`.
- [ ] **Step 2: Run test to verify failure**
  Run: `cd d:\baitap1\ZaloLite && .\mvnw.cmd -B test -pl server -Dtest=ServerIntegrationTest#cloudConversationIsIdempotentAndAcceptsMessages`
  Expected: FAIL.
- [ ] **Step 3: Implement `ensureCloudConversation`, set `c.isCloud`, update `ChatPanel`/`MainFrame`**
- [ ] **Step 4: Run test & compile client**
  Run: `cd d:\baitap1\ZaloLite && .\mvnw.cmd -B test -pl server -Dtest=ServerIntegrationTest#cloudConversationIsIdempotentAndAcceptsMessages && .\mvnw.cmd -B test-compile -pl client`
  Expected: PASS.
- [ ] **Step 5: Commit**
  `git add ZaloLite && git commit -m "Wave 5 Task 5: Cloud cua toi self-chat conversation"`

---

### Task 6: Trả lời trích dẫn (Reply), Chuyển tiếp (Forward) & Đếm thành viên nhóm

**Files:**
- Modify: `ZaloLite/server/src/main/java/com/zalolite/server/dao/MessageDao.java`
- Modify: `ZaloLite/server/src/main/java/com/zalolite/server/dao/ConversationDao.java`
- Modify: `ZaloLite/server/src/main/java/com/zalolite/server/ClientHandler.java`
- Modify: `ZaloLite/client/src/main/java/com/zalolite/client/ui/ChatPanel.java`
- Modify: `ZaloLite/client/src/main/java/com/zalolite/client/ui/MessageBubble.java`
- Modify: `ZaloLite/client/src/main/java/com/zalolite/client/ui/MainFrame.java`
- Test: `ZaloLite/server/src/test/java/com/zalolite/server/ServerIntegrationTest.java`

**Interfaces:**
- Consumes: `Message.replyTo`, `Message.replySender`, `Message.replyPreview`, `Message.forwarded`, `Conversation.memberCount`.
- Produces:
  - `MessageDao.save` & `history`: LEFT JOIN `messages r` + `users ru` để điền `replySender` và `replyPreview` (rút gọn 80 ký tự, hoặc `"Tin nhắn đã được thu hồi"` nếu tin gốc đã thu hồi).
  - `ConversationDao.listForUser` & `findForUser`: điền `memberCount` cho nhóm.
  - `MessageBubble`: hiển thị khung trích dẫn dọc màu tím phía trên nội dung khi `replyTo != null`, nhãn `"↪ Đã chuyển tiếp"` khi `forwarded == true`; menu chuột phải thêm **"↩ Trả lời"** và **"➡ Chuyển tiếp"**.
  - `ChatPanel`: thanh preview `"Đang trả lời <tên>: <nội dung> [✕]"` phía trên ô nhập; hộp thoại chọn hội thoại đích để chuyển tiếp (`MSG_SEND` kèm `forwarded = true`); header nhóm hiện `"<memberCount> thành viên"`.

- [ ] **Step 1: Write failing test `replyPreviewAndGroupMemberCount()` in `ServerIntegrationTest.java`**
  Send msg1 -> send msg2 with `replyTo = msg1.id` -> verify `MSG_HISTORY` returns `replySender` & `replyPreview`; verify group `CONV_LIST` returns `memberCount == 3`.
- [ ] **Step 2: Run test to verify failure**
  Run: `cd d:\baitap1\ZaloLite && .\mvnw.cmd -B test -pl server -Dtest=ServerIntegrationTest#replyPreviewAndGroupMemberCount`
  Expected: FAIL.
- [ ] **Step 3: Implement `MessageDao`, `ConversationDao`, `ClientHandler`, `MessageBubble`, `ChatPanel`, `MainFrame`**
- [ ] **Step 4: Run test & compile client**
  Run: `cd d:\baitap1\ZaloLite && .\mvnw.cmd -B test -pl server -Dtest=ServerIntegrationTest#replyPreviewAndGroupMemberCount && .\mvnw.cmd -B test-compile -pl client`
  Expected: PASS.
- [ ] **Step 5: Commit**
  `git add ZaloLite && git commit -m "Wave 5 Task 6: reply quote, forward message, and group member count"`

---

### Task 7: Tiện ích Zalo PC (Chuông báo tin mới, Kéo thả file/ảnh, Dán ảnh `Ctrl+V`)

**Files:**
- Modify: `ZaloLite/client/src/main/java/com/zalolite/client/ui/MainFrame.java`
- Modify: `ZaloLite/client/src/main/java/com/zalolite/client/ui/ChatPanel.java`

**Interfaces:**
- Consumes: `ChatPanel.sendMediaFile`, `MainFrame.onPush(MSG_NEW)`.
- Produces:
  - **Chuông báo:** khi nhận `MSG_NEW` từ người khác lúc cửa sổ không active (`!isActive()`) hoặc hội thoại không đang mở -> phát âm báo nhẹ (`Toolkit.getDefaultToolkit().beep()`, có nút bật/tắt tiếng 🔔/🔕 trên rail trái).
  - **Drag & Drop:** gắn `TransferHandler` nhận `DataFlavor.javaFileListFlavor` trên `ChatPanel` -> thả 1 hoặc nhiều file/ảnh vào khung chat là tự gọi `sendMediaFile` (tự phân loại ảnh/video/wav/file).
  - **Paste ảnh (`Ctrl+V`):** gắn keybinding `CTRL+V` trên `inputField` -> nếu clipboard chứa `DataFlavor.imageFlavor` thì ghi `BufferedImage` ra file tạm `.png` rồi hỏi/gửi ngay bằng `sendMediaFile(..., KIND_IMAGE, 0)`; nếu clipboard là chữ thì dán chữ bình thường.

- [ ] **Step 1: Implement notification toggle + beep in `MainFrame.java`**
- [ ] **Step 2: Implement Drag & Drop `TransferHandler` and `Ctrl+V` image paste in `ChatPanel.java`**
- [ ] **Step 3: Build & verify client compiles cleanly**
  Run: `cd d:\baitap1\ZaloLite && .\mvnw.cmd -B test-compile -pl client`
  Expected: `BUILD SUCCESS`.
- [ ] **Step 4: Commit**
  `git add ZaloLite/client && git commit -m "Wave 5 Task 7: notification sound, drag-and-drop files, and Ctrl+V image paste"`

---

### Task 8: Link Preview qua HTTP/1.1 Socket thuần (tái dùng `Chuong12`)

**Files:**
- Create: `ZaloLite/client/src/main/java/com/zalolite/client/LinkPreviewFetcher.java`
- Create: `ZaloLite/client/src/test/java/com/zalolite/client/LinkPreviewFetcherTest.java`
- Modify: `ZaloLite/client/src/main/java/com/zalolite/client/ui/MessageBubble.java`

**Interfaces:**
- Produces:
  - `LinkPreviewFetcher.Preview(String url, String host, String title)`
  - `LinkPreviewFetcher.extractFirstUrl(String text)` -> `String` (matches `https?://[^\s<>"]+`)
  - `LinkPreviewFetcher.fetch(String url)` -> `Preview` (or `null` on timeout/error):
    - Tự mở `Socket` (http:80) hoặc `SSLSocket` (https:443) với connect/read timeout **2000ms**, gửi `GET <path> HTTP/1.1\r\nHost: ...\r\nUser-Agent: ZaloLite/1.0\r\nConnection: close\r\n\r\n`.
    - Theo tối đa **2 lần redirect** (`301`/`302`/`307`/`308`), đọc tối đa 64KB HTML, trích thẻ `<title>...</title>` (giải mã entity cơ bản `&amp;`, `&lt;`, `&gt;`, `&quot;`).
    - Chặn SSRF cơ bản cho production (`allowLoopback` flag chỉ bật trong unit test; mặc định bỏ qua IP private/loopback nếu cần).
  - `MessageBubble`: khi tin `TEXT` có URL, chạy nền `Theme.runAsync(() -> LinkPreviewFetcher.fetch(url), ...)` -> gắn thẻ xem trước Berry Pop (`🔗 <title>` + `<host>`) bấm vào mở trình duyệt bằng `Desktop.getDesktop().browse(URI)`.

- [ ] **Step 1: Write failing unit tests in `LinkPreviewFetcherTest.java`**
  - `extractsTitleFromRawHttpServer()`: dựng `ServerSocket(0)` trả HTTP/1.1 200 với `<title>Đại học Sài Gòn</title>`, gọi `fetch` với `allowLoopback=true` -> khớp title.
  - `followsRedirectUpToLimit()`: 302 -> 200 thành công; redirect loop 3 lần -> trả `null` không treo.
  - `returnsNullQuicklyOnTimeout()`: socket không trả lời -> trả `null` trong ~2,5s.
- [ ] **Step 2: Run test to verify failure**
  Run: `cd d:\baitap1\ZaloLite && .\mvnw.cmd -B test -pl client -Dtest=LinkPreviewFetcherTest`
  Expected: FAIL (class not found).
- [ ] **Step 3: Implement `LinkPreviewFetcher.java` and wire async preview card in `MessageBubble.java`**
- [ ] **Step 4: Run test**
  Run: `cd d:\baitap1\ZaloLite && .\mvnw.cmd -B test -pl client -Dtest=LinkPreviewFetcherTest`
  Expected: `Tests run: 3, Failures: 0, Errors: 0`.
- [ ] **Step 5: Commit**
  `git add ZaloLite/client && git commit -m "Wave 5 Task 8: HTTP/1.1 socket link preview card"`

---

### Task 9: Gọi thoại 1-1 realtime qua UDP (báo hiệu TLS + âm thanh PCM UDP)

**Files:**
- Create: `ZaloLite/client/src/main/java/com/zalolite/client/VoiceCallSession.java`
- Create: `ZaloLite/client/src/test/java/com/zalolite/client/VoiceCallSessionTest.java`
- Create: `ZaloLite/client/src/main/java/com/zalolite/client/ui/VoiceCallDialog.java`
- Modify: `ZaloLite/server/src/main/java/com/zalolite/server/ClientHandler.java`
- Modify: `ZaloLite/client/src/main/java/com/zalolite/client/ui/MainFrame.java`
- Modify: `ZaloLite/client/src/main/java/com/zalolite/client/ui/ChatPanel.java`

**Interfaces:**
- Consumes: `PacketType.CALL_OFFER`, `CALL_ANSWER`, `CALL_REJECT`, `CALL_END`, `FriendDao.isBlockedEitherWay`.
- Produces:
  - **Signaling (qua TLS `:9000`):**
    - `CALL_OFFER` `{targetId, udpPort}` -> server kiểm tra không bị chặn & `targetId` đang online -> gắn `callerIp = socket.getInetAddress().getHostAddress()`, đẩy `CALL_OFFER` `{callerId, callerName, callerIp, udpPort}` cho `targetId`; trả `{ok}`.
    - `CALL_ANSWER` `{callerId, udpPort}` -> server gắn `calleeIp = socket.getInetAddress().getHostAddress()`, đẩy `CALL_ANSWER` `{calleeId, calleeIp, udpPort}` cho `callerId`.
    - `CALL_REJECT` `{callerId, reason}` -> đẩy cho `callerId`.
    - `CALL_END` `{peerId}` -> đẩy cho `peerId`.
  - **`VoiceCallSession`:**
    - Mở `DatagramSocket(0)` (`getLocalPort()`), định dạng PCM 16kHz / 16-bit / mono (frame 640 bytes = 20ms).
    - `connectPeer(String ip, int port)` -> bắt đầu 2 luồng daemon: luồng thu mic (`TargetDataLine` → gửi UDP, tôn trọng `setMuted(boolean)`) và luồng nhận UDP (→ phát loa `SourceDataLine`).
    - `close()` (idempotent): dừng cả 2 luồng, đóng `DatagramSocket`, `TargetDataLine`, `SourceDataLine` trong `finally`.
    - Thêm method `sendRawFrame(byte[] pcm)` + listener cho unit test không cần phần cứng mic!
  - **`VoiceCallDialog`:** giao diện Berry Pop hiện avatar to, trạng thái (`Đang gọi...` / `Cuộc gọi đến` / `00:15`), nút **Nghe** (xanh), **Từ chối / Cúp máy** (đỏ dâu), **Tắt mic (Mute)**; nút **📞** trên header `ChatPanel` (ẩn ở nhóm & Cloud).

- [ ] **Step 1: Write failing tests**
  - `VoiceCallSessionTest`: 2 `VoiceCallSession` chạy loopback UDP không cần mic -> gửi 10 frame PCM từ A sang B và ngược lại -> verify B nhận đủ byte; đóng `close()` giải phóng socket sạch.
  - `ServerIntegrationTest.voiceCallSignalingAndBlockCheck()`: A gọi B -> B nhận `CALL_OFFER` có `callerIp` & `udpPort` -> B `CALL_ANSWER` -> A nhận `calleeIp` & `udpPort` -> A `CALL_END`; sau đó B chặn A -> `CALL_OFFER` bị từ chối.
- [ ] **Step 2: Run tests to verify failure**
  Run: `cd d:\baitap1\ZaloLite && .\mvnw.cmd -B test -pl client -Dtest=VoiceCallSessionTest`
  Expected: FAIL.
- [ ] **Step 3: Implement `ClientHandler` signaling, `VoiceCallSession`, `VoiceCallDialog`, wire button `📞` in `ChatPanel` & push handlers in `MainFrame`**
- [ ] **Step 4: Run full project build & test suite**
  Run: `cd d:\baitap1\ZaloLite && .\mvnw.cmd -B package`
  Expected: **BUILD SUCCESS**, toàn bộ test cũ (17) + toàn bộ test mới Đợt 5 đều PASS (0 failures, 0 errors).
- [ ] **Step 5: Commit & Push**
  `git add ZaloLite && git commit -m "Wave 5 Task 9: 1-1 realtime UDP voice call with TLS signaling" && git push origin main`

---

## Verification Plan (Tổng thể Đợt 5)
1. **Automated:** `cd d:\baitap1\ZaloLite && .\mvnw.cmd -B package` — toàn bộ unit & integration test (PostgreSQL + TLS + TCP File + UDP Discovery + P2P + ESMTP + HTTP Link Preview + UDP Voice) đạt 100%.
2. **Manual Demo (2 máy LAN / 2 cửa sổ):**
   - Bấm **Quên mật khẩu?** → nhận OTP (Gmail hoặc console) → đổi mật khẩu → đăng nhập lại.
   - Bấm avatar góc trái → đổi tên + đổi mật khẩu.
   - Mở **☁️ Cloud của tôi** → kéo thả ảnh + dán `Ctrl+V` ảnh.
   - Chat 1-1 → gửi link `https://sgu.edu.vn` thấy thẻ Link Preview → chuột phải **Trả lời** và **Chuyển tiếp** sang nhóm (thấy đúng số thành viên) → nghe tiếng chuông báo.
   - Bấm **📞** gọi thoại giữa 2 máy LAN → bấm Nghe, nói chuyện, thử Mute, Cúp máy.
   - Thử **Chặn** → kiểm tra không nhắn và không gọi được → **Bỏ chặn**.
