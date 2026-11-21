#include <jni.h>
#include <android/log.h>
#include <errno.h>
#include <fcntl.h>
#include <poll.h>
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/ioctl.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <termios.h>
#include <unistd.h>

#define LOG_TAG "KestrelPTY"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static int set_cloexec(int fd) {
    int flags = fcntl(fd, F_GETFD);
    if (flags == -1) return -1;
    return fcntl(fd, F_SETFD, flags | FD_CLOEXEC);
}

extern "C" JNIEXPORT jintArray JNICALL
Java_com_developer_terminal_terminal_PtyProcess_createSubprocess(
    JNIEnv* env, jclass, jstring cmd_jstr, jstring cwd_jstr,
    jobjectArray args_jarr, jobjectArray env_jarr, jint rows, jint cols
) {
    const char* cmd = env->GetStringUTFChars(cmd_jstr, nullptr);
    const char* cwd = env->GetStringUTFChars(cwd_jstr, nullptr);
    int argc = args_jarr ? env->GetArrayLength(args_jarr) : 0;
    char** argv = (char**)calloc((size_t)argc + 1, sizeof(char*));
    for (int i = 0; i < argc; i++) {
        jstring s = (jstring)env->GetObjectArrayElement(args_jarr, i);
        const char* cs = env->GetStringUTFChars(s, nullptr);
        argv[i] = strdup(cs);
        env->ReleaseStringUTFChars(s, cs);
        env->DeleteLocalRef(s);
    }
    argv[argc] = nullptr;

    int envc = env_jarr ? env->GetArrayLength(env_jarr) : 0;
    char** envp = (char**)calloc((size_t)envc + 1, sizeof(char*));
    for (int i = 0; i < envc; i++) {
        jstring s = (jstring)env->GetObjectArrayElement(env_jarr, i);
        const char* cs = env->GetStringUTFChars(s, nullptr);
        envp[i] = strdup(cs);
        env->ReleaseStringUTFChars(s, cs);
        env->DeleteLocalRef(s);
    }
    envp[envc] = nullptr;

    int ptm = open("/dev/ptmx", O_RDWR | O_CLOEXEC);
    if (ptm < 0) {
        LOGE("open(/dev/ptmx) failed: %s", strerror(errno));
        env->ReleaseStringUTFChars(cmd_jstr, cmd);
        env->ReleaseStringUTFChars(cwd_jstr, cwd);
        return nullptr;
    }
    if (grantpt(ptm) != 0 || unlockpt(ptm) != 0) {
        close(ptm);
        env->ReleaseStringUTFChars(cmd_jstr, cmd);
        env->ReleaseStringUTFChars(cwd_jstr, cwd);
        return nullptr;
    }
    char pts_name[128] = {0};
    if (ptsname_r(ptm, pts_name, sizeof(pts_name)) != 0) {
        close(ptm);
        env->ReleaseStringUTFChars(cmd_jstr, cmd);
        env->ReleaseStringUTFChars(cwd_jstr, cwd);
        return nullptr;
    }
    struct winsize ws;
    memset(&ws, 0, sizeof(ws));
    ws.ws_row = (unsigned short)(rows > 0 ? rows : 24);
    ws.ws_col = (unsigned short)(cols > 0 ? cols : 80);
    ioctl(ptm, TIOCSWINSZ, &ws);

    pid_t pid = fork();
    if (pid < 0) {
        close(ptm);
        env->ReleaseStringUTFChars(cmd_jstr, cmd);
        env->ReleaseStringUTFChars(cwd_jstr, cwd);
        return nullptr;
    }
    if (pid == 0) {
        sigset_t unblock;
        sigfillset(&unblock);
        sigprocmask(SIG_UNBLOCK, &unblock, nullptr);
        close(ptm);
        setsid();
        int pts = open(pts_name, O_RDWR);
        if (pts < 0) _exit(127);
        ioctl(pts, TIOCSCTTY, 0);
        struct termios tios;
        if (tcgetattr(pts, &tios) == 0) {
            tios.c_iflag |= IUTF8;
            tios.c_iflag &= ~(IXON | IXOFF);
            tios.c_lflag |= (ICANON | ECHO | ECHOE | ECHOK | ISIG | IEXTEN);
            tcsetattr(pts, TCSANOW, &tios);
        }
        ioctl(pts, TIOCSWINSZ, &ws);
        dup2(pts, STDIN_FILENO);
        dup2(pts, STDOUT_FILENO);
        dup2(pts, STDERR_FILENO);
        if (pts > STDERR_FILENO) close(pts);
        if (cwd && strlen(cwd) > 0) chdir(cwd);
        execve(cmd, argv, envp);
        _exit(127);
    }
    set_cloexec(ptm);
    for (int i = 0; i < argc; i++) free(argv[i]);
    free(argv);
    for (int i = 0; i < envc; i++) free(envp[i]);
    free(envp);
    env->ReleaseStringUTFChars(cmd_jstr, cmd);
    env->ReleaseStringUTFChars(cwd_jstr, cwd);
    jintArray result = env->NewIntArray(2);
    jint vals[2] = { (jint)pid, (jint)ptm };
    env->SetIntArrayRegion(result, 0, 2, vals);
    return result;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_developer_terminal_terminal_PtyProcess_setPtyWindowSize(JNIEnv*, jclass, jint fd, jint rows, jint cols) {
    if (fd < 0) return -1;
    struct winsize ws;
    memset(&ws, 0, sizeof(ws));
    ws.ws_row = (unsigned short)(rows > 0 ? rows : 24);
    ws.ws_col = (unsigned short)(cols > 0 ? cols : 80);
    return ioctl(fd, TIOCSWINSZ, &ws);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_developer_terminal_terminal_PtyProcess_readFd(JNIEnv* env, jclass, jint fd, jbyteArray buffer, jint timeoutMs) {
    if (fd < 0) return -1;
    struct pollfd pfd;
    pfd.fd = fd; pfd.events = POLLIN; pfd.revents = 0;
    int pr = poll(&pfd, 1, timeoutMs);
    if (pr == 0) return 0;
    if (pr < 0) return (errno == EINTR) ? 0 : -1;
    if ((pfd.revents & (POLLERR | POLLHUP | POLLNVAL)) && !(pfd.revents & POLLIN)) return -2;
    jsize len = env->GetArrayLength(buffer);
    jbyte* buf = (jbyte*)malloc((size_t)len);
    if (!buf) return -1;
    ssize_t n = read(fd, buf, (size_t)len);
    if (n > 0) env->SetByteArrayRegion(buffer, 0, (jsize)n, buf);
    else if (n == 0) { free(buf); return -2; }
    else { int e = errno; free(buf); return (e == EAGAIN || e == EINTR) ? 0 : -2; }
    free(buf);
    return (jint)n;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_developer_terminal_terminal_PtyProcess_writeFd(JNIEnv* env, jclass, jint fd, jbyteArray data, jint length) {
    if (fd < 0 || length <= 0) return 0;
    jbyte* bytes = env->GetByteArrayElements(data, nullptr);
    if (!bytes) return -1;
    ssize_t total = 0;
    while (total < length) {
        ssize_t w = write(fd, bytes + total, (size_t)(length - total));
        if (w < 0) { if (errno == EINTR) continue; env->ReleaseByteArrayElements(data, bytes, JNI_ABORT); return -1; }
        total += w;
    }
    env->ReleaseByteArrayElements(data, bytes, JNI_ABORT);
    return (jint)total;
}

extern "C" JNIEXPORT void JNICALL
Java_com_developer_terminal_terminal_PtyProcess_closeFd(JNIEnv*, jclass, jint fd) {
    if (fd >= 0) close(fd);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_developer_terminal_terminal_PtyProcess_waitFor(JNIEnv*, jclass, jint pid) {
    if (pid <= 0) return -1;
    int status = 0;
    if (waitpid((pid_t)pid, &status, 0) < 0) return -1;
    if (WIFEXITED(status)) return WEXITSTATUS(status);
    if (WIFSIGNALED(status)) return 128 + WTERMSIG(status);
    return 0;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_developer_terminal_terminal_PtyProcess_sendSignal(JNIEnv*, jclass, jint pid, jint sig) {
    return (pid <= 0) ? -1 : kill((pid_t)pid, sig);
}
