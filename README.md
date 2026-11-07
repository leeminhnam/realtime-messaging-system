# BÀI TẬP LỚN: LẬP TRÌNH MẠNG  

## CHATNEST – NỀN TẢNG NHẮN TIN CLIENT–SERVER BẰNG JAVA SOCKET

---

## 🧑‍💻 THÔNG TIN NHÓM

| STT | Họ và Tên | MSSV | Email | Đóng góp |
|:---:|:---|:---:|:---|:---:|
| 1 | Phan Trung Đức | B23DCCN187 | Phanduc715@gmail.com | Module 1 & 2 |
| 2 | Vũ Quang Huy | B23DCCN412 | vuquanghuy18042005@gmail.com | Module 1 & 2 |
| 3 | Lê Tuấn Minh | B23DCCN551 | tuanminh30042005@gmail.com | Module 4 (File & Notification) |
| 4 | Lê Minh Nam | B23DCCN579 | leminhnam325@gmail.com | Module 3 (Group Chat) |

**Tên nhóm:** Nhóm 07 – Lập trình mạng - INT1433-20261-09  
**Đề tài:** ChatNest – Nền tảng nhắn tin nhóm & riêng tư, truyền file và thông báo theo thời gian thực

---

## 🧠 MÔ TẢ HỆ THỐNG

**ChatNest** được xây dựng theo mô hình **Client - Server**, hỗ trợ người dùng giao tiếp, nhắn tin tức thời (chat 1-1 và chat nhóm), quản lý trạng thái trực tuyến và truyền tải file qua mạng.

Hệ thống bao gồm 4 module chức năng chính:
- **Module 1: Quản lý tài khoản & Trạng thái người dùng (`account`)**: Đăng ký, đăng nhập, đăng xuất, quản lý hồ sơ cá nhân, xem danh sách người dùng và theo dõi trạng thái online/offline theo thời gian thực.
- **Module 2: Trò chuyện riêng tư (`private-chat`)**: Gửi và nhận tin nhắn riêng (1-1) trong thời gian thực, quản lý danh sách hội thoại, xem lịch sử tin nhắn và trạng thái đã đọc.
- **Module 3: Trò chuyện nhóm (`group-chat`)**: Tạo nhóm trò chuyện, thêm/xóa thành viên, phân quyền trưởng nhóm, nhắn tin nhóm trong thời gian thực và lưu trữ lịch sử tin nhắn nhóm.
- **Module 4: Truyền tải file & Thông báo (`file-notification`)**: *(Đã hoàn thành)*
  - Hỗ trợ **Hình ảnh (`IMAGE`)**: Xem trước thumbnail ngay trên bong bóng chat, phóng to/thu nhỏ (Zoom Viewer), lưu ảnh về máy.
  - Hỗ trợ **Đoạn ghi âm (`AUDIO / VOICE NOTE`)**: Thu âm trực tiếp bằng microphone qua Java Sound API, phát lại âm thanh trực tiếp trong ứng dụng.
  - Hỗ trợ **Video (`VIDEO`)**: Xem video tự động bằng trình phát mặc định của hệ điều hành.
  - Hỗ trợ **Tệp tin tài liệu (`FILE`)**: Tải về các định dạng file đính kèm.
  - **Hệ thống thông báo thời gian thực (`Notification Toast + Chime Sound`)**: Hiển thị popup góc màn hình kèm tiếng chuông du dương khi có tin/file mới, click để chuyển ngay tới đoạn chat.
  - 📖 *Xem tài liệu chi tiết tại:* [MODULE4_DOCUMENTATION.md](./MODULE4_DOCUMENTATION.md)

**Cấu trúc logic tổng quát:**
```
Client (Swing UI) <---> Server Socket (TCP/IP) <---> SQLite Database (messaging_app.db)
```

---

## ⚙️ CÔNG NGHỆ SỬ DỤNG

| Thành phần | Công nghệ | Ghi chú |
|:---|:---|:---|
| **Ngôn ngữ** | Java (JDK 17+) | Lập trình mạng hướng đối tượng |
| **Giao thức mạng** | TCP Socket (`ServerSocket`, `Socket`) | Phân luồng đa kết nối qua `ExecutorService` |
| **Định dạng dữ liệu** | JSON (Google Gson 2.10.1) | Giao tiếp dòng lệnh (Line-delimited JSON) |
| **Giao diện Client** | Java Swing + FlatLaf 3.4 | Giao diện phẳng hiện đại, trực quan |
| **Cơ sở dữ liệu** | SQLite 3.50 + JDBC | Lưu trữ tài khoản, hội thoại, tin nhắn, tệp tin |
| **Bảo mật** | jBCrypt 0.4 | Băm mật khẩu người dùng |
| **Âm thanh** | Java Sound API (`javax.sound.sampled`) | Thu âm WAV Mono 16kHz & tổng hợp âm báo sóng sin |

---

## 🚀 HƯỚNG DẪN CHẠY DỰ ÁN

### 1. Khởi động Server (bằng PowerShell)
Tại thư mục gốc dự án:
```powershell
$files = Get-ChildItem "source\server" -Recurse -Filter *.java | ForEach-Object { $_.FullName }; javac -cp "source/lib/*" -d out $files; if ($LASTEXITCODE -eq 0) { java -cp "out;source/lib/*" ServerMain }
```

### 2. Khởi động Client (mở nhiều terminal để chạy nhiều Client cùng lúc)
```powershell
$files = Get-ChildItem "source\client" -Recurse -Filter *.java | ForEach-Object { $_.FullName }; javac -cp "source/lib/*" -d out $files; if ($LASTEXITCODE -eq 0) { java -cp "out;source/lib/*" ClientMain }
```

---

## 🧩 CẤU TRÚC MÃ NGUỒN

```
realtime-messaging-system/
├── README.md
├── INSTRUCTION.md
├── MODULE4_DOCUMENTATION.md          # Tài liệu chi tiết kỹ thuật Module 4
├── statics/
│   ├── diagram.png
│   └── dataset_sample.csv
└── source/
    ├── lib/                          # Các thư viện jar phụ thuộc
    │   ├── flatlaf-3.4.jar
    │   ├── gson-2.10.1.jar
    │   ├── jbcrypt-0.4.jar
    │   └── sqlite-jdbc-3.50.3.0.jar
    ├── client/
    │   ├── ClientMain.java
    │   ├── MainFrame.java
    │   ├── account/                  # Đăng ký, đăng nhập, danh bạ
    │   ├── private_chat/             # Khung chat 1-1, bong bóng chat
    │   ├── group_chat/               # Module 3: Khung chat nhóm, thành viên, tạo nhóm
    │   └── file_notification/        # Module 4: Audio, Video, Image, Toast
    └── server/
        ├── ServerMain.java
        ├── database/DatabaseManager.java
        ├── account/                  # Xử lý tài khoản
        ├── private_chat/             # Xử lý nhắn tin 1-1
        ├── group_chat/               # Module 3: Xử lý chat nhóm
        └── file_notification/        # Module 4: Xử lý tệp & thông báo
```
