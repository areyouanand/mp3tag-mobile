/*
 * JNI audio transcoder for MP3Tag Mobile.
 *
 * Wraps a statically linked LGPL FFmpeg build (libavformat/libavcodec/
 * libswresample + libmp3lame) behind a single blocking call that the Kotlin
 * side runs on an IO/Default dispatcher.
 *
 * Supports every container FFmpeg can demux, plus DSDIFF (.dff) through a
 * minimal in-house demuxer because FFmpeg 8 no longer ships one. DFF audio is
 * raw MSB-first interleaved DSD, which is exactly what the `dsd_msbf` decoder
 * expects, so we only need to locate the "DSD " chunk and feed it.
 */

#include <jni.h>
#include <stdarg.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>
#include <pthread.h>

#include <android/log.h>

#include <libavcodec/avcodec.h>
#include <libavformat/avformat.h>
#include <libswresample/swresample.h>
#include <libavutil/audio_fifo.h>
#include <libavutil/channel_layout.h>
#include <libavutil/error.h>
#include <libavutil/log.h>
#include <libavutil/opt.h>

#define LOG_TAG "Mp3tagFFmpeg"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

#define RESULT_OK 0
#define RESULT_ERROR -1
#define RESULT_CANCELLED -2

/* ------------------------------------------------------------------ */
/* global state                                                       */
/* ------------------------------------------------------------------ */

static volatile int g_cancel = 0;

static pthread_mutex_t g_err_mutex = PTHREAD_MUTEX_INITIALIZER;
static char g_last_error[1024] = {0};

static void set_error(const char *fmt, ...) {
    va_list ap;
    va_start(ap, fmt);
    pthread_mutex_lock(&g_err_mutex);
    vsnprintf(g_last_error, sizeof(g_last_error), fmt, ap);
    pthread_mutex_unlock(&g_err_mutex);
    va_end(ap);
    LOGE("%s", g_last_error);
}

static void clear_error(void) {
    pthread_mutex_lock(&g_err_mutex);
    g_last_error[0] = '\0';
    pthread_mutex_unlock(&g_err_mutex);
}

/* Route FFmpeg's own diagnostics to logcat (tag: FFmpeg). */
static void android_av_log(void *ptr, int level, const char *fmt, va_list vl) {
    (void)ptr;
    if (level > av_log_get_level()) return;
    int prio = ANDROID_LOG_INFO;
    if (level <= AV_LOG_ERROR) prio = ANDROID_LOG_ERROR;
    else if (level <= AV_LOG_WARNING) prio = ANDROID_LOG_WARN;
    else if (level <= AV_LOG_VERBOSE) prio = ANDROID_LOG_DEBUG;
    __android_log_vprint(prio, "FFmpeg", fmt, vl);
}

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    (void)vm;
    (void)reserved;
    av_log_set_callback(android_av_log);
    av_log_set_level(AV_LOG_INFO);
    return JNI_VERSION_1_6;
}

/* ------------------------------------------------------------------ */
/* DSDIFF (DFF) demuxer                                               */
/* ------------------------------------------------------------------ */

typedef struct {
    FILE *f;
    int channels;
    int sample_rate;     /* DSD rate / 8 */
    int64_t data_offset;
    int64_t data_left;   /* bytes of interleaved DSD left */
    int64_t sample_pos;
} DffInput;

static uint64_t be64(const uint8_t *p) {
    return ((uint64_t)p[0] << 56) | ((uint64_t)p[1] << 48) | ((uint64_t)p[2] << 40) |
           ((uint64_t)p[3] << 32) | ((uint64_t)p[4] << 24) | ((uint64_t)p[5] << 16) |
           ((uint64_t)p[6] << 8) | (uint64_t)p[7];
}

static uint32_t be32(const uint8_t *p) {
    return ((uint32_t)p[0] << 24) | ((uint32_t)p[1] << 16) | ((uint32_t)p[2] << 8) | (uint32_t)p[3];
}

static uint16_t be16(const uint8_t *p) {
    return (uint16_t)(((uint16_t)p[0] << 8) | (uint16_t)p[1]);
}

/* Walks the FRM8 tree far enough to find the sample rate, channel count and
 * the start of the "DSD " payload. */
static int dff_open(DffInput *d, const char *path) {
    memset(d, 0, sizeof(*d));
    FILE *f = fopen(path, "rb");
    if (!f) {
        set_error("Cannot open %s", path);
        return RESULT_ERROR;
    }
    fseek(f, 0, SEEK_END);
    long file_size = ftell(f);
    fseek(f, 0, SEEK_SET);

    uint8_t hdr[12];
    if (fread(hdr, 1, 12, f) != 12 || memcmp(hdr, "FRM8", 4) != 0) {
        fclose(f);
        set_error("Not a DSDIFF (DFF) file");
        return RESULT_ERROR;
    }
    /* The form type ("DSD ") follows the FRM8 size; real chunks start after it. */
    uint8_t form[4];
    if (fread(form, 1, 4, f) != 4) {
        fclose(f);
        set_error("Invalid DSDIFF file (truncated header)");
        return RESULT_ERROR;
    }

    long pos = 16;
    uint8_t id[4];
    uint8_t sz[8];
    int have_audio = 0;
    int sample_rate = 0;
    int channels = 0;

    while (pos + 12 <= file_size) {
        if (fseek(f, pos, SEEK_SET) != 0) break;
        if (fread(id, 1, 4, f) != 4) break;
        if (fread(sz, 1, 8, f) != 8) break;
        uint64_t size = be64(sz);
        long body = pos + 12;

        if (memcmp(id, "PROP", 4) == 0) {
            uint8_t ftype[4];
            if (fseek(f, body, SEEK_SET) != 0) break;
            if (fread(ftype, 1, 4, f) != 4) break;
            long sub = body + 4;
            long end = body + (long)size;
            while (sub + 12 <= end && sub + 12 <= file_size) {
                uint8_t sid[4], ssz[8];
                if (fseek(f, sub, SEEK_SET) != 0) break;
                if (fread(sid, 1, 4, f) != 4) break;
                if (fread(ssz, 1, 8, f) != 8) break;
                uint64_t ssize = be64(ssz);
                long sbody = sub + 12;
                if (memcmp(sid, "FS  ", 4) == 0 && ssize >= 4) {
                    uint8_t v[4];
                    if (fseek(f, sbody, SEEK_SET) == 0 && fread(v, 1, 4, f) == 4) {
                        sample_rate = (int)be32(v);
                    }
                } else if (memcmp(sid, "CHNL", 4) == 0 && ssize >= 2) {
                    uint8_t v[2];
                    if (fseek(f, sbody, SEEK_SET) == 0 && fread(v, 1, 2, f) == 2) {
                        channels = be16(v);
                    }
                }
                sub = sbody + (long)ssize + ((ssize & 1) ? 1 : 0);
            }
        } else if (memcmp(id, "DSD ", 4) == 0) {
            d->data_offset = body;
            d->data_left = (int64_t)size;
            have_audio = 1;
        } else if (memcmp(id, "DST ", 4) == 0) {
            fclose(f);
            set_error("DST-compressed DSD files are not supported");
            return RESULT_ERROR;
        }

        pos = body + (long)size + ((size & 1) ? 1 : 0);
        if (have_audio && sample_rate > 0 && channels > 0) break;
    }

    if (!have_audio || sample_rate <= 0 || channels <= 0) {
        fclose(f);
        set_error("Invalid DSDIFF file (no DSD audio chunk)");
        return RESULT_ERROR;
    }
    if (d->data_offset + d->data_left > file_size) {
        d->data_left = file_size - d->data_offset;
    }

    /* Trust that we did not run past the file when scanning; a chunk size of
     * zero at EOF is normal. */
    fseek(f, (long)d->data_offset, SEEK_SET);
    d->f = f;
    d->channels = channels;
    d->sample_rate = sample_rate / 8;
    d->sample_pos = 0;
    return RESULT_OK;
}

/* ------------------------------------------------------------------ */
/* conversion context                                                 */
/* ------------------------------------------------------------------ */

typedef struct {
    AVFormatContext *ifmt;
    int istream;
    DffInput dff;
    int is_dff;

    AVCodecContext *dec;

    AVFormatContext *ofmt;
    AVCodecContext *enc;
    AVStream *ost;

    SwrContext *swr;
    AVAudioFifo *fifo;
    AVFrame *frame;
    AVFrame *out_frame;
    AVPacket *pkt;

    int64_t next_pts;
    int64_t duration_us;

    JNIEnv *env;
    jobject cb;
    jmethodID cb_progress;
    int64_t last_progress_ms;
} Ctx;

static int64_t now_ms(void) {
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return (int64_t)ts.tv_sec * 1000 + ts.tv_nsec / 1000000;
}

static void report_progress(Ctx *c, int64_t done_us) {
    if (!c->env || !c->cb || !c->cb_progress) return;
    int64_t now = now_ms();
    if (now - c->last_progress_ms < 200) return;
    c->last_progress_ms = now;
    int pct = 0;
    if (c->duration_us > 0) {
        pct = (int)((done_us * 100) / c->duration_us);
        if (pct < 0) pct = 0;
        if (pct > 100) pct = 100;
    }
    (*c->env)->CallVoidMethod(c->env, c->cb, c->cb_progress, (jint)pct);
    if ((*c->env)->ExceptionCheck(c->env)) {
        (*c->env)->ExceptionClear(c->env);
    }
}

/* ------------------------------------------------------------------ */
/* encoder setup                                                      */
/* ------------------------------------------------------------------ */

static int pick_sample_rate(const AVCodec *codec, int requested) {    if (!codec->supported_samplerates) return requested;
    int best = codec->supported_samplerates[0];
    int bestdist = abs(best - requested);
    for (int i = 0; codec->supported_samplerates[i]; i++) {
        int r = codec->supported_samplerates[i];
        if (r == requested) return requested;
        int d = abs(r - requested);
        if (d < bestdist) {
            best = r;
            bestdist = d;
        }
    }
    return best;
}

static enum AVSampleFormat pick_sample_fmt(const AVCodec *codec) {
    if (!codec->sample_fmts) return AV_SAMPLE_FMT_S16;
    return codec->sample_fmts[0];
}

/* Keeps 24-bit integer sources at S32 for the lossless encoders that support both
 * S16 and S32 (FLAC, ALAC, WavPack), instead of always falling back to 16 bit. */
static enum AVSampleFormat pick_sample_fmt_for(const AVCodec *codec, const AVCodecContext *dec) {
    enum AVSampleFormat def = pick_sample_fmt(codec);
    if (!codec->sample_fmts || !dec) return def;
    int src_high = dec->sample_fmt == AV_SAMPLE_FMT_S32 || dec->sample_fmt == AV_SAMPLE_FMT_S32P;
    if (!src_high) return def;
    for (int i = 0; codec->sample_fmts[i] != AV_SAMPLE_FMT_NONE; i++) {
        if (codec->sample_fmts[i] == AV_SAMPLE_FMT_S32 ||
            codec->sample_fmts[i] == AV_SAMPLE_FMT_S32P) {
            return codec->sample_fmts[i];
        }
    }
    return def;
}

/* Some demuxers report a channel count without a known layout; swresample needs a
 * concrete order. */
static void normalize_layout(AVChannelLayout *layout) {
    if (layout->nb_channels > 0 && layout->order == AV_CHANNEL_ORDER_UNSPEC) {
        av_channel_layout_default(layout, layout->nb_channels);
    }
}

static int open_encoder(Ctx *c, const char *muxer, const char *encoder, int bitrate,
                        int sample_rate, int channels, int quality) {
    const AVCodec *codec = avcodec_find_encoder_by_name(encoder);
    if (!codec) {
        set_error("Encoder '%s' is not available in this build", encoder);
        return RESULT_ERROR;
    }

    c->enc = avcodec_alloc_context3(codec);
    if (!c->enc) {
        set_error("Out of memory (encoder)");
        return RESULT_ERROR;
    }

    c->enc->sample_rate = pick_sample_rate(codec, sample_rate);
    av_channel_layout_default(&c->enc->ch_layout, channels);
    c->enc->sample_fmt = pick_sample_fmt_for(codec, c->dec);
    c->enc->time_base = (AVRational){1, c->enc->sample_rate};
    c->enc->strict_std_compliance = FF_COMPLIANCE_EXPERIMENTAL;
    /* Kotlin passes kbps; FFmpeg wants bits per second. */
    if (bitrate > 0) c->enc->bit_rate = (int64_t)bitrate * 1000;

    int ret = avformat_alloc_output_context2(&c->ofmt, NULL, muxer, NULL);
    if (ret < 0 || !c->ofmt) {
        set_error("Unknown output format '%s'", muxer);
        return RESULT_ERROR;
    }
    /* MP4/M4A and ASF need the codec's global header (extradata) in codecpar
     * before avformat_write_header() writes the container header. */
    if (c->ofmt->oformat->flags & AVFMT_GLOBALHEADER) {
        c->enc->flags |= AV_CODEC_FLAG_GLOBAL_HEADER;
    }

    /* Lossless / knob-style options. */
    if (quality > 0 && c->enc->priv_data) {
        if (strcmp(codec->name, "flac") == 0) {
            av_opt_set_int(c->enc->priv_data, "compression_level", quality, 0);
        } else if (strcmp(codec->name, "wavpack") == 0) {
            av_opt_set_int(c->enc->priv_data, "compression_level", quality, 0);
        }
    }

    ret = avcodec_open2(c->enc, codec, NULL);
    if (ret < 0) {
        set_error("Cannot open encoder '%s': %s", encoder, av_err2str(ret));
        return RESULT_ERROR;
    }

    c->ost = avformat_new_stream(c->ofmt, NULL);
    if (!c->ost) {
        set_error("Out of memory (output stream)");
        return RESULT_ERROR;
    }
    c->ost->time_base = c->enc->time_base;
    ret = avcodec_parameters_from_context(c->ost->codecpar, c->enc);
    if (ret < 0) {
        set_error("Cannot copy encoder parameters: %s", av_err2str(ret));
        return RESULT_ERROR;
    }
    return RESULT_OK;
}

static int open_decoder_from_stream(Ctx *c, AVStream *st) {
    const AVCodec *codec = avcodec_find_decoder(st->codecpar->codec_id);
    if (!codec) {
        set_error("No decoder for input codec %s",
                  avcodec_get_name(st->codecpar->codec_id));
        return RESULT_ERROR;
    }
    c->dec = avcodec_alloc_context3(codec);
    if (!c->dec) {
        set_error("Out of memory (decoder)");
        return RESULT_ERROR;
    }
    int ret = avcodec_parameters_to_context(c->dec, st->codecpar);
    if (ret < 0) {
        set_error("Cannot copy decoder parameters: %s", av_err2str(ret));
        return RESULT_ERROR;
    }
    c->dec->pkt_timebase = st->time_base;
    ret = avcodec_open2(c->dec, codec, NULL);
    if (ret < 0) {
        set_error("Cannot open decoder '%s': %s", codec->name, av_err2str(ret));
        return RESULT_ERROR;
    }
    normalize_layout(&c->dec->ch_layout);
    return RESULT_OK;
}

/* ------------------------------------------------------------------ */
/* resample + encode                                                  */
/* ------------------------------------------------------------------ */

static int drain_encoder(Ctx *c) {
    int ret;
    while (1) {
        ret = avcodec_receive_packet(c->enc, c->pkt);
        if (ret == AVERROR(EAGAIN) || ret == AVERROR_EOF) return 0;
        if (ret < 0) {
            set_error("Encoding failed: %s", av_err2str(ret));
            return RESULT_ERROR;
        }
        av_packet_rescale_ts(c->pkt, c->enc->time_base, c->ost->time_base);
        c->pkt->stream_index = c->ost->index;
        ret = av_interleaved_write_frame(c->ofmt, c->pkt);
        av_packet_unref(c->pkt);
        if (ret < 0) {
            set_error("Writing output failed: %s", av_err2str(ret));
            return RESULT_ERROR;
        }
    }
}

static int send_out_frame(Ctx *c) {
    int ret = avcodec_send_frame(c->enc, c->out_frame);
    if (ret < 0) {
        set_error("Encoder rejected frame: %s", av_err2str(ret));
        return RESULT_ERROR;
    }
    return drain_encoder(c);
}

static int send_fifo_frames(Ctx *c, int flushing);

static int encode_decoded_frame(Ctx *c, AVFrame *frame) {
    av_frame_unref(c->out_frame);
    int max_out = swr_get_out_samples(c->swr, frame->nb_samples);
    if (max_out < 0) max_out = frame->nb_samples + 4096;

    c->out_frame->format = c->enc->sample_fmt;
    c->out_frame->sample_rate = c->enc->sample_rate;
    av_channel_layout_copy(&c->out_frame->ch_layout, &c->enc->ch_layout);
    c->out_frame->nb_samples = max_out;
    int ret = av_frame_get_buffer(c->out_frame, 0);
    if (ret < 0) {
        set_error("Out of memory (resample buffer)");
        return RESULT_ERROR;
    }

    int n = swr_convert(c->swr, c->out_frame->extended_data, max_out,
                        (const uint8_t **)frame->extended_data, frame->nb_samples);
    if (n < 0) {
        set_error("Resampling failed: %s", av_err2str(n));
        return RESULT_ERROR;
    }
    if (n == 0) return RESULT_OK;
    if (av_audio_fifo_write(c->fifo, (void **)c->out_frame->extended_data, n) < n) {
        set_error("Out of memory (sample FIFO)");
        return RESULT_ERROR;
    }
    return send_fifo_frames(c, 0);
}

/* Encoders such as libmp3lame/AAC/FLAC only accept frames of exactly
 * avctx->frame_size samples (a shorter frame is allowed for the final one), so
 * resampled audio is staged in a FIFO and cut into whole frames. */
static int send_fifo_frames(Ctx *c, int flushing) {
    for (;;) {
        int avail = av_audio_fifo_size(c->fifo);
        if (avail <= 0) return RESULT_OK;

        const int frame_size = c->enc->frame_size;
        int want;
        if (frame_size <= 0) {
            want = avail;
        } else if (avail >= frame_size) {
            want = frame_size;
        } else if (flushing) {
            want = avail;
        } else {
            return RESULT_OK;
        }

        av_frame_unref(c->out_frame);
        c->out_frame->format = c->enc->sample_fmt;
        c->out_frame->sample_rate = c->enc->sample_rate;
        av_channel_layout_copy(&c->out_frame->ch_layout, &c->enc->ch_layout);
        c->out_frame->nb_samples = want;
        int ret = av_frame_get_buffer(c->out_frame, 0);
        if (ret < 0) {
            set_error("Out of memory (frame buffer)");
            return RESULT_ERROR;
        }
        if (av_audio_fifo_read(c->fifo, (void **)c->out_frame->extended_data, want) < want) {
            set_error("Sample FIFO underrun");
            return RESULT_ERROR;
        }
        c->out_frame->pts = c->next_pts;
        c->next_pts += want;
        ret = send_out_frame(c);
        if (ret != RESULT_OK) return ret;
        if (frame_size > 0 && avail < frame_size) return RESULT_OK;
    }
}

static int flush_resampler(Ctx *c) {
    int ret;
    while (1) {
        av_frame_unref(c->out_frame);
        c->out_frame->format = c->enc->sample_fmt;
        c->out_frame->sample_rate = c->enc->sample_rate;
        av_channel_layout_copy(&c->out_frame->ch_layout, &c->enc->ch_layout);
        c->out_frame->nb_samples = 4096;
        ret = av_frame_get_buffer(c->out_frame, 0);
        if (ret < 0) return RESULT_ERROR;
        int n = swr_convert(c->swr, c->out_frame->extended_data, 4096, NULL, 0);
        if (n < 0) {
            set_error("Resampler flush failed: %s", av_err2str(n));
            return RESULT_ERROR;
        }
        if (n == 0) return RESULT_OK;
        if (av_audio_fifo_write(c->fifo, (void **)c->out_frame->extended_data, n) < n) {
            set_error("Out of memory (sample FIFO)");
            return RESULT_ERROR;
        }
        ret = send_fifo_frames(c, 0);
        if (ret != RESULT_OK) return ret;
    }
}

static int decode_and_encode(Ctx *c, AVPacket *pkt, int64_t progress_us) {
    int ret = avcodec_send_packet(c->dec, pkt);
    if (ret < 0) {
        set_error("Decoder rejected packet: %s", av_err2str(ret));
        return RESULT_ERROR;
    }
    while (1) {
        ret = avcodec_receive_frame(c->dec, c->frame);
        if (ret == AVERROR(EAGAIN) || ret == AVERROR_EOF) break;
        if (ret < 0) {
            set_error("Decoding failed: %s", av_err2str(ret));
            return RESULT_ERROR;
        }
        ret = encode_decoded_frame(c, c->frame);
        av_frame_unref(c->frame);
        if (ret != RESULT_OK) return ret;
    }
    report_progress(c, progress_us);
    return g_cancel ? RESULT_CANCELLED : RESULT_OK;
}

static int flush_decoder(Ctx *c) {
    int ret = avcodec_send_packet(c->dec, NULL);
    if (ret < 0 && ret != AVERROR_EOF) return RESULT_ERROR;
    while (1) {
        ret = avcodec_receive_frame(c->dec, c->frame);
        if (ret == AVERROR(EAGAIN) || ret == AVERROR_EOF) break;
        if (ret < 0) {
            set_error("Decoding failed: %s", av_err2str(ret));
            return RESULT_ERROR;
        }
        ret = encode_decoded_frame(c, c->frame);
        av_frame_unref(c->frame);
        if (ret != RESULT_OK) return ret;
    }
    return RESULT_OK;
}

/* ------------------------------------------------------------------ */
/* main entry                                                         */
/* ------------------------------------------------------------------ */

static int looks_like_dff(const char *path) {
    FILE *f = fopen(path, "rb");
    if (!f) return 0;
    char magic[4] = {0};
    int ok = fread(magic, 1, 4, f) == 4 && memcmp(magic, "FRM8", 4) == 0;
    fclose(f);
    return ok;
}

static int do_transcode(JNIEnv *env, const char *in_path, const char *out_path,
                        const char *muxer, const char *encoder, int bitrate,
                        int sample_rate, int channels, int quality,
                        jobject cb, int64_t *elapsed_ms) {
    Ctx c;
    memset(&c, 0, sizeof(c));
    c.istream = -1;
    c.env = env;
    c.cb = cb;
    int result = RESULT_ERROR;
    int64_t started = now_ms();

    if (cb) {
        jclass cls = (*env)->GetObjectClass(env, cb);
        c.cb_progress = (*env)->GetMethodID(env, cls, "onProgress", "(I)V");
        (*env)->DeleteLocalRef(env, cls);
        if ((*env)->ExceptionCheck(env)) {
            (*env)->ExceptionClear(env);
            c.cb_progress = NULL;
        }
    }

    AVStream *ist = NULL;
    if (looks_like_dff(in_path)) {
        if (dff_open(&c.dff, in_path) != RESULT_OK) goto done;
        c.is_dff = 1;
        const AVCodec *codec = avcodec_find_decoder(AV_CODEC_ID_DSD_MSBF);
        if (!codec) {
            set_error("DSD decoder is not available in this build");
            goto done;
        }
        c.dec = avcodec_alloc_context3(codec);
        if (!c.dec) {
            set_error("Out of memory (decoder)");
            goto done;
        }
        c.dec->sample_rate = c.dff.sample_rate;
        av_channel_layout_default(&c.dec->ch_layout, c.dff.channels);
        if (avcodec_open2(c.dec, codec, NULL) < 0) {
            set_error("Cannot open DSD decoder");
            goto done;
        }
        int64_t bits = (int64_t)c.dff.data_left;
        c.duration_us = bits > 0 ? (bits / c.dff.channels) * 1000000 / c.dff.sample_rate : 0;
        if (channels <= 0) channels = c.dff.channels;
        if (sample_rate <= 0) sample_rate = 44100;
    } else {
        int ret = avformat_open_input(&c.ifmt, in_path, NULL, NULL);
        if (ret < 0) {
            set_error("Cannot read %s: %s", in_path, av_err2str(ret));
            goto done;
        }
        ret = avformat_find_stream_info(c.ifmt, NULL);
        if (ret < 0) {
            set_error("Cannot parse %s: %s", in_path, av_err2str(ret));
            goto done;
        }
        ret = av_find_best_stream(c.ifmt, AVMEDIA_TYPE_AUDIO, -1, -1, NULL, 0);
        if (ret < 0) {
            set_error("No audio stream found in %s", in_path);
            goto done;
        }
        c.istream = ret;
        ist = c.ifmt->streams[c.istream];
        if (open_decoder_from_stream(&c, ist) != RESULT_OK) goto done;
        if (c.ifmt->duration > 0) {
            c.duration_us = c.ifmt->duration;
        } else if (ist->duration != AV_NOPTS_VALUE && ist->duration > 0) {
            c.duration_us = av_rescale_q(ist->duration, ist->time_base, AV_TIME_BASE_Q);
        }
        if (channels <= 0) channels = ist->codecpar->ch_layout.nb_channels;
        if (sample_rate <= 0) sample_rate = ist->codecpar->sample_rate;
    }

    if (channels <= 0) channels = 2;
    if (sample_rate <= 0) sample_rate = 44100;

    /* Lossy targets are limited to 2 channels by their encoders. */
    if (channels > 2 && (strcmp(encoder, "libmp3lame") == 0 || strcmp(encoder, "aac") == 0 ||
                         strcmp(encoder, "libopus") == 0 || strcmp(encoder, "vorbis") == 0 ||
                         strcmp(encoder, "wmav2") == 0)) {
        channels = 2;
    }

    if (open_encoder(&c, muxer, encoder, bitrate, sample_rate, channels, quality) != RESULT_OK)
        goto done;

    /* Resampler: decoder output -> encoder input. */
    int ret = swr_alloc_set_opts2(&c.swr, &c.enc->ch_layout, c.enc->sample_fmt,
                                  c.enc->sample_rate, &c.dec->ch_layout, c.dec->sample_fmt,
                                  c.dec->sample_rate, 0, NULL);
    if (ret < 0 || !c.swr) {
        set_error("Cannot create resampler: %s", av_err2str(ret));
        goto done;
    }
    ret = swr_init(c.swr);
    if (ret < 0) {
        set_error("Cannot initialise resampler: %s", av_err2str(ret));
        goto done;
    }

    c.frame = av_frame_alloc();
    c.out_frame = av_frame_alloc();
    c.pkt = av_packet_alloc();
    c.fifo = av_audio_fifo_alloc(c.enc->sample_fmt, c.enc->ch_layout.nb_channels, 4096);
    if (!c.frame || !c.out_frame || !c.pkt || !c.fifo) {
        set_error("Out of memory");
        goto done;
    }

    ret = avio_open(&c.ofmt->pb, out_path, AVIO_FLAG_WRITE);
    if (ret < 0) {
        set_error("Cannot create %s: %s", out_path, av_err2str(ret));
        goto done;
    }
    ret = avformat_write_header(c.ofmt, NULL);
    if (ret < 0) {
        set_error("Cannot write header: %s", av_err2str(ret));
        goto done;
    }

    if (c.is_dff) {
        int64_t chunk = 32768;
        int64_t total = c.dff.data_left;
        while (total > 0) {
            if (g_cancel) {
                result = RESULT_CANCELLED;
                goto done;
            }
            int64_t n = chunk - (chunk % c.dff.channels);
            if (n > total) n = total;
            if (av_new_packet(c.pkt, (int)n) < 0) {
                set_error("Out of memory (packet)");
                goto done;
            }
            size_t got = fread(c.pkt->data, 1, (size_t)n, c.dff.f);
            if (got == 0) {
                av_packet_unref(c.pkt);
                break;
            }
            c.pkt->size = (int)got;
            c.pkt->pts = c.dff.sample_pos;
            c.pkt->duration = got / c.dff.channels;
            int64_t progress_us = (int64_t)c.dff.sample_pos * 1000000 / c.dff.sample_rate;
            c.dff.sample_pos += got / c.dff.channels;
            total -= (int64_t)got;
            ret = decode_and_encode(&c, c.pkt, progress_us);
            av_packet_unref(c.pkt);
            if (ret != RESULT_OK) {
                result = ret;
                goto done;
            }
        }
    } else {
        while (1) {
            if (g_cancel) {
                result = RESULT_CANCELLED;
                goto done;
            }
            ret = av_read_frame(c.ifmt, c.pkt);
            if (ret == AVERROR_EOF) break;
            if (ret < 0) {
                set_error("Read error: %s", av_err2str(ret));
                goto done;
            }
            if (c.pkt->stream_index != c.istream) {
                av_packet_unref(c.pkt);
                continue;
            }
            int64_t ts = c.pkt->pts != AV_NOPTS_VALUE ? c.pkt->pts : c.pkt->dts;
            int64_t progress_us = 0;
            if (ts != AV_NOPTS_VALUE) {
                progress_us = av_rescale_q(ts, ist->time_base, AV_TIME_BASE_Q);
            }
            ret = decode_and_encode(&c, c.pkt, progress_us);
            av_packet_unref(c.pkt);
            if (ret != RESULT_OK) {
                result = ret;
                goto done;
            }
        }
    }

    if (g_cancel) {
        result = RESULT_CANCELLED;
        goto done;
    }

    if (flush_decoder(&c) != RESULT_OK) goto done;
    if (flush_resampler(&c) != RESULT_OK) goto done;
    if (send_fifo_frames(&c, 1) != RESULT_OK) goto done;

    ret = avcodec_send_frame(c.enc, NULL);
    if (ret >= 0) drain_encoder(&c);
    av_write_trailer(c.ofmt);
    avio_closep(&c.ofmt->pb);
    result = RESULT_OK;

done:
    if (c.fifo) av_audio_fifo_free(c.fifo);
    if (c.swr) swr_free(&c.swr);
    if (c.frame) av_frame_free(&c.frame);
    if (c.out_frame) av_frame_free(&c.out_frame);
    if (c.pkt) av_packet_free(&c.pkt);
    if (c.enc) avcodec_free_context(&c.enc);
    if (c.dec) avcodec_free_context(&c.dec);
    if (c.ofmt) avformat_free_context(c.ofmt);
    if (c.ifmt) avformat_close_input(&c.ifmt);
    if (c.dff.f) fclose(c.dff.f);
    *elapsed_ms = now_ms() - started;
    return result;
}

/* ------------------------------------------------------------------ */
/* Streaming mono decoder used by the spectrum / ReplayGain analyzers  */
/* ------------------------------------------------------------------ */

typedef struct {
    AVFormatContext *fmt;
    int istream;
    DffInput dff;
    int is_dff;

    AVCodecContext *dec;
    SwrContext *swr;
    AVFrame *frame;
    AVPacket *pkt;

    int demux_eof;
    int flush_sent;
    int decoder_eof;

    int sample_rate;
    int channels;
    int64_t duration_us;

    float *pending;
    int pending_count;
    int pending_cap;

    uint64_t or_bits;
    int or_width;
    int source_bits;
} DecSession;

static void ds_free(DecSession *d) {
    if (!d) return;
    if (d->swr) swr_free(&d->swr);
    if (d->frame) av_frame_free(&d->frame);
    if (d->pkt) av_packet_free(&d->pkt);
    if (d->dec) avcodec_free_context(&d->dec);
    if (d->fmt) avformat_close_input(&d->fmt);
    if (d->dff.f) fclose(d->dff.f);
    av_freep(&d->pending);
    av_free(d);
}

static int ds_reserve(DecSession *d, int extra) {
    if (d->pending_count + extra <= d->pending_cap) return 0;
    int cap = d->pending_cap ? d->pending_cap : 8192;
    while (cap < d->pending_count + extra) cap *= 2;
    float *grown = av_realloc_array(d->pending, (size_t)cap, sizeof(float));
    if (!grown) return -1;
    d->pending = grown;
    d->pending_cap = cap;
    return 0;
}

/* OR of every integer sample, so the bit-depth check works without MediaCodec.
 * Float output (lossy codecs, DSD) leaves or_width == 0. */
static void ds_accumulate_stats(DecSession *d, const AVFrame *f) {
    int width;
    switch (f->format) {
    case AV_SAMPLE_FMT_S16:
    case AV_SAMPLE_FMT_S16P: width = 16; break;
    case AV_SAMPLE_FMT_S32:
    case AV_SAMPLE_FMT_S32P: width = 32; break;
    case AV_SAMPLE_FMT_S64:
    case AV_SAMPLE_FMT_S64P: width = 64; break;
    default: return;
    }
    d->or_width = width;
    int planes = av_sample_fmt_is_planar(f->format) ? f->ch_layout.nb_channels : 1;
    for (int p = 0; p < planes; p++) {
        const uint8_t *base = f->extended_data[p];
        if (!base) continue;
        for (int i = 0; i < f->nb_samples; i++) {
            if (width == 16) d->or_bits |= (uint16_t)((const int16_t *)base)[i];
            else if (width == 32) d->or_bits |= (uint32_t)((const int32_t *)base)[i];
            else d->or_bits |= (uint64_t)((const int64_t *)base)[i];
        }
    }
}

static int ds_resample(DecSession *d, const AVFrame *frame) {
    int max_out = swr_get_out_samples(d->swr, frame->nb_samples);
    if (max_out <= 0) max_out = frame->nb_samples + 1024;
    if (ds_reserve(d, max_out) < 0) {
        set_error("Out of memory (analysis buffer)");
        return -1;
    }
    uint8_t *out_planes[1] = {(uint8_t *)(d->pending + d->pending_count)};
    int n = swr_convert(d->swr, out_planes, max_out,
                        (const uint8_t **)frame->extended_data, frame->nb_samples);
    if (n < 0) {
        set_error("Resampling failed: %s", av_err2str(n));
        return -1;
    }
    d->pending_count += n;
    return 0;
}

/* 1 = packet, 0 = end of input, -1 = error */
static int ds_next_packet(DecSession *d, AVPacket *pkt) {
    if (d->is_dff) {
        if (d->dff.data_left <= 0) return 0;
        int64_t chunk = 32768;
        if (chunk > d->dff.data_left) chunk = d->dff.data_left;
        if (av_new_packet(pkt, (int)chunk) < 0) {
            set_error("Out of memory (packet)");
            return -1;
        }
        size_t got = fread(pkt->data, 1, (size_t)chunk, d->dff.f);
        if (got == 0) {
            av_packet_unref(pkt);
            return 0;
        }
        pkt->size = (int)got;
        pkt->pts = d->dff.sample_pos;
        pkt->duration = got / d->dff.channels;
        d->dff.sample_pos += got / d->dff.channels;
        d->dff.data_left -= (int64_t)got;
        return 1;
    }
    for (;;) {
        int ret = av_read_frame(d->fmt, pkt);
        if (ret == AVERROR_EOF) return 0;
        if (ret < 0) {
            set_error("Read error: %s", av_err2str(ret));
            return -1;
        }
        if (pkt->stream_index == d->istream) return 1;
        av_packet_unref(pkt);
    }
}

/* Fills `out` with up to `max` mono float samples; 0 means end of stream. */
static int ds_read(DecSession *d, float *out, int max) {
    int produced = 0;
    while (produced < max) {
        if (d->pending_count > 0) {
            int n = d->pending_count;
            if (n > max - produced) n = max - produced;
            memcpy(out + produced, d->pending, (size_t)n * sizeof(float));
            d->pending_count -= n;
            if (d->pending_count > 0) {
                memmove(d->pending, d->pending + n, (size_t)d->pending_count * sizeof(float));
            }
            produced += n;
            continue;
        }
        if (d->decoder_eof) break;

        int ret = avcodec_receive_frame(d->dec, d->frame);
        if (ret == 0) {
            ds_accumulate_stats(d, d->frame);
            int rr = ds_resample(d, d->frame);
            av_frame_unref(d->frame);
            if (rr < 0) break;
            continue;
        }
        if (ret == AVERROR_EOF) {
            d->decoder_eof = 1;
            break;
        }
        if (ret != AVERROR(EAGAIN)) {
            set_error("Decoding failed: %s", av_err2str(ret));
            d->decoder_eof = 1;
            break;
        }

        if (d->flush_sent) {
            d->decoder_eof = 1;
            break;
        }
        int pr = ds_next_packet(d, d->pkt);
        if (pr == 1) {
            avcodec_send_packet(d->dec, d->pkt);
            av_packet_unref(d->pkt);
            continue;
        }
        avcodec_send_packet(d->dec, NULL);
        d->flush_sent = 1;
        if (pr < 0) d->demux_eof = 1;
    }
    return produced;
}

static DecSession *ds_open(const char *path, double window_seconds) {
    DecSession *d = av_mallocz(sizeof(*d));
    if (!d) {
        set_error("Out of memory");
        return NULL;
    }
    d->istream = -1;

    if (looks_like_dff(path)) {
        if (dff_open(&d->dff, path) != RESULT_OK) {
            ds_free(d);
            return NULL;
        }
        d->is_dff = 1;
        const AVCodec *codec = avcodec_find_decoder(AV_CODEC_ID_DSD_MSBF);
        if (!codec) {
            set_error("DSD decoder is not available in this build");
            ds_free(d);
            return NULL;
        }
        d->dec = avcodec_alloc_context3(codec);
        if (!d->dec) {
            set_error("Out of memory (decoder)");
            ds_free(d);
            return NULL;
        }
        d->dec->sample_rate = d->dff.sample_rate;
        av_channel_layout_default(&d->dec->ch_layout, d->dff.channels);
        if (avcodec_open2(d->dec, codec, NULL) < 0) {
            set_error("Cannot open DSD decoder");
            ds_free(d);
            return NULL;
        }
        d->sample_rate = d->dff.sample_rate;
        d->channels = d->dff.channels;
        d->duration_us = (d->dff.data_left / d->channels) * 1000000LL / d->dff.sample_rate;
    } else {
        int ret = avformat_open_input(&d->fmt, path, NULL, NULL);
        if (ret < 0) {
            set_error("Cannot read file: %s", av_err2str(ret));
            ds_free(d);
            return NULL;
        }
        if (avformat_find_stream_info(d->fmt, NULL) < 0) {
            set_error("Cannot parse file");
            ds_free(d);
            return NULL;
        }
        ret = av_find_best_stream(d->fmt, AVMEDIA_TYPE_AUDIO, -1, -1, NULL, 0);
        if (ret < 0) {
            set_error("No audio stream found");
            ds_free(d);
            return NULL;
        }
        d->istream = ret;
        AVStream *st = d->fmt->streams[ret];
        const AVCodec *codec = avcodec_find_decoder(st->codecpar->codec_id);
        if (!codec) {
            set_error("No decoder for %s", avcodec_get_name(st->codecpar->codec_id));
            ds_free(d);
            return NULL;
        }
        d->dec = avcodec_alloc_context3(codec);
        if (!d->dec) {
            set_error("Out of memory (decoder)");
            ds_free(d);
            return NULL;
        }
        if (avcodec_parameters_to_context(d->dec, st->codecpar) < 0 ||
            avcodec_open2(d->dec, codec, NULL) < 0) {
            set_error("Cannot open decoder '%s'", codec->name);
            ds_free(d);
            return NULL;
        }
        normalize_layout(&d->dec->ch_layout);
        d->sample_rate = d->dec->sample_rate;
        d->channels = d->dec->ch_layout.nb_channels;
        if (d->fmt->duration > 0) {
            d->duration_us = d->fmt->duration;
        } else if (st->duration != AV_NOPTS_VALUE) {
            d->duration_us = av_rescale_q(st->duration, st->time_base, AV_TIME_BASE_Q);
        }
    }

    if (d->sample_rate <= 0 || d->channels <= 0) {
        set_error("Invalid audio stream (%d Hz, %d channels)", d->sample_rate, d->channels);
        ds_free(d);
        return NULL;
    }

    d->source_bits = d->dec->bits_per_raw_sample > 0
                         ? d->dec->bits_per_raw_sample
                         : av_get_bits_per_sample(d->dec->codec_id);

    /* Middle-of-file window, exactly like the old MediaCodec path. */
    if (window_seconds > 0 && d->duration_us > (int64_t)(window_seconds * 1e6)) {
        int64_t start_us = (d->duration_us - (int64_t)(window_seconds * 1e6)) / 2;
        if (d->is_dff) {
            int64_t skip = start_us * d->sample_rate / 1000000LL;
            int64_t skip_bytes = skip * d->channels;
            if (skip_bytes < d->dff.data_left) {
                fseek(d->dff.f, (long)(d->dff.data_offset + skip_bytes), SEEK_SET);
                d->dff.data_left -= skip_bytes;
                d->dff.sample_pos = skip;
            }
        } else {
            av_seek_frame(d->fmt, -1, start_us, AVSEEK_FLAG_BACKWARD);
            avcodec_flush_buffers(d->dec);
        }
    }

    AVChannelLayout mono = {0};
    av_channel_layout_default(&mono, 1);
    int ret = swr_alloc_set_opts2(&d->swr, &mono, AV_SAMPLE_FMT_FLT, d->sample_rate,
                                  &d->dec->ch_layout, d->dec->sample_fmt,
                                  d->dec->sample_rate, 0, NULL);
    av_channel_layout_uninit(&mono);
    if (ret < 0 || !d->swr || swr_init(d->swr) < 0) {
        set_error("Cannot create resampler");
        ds_free(d);
        return NULL;
    }

    d->frame = av_frame_alloc();
    d->pkt = av_packet_alloc();
    if (!d->frame || !d->pkt) {
        set_error("Out of memory");
        ds_free(d);
        return NULL;
    }
    return d;
}

/* ------------------------------------------------------------------ */
/* JNI exports                                                        */
/* ------------------------------------------------------------------ */

JNIEXPORT jint JNICALL
Java_com_mp3tag_mobile_data_convert_FfmpegBridge_nativeTranscode(
        JNIEnv *env, jclass cls, jstring in_path, jstring out_path, jstring muxer,
        jstring encoder, jint bitrate, jint sample_rate, jint channels, jint quality,
        jobject callback) {
    (void)cls;
    clear_error();
    g_cancel = 0;

    const char *in = (*env)->GetStringUTFChars(env, in_path, NULL);
    const char *out = (*env)->GetStringUTFChars(env, out_path, NULL);
    const char *mux = (*env)->GetStringUTFChars(env, muxer, NULL);
    const char *enc = (*env)->GetStringUTFChars(env, encoder, NULL);
    if (!in || !out || !mux || !enc) {
        set_error("Invalid arguments");
        if (in) (*env)->ReleaseStringUTFChars(env, in_path, in);
        if (out) (*env)->ReleaseStringUTFChars(env, out_path, out);
        if (mux) (*env)->ReleaseStringUTFChars(env, muxer, mux);
        if (enc) (*env)->ReleaseStringUTFChars(env, encoder, enc);
        return RESULT_ERROR;
    }

    int64_t elapsed = 0;
    int result = do_transcode(env, in, out, mux, enc, bitrate, sample_rate, channels,
                              quality, callback, &elapsed);
    LOGI("transcode %s -> %s [%s/%s] result=%d in %lld ms", in, out, mux, enc, result,
         (long long)elapsed);

    (*env)->ReleaseStringUTFChars(env, in_path, in);
    (*env)->ReleaseStringUTFChars(env, out_path, out);
    (*env)->ReleaseStringUTFChars(env, muxer, mux);
    (*env)->ReleaseStringUTFChars(env, encoder, enc);
    return result;
}

JNIEXPORT void JNICALL
Java_com_mp3tag_mobile_data_convert_FfmpegBridge_nativeCancel(JNIEnv *env, jclass cls) {
    (void)env;
    (void)cls;
    g_cancel = 1;
}

JNIEXPORT jstring JNICALL
Java_com_mp3tag_mobile_data_convert_FfmpegBridge_nativeLastError(JNIEnv *env, jclass cls) {
    (void)cls;
    pthread_mutex_lock(&g_err_mutex);
    jstring s = (*env)->NewStringUTF(env, g_last_error[0] ? g_last_error : "Unknown error");
    pthread_mutex_unlock(&g_err_mutex);
    return s;
}

JNIEXPORT jstring JNICALL
Java_com_mp3tag_mobile_data_convert_FfmpegBridge_nativeVersion(JNIEnv *env, jclass cls) {
    (void)cls;
    return (*env)->NewStringUTF(env, av_version_info());
}

/* ---- streaming analyzer decoder ------------------------------------- */

JNIEXPORT jlong JNICALL
Java_com_mp3tag_mobile_data_convert_FfmpegBridge_nativeDecodeOpen(
        JNIEnv *env, jclass cls, jstring path, jdouble window_seconds) {
    (void)cls;
    clear_error();
    const char *p = (*env)->GetStringUTFChars(env, path, NULL);
    if (!p) return 0;
    DecSession *d = ds_open(p, window_seconds);
    (*env)->ReleaseStringUTFChars(env, path, p);
    return (jlong)(intptr_t)d;
}

JNIEXPORT jfloatArray JNICALL
Java_com_mp3tag_mobile_data_convert_FfmpegBridge_nativeDecodeRead(
        JNIEnv *env, jclass cls, jlong handle, jint max_samples) {
    (void)cls;
    DecSession *d = (DecSession *)(intptr_t)handle;
    if (!d || max_samples <= 0) return NULL;
    float *buf = av_malloc_array((size_t)max_samples, sizeof(float));
    if (!buf) {
        set_error("Out of memory (analysis buffer)");
        return NULL;
    }
    int n = ds_read(d, buf, max_samples);
    if (n < 0) n = 0;
    jfloatArray arr = (*env)->NewFloatArray(env, n);
    if (arr && n > 0) {
        (*env)->SetFloatArrayRegion(env, arr, 0, n, (const jfloat *)buf);
    }
    av_free(buf);
    return arr;
}

/* [sampleRate, channels, durationUs, orBits, orWidth, sourceBits] */
JNIEXPORT jlongArray JNICALL
Java_com_mp3tag_mobile_data_convert_FfmpegBridge_nativeDecodeInfo(
        JNIEnv *env, jclass cls, jlong handle) {
    (void)cls;
    DecSession *d = (DecSession *)(intptr_t)handle;
    jlong vals[6] = {0, 0, 0, 0, 0, 0};
    if (d) {
        vals[0] = d->sample_rate;
        vals[1] = d->channels;
        vals[2] = d->duration_us;
        vals[3] = (jlong)d->or_bits;
        vals[4] = d->or_width;
        vals[5] = d->source_bits;
    }
    jlongArray arr = (*env)->NewLongArray(env, 6);
    if (arr) (*env)->SetLongArrayRegion(env, arr, 0, 6, vals);
    return arr;
}

JNIEXPORT jstring JNICALL
Java_com_mp3tag_mobile_data_convert_FfmpegBridge_nativeDecodeCodec(
        JNIEnv *env, jclass cls, jlong handle) {
    (void)cls;
    DecSession *d = (DecSession *)(intptr_t)handle;
    const char *name = (d && d->dec) ? avcodec_get_name(d->dec->codec_id) : "";
    return (*env)->NewStringUTF(env, name);
}

JNIEXPORT void JNICALL
Java_com_mp3tag_mobile_data_convert_FfmpegBridge_nativeDecodeClose(
        JNIEnv *env, jclass cls, jlong handle) {
    (void)env;
    (void)cls;
    ds_free((DecSession *)(intptr_t)handle);
}
