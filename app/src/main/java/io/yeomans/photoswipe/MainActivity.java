package io.yeomans.photoswipe;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.net.Uri;
import android.os.Handler;
import android.provider.MediaStore;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;
import android.util.Size;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceView;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.Toast;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;


public class MainActivity extends Activity {

    final int PICK_IMAGE = 101;
    Context context;
    String picturePath;
    private float x1,x2;
    static final int MIN_DISTANCE = 300;
    int swipeDirection = -1;
    int faceNumber = 0;
    String appPath;
    CameraManager camManager;
    CameraDevice cam;
    CaptureRequest capRequest;
    StreamConfigurationMap streamMap;
    ImageReader ir;
    boolean camConfigured = false;
    Handler mBackgroundHandler;
    private final ImageReader.OnImageAvailableListener mOnImageAvailableListener
            = new ImageReader.OnImageAvailableListener() {

        @Override
        public void onImageAvailable(ImageReader reader) {
            Log.d("Image", "Saving Image");
            faceNumber++;
            mBackgroundHandler.post(new ImageSaver(reader.acquireNextImage(), new File(appPath + "/" + faceNumber + ".jpg")));
            mBackgroundHandler.post(new Runnable() {
                @Override
                public void run() {
                    ImageView iv = (ImageView)findViewById(R.id.imageViewMain);
                    iv.setImageURI(Uri.parse(appPath + "/"+faceNumber+".jpg"));

                }
            });
        }

    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_main);
        context = getApplicationContext();
        mBackgroundHandler = new Handler();

        try {
            PackageManager m = getPackageManager();
            appPath = getPackageName();
            PackageInfo p = m.getPackageInfo(appPath, 0);
            appPath = p.applicationInfo.dataDir;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }

        getImage();
        openCam();

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
            
        }

        return super.onOptionsItemSelected(item);
    }

    public void getImage() {
        Intent getIntent = new Intent(Intent.ACTION_GET_CONTENT);
        getIntent.setType("image/*");

        Intent pickIntent = new Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        pickIntent.setType("image/*");

        Intent chooserIntent = Intent.createChooser(getIntent, "Select Image");
        chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Intent[] {pickIntent});

        startActivityForResult(chooserIntent, PICK_IMAGE);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_IMAGE && resultCode == Activity.RESULT_OK) {
            if (data == null) {
                //Display an error
                return;
            }
            Uri selectedImage = data.getData();
            String[] filePathColumn = { MediaStore.Images.Media.DATA };

            Cursor cursor = getContentResolver().query(selectedImage,
                    filePathColumn, null, null, null);
            cursor.moveToFirst();

            int columnIndex = cursor.getColumnIndex(filePathColumn[0]);
            picturePath = cursor.getString(columnIndex);
            cursor.close();

            ImageView iv = (ImageView) findViewById(R.id.imageViewMain);
            iv.setImageURI(selectedImage);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event)
    {
        switch(event.getAction())
        {
            case MotionEvent.ACTION_DOWN:
                x1 = event.getX();
                break;
            case MotionEvent.ACTION_UP:
                swipeDirection = -1;
                x2 = event.getX();
                float deltaX = x2 - x1;
                if (deltaX > MIN_DISTANCE)
                {
                    Toast.makeText(this, "left2right swipe", Toast.LENGTH_SHORT).show();
                    swipeDirection = 0;
                    takePicture();
                } else {
                    deltaX = x1 - x2;
                    if (deltaX > MIN_DISTANCE)
                    {
                        Toast.makeText(this, "right2left swipe", Toast.LENGTH_SHORT).show();
                        swipeDirection = 1;
                        takePicture();
                    }
                }
                break;
        }
        return super.onTouchEvent(event);
    }

    public void openCam() {
        camManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        String frontCamera = "";
        try {
            String[] idList = camManager.getCameraIdList();
            for(String id : idList) {
                CameraCharacteristics camChar = camManager.getCameraCharacteristics(id);
                streamMap = camChar.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                if(CameraCharacteristics.LENS_FACING_FRONT == camChar.get(CameraCharacteristics.LENS_FACING)) {
                    frontCamera = id;
                }
            }
            camManager.openCamera(frontCamera, new CamCallback(), mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    public void takePicture() {
        Log.d("Image", "Camera Opened");
        Size[] sizes = streamMap.getOutputSizes(ImageFormat.JPEG);
        ir = ImageReader.newInstance(sizes[0].getWidth(), sizes[0].getHeight(), ImageFormat.JPEG, 2);
        ir.setOnImageAvailableListener(mOnImageAvailableListener, mBackgroundHandler);
        List<Surface> surfaceList = new ArrayList<Surface>();
        surfaceList.add(ir.getSurface());
        try {
            cam.createCaptureSession(surfaceList,new CaptureSessionCallback(), mBackgroundHandler);
            CaptureRequest.Builder capBuild = cam.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            capBuild.addTarget(ir.getSurface());
            capRequest = capBuild.build();
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private class CamCallback extends CameraDevice.StateCallback {

        @Override
        public void onOpened(CameraDevice camera) {
            cam = camera;
        }

        @Override
        public void onDisconnected(CameraDevice camera) {

        }

        @Override
        public void onError(CameraDevice camera, int error) {

        }
    }

    private class CaptureSessionCallback extends CameraCaptureSession.StateCallback {

        @Override
        public void onConfigured(CameraCaptureSession session) {
            Log.d("Image", "Camera Configured");
            camConfigured = true;
            try {
                session.capture(capRequest, new CapCallback(), mBackgroundHandler);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onConfigureFailed(CameraCaptureSession session) {

        }
    }

    private class CapCallback extends CameraCaptureSession.CaptureCallback {
         @Override
        public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
             Log.d("Image","Capture Completed");
         }
    }

    private static class ImageSaver implements Runnable {

        /**
         * The JPEG image
         */
        private final Image mImage;
        /**
         * The file we save the image into.
         */
        private final File mFile;

        public ImageSaver(Image image, File file) {
            mImage = image;
            mFile = file;
        }

        @Override
        public void run() {
            ByteBuffer buffer = mImage.getPlanes()[0].getBuffer();
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            FileOutputStream output = null;
            try {
                output = new FileOutputStream(mFile);
                output.write(bytes);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                mImage.close();
                if (null != output) {
                    try {
                        output.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }

    }
}
