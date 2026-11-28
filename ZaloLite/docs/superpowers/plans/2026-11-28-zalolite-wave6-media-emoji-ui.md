# ZaloLite Wave 6 (Inline Video, Full-Color Emoji, Mic Fix & Zalo-Exact UI) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Nâng cấp `ZaloLite` dựa trên phản hồi thực tế và đối chiếu kiến trúc từ Zalo PC (`rea inspect-artifact` trên `Zalo-26.11.20\resources\app.asar`): (1) sửa triệt để lỗi ghi âm bị im lặng do Windows chọn nhầm micro ảo `Camo`, (2) phát video MP4 trực tiếp ngay trong bong bóng bằng JavaFX Media kèm nút tải về riêng, (3) thay toàn bộ emoji trắng đen bằng bộ PNG màu Twemoji chạy offline, và (4) nâng cấp giao diện giống bố cục Zalo PC (tab Tất cả/Chưa đọc, nút cảm xúc màu góc dưới phải bong bóng, khung soạn tin 2 tầng).

**Architecture:** Hầu hết nâng cấp (Task 1–5) tập trung ở `client/` (OpenJFX `javafx-media:21.0.2` + `javafx-swing:21.0.2` đã có sẵn trong `~/.m2`, 42 ảnh PNG màu Twemoji offline, sửa chọn micro vật lý cho `AudioHelper` & `VoiceCallSession`, nâng cấp UI chuẩn Zalo + chụp màn hình + `@mention` + lưu nháp). Riêng **Task 6 (Ghim tin nhắn lên đầu phòng chat)** bổ sung nhẹ 1 hằng `MSG_PIN` ở `common/` + cột `pinned_msg_id` (tự migrate `ADD COLUMN IF NOT EXISTS`) ở `server/` để cả nhóm đồng bộ tin được ghim.

**Tech Stack:** Java 17+ (JDK 24), Swing + FlatLaf 3.5.2, OpenJFX 21.0.2 (`javafx-media`, `javafx-swing`, classifier `win`), Java Sound API (`javax.sound.sampled`), Java AWT `Robot` (chụp màn hình), Twemoji PNG 72×72 (offline resources), PostgreSQL JDBC, JUnit 5.

**Spec:** Phản hồi code review ngày 2026-11-28 + ảnh chụp Zalo Web/PC + kết quả REA `inspect-artifact` (`Zalo-26.11.20\resources\app.asar`: 13,200 tệp, tách preload/worker, bộ emoji PNG/SVG đóng gói sẵn, tách xem inline vs lưu tệp, và 4 tiện ích Zalo: `capture`, `mention`, `draft`, `pin`).

## Global Constraints
- **Tương thích ngược 100%:** Toàn bộ 30 test cũ (5 `common` + 18 `server` + 7 `client`) tiếp tục xanh; cột `pinned_msg_id` dùng `ALTER TABLE conversations ADD COLUMN IF NOT EXISTS`.
- **100% Offline cho Emoji & UI:** Toàn bộ 42 ảnh PNG Twemoji nằm trong `client/src/main/resources/emoji/`, không gọi CDN lúc chạy app.
- **Màu thương hiệu nhóm:** Giữ thanh rail tím mận (`#4C1D95`) và bong bóng mình đỏ dâu (`#E11D48`), nền khung chat sáng gọn kiểu Zalo (`#F4F5F7`) với bong bóng đối phương trắng tinh (`#FFFFFF`) viền mảnh (`#E5E7EB`).
- **Tài nguyên & EDT:** Không chặn Swing EDT khi tải ảnh/video/âm thanh; gọi `MediaPlayer.dispose()` khi `ChatPanel.cleanup()` để tránh rò rỉ bộ nhớ native của JavaFX Media.
- **Chạy được trực tiếp bằng `run-client.cmd` (`java -jar zalolite-client.jar`):** Xác minh JavaFX native libs hoạt động mượt trong fat JAR (do `ZaloApp` không kế thừa `javafx.application.Application` mà khởi tạo `JFXPanel` từ Swing).

## Review Focus
1. **Windows có nhiều micro (Camo / OBS / Virtual Audio):** `AudioHelper` và `VoiceCallSession` phải tự động tránh micro ảo nếu có micro vật lý thật (`Microphone Array`/`Realtek`/`Headset`), đồng thời cho người dùng chọn tay và thấy thanh VU meter nhảy sóng thật; nếu file ghi ra vẫn im lặng (`peak < 50`) thì cảnh báo ngay trước khi gửi. *(Pinned in Task 1)*
2. **Định dạng Video không hỗ trợ codec (ví dụ `.mkv`/`.avi` hoặc H.265):** `JavaFX Media` hỗ trợ tốt `.mp4` (H.264/AAC); nếu gặp codec lạ phải bắt `mediaPlayer.setOnError(...)` và hiện nút mở bằng trình phát hệ thống (`MediaViewer.openWithSystemPlayer`) thay vì văng lỗi hay khung đen treo. *(Pinned in Task 3)*
3. **Rò rỉ `MediaPlayer` khi phòng chat có nhiều video hoặc chuyển phòng:** Chỉ tạo `MediaPlayer` khi cần (hoặc dừng ngay khi chuyển phòng), đăng ký danh sách `Runnable disposers` trong `ChatPanel.cleanup()` để `dispose()` mọi `MediaPlayer` trên `Platform.runLater(...)`. *(Pinned in Task 3 & Task 5)*
4. **Unicode Emoji có Variation Selector (`\uFE0F` như `❤️`, `☁️`):** `EmojiAssets` phải khớp được cả dạng có lẫn không có `\uFE0F` để `"❤️"` gửi từ bàn phím hay từ `EmojiPicker` đều hiện ảnh PNG màu chuẩn. *(Pinned in Task 2)*
5. **Tải về máy (Download Save-As) giữ đúng tên tệp gốc:** Nút `⬇ Tải về` trên Video/File mở `JFileChooser` điền sẵn `msg.fileName` (không phải tên cache có UUID) và sao chép trên luồng nền ngoài EDT. *(Pinned in Task 4)*

---

## Bản đồ file & thứ tự thực hiện

| Task | Module | File tạo mới / chỉnh sửa | Sở hữu độc lập |
|---|---|---|---|
| **Task 1** | `client` | Sửa `AudioHelper.java`, `VoiceCallSession.java`; Tạo `AudioHelperTest.java` | Nhánh Âm thanh |
| **Task 2** | `client` | Tạo `client/src/main/resources/emoji/*.png` (42 ảnh), `EmojiAssets.java`, `EmojiAssetsTest.java` | Nhánh Emoji PNG |
| **Task 3** | `client` | Sửa `client/pom.xml`; Tạo `VideoPlayerPane.java` | Nhánh JavaFX Video |
| **Task 4** | `client/ui` | Sửa `EmojiPicker.java`, `MessageBubble.java`, `MediaViewer.java` | Nối Emoji màu + Reaction bar + Video inline |
| **Task 5** | `client/ui` | Sửa `Theme.java`, `MainFrame.java`, `ChatPanel.java` | Nối Layout Zalo + Tab lọc + Composer 2 tầng + Thanh VU meter + **Chụp màn hình + `@mention` + Lưu nháp** |
| **Task 6** | `common`, `server`, `client` | Sửa `PacketType.java`, `Conversation.java`, `schema.sql`, `ConversationDao.java`, `ClientHandler.java`, `ServerIntegrationTest.java`, `ChatPanel.java`, `MessageBubble.java`, `MainFrame.java` | **Ghim tin nhắn lên đầu phòng chat** (`MSG_PIN` đồng bộ toàn nhóm) |

---

### Task 1: Sửa triệt để lỗi Ghi âm & Chọn Micro vật lý (`AudioHelper` + `VoiceCallSession`)

**Files:**
- Modify: `ZaloLite/client/src/main/java/com/zalolite/client/AudioHelper.java`
- Modify: `ZaloLite/client/src/main/java/com/zalolite/client/VoiceCallSession.java`
- Create: `ZaloLite/client/src/test/java/com/zalolite/client/AudioHelperTest.java`

**Interfaces:**
- Produces:
  - `AudioHelper.listMicrophones()` -> `List<String>` (danh sách tên mixer hỗ trợ `TargetDataLine` 16kHz/16-bit mono, xếp micro vật lý thật lên đầu, đẩy mic ảo `Camo`/`OBS`/`Virtual`/`Primary Sound Capture Driver` xuống sau).
  - `AudioHelper.isVirtualMicName(String name)` -> `boolean`.
  - `AudioHelper.setPreferredMicrophone(String mixerName)` / `getSelectedMicrophoneName()`.
  - `AudioHelper.startRecording(Runnable onAutoStop, IntConsumer onLevelPercent)` (tính mức âm lượng 0..100 theo từng chunk PCM để UI vẽ VU meter sống!).
  - `AudioHelper.RecordedAudio(File file, int durationSec, int peakAmplitude)` (giữ constructor/helper tương thích; nếu `peakAmplitude < 50`, UI biết ngay micro đang tắt tiếng/sai nguồn!).
  - `AudioHelper.openBestMicrophone(AudioFormat format, String preferredName)` -> `TargetDataLine` (dùng chung cho cả `VoiceCallSession` để gọi thoại UDP cũng tự tránh mic ảo `Camo`!).

- [ ] **Step 1: Write failing tests in `AudioHelperTest.java`**
  - `ranksPhysicalMicrophoneAheadOfVirtualMics()`: kiểm tra `isVirtualMicName("Microphone (Camo)") == true`, `isVirtualMicName("Microphone Array (Intel Smart Sound)") == false`, và hàm chọn ưu tiên xếp micro vật lý lên trước `Camo`/`Primary Sound Capture Driver`.
  - `computesPeakAmplitudeAndDurationOnWav()`: tạo PCM sóng sin 440Hz, ghi WAV, kiểm tra `peakAmplitude > 10000` và `wavDurationSec == 1`.
- [ ] **Step 2: Run test to verify failure**
  Run: `cd d:\baitap1\ZaloLite && .\mvnw.cmd -B test -pl client -am -Dtest=AudioHelperTest -Dsurefire.failIfNoSpecifiedTests=false`
  Expected: Compilation failure.
- [ ] **Step 3: Implement smart mic selection, live VU level callback, and peak detection in `AudioHelper.java` & reuse `openBestMicrophone` in `VoiceCallSession.java`**
- [ ] **Step 4: Run test**
  Run: `cd d:\baitap1\ZaloLite && .\mvnw.cmd -B test -pl client -am -Dtest=AudioHelperTest,VoiceCallSessionTest -Dsurefire.failIfNoSpecifiedTests=false`
  Expected: PASS (0 failures).
- [ ] **Step 5: Commit**
  `git add ZaloLite/client && git commit -m "Wave 6 Task 1: auto-select physical mic, add VU level & silence check"`

---

### Task 2: Bộ ảnh PNG Emoji Đầy Màu Sắc Offline (`EmojiAssets`)

**Files:**
- Create: `ZaloLite/client/src/main/resources/emoji/*.png` (42 ảnh PNG 72×72 Twemoji cho 36 emoji `EmojiPicker` + 6 reaction `👍 ❤️ 😂 😮 😢 😡`)
- Create: `ZaloLite/client/src/main/java/com/zalolite/client/ui/EmojiAssets.java`
- Create: `ZaloLite/client/src/test/java/com/zalolite/client/ui/EmojiAssetsTest.java`

**Interfaces:**
- Produces:
  - `EmojiAssets.PICKER_EMOJIS` (36 emoji), `EmojiAssets.REACTION_EMOJIS` (`{"👍", "❤️", "😂", "😮", "😢", "😡"}`).
  - `EmojiAssets.icon(String emoji, int sizePx)` -> `ImageIcon` (cache theo `(normalizedEmoji, sizePx)`, tự bỏ `\uFE0F` khi tra cứu; trả `null` nếu không có).
  - `EmojiAssets.insertRichText(JTextPane pane, String text, Color fg, float fontSize)` -> duyệt chuỗi, chèn văn bản tiếng Việt bằng `Theme.uiFont(fontSize)` và thay thế mọi emoji trong bảng bằng `ImageIcon` màu kích thước `20px`!

- [ ] **Step 1: Download 42 Twemoji PNGs (72×72) into `client/src/main/resources/emoji/` via Python script (verified non-empty valid PNGs)**
- [ ] **Step 2: Write failing test `EmojiAssetsTest.java`**
  - `allPickerAndReactionEmojisLoadAsColorIcons()`: kiểm tra đủ 36 picker + 6 reaction emoji đều trả `ImageIcon != null` với `width == 20 && height == 20`, kể cả khi có/không có `\uFE0F` (`"❤️"` và `"❤"`).
  - `insertRichTextReplacesEmojisWithIconAttributes()`: chèn `"Chào bạn ❤️😂!"` vào `JTextPane` và xác minh document chứa đủ 2 icon attribute.
- [ ] **Step 3: Implement `EmojiAssets.java`**
- [ ] **Step 4: Run test**
  Run: `cd d:\baitap1\ZaloLite && .\mvnw.cmd -B test -pl client -am -Dtest=EmojiAssetsTest -Dsurefire.failIfNoSpecifiedTests=false`
  Expected: PASS (2/2).
- [ ] **Step 5: Commit**
  `git add ZaloLite/client && git commit -m "Wave 6 Task 2: bundle 42 offline Twemoji PNGs and EmojiAssets renderer"`

---

### Task 3: Trình phát Video MP4 trực tiếp trong ZaloLite (`JavaFX Media` + `VideoPlayerPane`)

**Files:**
- Modify: `ZaloLite/client/pom.xml` (thêm `javafx-base`, `javafx-graphics`, `javafx-controls`, `javafx-media`, `javafx-swing` bản `21.0.2` + classifier `win` đã có sẵn trong `~/.m2`)
- Create: `ZaloLite/client/src/main/java/com/zalolite/client/ui/VideoPlayerPane.java`

**Interfaces:**
- Produces:
  - `VideoPlayerPane(File videoFile, String fileName, long fileSize, Runnable onDownloadClick, Runnable onOpenExternal)` (`JPanel` kích thước ~`300x210` nhúng `JFXPanel` + `MediaView` + thanh điều khiển nhỏ gọn `▶/⏸`, thanh tua `JSlider`, thời lượng `00:12 / 01:05`, nút `⛶` phóng to xem cửa sổ lớn, và nút `⬇` tải về máy riêng).
  - `VideoPlayerPane.showFullPlayerDialog(Component parent, File videoFile, String title, Runnable onDownloadClick)` (phát video lớn ~`800x500` ngay trong app).
  - `VideoPlayerPane.dispose()` (giải phóng `MediaPlayer` an toàn trên luồng JavaFX `Platform.runLater`, idempotent).
  - Lazy init: ban đầu chỉ hiển thị khung video đẹp + nút `▶` chính giữa (hoặc load khung đầu rồi pause) để phòng chat có 20 video vẫn nhẹ RAM; khi gặp codec không hỗ trợ (`.mkv`/H.265), `setOnError` hiện thông báo gọn + nút mở trình phát ngoài.

- [ ] **Step 1: Add OpenJFX 21.0.2 dependencies to `client/pom.xml`**
- [ ] **Step 2: Implement `VideoPlayerPane.java` (with `Platform.setImplicitExit(false)` so closing a dialog never kills JavaFX runtime!)**
- [ ] **Step 3: Compile & build shaded JAR, verify JavaFX initializes cleanly**
  Run: `cd d:\baitap1\ZaloLite && .\mvnw.cmd -B package -pl client -am`
  Expected: `BUILD SUCCESS`, `zalolite-client.jar` builds cleanly.
- [ ] **Step 4: Commit**
  `git add ZaloLite/client && git commit -m "Wave 6 Task 3: inline JavaFX MP4 VideoPlayerPane with separate download action"`

---

### Task 4: Nâng cấp `MessageBubble`, `EmojiPicker` & `MediaViewer` (Emoji màu + Reaction góc dưới bong bóng + Download Save-As)

**Files:**
- Modify: `ZaloLite/client/src/main/java/com/zalolite/client/ui/EmojiPicker.java`
- Modify: `ZaloLite/client/src/main/java/com/zalolite/client/ui/MediaViewer.java`
- Modify: `ZaloLite/client/src/main/java/com/zalolite/client/ui/MessageBubble.java`

**Interfaces:**
- Consumes: `EmojiAssets`, `VideoPlayerPane`.
- Produces:
  - `MediaViewer.saveFileAs(Component parent, File sourceFile, String suggestedName)` -> mở `JFileChooser` chọn chỗ lưu bản sao (chạy copy trên `Theme.runAsync`).
  - `EmojiPicker`: hiển thị 36 ảnh PNG màu (`EmojiAssets.icon(e, 24)`).
  - `MessageBubble`:
    - Văn bản dùng `JTextPane` + `EmojiAssets.insertRichText(...)` → emoji trong tin nhắn có đầy đủ màu!
    - **Góc dưới phải bong bóng (giống hệt ảnh Zalo):** thanh gọn chứa các huy hiệu đếm cảm xúc có ảnh màu (`[icon 👍] 1`) + nút tròn nhỏ `+👍` bấm vào bung **popup 6 emoji màu nổi** (`👍 ❤️ 😂 😮 😢 😡`)!
    - **Video:** nhúng `VideoPlayerPane` trực tiếp trong bong bóng (tự tải cache nền khi hiển thị), có nút `⛶ Phóng to` và nút `⬇ Tải về` (`MediaViewer.saveFileAs`).
    - **File:** hiển thị 2 nút: `📂 Mở` và `⬇ Lưu về máy`.
    - **Image:** khi xem lớn có thêm nút `⬇ Lưu ảnh`.
    - **Audio:** hiện waveform nhỏ + nút `▶ Nghe` / `⏹ Dừng` + thời lượng.
    - Expose `MessageBubble.dispose()` để `ChatPanel.cleanup()` gọi giải phóng mọi `VideoPlayerPane`.

- [ ] **Step 1: Add `MediaViewer.saveFileAs(...)` and upgrade `EmojiPicker` to full-color PNG icons**
- [ ] **Step 2: Upgrade `MessageBubble` with `JTextPane` color emoji, Zalo bottom-right reaction pill + floating 6-emoji bar, inline `VideoPlayerPane`, and Save-As button**
- [ ] **Step 3: Compile & run client tests**
  Run: `cd d:\baitap1\ZaloLite && .\mvnw.cmd -B test -pl client -am`
  Expected: `BUILD SUCCESS`, all client tests PASS.
- [ ] **Step 4: Commit**
  `git add ZaloLite/client && git commit -m "Wave 6 Task 4: full-color emoji bubbles, Zalo reaction bar, inline video & save-as"`

---

### Task 5: Nâng cấp Bố cục Zalo PC + Thanh Ghi âm VU Meter & Chọn Micro (`Theme`, `MainFrame`, `ChatPanel`)

**Files:**
- Modify: `ZaloLite/client/src/main/java/com/zalolite/client/ui/Theme.java`
- Modify: `ZaloLite/client/src/main/java/com/zalolite/client/ui/MainFrame.java`
- Modify: `ZaloLite/client/src/main/java/com/zalolite/client/ui/ChatPanel.java`

**Interfaces:**
- Consumes: `AudioHelper` (mic selector + live VU level + silence check), `EmojiAssets`, `MessageBubble.dispose()`.
- Produces:
  - **`Theme.java`:** nền khung chat `#F4F5F7`, bong bóng đối phương trắng tinh `#FFFFFF` có viền mảnh `#E5E7EB`, nhãn giờ kiểu viên thuốc bo tròn (`#9CA3AF` trên nền `#E5E7EB`/nhạt giống ảnh Zalo), giữ rail `#4C1D95` & bong bóng mình `#E11D48`.
  - **`MainFrame.java`:**
    - Đầu cột danh sách hội thoại giống ảnh Zalo: ô tìm kiếm + 2 nút nhanh **`👤+`** (mở tab tìm/thêm bạn) & **`👥+`** (mở tạo nhóm).
    - 2 tab lọc **`Tất cả`** | **`Chưa đọc`** (có gạch chân màu nổi) lọc danh sách hội thoại ngay lập tức.
  - **`ChatPanel.java`:**
    - **Khung soạn tin 2 tầng giống Zalo PC:**
      - Tầng trên (Toolbar): các nút công cụ rõ ràng (`[Emoji PNG màu]`, `🖼️ Ảnh`, `📎 File/Video`, **`📸 Chụp màn hình`**, `🎤 Ghi âm`, và nút `🎙️ Micro` nhỏ cho chọn nhanh micro vật lý!).
      - Đường kẻ mảnh `#E5E7EB`.
      - Tầng dưới: ô nhập tin `Nhập @, tin nhắn tới <tên>...` (chữ to thoáng) + nút `Gửi`.
    - **Chụp màn hình gửi nhanh (`capture`):** bấm nút `📸` -> thu nhỏ cửa sổ tạm 250ms -> dùng `java.awt.Robot.createScreenCapture` chụp màn hình chính (hoặc vùng chọn nhanh), hiện cửa sổ xem trước nhỏ có nút "Gửi ngay" / "Huỷ" -> ghi PNG tạm và gọi `sendMediaFile(..., KIND_IMAGE, 0)`.
    - **Gõ `@` nhắc tên trong nhóm (`mention`):** trong phòng chat nhóm, khi gõ `@` vào `inputField`, nạp danh sách thành viên (`GROUP_MEMBERS` cache sẵn) và mở `JPopupMenu` chọn thành viên -> chèn `@Tên ` vào ô nhập; trong `MessageBubble` tự tô đậm & màu tím nổi các đoạn `@Tên`.
    - **Lưu nháp tin nhắn (`draft`):** `MainFrame` giữ `Map<Long, String> drafts`; khi chuyển khỏi `ChatPanel` lưu `inputField.getText()` vào map, khi mở lại hội thoại đó tự khôi phục chữ đang gõ dở (và hiện nhãn nhỏ `[Nháp]` màu đỏ trên danh sách hội thoại!).
    - **Thanh ghi âm sống (Voice Recording Bar):** khi bấm `🎤`, hiện thanh màu hồng nhạt phía trên khung nhập có đèn đỏ nhấp nháy, đồng hồ đếm giây `00:04`, **thanh VU meter nhảy theo giọng nói thật**, nút `✕ Huỷ` và nút `➤ Gửi thoại`; nếu dừng mà `peak < 50` (im lặng), hiện cảnh báo nhắc đổi micro ở nút `🎙️ Micro`!
    - Trong `ChatPanel.cleanup()`: gọi `bubble.dispose()` trên tất cả `MessageBubble` để dọn sạch JavaFX `MediaPlayer`.

- [ ] **Step 1: Update `Theme.java` palette (Zalo light gray chat `#F4F5F7` + crisp white incoming bubbles)**
- [ ] **Step 2: Upgrade `MainFrame.java` conversation sidebar (+Bạn, +Nhóm, Tất cả | Chưa đọc tabs, draft map & `[Nháp]` preview)**
- [ ] **Step 3: Upgrade `ChatPanel.java` to 2-tier Zalo composer, screenshot capture, `@mention` popup, live VU recording bar, mic selector dropdown, and `MediaPlayer` cleanup**
- [ ] **Step 4: Commit Task 5**
  `git add ZaloLite/client && git commit -m "Wave 6 Task 5: Zalo PC layout, unread filter, 2-tier composer, capture, @mention, drafts & VU mic bar"`

---

### Task 6: Ghim tin nhắn quan trọng lên đầu khung chat (`MSG_PIN` đồng bộ toàn phòng)

**Files:**
- Modify: `ZaloLite/common/src/main/java/com/zalolite/common/PacketType.java` (`MSG_PIN`, `MSG_PINNED`)
- Modify: `ZaloLite/common/src/main/java/com/zalolite/common/Conversation.java` (`pinnedMsgId`, `pinnedMsgPreview`, `pinnedMsgSender`)
- Modify: `ZaloLite/server/src/main/resources/schema.sql` (`ALTER TABLE conversations ADD COLUMN IF NOT EXISTS pinned_msg_id BIGINT REFERENCES messages(id) ON DELETE SET NULL;`)
- Modify: `ZaloLite/server/src/main/java/com/zalolite/server/dao/ConversationDao.java` (`pinMessage(convId, msgId)` + nạp `pinnedMsg*` trong `listForUser`/`findForUser`)
- Modify: `ZaloLite/server/src/main/java/com/zalolite/server/ClientHandler.java` (xử lý `MSG_PIN` `{convId, msgId}` — `msgId <= 0` để bỏ ghim -> đẩy `MSG_PINNED` tới mọi thành viên)
- Modify: `ZaloLite/server/src/test/java/com/zalolite/server/ServerIntegrationTest.java` (thêm `pinAndUnpinMessageFlow()`)
- Modify: `ZaloLite/client/src/main/java/com/zalolite/client/ui/MessageBubble.java`, `ChatPanel.java`, `MainFrame.java` (thêm menu chuột phải `"📌 Ghim tin nhắn"` + banner ghim cố định ngay dưới Header `ChatPanel` có nút bấm nhảy tới tin & nút `✕ Bỏ ghim`)

**Interfaces:**
- Produces:
  - Wire: `MSG_PIN` `{convId, msgId}` (`msgId == 0` để bỏ ghim) -> res `{ok}` + push `MSG_PINNED` `{convId, msgId, senderName, preview}` tới tất cả thành viên online.

- [ ] **Step 1: Write failing integration test `pinAndUnpinMessageFlow()` in `ServerIntegrationTest.java`**
- [ ] **Step 2: Implement `PacketType`, `Conversation`, `schema.sql`, `ConversationDao.pinMessage`, `ClientHandler.msgPin`, and banner UI in `ChatPanel`/`MessageBubble`/`MainFrame`**
- [ ] **Step 3: Run FULL project build & test suite (`common` + `server` + `client`)**
  Run: `cd d:\baitap1\ZaloLite && .\mvnw.cmd -B package`
  Expected: **BUILD SUCCESS**, toàn bộ 33+ test PASS (0 failures, 0 errors), đóng gói lại `zalolite-server.jar` và `zalolite-client.jar`.
- [ ] **Step 4: Commit & Push to `origin/main`**
  `git add ZaloLite && git commit -m "Wave 6 Task 6: pin message banner synced across conversation members" && git push origin main`

---

## Verification Plan
- **Automated (`.\mvnw.cmd -B package`):** toàn bộ test cũ (30) + `AudioHelperTest` + `EmojiAssetsTest` đều PASS.
- **Manual Smoke Test (trên máy bạn):**
  1. Bấm `🎤` → nói thử → thấy **thanh VU meter nhảy xanh/đỏ** (không còn im lặng `peak=1` do `Camo` nữa!) → gửi → bấm `▶ Nghe` nghe rõ tiếng mình.
  2. Gửi 1 file `.mp4` → video hiện khung phát **ngay trên bong bóng** → bấm `▶` xem trực tiếp trong ZaloLite, bấm `⛶` xem lớn, bấm `⬇` lưu về Desktop.
  3. Mở `EmojiPicker` và gửi tin `"Chào bạn ❤️😂🔥"` → emoji có **màu thật 100%**; bấm nút thả cảm xúc góc dưới phải bong bóng → hiện dải 6 emoji màu nổi giống hệt ảnh Zalo.
  4. Kiểm tra bố cục cột trái (`+Bạn`, `+Nhóm`, tab `Tất cả` / `Chưa đọc`) và khung nhập 2 tầng.
