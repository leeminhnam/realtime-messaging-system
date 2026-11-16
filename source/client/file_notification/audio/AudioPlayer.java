package file_notification.audio;

import javax.sound.sampled.*;
import java.io.ByteArrayInputStream;

public class AudioPlayer {
    private static AudioPlayer instance;
    private Clip activeClip;
    private Runnable activeCompleteCallback;

    public static synchronized AudioPlayer getInstance() {
        if (instance == null) {
            instance = new AudioPlayer();
        }
        return instance;
    }

    // Phát dữ liệu WAV, nếu đang phát đoạn khác thì dừng đoạn đó trước
    public synchronized void play(byte[] wavBytes, Runnable onComplete) {
        stop(); // dừng đoạn đang phát

        if (wavBytes == null || wavBytes.length == 0) {
            if (onComplete != null) onComplete.run();
            return;
        }

        try {
            ByteArrayInputStream bais = new ByteArrayInputStream(wavBytes);
            AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(bais);
            activeClip = AudioSystem.getClip();
            this.activeCompleteCallback = onComplete;

            activeClip.addLineListener(event -> {
                if (event.getType() == LineEvent.Type.STOP) {
                    synchronized (AudioPlayer.this) {
                        if (activeClip != null) {
                            activeClip.close();
                            activeClip = null;
                        }
                    }
                    if (activeCompleteCallback != null) {
                        Runnable cb = activeCompleteCallback;
                        activeCompleteCallback = null;
                        cb.run();
                    }
                }
            });

            activeClip.open(audioInputStream);
            activeClip.start();
        } catch (Exception e) {
            System.err.println("[AUDIO PLAYER] Lỗi phát âm thanh: " + e.getMessage());
            stop();
            if (onComplete != null) onComplete.run();
        }
    }

    // Dừng âm thanh đang phát
    public synchronized void stop() {
        if (activeClip != null) {
            try {
                if (activeClip.isRunning()) {
                    activeClip.stop();
                }
                activeClip.close();
            } catch (Exception ignored) {
            }
            activeClip = null;
        }
        if (activeCompleteCallback != null) {
            Runnable cb = activeCompleteCallback;
            activeCompleteCallback = null;
            cb.run();
        }
    }

    public synchronized boolean isPlaying() {
        return activeClip != null && activeClip.isRunning();
    }
}
