# BÀI TẬP LỚN: LẬP TRÌNH MẠNG  

## HỆ THỐNG NHẮN TIN THỜI GIAN THỰC

---

## 🧑‍💻 THÔNG TIN NHÓM

| STT | Họ và Tên | MSSV | Email | Đóng góp |
|-----|-----------|------|-------|----------|
| 1 | Phan Trung Đức | B23DCCN187 | a@example.com | ... |
| 2 | Vũ Quang Huy | B23DCCN412 | vuquanghuy18042005@gmail.com | ... |
| 3 | Lê Tuấn Minh | B23DCCN551 | tuanminh30042005@gmail.com | ... |
| 4 | Lê Minh Nam | B23DCCN579 | leminhnam325@gmail.com | ... |

**Tên nhóm:** Nhóm 07 – Lập trình mạng - INT1433-20261-09 
**Chủ đề đã đăng ký:** Hệ thống nhắn tin thời gian thực

---

## 🧠 MÔ TẢ HỆ THỐNG

Hệ thống nhắn tin thời gian thực (Real-time Messaging System) được xây dựng theo mô hình **Client - Server**, hỗ trợ người dùng giao tiếp, nhắn tin tức thời (chat 1-1 và chat nhóm), quản lý trạng thái trực tuyến và truyền tải file qua mạng.

Hệ thống bao gồm 4 module chức năng chính:
- **Module 1: Quản lý tài khoản & Trạng thái người dùng (`account`)**: Đăng ký, đăng nhập, đăng xuất, quản lý hồ sơ cá nhân, xem danh sách người dùng và theo dõi trạng thái online/offline theo thời gian thực.
- **Module 2: Trò chuyện riêng tư (`private-chat`)**: Gửi và nhận tin nhắn riêng (1-1) trong thời gian thực, quản lý danh sách hội thoại, xem lịch sử tin nhắn và trạng thái đã đọc.
- **Module 3: Trò chuyện nhóm (`group-chat`)**: Tạo nhóm trò chuyện, thêm/xóa thành viên, phân quyền trưởng nhóm, nhắn tin nhóm trong thời gian thực và lưu trữ lịch sử tin nhắn nhóm.
- **Module 4: Truyền tải file & Thông báo (`file-notification`)**: Upload/download file, gửi file trong chat riêng và chat nhóm, hiển thị trạng thái gửi/nhận file và gửi thông báo tin nhắn mới theo thời gian thực.

**Cấu trúc logic tổng quát:**
```
client  <-->  server  <-->  (database / service nếu có)
```

**Sơ đồ hệ thống:**

![System Diagram](./statics/diagram.png)

---

## ⚙️ CÔNG NGHỆ SỬ DỤNG

> Liệt kê công nghệ, framework, thư viện chính mà nhóm sử dụng.

| Thành phần | Công nghệ | Ghi chú |
|------------|-----------|---------|
| Server | Python 3.11 + Flask | REST API |
| Client | Node.js 20 + Axios | Giao tiếp HTTP |
| Database | SQLite | Lưu trữ dữ liệu tạm thời |
| Triển khai | Docker | (nếu có) |

---

## 🚀 HƯỚNG DẪN CHẠY DỰ ÁN

### 1. Clone repository
```bash
git clone https://github.com/leeminhnam/realtime-messaging-system.git
cd realtime-messaging-system
```

### 2. Chạy server
```bash
java -cp "out/production/realtime-messaging-system;source/lib/*" ServerMain
```

### 3. Chạy client
```bash
java -cp "out/production/realtime-messaging-system;source/lib/*" ClientMain
```

### 4. Kiểm thử nhanh
```bash
# Các lệnh test
```

---

## 🔗 GIAO TIẾP (GIAO THỨC SỬ DỤNG)

| Endpoint | Protocol | Method | Input | Output |
|----------|----------|--------|-------|--------|
| `/health` | HTTP/1.1 | GET | — | `{"status": "ok"}` |
| `/compute` | HTTP/1.1 | POST | `{"task":"sum","payload":[1,2,3]}` | `{"result":6}` |

---

## 📊 KẾT QUẢ THỰC NGHIỆM

> Đưa ảnh chụp kết quả hoặc mô tả log chạy thử.

![Demo Result](./statics/result.png)

---

## 🧩 CẤU TRÚC DỰ ÁN
```
realtime-messaging-system/
├── README.md
├── INSTRUCTION.md
├── statics/
│   ├── diagram.png
│   └── dataset_sample.csv
└── source/
    ├── .gitignore
    ├── client/
    │   ├── README.md
    │   ├── account/
    │   ├── private-chat/
    │   ├── group-chat/
    │   └── file-notification/
    ├── server/
    │   ├── README.md
    │   ├── account/
    │   ├── private-chat/
    │   ├── group-chat/
    │   └── file-notification/
    └── (các module khác nếu có)
```

---

## 🧩 HƯỚNG PHÁT TRIỂN THÊM

> Nêu ý tưởng mở rộng hoặc cải tiến hệ thống.

- [ ] Cải thiện giao diện người dùng
- [ ] Thêm tính năng xác thực và phân quyền
- [ ] Tối ưu hóa hiệu suất
- [ ] Triển khai trên cloud

---

## 📝 GHI CHÚ

- Repo tuân thủ đúng cấu trúc đã hướng dẫn trong `INSTRUCTION.md`.
- Đảm bảo test kỹ trước khi submit.

---

## 📚 TÀI LIỆU THAM KHẢO

> (Nếu có) Liệt kê các tài liệu, API docs, hoặc nguồn tham khảo đã sử dụng.