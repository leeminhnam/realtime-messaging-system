# MODULE SERVER – CHATNEST

Phần **server** của ChatNest: một TCP server viết bằng Java thuần, nhận kết nối từ nhiều Client Swing, xử lý nghiệp vụ của 4 module (tài khoản, chat 1-1, chat nhóm, tệp & thông báo), lưu dữ liệu vào SQLite và đẩy tin nhắn/sự kiện tới các Client đang online theo thời gian thực.

---

## 🎯 MỤC TIÊU

Server chịu trách nhiệm:
- Lắng nghe kết nối TCP ở cổng **8888**, mỗi Client được phục vụ bởi một luồng riêng (`ExecutorService` cached thread pool).
- Đọc từng gói JSON (một dòng một gói), phân luồng theo trường `action` tới handler của module tương ứng.
- Xác thực người dùng (băm mật khẩu bằng BCrypt), quản lý trạng thái ONLINE/OFFLINE.
- Lưu tài khoản, hội thoại, tin nhắn, nhóm, metadata tệp vào SQLite; lưu nội dung tệp vào thư mục `uploads`.
- Chủ động đẩy (push) tin nhắn mới, thông báo, thay đổi trạng thái và cập nhật nhóm tới các Client liên quan.

---

## ⚙️ CÔNG NGHỆ SỬ DỤNG

| Thành phần | Công nghệ |
|------------|-----------|
| Ngôn ngữ | Java (JDK 17+) |
| Mạng | `java.net.ServerSocket` / `Socket` (TCP), đa luồng bằng `ExecutorService` |
| Định dạng gói tin | JSON một dòng (line-delimited), thư viện Gson 2.10.1 |
| Cơ sở dữ liệu | SQLite 3.50 qua JDBC (`sqlite-jdbc-3.50.3.0.jar`) |
| Bảo mật | jBCrypt 0.4 (băm mật khẩu) |
| HTTP adapter (tùy chọn) | `com.sun.net.httpserver.HttpServer` có sẵn trong JDK, dùng để test bằng Postman |

Không dùng framework; các thư viện `.jar` nằm trong `source/lib/`.

---

## 🚀 HƯỚNG DẪN CHẠY

> Mọi lệnh chạy **tại thư mục gốc dự án** (`realtime-messaging-system/`), vì đường dẫn CSDL mặc định là `source/server/data` tính từ thư mục hiện tại.

### 1. Biên dịch

PowerShell (Windows):
```powershell
javac -encoding UTF-8 -cp "source/lib/*" -d out/server (Get-ChildItem -Recurse source/server -Filter *.java).FullName
```

Git Bash / Linux / macOS:
```bash
javac -encoding UTF-8 -cp "source/lib/*" -d out/server $(find source/server -name '*.java')
```

### 2. Khởi động server

```powershell
java -Dfile.encoding=UTF-8 -cp "out/server;source/lib/*" ServerMain
```
> Trên Linux/macOS đổi `;` thành `:` trong classpath.

Khi chạy thành công, console in:
```
CHATNEST - SERVER
[SERVER] Đang lắng nghe tại cổng: 8888
[SERVER] Sẵn sàng tiếp nhận kết nối từ Client...
```

Lần đầu chạy, server tự tạo file `source/server/data/messaging_app.db`, các bảng và thư mục `source/server/data/uploads/`. Mỗi lần khởi động, mọi tài khoản được đưa về `OFFLINE`.

### 3. (Tùy chọn) HTTP adapter cho Postman

`HttpApiAdapter` là cầu nối HTTP → TCP, chỉ nghe trên `127.0.0.1`, dùng để gọi thử các action bằng Postman mà không cần mở giao diện Client. Cần chạy `ServerMain` trước, rồi mở terminal khác:

```powershell
java -Dfile.encoding=UTF-8 -cp "out/server;source/lib/*" HttpApiAdapter
```

Adapter chạy tại `http://127.0.0.1:8080`. Đổi cổng bằng `-Dchatnest.http.port=...` và `-Dchatnest.tcp.port=...`.

---

## 🔗 GIAO THỨC TCP

Mỗi gói tin là **một đối tượng JSON trên một dòng**, mã hóa UTF-8, kết thúc bằng `\n`.

```json
// Client -> Server
{"action": "LOGIN", "data": {"username": "alice", "password": "pw123"}}

// Server -> Client
{"action": "LOGIN_RESPONSE", "status": "SUCCESS", "message": "Đăng nhập thành công!", "data": { ... }}
```

Action không hỗ trợ nhận lại `{"action":"ERROR","status":"FAILED","message":"..."}`. Gói JSON hỏng bị bỏ qua, kết nối vẫn giữ.

### Action Client gửi lên

| Module | Action | Phản hồi |
|--------|--------|----------|
| Account | `REGISTER`, `LOGIN`, `UPDATE_PROFILE`, `GET_USER_LIST` | `<ACTION>_RESPONSE` |
| Private chat | `SEND_PRIVATE_MESSAGE`, `GET_CHAT_HISTORY`, `MARK_MESSAGES_READ` | `<ACTION>_RESPONSE` |
| Group chat | `CREATE_GROUP`, `GET_MY_GROUPS`, `GET_GROUP_MEMBERS`, `ADD_GROUP_MEMBER`, `REMOVE_GROUP_MEMBER`, `TRANSFER_LEADER`, `LEAVE_GROUP`, `DELETE_GROUP`, `SEND_GROUP_MESSAGE`, `GET_GROUP_HISTORY` | `<ACTION>_RESPONSE` |
| File | `SEND_FILE`, `SEND_GROUP_FILE`, `DOWNLOAD_FILE` (nội dung tệp mã hóa Base64) | `<ACTION>_RESPONSE` |

Các action chat 1-1, chat nhóm và `UPDATE_PROFILE` yêu cầu kết nối đã đăng nhập; server lấy người gửi từ phiên đăng nhập của socket, không tin vào username trong gói.

### Gói server chủ động đẩy xuống

| Action | Khi nào |
|--------|---------|
| `USER_STATUS_CHANGE` | Có người đăng nhập, đăng xuất hoặc mất kết nối (broadcast tới mọi Client khác) |
| `RECEIVE_PRIVATE_MESSAGE` | Có tin nhắn hoặc tệp 1-1 mới cho người nhận đang online |
| `RECEIVE_NOTIFICATION` | Kèm theo tin mới, để Client hiện popup thông báo |
| `RECEIVE_GROUP_MESSAGE` | Có tin hoặc tệp mới trong nhóm (gửi tới các thành viên online, trừ người gửi) |
| `GROUP_UPDATED` | Nhóm được tạo, thêm/xóa thành viên, chuyển trưởng nhóm, có người rời nhóm |
| `GROUP_DELETED` | Trưởng nhóm giải tán nhóm |

Chi tiết trường dữ liệu và ví dụ luồng xem [README gốc](../../README.md#-giao-tiếp-giao-thức-sử-dụng).

### Quy tắc nghiệp vụ chính

- Username không phân biệt hoa/thường; mật khẩu lưu dạng băm BCrypt.
- Mỗi cặp người dùng có đúng một hội thoại; không thể tự chat với chính mình.
- Tin gửi khi người nhận offline vẫn được lưu và hiện khi họ mở lịch sử.
- Nhóm cần tối thiểu 3 người khi tạo. Chỉ trưởng nhóm (`LEADER`) được thêm/xóa thành viên, chuyển quyền và giải tán nhóm. Trưởng nhóm phải chuyển quyền trước khi rời nhóm.
- Tệp tối đa 20 MB, được lưu với tên đã làm sạch. Tệp trong nhóm chỉ thành viên mới tải được và bị xóa cùng nhóm.

---

## 🌐 HTTP ADAPTER (POSTMAN)

| Endpoint | Method | Header / Body | Kết quả |
|----------|--------|---------------|---------|
| `/api/health` | GET | — | Trạng thái adapter, địa chỉ TCP upstream |
| `/api/sessions` | POST | — | Mở một kết nối TCP mới, trả `sessionId` (hết hạn sau 30 phút không dùng) |
| `/api/actions` | POST | `X-Session-Id`, body `{"action": "...", "data": {...}}` | Trả nguyên văn phản hồi TCP của action đó |
| `/api/events` | GET | `X-Session-Id` | Lấy các gói server đã đẩy xuống phiên này |
| `/api/sessions` | DELETE | `X-Session-Id` | Đóng phiên và kết nối TCP |

Adapter chặn request có header `Origin` (không dùng từ trình duyệt), giới hạn body 30 MB và 64 phiên đồng thời. Collection mẫu: [Postman Collection](https://www.postman.com/duc-s-team/workspace/my-workspace/collection/47918178-ac1b74a9-c408-4de4-907a-fd5cb8e45299?action=share&source=copy-link&creator=47918178).

Ví dụ bằng `curl`:
```bash
curl http://127.0.0.1:8080/api/health
curl -X POST http://127.0.0.1:8080/api/sessions
curl -X POST http://127.0.0.1:8080/api/actions -H "Content-Type: application/json" -H "X-Session-Id: <sessionId>" \
     -d '{"action":"LOGIN","data":{"username":"alice","password":"pw123"}}'
```

---

## 🗄️ CƠ SỞ DỮ LIỆU

File SQLite: `source/server/data/messaging_app.db` (bảng được tạo bằng `CREATE TABLE IF NOT EXISTS` khi khởi động).

| Bảng | Nội dung |
|------|----------|
| `users` | Tài khoản: username, mật khẩu băm, họ tên, avatar, trạng thái |
| `conversations` | Hội thoại 1-1, mỗi cặp người dùng một dòng |
| `messages` | Tin nhắn 1-1, cờ `is_read` |
| `chat_groups` | Nhóm chat |
| `group_members` | Thành viên nhóm và vai trò `LEADER` / `MEMBER` |
| `group_messages` | Tin nhắn nhóm |
| `file_attachments` | Metadata tệp (loại `IMAGE`/`AUDIO`/`VIDEO`/`FILE`, kích thước, đường dẫn lưu, `group_id` nếu là tệp nhóm) |

Nội dung tệp nằm trong `source/server/data/uploads/`. Đổi thư mục dữ liệu bằng `-Dchatnest.db.dir=<thư mục>` (bộ test dùng cách này để không đụng CSDL thật).

---

## 📦 CẤU TRÚC

```
server/
├── README.md
├── ServerMain.java                  # Điểm vào: ServerSocket :8888, ClientHandler, broadcast
├── HttpApiAdapter.java              # Cầu nối HTTP -> TCP cho Postman (tùy chọn)
├── database/
│   └── DatabaseManager.java         # Kết nối SQLite, tạo bảng, chỉ mục, thư mục uploads
├── account/                         # Module 1
│   ├── AccountHandler.java          # REGISTER, LOGIN, UPDATE_PROFILE, GET_USER_LIST
│   └── UserDAO.java
├── private_chat/                    # Module 2
│   ├── handler/PrivateChatHandler.java
│   ├── dao/ConversationDAO.java, MessageDAO.java
│   └── model/Conversation.java, Message.java
├── group_chat/                      # Module 3
│   ├── handler/GroupChatHandler.java
│   ├── dao/GroupDAO.java, GroupMessageDAO.java
│   └── model/ChatGroup.java, GroupMember.java, GroupMessage.java
├── file_notification/               # Module 4
│   ├── handler/FileNotificationHandler.java
│   ├── dao/FileAttachmentDAO.java
│   └── model/FileAttachment.java
├── util/
│   └── ConsoleUtf8.java             # Bật UTF-8 cho console Windows
└── data/
    ├── messaging_app.db             # CSDL SQLite (tự tạo)
    └── uploads/                     # Tệp người dùng gửi (tự tạo)
```

Luồng xử lý một gói tin:
```
Socket -> ClientHandler.readLine() -> parse JSON -> theo action:
    AccountHandler | PrivateChatHandler | GroupChatHandler | FileNotificationHandler
        -> DAO (SQLite) -> trả <ACTION>_RESPONSE cho người gửi
        -> đẩy gói tới người nhận qua ServerMain.onlineClients
```

---

## 🧪 TEST

Bộ test nằm ở `source/test/`, test trực tiếp DAO và handler bằng gói JSON trên CSDL tạm (không cần mở socket). Tại thư mục gốc dự án, dùng Git Bash:

```bash
javac -encoding UTF-8 -cp "source/lib/*" -d out_test $(find source/server source/test -name '*.java') && java -Dfile.encoding=UTF-8 -cp "out_test;source/lib/*" AllTests
```

Kết quả mong đợi: `Kết quả: 175 đạt, 0 lỗi`.

Test mạng thật (khởi động server và nhiều Client socket, cần tắt server đang chạy vì dùng cổng 8888):
```bash
java -Dfile.encoding=UTF-8 -cp "out_test;source/lib/*" NetworkTest
```

Test HTTP adapter theo Postman collection (cần Python 3.9+):
```bash
python -I source/test/HttpApiAdapterTest.py
```

---

## 📝 GHI CHÚ

- Cổng TCP mặc định: **8888** (hằng `ServerMain.PORT`).
- Cổng HTTP adapter mặc định: **8080**, chỉ nghe `127.0.0.1`.
- Luôn chạy từ thư mục gốc dự án, nếu không server sẽ tạo CSDL mới ở chỗ khác.
- Kết nối hiện là TCP thuần, chưa mã hóa TLS; chỉ nên dùng trong mạng nội bộ hoặc để học tập.
