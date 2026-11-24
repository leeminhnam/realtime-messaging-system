# MODULE CLIENT – CHATNEST

Phần **client** của ChatNest: ứng dụng desktop viết bằng Java Swing (giao diện FlatLaf), kết nối tới server qua TCP socket để đăng nhập, nhắn tin 1-1, chat nhóm, gửi ảnh/video/tài liệu/ghi âm và nhận thông báo theo thời gian thực.

---

## 🎯 MỤC TIÊU

Client chịu trách nhiệm:
- Cung cấp giao diện đăng ký, đăng nhập, chỉnh sửa hồ sơ.
- Mở một kết nối TCP tới server, gửi yêu cầu dưới dạng gói JSON một dòng.
- Chạy một luồng nền đọc liên tục các gói server gửi xuống, chuyển tới controller tương ứng và cập nhật giao diện trên luồng Swing (EDT).
- Hiển thị danh sách người dùng (online/offline, số tin chưa đọc), danh sách nhóm, khung chat và tin nhắn đa phương tiện.
- Hiện popup thông báo kèm âm báo khi có tin mới.

---

## ⚙️ CÔNG NGHỆ SỬ DỤNG

| Thành phần | Công nghệ |
|------------|-----------|
| Ngôn ngữ | Java (JDK 17+) |
| Giao diện | Java Swing + FlatLaf 3.4 (`FlatLightLaf`) |
| Giao thức | TCP Socket (`java.net.Socket`), gói JSON một dòng, UTF-8 |
| Xử lý JSON | Gson 2.10.1 |
| Âm thanh | Java Sound API (`javax.sound.sampled`): thu âm WAV 16 kHz, 16-bit, mono; phát lại; tạo âm báo |
| Mở video / tệp | `java.awt.Desktop` (trình phát mặc định của hệ điều hành) |

---

## 🚀 HƯỚNG DẪN CHẠY

> Server phải được khởi động trước (xem [README server](../server/README.md)). Mọi lệnh chạy tại **thư mục gốc dự án**.

### 1. Biên dịch

PowerShell (Windows):
```powershell
javac -encoding UTF-8 -cp "source/lib/*" -d out/client (Get-ChildItem -Recurse source/client -Filter *.java).FullName
```

Git Bash / Linux / macOS:
```bash
javac -encoding UTF-8 -cp "source/lib/*" -d out/client $(find source/client -name '*.java')
```

### 2. Chạy client

```powershell
java -Dfile.encoding=UTF-8 -cp "out/client;source/lib/*" ClientMain
```
> Trên Linux/macOS đổi `;` thành `:` trong classpath.

Muốn thử chat giữa nhiều người, mở thêm terminal và chạy lại lệnh trên; mỗi cửa sổ đăng nhập một tài khoản khác nhau.

### 3. Kết nối tới server ở máy khác

Mặc định client kết nối `localhost:8888`. Đổi bằng tham số JVM:

```powershell
java -Dfile.encoding=UTF-8 -Dchat.host=192.168.1.10 -Dchat.port=8888 -cp "out/client;source/lib/*" ClientMain
```

Máy chạy server cần mở cổng 8888 trên tường lửa.

---

## 💡 SỬ DỤNG

1. **Đăng ký / đăng nhập**: cửa sổ đầu tiên là `LoginFrame`. Bấm đăng ký để tạo tài khoản (username, họ tên, mật khẩu), sau đó đăng nhập.
2. **Cửa sổ chính** (`MainFrame`):
   - Thanh bên có hai tab **Người dùng** và **Nhóm**. Tab Người dùng hiện trạng thái online/offline và số tin chưa đọc của từng người.
   - Góc trên có nút **Hồ sơ cá nhân** (đổi họ tên, avatar) và **Đăng xuất**.
3. **Chat 1-1**: chọn một người trong danh sách, gõ tin và gửi. Tin nhắn hiện dạng bong bóng, có dấu phân cách theo ngày và trạng thái đã đọc.
4. **Chat nhóm**: ở tab Nhóm, tạo nhóm mới (chọn ít nhất 2 người khác). Trưởng nhóm thêm/xóa thành viên, chuyển quyền hoặc giải tán nhóm trong hộp thoại **Thành viên**. Thành viên thường có thể rời nhóm.
5. **Gửi tệp** (cả chat 1-1 và nhóm):
   - Nút đính kèm: chọn ảnh, video hoặc tài liệu (tối đa **20 MB**).
   - Nút micro: thu âm rồi gửi tin nhắn thoại.
   - Bấm vào ảnh để xem phóng to/thu nhỏ và lưu về máy; bấm vào video để mở bằng trình phát mặc định; bấm vào tài liệu để tải về; bấm vào ghi âm để phát.
6. **Thông báo**: khi có tin mới ở đoạn chat khác hoặc khi cửa sổ không được chọn, góc màn hình hiện popup kèm âm báo; bấm vào popup để mở ngay đoạn chat đó.

---

## 🔗 GIAO TIẾP VỚI SERVER

- Đăng nhập: `LoginFrame` mở `Socket` tới server, gửi `LOGIN`; nếu thành công, cùng socket đó được chuyển cho `MainFrame` dùng suốt phiên.
- Đăng ký: `RegisterFrame` mở một socket riêng, gửi `REGISTER`, nhận phản hồi rồi đóng.
- Sau khi đăng nhập, `MainFrame` chạy một luồng nền `reader.readLine()` liên tục và phân phối gói theo `action`:

| Gói nhận được | Xử lý tại |
|---------------|-----------|
| `USER_STATUS_CHANGE` | `UserListPanel` + `PrivateChatController` (cập nhật trạng thái đối tác) |
| `UPDATE_PROFILE_RESPONSE` | `MainFrame` (cập nhật tên, avatar ở header) |
| `RECEIVE_PRIVATE_MESSAGE`, `GET_CHAT_HISTORY_RESPONSE`, `SEND_PRIVATE_MESSAGE_RESPONSE`, `SEND_FILE_RESPONSE` | `PrivateChatController` |
| `DOWNLOAD_FILE_RESPONSE` | `FileClientManager` |
| `RECEIVE_NOTIFICATION` | `NotificationToast` |
| `GET_MY_GROUPS_RESPONSE`, `GROUP_UPDATED`, `GROUP_DELETED`, `GET_GROUP_MEMBERS_RESPONSE`, `GET_GROUP_HISTORY_RESPONSE`, `RECEIVE_GROUP_MESSAGE`, ... | `GroupChatController` |

Danh sách đầy đủ action và định dạng gói xem [README gốc](../../README.md#-giao-tiếp-giao-thức-sử-dụng) và [README server](../server/README.md#-giao-thức-tcp).

---

## 📦 CẤU TRÚC

```
client/
├── README.md
├── ClientMain.java                  # Điểm vào: cài FlatLaf, mở LoginFrame
├── MainFrame.java                   # Cửa sổ chính, luồng đọc socket, phân phối gói tin
├── account/                         # Module 1
│   ├── LoginFrame.java              # Đăng nhập, mở kết nối TCP
│   ├── RegisterFrame.java           # Đăng ký tài khoản
│   ├── ProfileDialog.java           # Sửa họ tên, avatar
│   └── UserListPanel.java           # Danh sách người dùng, online/offline, tin chưa đọc
├── private_chat/                    # Module 2
│   ├── controller/PrivateChatController.java
│   ├── model/ChatMessage.java, ChatPartner.java
│   └── view/PrivateChatPanel.java, ChatHeaderPanel.java, ChatInputPanel.java,
│            MessageListPanel.java, MessageBubblePanel.java
├── group_chat/                      # Module 3
│   ├── controller/GroupChatController.java
│   ├── model/GroupItem.java, MemberItem.java
│   └── view/GroupListPanel.java, GroupChatPanel.java, CreateGroupDialog.java,
│            GroupMembersDialog.java
└── file_notification/               # Module 4
    ├── controller/FileClientManager.java    # Đọc tệp, mã hóa Base64, gửi/tải tệp
    ├── model/MediaContent.java              # Nội dung tin nhắn media
    ├── audio/AudioRecorder.java, AudioPlayer.java, VoiceRecordDialog.java
    ├── view/ImageViewerDialog.java          # Xem ảnh: zoom, lưu
    └── notification/NotificationToast.java  # Popup thông báo + âm báo
```

Mỗi module tách theo mô hình **controller / model / view**: view là các panel Swing, controller nhận gói từ `MainFrame` và gửi yêu cầu qua `PrintWriter` của socket.

---

## 📝 GHI CHÚ

- Đảm bảo server đã chạy trước khi khởi động client, nếu không cửa sổ đăng nhập sẽ báo không kết nối được.
- Mặc định kết nối tới `localhost:8888`; đổi bằng `-Dchat.host` và `-Dchat.port`.
- Thu âm cần máy có microphone và được hệ điều hành cho phép truy cập.
- Tệp gửi tối đa 20 MB vì cả tệp được mã hóa Base64 trong một gói JSON.
- Đóng cửa sổ hoặc mất kết nối thì server tự chuyển tài khoản sang OFFLINE.
