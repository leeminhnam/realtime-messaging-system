package file_notification.audio;

import javax.sound.sampled.*;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class AudioRecorder {
    // 16 kHz, 16 bit, mono: đủ nghe rõ giọng nói mà tệp nhỏ
    private static final AudioFormat FORMAT = new AudioFormat(16000.0f, 16, 1, true, false);

    private TargetDataLine targetLine;
    private Thread recordingThread;
    private ByteArrayOutputStream rawAudioStream;
    private boolean recording = false;
    private long startTime;
    private long endTime;

    // Bắt đầu ghi âm từ micro
    public synchronized void startRecording() throws LineUnavailableException {
        if (recording) return;

        DataLine.Info info = new DataLine.Info(TargetDataLine.class, FORMAT);
        if (!AudioSystem.isLineSupported(info)) {
            throw new LineUnavailableException("Microphone không hỗ trợ định dạng âm thanh chuẩn.");
        }

        targetLine = (TargetDataLine) AudioSystem.getLine(info);
        targetLine.open(FORMAT);
        targetLine.start();

        rawAudioStream = new ByteArrayOutputStream();
        recording = true;
        startTime = System.currentTimeMillis();

        recordingThread = new Thread(() -> {
            byte[] buffer = new byte[2048];
            while (recording) {
                int count = targetLine.read(buffer, 0, buffer.length);
                if (count > 0) {
                    rawAudioStream.write(buffer, 0, count);
                }
            }
        });
        recordingThread.start();
    }

    // Dừng ghi và trả về dữ liệu WAV (đã có header)
    public synchronized byte[] stopRecording() {
        if (!recording) return null;

        recording = false;
        endTime = System.currentTimeMillis();

        if (targetLine != null) {
            targetLine.stop();
            targetLine.close();
        }

        try {
            if (recordingThread != null) {
                recordingThread.join(1000);
            }
        } catch (InterruptedException ignored) {
        }

        byte[] pcmData = rawAudioStream.toByteArray();
        return convertPcmToWav(pcmData);
    }

    // Hủy ghi âm, không giữ lại dữ liệu
    public synchronized void cancelRecording() {
        if (!recording) return;

        recording = false;
        if (targetLine != null) {
            targetLine.stop();
            targetLine.close();
        }
        rawAudioStream = null;
    }

    public boolean isRecording() {
        return recording;
    }

    public int getRecordedSeconds() {
        if (!recording && endTime == 0) return 0;
        long end = recording ? System.currentTimeMillis() : endTime;
        return (int) Math.max(1, (end - startTime) / 1000);
    }

    // Thêm header RIFF để dữ liệu PCM thành tệp WAV
    private byte[] convertPcmToWav(byte[] pcmData) {
        ByteArrayInputStream bais = new ByteArrayInputStream(pcmData);
        AudioInputStream audioStream = new AudioInputStream(bais, FORMAT, pcmData.length / FORMAT.getFrameSize());
        ByteArrayOutputStream wavOut = new ByteArrayOutputStream();
        try {
            AudioSystem.write(audioStream, AudioFileFormat.Type.WAVE, wavOut);
            return wavOut.toByteArray();
        } catch (IOException e) {
            System.err.println("[AUDIO] Lỗi tạo file WAV: " + e.getMessage());
            return null;
        }
    }
}
