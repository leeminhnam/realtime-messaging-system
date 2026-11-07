import account.AccountModuleTest;
import file_notification.FileNotificationModuleTest;
import group_chat.GroupChatModuleTest;
import private_chat.PrivateChatModuleTest;
import testutil.TestSupport;

/**
 * Chạy toàn bộ test của 4 module trên cùng một CSDL SQLite tạm.
 * Thoát với mã 1 nếu có ca kiểm thử lỗi.
 */
public class AllTests {
    public static void main(String[] args) throws Exception {
        TestSupport.initTempDb();

        AccountModuleTest.run();          // Module 1
        PrivateChatModuleTest.run();      // Module 2
        GroupChatModuleTest.run();        // Module 3
        FileNotificationModuleTest.run(); // Module 4

        System.exit(TestSupport.summary());
    }
}
