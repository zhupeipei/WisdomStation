package com.winsion.dispatch.data;

import android.annotation.SuppressLint;
import android.app.Application;

import com.alibaba.fastjson.JSON;
import com.lzy.okgo.OkGo;
import com.lzy.okgo.cache.CacheEntity;
import com.lzy.okgo.cache.CacheMode;
import com.lzy.okgo.callback.FileCallback;
import com.lzy.okgo.callback.StringCallback;
import com.lzy.okgo.cookie.CookieJarImpl;
import com.lzy.okgo.cookie.store.MemoryCookieStore;
import com.lzy.okgo.interceptor.HttpLoggingInterceptor;
import com.lzy.okgo.model.HttpParams;
import com.lzy.okgo.model.Progress;
import com.lzy.okgo.model.Response;
import com.lzy.okrx2.adapter.ObservableBody;
import com.winsion.dispatch.BuildConfig;
import com.winsion.dispatch.R;
import com.winsion.dispatch.application.AppApplication;
import com.winsion.dispatch.common.biz.CommonBiz;
import com.winsion.dispatch.data.constants.OpeType;
import com.winsion.dispatch.data.constants.ParamKey;
import com.winsion.dispatch.data.constants.Urls;
import com.winsion.dispatch.data.converter.ObjectConverter;
import com.winsion.dispatch.data.entity.OrderBy;
import com.winsion.dispatch.data.entity.QueryParameter;
import com.winsion.dispatch.data.entity.WhereClause;
import com.winsion.dispatch.data.listener.DownloadListener;
import com.winsion.dispatch.data.listener.ResponseListener;
import com.winsion.dispatch.data.listener.UploadListener;
import com.winsion.dispatch.media.constants.FileType;
import com.winsion.dispatch.modules.operation.entity.FileEntity;
import com.winsion.dispatch.modules.operation.entity.JobEntity;
import com.winsion.dispatch.modules.operation.entity.JobParameter;
import com.winsion.dispatch.utils.HashUtils;
import com.winsion.dispatch.utils.LogUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSession;

import io.reactivex.Observer;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import okhttp3.OkHttpClient;

/**
 * Created by wyl on 2017/6/8
 * 网络数据
 */

public class NetDataSource {
    private static final int PAGE_START = 1;
    private static final int PAGE_SIZE = 999;
    private static final int READ_TIMEOUT = 10;
    private static final int WRITE_TIMEOUT = 10;
    private static final int CONNECT_TIMEOUT = 10;
    private static final int RETRY_COUNT = 0;

    private static HashMap<Object, CompositeDisposable> requestMap = new HashMap<>();

    /**
     * 调用其他方法的前提
     */
    public static void init(Application application) {
        OkHttpClient.Builder builder = new OkHttpClient.Builder();

        // DEBUG模式下输出LOG
        if (BuildConfig.DEBUG) {
            String logTag = application.getString(R.string.app_name);
            HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor(logTag);
            // log打印级别，决定了log显示的详细程度
            loggingInterceptor.setPrintLevel(HttpLoggingInterceptor.Level.BODY);
            // log颜色级别，决定了log在控制台显示的颜色
            loggingInterceptor.setColorLevel(Level.INFO);
            builder.addInterceptor(loggingInterceptor);
        }

        // 配置超时时间
        // 全局的读取超时时间
        builder.readTimeout(READ_TIMEOUT, TimeUnit.SECONDS);
        // 全局的写入超时时间
        builder.writeTimeout(WRITE_TIMEOUT, TimeUnit.SECONDS);
        // 全局的连接超时时间
        builder.connectTimeout(CONNECT_TIMEOUT, TimeUnit.SECONDS);

        // 配置Cookie
        // 使用内存保持cookie，app退出后，cookie消失
        builder.cookieJar(new CookieJarImpl(new MemoryCookieStore()));

        // 配置Https
        // 信任所有证书
        // HttpsUtils.SSLParams sslParams = HttpsUtils.getSslSocketFactory();
        // builder.sslSocketFactory(sslParams.sSLSocketFactory, sslParams.trustManager);
        // 配置https的域名匹配规则，详细看demo的初始化介绍，不需要就不要加入，使用不当会导致https握手失败
        // builder.hostnameVerifier(new SafeHostnameVerifier());

        //必须调用初始化
        OkGo.getInstance()
                .init(application)
                //设置OkHttpClient
                .setOkHttpClient(builder.build())
                //全局统一缓存模式，默认不使用缓存，可以不传
                .setCacheMode(CacheMode.NO_CACHE)
                //全局统一缓存时间，默认永不过期，可以不传
                .setCacheTime(CacheEntity.CACHE_NEVER_EXPIRE)
                //全局统一超时重连次数，默认为三次，那么最差的情况会请求4次(一次原始请求，三次重连请求)，不需要可以设置为0
                .setRetryCount(RETRY_COUNT);
    }

    public static <T> void post(Object tag, String url, List<WhereClause> whereList, List<OrderBy> orderList,
                                String viewName, int opeCode, ResponseListener<T> listener) {
        String whereStr = "";
        if (whereList != null) {
            whereStr = JSON.toJSONString(whereList);
        }
        String orderStr = "";
        if (orderList != null) {
            orderStr = JSON.toJSONString(orderList);
        }
        QueryParameter dataParameter = new QueryParameter();
        dataParameter.setWhereClause(whereStr);
        dataParameter.setOrderBy(orderStr);
        dataParameter.setPageStart(PAGE_START);
        dataParameter.setPageSize(PAGE_SIZE);
        dataParameter.setViewName(viewName);

        long time = System.currentTimeMillis();
        String dataStr = JSON.toJSONString(dataParameter);
        String token = CacheDataSource.getToken();
        String httpKey = CacheDataSource.getHttpKey();
        String sha1Str = HashUtils.getSha1Str(dataStr + time + httpKey);

        HttpParams httpParams = new HttpParams();
        httpParams.put(ParamKey.TIME, time);
        httpParams.put(ParamKey.DATA, dataStr);
        httpParams.put(ParamKey.TOKEN, token);
        httpParams.put(ParamKey.HASH, sha1Str);
        httpParams.put(ParamKey.OPE_CODE, opeCode);
        post(tag, url, httpParams, listener);
    }

    /**
     * 不需要opeCode就传0
     *
     * @param opeCode {@link com.winsion.dispatch.data.constants.OpeCode}
     */
    public static <T> void post(Object tag, String url, Object data, int opeCode, ResponseListener<T> listener) {
        long time = System.currentTimeMillis();
        String dataStr = JSON.toJSONString(data);
        String token = CacheDataSource.getToken();
        String httpKey = CacheDataSource.getHttpKey();
        String sha1Str = HashUtils.getSha1Str(dataStr + time + httpKey);

        HttpParams httpParams = new HttpParams();
        httpParams.put(ParamKey.TIME, time);
        httpParams.put(ParamKey.DATA, dataStr);
        httpParams.put(ParamKey.TOKEN, token);
        httpParams.put(ParamKey.HASH, sha1Str);
        if (opeCode != 0) {
            httpParams.put(ParamKey.OPE_CODE, opeCode);
        }
        post(tag, url, httpParams, listener);
    }

    public static <T> void post(Object tag, String url, HttpParams httpParams, ResponseListener<T> listener) {
        url = CacheDataSource.getBaseUrl() + url;
        OkGo.<T>post(url)
                .params(httpParams)
                .converter(new ObjectConverter<>(listener))
                .adapt(new ObservableBody<>())
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(getObserver(tag, url, listener));
    }

    public static <T> void get(Object tag, String url, HttpParams httpParams, ResponseListener<T> listener) {
        url = CacheDataSource.getBaseUrl() + url;
        OkGo.<T>get(url)
                .params(httpParams)
                .converter(new ObjectConverter<>(listener))
                .adapt(new ObservableBody<>())
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(getObserver(tag, url, listener));
    }

    public static void uploadFileNoData(Object tag, File file, UploadListener uploadListener) {
        long time = System.currentTimeMillis();
        String dataStr = file.getName();
        String token = CacheDataSource.getToken();
        String httpKey = CacheDataSource.getHttpKey();
        String sha1Str = HashUtils.getSha1Str(dataStr + time + httpKey);

        HttpParams httpParams = new HttpParams();
        httpParams.put(ParamKey.TIME, time);
        httpParams.put(ParamKey.FILE, file);
        httpParams.put(ParamKey.TOKEN, token);
        httpParams.put(ParamKey.HASH, sha1Str);
        OkGo.<String>post(CacheDataSource.getBaseUrl() + Urls.UPLOAD_SINGLE)
                .tag(tag)
                .params(httpParams)
                .execute(new StringCallback() {
                    @Override
                    public void onSuccess(Response<String> response) {
                        LogUtils.i("上传文件", file.getName() + "上传成功");
                        uploadListener.uploadSuccess(file);
                    }

                    @Override
                    public void uploadProgress(Progress progress) {
                        LogUtils.i("上传文件", file.getName() + "上传进度：" + progress.fraction + "%");
                        uploadListener.uploadProgress(file, progress.fraction);
                    }

                    @Override
                    public void onError(Response<String> response) {
                        LogUtils.i("上传文件", file.getName() + "上传失败");
                        uploadListener.uploadFailed(file);
                    }
                });
    }

    /**
     * 上传文件
     */
    public static void uploadFile(Object tag, JobEntity jobEntity, File file, UploadListener uploadListener) {
        JobParameter jobParameter = new JobParameter();
        jobParameter.setSsId(CommonBiz.getBSSID(AppApplication.getContext()));
        jobParameter.setTaskId(jobEntity.getTasksid());
        jobParameter.setOpormotId(jobEntity.getJoboperatorsid());
        jobParameter.setOpType(OpeType.RUNNING);
        jobParameter.setJobsId(jobEntity.getJobsid());
        jobParameter.setUsersId(CacheDataSource.getUserId());

        List<FileEntity> fileList = new ArrayList<>();
        FileEntity fileEntity = new FileEntity();
        fileEntity.setFileName(file.getName());
        fileEntity.setFileType(getFileType(file.getName()));
        fileList.add(fileEntity);
        jobParameter.setFileList(fileList);

        String token = CacheDataSource.getToken();
        long time = System.currentTimeMillis();
        String dataStr = JSON.toJSONString(jobParameter);
        String httpKey = CacheDataSource.getHttpKey();
        String sha1Str = HashUtils.getSha1Str(dataStr + time + httpKey);

        HttpParams httpParams = new HttpParams();
        httpParams.put(ParamKey.FILE, file);
        httpParams.put(ParamKey.TOKEN, token);
        httpParams.put(ParamKey.TIME, time);
        httpParams.put(ParamKey.DATA, dataStr);
        httpParams.put(ParamKey.HASH, sha1Str);

        OkGo.<String>post(CacheDataSource.getBaseUrl() + Urls.UPLOAD)
                .tag(tag)
                .params(httpParams)
                .execute(new StringCallback() {
                    @Override
                    public void onSuccess(Response<String> response) {
                        LogUtils.i("上传文件", file.getName() + "上传成功");
                        uploadListener.uploadSuccess(file);
                    }

                    @Override
                    public void uploadProgress(Progress progress) {
                        LogUtils.i("上传文件", file.getName() + "上传进度：" + progress.fraction + "%");
                        uploadListener.uploadProgress(file, progress.fraction);
                    }

                    @Override
                    public void onError(Response<String> response) {
                        LogUtils.i("上传文件", file.getName() + "上传失败");
                        uploadListener.uploadFailed(file);
                    }
                });
    }

    /**
     * 根据文件名返回文件类型
     *
     * @param fileName 文件名
     * @return 没有符合的返回-1
     */
    private static int getFileType(String fileName) {
        if (fileName.endsWith(".jpg")) {
            return FileType.PICTURE;
        } else if (fileName.endsWith(".mp4")) {
            return FileType.VIDEO;
        } else if (fileName.endsWith(".aac")) {
            return FileType.AUDIO;
        }
        return -1;
    }

    /**
     * 下载文件
     *
     * @param serverUri        文件服务器地址
     * @param targetDir        文件目标存储目录
     * @param downloadListener 下载状态监听
     */
    public static void downloadFile(Class tag, String serverUri, String targetDir, DownloadListener downloadListener) {
        String[] split = serverUri.split("/");
        if (split.length != 0) {
            String fileName = split[split.length - 1];
            OkGo.<File>get(serverUri)
                    .tag(tag)
                    .execute(new FileCallback(targetDir, fileName) {
                        @Override
                        public void onSuccess(Response<File> response) {
                            LogUtils.i("下载文件", fileName + "下载成功");
                            downloadListener.downloadSuccess(serverUri);
                        }

                        @Override
                        public void downloadProgress(Progress progress) {
                            LogUtils.i("下载文件", fileName + "下载进度：" + progress.fraction + "%");
                            downloadListener.downloadProgress(serverUri, progress.fraction);
                        }

                        @Override
                        public void onError(Response<File> response) {
                            LogUtils.i("下载文件", fileName + "下载失败");
                            downloadListener.downloadFailed(serverUri);
                        }
                    });
        }
    }

    private static <T> Observer<T> getObserver(Object tag, String url, ResponseListener<T> listener) {
        return new Observer<T>() {
            @Override
            public void onSubscribe(Disposable d) {
                addDisposable(tag, d);
            }

            @Override
            public void onNext(T t) {
                if (listener != null) {
                    listener.onSuccess(t);
                }
            }

            @Override
            public void onError(Throwable e) {
                LogUtils.e("WebService", "onError:::" + url + ":::" + e.toString());
                if (listener != null) {
                    listener.onFailed(0, e.getMessage());
                }
            }

            @Override
            public void onComplete() {

            }
        };
    }

    private static void addDisposable(Object tag, Disposable d) {
        if (tag != null) {
            CompositeDisposable subscription;
            if ((subscription = requestMap.get(tag)) == null) {
                subscription = new CompositeDisposable();
                requestMap.put(tag, subscription);
            }
            subscription.add(d);
        }
    }

    public static void unSubscribe(Object tag) {
        if (requestMap.containsKey(tag)) {
            requestMap.get(tag).dispose();
            requestMap.remove(tag);
        }
    }

    @SuppressLint("BadHostnameVerifier")
    private static class SafeHostnameVerifier implements HostnameVerifier {
        @Override
        public boolean verify(String hostname, SSLSession session) {
            return true;
        }
    }
}