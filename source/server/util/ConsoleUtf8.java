package util;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

/**
 * Cho console hiển thị đúng tiếng Việt có dấu.
 * - System.out/System.err luôn ghi UTF-8 (từ JDK 18, -Dfile.encoding không còn tác dụng với console).
 * - Trên Windows, chuyển bảng mã của cửa sổ console sang UTF-8 (chcp 65001) để console đọc đúng.
 * Gọi ở đầu hàm main; gọi nhiều lần cũng chỉ chạy một lần.
 */
public final class ConsoleUtf8 {
    private static boolean enabled = false;

    private ConsoleUtf8() {
    }

    public static synchronized void enable() {
        if (enabled) {
            return;
        }
        enabled = true;

        if (System.getProperty("os.name", "").toLowerCase().contains("win")) {
            try {
                new ProcessBuilder("cmd", "/c", "chcp 65001 >nul").inheritIO().start().waitFor();
            } catch (Exception ignored) {
                // Không đổi được bảng mã (không có console, bị chặn...) thì vẫn chạy tiếp bình thường
            }
        }

        System.setOut(new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(new FileOutputStream(FileDescriptor.err), true, StandardCharsets.UTF_8));
    }
}
