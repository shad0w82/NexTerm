package com.termux.terminal;

import android.annotation.SuppressLint;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * A terminal session: a {@link TerminalEmulator} coupled to an arbitrary byte transport.
 * <p>
 * MODIFIED BY NEXTERM (2026-11-20): the original termux-app class spawned a local subprocess on a
 * pty through JNI. This version has no process. Bytes from the remote side are pushed in with
 * {@link #feed(byte[], int, int)} (from any thread), and bytes typed by the user go out through the
 * {@link Output} given to the constructor. The public surface used by {@code TerminalView} is
 * unchanged.
 * <p>
 * The emulator is created eagerly at a default size, so data that arrives while no view is
 * attached (for example while the app is in the background) is still processed into the screen
 * and scrollback. All emulation and client callbacks run on the main thread.
 */
public class TerminalSession extends TerminalOutput {

    /** Where user input (keyboard, paste, terminal responses) is sent. Called on the main thread. */
    public interface Output {
        void write(byte[] data, int offset, int count);
    }

    /** Notified when the visible terminal size changes, e.g. to forward it to an SSH pty later. */
    public interface SizeListener {
        void onSizeChanged(int columns, int rows);
    }

    private static final int MSG_NEW_INPUT = 1;
    private static final int MSG_CLOSED = 4;
    private static final String LOG_TAG = "TerminalSession";

    public final String mHandle = UUID.randomUUID().toString();

    TerminalEmulator mEmulator;

    /** Written by the transport's reader thread, drained on the main thread into the emulator. */
    final ByteQueue mRemoteToTerminalQueue = new ByteQueue(64 * 1024);

    private final byte[] mUtf8InputBuffer = new byte[5];

    TerminalSessionClient mClient;

    /** Set by the application for user identification of the session. */
    public String mSessionName;

    private final Output mOutput;
    private volatile SizeListener mSizeListener;
    private volatile boolean mRunning = true;
    private volatile boolean mNewlineIsCrLf;

    final Handler mMainThreadHandler = new MainThreadHandler();

    public TerminalSession(Integer transcriptRows, TerminalSessionClient client, Output output) {
        this.mClient = client;
        this.mOutput = output;
        // Default 80x24 until a view reports its real size; cell pixel sizes are only used for
        // pixel-size reports and are filled in once a view is attached.
        mEmulator = new TerminalEmulator(this, 80, 24, 0, 0, transcriptRows, client);
    }

    public void updateTerminalSessionClient(TerminalSessionClient client) {
        mClient = client;
        if (mEmulator != null) mEmulator.updateTerminalSessionClient(client);
    }

    public void setSizeListener(SizeListener listener) {
        mSizeListener = listener;
    }

    /**
     * When true, a received bare LF also returns the cursor to column 0 (for devices that print
     * "\n" only). Applied on input to the emulator; nothing is changed on the wire.
     */
    public void setNewlineIsCrLf(boolean value) {
        mNewlineIsCrLf = value;
    }

    /** Called by the view when its size in cells changes. */
    public void updateSize(int columns, int rows, int cellWidthPixels, int cellHeightPixels) {
        if (columns == mEmulator.mColumns && rows == mEmulator.mRows) return;
        mEmulator.resize(columns, rows, cellWidthPixels, cellHeightPixels);
        SizeListener l = mSizeListener;
        if (l != null) l.onSizeChanged(columns, rows);
        notifyScreenUpdate();
    }

    /** Kept for API compatibility; the emulator always exists. */
    public void initializeEmulator(int columns, int rows, int cellWidthPixels, int cellHeightPixels) {
        updateSize(columns, rows, cellWidthPixels, cellHeightPixels);
    }

    public String getTitle() {
        return mEmulator.getTitle();
    }

    /**
     * Push bytes received from the remote side. Safe to call from any thread; may block briefly if
     * the main thread is far behind, which is the desired backpressure for a reader thread.
     */
    public void feed(byte[] data, int offset, int count) {
        if (!mRunning || count <= 0) return;
        if (mRemoteToTerminalQueue.write(data, offset, count)) {
            mMainThreadHandler.sendEmptyMessage(MSG_NEW_INPUT);
        }
    }

    /** Mark the session closed (transport gone). Safe to call from any thread. */
    public void close(String reason) {
        if (!mRunning) return;
        mRunning = false;
        mMainThreadHandler.sendMessage(mMainThreadHandler.obtainMessage(MSG_CLOSED, reason));
    }

    /** Send user input to the remote side. */
    @Override
    public void write(byte[] data, int offset, int count) {
        if (mRunning) mOutput.write(data, offset, count);
    }

    /** Write the Unicode code point to the terminal encoded in UTF-8. */
    public void writeCodePoint(boolean prependEscape, int codePoint) {
        if (codePoint > 1114111 || (codePoint >= 0xD800 && codePoint <= 0xDFFF)) {
            throw new IllegalArgumentException("Invalid code point: " + codePoint);
        }
        int pos = 0;
        if (prependEscape) mUtf8InputBuffer[pos++] = 27;
        if (codePoint <= 0x7F) {
            mUtf8InputBuffer[pos++] = (byte) codePoint;
        } else if (codePoint <= 0x7FF) {
            mUtf8InputBuffer[pos++] = (byte) (0xC0 | (codePoint >> 6));
            mUtf8InputBuffer[pos++] = (byte) (0x80 | (codePoint & 0x3F));
        } else if (codePoint <= 0xFFFF) {
            mUtf8InputBuffer[pos++] = (byte) (0xE0 | (codePoint >> 12));
            mUtf8InputBuffer[pos++] = (byte) (0x80 | ((codePoint >> 6) & 0x3F));
            mUtf8InputBuffer[pos++] = (byte) (0x80 | (codePoint & 0x3F));
        } else {
            mUtf8InputBuffer[pos++] = (byte) (0xF0 | (codePoint >> 18));
            mUtf8InputBuffer[pos++] = (byte) (0x80 | ((codePoint >> 12) & 0x3F));
            mUtf8InputBuffer[pos++] = (byte) (0x80 | ((codePoint >> 6) & 0x3F));
            mUtf8InputBuffer[pos++] = (byte) (0x80 | (codePoint & 0x3F));
        }
        write(mUtf8InputBuffer, 0, pos);
    }

    /** Show a local status line (not sent to the remote side). Main thread only. */
    public void appendLocalMessage(String text) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        mEmulator.append(bytes, bytes.length);
        notifyScreenUpdate();
    }

    public TerminalEmulator getEmulator() {
        return mEmulator;
    }

    protected void notifyScreenUpdate() {
        if (mClient != null) mClient.onTextChanged(this);
    }

    public void reset() {
        mEmulator.reset();
        notifyScreenUpdate();
    }

    /** Kept for API compatibility: closes the session. */
    public void finishIfRunning() {
        close(null);
    }

    @Override
    public void titleChanged(String oldTitle, String newTitle) {
        if (mClient != null) mClient.onTitleChanged(this);
    }

    public boolean isRunning() {
        return mRunning;
    }

    public int getExitStatus() {
        return 0;
    }

    @Override
    public void onCopyTextToClipboard(String text) {
        if (mClient != null) mClient.onCopyTextToClipboard(this, text);
    }

    @Override
    public void onPasteTextFromClipboard() {
        if (mClient != null) mClient.onPasteTextFromClipboard(this);
    }

    @Override
    public void onBell() {
        if (mClient != null) mClient.onBell(this);
    }

    @Override
    public void onColorsChanged() {
        if (mClient != null) mClient.onColorsChanged(this);
    }

    /** No local process; kept for API compatibility. */
    public int getPid() {
        return 0;
    }

    /** No local process; kept for API compatibility. */
    public String getCwd() {
        return null;
    }

    @SuppressLint("HandlerLeak")
    class MainThreadHandler extends Handler {
        final byte[] mReceiveBuffer = new byte[16 * 1024];
        byte[] mExpanded = new byte[32 * 1024];

        MainThreadHandler() {
            super(Looper.getMainLooper());
        }

        @Override
        public void handleMessage(Message msg) {
            int bytesRead;
            boolean any = false;
            while ((bytesRead = mRemoteToTerminalQueue.read(mReceiveBuffer, false)) > 0) {
                appendToEmulator(mReceiveBuffer, bytesRead);
                any = true;
            }
            if (any) notifyScreenUpdate();

            if (msg.what == MSG_CLOSED) {
                mRemoteToTerminalQueue.close();
                String reason = (String) msg.obj;
                appendLocalMessage("\r\n[Disconnected" + (reason == null ? "" : ": " + reason) + "]\r\n");
                if (mClient != null) mClient.onSessionFinished(TerminalSession.this);
            }
        }

        private void appendToEmulator(byte[] buf, int len) {
            if (!mNewlineIsCrLf) {
                mEmulator.append(buf, len);
                return;
            }
            if (mExpanded.length < len * 2) mExpanded = new byte[len * 2];
            int n = 0;
            byte prev = 0;
            for (int i = 0; i < len; i++) {
                byte b = buf[i];
                if (b == '\n' && prev != '\r') mExpanded[n++] = '\r';
                mExpanded[n++] = b;
                prev = b;
            }
            mEmulator.append(mExpanded, n);
        }
    }
}
