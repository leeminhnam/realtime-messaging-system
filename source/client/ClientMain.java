import account.view.LoginFrame;
import com.formdev.flatlaf.FlatLightLaf;

import javax.swing.*;

public class ClientMain {
    public static void main(String[] args) {
        try {
            FlatLightLaf.setup();
        } catch (Exception e) {
            System.err.println("Không thể thiết lập FlatLaf: " + e.getMessage());
        }

        SwingUtilities.invokeLater(ClientMain::showLoginFrame);
    }

    /** Mở màn hình đăng nhập, dùng lại cả khi người dùng đăng xuất khỏi MainFrame. */
    public static void showLoginFrame() {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(ClientMain::showLoginFrame);
            return;
        }
        new LoginFrame((socket, reader, writer, userData) -> {
            new MainFrame(socket, reader, writer, userData).setVisible(true);
        }).setVisible(true);
    }
}