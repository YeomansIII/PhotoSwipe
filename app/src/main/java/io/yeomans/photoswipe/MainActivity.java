package io.yeomans.photoswipe;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
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
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.net.Uri;
import android.provider.MediaStore;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Size;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceView;
import android.widget.ImageView;
import android.widget.Toast;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;


public class MainActivity extends ActionBarActivity {

    final int PICK_IMAGE = 101;
    Context context;
    String picturePath;
    private float x1,x2;
    static final int MIN_DISTANCE = 300;
    int swipeDirection = -1;
    CameraManager camera;
    StreamConfigurationMap streamMap;
    boolean camConfigured = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        context = getApplicationContext();
        getImage();
        CameraManager camManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
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
            camManager.openCamera(frontCamera, new CamCallback(), null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

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
                } else {
                    deltaX = x1 - x2;
                    if (deltaX > MIN_DISTANCE)
                    {
                        Toast.makeText(this, "right2left swipe", Toast.LENGTH_SHORT).show();
                        swipeDirection = 1;
                    }
                }
                break;
        }
        return super.onTouchEvent(event);
    }

    private class CamCallback extends CameraDevice.StateCallback {

        @Override
        public void onOpened(CameraDevice camera) {
            Size[] sizes = streamMap.getOutputSizes(ImageFormat.JPEG);
            ImageReader ir = ImageReader.newInstance(sizes[0].getWidth(), sizes[0].getHeight(), ImageFormat.JPEG, 2);
            List<Surface> surfaceList = new ArrayList<Surface>();
            surfaceList.add(ir.getSurface());
            try {
                camera.createCaptureSession(surfaceList,new CaptureCallback(), null);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onDisconnected(CameraDevice camera) {

        }

        @Override
        public void onError(CameraDevice camera, int error) {

        }
    }

    private class CaptureCallback extends CameraCaptureSession.StateCallback {

        @Override
        public void onConfigured(CameraCaptureSession session) {
            camConfigured = true;
        }

        @Override
        public void onConfigureFailed(CameraCaptureSession session) {

        }
    }
}
