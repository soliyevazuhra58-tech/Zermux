#include <dirent.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <sys/utsname.h>
#include <unistd.h>

static void print_usage(void) {
    printf("kestrel-utils 1.0.0 (ARM64 native multi-call helper)\n");
    printf("Applets: sysinfo, tree, wc, which\n");
}

static int cmd_sysinfo(void) {
    struct utsname u;
    if (uname(&u) == 0) {
        printf("Kernel: %s %s (%s)\n", u.sysname, u.release, u.machine);
        printf("Node:   %s\n", u.nodename);
    }
    char cwd[1024];
    if (getcwd(cwd, sizeof(cwd))) printf("CWD:    %s\n", cwd);
    printf("UID:    %d  GID: %d  PID: %d\n", getuid(), getgid(), getpid());
    return 0;
}

static void walk_tree(const char* path, int depth, int max_depth) {
    if (depth > max_depth) return;
    DIR* dir = opendir(path);
    if (!dir) return;
    struct dirent* ent;
    while ((ent = readdir(dir)) != NULL) {
        if (strcmp(ent->d_name, ".") == 0 || strcmp(ent->d_name, "..") == 0) continue;
        for (int i = 0; i < depth; i++) printf("  ");
        char full[2048];
        snprintf(full, sizeof(full), "%s/%s", path, ent->d_name);
        struct stat st;
        if (lstat(full, &st) == 0 && S_ISDIR(st.st_mode)) {
            printf("|-- %s/\n", ent->d_name);
            walk_tree(full, depth + 1, max_depth);
        } else {
            printf("|-- %s (%lld B)\n", ent->d_name, (long long)st.st_size);
        }
    }
    closedir(dir);
}

static int cmd_wc(int argc, char** argv) {
    FILE* f = (argc >= 2 && strcmp(argv[1], "-") != 0) ? fopen(argv[1], "rb") : stdin;
    if (!f) { perror("wc"); return 1; }
    long lines = 0, words = 0, bytes = 0;
    int in_word = 0, c;
    while ((c = fgetc(f)) != EOF) {
        bytes++;
        if (c == '\n') lines++;
        if (c == ' ' || c == '\n' || c == '\t' || c == '\r') in_word = 0;
        else if (!in_word) { in_word = 1; words++; }
    }
    if (f != stdin) fclose(f);
    printf("%8ld %8ld %8ld%s%s\n", lines, words, bytes, (argc >= 2 ? " " : ""), (argc >= 2 ? argv[1] : ""));
    return 0;
}

static int cmd_which(int argc, char** argv) {
    if (argc < 2) return 1;
    const char* path_env = getenv("PATH");
    if (!path_env) return 1;
    char* copy = strdup(path_env);
    char* tok = strtok(copy, ":");
    int found = 1;
    while (tok) {
        char candidate[2048];
        snprintf(candidate, sizeof(candidate), "%s/%s", tok, argv[1]);
        if (access(candidate, X_OK) == 0) { printf("%s\n", candidate); found = 0; break; }
        tok = strtok(NULL, ":");
    }
    free(copy);
    return found;
}

int main(int argc, char** argv) {
    if (argc < 2) { print_usage(); return 0; }
    const char* sub = argv[1];
    if (strcmp(sub, "sysinfo") == 0) return cmd_sysinfo();
    if (strcmp(sub, "tree") == 0) {
        const char* root = (argc >= 3) ? argv[2] : ".";
        printf("%s\n", root);
        walk_tree(root, 0, 3);
        return 0;
    }
    if (strcmp(sub, "wc") == 0) return cmd_wc(argc - 1, argv + 1);
    if (strcmp(sub, "which") == 0) return cmd_which(argc - 1, argv + 1);
    print_usage();
    return 0;
}
