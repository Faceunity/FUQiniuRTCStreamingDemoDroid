# FUQiniuRTCStreamingDemo（android）

## 概述

Faceunity SDK与七牛连麦SDK对接demo。

## 更新SDK

[**Nama SDK发布地址**](https://github.com/Faceunity/FULiveDemoDroid/releases),可查看Nama SDK的所有版本和发布说明。
更新方法为下载Faceunity*.zip解压后替换faceunity模块中的相应库文件。

## SDK使用介绍

 - Faceunity SDK的使用方法请参看 [**Faceunity/FULiveDemoDroid**](https://github.com/Faceunity/FULiveDemoDroid)
 - 七牛连麦SDK的使用方法请参看七牛官网。

## 集成方法

本demo中把大部分关于faceunity的代码都封装在RTCStreamingActivity中，主要实现了两个七牛的回调 StreamingPreviewCallback 与 SurfaceTextureCallback 。

### StreamingPreviewCallback

该回调用于获取摄像头产生的画面数据byte[]，同时该回调的byte[]对象会被用于推流，在该byte[]上的修改也将被推流出去。由于在该回调中没有GL环境，faceunity SDK 对画面处理的过程不能放在该回调中执行。只能把原始的camera数据复制到mCameraNV21Byte中，同时把处理过的画面数据fuImgNV21Bytes复制回回调给的byte[]对象中用于推流。

```
private StreamingPreviewCallback mStreamingPreviewCallback = new StreamingPreviewCallback() {
    @Override
    public boolean onPreviewFrame(byte[] bytes, int width, int height, int rotation, int fmt,
                long tsInNanoTime) {
        if (mCameraNV21Byte == null || fuImgNV21Bytes == null) {
            mCameraNV21Byte = new byte[bytes.length];
            fuImgNV21Bytes = new byte[bytes.length];
        }
        System.arraycopy(bytes, 0, mCameraNV21Byte, 0, bytes.length);
        System.arraycopy(fuImgNV21Bytes, 0, bytes, 0, bytes.length);
        return true;
    }
};
```

### SurfaceTextureCallback

#### onSurfaceCreated

加载Faceunity SDK所需要的数据文件（读取人脸数据文件、美颜数据文件）：

```
@Override
public void onSurfaceCreated() {
    Log.e(TAG, "onSurfaceCreated " + Thread.currentThread().getId());
    mCreateItemThread = new HandlerThread("faceunity-efect");
    mCreateItemThread.start();
    mCreateItemHandler = new CreateItemHandler(mCreateItemThread.getLooper());

    try {
        InputStream is = getAssets().open("v3.mp3");
        byte[] v3data = new byte[is.available()];
        int len = is.read(v3data);
        is.close();
        faceunity.fuSetup(v3data, null, authpack.A());
        //faceunity.fuSetMaxFaces(1);
        Log.e(TAG, "fuSetup v3 len " + len);
        Log.e(TAG, "fuSetup version " + faceunity.fuGetVersion());

        is = getAssets().open("face_beautification.mp3");
        byte[] itemData = new byte[is.available()];
        len = is.read(itemData);
        Log.e(TAG, "beautification len " + len);
        is.close();
        mFacebeautyItem = faceunity.fuCreateItemFromPackage(itemData);
        itemsArray[0] = mFacebeautyItem;

    } catch (IOException e) {
        e.printStackTrace();
    }
}
```

#### onDrawFrame

该回调返回的纹理用于预览。该回调能够获取画面的纹理数据，同时拥有GL环境，因此 faceunity SDK 对画面的处理过程都放在该回调中执行。

##### 人脸识别状态

获取人脸识别状态，判断并修改UI以提示用户。

```
final int isTracking = faceunity.fuIsTracking();
if (isTracking != faceTrackingStatus) {
    faceTrackingStatus = isTracking;
}
```

##### 道具加载

判断isNeedEffectItem（是否需要加载新道具数据flag），由于加载数据比较耗时，防止画面卡顿采用异步加载形式。

###### 发送加载道具Message

```
if (isNeedEffectItem) {
    isNeedEffectItem = false;
    mCreateItemHandler.sendEmptyMessage(CreateItemHandler.HANDLE_CREATE_ITEM);
}
```

###### 自定义Handler，收到Message异步加载道具

```
class CreateItemHandler extends Handler {

    static final int HANDLE_CREATE_ITEM = 1;

    CreateItemHandler(Looper looper) {
        super(looper);
    }

    @Override
    public void handleMessage(Message msg) {
        super.handleMessage(msg);
        switch (msg.what) {
            case HANDLE_CREATE_ITEM:
                try {
                    if (mEffectFileName.equals("none")) {
                        itemsArray[1] = mEffectItem = 0;
                    } else {
                        InputStream is = getAssets().open(mEffectFileName);
                        byte[] itemData = new byte[is.available()];
                        int len = is.read(itemData);
                        Log.e(TAG, "effect len " + len);
                        is.close();
                        final int tmp = itemsArray[1];
                        itemsArray[1] = mEffectItem = faceunity.fuCreateItemFromPackage(itemData);
                        mCameraPreviewFrameView.queueEvent(new Runnable() {
                            @Override
                            public void run() {
                                faceunity.fuItemSetParam(mEffectItem, "isAndroid", 1.0);
                                if (tmp != 0) {
                                    faceunity.fuDestroyItem(tmp);
                                }
                            }
                        });
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
                break;
        }
    }
}
```

##### 美颜参数设置

```
faceunity.fuItemSetParam(mFacebeautyItem, "color_level", mFacebeautyColorLevel);
faceunity.fuItemSetParam(mFacebeautyItem, "blur_level", mFacebeautyBlurLevel);
faceunity.fuItemSetParam(mFacebeautyItem, "filter_name", mFilterName);
faceunity.fuItemSetParam(mFacebeautyItem, "cheek_thinning", mFacebeautyCheeckThin);
faceunity.fuItemSetParam(mFacebeautyItem, "eye_enlarging", mFacebeautyEnlargeEye);
faceunity.fuItemSetParam(mFacebeautyItem, "face_shape", mFaceShape);
faceunity.fuItemSetParam(mFacebeautyItem, "face_shape_level", mFaceShapeLevel);
faceunity.fuItemSetParam(mFacebeautyItem, "red_level", mFacebeautyRedLevel);
```

##### 处理图像数据

使用fuDualInputToTexture后会得到新的texture，返回的texture类型为TEXTURE_2D，其中要求输入的图像分别以内存数组byte[]以及OpenGL纹理的方式。下方代码中mCameraNV21Byte为在PLCameraPreviewListener接口回调中获取的图像数据，i为PLVideoFilterListener接口的onDrawFrame方法的纹理ID参数，i2与i1为图像数据的宽高。

```
int fuTex = faceunity.fuDualInputToTexture(mCameraNV21Byte, i, 1,
        i2, i1, mFrameId++, new int[]{mEffectItem, mFacebeautyItem});
```

#### onSurfaceDestroyed

```
@Override
public void onSurfaceDestroyed() {
    Log.e(TAG, "onSurfaceDestroyed");
    mFrameId = 0;
    isRuning = false;

    if (mCreateItemHandler != null) {
        mCreateItemHandler.removeMessages(CreateItemHandler.HANDLE_CREATE_ITEM);
        mCreateItemHandler = null;
        mCreateItemThread.quitSafely();
        mCreateItemThread = null;
    }

    //Note: 切忌使用一个已经destroy的item
    faceunity.fuDestroyItem(mEffectItem);
    itemsArray[1] = mEffectItem = 0;
    faceunity.fuDestroyItem(mFacebeautyItem);
    itemsArray[0] = mFacebeautyItem = 0;
    faceunity.fuOnDeviceLost();
    faceunity.fuDestroyAllItems();
    isNeedEffectItem = true;

    lastOneHundredFrameTimeStamp = 0;
    oneHundredFrameFUTime = 0;
    isRuning = true;
}
```

## 切换摄像头onSurfaceDestroyed不被调用的问题

在onClickSwitchCamera方法中主动调用onSurfaceDestroyed函数。

```
mCameraPreviewFrameView.queueEvent(new Runnable() {
    @Override
    public void run() {
        mSurfaceTextureCallback.onSurfaceDestroyed();
    }
});
```