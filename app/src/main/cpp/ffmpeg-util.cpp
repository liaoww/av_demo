#include <jni.h>
#include <android/log.h>
#include <queue>
#include "util.h"

extern "C"
{
#include <pthread.h>
#include <unistd.h>

#include "libavformat/avformat.h"
#include "libavfilter/avfilter.h"
#include "libavcodec/avcodec.h"
#include "libswscale/swscale.h"
#include "libswresample/swresample.h"
#include "libavutil/imgutils.h"

#include "libyuv.h"
}


#define LOG_TAG "FFMPEG"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR,LOG_TAG,__VA_ARGS__)

AVFormatContext *avformat_context = NULL;
AVCodecContext *avcodec_context = NULL;
const AVOutputFormat *ofmt = NULL;
AVStream *avvideo_stream = NULL;
AVFrame *frame = NULL;
uint8_t *out_buffer = NULL;
AVPacket *av_packet = NULL;

pthread_t decode_pthread;
pthread_mutex_t pthread_decode_mutex;
pthread_cond_t pthread_cond_decode;

volatile bool encoder_init = false;

volatile bool consumer_running = false;

std::queue<uint8_t *> yuv_buffer_list;

int pts = 0;

int avformat_write_header_result = -1;

extern "C"
void encoder(uint8_t *data, int width, int height) {
    int ret;

    if (avformat_write_header_result < 0) {
        LOGE("写文件头");
        avformat_write_header_result = avformat_write_header(avformat_context, NULL);
        if (avformat_write_header_result != AVSTREAM_INIT_IN_WRITE_HEADER) {
            LOGE("avformat write header error : %s",
                 av_err2str(avformat_write_header_result));
            return;
        }
    }


    if (!out_buffer) {
        int buffer_size = av_image_get_buffer_size(avcodec_context->pix_fmt,
                                                   avcodec_context->width,
                                                   avcodec_context->height,
                                                   1);
        LOGE("初始化buffer : %d", buffer_size);
        out_buffer = (uint8_t *) av_malloc(buffer_size);

        frame = av_frame_alloc();
        av_image_fill_arrays(frame->data,
                             frame->linesize,
                             data,
                             avcodec_context->pix_fmt,
                             avcodec_context->width,
                             avcodec_context->height,
                             1);
        frame->format = AV_PIX_FMT_YUV420P;
        frame->width = width;
        frame->height = height;

        av_packet = av_packet_alloc();
    }


    // 封装yuv帧数据
    frame->data[0] = data;                          // y数据的起始位置在数组中的索引
    frame->data[1] = data + width * height;         // u数据的起始位置在数组中的索引
    frame->data[2] = data + width * height * 5 / 4; // v数据的起始位置在数组中的索引
    frame->linesize[0] = width;                            // y数据的行宽
    frame->linesize[1] = width / 2;                        // u数据的行宽
    frame->linesize[2] = width / 2;                        // v数据的行宽
    frame->pts = pts++;

    ret = avcodec_send_frame(avcodec_context, frame); // 将yuv帧数据送入编码器
    LOGE("avcodec send frame result  : %d", ret);

    if (ret < 0) {
        LOGE("avcodec send frame error : %s", av_err2str(ret));
        return;
    }

    for (;;) {
        ret = avcodec_receive_packet(avcodec_context, av_packet); // 从编码器中取出h264帧
        if (ret == 0) {
            LOGE("解码成功 size : %d", av_packet->size);
        } else if (ret == AVERROR(EAGAIN)) {
            LOGE("EAGAIN :数据不够输出一帧，需要继续调用avcodec_send_packet");
            break;
        } else if (ret == AVERROR(EOF)) {
            LOGE("EOF : 解码器中所有缓冲已经flush,结束,本次调用没有有效frame 输出,仅仅返回一个状态码");
            break;
        } else {
            LOGE("avcodec receive packet error : %s", av_err2str(ret));
            av_packet_unref(av_packet);
            return;
        }

        av_packet_rescale_ts(av_packet, avcodec_context->time_base, avvideo_stream->time_base);
        av_packet->stream_index = avvideo_stream->index;

        // 将帧写入视频文件中，与av_write_frame的区别是,将对 packet 进行缓存和 pts 检查。
        ret = av_interleaved_write_frame(avformat_context, av_packet);
        if (ret < 0) {
            LOGE("av interleaved write frame error : %s", av_err2str(ret));
            av_packet_unref(av_packet);
            return;
        }
        LOGE("成功写入一帧 pts : %d", pts);
    }

    av_packet_unref(av_packet);
    //TODO flush
    //读取剩余的帧,解码器中会有缓存，需要frame传null,将缓存取出
    //avcodec_send_frame(avcodec_context, NULL);

}

void *consumer(void *arg) {
    LOGE("decode thread start width : %d  height : %d", avcodec_context->width,
         avcodec_context->height);

    while (consumer_running) {
        pthread_mutex_lock(&pthread_decode_mutex);
        if (yuv_buffer_list.empty()) {
            LOGE("无缓冲，等待中");
            pthread_cond_wait(&pthread_cond_decode, &pthread_decode_mutex);
        } else {
            LOGE("缓存到来，等待结束 ，开始解码");
            uint8_t *yuv_buffer = yuv_buffer_list.front();
            yuv_buffer_list.pop();
            LOGE("获取到缓冲yuv数据大小 %d , 缓冲队列剩余size : %d", sizeof(yuv_buffer), yuv_buffer_list.size());
            encoder(yuv_buffer, avcodec_context->width, avcodec_context->height);;
            free(yuv_buffer);
        }
        pthread_mutex_unlock(&pthread_decode_mutex);
    }

    while (!yuv_buffer_list.empty()) {
        uint8_t *yuv_buffer = yuv_buffer_list.front();
        yuv_buffer_list.pop();
        LOGE("flush 缓冲 size :  %d , 缓冲队列剩余size : %d", sizeof(yuv_buffer), yuv_buffer_list.size());
        encoder(yuv_buffer, avcodec_context->width, avcodec_context->height);;
        free(yuv_buffer);
    }

    LOGE("清空缓冲队列");
    std::queue<uint8_t *> empty;
    swap(empty, yuv_buffer_list);

    //写入文件尾 如果没有调用 avformat_write_header 会报错
    if (avformat_write_header_result >= 0) {
        av_write_trailer(avformat_context);
        avformat_write_header_result = -1;
        LOGE("写入文件尾");
    }

    pts = 0;

    encoder_init = false;

    if (frame) {
        av_frame_free(&frame);
    }

    if (out_buffer) {
        av_freep(out_buffer);
        out_buffer = NULL;
    }

    if (avformat_context) {
        avformat_close_input(&avformat_context);
        // 关闭输出的缓冲区的大小
        if (avformat_context && !(ofmt->flags & AVFMT_NOFILE)) {
            avio_closep(&avformat_context->pb);
        }
        avformat_free_context(avformat_context);
    }

    if (avcodec_context)
        avcodec_free_context(&avcodec_context);

    // 释放AV_PACKET 本身占用的空间
    if (av_packet)
        av_packet_free(&av_packet);
    LOGE("编码线程已终止");
    //退出线程
    pthread_mutex_destroy(&pthread_decode_mutex);
    pthread_cond_destroy(&pthread_cond_decode);
    pthread_exit(&decode_pthread);
}

extern "C"
int initEncoder(const char *out_path, int output_width, int output_height) {

    const int fps = 30;//安卓手机录制视频默认30帧


    LOGE("output path is  : %s  width is : %d height : %d\n", out_path, output_width,
         output_height);

    int ret;

    const AVCodec *codec = NULL;

    // 初始化封装格式化上下文,通过输出文件找到合适的封装格式
    ret = avformat_alloc_output_context2(&avformat_context, NULL, NULL, out_path);
    if (ret < 0) {
        LOGE("avformat alloc output context error : %s\n", av_err2str(ret));
        return ret;
    }
    ofmt = avformat_context->oformat;

    // 打开输出文件
    ret = avio_open(&avformat_context->pb, out_path, AVIO_FLAG_WRITE);
    if (ret < 0) {
        LOGE("avio open error : %s\n", av_err2str(ret));
        return ret;
    }

//    codec = avcodec_find_encoder_by_name("libx264");
//    codec = avcodec_find_encoder(AV_CODEC_ID_MPEG4);
    //FFMPEG 默认不支持 H264编码，需要手动编译X264 并链接到FFMPEG 中
    codec = avcodec_find_encoder(AV_CODEC_ID_H264);
    if (!codec) {
        LOGE("avcodec find encoder error : AV_CODEC_ID_MPEG4\n");
        return ret;
    }
    avcodec_context = avcodec_alloc_context3(codec);
    // 时间基,pts,dts的时间单位  pts(解码后帧被显示的时间), dts(视频帧送入解码器的时间)的时间单位,是两帧之间的时间间隔
    avcodec_context->time_base.den = fps; // pts
    avcodec_context->time_base.num = 1;   // 1秒
    avcodec_context->bit_rate = 400000;
    avcodec_context->gop_size = 10;
    avcodec_context->codec_id = ofmt->video_codec;

    avcodec_context->codec_type = AVMEDIA_TYPE_VIDEO; // 表示视频类型
    avcodec_context->pix_fmt = AV_PIX_FMT_YUV420P;    // 视频数据像素格式

    avcodec_context->width = output_width; // 视频宽高
    avcodec_context->height = output_height;

//    avcodec_context->qmin = 10;
//    avcodec_context->qmax = 51;

    avvideo_stream = avformat_new_stream(avformat_context, NULL); // 创建一个流
    if (!avvideo_stream) {
        LOGE("avformat new stream error s\n");
        return ret;
    }

    // 复制参数到输入流
    avcodec_parameters_from_context(avvideo_stream->codecpar, avcodec_context);

    // 初始化编解码器
    ret = avcodec_open2(avcodec_context, codec, NULL);
    if (ret < 0) {
        LOGE("avcodec open error : %s\n", av_err2str(ret));
        return ret;
    }

    //初始化相关线程
    pthread_cond_init(&pthread_cond_decode, NULL);
    pthread_mutex_init(&pthread_decode_mutex, NULL);
    pthread_create(&decode_pthread, NULL, consumer, NULL);

    consumer_running = true;
    return ret;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_liaoww_media_jni_FFmpeg_yuv2Mp4(JNIEnv *env, jclass clazz, jstring path,
                                         jbyteArray yuv_data,
                                         jint length,
                                         jint output_width, jint output_height) {

    if (!encoder_init) {
        LOGE("initEncoder");
        char *output_file = const_cast<char *>(env->GetStringUTFChars(path, nullptr));
        int width = output_width;
        int height = output_height;
        int ret = initEncoder(output_file, width, height);
        encoder_init = (ret == 0);
        env->ReleaseStringUTFChars(path, output_file);
    }

    pthread_mutex_lock(&pthread_decode_mutex);
    if (consumer_running) {
        const int width = output_width;
        const int height = output_height;

//        uint8_t *buf = reinterpret_cast<uint8_t *>(env->GetByteArrayElements(yuv_data, 0));
//        yuv_buffer_list.push(buf);

        jbyte *byte_data = new jbyte[length];
        env->GetByteArrayRegion(yuv_data, 0, length, byte_data);
        yuv_buffer_list.push((uint8_t *) byte_data);
        LOGE("yuv 入队,当前缓冲size : %d width : %d  height : %d data size : %d", yuv_buffer_list.size(),
             width, height, length);
        pthread_cond_signal(&pthread_cond_decode);
    }
    pthread_mutex_unlock(&pthread_decode_mutex);
}



extern "C"
JNIEXPORT void JNICALL
Java_com_liaoww_media_jni_FFmpeg_releaseEncoder(JNIEnv *env, jclass clazz) {
    pthread_mutex_lock(&pthread_decode_mutex);

    LOGE("native_releaseEncoder");
    consumer_running = false;
    pthread_mutex_unlock(&pthread_decode_mutex);
    pthread_cond_broadcast(&pthread_cond_decode);
}


extern "C"
JNIEXPORT void JNICALL
Java_com_liaoww_media_jni_FFmpeg_yuv2Mp4_1422(JNIEnv *env, jclass clazz, jstring path,
                                              jbyteArray yuv_data, jint length, jint width,
                                              jint height) {
    uint8_t *src_yuv_422;
    int src_stride_uyvy;
    uint8_t *dst_y;
    int dst_stride_y;
    uint8_t *dst_uv;
    int dst_stride_uv;
    libyuv::UYVYToNV12(src_yuv_422, src_stride_uyvy, dst_y, dst_stride_y, dst_uv, dst_stride_uv,
                       width, height);

}


extern "C"
jobject buildJObject(JNIEnv *env, const char *classname, ...) {
    jclass jClass;
    jobject jObject = nullptr;
    jClass = (*env).FindClass(classname);
    if (jClass != nullptr) {
        jmethodID construct_function = (*env).GetMethodID(jClass, "<init>",
                                                          "()V");
        if (construct_function != nullptr) {
            va_list args;
            va_start(args, classname);
            jObject = (*env).NewObjectV(jClass, construct_function, args);
            va_end(args);

        }
        (*env).DeleteLocalRef(jClass);
    }
    return jObject;
}

extern "C" int
callMethodByName(JNIEnv *env, jobject obj, jclass clazz, const char *name, const char *sig, ...) {
    jmethodID methodId;
    methodId = (*env).GetMethodID(clazz, name, sig);
    if (methodId == nullptr) {
        return -1;
    }
    va_list args;
    va_start(args, sig);
    (*env).CallNonvirtualVoidMethodV(obj, clazz, methodId, args);
    va_end(args);

    return 0;
}

extern "C"
JNIEXPORT jobject JNICALL
buildJClassMediaInfo(JNIEnv *env, jstring url, AVFormatContext *pFormatContext) {
    jclass cls_media_info;
    jobject obj_media_info;
    jobject obj_av_codec_info;

    jlong duration, bitrate;
    jint height, width;
    jint fps;
    AVStream *avStream;
    AVCodecParameters *codecpar;

    //封装格式信息
    duration = static_cast<long>(pFormatContext->duration / 1000);//单位为us 换算成ms
    bitrate = static_cast<long>(pFormatContext->bit_rate);//单位bps

    //输出流信息
    avStream = *pFormatContext->streams;
    codecpar = avStream->codecpar;
    // 平均帧率 1/25
    fps = static_cast<int>(avStream->avg_frame_rate.num);
    // 1/128000
    avStream->time_base;

    //codec 信息
    const AVCodec *avCodec = avcodec_find_decoder(codecpar->codec_id);
    height = static_cast<int>(codecpar->height);
    width = static_cast<int>(codecpar->width);

    //找到对应的 java class
    cls_media_info = (*env).FindClass("com/liaoww/media/jni/MediaInfo");
    if (cls_media_info == nullptr) {
        return nullptr;
    }

    //执行构造方法，传入参数url
    obj_media_info = buildJObject(env, "com/liaoww/media/jni/MediaInfo", url);

    if (obj_media_info == nullptr) {
        return nullptr;
    }

    callMethodByName(env, obj_media_info, cls_media_info, "setPath", "(Ljava/lang/String;)V", url);

    //setDuration
    callMethodByName(env, obj_media_info, cls_media_info, "setDuration", "(J)V", duration);

    //setBitrate
    callMethodByName(env, obj_media_info, cls_media_info, "setBitrate", "(J)V", bitrate);

    //setHeight
    callMethodByName(env, obj_media_info, cls_media_info, "setHeight", "(I)V", height);

    //setWidth
    callMethodByName(env, obj_media_info, cls_media_info, "setWidth", "(I)V", width);

    //setFps
    callMethodByName(env, obj_media_info, cls_media_info, "setFps", "(I)V", fps);

    //删除本地变量引用
    (*env).DeleteLocalRef(cls_media_info);
    return obj_media_info;
}


extern "C"
JNIEXPORT jobject JNICALL
Java_com_liaoww_media_jni_FFmpeg_fetchMediaInfo(JNIEnv *env, jclass clazz, jstring path) {
    const char *url = env->GetStringUTFChars(path, nullptr);

    AVFormatContext *pFormatContext = avformat_alloc_context();

    int result = avformat_open_input(&pFormatContext, url, nullptr, nullptr);

    jobject mediaInfoObject = nullptr;

    if (result == 0) {
        if (avformat_find_stream_info(pFormatContext, nullptr) == 0) {
            mediaInfoObject = buildJClassMediaInfo(env, path, pFormatContext);
        }
    }

    avformat_close_input(&pFormatContext);
    avformat_free_context(pFormatContext);
    env->ReleaseStringUTFChars(path, url);
    return mediaInfoObject;
}


