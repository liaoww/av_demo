//
// Created by 廖伟炜 on 2023/8/24.
//
#include <jni.h>
#include <android/log.h>
#include <string.h>


extern "C"
{
#include "libavformat/avformat.h"
#include "libavfilter/avfilter.h"
#include "libavcodec/avcodec.h"
#include "libavutil/imgutils.h"
#include "libavutil/opt.h"
#include "libavfilter/buffersrc.h"
#include "libavfilter/buffersink.h"
}

#define LOG_TAG "FFMPEG_FILTER"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR,LOG_TAG,__VA_ARGS__)

int saveAsJPG(char *output_path, AVCodecContext *dec_ctx, AVFrame *frame) {
    FILE *imgFile = nullptr;
    AVCodecContext *imgContext = nullptr;
    AVPacket *pkt = nullptr;
    int ret = -1;
    const AVCodec *imgEncoder = avcodec_find_encoder(AV_CODEC_ID_MJPEG);
    if (!imgEncoder) {
        LOGE("Cannot create the image encoder");
        goto end;
    }

    imgContext = avcodec_alloc_context3(imgEncoder);
    if (!imgContext) {
        LOGE("Cannot create the image encoder context");
        goto end;
    }

    LOGE("pix_fmt :%d\n", dec_ctx->pix_fmt);
    LOGE("AV_PIX_FMT_YUVJ420P :%d\n", AV_PIX_FMT_YUVJ420P);
    LOGE("time base1 :%d\n", dec_ctx->time_base.den);
    LOGE("time base2 :%d\n", dec_ctx->time_base.num);
    LOGE("width :%d\n", frame->width);
    LOGE("height :%d\n", frame->height);

    // 对于MJPEG编码器来说，它支持的是YUVJ420P/YUVJ422P/YUVJ444P格式的像素
    imgContext->pix_fmt = AV_PIX_FMT_YUVJ420P;
//    imgContext->time_base = dec_ctx->time_base;
    imgContext->time_base = (AVRational) {1, 25};
    imgContext->width = frame->width;
    imgContext->height = frame->height;

    ret = avcodec_open2(imgContext, imgEncoder, nullptr);
    if (ret < 0) {
        LOGE(" avcodec open error : %s\n", av_err2str(ret));
        goto end;
    }

    ret = avcodec_send_frame(imgContext, frame);
    if (ret < 0) {
        LOGE("Cannot send the frame to context : %s", av_err2str(ret));
        goto end;
    }

    pkt = av_packet_alloc();
    ret = avcodec_receive_packet(imgContext, pkt);
    if (ret < 0) {
        LOGE("Cannot reveice the frame from context : %s", av_err2str(ret));
        goto end;
    }

    imgFile = fopen(output_path, "wb");
    if (!imgFile) {
        LOGE("fopen file : %s error", output_path);
        ret = -1;
        goto end;
    }

    fwrite(pkt->data, 1, pkt->size, imgFile);


    end:
    if (imgFile)
        fclose(imgFile);
    if (pkt)
        av_packet_free(&pkt);
    if (imgContext)
        avcodec_close(imgContext);
    return ret;
}

extern "C"
JNIEXPORT int JNICALL
Java_com_liaoww_media_jni_FFmpeg_rotation(JNIEnv *env, jclass clazz, jstring input, jstring output,
                                          jint output_rotation, jint mirror_rotation) {
    char *input_file_path = const_cast<char *>(env->GetStringUTFChars(input, nullptr));
    char *output_file_path = const_cast<char *>(env->GetStringUTFChars(output, nullptr));

    const AVCodec *avCodec;
    AVFormatContext *pFormatCtx = nullptr;
    AVCodecContext *pCodecCtx = nullptr;
    AVFrame *avFrame = nullptr;
    AVFrame *filt_frame = nullptr;
    AVPacket *pkt = nullptr;
    int stream_index;

    AVFilterContext *buffersink_ctx;
    AVFilterContext *buffersrc_ctx;

    const AVFilter *buffersrc;
    const AVFilter *buffersink;

    AVFilterInOut *outputs;
    AVFilterInOut *inputs;

    // 设置输出像素格式为pix_fmts[]中指定的格式
    enum AVPixelFormat pix_fmts[] = {AV_PIX_FMT_YUVJ420P, AV_PIX_FMT_NONE};

    //过滤器图，统筹管理所有filter实例
    AVFilterGraph *filter_graph = nullptr;

    //滤镜参数
    char args[128];
    char filters[128] = "";
    int ret = -1;

    ret = avformat_open_input(&pFormatCtx, input_file_path, nullptr, nullptr);
    if (ret != 0) {
        LOGE("can not open  : %s ", input_file_path);
        goto end;
    }

    ret = avformat_find_stream_info(pFormatCtx, NULL);
    if (ret < 0) {
        LOGE("can not find stream  : %s", av_err2str(ret));
        goto end;
    }

    stream_index = av_find_best_stream(pFormatCtx, AVMEDIA_TYPE_VIDEO, -1, -1, nullptr, 0);
    if (stream_index < 0) {
        LOGE("can not find best stream");
        ret = -1;
        goto end;
    }

    avCodec = avcodec_find_decoder(pFormatCtx->streams[stream_index]->codecpar->codec_id);
    if (!avCodec) {
        LOGE("avcodec_find_decoder error");
        ret = -1;
        goto end;
    }

    pCodecCtx = avcodec_alloc_context3(avCodec);
    if (!pCodecCtx) {
        LOGE("avcodec_alloc_context3 error");
        ret = -1;
        goto end;
    }

    ret = avcodec_parameters_to_context(pCodecCtx, pFormatCtx->streams[stream_index]->codecpar);
    if (ret < 0) {
        LOGE("avcodec_parameters_to_context error : %s", av_err2str(ret));
        goto end;
    }

    ret = avcodec_open2(pCodecCtx, avCodec, nullptr);
    if (ret < 0) {
        LOGE("Could not open codec : %s", av_err2str(ret));
        goto end;
    }

    avFrame = av_frame_alloc();
    if (!avFrame) {
        LOGE("av frame alloc error : %s", av_err2str(ret));
        ret = -1;
        goto end;
    }

    pkt = av_packet_alloc();
    while (av_read_frame(pFormatCtx, pkt) >= 0) {
        if (pkt->stream_index == stream_index) {
            ret = avcodec_send_packet(pCodecCtx, pkt);
            if (ret < 0) {
                LOGE("avcodec send packet error: %s", av_err2str(ret));
                goto end;
            }
            for (;;) {
                ret = avcodec_receive_frame(pCodecCtx, avFrame);
                LOGE("avcodec receive frame : %d", ret);
                if (ret == 0) {
                    // 成功解码出一帧
                    LOGE("decodec frame packet size : %d", avFrame->pkt_size);
                    LOGE("input frame width : %d", avFrame->width);
                    LOGE("input frame height : %d", avFrame->height);
                    break;
                } else if (ret == AVERROR(EAGAIN) || ret == AVERROR(EOF)) {
                    // EAGAIN :数据不够输出一帧，需要继续调用avcodec_send_packet
                    // EOF : 解码器中所有缓冲已经flush,结束,本次调用没有有效frame 输出,仅仅返回一个状态码
                    break;
                } else {
                    // 其他异常
                    goto end;
                }
            }
            av_packet_unref(pkt);
        }
    }



    // 特殊滤镜 用于输入输出
    buffersrc = avfilter_get_by_name("buffer");
    buffersink = avfilter_get_by_name("buffersink");

    outputs = avfilter_inout_alloc();
    inputs = avfilter_inout_alloc();

    //过滤器图，统筹管理所有filter实例
    filter_graph = avfilter_graph_alloc();

    if (!filter_graph) {
        LOGE("avfilter graph alloc error");
        goto end;
    }


    snprintf(args,
             sizeof(args),
             "video_size=%dx%d:pix_fmt=%d:time_base=%d/%d:pixel_aspect=%d/%d",
             pCodecCtx->width, pCodecCtx->height,
             pCodecCtx->pix_fmt,
             pFormatCtx->streams[stream_index]->time_base.num,
             pFormatCtx->streams[stream_index]->time_base.den,
             pCodecCtx->sample_aspect_ratio.num, pCodecCtx->sample_aspect_ratio.den);

    // 创建过滤器图的输入端，即buffer过滤器并配置相关参数
    ret = avfilter_graph_create_filter(&buffersrc_ctx, buffersrc, "in",
                                       args, nullptr, filter_graph);
    if (ret < 0) {
        LOGE("avfilter graph create filter in error : %s", av_err2str(ret));
        goto end;
    }

    // 创建过滤器图的输出端，即buffersink过滤器并配置相关参数
    ret = avfilter_graph_create_filter(&buffersink_ctx, buffersink, "out",
                                       nullptr, nullptr, filter_graph);
    if (ret < 0) {
        LOGE("avfilter graph create filter out error : %s", av_err2str(ret));
        goto end;
    }


    ret = av_opt_set_int_list(buffersink_ctx, "pix_fmts", pix_fmts,
                              AV_PIX_FMT_NONE, AV_OPT_SEARCH_CHILDREN);
    if (ret < 0) {
        LOGE("Cannot set output pixel format");
        goto end;
    }


    // 2. 将filters_descr描述的滤镜图添加到filter_graph滤镜图中

    // outputs变量意指buffersrc_ctx滤镜的输出引脚(output pad)
    // src缓冲区(buffersrc_ctx滤镜)的输出必须连到filters_descr中第一个
    // 滤镜的输入；filters_descr中第一个滤镜的输入标号未指定，故默认为
    // "in"，此处将buffersrc_ctx的输出标号也设为"in"，就实现了同标号相连
    outputs->name = av_strdup("in");
    outputs->filter_ctx = buffersrc_ctx;
    outputs->pad_idx = 0;
    outputs->next = nullptr;


    // inputs变量意指buffersink_ctx滤镜的输入引脚(input pad)
    // sink缓冲区(buffersink_ctx滤镜)的输入必须连到filters_descr中最后
    // 一个滤镜的输出；filters_descr中最后一个滤镜的输出标号未指定，故
    // 默认为"out"，此处将buffersink_ctx的输出标号也设为"out"，就实现了
    // 同标号相连
    inputs->name = av_strdup("out");
    inputs->filter_ctx = buffersink_ctx;
    inputs->pad_idx = 0;
    inputs->next = nullptr;


    // 将filter_descr字符串描述的过滤器链添加到filter grapha中
    // const char *filter_descr = "scale=78:24,transpose=cclock";
    // filter_descr表示的是一个过滤器链，scale+transpose
    if (output_rotation == 90) {
        strcat(filters, "transpose=1");
    } else if (output_rotation == 180) {
        strcat(filters, "transpose=1,transpose=1");
    } else if (output_rotation == 270) {
        strcat(filters, "transpose=2");
    }

    if (mirror_rotation == 180) {
        //添加镜像滤镜
        strcat(filters, output_rotation == 0 ? "hflip" : ",hflip");
    }

    ret = avfilter_graph_parse_ptr(filter_graph, filters,
                                   &inputs, &outputs, nullptr);
    if (ret < 0) {
        LOGE("avfilter_graph_parse_ptr error : %s", av_err2str(ret));
        goto end;
    }


    // 3. 配置filtergraph滤镜图，建立滤镜间的连接
    ret = avfilter_graph_config(filter_graph, nullptr);
    if (ret < 0) {
        LOGE("avfilter_graph_config error");
        goto end;
    }

    //将数据帧发送给滤镜
    ret = av_buffersrc_add_frame_flags(buffersrc_ctx, avFrame, AV_BUFFERSRC_FLAG_KEEP_REF);
    if (ret < 0) {
        LOGE("av buffer src add frame flags error : %s", av_err2str(ret));
        goto end;
    }

    filt_frame = av_frame_alloc();

    for (;;) {
        //取出滤镜处理后的帧
        ret = av_buffersink_get_frame(buffersink_ctx, filt_frame);
        if (ret == 0) {
            //成功
            LOGE("out put frame width : %d", filt_frame->width);
            LOGE("out put frame height : %d", filt_frame->height);
            ret = saveAsJPG(output_file_path, pCodecCtx, filt_frame);
            break;
        } else if (ret == AVERROR(EAGAIN) || ret == AVERROR_EOF) {
            //数据不够，继续发送数据
            break;
        } else if (ret < 0) {
            //失败异常
            goto end;
        }
        av_frame_unref(filt_frame);
    }
    end:
    // 关闭上下文 和 avfoamat_open_input 成对使用
    if (pFormatCtx)
        avformat_close_input(&pFormatCtx);

    // 释放解码器上下文
    if (pCodecCtx) {
        avcodec_free_context(&pCodecCtx);
    }

    // 释放AV_PACKET 本身占用的空间
    if (pkt)
        av_packet_free(&pkt);

    // 释放AvFrame
    if (avFrame)
        av_frame_free(&avFrame);

    // 释放filt_frame
    if (filt_frame)
        av_frame_free(&filt_frame);

    //释放AVFilterGraph
    if (filter_graph)
        avfilter_graph_free(&filter_graph);

    //释放pkt
    if (pkt)
        av_packet_unref(pkt);


    avfilter_inout_free(&inputs);
    avfilter_inout_free(&outputs);

    return ret;

}