# ⚡ Superpowers Cheat Sheet — Cẩm Nang Lệnh Kích Hoạt 100%

> \*\*Tài liệu tra cứu nhanh:\*\* Sử dụng khi bạn muốn AI tuân thủ nghiêm ngặt quy trình kỹ thuật phần mềm chuẩn mực, không nhảy cóc, không đoán mò và không tự ý code bừa.

\---

## 📌 Cách Kích Hoạt Ép Buộc (Chống AI "Trôi" Tự Động)

Khi chat bình thường, nếu câu lệnh tiếng Việt không kích hoạt tự động, bạn chỉ cần dùng **1 trong 2 cách sau** để ép AI tuân thủ:

1. **Dùng Slash Command (Gõ trực tiếp vào ô chat):**

   * `/plan`: Ép AI lập kế hoạch triển khai chi tiết từng bước trước khi viết code.
   * `/boost`: Chế độ suy nghĩ chuyên sâu, đa góc nhìn và giải quyết vấn đề phức tạp.
   * `/grill-me`: Chế độ phỏng vấn — AI sẽ hỏi vặn lại bạn để làm rõ thiết kế trước.
2. **Gọi đích danh tên Skill (Cú pháp copy dùng ngay):**

   * Cú pháp chuẩn: `Dùng skill <tên-skill> để <nhiệm vụ của bạn>`

\---

## 🚀 Trọn Bộ 15 Skills Của Superpowers

### 1\. Nhóm Ý Tưởng \& Lập Kế Hoạch (Planning \& Design)

tui muốn bạn khi tui gõ lên ý tưởng thì hãy tới brainstorming
tui gõ lên kế hoạch thì bạn vô writing-plans
Tui bảo tạo thư mục nhánh git độc lập để thư nghiệm thì tới git-worktree

|Tên Skill|Câu lệnh mẫu (Copy dùng ngay)|Khi nào nên dùng?|Kết quả AI sẽ làm|
|-|-|-|-|
|**`brainstorming`**|`Dùng skill brainstorming để phân tích tính năng này`|Khi bạn có ý tưởng mới nhưng chưa rõ kiến trúc, hoặc muốn thảo luận phương án tối ưu.|Đặt câu hỏi làm rõ intent, đề xuất 2-3 kiến trúc khác nhau để bạn chọn.|
|**`writing-plans`**|`Dùng skill writing-plans để lên kế hoạch triển khai`|Khi đã có ý tưởng/spec rõ ràng, chuẩn bị bắt tay vào code nhiều bước.|Tạo ra file kế hoạch `.md` với checklist `- \[ ]` cực kỳ chi tiết, dễ kiểm soát.|
|**`using-git-worktrees`**|`Dùng skill using-git-worktrees để tạo không gian làm việc`|Khi muốn làm tính năng mới mà không sợ ảnh hưởng tới code gốc hiện tại.|Tạo một thư mục nhánh git độc lập để code và thử nghiệm an toàn.|

\---

### 2\. Nhóm Thực Thi \& Điều Phối Subagent (Execution)

tui muốn khi kêu làm kế hoạch thì sử dụng executing plans
tui muốn bạn luôn luôn làm subagent-driven-development
tui muốn bạn khi tui đưa ra 2 công viẹc khác nhau thì bạn hãy sử dụng đến dispatching-parallel-agent

|Tên Skill|Câu lệnh mẫu (Copy dùng ngay)|Khi nào nên dùng?|Kết quả AI sẽ làm|
|-|-|-|-|
|**`executing-plans`**|`Dùng skill executing-plans để thực thi kế hoạch`|Sau khi đã có file kế hoạch do `writing-plans` tạo ra.|Làm lần lượt từng task trong checklist, tự kiểm tra sau mỗi bước.|
|**`subagent-driven-development`**|`Dùng skill subagent-driven-development để code tính năng`|Khi kế hoạch lớn, cần các AI phụ làm từng task độc lập.|Gọi subagent làm từng việc, review chất lượng qua 2 vòng rồi mới gộp.|
|**`dispatching-parallel-agents`**|`Dùng skill dispatching-parallel-agents để làm song song`|Khi có từ 2 công việc trở lên hoàn toàn độc lập (vd: vừa viết UI vừa viết Backend).|Chạy nhiều subagent cùng lúc, tiết kiệm tối đa thời gian.|

\---

### 3\. Nhóm Kiểm Thử, Sửa Lỗi \& Đảm Bảo Chất Lượng (Quality \& Debugging)

Khi tui bảo bạn lí do code lỗi thì hãy sử dụng systematic-debugging
Khi tui bảo bạn viết file test thì hãy tới test-driven-development
Khi tui bảo bạn chạy thử xem kết quả thì hãy tới **`verification-before-completion`**

|Tên Skill|Câu lệnh mẫu (Copy dùng ngay)|Khi nào nên dùng?|Kết quả AI sẽ làm|
|-|-|-|-|
|**`systematic-debugging`**|`Dùng skill systematic-debugging để điều tra lỗi này`|Khi gặp bug, crash, lỗi mạng hoặc hành vi không như mong muốn.|Tuân thủ luật sắt: **Tìm nguyên nhân gốc (root cause)** bằng chứng cứ log trước khi sửa, tuyệt đối không sửa mò.|
|**`test-driven-development`**|`Dùng skill test-driven-development để viết chức năng X`|Khi muốn viết code chắc chắn không lỗi hồi quy (regression).|Viết file test trước ➔ chạy test báo đỏ (Red) ➔ viết code vừa đủ để test xanh (Green) ➔ Refactor.|
|**`verification-before-completion`**|`Dùng skill verification-before-completion để xác minh`|Trước khi chốt xong bài hoặc tuyên bố "đã sửa hết lỗi".|Chạy lệnh build, test thực tế và đối chiếu kết quả đầu ra; có bằng chứng mới báo xong.|

\---

### 4\. Nhóm Rà Soát \& Đóng Gói Code (Review \& Delivery)

khi tui kêu bạn kiểm tra thì hãy **`requesting-code-review` và eceiving-code-review`
còn khi tui kêu gì liên quan tới github thì hãy tới `finishing-a-development-branch**



|Tên Skill|Câu lệnh mẫu (Copy dùng ngay)|Khi nào nên dùng?|Kết quả AI sẽ làm|
|-|-|-|-|
|**`requesting-code-review`**|`Dùng skill requesting-code-review để kiểm tra lại toàn bộ code`|Khi vừa hoàn thành tính năng, trước khi nộp bài cho thầy hoặc tạo PR.|Tự rà soát toàn bộ code theo checklist nghiêm ngặt (logic vô lý, hiệu năng, chuẩn code).|
|**`receiving-code-review`**|`Dùng skill receiving-code-review để xử lý feedback`|Khi nhận được phản biện, lời chê hoặc yêu cầu sửa từ người khác/thầy cô.|Phân tích feedback một cách khoa học, kiểm chứng kỹ càng trước khi sửa, không mù quáng đồng ý.|
|**`finishing-a-development-branch`**|`Dùng skill finishing-a-development-branch để hoàn tất nhánh`|Khi mọi việc đã xong, tất cả bài test đều đã pass.|Đưa ra quyết định hợp nhất nhánh (merge), tạo PR hoặc dọn dẹp các file rác.|

\---

### 5\. Nhóm Hệ Thống \& Chẩn Đoán (Meta)

|Tên Skill|Câu lệnh mẫu (Copy dùng ngay)|Khi nào nên dùng?|Kết quả AI sẽ làm|
|-|-|-|-|
|**`using-superpowers`**|`Dùng skill using-superpowers để giải thích quy trình`|Khi muốn biết cách hệ thống skill phối hợp với nhau.|Hiển thị hướng dẫn và các quy tắc vận hành của bộ Superpowers.|
|**`diagnosing-superpowers`**|`Dùng skill diagnosing-superpowers để xem phiên làm việc bị lỗi gì`|Khi thấy AI làm việc vòng vo, tốn token, chạy sai quy trình.|Phân tích transcript log của phiên chat để chỉ ra nguyên nhân hỏng hóc.|
|**`writing-skills`**|`Dùng skill writing-skills để tạo skill mới`|Khi bạn muốn tự tạo ra một skill riêng cho bản thân theo chuẩn Superpowers.|Hướng dẫn cấu trúc thư mục, viết file `SKILL.md` và viết test case cho skill.|

\---

## 💡 Quy Trình Chuẩn (Combo Thực Chiến Khuyên Dùng)

Khi làm một bài tập lớn hoặc dự án phần mềm, bạn chỉ cần đi theo 5 bước này:

```mermaid
flowchart LR
    A\["1. brainstorming<br>(Gợi mở ý tưởng)"] --> B\["2. writing-plans<br>(Lập kế hoạch)"]
    B --> C\["3. executing-plans<br>(Thực thi từng task)"]
    C --> D\["4. verification-before-completion<br>(Xác minh bằng test)"]
    D --> E\["5. requesting-code-review<br>(Rà soát toàn diện)"]
```

> \[!TIP]
> \*\*Khi gặp sự cố ở bất kỳ bước nào:\*\* Kích hoạt ngay \*\*`systematic-debugging`\*\* để tìm đúng bệnh trước khi sửa!

