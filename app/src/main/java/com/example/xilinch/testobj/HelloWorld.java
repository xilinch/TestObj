package com.example.xilinch.testobj;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.lang.reflect.Field;
import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLDisplay;
import javax.microedition.khronos.opengles.GL10;

import android.Manifest;
import android.app.Activity;
import android.app.Dialog;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.ActivityCompat;
import android.util.FloatMath;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.threed.jpct.Camera;
import com.threed.jpct.FrameBuffer;
import com.threed.jpct.Light;
import com.threed.jpct.Loader;
import com.threed.jpct.Logger;
import com.threed.jpct.Object3D;
import com.threed.jpct.RGBColor;
import com.threed.jpct.SimpleVector;
import com.threed.jpct.Texture;
import com.threed.jpct.TextureManager;
import com.threed.jpct.World;
import com.threed.jpct.util.BitmapHelper;
import com.threed.jpct.util.MemoryHelper;

/**
 * A simple demo. This shows more how to use jPCT-AE than it shows how to write
 * a proper application for Android. It includes basic activity management to
 * handle pause and resume...
 * 
 * @author EgonOlsen
 * 
 */
public class HelloWorld extends Activity {

	// Used to handle pause and resume...
	private static HelloWorld master = null;

	private GLSurfaceView mGLView;
	private MyRenderer renderer = null;
	private FrameBuffer fb = null;
	private World world = null;
	private RGBColor back = new RGBColor(50, 50, 100);

	private float touchTurn = 0;
	private float touchTurnUp = 0;

	private float touchScale = 1;
	
	private float xpos = -1;
	private float ypos = -1;

	//����
	float preDist;
	boolean zoom = false;;
	
	private Object3D model = null;
	private int fps = 0;

	private Light sun = null;
	private Dialog dialog;
    private int progressValue = 0;
    private ProgressBar progress;
    private TextView progressPercent;
	
	private String path = "/sdcard/moxingzhenghe/dragon";
	
	protected void onCreate(Bundle savedInstanceState) {

		Logger.log("onCreate");

		if (master != null) {
			copy(master);
		}

		super.onCreate(savedInstanceState);
		boolean isGranted = ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
		if(isGranted){
			ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 0);
		}


		mGLView = new GLSurfaceView(getApplication());

		mGLView.setEGLConfigChooser(new GLSurfaceView.EGLConfigChooser() {
			public EGLConfig chooseConfig(EGL10 egl, EGLDisplay display) {
				// Ensure that we get a 16bit framebuffer. Otherwise, we'll fall
				// back to Pixelflinger on some device (read: Samsung I7500)
				int[] attributes = new int[] { EGL10.EGL_DEPTH_SIZE, 16, EGL10.EGL_NONE };
				EGLConfig[] configs = new EGLConfig[1];
				int[] result = new int[1];
				egl.eglChooseConfig(display, attributes, configs, 1, result);
				return configs[0];
			}
		});

		renderer = new MyRenderer();
		mGLView.setRenderer(renderer);
		setContentView(mGLView);
	}

	
	@Override
	protected void onPause() {
		super.onPause();
		mGLView.onPause();
	}

	@Override
	protected void onResume() {
		super.onResume();
		mGLView.onResume();
	}

	@Override
	protected void onStop() {
		super.onStop();
	}

	private void copy(Object src) {
		try {
			Logger.log("Copying data from master Activity!");
			Field[] fs = src.getClass().getDeclaredFields();
			for (Field f : fs) {
				f.setAccessible(true);
				f.set(this, f.get(src));
			}
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public boolean onTouchEvent(MotionEvent me) {
//		mScaleGestureDetector.onTouchEvent(me);
		int action = me.getAction() & MotionEvent.ACTION_MASK;
		switch(action){
		case MotionEvent.ACTION_DOWN: 
			xpos = me.getX();
			ypos = me.getY();
			break;
		case MotionEvent.ACTION_UP:
			xpos = -1;
			ypos = -1;
			touchTurn = 0;
			touchTurnUp = 0;
			break;
		case MotionEvent.ACTION_MOVE:
			if(me.getPointerCount() == 1){
				float xd = me.getX() - xpos;
				float yd = me.getY() - ypos;
	
				xpos = me.getX();
				ypos = me.getY();
	
				touchTurn = xd / -100f;
				touchTurnUp = yd / -100f;
			} else if (me.getPointerCount() == 2 && zoom ){
				float dist = spacing(me);
				touchScale = 1 - (preDist - dist)/100f;
				preDist = dist;
			}
			break;
		case MotionEvent.ACTION_POINTER_DOWN:
			preDist = spacing(me);
			if (spacing(me) > 10f){
				zoom = true;
			}
			break;
		case MotionEvent.ACTION_POINTER_UP:
			zoom = false;
			touchTurn = 0;
			touchTurnUp = 0;
			break;
		}

		try {
			Thread.sleep(15);
		} catch (Exception e) {
			// No need for this...
		}

		return super.onTouchEvent(me);
	}

	private float spacing(MotionEvent event) {
		float x = event.getX(0) - event.getX(1);
		float y = event.getY(0) - event.getY(1);
		return (float)Math.sqrt(x * x + y * y);
	}
	
	protected boolean isFullscreenOpaque() {
		return true;
	}

	class MyRenderer implements GLSurfaceView.Renderer {

		private long time = System.currentTimeMillis();

		public MyRenderer() {
		}

		public void onSurfaceChanged(GL10 gl, int w, int h) {
			if (fb != null) {
				fb.dispose();
			}
			fb = new FrameBuffer(gl, w, h);

			if (master == null) {

				world = new World();
				world.setAmbientLight(20, 20, 20);

				sun = new Light(world);
				sun.setIntensity(250, 250, 250);

				Object3D[] objs = null;
				try {
					File files = new File(path);
					if(files == null || files.listFiles() == null || files.listFiles().length == 0){
						return;
					}
					FileInputStream objstream = null;
					FileInputStream mtlstream = null;
					for(File file : files.listFiles()){
						if(file.getName().toLowerCase().endsWith(".obj")){
							objstream = new FileInputStream(file);
						} else if(file.getName().toLowerCase().endsWith(".mtl")){
							mtlstream = new FileInputStream(file);
						} else if(isImage(file)){
							Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath());
							int scaleWidth = -1;
							int scaleHeight = -1;
							if(!isPowerOf2(bitmap.getHeight()) || !isPowerOf2(bitmap.getWidth())){
								scaleWidth = nextPowerOf2(bitmap.getWidth());
								scaleHeight = nextPowerOf2(bitmap.getHeight());
							}
							bitmap = BitmapHelper.rescale(bitmap, scaleWidth, scaleHeight);
							Texture texture = new Texture(bitmap);
							TextureManager.getInstance().addTexture(file.getName(), texture);
						}
					}
					objs = Loader.loadOBJ(objstream, mtlstream, 2);
				} catch (FileNotFoundException e) {
					e.printStackTrace();
					return;
				}
				model = Object3D.mergeAll(objs);
				model.strip();
				model.build();

				world.addObject(model);

				Camera cam = world.getCamera();
				cam.moveCamera(Camera.CAMERA_MOVEOUT, 50);
				cam.lookAt(model.getTransformedCenter());

				SimpleVector sv = new SimpleVector();
				sv.set(model.getTransformedCenter());
				sv.y -= 100;
				sv.z -= 100;
				sun.setPosition(sv);
				MemoryHelper.compact();

				if (master == null) {
					Logger.log("Saving master Activity!");
					master = HelloWorld.this;
				}
			}
		}

		public void onSurfaceCreated(GL10 gl, EGLConfig config) {
		}

		public void onDrawFrame(GL10 gl) {
			if (touchTurn != 0 && !zoom ) {
				model.rotateY(touchTurn);
				touchTurn = 0;
			}

			if (touchTurnUp != 0 && !zoom) {
				model.rotateX(touchTurnUp);
				touchTurnUp = 0;
				
			}
			if(touchScale != 1 && touchScale > 0){
				model.scale(touchScale);
				touchScale = 1;
			}
			
			fb.clear(back);
			world.renderScene(fb);
			world.draw(fb);
			fb.display();

			if (System.currentTimeMillis() - time >= 1000) {
				Logger.log(fps + "fps");
				fps = 0;
				time = System.currentTimeMillis();
			}
			fps++;
		}
		
		public boolean isImage(File file){
			if(file.getName().toLowerCase().endsWith(".bmp") || 
			file.getName().toLowerCase().endsWith(".jpg") ||
			file.getName().toLowerCase().endsWith(".png")){
				return true;
			}
			return false;
		}
		
		public  boolean isPowerOf2(int n) {
		    return ((n & -n) == n);
		  }

		  public  int nextPowerOf2(int n) {
		    --n;
		    n |= n >>> 16;
		    n |= n >>> 8;
		    n |= n >>> 4;
		    n |= n >>> 2;
		    n |= n >>> 1;
		    return (n + 1);
		  }

		  public  int prevPowerOf2(int n) {
		    if (isPowerOf2(n))
		      return nextPowerOf2(n);

		    return (nextPowerOf2(n) - 1);
		  }
	}
}