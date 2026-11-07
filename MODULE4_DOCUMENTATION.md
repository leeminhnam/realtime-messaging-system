# BÁO CÁO KỸ THUẬT & TÀI LIỆU HƯỚNG DẪN CHI TIẾT
## MODULE 4: TRUYỀN TẢI TỆP ĐA PHƯƠNG TIỆN & HỆ THỐNG THÔNG BÁO THỜI GIAN THỰC
*(Multimedia File Transfer & Real-time Notification System)*

---

### 📌 THÔNG TIN CHUNG
* **Dự án:** ChatNest – Nền tảng nhắn tin Client–Server bằng Java Socket
* **Học phần:** Lập trình mạng (INT1433) — TS. Đặng Ngọc Hùng
* **Nhóm:** 07
* **Module:** Module 4 — Hỗ trợ Hình ảnh, Video, Ghi âm giọng nói (Voice Note), Tệp tin và Thông báo nổi thời gian thực.

---

## 1. 🎯 MỤC TIÊU VÀ YÊU CẦU ĐẶT RA

Trong một hệ thống nhắn tin hiện đại, việc chỉ gửi tin nhắn văn bản thuần túy (text) là chưa đủ. Module 4 được xây dựng nhằm giải quyết 2 bài toán lớn trong môn học Lập trình mạng:

1. **Truyền tải dữ liệu nhị phân (Binary Data Transfer) qua Socket TCP:**
   - **Hình ảnh (`IMAGE`):** Gửi các định dạng ảnh phổ biến (`PNG`, `JPG`, `JPEG`, `GIF`, `BMP`). Hiển thị ảnh thu nhỏ (thumbnail) trực tiếp trong bong bóng chat, hỗ trợ click xem toàn màn hình (Full Viewer) và phóng to/thu nhỏ (Zoom), tải ảnh về máy.
   - **Đoạn ghi âm (`AUDIO / VOICE NOTE`):** Thu âm trực tiếp giọng nói từ Microphone của máy tính bằng **Java Sound API**, mã hóa thành định dạng chuẩn **WAV (PCM 16-bit, 16kHz)**, truyền qua Socket. Phía người nhận có thanh phát âm thanh (Player) trực tiếp trong khung chat với các nút `[Phát/Dừng]`, thời lượng và tải về máy.
   - **Video Clip (`VIDEO`):** Gửi video (`MP4`, `AVI`, `MKV`, `MOV`...), hiển thị thẻ video trực quan. Người dùng có thể nhấn **[Xem Video]** để hệ thống tự động mở video bằng trình phát mặc định của hệ điều hành (Windows Media Player, Movies & TV, VLC...).
   - **Tệp tài liệu thông thường (`FILE`):** Gửi file `PDF`, `DOCX`, `ZIP`... kèm dung lượng và nút tải về máy tính.

2. **Hệ thống thông báo thời gian thực (Real-time Notification System):**
   - Khi có tin nhắn hoặc tệp mới gửi đến, nếu người nhận đang Online sẽ xuất hiện một **cửa sổ thông báo nổi (Toast Notification)** ở góc dưới bên phải màn hình.
   - Phát **âm thanh chuông báo dịu nhẹ (Chime Sound - Ding Dong)** được tổng hợp tự động bằng thuật toán toán học (sin wave) qua `Java Sound API`.
   - Người dùng click vào thông báo sẽ **tự động chuyển ngay tới cuộc trò chuyện** của người gửi.

---

## 2. 🏗️ KIẾN TRÚC & LUỒNG DỮ LIỆU (DATA FLOW)

### 2.1 Sơ đồ tuần tự: Gửi tệp & Phát thông báo (Sequence Diagram)

```
[Client A (Người gửi)]             [Server Socket]                [Client B (Người nhận)]
       |                                  |                                  |
       |-- 1. SEND_FILE (Base64) -------->|                                  |
       |                                  |-- 2. Decode Base64 & Lưu đĩa     |
       |                                  |-- 3. Lưu CSDL (file_attachments) |
       |                                  |-- 4. Lưu CSDL (messages)         |
       |<-- 5. SEND_FILE_RESPONSE (OK) ---|                                  |
       |                                  |-- 6. RECEIVE_PRIVATE_MESSAGE --->| (Hiển thị bóng chat)
       |                                  |-- 7. RECEIVE_NOTIFICATION ------>| (Hiển thị Toast + Chuông)
       |                                  |                                  |
       |                                  |<-- 8. DOWNLOAD_FILE (fileId) ----| (Nếu cần nạp data)
       |                                  |-- 9. DOWNLOAD_FILE_RESPONSE ---->|
```

---

## 3. 💾 THIẾT KẾ CƠ SỞ DỮ LIỆU (SQLITE SCHEMA)

Module 4 bổ sung bảng `file_attachments` liên kết với bảng `conversations` và `messages`:

```sql
CREATE TABLE IF NOT EXISTS file_attachments (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    conversation_id INTEGER NOT NULL,
    message_id INTEGER,
    sender_username TEXT NOT NULL COLLATE NOCASE,
    receiver_username TEXT NOT NULL COLLATE NOCASE,
    file_name TEXT NOT NULL,
    file_type TEXT NOT NULL, -- 'IMAGE', 'AUDIO', 'VIDEO', 'FILE'
    file_size INTEGER NOT NULL,
    stored_path TEXT NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (conversation_id) REFERENCES conversations(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_files_conv 
ON file_attachments(conversation_id, created_at);
```

### Cơ chế tương thích ngược (Backward Compatibility) trong `messages.content`:
Khi gửi một tệp đa phương tiện, cột `content` trong bảng `messages` được lưu trữ dưới dạng một chuỗi JSON có cấu trúc nhận diện:
```json
{
  "isMedia": true,
  "fileId": 15,
  "fileType": "IMAGE",
  "fileName": "anh_demo.png",
  "fileSize": 245100,
  "duration": 0,
  "caption": ""
}
```
* **Ưu điểm vượt trội:** 
  - Không phá vỡ cấu trúc CSDL hiện có của Module 2.
  - Khi load lịch sử chat cũ (`GET_CHAT_HISTORY`), cả tin nhắn chữ và các tệp ảnh, video, âm thanh đều được tải lên tự nhiên theo đúng thứ tự thời gian.

---

## 4. 📡 ĐẶC TẢ GIAO THỨC TRUYỀN THÔNG (JSON PROTOCOL)

### 4.1 Gói tin gửi tệp: `SEND_FILE`
* **Client gửi lên Server:**
```json
{
  "action": "SEND_FILE",
  "data": {
    "receiver": "huyvq",
    "fileName": "recording_1730338000.wav",
    "fileType": "AUDIO",
    "fileSize": 96044,
    "duration": 3,
    "caption": "",
    "base64Data": "UklGRiTGAABXQVZFZm10IBAAAAABAAEA..."
  }
}
```

* **Server phản hồi cho người gửi (`SEND_FILE_RESPONSE`):**
```json
{
  "action": "SEND_FILE_RESPONSE",
  "status": "SUCCESS",
  "message": "Gửi tệp thành công!",
  "data": {
    "id": 102,
    "conversationId": 5,
    "sender": "minhlt",
    "receiver": "huyvq",
    "content": "{\"isMedia\":true,\"fileId\":12,\"fileType\":\"AUDIO\",\"fileName\":\"recording_1730338000.wav\",\"fileSize\":96044,\"duration\":3,\"caption\":\"\"}",
    "sentAt": "2026-10-31 08:35:10",
    "fileId": 12,
    "fileType": "AUDIO",
    "fileName": "recording_1730338000.wav",
    "fileSize": 96044,
    "duration": 3
  }
}
```

* **Server đẩy tin nhắn mới tới người nhận (`RECEIVE_PRIVATE_MESSAGE`):**
Tương tự cấu trúc gói tin của Module 2 với `content` chứa chuỗi JSON nhận diện Media.

* **Server đẩy thông báo nổi tới người nhận (`RECEIVE_NOTIFICATION`):**
```json
{
  "action": "RECEIVE_NOTIFICATION",
  "status": "SUCCESS",
  "data": {
    "type": "FILE",
    "fileType": "AUDIO",
    "sender": "minhlt",
    "senderFullName": "Lê Tuấn Minh",
    "fileName": "recording_1730338000.wav",
    "fileId": 12,
    "message": "Lê Tuấn Minh đã gửi cho bạn một đoạn ghi âm 🎙️ (3s)"
  }
}
```

### 4.2 Gói tin tải dữ liệu tệp: `DOWNLOAD_FILE`
* **Client yêu cầu nạp dữ liệu:**
```json
{
  "action": "DOWNLOAD_FILE",
  "data": {
    "fileId": 12
  }
}
```

* **Server phản hồi (`DOWNLOAD_FILE_RESPONSE`):**
```json
{
  "action": "DOWNLOAD_FILE_RESPONSE",
  "status": "SUCCESS",
  "data": {
    "fileId": 12,
    "fileName": "recording_1730338000.wav",
    "fileType": "AUDIO",
    "fileSize": 96044,
    "base64Data": "UklGRiTGAABXQVZFZm10IBAAAAABAAEA..."
  }
}
```

---

## 5. 💻 CHI TIẾT CÁCH CODE VÀ CÁC THÀNH PHẦN KỸ THUẬT

### 5.1 Phía Server (`source/server/file_notification`)
1. **`model/FileAttachment.java`:** Lưu trữ thông tin metadata của tệp (id, conversationId, messageId, tên file, loại file, dung lượng, đường dẫn đĩa vật lý, thời gian tạo).
2. **`dao/FileAttachmentDAO.java`:** Thực thi các thao tác SQL với SQLite:
   - `saveFileAttachment()`: Lưu bản ghi và trả về id tự tăng.
   - `getFileAttachmentById()`: Truy vấn đường dẫn vật lý và thông tin tệp.
3. **`handler/FileNotificationHandler.java`:**
   - Tiếp nhận action `SEND_FILE` và `DOWNLOAD_FILE`.
   - Lưu trữ an toàn: Giải mã chuỗi `Base64` và ghi ra đĩa tại `source/server/data/uploads/` kèm tiền tố timestamp và UUID chống trùng tên file.
   - Kích hoạt phân phối 2 luồng: `RECEIVE_PRIVATE_MESSAGE` (vào khung chat) và `RECEIVE_NOTIFICATION` (thông báo nổi).
4. **`ServerMain.java`:** Bổ sung điều hướng luồng gói tin:
   ```java
   else if (FileNotificationHandler.isFileNotificationAction(action)) {
       FileNotificationHandler.handleFileNotificationAction(req, writer, authenticatedUsername, onlineClients);
   }
   ```

---

### 5.2 Phía Client (`source/client/file_notification`)

#### A. Xử lý Ghi âm và Phát âm thanh (Voice Note)
* **`audio/AudioRecorder.java`:**
  - Sử dụng `javax.sound.sampled.TargetDataLine` đọc luồng âm thanh trực tiếp từ Microphone máy tính.
  - Cấu hình chuẩn Studio: `AudioFormat(16000.0f, 16, 1, true, false)`: Tần số lấy mẫu `16kHz`, độ sâu mẫu `16 bit`, `Mono` (1 kênh). Định dạng này cho giọng nói rõ ràng nhưng dung lượng rất nhẹ (~32 KB/giây).
  - Đóng gói dữ liệu PCM thô thành file `.WAV` có RIFF Header bằng `AudioSystem.write()`.
* **`audio/AudioPlayer.java`:**
  - Sử dụng `javax.sound.sampled.Clip` phát lại luồng âm thanh WAV trực tiếp từ mảng byte trong bộ nhớ RAM mà không cần ghi tạm ra đĩa.
  - Tự động quản lý giải phóng tài nguyên khi phát xong bài (`LineListener`).
* **`audio/VoiceRecordDialog.java`:**
  - Hộp thoại giao diện ghi âm chuyên nghiệp: Đồng hồ đếm thời gian thực `00:03`, chấm đỏ nhấp nháy `🔴 Đang ghi âm...`, các nút `[⏹ Dừng & Gửi]`, `[▶ Nghe lại thử]`, và `[Hủy bỏ]`.

#### B. Xem ảnh và Video
* **`view/ImageViewerDialog.java`:**
  - Khi click vào ảnh thu nhỏ trong đoạn chat, mở cửa sổ xem ảnh lớn.
  - Hỗ trợ công cụ phóng to (`Zoom In`), thu nhỏ (`Zoom Out`), trở về kích thước chuẩn và nút `[💾 Lưu ảnh về máy]`.
* **Mở Video qua ứng dụng mặc định:**
  - Trong `FileClientManager.java`, hàm `openVideo()` ghi dữ liệu video ra file tạm và kích hoạt `Desktop.getDesktop().open(tempVideo)`. Video sẽ phát mượt mà ngay trên ứng dụng Windows Media Player hoặc VLC có sẵn của máy tính.

#### C. Hệ thống thông báo nổi (Toast Notification) & Chuông báo
* **`notification/NotificationToast.java`:**
  - Là một `JWindow` bán trong suốt, bo tròn góc với hiệu ứng đổ bóng, hiển thị cố định ở góc dưới bên phải màn hình.
  - Tự động đóng sau `4.5 giây` hoặc khi người dùng click vào dấu `✕`.
  - Hỗ trợ click vào thông báo để chuyển ngay tới cuộc trò chuyện của người gửi.
  - **Tạo chuông báo (Chime Sound):** Sử dụng thuật toán tạo sóng sin (Sine Wave Synthesis) phát trực tiếp qua `SourceDataLine` với hai tần số nốt nhạc `880Hz` và `1174Hz` (tạo âm sắc ding-dong du dương mà không cần bất kỳ file mp3 bên ngoài nào).

#### D. Quản lý Cache và Tệp tin
* **`controller/FileClientManager.java`:**
  - Thiết kế theo mô hình **Singleton**.
  - Tích hợp bộ nhớ đệm `Map<Integer, byte[]> fileCache`: Khi ảnh hoặc file âm thanh đã tải một lần thì không bao giờ cần gửi request tải lại từ server, tiết kiệm băng thông tối đa.

#### E. Tích hợp giao diện Chat
* **`ChatInputPanel.java`:** Bổ sung nút đính kèm `[📎]` mở menu chọn gửi Ảnh, Video, Tài liệu và nút `[🎙️]` mở giao diện thu âm.
* **`MessageBubblePanel.java`:** Tự động phân tích tin nhắn: Nếu là tin văn bản thì hiển thị bong bóng chat truyền thống; nếu là Media thì hiển thị thẻ Card trực quan tương ứng (Ảnh có ảnh thu nhỏ, Voice Note có nút bấm Play, Video có icon 🎬 và nút Xem).

---

## 6. 🧪 KỊCH BẢN KIỂM THỬ (TEST CASES)

| STT | Kịch bản kiểm thử | Thao tác thực hiện | Kết quả mong đợi |
|:---:|:---|:---|:---|
| **1** | **Gửi hình ảnh** | Nhấn `📎` -> Chọn `Gửi hình ảnh...` -> Chọn file `.png`/`.jpg`. | Bong bóng chat hiển thị ảnh thu nhỏ (thumbnail). Click vào ảnh mở cửa sổ Zoom và Lưu ảnh. |
| **2** | **Ghi âm giọng nói** | Nhấn `🎙️` -> Nói vài câu (bộ đếm chạy) -> Bấm `⏹ Dừng & Gửi`. | Tin nhắn gửi đi dưới dạng Voice Note kèm thời lượng (VD: `00:04`). Người nhận bấm `▶ Phát` để nghe âm thanh trực tiếp. |
| **3** | **Gửi Video clip** | Nhấn `📎` -> Chọn `Gửi video clip...` -> Chọn file `.mp4`. | Hiển thị thẻ video kèm dung lượng. Nhấn `▶ Xem` để mở video bằng trình phát mặc định của Windows. |
| **4** | **Thông báo nổi Realtime** | Client A gửi ảnh/tin nhắn cho Client B khi Client B đang mở hội thoại với người khác hoặc đang thu nhỏ. | Góc màn hình của Client B xuất hiện Popup thông báo nổi kèm tiếng chuông "Ding-Dong". Click vào thông báo chuyển ngay tới Client A. |
| **5** | **Khởi động lại & Lịch sử** | Tắt Client và Server, sau đó mở lại và đăng nhập. | Mở lại cuộc trò chuyện, toàn bộ lịch sử ảnh, file âm thanh, video vẫn hiển thị đầy đủ và có thể bấm nghe/xem lại bình thường. |

---

## 7. 🚀 LỆNH BIÊN DỊCH VÀ CHẠY DỰ ÁN

Mở **PowerShell** tại thư mục gốc của dự án (`realtime-messaging-system`):

### 1. Khởi động Server:
```powershell
$files = Get-ChildItem "source\server" -Recurse -Filter *.java | ForEach-Object { $_.FullName }; javac -cp "source/lib/*" -d out $files; if ($LASTEXITCODE -eq 0) { java -cp "out;source/lib/*" ServerMain }
```

### 2. Khởi động Client (có thể mở nhiều cửa sổ để test chat giữa các tài khoản):
```powershell
$files = Get-ChildItem "source\client" -Recurse -Filter *.java | ForEach-Object { $_.FullName }; javac -cp "source/lib/*" -d out $files; if ($LASTEXITCODE -eq 0) { java -cp "out;source/lib/*" ClientMain }
```
