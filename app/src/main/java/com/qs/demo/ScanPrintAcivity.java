package com.qs.demo;

import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Hashtable;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.posapi.PosApi;
import android.posapi.PosApi.OnCommEventListener;
import android.posapi.PrintQueue;
import android.posapi.PrintQueue.OnPrintListener;
import android.support.v4.app.ActivityCompat;
import android.text.Layout.Alignment;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.qs.util.BarcodeCreater;
import com.qs.util.BitmapTools;
import com.qs.yspdemo.R;

/**
 * 群索PDA 扫描和打印 示范例子
 *
 * 注意：使用本例子之前，由于出厂自带的3505和5501是一直运行于后台的，会一直占用串口
 * 需要先卸掉3505和5501，避免打印和扫描串口冲突，从而导致打印失败甚至延迟
 *
 * 当安装APK时候出现Installation failed with message Invalid File:问题时，
 * 解决办法如下：
 * 1.点击工具栏上的Build中的Clean Project
 * 2.再点击工具栏上的Build中的Rebulid Project!
 * @author wsl
 *
 */
public class ScanPrintAcivity extends Activity {

	private Button mBtnOpen = null;
	private Button mBtnClose = null;
	private Button mBtnScan = null;
	private EditText mTv = null;

	private PosApi mPosSDK = null;

	private byte mGpioPower = 0x1E;// PB14
	private byte mGpioTrig = 0x29;// PC9

	private int mCurSerialNo = 3; // usart3
	private int mBaudrate = 4; // 9600

	private ScanBroadcastReceiver scanBroadcastReceiver;

	private PrintQueue mPrintQueue = null;

	MediaPlayer player;

	String str, str2, str3;

	private static final int REQUEST_EXTERNAL_STORAGE = 1;
	private static String[] PERMISSIONS_STORAGE = {
			"android.permission.READ_EXTERNAL_STORAGE",
			"android.permission.WRITE_EXTERNAL_STORAGE" };

	Bitmap btMap;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(com.qs.yspdemo.R.layout.scan_layout1);

		// 获取PosApi实例
		mPosSDK = PosApi.getInstance(this);

		// 根据型号进行初始化mPosApi类
		if (Build.MODEL.contains("LTE")
				|| android.os.Build.DISPLAY.contains("3508")
				|| android.os.Build.DISPLAY.contains("403")
				|| android.os.Build.DISPLAY.contains("35S09")) {
			mPosSDK.initPosDev("ima35s09");
		} else if (Build.MODEL.contains("5501")) {
			mPosSDK.initPosDev("ima35s12");
		} else {
			mPosSDK.initPosDev(PosApi.PRODUCT_MODEL_IMA80M01);
		}

		//监听初始化回调结果
		mPosSDK.setOnComEventListener(mCommEventListener);
		// 打印队列初始化
		mPrintQueue = new PrintQueue(this, mPosSDK);
		// 打印队列初始化
		mPrintQueue.init();
		// 打印结果监听
		mPrintQueue.setOnPrintListener(new OnPrintListener() {
			@Override
			public void onFinish() {
				// TODO Auto-generated method stub
				Toast.makeText(getApplicationContext(), "打印完成",
						Toast.LENGTH_SHORT).show();
			}

			@Override
			public void onFailed(int state) {
				// TODO Auto-generated method stub
				switch (state) {
					case PosApi.ERR_POS_PRINT_NO_PAPER:
						// 打印缺纸
						showTip("打印缺纸");
						break;
					case PosApi.ERR_POS_PRINT_FAILED:
						// 打印失败
						showTip("打印失败");
						break;
					case PosApi.ERR_POS_PRINT_VOLTAGE_LOW:
						// 电压过低
						showTip("电压过低");
						break;
					case PosApi.ERR_POS_PRINT_VOLTAGE_HIGH:
						// 电压过高
						showTip("电压过高");
						break;
				}
			}

			@Override
			public void onGetState(int arg0) {
				// TODO Auto-generated method stub

			}

			@Override
			public void onPrinterSetting(int state) {
				// TODO Auto-generated method stub
				switch (state) {
					case 0:
						Toast.makeText(ScanPrintAcivity.this, "持续有纸",
								Toast.LENGTH_SHORT).show();
						break;
					case 1:
						Toast.makeText(ScanPrintAcivity.this, "缺纸",
								Toast.LENGTH_SHORT).show();
						break;
					case 2:
						Toast.makeText(ScanPrintAcivity.this, "检测到黑标",
								Toast.LENGTH_SHORT).show();
						break;
				}
			}
		});

		// 初始化控件
		initViews();

		//注册获取扫描信息的广播接收器
		IntentFilter mFilter = new IntentFilter();
		mFilter.addAction(PosApi.ACTION_POS_COMM_STATUS);
		registerReceiver(receiver, mFilter);

		// 物理扫描键按下时候会有动作为ismart.intent.scandown的广播发出，可监听该广播实现触发扫描动作
		scanBroadcastReceiver = new ScanBroadcastReceiver();
		IntentFilter intentFilter = new IntentFilter();
		intentFilter.addAction("ismart.intent.scandown");
		this.registerReceiver(scanBroadcastReceiver, intentFilter);

		// 扫描提示音
		player = MediaPlayer.create(getApplicationContext(),
				com.qs.yspdemo.R.raw.beep);

	}

	// 控件初始化
	private void initViews() {

		mBtnOpen = (Button) this.findViewById(com.qs.yspdemo.R.id.btn_open);
		mBtnClose = (Button) this.findViewById(com.qs.yspdemo.R.id.btn_close);
		mBtnScan = (Button) this.findViewById(com.qs.yspdemo.R.id.btn_scan);
		mTv = (EditText) this.findViewById(com.qs.yspdemo.R.id.tv);

		mBtnClose.setText("保存数据");

		mBtnOpen.setText("打印文字");

		mBtnScan.setText("清空屏幕");

		mBtnOpen.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {

				// 打印文字
				printText();

			}
		});

		mBtnClose.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {

				// 打印二维码
				printQRCode();

			}
		});

		mBtnScan.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {

				// 打印一维码
				printBarCode();

			}
		});

		// 扫描按键监听
		findViewById(com.qs.yspdemo.R.id.btn_scan1).setOnClickListener(
				new OnClickListener() {
					@Override
					public void onClick(View v) {
						// TODO Auto-generated method stub
						ScanDomn();
					}
				});

		// 获取权限，在安卓6.0或者以上系统的机器上需要进行该操作，否则将会出现无法读写SD卡的情况
		verifyStoragePermissions(this);

	}

	// 打开串口以及GPIO口
	private void openDevice() {
		// open power
		mPosSDK.gpioControl(mGpioPower, 0, 1);

		mPosSDK.extendSerialInit(mCurSerialNo, mBaudrate, 1, 1, 1, 1);

	}

	// 检查读写权限
	public static void verifyStoragePermissions(Activity activity) {
		try {
			// 检测是否有写的权限
			int permission = ActivityCompat.checkSelfPermission(activity,
					"android.permission.WRITE_EXTERNAL_STORAGE");
			if (permission != PackageManager.PERMISSION_GRANTED) {
				// 没有写的权限，去申请写的权限，会弹出对话框
				ActivityCompat.requestPermissions(activity,
						PERMISSIONS_STORAGE, REQUEST_EXTERNAL_STORAGE);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	// 部分参考方法,例如字体设置，对齐方式等方法
	private void test() {

		int concentration = 60;// 打印浓度
		StringBuilder sb = new StringBuilder();
		sb.append("1234567890\n");
		PrintQueue.TextData tData = mPrintQueue.new TextData();// 构造TextData实例
		tData.addParam(PrintQueue.PARAM_TEXTSIZE_2X);// 设置两倍字体大小
		tData.addText(sb.toString());// 添加打印内容
		mPrintQueue.addText(concentration, tData);// 添加到打印队列

		tData = mPrintQueue.new TextData();// 构造TextData实例
		tData.addParam(PrintQueue.PARAM_TEXTSIZE_1X);// 设置一倍字体大小
		tData.addParam(PrintQueue.PARAM_ALIGN_MIDDLE);// 设置居中对齐
		tData.addText(sb.toString());
		mPrintQueue.addText(concentration, tData);

		tData = mPrintQueue.new TextData();
		tData.addParam(PrintQueue.PARAM_ALIGN_RIGHT);// 设置右对齐
		tData.addText(sb.toString());
		mPrintQueue.addText(concentration, tData);

		tData = mPrintQueue.new TextData();
		tData.addParam(PrintQueue.PARAM_TEXTSIZE_1X);// 设置一倍字体大小
		tData.addParam(PrintQueue.PARAM_UNDERLINE);// 下划线
		tData.addText(sb.toString());
		mPrintQueue.addText(concentration, tData);

		tData = mPrintQueue.new TextData();
		tData.addParam(PrintQueue.PARAM_ALIGN_MIDDLE);// 设置居中对齐
		tData.addParam(PrintQueue.PARAM_UNDERLINE);// 下划线
		tData.addParam(PrintQueue.PARAM_TEXTSIZE_2X);// 设置两倍字体大小
		tData.addText(sb.toString());
		mPrintQueue.addText(concentration, tData);

		tData = mPrintQueue.new TextData();
		tData.addText(sb.toString());// 直接添加打印内容 不设置参数
		mPrintQueue.addText(concentration, tData);

		mPrintQueue.printStart();// 开始队列打印

	}

	/**
	 * 打印文字000
	 */
	public void printText() {

		try {
			// 获取编辑框中的字符串1234
			str2 = mTv.getText().toString().trim();
			String[] array = str2.split("]");
			for (int i = 0; i < array.length; i++) {
				String[] array1 = array[i].split(";");
					// 直接把字符串转成byte数组，然后添加到打印队列，这里打印多个\n是为了打印的文字能够出到外面，方便客户看到
					addPrintTextWithSize(1, 50, ("牌号:" + array1[1] + "\n ").getBytes("GBK"));
                    addPrintTextWithSize(1, 50, ("规格:" + array1[2] + "\n ").getBytes("GBK"));
                    addPrintTextWithSize(1, 50, ("重量:" + array1[3] + "\n ").getBytes("GBK"));
                    addPrintTextWithSize(1, 50, ("炉批号:" + array1[4] + "\n ").getBytes("GBK"));
                    addPrintTextWithSize(1, 50, ("生产号:" + array1[5] + "\n ").getBytes("GBK"));
                    addPrintTextWithSize(1, 50, ("捆号:" + array1[6] + "\n ").getBytes("GBK"));
                    addPrintTextWithSize(1, 50, ("支数:" + array1[7] + "\n ").getBytes("GBK"));
                	addPrintTextWithSize(1, 50, ( "\n").getBytes("GBK"));
			}
			// 打印队列启动
            addPrintTextWithSize(1, 50, ("签名确认:" +  "\n ").getBytes("GBK"));
            addPrintTextWithSize(1, 50, ( "\n").getBytes("GBK"));
			mPrintQueue.printStart();
		} catch (UnsupportedEncodingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	/**
	 * 打印一维码
	 */
//	public void printBarCode() {
//
//		// 获取编辑框中的字符串
//		str2 = mTv.getText().toString().trim();
//		if (str2 == null || str2.length() <= 0)
//			return;
//
//		// 判断当前字符能否生成条码
//		if (str2.getBytes().length > str2.length()) {
//			Toast.makeText(this, "当前数据不能生成一维码", 2000).show();
//			return;
//		}
//		try {
//
//			// 生成条码图片
//			btMap = BarcodeCreater.creatBarcode(this, str2.trim(), 300, 100,
//					true, 1);
//			// 条码图片转成打印字节数组
//			byte[] printData = BitmapTools.bitmap2PrinterBytes(btMap);
//			// 将打印数组添加到打印队列
//			mPrintQueue.addBmp(60, 0, btMap.getWidth(), btMap.getHeight(),
//					printData);
//			// 打印6个空行，使一维码显示到打印头外面
//			mPrintQueue.addText(50, "\n\n\n\n\n\n".getBytes("GBK"));
//
//		} catch (UnsupportedEncodingException e1) {
//			// TODO Auto-generated catch block
//			e1.printStackTrace();
//		}
//
//		// 打印队列开始
//		mPrintQueue.printStart();
//
//	}
    public void printBarCode() {

    	mTv.setText("");
    }

	/**
	 * 打印二维码
	 */
	public void printQRCode() {

		// 获取编辑框中的字符串
		str2 = mTv.getText().toString().trim();

		if (str2 == null || str2.length() <= 0)
			return;

		try {

			// 二维码生成
			btMap = encode2dAsBitmap(str2, 300, 300, 2);

			// 二维码图片转成打印字节数组
			byte[] printData = BitmapTools.bitmap2PrinterBytes(btMap);

			// 将打印数组添加到打印队列
			mPrintQueue.addBmp(50, 50, btMap.getWidth(), btMap.getHeight(),
					printData);

			// 打印6个空行，使二维码显示到打印头外面
			mPrintQueue.addText(50, "\n\n\n\n\n\n".getBytes("GBK"));

		} catch (UnsupportedEncodingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		// 打印队列开始
		mPrintQueue.printStart();

	}





	 //扫描
	BroadcastReceiver receiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			// TODO Auto-generated method stub
			String action = intent.getAction();
			if (action.equalsIgnoreCase(PosApi.ACTION_POS_COMM_STATUS)) {

				// 串口标志判断
				int cmdFlag = intent.getIntExtra(PosApi.KEY_CMD_FLAG, -1);

				// 获取串口返回的字节数组
					byte[] buffer = intent.getByteArrayExtra(PosApi.KEY_CMD_DATA_BUFFER);
				switch (cmdFlag) {
					// 如果为扫描数据返回串口
					case PosApi.POS_EXPAND_SERIAL3:
						if (buffer == null)
							return;
						try {
							// 将字节数组转成字符串
							String str = new String(buffer, "UTF-8");
							// 开启提示音，提示客户条码或者二维码已经被扫到
							player.start();
							// 显示到编辑框中
							mTv.append(str);

						} catch (UnsupportedEncodingException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
						break;

				}
//				// 扫描完本次后清空，以便下次扫描
			buffer = null;

			}
		}

	};

	@Override
	protected void onResume() {
		// TODO Auto-generated method stub
		// after 1000ms openDevice
		// 必须延迟一秒，否则将会出现第一次扫描和打印延迟的现象
		new Handler().postDelayed(new Runnable() {
			@Override
			public void run() {
				// TODO Auto-generated method stub
				// 打开GPIO，给扫描头上电
				openDevice();

			}
		}, 1000);

		super.onResume();
	}

	@Override
	protected void onDestroy() {
		// TODO Auto-generated method stub
		super.onDestroy();

		// 注销获取扫描数据的广播
		unregisterReceiver(receiver);

		// 注销物理SCAN按键的接收广播
		unregisterReceiver(scanBroadcastReceiver);

		// 关闭下层串口以及打印机
		mPosSDK.closeDev();

		if (mPrintQueue != null) {
			// 打印队列关闭
			mPrintQueue.close();
		}
	}

	/**
	 * 初始化
	 */
	OnCommEventListener mCommEventListener = new OnCommEventListener() {
		@Override
		public void onCommState(int cmdFlag, int state, byte[] resp, int respLen) {
			// TODO Auto-generated method stub
			switch (cmdFlag) {
				case PosApi.POS_INIT:
					if (state == PosApi.COMM_STATUS_SUCCESS) {
						Toast.makeText(getApplicationContext(), "设备初始化成功",
								Toast.LENGTH_SHORT).show();
					} else {
						Toast.makeText(getApplicationContext(), "设备初始化失败",
								Toast.LENGTH_SHORT).show();
					}
					break;
			}
		}
	};

	/**
	 * 物理SCAN按键监听
	 */
	boolean isScan = false;

	class ScanBroadcastReceiver extends BroadcastReceiver {
		@Override
		public void onReceive(Context context, Intent intent) {
			// TODO Auto-generated method stub
			//监听到SCAN按键按下广播，执行扫描
			ScanDomn();
		}
	}

	/**
	 * 执行扫描，扫描后的结果会通过action为PosApi.ACTION_POS_COMM_STATUS的广播发回
	 */
	public void ScanDomn(){
		if (!isScan) {
			mPosSDK.gpioControl(mGpioTrig, 0, 0);
			isScan = true;
			handler.removeCallbacks(run);
			// 3秒后还没有扫描到信息则强制关闭扫描头
			handler.postDelayed(run, 3000);
		} else {
			mPosSDK.gpioControl(mGpioTrig, 0, 1);
			mPosSDK.gpioControl(mGpioTrig, 0, 0);
			isScan = true;
			handler.removeCallbacks(run);
			// 3秒后还没有扫描到信息则强制关闭扫描头
			handler.postDelayed(run, 3000);
		}
	}

	Handler handler = new Handler();
	Runnable run = new Runnable() {
		@Override
		public void run() {
			// TODO Auto-generated method stub
			// 强制关闭扫描头
			mPosSDK.gpioControl(mGpioTrig, 0, 1);
			isScan = false;
		}
	};

	/**
	 * 弹出提示框
	 *
	 * @param msg
	 */
	private void showTip(String msg) {
		new AlertDialog.Builder(this).setTitle("提示").setMessage(msg)
				.setNegativeButton("关闭", new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						// TODO Auto-generated method stub
						dialog.dismiss();
						// isCanPrint=true;
					}
				}).show();
	}

	/**
	 * 字符串转成GBK
	 *
	 * @param str
	 * @return
	 * @throws UnsupportedEncodingException
	 */
	public String toGBK(String str) throws UnsupportedEncodingException {
		return this.changeCharset(str, "GBK");
	}

	/**
	 * 字符串编码转换的实现方法
	 *
	 * @param str
	 *            待转换编码的字符串
	 * @param newCharset
	 *            目标编码
	 * @return
	 * @throws UnsupportedEncodingException
	 */
	public String changeCharset(String str, String newCharset)
			throws UnsupportedEncodingException {
		if (str != null) {
			// 用默认字符编码解码字符串。
			byte[] bs = str.getBytes();
			// 用新的字符编码生成字符串
			return new String(bs, newCharset);
		}
		return null;
	}

	/**
	 * 生成二维码 要转换的地址或字符串,可以是中文
	 *
	 * @param url
	 * @param width
	 * @param height
	 * @return
	 */
	public Bitmap createQRImage(String url, int width, int height) {
		try {
			// 判断URL合法性
			if (url == null || "".equals(url) || url.length() < 1) {
				return null;
			}
			Hashtable<EncodeHintType, String> hints = new Hashtable<EncodeHintType, String>();
			hints.put(EncodeHintType.CHARACTER_SET, "GBK");
			// 图像数据转换，使用了矩阵转换
			BitMatrix bitMatrix = new QRCodeWriter().encode(url,
					BarcodeFormat.QR_CODE, width, height, hints);
			// bitMatrix = deleteWhite(bitMatrix);// 删除白边
			bitMatrix = deleteWhite(bitMatrix);// 删除白边
			width = bitMatrix.getWidth();
			height = bitMatrix.getHeight();
			int[] pixels = new int[width * height];
			// 下面这里按照二维码的算法，逐个生成二维码的图片，
			// 两个for循环是图片横列扫描的结果
			for (int y = 0; y < height; y++) {
				for (int x = 0; x < width; x++) {
					if (bitMatrix.get(x, y)) {
						pixels[y * width + x] = 0xff000000;
					} else {
						pixels[y * width + x] = 0xffffffff;
					}
				}
			}
			// 生成二维码图片的格式，使用ARGB_8888
			Bitmap bitmap = Bitmap
					.createBitmap(width, height, Config.ARGB_8888);
			bitmap.setPixels(pixels, 0, width, 0, 0, width, height);
			return bitmap;
		} catch (WriterException e) {
			e.printStackTrace();
		}
		return null;
	}

	/**
	 * 生成去除白边的二维码
	 *
	 * @param str
	 * @param width
	 * @param height
	 * @return
	 */
	public static Bitmap Create2DCode(String str, int width, int height) {
		try {
			Hashtable<EncodeHintType, Object> hints = new Hashtable<EncodeHintType, Object>();
			hints.put(EncodeHintType.CHARACTER_SET, "GBK");
			hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H);
			BitMatrix matrix = new QRCodeWriter().encode(str,
					BarcodeFormat.QR_CODE, width, height);
			matrix = deleteWhite(matrix);// 删除白边
			width = matrix.getWidth();
			height = matrix.getHeight();
			int[] pixels = new int[width * height];
			for (int y = 0; y < height; y++) {
				for (int x = 0; x < width; x++) {
					if (matrix.get(x, y)) {
						pixels[y * width + x] = Color.BLACK;
					} else {
						pixels[y * width + x] = Color.WHITE;
					}
				}
			}
			Bitmap bitmap = Bitmap.createBitmap(width, height,
					Bitmap.Config.ARGB_8888);
			bitmap.setPixels(pixels, 0, width, 0, 0, width, height);
			return bitmap;
		} catch (Exception e) {
			return null;
		}
	}

	private static BitMatrix deleteWhite(BitMatrix matrix) {
		int[] rec = matrix.getEnclosingRectangle();
		int resWidth = rec[2] + 1;
		int resHeight = rec[3] + 1;

		BitMatrix resMatrix = new BitMatrix(resWidth, resHeight);
		resMatrix.clear();
		for (int i = 0; i < resWidth; i++) {
			for (int j = 0; j < resHeight; j++) {
				if (matrix.get(i + rec[0], j + rec[1]))
					resMatrix.set(i, j);
			}
		}
		return resMatrix;
	}

	/**
	 * 文字转图片
	 *
	 * @param str
	 * @return
	 */
	public Bitmap word2bitmap(String str) {

		Bitmap bMap = Bitmap.createBitmap(300, 80, Config.ARGB_8888);
		Canvas canvas = new Canvas(bMap);
		canvas.drawColor(Color.WHITE);
		TextPaint textPaint = new TextPaint();
		textPaint.setStyle(Paint.Style.FILL);
		textPaint.setColor(Color.BLACK);
		textPaint.setTextSize(40.0F);
		StaticLayout layout = new StaticLayout(str, textPaint, bMap.getWidth(),
				Alignment.ALIGN_NORMAL, (float) 1.0, (float) 0.0, true);
		layout.draw(canvas);

		return bMap;

	}

	/**
	 * 两张图片上下合并成一张
	 *
	 * @param bitmap1
	 * @param bitmap2
	 * @return
	 */
	public Bitmap twoBtmap2One(Bitmap bitmap1, Bitmap bitmap2) {
		Bitmap bitmap3 = Bitmap.createBitmap(bitmap1.getWidth(),
				bitmap1.getHeight() + bitmap2.getHeight(), bitmap1.getConfig());
		Canvas canvas = new Canvas(bitmap3);
		canvas.drawBitmap(bitmap1, new Matrix(), null);
		canvas.drawBitmap(bitmap2, 0, bitmap1.getHeight(), null);
		return bitmap3;
	}

	/**
	 * 文字转图片
	 *
	 * @param text
	 *            将要生成图片的内容
	 * @param textSize
	 *            文字大小
	 * @return
	 */
	public static Bitmap textAsBitmap(String text, float textSize) {

		TextPaint textPaint = new TextPaint();

		textPaint.setColor(Color.BLACK);

		textPaint.setTextSize(textSize);

		StaticLayout layout = new StaticLayout(text, textPaint, 380,
				Alignment.ALIGN_NORMAL, 1.3f, 0.0f, true);
		Bitmap bitmap = Bitmap.createBitmap(layout.getWidth() + 20,
				layout.getHeight() + 20, Bitmap.Config.ARGB_8888);
		Canvas canvas = new Canvas(bitmap);
		canvas.translate(10, 10);
		canvas.drawColor(Color.WHITE);

		layout.draw(canvas);
		Log.d("textAsBitmap",
				String.format("1:%d %d", layout.getWidth(), layout.getHeight()));
		return bitmap;
	}

	/*
	 * 打印文字 size 1 --倍大小 2--2倍大小
	 */
	private void addPrintTextWithSize(int size, int concentration, byte[] data) {
		if (data == null)
			return;
		// 2倍字体大小
		byte[] _2x = new byte[] { 0x1b, 0x57, 0x02 };
		// 1倍字体大小
		byte[] _1x = new byte[] { 0x1b, 0x57, 0x01 };
		byte[] mData = null;
		if (size == 1) {
			mData = new byte[3 + data.length];
			// 1倍字体大小 默认
			System.arraycopy(_1x, 0, mData, 0, _1x.length);
			System.arraycopy(data, 0, mData, _1x.length, data.length);

			mPrintQueue.addText(concentration, mData);

		} else if (size == 2) {
			mData = new byte[3 + data.length];
			// 1倍字体大小 默认
			System.arraycopy(_2x, 0, mData, 0, _2x.length);
			System.arraycopy(data, 0, mData, _2x.length, data.length);

			mPrintQueue.addText(concentration, mData);

		}

	}

	/**
	 * 图片旋转
	 *
	 * @param bm
	 * @param orientationDegree
	 * @return
	 */
	Bitmap adjustPhotoRotation(Bitmap bm, final int orientationDegree) {

		Matrix m = new Matrix();
		m.setRotate(orientationDegree, (float) bm.getWidth() / 2,
				(float) bm.getHeight() / 2);
		float targetX, targetY;
		if (orientationDegree == 90) {
			targetX = bm.getHeight();
			targetY = 0;
		} else {
			targetX = bm.getHeight();
			targetY = bm.getWidth();
		}

		final float[] values = new float[9];
		m.getValues(values);

		float x1 = values[Matrix.MTRANS_X];
		float y1 = values[Matrix.MTRANS_Y];

		m.postTranslate(targetX - x1, targetY - y1);

		Bitmap bm1 = Bitmap.createBitmap(bm.getHeight(), bm.getWidth(),
				Bitmap.Config.ARGB_8888);
		Paint paint = new Paint();
		Canvas canvas = new Canvas(bm1);
		canvas.drawBitmap(bm, m, paint);

		return bm1;
	}

	/**
	 * 生成二维码
	 *
	 * @param contents
	 *            二维码内容
	 * @param desiredWidth
	 *            二维码宽度
	 * @param desiredHeight
	 *            二维码高度
	 * @param barType
	 *            条码类型
	 * @return
	 */
	public static Bitmap encode2dAsBitmap(String contents, int desiredWidth,
										  int desiredHeight, int barType) {
		BarcodeFormat barcodeFormat = BarcodeFormat.CODE_128;
		if (barType == 1) {
			barcodeFormat = BarcodeFormat.CODE_128;
		} else if (barType == 2) {
			barcodeFormat = BarcodeFormat.QR_CODE;
		}

		Bitmap barcodeBitmap = null;
		try {
			barcodeBitmap = encodeAsBitmap(contents, barcodeFormat,
					desiredWidth, desiredHeight);
		} catch (WriterException e) {
			e.printStackTrace();
		}

		return barcodeBitmap;
	}

	public static Bitmap encodeAsBitmap(String contents, BarcodeFormat format,
										int desiredWidth, int desiredHeight) throws WriterException {
		final int WHITE = 0xFFFFFFFF;
		final int BLACK = 0xFF000000;

		HashMap<EncodeHintType, String> hints = null;
		String encoding = guessAppropriateEncoding(contents);
		if (encoding != null) {
			hints = new HashMap<EncodeHintType, String>(2);
			hints.put(EncodeHintType.CHARACTER_SET, encoding);
		}
		MultiFormatWriter writer = new MultiFormatWriter();
		BitMatrix result = writer.encode(contents, format, desiredWidth,
				desiredHeight, hints);
		result = deleteWhite(result);// 删除白边
		int width = result.getWidth();
		int height = result.getHeight();
		int[] pixels = new int[width * height];
		// All are 0, or black, by default
		for (int y = 0; y < height; y++) {
			int offset = y * width;
			for (int x = 0; x < width; x++) {
				pixels[offset + x] = result.get(x, y) ? BLACK : WHITE;
			}
		}

		Bitmap bitmap = Bitmap.createBitmap(width, height,
				Bitmap.Config.ARGB_8888);
		bitmap.setPixels(pixels, 0, width, 0, 0, width, height);
		return bitmap;
	}

	public static String guessAppropriateEncoding(CharSequence contents) {
		// Very crude at the moment
		for (int i = 0; i < contents.length(); i++) {
			if (contents.charAt(i) > 0xFF) {
				return "UTF-8";
			}
		}
		return null;
	}

	/**
	 *
	 * 读取SD卡中文本文件
	 *
//	 * @param fileName
	 *
//	 * @return
	 */
	@SuppressWarnings("resource")
	public String readSDFile() {
		try {
			File file = new File("/mnt/sdcard/pro.txt");
			FileInputStream is = new FileInputStream(file);
			byte[] b = new byte[is.available()];
			is.read(b);
			String result = new String(b);
			System.out.println("读取成功：" + result);
			return result;
		} catch (Exception e) {
			e.printStackTrace();
			return "";
		}
	}

	// 保存至SD卡
	public void saveDada2SD(String sb) {
		String filePath = null;
		boolean hasSDCard = Environment.getExternalStorageState().equals(
				Environment.MEDIA_MOUNTED);
		if (hasSDCard) { // SD卡根目录的hello.text
			filePath = "/mnt/sdcard/pro.txt";
		}
		try {
			File file = new File(filePath);
			if (!file.exists()) {
				File dir = new File(file.getParent());
				dir.mkdirs();
				file.createNewFile();
			}
			FileOutputStream fileOut = null;
			BufferedOutputStream writer = null;
			OutputStreamWriter outputStreamWriter = null;
			BufferedWriter bufferedWriter = null;
			try {
				fileOut = new FileOutputStream(file);
				writer = new BufferedOutputStream(fileOut);
				outputStreamWriter = new OutputStreamWriter(writer, "UTF-8");
				bufferedWriter = new BufferedWriter(outputStreamWriter);
				bufferedWriter.write(new String(sb.toString()));
				bufferedWriter.close();
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}

		} catch (Exception e) {
			e.printStackTrace();
		}
		Toast.makeText(this, "保存成功，请到SD卡查看,文件名为pro.txt", Toast.LENGTH_SHORT)
				.show();
	}

}
