/* vcam_probe.inc — included inside vcam::anonymous after globals */
void write_diag(const char *msg) {
    write_str(kDiagPath, msg ? msg : "");
}
void write_error(const char *msg) {
    write_str(kLastErrorPath, msg ? msg : "");
}
void run_probe_if_requested() {
    if (!file_ok(kProbeReqPath)) return;
    unlink(kProbeReqPath);
    char caps[256];
    snprintf(caps, sizeof(caps),
             "egl_image=%d ncb=%d target_oes=%d oes_active=%d hooks=%d decoder=%d "
             "frames=%d tex=%u shadow=%d hwbuf=%d",
             p_eglCreateImageKHR ? 1 : 0,
             p_eglGetNativeClientBufferANDROID ? 1 : 0,
             p_glEGLImageTargetTexture2DOES ? 1 : 0,
             g_oes_active.load() ? 1 : 0,
             g_hooks_installed.load() ? 1 : 0,
             g_decoder_running.load() ? 1 : 0,
             g_frames.load(),
             (unsigned)g_vtex.load(),
             g_shadow_lib ? 1 : 0,
             g_hwbuf ? 1 : 0);
    write_str(kCapsPath, caps);
    char diag[320];
    if (!g_hooks_installed.load() && !g_shadow_lib) {
        snprintf(diag, sizeof(diag), "PROBE: ShadowHook missing. caps=[%s]", caps);
        write_error("shadowhook_missing");
    } else if (g_oes_active.load()) {
        snprintf(diag, sizeof(diag), "PROBE: OES ACTIVE frames=%d. caps=[%s]", g_frames.load(), caps);
        write_error("");
    } else if (g_egl_ext_ok) {
        snprintf(diag, sizeof(diag), "PROBE: EGL ok, OES waiting. caps=[%s]", caps);
        write_error("oes_waiting_context");
    } else {
        snprintf(diag, sizeof(diag), "PROBE: OES ext missing, 2D. caps=[%s]", caps);
        write_error("oes_ext_missing");
    }
    write_diag(diag);
    publish_telemetry();
    LOGI("%s", diag);
}
